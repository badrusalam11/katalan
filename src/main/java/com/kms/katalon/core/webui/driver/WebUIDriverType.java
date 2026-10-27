package com.kms.katalon.core.webui.driver;

import com.kms.katalon.core.driver.IDriverType;

/**
 * Katalon compatibility: the browser types Katalon Studio executes WebUI tests with.
 * Mirrors Katalon's enum - {@link #getName()} is the constant name ("CHROME_DRIVER"),
 * {@link #toString()} the display name ("Chrome"). Returned by
 * {@link DriverFactory#getExecutedBrowser()}.
 */
public enum WebUIDriverType implements IDriverType {
    FIREFOX_DRIVER("Firefox"),
    IE_DRIVER("IE"),
    CHROME_DRIVER("Chrome"),
    SAFARI_DRIVER("Safari"),
    REMOTE_WEB_DRIVER("Remote"),
    ANDROID_DRIVER("Android"),
    IOS_DRIVER("iOS"),
    EDGE_DRIVER("Edge"),
    EDGE_CHROMIUM_DRIVER("Edge Chromium"),
    REMOTE_FIREFOX_DRIVER("Remote Firefox"),
    REMOTE_CHROME_DRIVER("Remote Chrome"),
    KOBITON_WEB_DRIVER("Kobiton Device"),
    HEADLESS_DRIVER("Chrome (headless)"),
    FIREFOX_HEADLESS_DRIVER("Firefox (headless)"),
    TESTCLOUD_DRIVER("TestCloud");

    private final String driverName;

    WebUIDriverType(String driverName) {
        this.driverName = driverName;
    }

    @Override
    public String getName() {
        return name();
    }

    @Override
    public String toString() {
        return driverName;
    }

    public static String[] stringValues() {
        WebUIDriverType[] values = values();
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = values[i].toString();
        }
        return out;
    }

    public static WebUIDriverType fromStringValue(String value) {
        if (value == null) {
            return null;
        }
        for (WebUIDriverType type : values()) {
            if (type.toString().equals(value)) {
                return type;
            }
        }
        return null;
    }

    @Override
    public String getPropertyKey() {
        return "browserType";
    }

    @Override
    public String getPropertyValue() {
        return name();
    }
}
