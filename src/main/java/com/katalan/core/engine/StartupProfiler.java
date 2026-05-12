package com.katalan.core.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Lightweight startup profiler for Katalan engine initialization diagnostics.
 *
 * <p>Two log tiers are emitted:</p>
 * <ul>
 *   <li><b>INFO</b> – concise startup summary: total duration, slow-phase top-N,
 *       and scalar counters (JAR counts, listener counts, …).</li>
 *   <li><b>DEBUG</b> – full per-phase breakdown, memory snapshots, and any
 *       per-item details (shown only when {@code --debug} is passed on the CLI,
 *       i.e. when {@link #setDebugEnabled(boolean) setDebugEnabled(true)} has
 *       been called before engine startup).</li>
 * </ul>
 *
 * <p>This class has no dependencies beyond SLF4J which is already on the classpath.
 * It is not thread-safe (startup is inherently sequential).</p>
 */
public final class StartupProfiler {

    private static final Logger logger = LoggerFactory.getLogger(StartupProfiler.class);

    /** Minimum phase duration (ms) before it appears in the INFO summary. */
    private static final long INFO_PHASE_THRESHOLD_MS = 5L;

    /** Maximum number of slowest phases listed in the INFO summary. */
    private static final int TOP_SLOW_PHASES = 6;

    // ------------------------------------------------------------------
    // Static debug flag (set once by CLI before engine construction)
    // ------------------------------------------------------------------

    private static volatile boolean debugEnabled = false;

    /**
     * Enable per-item DEBUG diagnostics for all profiler instances.
     * Call this before constructing {@code KatalanEngine} when {@code --debug}
     * is present on the command line.
     */
    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    /** Returns {@code true} if debug diagnostics are active. */
    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    // ------------------------------------------------------------------
    // Instance state
    // ------------------------------------------------------------------

    /** Label used in every log line (e.g. "engine-init"). */
    private final String label;

    /** Absolute start time of this profiler (millis). */
    private final long createdAtMs = System.currentTimeMillis();

    /** Phase start timestamps, keyed by phase name (insertion order). */
    private final Map<String, Long> phaseStarts = new LinkedHashMap<>();

    /** Completed phase durations in millis, keyed by phase name (insertion order). */
    private final Map<String, Long> phaseDurations = new LinkedHashMap<>();

    /** Scalar counters (JAR counts, listener counts, etc.). */
    private final Map<String, Long> counters = new LinkedHashMap<>();

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------

    /**
     * Create a new profiler with the given label.
     *
     * @param label short label shown in every log line (e.g. {@code "engine-init"})
     */
    public StartupProfiler(String label) {
        this.label = label;
    }

    // ------------------------------------------------------------------
    // Phase timing API
    // ------------------------------------------------------------------

    /**
     * Mark the start of a named phase.
     * Must be followed by a matching {@link #end(String)} call.
     */
    public void begin(String phase) {
        phaseStarts.put(phase, System.currentTimeMillis());
    }

    /**
     * Mark the end of a named phase.
     *
     * @return duration in milliseconds, or {@code -1} if {@link #begin(String)}
     *         was never called for this phase name
     */
    public long end(String phase) {
        Long start = phaseStarts.get(phase);
        if (start == null) return -1L;
        long d = System.currentTimeMillis() - start;
        phaseDurations.put(phase, d);
        logger.debug("[startup:{}] phase '{}' completed in {} ms", label, phase, d);
        return d;
    }

    /**
     * Time a {@link Supplier} under the given phase name and return its result.
     * Equivalent to {@code begin(phase); T r = action.get(); end(phase); return r;}.
     */
    public <T> T time(String phase, Supplier<T> action) {
        begin(phase);
        T result = action.get();
        end(phase);
        return result;
    }

    /**
     * Time a {@link Runnable} under the given phase name.
     */
    public void time(String phase, Runnable action) {
        begin(phase);
        action.run();
        end(phase);
    }

    // ------------------------------------------------------------------
    // Memory snapshots (DEBUG only)
    // ------------------------------------------------------------------

    /**
     * Return current heap used in MB (fast, no GC triggered).
     */
    public long heapUsedMb() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
    }

    /**
     * Log a heap-usage snapshot at DEBUG level.
     * Does nothing unless {@link #isDebugEnabled()} is {@code true}.
     *
     * @param tag short description of the snapshot point (e.g. "before-initialize")
     */
    public void logMemorySnapshot(String tag) {
        if (debugEnabled) {
            long usedMb = heapUsedMb();
            long maxMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
            logger.debug("[startup:{}] memory @ {}: {} MB used / {} MB max",
                    label, tag, usedMb, maxMb);
        }
    }

    // ------------------------------------------------------------------
    // Counter API
    // ------------------------------------------------------------------

    /**
     * Record a scalar counter value (overwrites any previous value for the key).
     */
    public void count(String key, long value) {
        counters.put(key, value);
    }

    /**
     * Increment a counter by 1 (starting from 0 on first call).
     */
    public void increment(String key) {
        counters.merge(key, 1L, Long::sum);
    }

    // ------------------------------------------------------------------
    // Summary logging
    // ------------------------------------------------------------------

    /**
     * Emit the startup summary.
     *
     * <p>At INFO level: total elapsed time, scalar counters, and the top-N
     * slowest phases (those above {@value #INFO_PHASE_THRESHOLD_MS} ms).</p>
     * <p>At DEBUG level (only when {@link #isDebugEnabled()}): full phase
     * breakdown with all durations in insertion order.</p>
     *
     * <p>Call this once at the end of the initialization sequence.</p>
     */
    public void logSummary() {
        long totalMs = System.currentTimeMillis() - createdAtMs;

        // --- INFO: one-line headline ---
        StringBuilder headline = new StringBuilder();
        headline.append("[STARTUP:").append(label).append("] ready in ").append(totalMs).append(" ms");
        if (!counters.isEmpty()) {
            counters.forEach((k, v) -> headline.append(" | ").append(k).append('=').append(v));
        }
        logger.info(headline.toString());

        // --- INFO: top-N slowest phases ---
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(phaseDurations.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        int shown = 0;
        for (Map.Entry<String, Long> e : sorted) {
            if (e.getValue() >= INFO_PHASE_THRESHOLD_MS && shown < TOP_SLOW_PHASES) {
                logger.info("[STARTUP:{}]   {}", label,
                        String.format("%-44s %5d ms", e.getKey(), e.getValue()));
                shown++;
            }
        }

        // --- DEBUG: full breakdown ---
        if (debugEnabled) {
            logger.debug("[STARTUP:{}] === full phase breakdown (insertion order) ===", label);
            phaseDurations.forEach((phase, d) ->
                    logger.debug("[STARTUP:{}]   {} ms  {}",
                            label, String.format("%5d", d), phase));
            if (!counters.isEmpty()) {
                logger.debug("[STARTUP:{}] === counters ===", label);
                counters.forEach((k, v) ->
                        logger.debug("[STARTUP:{}]   {}={}", label, k, v));
            }
        }
    }
}
