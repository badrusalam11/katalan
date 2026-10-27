package com.kms.katalon.core.mobile.keyword.internal;

import com.katalan.core.context.ExecutionContext;
import com.katalan.core.driver.MobileDeviceInfo;

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

    // Katalon reads the device getters below from execution.properties (resolved before the
    // run starts), so they work before any Appium session exists - keywords such as
    // brispot's CustomReport.detectDevice() call getDeviceOS() from a static initializer.

    public static String getDeviceName() {
        return device("deviceName");
    }

    public static String getDeviceId() {
        return device("deviceId");
    }

    public static String getDeviceOS() {
        return MobileDeviceInfo.deviceOS();
    }

    public static String getDeviceOSVersion() {
        return device("deviceOSVersion");
    }

    public static String getDeviceModel() {
        return device("deviceModel");
    }

    public static String getDeviceManufacturer() {
        return device("deviceManufacturer");
    }

    public static String getDevicePlatform() {
        return MobileDeviceInfo.devicePlatform();
    }

    private static String device(String key) {
        Object value = MobileDeviceInfo.systemProperties().get(key);
        return value != null ? value.toString() : "";
    }
}
