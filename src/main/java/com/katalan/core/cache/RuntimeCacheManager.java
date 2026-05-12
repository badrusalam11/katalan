package com.katalan.core.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central in-memory cache registry for a single JVM run.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>Singleton — one instance per JVM process (same lifetime as the caches
 *       themselves).</li>
 *   <li>Each individual cache (e.g. {@link PreprocessedSourceCache}) calls
 *       {@link #registerStats(String)} during its own static initialiser and
 *       stores the returned {@link CacheStats} object.  This manager therefore
 *       never needs to know the concrete cache implementation.</li>
 *   <li>{@link #logSummary()} is called once at the end of a suite run by
 *       {@code KatalanEngine} to emit a concise INFO-level summary.</li>
 * </ul>
 *
 * <h3>Thread safety</h3>
 * {@link CopyOnWriteArrayList} is used for the stats registry because
 * registrations only happen at class-initialisation time (effectively
 * single-threaded) while reads happen later.
 *
 * <h3>Lifecycle</h3>
 * This cache is NOT expected to survive across separate CLI runs.  Each
 * {@code java -jar … run} invocation starts from scratch because the JVM
 * process is new.  All caches are therefore process-scoped.
 */
public final class RuntimeCacheManager {

    private static final Logger logger = LoggerFactory.getLogger(RuntimeCacheManager.class);

    // Singleton — one per JVM process.
    private static final RuntimeCacheManager INSTANCE = new RuntimeCacheManager();

    /** Ordered list of all registered cache stats areas. */
    private final List<CacheStats> registry = new CopyOnWriteArrayList<>();

    private RuntimeCacheManager() {}

    /** Returns the singleton manager. */
    public static RuntimeCacheManager getInstance() {
        return INSTANCE;
    }

    /**
     * Register a new cache area and return its {@link CacheStats} counter.
     * Called once from the static initialiser of each cache class.
     *
     * @param name Short human-readable cache name, e.g. {@code "preprocessed-source"}.
     * @return A new {@link CacheStats} instance wired into this manager.
     */
    public CacheStats registerStats(String name) {
        CacheStats stats = new CacheStats(name);
        registry.add(stats);
        return stats;
    }

    /**
     * Emit a concise INFO-level cache summary (hit/miss per area) and a more
     * detailed DEBUG-level breakdown.  Called once at the end of a suite run.
     *
     * <p>Example INFO output:
     * <pre>
     * [cache] Runtime cache summary (process-scoped, not persisted):
     * [cache]   preprocessed-source : 8 hits / 2 misses / 0 errors
     * [cache]   keyword-class        : 24 hits / 6 misses / 0 errors
     * [cache]   compiled-script      : 12 hits / 8 misses / 0 errors
     * </pre>
     */
    public void logSummary() {
        if (registry.isEmpty()) {
            return;
        }

        long totalHits   = registry.stream().mapToLong(CacheStats::getHits).sum();
        long totalMisses = registry.stream().mapToLong(CacheStats::getMisses).sum();
        long totalErrors = registry.stream().mapToLong(CacheStats::getErrors).sum();

        logger.info("[cache] Runtime cache summary (process-scoped, not persisted):");
        for (CacheStats s : registry) {
            long total = s.getTotal();
            if (total == 0 && s.getErrors() == 0) {
                // Cache was registered but never accessed — skip in INFO to reduce noise.
                logger.debug("[cache]   {} — not accessed this run", s.getName());
                continue;
            }
            logger.info("[cache]   {}", s);
        }
        logger.info("[cache] Total: {} hits / {} misses / {} errors",
                totalHits, totalMisses, totalErrors);

        // DEBUG: more context about what each cache does
        logger.debug("[cache] Cache descriptions:");
        logger.debug("[cache]   preprocessed-source — avoids re-copying + re-preprocessing .groovy dirs to temp");
        logger.debug("[cache]   keyword-class        — avoids re-compiling keyword .groovy files in BDD mode");
        logger.debug("[cache]   compiled-script      — avoids re-compiling Groovy test scripts for repeated callTestCase");
    }
}
