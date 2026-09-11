package com.katalan.core.logging;

/**
 * Runtime sink for the per-statement step records emitted by Test Listener
 * methods instrumented by {@link ListenerStepTracingCustomizer}.
 *
 * <p>Listener bodies used to be logged by walking the source AST and emitting
 * every statement found in the method <em>before</em> the method ran. That
 * ignored control flow: an untaken {@code if} branch, a {@code catch} block
 * that never fired, and everything after an early {@code return} were all
 * reported as executed steps. The instrumented call sites in this class are
 * reached only when the statement really runs, so the report now mirrors the
 * actual execution path.</p>
 *
 * <p>Tracing is active only between {@link #begin()} and {@link #end()}, which
 * {@code TestListenerRegistry} brackets around each lifecycle invocation. A
 * listener helper called from ordinary test code therefore stays silent.</p>
 */
public final class ListenerStepTracer {

    /**
     * Upper bound on step records emitted for a single listener invocation.
     * Loops inside a listener now log every iteration (as they should), so a
     * runaway loop could otherwise flood execution0.log.
     */
    private static final int MAX_STEPS_PER_INVOCATION = 2000;

    /** {@code [nextStepIndex, activeDepth]} for the current thread, or null when off. */
    private static final ThreadLocal<int[]> STATE = new ThreadLocal<>();

    private ListenerStepTracer() {}

    /** Start (or re-enter) tracing for the current thread. */
    public static void begin() {
        int[] state = STATE.get();
        if (state == null) {
            STATE.set(new int[]{1, 1});
        } else {
            state[1]++;
        }
    }

    /** Stop tracing for the current thread once the outermost invocation ends. */
    public static void end() {
        int[] state = STATE.get();
        if (state == null) {
            return;
        }
        if (--state[1] <= 0) {
            STATE.remove();
        }
    }

    /**
     * Record one executed listener statement. Called from instrumented Groovy
     * bytecode, so it must never throw.
     *
     * @param actionText human-readable label for the statement
     * @param line       1-based source line, or 0 when unknown
     */
    public static void step(String actionText, int line) {
        int[] state = STATE.get();
        if (state == null) {
            return;
        }
        int stepIndex = state[0];
        if (stepIndex > MAX_STEPS_PER_INVOCATION) {
            return;
        }
        state[0] = stepIndex + 1;
        try {
            XmlKeywordLogger kwLogger = XmlKeywordLogger.getInstance();
            kwLogger.startKeyword(actionText, line > 0 ? Integer.valueOf(line) : null, Integer.valueOf(stepIndex));
            kwLogger.endKeyword(actionText);
        } catch (Throwable ignored) {
            // Step logging is never allowed to break listener execution.
        }
    }
}
