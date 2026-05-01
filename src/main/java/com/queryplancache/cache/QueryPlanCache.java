package com.queryplancache.cache;

import com.queryplancache.plan.QueryPlan;

import java.util.Optional;

/**
 * Interface for query plan cache implementations.
 * Defines the contract for storing, retrieving, and invalidating cached query plans.
 */
public interface QueryPlanCache {

    /**
     * Looks up a cached query plan by its cache key (SHA-256 hash of normalized query).
     *
     * @param cacheKey the cache key derived from the normalized query
     * @return an Optional containing the CacheEntry if found and valid, empty otherwise
     */
    Optional<CacheEntry> get(String cacheKey);

    /**
     * Stores a query plan in the cache.
     *
     * @param cacheKey the cache key
     * @param queryPlan the execution plan to cache
     */
    void put(String cacheKey, QueryPlan queryPlan);

    /**
     * Invalidates all cached plans that reference the specified table.
     * Called when DDL changes (ALTER TABLE, DROP INDEX, etc.) are detected.
     *
     * @param tableName the table whose plans should be invalidated
     * @return the number of entries invalidated
     */
    int invalidateByTable(String tableName);

    /**
     * Clears the entire cache.
     */
    void clear();

    /**
     * Returns the current number of entries in the cache.
     */
    int size();

    /**
     * Returns the cache statistics tracker.
     */
    CacheStats getStats();
}
