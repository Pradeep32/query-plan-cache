package com.queryplancache.cache;

import com.queryplancache.plan.PlanOperation;
import com.queryplancache.plan.QueryPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LRUQueryPlanCache Tests")
class LRUQueryPlanCacheTest {

    private LRUQueryPlanCache cache;

    @BeforeEach
    void setUp() {
        cache = new LRUQueryPlanCache(5, 60_000); // 5 entries, 60s TTL
    }

    private QueryPlan createTestPlan(String normalizedQuery, String... tables) {
        PlanOperation scanOp = new PlanOperation(
                "TABLE_SCAN", tables.length > 0 ? tables[0] : "test",
                List.of("*"), null, 100, 10.0);
        return new QueryPlan(normalizedQuery, "SELECT", scanOp,
                Set.of(tables), 10.0);
    }

    // =========================================================================
    // Basic Cache Operations
    // =========================================================================
    @Nested
    @DisplayName("Basic Cache Operations")
    class BasicOperations {

        @Test
        @DisplayName("Put and get a cache entry")
        void shouldPutAndGet() {
            QueryPlan plan = createTestPlan("SELECT * FROM orders WHERE id = ?", "orders");
            cache.put("key1", plan);

            Optional<CacheEntry> result = cache.get("key1");

            assertTrue(result.isPresent());
            assertEquals("SELECT * FROM orders WHERE id = ?",
                    result.get().getQueryPlan().getNormalizedQuery());
        }

        @Test
        @DisplayName("Returns empty for missing key")
        void shouldReturnEmptyForMissingKey() {
            Optional<CacheEntry> result = cache.get("nonexistent");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Returns correct cache size")
        void shouldTrackSize() {
            assertEquals(0, cache.size());

            cache.put("key1", createTestPlan("q1", "t1"));
            cache.put("key2", createTestPlan("q2", "t2"));

            assertEquals(2, cache.size());
        }

        @Test
        @DisplayName("Clear removes all entries")
        void shouldClearAllEntries() {
            cache.put("key1", createTestPlan("q1", "t1"));
            cache.put("key2", createTestPlan("q2", "t2"));

            cache.clear();

            assertEquals(0, cache.size());
            assertTrue(cache.get("key1").isEmpty());
        }
    }

    // =========================================================================
    // LRU Eviction
    // =========================================================================
    @Nested
    @DisplayName("LRU Eviction")
    class LruEviction {

        @Test
        @DisplayName("Evicts oldest entry when capacity exceeded")
        void shouldEvictOldestEntry() {
            // Fill cache to capacity (5)
            for (int i = 1; i <= 5; i++) {
                cache.put("key" + i, createTestPlan("q" + i, "t" + i));
            }
            assertEquals(5, cache.size());

            // Add one more — should evict key1 (oldest)
            cache.put("key6", createTestPlan("q6", "t6"));

            assertEquals(5, cache.size());
            assertTrue(cache.get("key1").isEmpty(), "key1 should have been evicted");
            assertTrue(cache.get("key6").isPresent(), "key6 should be present");
        }

        @Test
        @DisplayName("Accessing an entry prevents it from being evicted")
        void shouldPreserveRecentlyAccessedEntry() {
            for (int i = 1; i <= 5; i++) {
                cache.put("key" + i, createTestPlan("q" + i, "t" + i));
            }

            // Access key1 to make it "recently used"
            cache.get("key1");

            // Add new entry — should evict key2 (now the least recently used)
            cache.put("key6", createTestPlan("q6", "t6"));

            assertTrue(cache.get("key1").isPresent(), "key1 should still be present (recently accessed)");
            assertTrue(cache.get("key2").isEmpty(), "key2 should have been evicted");
        }

        @Test
        @DisplayName("Records eviction in statistics")
        void shouldTrackEvictions() {
            for (int i = 1; i <= 6; i++) {
                cache.put("key" + i, createTestPlan("q" + i, "t" + i));
            }

            assertEquals(1, cache.getStats().getEvictions());
        }
    }

    // =========================================================================
    // TTL Expiration
    // =========================================================================
    @Nested
    @DisplayName("TTL Expiration")
    class TtlExpiration {

        @Test
        @DisplayName("Entry is valid before TTL expires")
        void shouldReturnEntryBeforeTtl() {
            cache.put("key1", createTestPlan("q1", "t1"));

            Optional<CacheEntry> result = cache.get("key1");
            assertTrue(result.isPresent());
        }

        @Test
        @DisplayName("Entry expires after TTL")
        void shouldExpireAfterTtl() throws InterruptedException {
            // Create cache with very short TTL (100ms)
            LRUQueryPlanCache shortTtlCache = new LRUQueryPlanCache(10, 100);
            shortTtlCache.put("key1", createTestPlan("q1", "t1"));

            // Wait for TTL to expire
            Thread.sleep(150);

            Optional<CacheEntry> result = shortTtlCache.get("key1");
            assertTrue(result.isEmpty(), "Entry should have expired");
        }
    }

    // =========================================================================
    // Table-Level Invalidation
    // =========================================================================
    @Nested
    @DisplayName("Table-Level Invalidation")
    class TableInvalidation {

        @Test
        @DisplayName("Invalidates entries referencing a specific table")
        void shouldInvalidateByTable() {
            cache.put("key1", createTestPlan("SELECT * FROM orders WHERE id = ?", "orders"));
            cache.put("key2", createTestPlan("SELECT * FROM users WHERE id = ?", "users"));
            cache.put("key3", createTestPlan("SELECT * FROM orders WHERE status = ?", "orders"));

            int invalidated = cache.invalidateByTable("orders");

            assertEquals(2, invalidated);
            assertEquals(1, cache.size());
            assertTrue(cache.get("key1").isEmpty());
            assertTrue(cache.get("key2").isPresent());
            assertTrue(cache.get("key3").isEmpty());
        }

        @Test
        @DisplayName("Table invalidation does not increment schema version (only clear does)")
        void shouldNotIncrementSchemaVersionOnTableInvalidation() {
            long initialVersion = cache.getSchemaVersion();

            cache.invalidateByTable("orders");

            assertEquals(initialVersion, cache.getSchemaVersion(),
                    "Table-level invalidation should not bump schema version");
        }

        @Test
        @DisplayName("Full cache clear increments schema version")
        void shouldIncrementSchemaVersionOnClear() {
            long initialVersion = cache.getSchemaVersion();

            cache.clear();

            assertEquals(initialVersion + 1, cache.getSchemaVersion());
        }

        @Test
        @DisplayName("Returns 0 when no entries match the table")
        void shouldReturn0WhenNoMatch() {
            cache.put("key1", createTestPlan("q1", "users"));

            int invalidated = cache.invalidateByTable("nonexistent");

            assertEquals(0, invalidated);
            assertEquals(1, cache.size());
        }
    }

    // =========================================================================
    // Cache Statistics
    // =========================================================================
    @Nested
    @DisplayName("Cache Statistics")
    class Statistics {

        @Test
        @DisplayName("Tracks hits and misses correctly")
        void shouldTrackHitsAndMisses() {
            cache.put("key1", createTestPlan("q1", "t1"));

            cache.get("key1");  // Hit
            cache.get("key1");  // Hit
            cache.get("key2");  // Miss

            CacheStats stats = cache.getStats();
            assertEquals(2, stats.getHits());
            assertEquals(1, stats.getMisses());
        }

        @Test
        @DisplayName("Computes hit ratio correctly")
        void shouldComputeHitRatio() {
            cache.put("key1", createTestPlan("q1", "t1"));

            cache.get("key1");  // Hit
            cache.get("key1");  // Hit
            cache.get("key1");  // Hit
            cache.get("missing"); // Miss

            CacheStats stats = cache.getStats();
            assertEquals(75.0, stats.getHitRatio(), 0.1);
        }

        @Test
        @DisplayName("Hit ratio is 0 with no requests")
        void shouldReturn0HitRatioWhenEmpty() {
            assertEquals(0.0, cache.getStats().getHitRatio());
        }

        @Test
        @DisplayName("Reset clears all counters")
        void shouldResetStats() {
            cache.put("key1", createTestPlan("q1", "t1"));
            cache.get("key1");
            cache.get("missing");

            cache.getStats().reset();

            CacheStats stats = cache.getStats();
            assertEquals(0, stats.getHits());
            assertEquals(0, stats.getMisses());
            assertEquals(0.0, stats.getHitRatio());
        }
    }

    // =========================================================================
    // Hit Count Tracking
    // =========================================================================
    @Nested
    @DisplayName("Hit Count per Entry")
    class HitCountTracking {

        @Test
        @DisplayName("Tracks per-entry hit count")
        void shouldTrackPerEntryHitCount() {
            cache.put("key1", createTestPlan("q1", "t1"));

            cache.get("key1");
            cache.get("key1");
            cache.get("key1");

            Optional<CacheEntry> entry = cache.get("key1");
            assertTrue(entry.isPresent());
            assertEquals(4, entry.get().getHitCount()); // 3 + the get() above
        }
    }
}
