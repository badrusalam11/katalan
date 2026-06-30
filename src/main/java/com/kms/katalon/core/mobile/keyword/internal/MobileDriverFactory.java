package com.kms.katalon.core.mobile.keyword.internal;

import com.katalan.core.context.ExecutionContext;

/**
 * Katalon compatibility layer for MobileDriverFactory.
 * Delegates to katalan's real Appium-backed Mobile engine. Matches Katalon's own
 * behavior of throwing when no driver session is active yet (CSMobile-style scripts
 * rely on this to detect "do I need to start a session?" via try/catch).
 */
public class MobileDriverFactory {

    public static Object getDriver() {
        Object driver = ExecutionContext.getCurrent().getMobileDriver();
        if (driver == null) {
            throw new IllegalStateException(
                    "Mobile driver is not initialized. Call Mobile.startApplication() or Mobile.startExistingApplication() first.");
        }
        return driver;
    }

    public static void closeDriver() {
        com.katalan.keywords.Mobile.closeApplication();
    }

    public static String getDeviceName() {
        return com.katalan.keywords.Mobile.getDeviceName();
    }

    public static String getDeviceId() {
        return com.katalan.keywords.Mobile.getDeviceId();
    }
}
