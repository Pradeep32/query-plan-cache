package com.queryplancache.cache;

import com.queryplancache.plan.QueryPlan;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a single entry in the query plan cache.
 * Tracks metadata for TTL expiration, access patterns, and hit counting.
 */
public class CacheEntry {

    private final String cacheKey;
    private final QueryPlan queryPlan;
    private final Instant createdAt;
    private final long schemaVersion;
    private volatile Instant lastAccessedAt;
    private final AtomicLong hitCount;

    public CacheEntry(String cacheKey, QueryPlan queryPlan, long schemaVersion) {
        this.cacheKey = cacheKey;
        this.queryPlan = queryPlan;
        this.createdAt = Instant.now();
        this.lastAccessedAt = this.createdAt;
        this.hitCount = new AtomicLong(0);
        this.schemaVersion = schemaVersion;
    }

    /**
     * Records an access to this cache entry (cache hit).
     */
    public void recordAccess() {
        this.lastAccessedAt = Instant.now();
        this.hitCount.incrementAndGet();
    }

    /**
     * Checks if this entry has expired based on the given TTL.
     *
     * @param ttlMillis time-to-live in milliseconds
     * @return true if the entry has expired
     */
    public boolean isExpired(long ttlMillis) {
        return Instant.now().toEpochMilli() - createdAt.toEpochMilli() > ttlMillis;
    }

    /**
     * Checks if this entry is stale due to a schema change.
     *
     * @param currentSchemaVersion the current global schema version
     * @return true if the entry was created with an older schema version
     */
    public boolean isStale(long currentSchemaVersion) {
        return this.schemaVersion < currentSchemaVersion;
    }

    public String getCacheKey() { return cacheKey; }
    public QueryPlan getQueryPlan() { return queryPlan; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastAccessedAt() { return lastAccessedAt; }
    public long getHitCount() { return hitCount.get(); }
    public long getSchemaVersion() { return schemaVersion; }
}
