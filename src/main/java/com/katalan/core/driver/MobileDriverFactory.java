package com.katalan.core.driver;

import com.katalan.core.config.RunConfiguration;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;
import io.appium.java_client.remote.options.BaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Mobile Driver Factory - Creates and configures Appium driver instances (Android/iOS).
 *
 * Mirrors {@link WebDriverFactory}'s role for desktop browsers: builds capabilities from
 * {@link RunConfiguration}, makes sure a server (local or remote) is reachable via
 * {@link AppiumServerManager}, and hands back a ready-to-use {@link AppiumDriver}.
 */
public class MobileDriverFactory {

    private static final Logger logger = LoggerFactory.getLogger(MobileDriverFactory.class);

    /**
     * Create an Appium driver instance based on configuration. Dynamic capability
     * overrides set at runtime via {@code RunConfiguration.setMobileDriverPreferencesProperty}
     * (Katalon compat layer) are merged in last, so scripts can always override a default.
     */
    public static AppiumDriver createDriver(RunConfiguration config) {
        String serverUrl = AppiumServerManager.ensureRunning(config);
        URL url;
        try {
            url = new URL(serverUrl);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid Appium server URL: " + serverUrl, e);
        }

        Map<String, Object> dynamicPrefs = com.kms.katalon.core.configuration.RunConfiguration
                .getMobileDriverPreferencesProperties();

        if (config.getMobilePlatform() == RunConfiguration.MobilePlatform.IOS) {
            XCUITestOptions options = buildIosOptions(config, dynamicPrefs);
            logger.info("📱 Starting IOSDriver - udid={}, app={}", options.getUdid().orElse("<auto>"),
                    options.getApp().orElse(config.getMobileAppPackage()));
            return new IOSDriver(url, options);
        }

        UiAutomator2Options options = buildAndroidOptions(config, dynamicPrefs);
        logger.info("📱 Starting AndroidDriver - udid={}, app={}, appPackage={}",
                options.getUdid().orElse("<auto>"), options.getApp().orElse(null),
                options.getAppPackage().orElse(config.getMobileAppPackage()));
        return new AndroidDriver(url, options);
    }

    private static UiAutomator2Options buildAndroidOptions(RunConfiguration config, Map<String, Object> dynamicPrefs) {
        UiAutomator2Options options = new UiAutomator2Options();
        options.setPlatformName("Android");
        options.setAutomationName(
                config.getMobileAutomationName() != null ? config.getMobileAutomationName() : "UiAutomator2");

        String deviceId = resolveAndroidDeviceId(config);
        if (deviceId != null) {
            options.setUdid(deviceId);
            options.setDeviceName(deviceId);
        }

        if (config.getMobilePlatformVersion() != null) {
            options.setPlatformVersion(config.getMobilePlatformVersion());
        }

        if (config.getMobileAppFile() != null && !config.getMobileAppFile().isEmpty()) {
            options.setApp(resolveAppPath(config.getMobileAppFile()));
        } else if (config.getMobileAppPackage() != null) {
            options.setAppPackage(config.getMobileAppPackage());
            if (config.getMobileAppActivity() != null) {
                options.setAppActivity(config.getMobileAppActivity());
            }
        }

        options.setNoReset(config.isMobileNoReset());
        options.setFullReset(config.isMobileFullReset());
        options.setAutoGrantPermissions(config.isMobileAutoGrantPermissions());
        options.setNewCommandTimeout(Duration.ofSeconds(config.getMobileNewCommandTimeout()));

        applyCustomCapabilities(options, config.getMobileCapabilities());
        applyCustomCapabilities(options, dynamicPrefs);
        return options;
    }

    private static XCUITestOptions buildIosOptions(RunConfiguration config, Map<String, Object> dynamicPrefs) {
        XCUITestOptions options = new XCUITestOptions();
        options.setPlatformName("iOS");
        options.setAutomationName(
                config.getMobileAutomationName() != null ? config.getMobileAutomationName() : "XCUITest");

        if (config.getMobileDeviceId() != null) {
            options.setUdid(config.getMobileDeviceId());
        }
        if (config.getMobilePlatformVersion() != null) {
            options.setPlatformVersion(config.getMobilePlatformVersion());
        }
        if (config.getMobileAppFile() != null && !config.getMobileAppFile().isEmpty()) {
            options.setApp(resolveAppPath(config.getMobileAppFile()));
        } else if (config.getMobileAppPackage() != null) {
            options.setBundleId(config.getMobileAppPackage());
        }

        options.setNoReset(config.isMobileNoReset());
        options.setFullReset(config.isMobileFullReset());
        options.setNewCommandTimeout(Duration.ofSeconds(config.getMobileNewCommandTimeout()));

        applyCustomCapabilities(options, config.getMobileCapabilities());
        applyCustomCapabilities(options, dynamicPrefs);
        return options;
    }

    private static void applyCustomCapabilities(BaseOptions<?> options, Map<String, Object> caps) {
        if (caps == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : caps.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().isEmpty()) {
                options.setCapability(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Resolve an app path: absolute paths are used as-is, relative paths are resolved
     * against the project directory (matching how Katalon scripts reference
     * {@code "Documents/apk/app.apk"} relative to the project root).
     */
    private static String resolveAppPath(String appFile) {
        java.io.File f = new java.io.File(appFile);
        if (f.isAbsolute()) {
            return f.getAbsolutePath();
        }
        java.io.File resolved = new java.io.File(System.getProperty("user.dir"), appFile);
        return resolved.exists() ? resolved.getAbsolutePath() : f.getAbsolutePath();
    }

    /**
     * Resolve which Android device to attach to: explicit config wins, otherwise fall
     * back to the single device currently visible to `adb devices`. Throws with a clear
     * message when the choice is ambiguous (so the user picks via --device-id) rather than
     * silently guessing.
     */
    private static String resolveAndroidDeviceId(RunConfiguration config) {
        if (config.getMobileDeviceId() != null && !config.getMobileDeviceId().isEmpty()) {
            return config.getMobileDeviceId();
        }

        List<String> devices = listAdbDevices();
        if (devices.isEmpty()) {
            logger.warn("No ADB devices detected and no --device-id given - letting Appium pick a default device.");
            return null;
        }
        if (devices.size() > 1) {
            throw new IllegalStateException(
                    "Multiple ADB devices attached (" + devices + ") - specify which one to use with --device-id");
        }
        String deviceId = devices.get(0);
        logger.info("🔌 Auto-detected single attached ADB device: {}", deviceId);
        config.setMobileDeviceId(deviceId);
        System.setProperty(DEVICE_ID_PROPERTY, deviceId);
        return deviceId;
    }

    /** System property mirroring the resolved device id (visible across Groovy class loaders). */
    public static final String DEVICE_ID_PROPERTY = "katalan.mobile.deviceId";

    /**
     * Eager, non-throwing variant of the device lookup used before any Appium session exists:
     * Katalon writes the device into execution.properties up front, and keywords read it at
     * class-init time. Returns the id only when exactly one device is attached.
     */
    public static String autoDetectAndroidDeviceId() {
        List<String> devices = listAdbDevices();
        if (devices.size() == 1) {
            logger.info("🔌 Auto-detected single attached ADB device: {}", devices.get(0));
            return devices.get(0);
        }
        if (devices.size() > 1) {
            logger.warn("Multiple ADB devices attached ({}) - pass --device-id to pick one", devices);
        }
        return null;
    }

    /**
     * Locate adb: PATH first, then ANDROID_HOME / ANDROID_SDK_ROOT, then the default SDK
     * locations used by Android Studio and Katalon Studio.
     */
    public static String adbExecutable() {
        String exe = System.getProperty("os.name", "").toLowerCase().contains("win") ? "adb.exe" : "adb";
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(java.io.File.pathSeparator)) {
                java.io.File f = new java.io.File(dir, exe);
                if (f.canExecute()) {
                    return f.getAbsolutePath();
                }
            }
        }
        String home = System.getProperty("user.home");
        String[] sdks = {
                System.getenv("ANDROID_HOME"),
                System.getenv("ANDROID_SDK_ROOT"),
                home + "/Library/Android/sdk",
                home + "/Android/Sdk",
                home + "/AppData/Local/Android/Sdk",
                home + "/.katalon/tools/android_sdk"
        };
        for (String sdk : sdks) {
            if (sdk == null || sdk.isEmpty()) {
                continue;
            }
            java.io.File f = new java.io.File(sdk, "platform-tools/" + exe);
            if (f.canExecute()) {
                return f.getAbsolutePath();
            }
        }
        return exe;
    }

    /** {@code adb [-s serial] shell getprop <prop>}, or null when adb is unavailable. */
    public static String adbGetProp(String serial, String prop) {
        List<String> command = new java.util.ArrayList<>();
        command.add(adbExecutable());
        if (serial != null && !serial.isEmpty()) {
            command.add("-s");
            command.add(serial);
        }
        command.add("shell");
        command.add("getprop");
        command.add(prop);
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try (java.io.BufferedReader reader =
                         new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                return line != null ? line.trim() : null;
            }
        } catch (Exception e) {
            logger.debug("adb getprop {} failed: {}", prop, e.getMessage());
            return null;
        }
    }

    private static List<String> listAdbDevices() {
        List<String> result = new java.util.ArrayList<>();
        try {
            Process process = new ProcessBuilder(adbExecutable(), "devices").redirectErrorStream(true).start();
            try (java.io.BufferedReader reader =
                         new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                boolean first = true;
                while ((line = reader.readLine()) != null) {
                    if (first) {
                        first = false; // skip "List of devices attached" header
                        continue;
                    }
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2 && "device".equals(parts[1])) {
                        result.add(parts[0]);
                    }
                }
            }
            process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.debug("adb devices lookup failed: {}", e.getMessage());
        }
        return result;
    }
}
