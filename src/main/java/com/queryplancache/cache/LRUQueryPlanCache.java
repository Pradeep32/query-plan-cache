package com.queryplancache.cache;

import com.queryplancache.plan.QueryPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * LRU (Least Recently Used) implementation of the QueryPlanCache.
 *
 * <p>Uses a {@link LinkedHashMap} in access-order mode as the underlying data structure,
 * which provides O(1) access and automatic LRU ordering. Thread safety is ensured
 * via a {@link ReadWriteLock} allowing concurrent reads with exclusive writes.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>LRU eviction when capacity is exceeded</li>
 *   <li>TTL-based expiration (lazy eviction on access)</li>
 *   <li>Schema-version-based staleness detection</li>
 *   <li>Table-level cache invalidation for DDL changes</li>
 *   <li>Thread-safe with read/write lock</li>
 *   <li>Comprehensive cache statistics</li>
 * </ul>
 */
public class LRUQueryPlanCache implements QueryPlanCache {

    private static final Logger log = LoggerFactory.getLogger(LRUQueryPlanCache.class);

    private final int maxSize;
    private final long ttlMillis;
    private final LinkedHashMap<String, CacheEntry> cache;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final CacheStats stats = new CacheStats();
    private final AtomicLong schemaVersion = new AtomicLong(0);

    /**
     * Creates a new LRU query plan cache.
     *
     * @param maxSize   maximum number of entries before LRU eviction
     * @param ttlMillis time-to-live for each entry in milliseconds (0 = no expiration)
     */
    public LRUQueryPlanCache(int maxSize, long ttlMillis) {
        this.maxSize = maxSize;
        this.ttlMillis = ttlMillis;

        // LinkedHashMap with access-order=true provides LRU behavior.
        // Override removeEldestEntry to auto-evict when capacity is exceeded.
        this.cache = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                boolean shouldRemove = size() > LRUQueryPlanCache.this.maxSize;
                if (shouldRemove) {
                    log.debug("LRU eviction: removing cache entry for key {}", eldest.getKey());
                    stats.recordEviction();
                }
                return shouldRemove;
            }
        };
    }

    /**
     * Creates a cache with default settings: 1000 entries, 30 min TTL.
     */
    public LRUQueryPlanCache() {
        this(1000, 30 * 60 * 1000L); // 1000 entries, 30 minutes TTL
    }

    @Override
    public Optional<CacheEntry> get(String cacheKey) {
        long startNanos = System.nanoTime();

        lock.readLock().lock();
        try {
            CacheEntry entry = cache.get(cacheKey);

            if (entry == null) {
                log.debug("Cache MISS for key: {}", cacheKey);
                stats.recordMiss(0); // Plan gen time will be recorded separately
                return Optional.empty();
            }

            // Check TTL expiration
            if (ttlMillis > 0 && entry.isExpired(ttlMillis)) {
                log.debug("Cache entry EXPIRED for key: {}", cacheKey);
                // Upgrade to write lock to remove expired entry
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    cache.remove(cacheKey);
                    stats.recordEviction();
                    lock.readLock().lock(); // Downgrade back to read lock
                } finally {
                    lock.writeLock().unlock();
                }
                stats.recordMiss(0);
                return Optional.empty();
            }

            // Check schema staleness
            if (entry.isStale(schemaVersion.get())) {
                log.debug("Cache entry STALE (schema changed) for key: {}", cacheKey);
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    cache.remove(cacheKey);
                    stats.recordEviction();
                    lock.readLock().lock();
                } finally {
                    lock.writeLock().unlock();
                }
                stats.recordMiss(0);
                return Optional.empty();
            }

            // Cache HIT — record access
            entry.recordAccess();
            long elapsedNanos = System.nanoTime() - startNanos;
            stats.recordHit(elapsedNanos);
            log.debug("Cache HIT for key: {} (hit count: {})", cacheKey, entry.getHitCount());

            return Optional.of(entry);

        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void put(String cacheKey, QueryPlan queryPlan) {
        lock.writeLock().lock();
        try {
            CacheEntry entry = new CacheEntry(cacheKey, queryPlan, schemaVersion.get());
            cache.put(cacheKey, entry);
            log.debug("Cached plan for key: {} (cache size: {})", cacheKey, cache.size());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Invalidates all cached plans referencing the specified table.
     * Called when DDL statements (ALTER TABLE, CREATE INDEX, etc.) are detected.
     * Also increments the global schema version.
     */
    @Override
    public int invalidateByTable(String tableName) {
        lock.writeLock().lock();
        try {
            String normalizedTable = tableName.toLowerCase();
            int removedCount = 0;

            Iterator<Map.Entry<String, CacheEntry>> it = cache.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, CacheEntry> entry = it.next();
                QueryPlan plan = entry.getValue().getQueryPlan();
                if (plan.getReferencedTables() != null
                        && plan.getReferencedTables().contains(normalizedTable)) {
                    it.remove();
                    removedCount++;
                }
            }

            stats.recordInvalidation(removedCount);

            log.info("Invalidated {} cache entries for table '{}'. Removed {} plans.",
                    removedCount, tableName, removedCount);

            return removedCount;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            int size = cache.size();
            cache.clear();
            schemaVersion.incrementAndGet();
            log.info("Cache cleared. Removed {} entries. Schema version: {}", size, schemaVersion.get());
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return cache.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public CacheStats getStats() {
        return stats;
    }

    public long getSchemaVersion() {
        return schemaVersion.get();
    }
}
