package com.katalan.core.cache;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe hit / miss / error counter for a single named cache area.
 *
 * Instances are created by {@link RuntimeCacheManager#registerStats(String)}
 * and remain alive for the lifetime of the JVM process (same as the caches
 * they describe).  No reset API is exposed intentionally — stats reflect the
 * full process run so summary logs at shutdown are accurate.
 */
public final class CacheStats {

    private final String name;
    private final AtomicLong hits   = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();

    CacheStats(String name) {
        this.name = name;
    }

    /** Record a successful cache hit. */
    public void recordHit()   { hits.incrementAndGet(); }

    /** Record a cache miss (computation performed and result may be stored). */
    public void recordMiss()  { misses.incrementAndGet(); }

    /**
     * Record a cache error (e.g. an entry was found but could not be used;
     * the caller must fall back to the non-cached code path).
     */
    public void recordError() { errors.incrementAndGet(); }

    public String getName()  { return name; }
    public long   getHits()  { return hits.get(); }
    public long   getMisses(){ return misses.get(); }
    public long   getErrors(){ return errors.get(); }

    /** Total number of lookup attempts (hits + misses). */
    public long getTotal()   { return hits.get() + misses.get(); }

    /**
     * Returns a short human-readable summary string, for example:
     * {@code "preprocessed-source: 8 hits / 2 misses / 0 errors"}
     */
    @Override
    public String toString() {
        return String.format("%s: %d hits / %d misses / %d errors",
                name, hits.get(), misses.get(), errors.get());
    }
}
