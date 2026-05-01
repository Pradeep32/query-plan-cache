package com.queryplancache.cache;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe statistics tracker for the query plan cache.
 * Provides hit/miss ratios and performance metrics.
 */
public class CacheStats {

    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);
    private final AtomicLong invalidations = new AtomicLong(0);
    private final AtomicLong totalLookupTimeNanos = new AtomicLong(0);
    private final AtomicLong totalPlanGenTimeNanos = new AtomicLong(0);

    public void recordHit(long lookupTimeNanos) {
        hits.incrementAndGet();
        totalLookupTimeNanos.addAndGet(lookupTimeNanos);
    }

    public void recordMiss(long planGenTimeNanos) {
        misses.incrementAndGet();
        totalPlanGenTimeNanos.addAndGet(planGenTimeNanos);
    }

    public void recordEviction() {
        evictions.incrementAndGet();
    }

    public void recordInvalidation(int count) {
        invalidations.addAndGet(count);
    }

    public long getHits() { return hits.get(); }
    public long getMisses() { return misses.get(); }
    public long getEvictions() { return evictions.get(); }
    public long getInvalidations() { return invalidations.get(); }
    public long getTotalRequests() { return hits.get() + misses.get(); }

    /**
     * Returns the cache hit ratio as a percentage (0.0 to 100.0).
     */
    public double getHitRatio() {
        long total = getTotalRequests();
        if (total == 0) return 0.0;
        return (hits.get() * 100.0) / total;
    }

    /**
     * Returns average lookup time in microseconds for cache hits.
     */
    public double getAvgHitLookupTimeMicros() {
        long h = hits.get();
        if (h == 0) return 0.0;
        return (totalLookupTimeNanos.get() / h) / 1000.0;
    }

    /**
     * Returns average plan generation time in milliseconds for cache misses.
     */
    public double getAvgMissPlanGenTimeMs() {
        long m = misses.get();
        if (m == 0) return 0.0;
        return (totalPlanGenTimeNanos.get() / m) / 1_000_000.0;
    }

    /**
     * Resets all statistics counters.
     */
    public void reset() {
        hits.set(0);
        misses.set(0);
        evictions.set(0);
        invalidations.set(0);
        totalLookupTimeNanos.set(0);
        totalPlanGenTimeNanos.set(0);
    }

    @Override
    public String toString() {
        return String.format(
                "CacheStats{hits=%d, misses=%d, hitRatio=%.1f%%, evictions=%d, "
                + "invalidations=%d, avgHitTime=%.2fus, avgMissTime=%.2fms}",
                getHits(), getMisses(), getHitRatio(), getEvictions(),
                getInvalidations(), getAvgHitLookupTimeMicros(), getAvgMissPlanGenTimeMs()
        );
    }
}
