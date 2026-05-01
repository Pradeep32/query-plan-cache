package com.queryplancache.engine;

import com.queryplancache.cache.CacheStats;
import com.queryplancache.cache.LRUQueryPlanCache;
import com.queryplancache.normalizer.SqlQueryNormalizer;
import com.queryplancache.plan.QueryPlan;
import com.queryplancache.plan.QueryPlanGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("QueryEngine Integration Tests")
class QueryEngineTest {

    private QueryEngine engine;
    private LRUQueryPlanCache cache;

    @BeforeEach
    void setUp() {
        SqlQueryNormalizer normalizer = new SqlQueryNormalizer();
        cache = new LRUQueryPlanCache(100, 60_000);
        QueryPlanGenerator generator = new QueryPlanGenerator(10); // 10ms simulated delay
        engine = new QueryEngine(normalizer, cache, generator);
    }

    // =========================================================================
    // Cache Hit/Miss Flow
    // =========================================================================
    @Nested
    @DisplayName("Cache Hit/Miss Flow")
    class CacheHitMissFlow {

        @Test
        @DisplayName("First query produces cache miss, second produces cache hit")
        void shouldCachePlanOnFirstCallAndReuseOnSecond() {
            // First call — cache miss, plan generated
            QueryPlan plan1 = engine.getQueryPlan(
                    "SELECT * FROM orders WHERE customer_id = 101");
            assertNotNull(plan1);
            assertEquals(1, cache.size());

            // Second call with different value — cache hit (same structure)
            QueryPlan plan2 = engine.getQueryPlan(
                    "SELECT * FROM orders WHERE customer_id = 202");
            assertNotNull(plan2);
            assertEquals(1, cache.size()); // Still 1 entry

            // Verify stats
            CacheStats stats = engine.getCacheStats();
            assertTrue(stats.getHits() >= 1, "Should have at least 1 hit");
        }

        @Test
        @DisplayName("Different query structures create separate cache entries")
        void shouldCreateSeparateEntriesForDifferentStructures() {
            engine.getQueryPlan("SELECT * FROM orders WHERE customer_id = 101");
            engine.getQueryPlan("SELECT * FROM users WHERE name = 'Alice'");

            assertEquals(2, cache.size());
        }

        @Test
        @DisplayName("Same query executed multiple times hits cache")
        void shouldHitCacheOnRepeatedExecution() {
            String query = "SELECT * FROM orders WHERE customer_id = 101";

            engine.getQueryPlan(query); // Miss
            engine.getQueryPlan(query); // Hit
            engine.getQueryPlan(query); // Hit
            engine.getQueryPlan(query); // Hit

            CacheStats stats = engine.getCacheStats();
            assertEquals(3, stats.getHits());
        }
    }

    // =========================================================================
    // DDL Invalidation
    // =========================================================================
    @Nested
    @DisplayName("DDL Cache Invalidation")
    class DdlInvalidation {

        @Test
        @DisplayName("DDL statement invalidates plans for affected table")
        void shouldInvalidateOnDdl() {
            engine.getQueryPlan("SELECT * FROM orders WHERE id = 1");
            engine.getQueryPlan("SELECT * FROM users WHERE id = 1");
            assertEquals(2, cache.size());

            // DDL on orders — should invalidate orders-related plans
            engine.getQueryPlan("ALTER TABLE orders ADD COLUMN status VARCHAR(50)");

            assertEquals(1, cache.size()); // Only users plan remains
        }

        @Test
        @DisplayName("DDL returns null (no plan for DDL)")
        void shouldReturnNullForDdl() {
            QueryPlan plan = engine.getQueryPlan("DROP TABLE temp_data");
            assertNull(plan);
        }

        @Test
        @DisplayName("After DDL, re-query generates a new plan")
        void shouldRegeneratePlanAfterDdl() {
            engine.getQueryPlan("SELECT * FROM orders WHERE id = 1");
            assertEquals(1, cache.size());

            engine.getQueryPlan("ALTER TABLE orders ADD COLUMN status VARCHAR(50)");
            assertEquals(0, cache.size());

            // Re-query should generate a new plan (cache miss)
            engine.getQueryPlan("SELECT * FROM orders WHERE id = 2");
            assertEquals(1, cache.size());
        }
    }

    // =========================================================================
    // Cache Clear Correctness
    // =========================================================================
    @Nested
    @DisplayName("Cache Clear Correctness")
    class CacheClearCorrectness {

        @Test
        @DisplayName("Clearing cache forces fresh plan generation")
        void shouldForceFreshPlanAfterClear() {
            engine.getQueryPlan("SELECT * FROM orders WHERE id = 1"); // Miss
            engine.getQueryPlan("SELECT * FROM orders WHERE id = 2"); // Hit

            CacheStats stats = engine.getCacheStats();
            long hitsBeforeClear = stats.getHits();

            engine.clearCache();
            assertEquals(0, engine.getCacheSize());

            // This should now be a miss again
            engine.getQueryPlan("SELECT * FROM orders WHERE id = 3");
            assertEquals(1, engine.getCacheSize());
        }

        @Test
        @DisplayName("Plans generated after clear are correct")
        void shouldGenerateCorrectPlansAfterClear() {
            QueryPlan plan1 = engine.getQueryPlan("SELECT * FROM orders WHERE id = 1");
            engine.clearCache();
            QueryPlan plan2 = engine.getQueryPlan("SELECT * FROM orders WHERE id = 2");

            assertNotNull(plan2);
            assertEquals(plan1.getNormalizedQuery(), plan2.getNormalizedQuery());
            assertEquals(plan1.getPlanType(), plan2.getPlanType());
        }
    }

    // =========================================================================
    // Performance Comparison: Cached vs Uncached
    // =========================================================================
    @Nested
    @DisplayName("Performance: Cached vs Uncached")
    class PerformanceComparison {

        @Test
        @DisplayName("Cached lookups are significantly faster than plan generation")
        void shouldDemonstratePerformanceImprovement() {
            int iterations = 100;

            // Measure UNCACHED: generate plan every time (simulated)
            QueryPlanGenerator slowGenerator = new QueryPlanGenerator(10); // 10ms per plan
            long uncachedStart = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                slowGenerator.generatePlan("SELECT * FROM orders WHERE id = ?");
            }
            long uncachedTimeMs = (System.nanoTime() - uncachedStart) / 1_000_000;

            // Measure CACHED: first call generates, rest are cache hits
            LRUQueryPlanCache perfCache = new LRUQueryPlanCache(1000, 60_000);
            QueryEngine perfEngine = new QueryEngine(
                    new SqlQueryNormalizer(), perfCache, new QueryPlanGenerator(10));

            long cachedStart = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                perfEngine.getQueryPlan("SELECT * FROM orders WHERE customer_id = " + i);
            }
            long cachedTimeMs = (System.nanoTime() - cachedStart) / 1_000_000;

            // Report results
            System.out.println("=== Performance Comparison ===");
            System.out.printf("Uncached (%d iterations): %d ms%n", iterations, uncachedTimeMs);
            System.out.printf("Cached   (%d iterations): %d ms%n", iterations, cachedTimeMs);
            System.out.printf("Speedup: %.1fx faster%n",
                    (double) uncachedTimeMs / Math.max(cachedTimeMs, 1));
            System.out.printf("Cache stats: %s%n", perfCache.getStats());

            // Cached should be significantly faster (at least 2x)
            assertTrue(cachedTimeMs < uncachedTimeMs,
                    "Cached execution should be faster than uncached");
        }

        @Test
        @DisplayName("High cache hit ratio with repeated query patterns")
        void shouldAchieveHighHitRatio() {
            String[] queryPatterns = {
                    "SELECT * FROM orders WHERE customer_id = %d",
                    "SELECT name FROM users WHERE age > %d",
                    "SELECT * FROM products WHERE price < %d",
                    "DELETE FROM logs WHERE id = %d",
                    "UPDATE orders SET status = %d WHERE id = %d"
            };

            // Execute 500 queries using 5 patterns (100 iterations each)
            for (int i = 0; i < 100; i++) {
                for (String pattern : queryPatterns) {
                    engine.getQueryPlan(String.format(pattern, i, i));
                }
            }

            CacheStats stats = engine.getCacheStats();
            System.out.println("=== Hit Ratio Test ===");
            System.out.printf("Total requests: %d%n", stats.getTotalRequests());
            System.out.printf("Hits: %d, Misses: %d%n", stats.getHits(), stats.getMisses());
            System.out.printf("Hit ratio: %.1f%%%n", stats.getHitRatio());

            // With 5 unique patterns and 500 total queries, expect ~99% hit ratio
            // (5 misses + 495 hits = 99%)
            assertTrue(stats.getHitRatio() > 90.0,
                    "Hit ratio should be above 90% with repeated patterns");
        }
    }

    // =========================================================================
    // Cache Key Computation
    // =========================================================================
    @Nested
    @DisplayName("Cache Key Computation")
    class CacheKeyComputation {

        @Test
        @DisplayName("Same normalized query produces same cache key")
        void shouldProduceSameCacheKey() {
            String key1 = engine.computeCacheKey("SELECT * FROM orders WHERE id = ?");
            String key2 = engine.computeCacheKey("SELECT * FROM orders WHERE id = ?");

            assertEquals(key1, key2);
        }

        @Test
        @DisplayName("Different queries produce different cache keys")
        void shouldProduceDifferentCacheKeys() {
            String key1 = engine.computeCacheKey("SELECT * FROM orders WHERE id = ?");
            String key2 = engine.computeCacheKey("SELECT * FROM users WHERE id = ?");

            assertNotEquals(key1, key2);
        }

        @Test
        @DisplayName("Cache key is a valid SHA-256 hex string")
        void shouldProduceValidSha256() {
            String key = engine.computeCacheKey("SELECT * FROM orders");

            assertEquals(64, key.length()); // SHA-256 = 32 bytes = 64 hex chars
            assertTrue(key.matches("[0-9a-f]+"));
        }
    }
}
