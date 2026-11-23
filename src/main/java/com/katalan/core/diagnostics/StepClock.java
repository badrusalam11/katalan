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

    private static volatile long lastActionStart;
    private static volatile String lastActionName;
    private static volatile long failedStart;
    private static volatile String failedName;
    private static volatile long failedActionStart;
    private static volatile String failedActionName;

    private StepClock() {}

    /** Called when a keyword starts; returns its start time. */
    public static long begin(String keyword) {
        long now = System.currentTimeMillis();
        if (keyword != null && ACTIONS.contains(keyword)) {
            lastActionStart = now;
            lastActionName = keyword;
        }
        return now;
    }

    /**
     * Called when a keyword throws. Nested keyword calls fail from the inside out, so the innermost
     * (latest start) is kept.
     */
    public static void failed(String keyword, long start) {
        if (start >= failedStart) {
            failedStart = start;
            failedName = keyword;
            failedActionStart = lastActionStart;
            failedActionName = lastActionName;
        }
    }

    /** New test case attempt. */
    public static void reset() {
        lastActionStart = 0;
        lastActionName = null;
        failedStart = 0;
        failedName = null;
        failedActionStart = 0;
        failedActionName = null;
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
