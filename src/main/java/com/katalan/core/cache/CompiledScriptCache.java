package com.katalan.core.cache;

import groovy.lang.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for compiled Groovy {@link Script} <em>classes</em>
 * (not instances) produced by {@code GroovyScriptExecutor.executeScript(Path)}.
 *
 * <h3>Why this cache is safe</h3>
 * A {@code Script} class is stateless — all mutable state (variable bindings)
 * lives in the {@link groovy.lang.Binding} instance that is passed at
 * execution time, not in the class itself.  Caching the {@code Class} object
 * therefore has no effect on variable-binding semantics.
 *
 * <p>On each cache <em>hit</em> a <b>fresh</b> {@code Script} instance is
 * created via
 * {@link org.codehaus.groovy.runtime.InvokerHelper#createScript(Class, groovy.lang.Binding)}
 * and given the <em>current</em> binding, exactly as {@code GroovyShell.parse()}
 * would do internally.
 *
 * <h3>Where reuse happens</h3>
 * The main value is in {@code callTestCase()} scenarios where the same shared
 * test-case script is invoked by multiple test cases in one suite run.
 * Direct top-level test-case scripts (run once per TC) still hit the cache on
 * first access; any retry of the same TC avoids recompilation.
 *
 * <h3>Invalidation</h3>
 * Keyed by absolute path + {@code lastModified} timestamp.  If the file
 * timestamp changes the entry is evicted and the script recompiled.
 *
 * <h3>Memory</h3>
 * One entry per unique script file.  Typical suites have ≤ 100 scripts —
 * negligible heap.  The stored {@code Class} keeps its classloader alive, but
 * that classloader was already going to stay alive for the duration of the
 * suite run inside the shared {@code GroovyShell}.
 */
public final class CompiledScriptCache {

    private static final Logger logger = LoggerFactory.getLogger(CompiledScriptCache.class);

    public static final CacheStats STATS =
            RuntimeCacheManager.getInstance().registerStats("compiled-script");

    private static final class CacheEntry {
        final Class<? extends Script> scriptClass;
        final long lastModified;

        CacheEntry(Class<? extends Script> scriptClass, long lastModified) {
            this.scriptClass  = scriptClass;
            this.lastModified = lastModified;
        }
    }

    /** Key: absolute, normalized file path string. */
    private static final ConcurrentHashMap<String, CacheEntry> MAP =
            new ConcurrentHashMap<>();

    private CompiledScriptCache() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Look up a cached compiled script class.
     *
     * @param scriptPath Absolute path to the {@code .groovy} script file.
     * @return The cached {@code Class<? extends Script>}, or {@code null} on miss / stale.
     */
    @SuppressWarnings("unchecked")
    public static Class<? extends Script> get(Path scriptPath) {
        String key = scriptPath.toAbsolutePath().normalize().toString();
        CacheEntry entry = MAP.get(key);
        if (entry == null) {
            STATS.recordMiss();
            logger.debug("[cache] compiled-script MISS  file={}", scriptPath.getFileName());
            return null;
        }

        // Validate: confirm the file hasn't been modified.
        try {
            long currentMod = Files.getLastModifiedTime(scriptPath).toMillis();
            if (currentMod != entry.lastModified) {
                logger.debug("[cache] compiled-script INVALIDATED (file changed) file={}",
                        scriptPath.getFileName());
                MAP.remove(key);
                STATS.recordMiss();
                return null;
            }
        } catch (IOException e) {
            logger.debug("[cache] compiled-script validation error, evicting file={} — {}",
                    scriptPath.getFileName(), e.getMessage());
            MAP.remove(key);
            STATS.recordError();
            return null;
        }

        STATS.recordHit();
        logger.debug("[cache] compiled-script HIT   file={}", scriptPath.getFileName());
        return entry.scriptClass;
    }

    /**
     * Store a compiled script class.
     *
     * @param scriptPath   Absolute path to the source file.
     * @param scriptClass  The compiled {@code Class<? extends Script>}.
     * @param lastModified The {@code lastModified} of {@code scriptPath} at compile time.
     */
    public static void put(Path scriptPath,
                           Class<? extends Script> scriptClass,
                           long lastModified) {
        String key = scriptPath.toAbsolutePath().normalize().toString();
        MAP.put(key, new CacheEntry(scriptClass, lastModified));
        logger.debug("[cache] compiled-script STORED file={}", scriptPath.getFileName());
    }
}
