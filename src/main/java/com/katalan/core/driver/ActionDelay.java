package com.katalan.core.driver;

/**
 * Katalon's "Delay between actions" (project setting execution.default.webUi.actionDelay, in
 * milliseconds unless execution.default.useDelayActionInSecond is SECONDS). Katalon sleeps this
 * long before every click, sendKeys, navigation and alert accept/dismiss - including raw
 * WebElement calls in scripts - which gives reactive forms (e.g. NDS) time to settle between
 * steps.
 */
public final class ActionDelay {

    private static volatile Long delayMs;

    private ActionDelay() {}

    public static void delay() {
        long ms = getDelayMs();
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static long getDelayMs() {
        Long ms = delayMs;
        if (ms == null) {
            ms = readDelayMs();
            delayMs = ms;
        }
        return ms;
    }

    private static long readDelayMs() {
        String override = System.getProperty("katalan.actionDelayMs");
        if (override != null) {
            return parse(override);
        }
        long delay = parse(SmartWait.readProjectSetting("execution.default.webUi.actionDelay"));
        if ("SECONDS".equalsIgnoreCase(SmartWait.readProjectSetting("execution.default.useDelayActionInSecond"))) {
            delay *= 1000;
        }
        return delay;
    }

    private static long parse(String value) {
        try {
            return value == null ? 0 : Math.max(0, Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
