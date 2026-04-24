package com.kms.katalon.core.configuration;

import com.katalan.core.context.ExecutionContext;
import com.kms.katalon.core.model.FailureHandling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Katalon compatibility class for RunConfiguration
 * This provides access to runtime configuration settings that Katalon scripts expect.
 * 
 * IMPORTANT: This returns com.kms.katalon.core.model.FailureHandling because JAR libraries
 * compiled against Katalon (like CSWeb) expect this type when casting the FailureHandling field.
 */
public class RunConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(RunConfiguration.class);
    private static final Map<String, Object> executionProperties = new HashMap<>();
    private static volatile String executionProfile = "default";
    private static volatile String reportFolderOverride;

    /**
     * Set the current report folder (called by engine after generating the
     * Katalon-style nested folder: Reports/&lt;timestamp&gt;/&lt;SuiteName&gt;/&lt;timestamp&gt;).
     * This is what Katalon scripts expect when they call
     * {@code RunConfiguration.getReportFolder()} -- the ACTUAL per-run folder,
     * not the top-level "Reports" directory.
     */
    public static void setReportFolder(String folder) {
        reportFolderOverride = folder;
        logger.info("RunConfiguration.reportFolder set to: {}", folder);
    }

    /**
     * Set the active execution profile name (called by engine at startup).
     */
    public static void setExecutionProfile(String profile) {
        executionProfile = (profile == null || profile.isEmpty()) ? "default" : profile;
        logger.info("RunConfiguration.executionProfile set to: {}", executionProfile);
    }
    
    /**
     * Get the default failure handling behavior
     * Returns com.kms.katalon.core.model.FailureHandling for compatibility with 
     * JAR libraries compiled against Katalon
     */
    public static FailureHandling getDefaultFailureHandling() {
        return FailureHandling.STOP_ON_FAILURE;
    }
    
    /**
     * Get the project directory path (always returned as absolute path).
     * Katalon scripts expect absolute paths (e.g. `CSWeb` checks
     * `getProjectDir().substring(0,7) == '/Users/'`), so we resolve
     * relative paths like "." to the real absolute directory.
     */
    public static String getProjectDir() {
        ExecutionContext ctx = ExecutionContext.getCurrent();
        Path p = null;
        if (ctx != null && ctx.getProjectDir() != null) {
            p = ctx.getProjectDir();
        }
        if (p == null) {
            p = Paths.get(System.getProperty("user.dir"));
        }
        try {
            return p.toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            return p.toString();
        }
    }
    
    /**
     * Legacy helper: returns a non-null project dir string even when no context.
     */
    private static String _legacyProjectDir() {
        ExecutionContext ctx = ExecutionContext.getCurrent();
        if (ctx != null && ctx.getProjectDir() != null) {
            return ctx.getProjectDir().toString();
        }
        return System.getProperty("user.dir");
    }
    
    /**
     * Get the report folder path.
     *
     * When a test suite is running, this returns the Katalon-style per-run
     * folder: {@code Reports/<timestamp>/<SuiteName>/<timestamp>}.
     * Katalon scripts (e.g. CSReport, custom listeners) expect this nested
     * path so they can read {@code execution.properties}, {@code JUnit_Report.xml},
     * etc.
     *
     * Falls back to {@code <projectDir>/Reports} if no run is active.
     */
    public static String getReportFolder() {
        String override = reportFolderOverride;
        if (override != null && !override.isEmpty()) {
            return override;
        }
        return Paths.get(getProjectDir(), "Reports").toString();
    }
    
    /**
     * Get the log folder path  
     */
    public static String getLogFolder() {
        return Paths.get(getProjectDir(), "logs").toString();
    }
    
    /**
     * Get execution source (e.g., "katalon" or "katalan")
     */
    public static String getExecutionSource() {
        return "katalan";
    }
    
    /**
     * Get execution source name
     */
    public static String getExecutionSourceName() {
        return "Katalan Runner";
    }
    
    /**
     * Get the default timeout for wait operations
     */
    public static int getDefaultTimeout() {
        return 30;
    }
    
    /**
     * Get the timeout (alias for getDefaultTimeout)
     */
    public static int getTimeOut() {
        return getDefaultTimeout();
    }
    
    /**
     * Get the default page load timeout
     */
    public static int getDefaultPageLoadTimeout() {
        return 60;
    }
    
    /**
     * Check if running in headless mode
     */
    public static boolean isHeadless() {
        ExecutionContext ctx = ExecutionContext.getCurrent();
        if (ctx != null && ctx.getRunConfiguration() != null) {
            return ctx.getRunConfiguration().isHeadless();
        }
        return false;
    }
    
    /**
     * Get the browser type
     */
    public static String getBrowserType() {
        ExecutionContext ctx = ExecutionContext.getCurrent();
        if (ctx != null && ctx.getRunConfiguration() != null) {
            return ctx.getRunConfiguration().getBrowserType().name();
        }
        return "CHROME";
    }
    
    /**
     * Get the host operating system
     */
    public static String getHostOS() {
        return System.getProperty("os.name");
    }
    
    /**
     * Get the host name
     */
    public static String getHostName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "localhost";
        }
    }
    
    /**
     * Get the host address
     */
    public static String getHostAddress() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
    
    /**
     * Get an execution property
     */
    public static Object getExecutionProperty(String key) {
        return executionProperties.get(key);
    }
    
    /**
     * Set an execution property
     */
    public static void setExecutionProperty(String key, Object value) {
        executionProperties.put(key, value);
    }
    
    /**
     * Get the driver path (system driver)
     */
    public static String getDriverSystemPath() {
        return getDriverSystemPath(getBrowserType());
    }
    
    /**
     * Get the driver path for a specific browser
     */
    public static String getDriverSystemPath(String browserType) {
        // WebDriverManager handles this automatically in Katalan
        return null;
    }
    
    /**
     * Get the Katalon/Katalan version
     */
    public static String getAppVersion() {
        return "1.0.0";
    }
    
    /**
     * Get the session ID
     */
    public static String getSessionId() {
        ExecutionContext ctx = ExecutionContext.getCurrent();
        if (ctx != null) {
            return ctx.getSessionId();
        }
        return "katalan-" + System.currentTimeMillis();
    }
    
    /**
     * Get the test suite name
     */
    public static String getTestSuiteName() {
        ExecutionContext ctx = ExecutionContext.getCurrent();
        if (ctx != null) {
            return ctx.getCurrentTestSuiteName();
        }
        return "";
    }
    
    /**
     * Get the test case name
     */
    public static String getTestCaseName() {
        ExecutionContext ctx = ExecutionContext.getCurrent();
        if (ctx != null) {
            return ctx.getCurrentTestCaseName();
        }
        return "";
    }
    
    /**
     * Check if should take screenshot on failure
     */
    public static boolean shouldTakeScreenshotOnFailure() {
        return true;
    }
    
    /**
     * Check if video recording is enabled
     */
    public static boolean isVideoRecordingEnabled() {
        return false;
    }
    
    /**
     * Get execution profile name
     */
    public static String getExecutionProfile() {
        return executionProfile != null ? executionProfile : "default";
    }
    
    /**
     * Get execution properties as a Map structure compatible with Katalon
     * This returns a structure that mimics Katalon's ExecutionProperties object
     * Used by listeners like Actmov3 that call RunConfiguration.getExecutionProperties().drivers.system
     */
    public static Map<String, Object> getExecutionProperties() {
        Map<String, Object> props = new HashMap<>();
        
        // Create the drivers.system structure
        Map<String, Object> drivers = new HashMap<>();
        Map<String, Object> system = new HashMap<>();
        Map<String, Object> webUI = new HashMap<>();
        Map<String, Object> mobile = new HashMap<>();
        
        // Set browser type for WebUI
        String browserType = getBrowserType();
        webUI.put("browserType", browserType);
        
        // Set device platform for Mobile (if applicable)
        mobile.put("devicePlatform", ""); // Empty for non-mobile tests
        
        system.put("WebUI", webUI);
        system.put("Mobile", mobile);
        drivers.put("system", system);
        props.put("drivers", drivers);
        
        return props;
    }
    
    /**
     * Store a value in execution context
     */
    public static void storeToExecutionContext(String key, Object value) {
        setExecutionProperty(key, value);
    }
    
    /**
     * Get a value from execution context
     */
    public static Object getFromExecutionContext(String key) {
        return getExecutionProperty(key);
    }
    
    /**
     * Get the data file path
     */
    public static String getDataFilePath(String dataFileName) {
        return Paths.get(getProjectDir(), "Data Files", dataFileName).toString();
    }
    
    /**
     * Get the keywords path
     */
    public static String getKeywordsPath() {
        return Paths.get(getProjectDir(), "Keywords").toString();
    }
    
    /**
     * Get the test cases path
     */
    public static String getTestCasesPath() {
        return Paths.get(getProjectDir(), "Test Cases").toString();
    }
    
    /**
     * Get the test suites path
     */
    public static String getTestSuitesPath() {
        return Paths.get(getProjectDir(), "Test Suites").toString();
    }
    
    /**
     * Get the object repository path
     */
    public static String getObjectRepositoryPath() {
        return Paths.get(getProjectDir(), "Object Repository").toString();
    }
    
    /**
     * Get the plugins path
     */
    public static String getPluginsPath() {
        return Paths.get(getProjectDir(), "Plugins").toString();
    }
    
    /**
     * Get the drivers path
     */
    public static String getDriversPath() {
        return Paths.get(getProjectDir(), "Drivers").toString();
    }
    
    /**
     * Get execution general properties
     */
    public static Map<String, Object> getExecutionGeneralProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("hostOS", getHostOS());
        props.put("hostName", getHostName());
        props.put("browserType", getBrowserType());
        props.put("isHeadless", isHeadless());
        return props;
    }
    
    /**
     * Check if running in API test mode
     */
    public static boolean isApiMode() {
        return false;
    }
    
    /**
     * Check if running in web UI test mode
     */
    public static boolean isWebUIMode() {
        return true;
    }
    
    /**
     * Check if running in mobile test mode
     */
    public static boolean isMobileMode() {
        return false;
    }
    
    /**
     * Check if running in Windows desktop test mode
     */
    public static boolean isWindowsMode() {
        return false;
    }

    // ============================================================
    // WebDriver preferences (Katalon compat stubs)
    // Scripts call these to inject driver preferences/capabilities
    // at runtime (e.g. chrome args, prefs). We just store them in
    // the executionProperties map; actual driver factory can read
    // them later if needed.
    // ============================================================

    private static final String WD_PREFS_PREFIX = "webdriver.preferences.";
    private static final String WD_CAPS_PREFIX = "webdriver.capabilities.";

    public static void setWebDriverPreferencesProperty(String key, Object value) {
        executionProperties.put(WD_PREFS_PREFIX + key, value);
        logger.debug("setWebDriverPreferencesProperty({}, {})", key, value);
    }

    public static Object getWebDriverPreferencesProperty(String key) {
        return executionProperties.get(WD_PREFS_PREFIX + key);
    }

    public static Map<String, Object> getWebDriverPreferencesProperties() {
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<String, Object> e : executionProperties.entrySet()) {
            if (e.getKey().startsWith(WD_PREFS_PREFIX)) {
                out.put(e.getKey().substring(WD_PREFS_PREFIX.length()), e.getValue());
            }
        }
        return out;
    }

    public static void setWebDriverCapabilitiesProperty(String key, Object value) {
        executionProperties.put(WD_CAPS_PREFIX + key, value);
        logger.debug("setWebDriverCapabilitiesProperty({}, {})", key, value);
    }

    public static Object getWebDriverCapabilitiesProperty(String key) {
        return executionProperties.get(WD_CAPS_PREFIX + key);
    }

    public static Map<String, Object> getWebDriverCapabilitiesProperties() {
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<String, Object> e : executionProperties.entrySet()) {
            if (e.getKey().startsWith(WD_CAPS_PREFIX)) {
                out.put(e.getKey().substring(WD_CAPS_PREFIX.length()), e.getValue());
            }
        }
        return out;
    }
}
