package com.katalan.core.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for compiled Groovy keyword {@link Class} objects used by
 * {@code KatalanBDDExecutor.CustomKeywordsClosure}.
 *
 * <h3>Why this cache is safe</h3>
 * In BDD mode a new {@code CustomKeywordsClosure} is created for every
 * {@code executeTestCase()} call, so the instance-level {@code keywordInstances}
 * map is discarded after each test case.  Without this cache the keyword
 * {@code .groovy} source is re-read, re-preprocessed, and re-compiled on
 * every test case that calls the same keyword — even though the compiled
 * {@code Class} object is identical.
 *
 * <p>What is cached:
 * <ul>
 *   <li>The compiled {@link Class} object (deterministic output of Groovy
 *       compilation of a specific file).  The Class is immutable after
 *       compilation.</li>
 *   <li>The absolute path of the {@code .groovy} source file that was found
 *       (avoids repeating the filesystem search).</li>
 * </ul>
 *
 * <p>What is NOT cached:
 * <ul>
 *   <li>Keyword <em>instances</em> — callers must call
 *       {@code clazz.getDeclaredConstructor().newInstance()} themselves to
 *       preserve the "fresh object per call" semantics that the existing code
 *       relies on.  Instance fields / constructor side-effects are never
 *       shared.</li>
 * </ul>
 *
 * <h3>Invalidation</h3>
 * An entry is invalidated when the {@code lastModified} timestamp of the
 * cached source file changes.  This is a safety net; in normal suite runs
 * source files do not change mid-run.
 *
 * <h3>Memory</h3>
 * One entry per distinct keyword class per project.  Typical projects have
 * ≤ 50 keyword files — negligible heap cost.  The stored Class keeps its
 * GroovyClassLoader alive, but that classloader would have stayed alive in
 * the old code anyway (until the method returned).
 */
public final class KeywordClassCache {

    private static final Logger logger = LoggerFactory.getLogger(KeywordClassCache.class);

    public static final CacheStats STATS =
            RuntimeCacheManager.getInstance().registerStats("keyword-class");

    /** Cached entry: resolved file path + compiled class + lastModified at compile time. */
    public static final class KeywordClassEntry {
        /** Absolute path to the {@code .groovy} source file that was compiled. */
        public final Path   sourceFile;
        /** The compiled Groovy class.  Callers must instantiate with {@code newInstance()}. */
        public final Class<?> clazz;
        /** File {@code lastModified} at compile time, used for invalidation. */
        final long lastModified;

        public KeywordClassEntry(Path sourceFile, Class<?> clazz, long lastModified) {
            this.sourceFile   = sourceFile;
            this.clazz        = clazz;
            this.lastModified = lastModified;
        }
    }

    /**
     * Cache map: absolute source-file path (String) → entry.
     * Key is the absolute, normalized file path string so it is stable across
     * different {@code Path} object instances.
     */
    private static final ConcurrentHashMap<String, KeywordClassEntry> MAP =
            new ConcurrentHashMap<>();

    private KeywordClassCache() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Look up a cached keyword class entry by the absolute source file path.
     *
     * @param sourceFile Absolute path to the keyword {@code .groovy} source file.
     * @return A valid {@link KeywordClassEntry}, or {@code null} on miss / stale.
     */
    public static KeywordClassEntry get(Path sourceFile) {
        String key = sourceFile.toAbsolutePath().normalize().toString();
        KeywordClassEntry entry = MAP.get(key);
        if (entry == null) {
            STATS.recordMiss();
            logger.debug("[cache] keyword-class MISS  file={}", sourceFile.getFileName());
            return null;
        }

        // Validate: check that the source file hasn't changed.
        try {
            long currentMod = Files.getLastModifiedTime(sourceFile).toMillis();
            if (currentMod != entry.lastModified) {
                logger.debug("[cache] keyword-class INVALIDATED (file changed) file={}  old={} new={}",
                        sourceFile.getFileName(), entry.lastModified, currentMod);
                MAP.remove(key);
                STATS.recordMiss();
                return null;
            }
        } catch (IOException e) {
            logger.debug("[cache] keyword-class validation error, evicting file={} — {}",
                    sourceFile.getFileName(), e.getMessage());
            MAP.remove(key);
            STATS.recordError();
            return null;
        }

        STATS.recordHit();
        logger.debug("[cache] keyword-class HIT   file={} class={}",
                sourceFile.getFileName(), entry.clazz.getSimpleName());
        return entry;
    }

    /**
     * Store a newly compiled keyword class in the cache.
     *
     * @param sourceFile   Absolute path to the keyword {@code .groovy} source file.
     * @param clazz        The compiled Groovy {@link Class}.
     * @param lastModified The {@code lastModified} of {@code sourceFile} at compile time.
     */
    public static void put(Path sourceFile, Class<?> clazz, long lastModified) {
        String key = sourceFile.toAbsolutePath().normalize().toString();
        MAP.put(key, new KeywordClassEntry(sourceFile, clazz, lastModified));
        logger.debug("[cache] keyword-class STORED file={} class={}",
                sourceFile.getFileName(), clazz.getSimpleName());
    }
}
