package com.katalan.core.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for the results of
 * {@link com.katalan.core.compat.GroovySourcePreprocessor#createPreprocessedCopy(Path, String)}.
 *
 * <h3>Why this cache is safe</h3>
 * {@code createPreprocessedCopy} copies a source directory to a temp directory after
 * applying Groovy-4 source patches.  The output is a pure function of:
 * <ol>
 *   <li>The source directory path.</li>
 *   <li>The maximum {@code lastModified} timestamp of any {@code .groovy} file
 *       inside that directory (used as a change-detection proxy).</li>
 * </ol>
 * If neither changes between calls, the temp directory can be reused as-is.
 * Each entry stores the temp {@link Path} so the same directory object is
 * returned to all callers — they only add it to a classloader URL list, which
 * is idempotent.
 *
 * <h3>Invalidation</h3>
 * A cache entry is invalidated (and evicted) when the max {@code lastModified}
 * of {@code .groovy} files in the source directory changes relative to the value
 * stored at insertion time.  Within one JVM run source files are not expected to
 * change; this check is a safety net.
 *
 * <h3>Error handling</h3>
 * All cache operations are best-effort.  Any exception evicts the entry and
 * lets the caller fall through to the original non-cached code path.
 *
 * <h3>Memory</h3>
 * Entries are small (one {@link Path} + one {@code long} per unique source
 * directory).  In practice there are ≤ 2 unique source directories per project
 * (Keywords/ and Include/scripts/groovy/), so the cache never grows large.
 */
public final class PreprocessedSourceCache {

    private static final Logger logger = LoggerFactory.getLogger(PreprocessedSourceCache.class);

    /** Stats registered with the central manager for the end-of-run summary. */
    public static final CacheStats STATS =
            RuntimeCacheManager.getInstance().registerStats("preprocessed-source");

    /** Composite cache key: (srcDir absolute path, tagName). */
    private static final class CacheKey {
        final String srcDir;
        final String tagName;

        CacheKey(Path srcDir, String tagName) {
            this.srcDir  = srcDir.toAbsolutePath().normalize().toString();
            this.tagName = tagName == null ? "" : tagName;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CacheKey)) return false;
            CacheKey k = (CacheKey) o;
            return srcDir.equals(k.srcDir) && tagName.equals(k.tagName);
        }

        @Override
        public int hashCode() {
            return 31 * srcDir.hashCode() + tagName.hashCode();
        }
    }

    /** Cached entry: temp dir path + the max lastModified at insertion time. */
    private static final class CacheEntry {
        final Path   tempDir;
        final long   maxLastModified;
        final long   insertedAtMs;

        CacheEntry(Path tempDir, long maxLastModified) {
            this.tempDir         = tempDir;
            this.maxLastModified = maxLastModified;
            this.insertedAtMs    = System.currentTimeMillis();
        }
    }

    private static final ConcurrentHashMap<CacheKey, CacheEntry> MAP =
            new ConcurrentHashMap<>();

    private PreprocessedSourceCache() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Look up a previously cached preprocessed directory.
     *
     * @param srcDir  Source directory that was preprocessed.
     * @param tagName Tag name used when creating the temp dir (e.g. "keywords").
     * @return The cached temp {@link Path}, or {@code null} on miss / invalid.
     */
    public static Path get(Path srcDir, String tagName) {
        CacheKey key = new CacheKey(srcDir, tagName);
        CacheEntry entry = MAP.get(key);
        if (entry == null) {
            STATS.recordMiss();
            logger.debug("[cache] preprocessed-source MISS  key={}:{}", tagName, srcDir.getFileName());
            return null;
        }

        // Validate: check that the temp dir still exists and the source hasn't changed.
        try {
            if (!Files.exists(entry.tempDir)) {
                logger.debug("[cache] preprocessed-source INVALID (temp dir gone) key={}:{}", tagName, srcDir.getFileName());
                MAP.remove(key);
                STATS.recordMiss();
                return null;
            }
            long currentMax = maxLastModified(srcDir);
            if (currentMax > entry.maxLastModified) {
                logger.debug("[cache] preprocessed-source INVALIDATED (source changed) key={}:{}  old={} new={}",
                        tagName, srcDir.getFileName(), entry.maxLastModified, currentMax);
                MAP.remove(key);
                STATS.recordMiss();
                return null;
            }
        } catch (Exception e) {
            // Validation error → conservatively evict and return miss.
            logger.debug("[cache] preprocessed-source validation error, evicting key={}:{} — {}",
                    tagName, srcDir.getFileName(), e.getMessage());
            MAP.remove(key);
            STATS.recordError();
            return null;
        }

        STATS.recordHit();
        long ageMs = System.currentTimeMillis() - entry.insertedAtMs;
        logger.debug("[cache] preprocessed-source HIT   key={}:{} age={}ms",
                tagName, srcDir.getFileName(), ageMs);
        return entry.tempDir;
    }

    /**
     * Store a newly created preprocessed temp directory in the cache.
     *
     * @param srcDir       Original source directory.
     * @param tagName      Tag name (e.g. "keywords").
     * @param tempDir      The temp directory created by the preprocessor.
     * @param maxLastMod   Max {@code lastModified} of {@code .groovy} files in srcDir at creation time.
     */
    public static void put(Path srcDir, String tagName, Path tempDir, long maxLastMod) {
        CacheKey key = new CacheKey(srcDir, tagName);
        MAP.put(key, new CacheEntry(tempDir, maxLastMod));
        logger.debug("[cache] preprocessed-source STORED key={}:{} tempDir={}",
                tagName, srcDir.getFileName(), tempDir.getFileName());
    }

    /**
     * Compute the maximum {@code lastModified} timestamp of all {@code .groovy}
     * files directly under {@code dir} and its subdirectories.
     * Returns {@code 0} if the directory does not exist or is empty.
     */
    public static long maxLastModified(Path dir) throws IOException {
        if (!Files.exists(dir)) return 0L;
        return Files.walk(dir)
                .filter(p -> !Files.isDirectory(p) && p.toString().endsWith(".groovy"))
                .mapToLong(p -> {
                    try { return Files.getLastModifiedTime(p).toMillis(); }
                    catch (IOException e) { return 0L; }
                })
                .max()
                .orElse(0L);
    }
}
