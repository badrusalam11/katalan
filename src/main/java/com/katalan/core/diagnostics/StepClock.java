package com.katalan.core.diagnostics;

import java.util.Set;

/**
 * Remembers when keywords start, so failure diagnostics can tell requests caused by the failing step
 * apart from background requests of the page (docs/FAILURE_DIAGNOSTICS.md).
 *
 * <p>The window that matters starts at the last <em>action</em> before the failure (click, setText,
 * ...), not at the failing keyword itself: a wait such as {@code verifyElementPresent("Order Summary")}
 * fails because of the request the preceding {@code setText} triggered.</p>
 *
 * <p>Cost: two {@code System.currentTimeMillis()} calls per keyword, no allocation.</p>
 */
public final class StepClock {

    /** Keywords that change the page or send input, i.e. can trigger a request. */
    private static final Set<String> ACTIONS = Set.of(
            "click", "doubleClick", "rightClick", "clickOffset", "enhancedClick",
            "setText", "setEncryptedText", "sendKeys", "clearText", "uploadFile",
            "selectOptionByIndex", "selectOptionByLabel", "selectOptionByValue", "selectAllOption",
            "deselectOptionByIndex", "deselectOptionByLabel", "deselectOptionByValue", "deselectAllOption",
            "check", "uncheck", "submit", "navigateToUrl", "openBrowser", "refresh", "back", "forward",
            "dragAndDropToObject", "dragAndDropByOffset", "executeJavaScript");

    private static volatile Object lastActionTarget;
    // The action before the last one: when the failing keyword is itself an action (a click that finds
    // nothing), what matters is the action before it (the click that should have opened the popup).
    private static volatile long prevActionStart;
    private static volatile String prevActionName;
    private static volatile Object prevActionTarget;
    private static volatile Object failedActionTarget;
    private static volatile long lastActionStart;
    private static volatile String lastActionName;
    private static volatile long failedStart;
    private static volatile String failedName;
    private static volatile long failedActionStart;
    private static volatile String failedActionName;

    private StepClock() {}

    /** Called when a keyword starts; returns its start time. */
    public static long begin(String keyword) {
        return begin(keyword, null);
    }

    /** Same, also remembering the action's target object (first argument) - only a reference, no work. */
    public static long begin(String keyword, Object[] args) {
        long now = System.currentTimeMillis();
        // callTestCase runs a whole test case: its keywords are the test's own actions, not nested internals
        int depth = isContainer(keyword) ? DEPTH.get()[0] : ++DEPTH.get()[0];
        // A keyword can dispatch to another overload internally (e.g. a call from a custom keyword class is
        // converted and dispatched again): only the outermost call is the test's action.
        if (depth == 1 && keyword != null && ACTIONS.contains(keyword)) {
            prevActionStart = lastActionStart;
            prevActionName = lastActionName;
            prevActionTarget = lastActionTarget;
            lastActionStart = now;
            lastActionName = keyword;
            lastActionTarget = args != null && args.length > 0 ? args[0] : null;
        }
        return now;
    }

    /**
     * Called when a keyword throws. Nested keyword calls fail from the inside out, so the innermost
     * (latest start) is kept.
     */
    public static void failed(String keyword, long start) {
        if (DEPTH.get()[0] > 1) return; // the outermost call records the failure
        if (start >= failedStart) {
            failedStart = start;
            failedName = keyword;
            boolean failingIsTheAction = lastActionStart == start && prevActionStart > 0;
            failedActionStart = failingIsTheAction ? prevActionStart : lastActionStart;
            failedActionName = failingIsTheAction ? prevActionName : lastActionName;
            failedActionTarget = failingIsTheAction ? prevActionTarget : lastActionTarget;
        }
    }

    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    /** Called when a keyword returns or throws (pairs with {@link #begin}). */
    public static void end(String keyword) {
        if (isContainer(keyword)) return;
        int[] d = DEPTH.get();
        if (d[0] > 0) d[0]--;
    }

    private static boolean isContainer(String keyword) {
        return keyword != null && keyword.startsWith("callTestCase");
    }

    /** New test case attempt. */
    public static void reset() {
        DEPTH.get()[0] = 0;
        lastActionStart = 0;
        lastActionName = null;
        failedStart = 0;
        failedName = null;
        failedActionStart = 0;
        failedActionName = null;
        lastActionTarget = null;
        failedActionTarget = null;
        prevActionStart = 0;
        prevActionName = null;
        prevActionTarget = null;
    }

    /** {by, value} of the last action's target before the failure, or null. */
    public static String[] actionTargetLocator() {
        return failedActionStart > 0
                ? com.katalan.core.engine.KatalonStepFailure.locatorPartsOf(failedActionTarget) : null;
    }

    /** Epoch ms from which requests count as caused by the failing step, or 0 when unknown. */
    public static long windowStart() {
        if (failedStart == 0) return 0;
        long from = failedActionStart > 0 && failedActionStart <= failedStart ? failedActionStart : failedStart;
        return from - 1000; // typing/debounce and clock jitter between JVM and browser
    }

    public static String failedKeyword() { return failedName; }
    public static String actionKeyword() { return failedActionStart > 0 ? failedActionName : null; }
    public static long failedStartedAt() { return failedStart; }
}
