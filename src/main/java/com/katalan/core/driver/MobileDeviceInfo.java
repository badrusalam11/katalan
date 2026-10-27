package com.katalan.core.driver;

import com.katalan.core.config.RunConfiguration;
import com.katalan.core.context.ExecutionContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Katalon-shaped device description for a Mobile run ({@code execution.drivers.system.Mobile}).
 *
 * Katalon Studio resolves the device before the run starts and writes it into
 * execution.properties, so keywords can read {@code deviceOS}, {@code devicePlatform} etc.
 * (directly, or via {@code MobileDriverFactory.getDeviceOS()} / {@code RunConfiguration
 * .getDriverPreferencesProperties()}) before any Appium session exists. This mirrors that:
 * the live session wins when there is one, otherwise the attached device is queried via adb.
 */
public final class MobileDeviceInfo {

    private static final Map<String, Map<String, Object>> ADB_CACHE = new ConcurrentHashMap<>();

    private MobileDeviceInfo() {
    }

    /** True when the CLI was asked to run against a mobile platform ({@code --platform android|ios}). */
    public static boolean isMobileRun() {
        String platform = System.getProperty("katalan.browser");
        return "android".equalsIgnoreCase(platform) || "ios".equalsIgnoreCase(platform);
    }

    public static boolean isIos() {
        return "ios".equalsIgnoreCase(System.getProperty("katalan.browser"));
    }

    /** Device serial/udid: live session, then the CLI/auto-detected id. Empty when unknown. */
    public static String deviceId() {
        ExecutionContext ctx = ExecutionContext.getCurrent();
        if (ctx != null && ctx.getMobileDriver() != null) {
            Object udid = ctx.getMobileDriver().getCapabilities().getCapability("udid");
            if (udid != null && !udid.toString().isEmpty()) {
                return udid.toString();
            }
        }
        String id = System.getProperty(MobileDriverFactory.DEVICE_ID_PROPERTY);
        if (id != null && !id.isEmpty()) {
            return id;
        }
        if (ctx != null && ctx.getRunConfiguration() != null) {
            RunConfiguration cfg = ctx.getRunConfiguration();
            if (cfg.getMobileDeviceId() != null) {
                return cfg.getMobileDeviceId();
            }
        }
        return "";
    }

    /** "Android" / "iOS", as Katalon's MobileDriverFactory.getDeviceOS() reports it. */
    public static String deviceOS() {
        return isIos() ? "iOS" : "Android";
    }

    /** Katalon's MobileDriverType name: ANDROID_DRIVER / IOS_DRIVER. */
    public static String devicePlatform() {
        return isIos() ? "IOS_DRIVER" : "ANDROID_DRIVER";
    }

    /**
     * The {@code execution.drivers.system.Mobile} block Katalon writes for a mobile run:
     * deviceOS, deviceModel, devicePlatform, deviceManufacturer, deviceId, deviceName, deviceOSVersion.
     */
    public static Map<String, Object> systemProperties() {
        Map<String, Object> out = new LinkedHashMap<>();
        String id = deviceId();
        out.put("deviceOS", deviceOS());
        if (isIos()) {
            out.put("deviceModel", "");
            out.put("devicePlatform", devicePlatform());
            out.put("deviceManufacturer", "Apple");
            out.put("deviceId", id);
            out.put("deviceName", id);
            out.put("deviceOSVersion", "");
            return out;
        }
        Map<String, Object> adb = id.isEmpty() ? new LinkedHashMap<>() : ADB_CACHE.computeIfAbsent(id, MobileDeviceInfo::queryAdb);
        String model = (String) adb.getOrDefault("model", "");
        String manufacturer = (String) adb.getOrDefault("manufacturer", "");
        String version = (String) adb.getOrDefault("version", "");
        out.put("deviceModel", model);
        out.put("devicePlatform", devicePlatform());
        out.put("deviceManufacturer", manufacturer);
        out.put("deviceId", id);
        // Katalon's display name, e.g. "samsung SM-A315G (Android 12)"
        out.put("deviceName", model.isEmpty() ? id : (manufacturer + " " + model + " (Android " + version + ")").trim());
        out.put("deviceOSVersion", version);
        return out;
    }

    private static Map<String, Object> queryAdb(String id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("model", nullToEmpty(MobileDriverFactory.adbGetProp(id, "ro.product.model")));
        m.put("manufacturer", nullToEmpty(MobileDriverFactory.adbGetProp(id, "ro.product.manufacturer")));
        m.put("version", nullToEmpty(MobileDriverFactory.adbGetProp(id, "ro.build.version.release")));
        return m;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
