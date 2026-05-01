package com.queryplancache.engine;

import com.queryplancache.cache.CacheEntry;
import com.queryplancache.cache.CacheStats;
import com.queryplancache.cache.QueryPlanCache;
import com.queryplancache.normalizer.ParameterBinding;
import com.queryplancache.normalizer.QueryNormalizer;
import com.queryplancache.plan.QueryPlan;
import com.queryplancache.plan.QueryPlanGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * The main query execution engine that integrates query normalization,
 * plan caching, and plan generation.
 *
 * <p>Execution flow:</p>
 * <ol>
 *   <li>Receive raw SQL query</li>
 *   <li>Check for DDL → trigger cache invalidation if detected</li>
 *   <li>Normalize query via ANTLR-based normalizer → extract parameter bindings</li>
 *   <li>Compute cache key (SHA-256 hash of normalized query)</li>
 *   <li>Lookup in cache:
 *       <ul>
 *         <li>HIT: Return cached plan (with new parameter bindings)</li>
 *         <li>MISS: Generate plan, store in cache, return</li>
 *       </ul>
 *   </li>
 * </ol>
 */
public class QueryEngine {

    private static final Logger log = LoggerFactory.getLogger(QueryEngine.class);

    private final QueryNormalizer normalizer;
    private final QueryPlanCache cache;
    private final QueryPlanGenerator planGenerator;

    public QueryEngine(QueryNormalizer normalizer, QueryPlanCache cache,
                       QueryPlanGenerator planGenerator) {
        this.normalizer = normalizer;
        this.cache = cache;
        this.planGenerator = planGenerator;
    }

    /**
     * Executes the query plan lookup/generation pipeline for a raw SQL query.
     *
     * @param rawSql the original SQL query
     * @return the QueryPlan (from cache or newly generated)
     */
    public QueryPlan getQueryPlan(String rawSql) {
        log.info("Processing query: {}", rawSql);

        // Step 1: Check for DDL — trigger invalidation if needed
        if (normalizer.isDdlStatement(rawSql)) {
            handleDdl(rawSql);
            return null; // DDL statements don't produce query plans
        }

        // Step 2: Normalize the query using ANTLR-based normalizer
        ParameterBinding binding = normalizer.normalize(rawSql);
        String normalizedQuery = binding.getNormalizedQuery();
        log.debug("Normalized: '{}' with params: {}", normalizedQuery, binding.getParameters());

        // Step 3: Compute cache key (SHA-256 of normalized query)
        String cacheKey = computeCacheKey(normalizedQuery);
        log.debug("Cache key: {}", cacheKey);

        // Step 4: Cache lookup
        Optional<CacheEntry> cached = cache.get(cacheKey);

        if (cached.isPresent()) {
            // CACHE HIT — reuse existing plan
            QueryPlan plan = cached.get().getQueryPlan();
            log.info("CACHE HIT: Reusing plan for '{}' (hit #{}) | params: {}",
                    normalizedQuery, cached.get().getHitCount(), binding.getParameters());
            return plan;
        }

        // CACHE MISS — generate new plan
        log.info("CACHE MISS: Generating new plan for '{}'", normalizedQuery);

        long genStart = System.nanoTime();
        QueryPlan plan = planGenerator.generatePlan(normalizedQuery);
        long genTimeNanos = System.nanoTime() - genStart;

        // Store in cache for future reuse
        cache.put(cacheKey, plan);
        cache.getStats().recordMiss(genTimeNanos);

        log.info("Plan generated and cached in {}ms for '{}'",
                genTimeNanos / 1_000_000, normalizedQuery);

        return plan;
    }

    /**
     * Handles DDL statements by invalidating relevant cache entries.
     */
    private void handleDdl(String rawSql) {
        String targetTable = normalizer.extractDdlTargetTable(rawSql);
        if (targetTable != null) {
            int invalidated = cache.invalidateByTable(targetTable);
            log.warn("DDL detected for table '{}'. Invalidated {} cached plans.", targetTable, invalidated);
        } else {
            log.warn("DDL detected but could not determine target table. Clearing entire cache.");
            cache.clear();
        }
    }

    /**
     * Computes a SHA-256 hash of the normalized query string to produce a
     * fixed-length cache key.
     */
    String computeCacheKey(String normalizedQuery) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalizedQuery.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Returns the cache statistics for monitoring and reporting.
     */
    public CacheStats getCacheStats() {
        return cache.getStats();
    }

    /**
     * Clears the entire query plan cache.
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Returns the current cache size.
     */
    public int getCacheSize() {
        return cache.size();
    }
}
