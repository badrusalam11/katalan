package com.katalan.core.config;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for test execution
 */
public class RunConfiguration {
    
    // Browser Configuration
    private BrowserType browserType = BrowserType.CHROME;
    private boolean headless = false;
    // Katalon sets implicit wait to 0 by default. WebUI keywords use their own
    // explicit WebDriverWait with per-call timeout, so implicit wait would only
    // interfere (each failed findElement during polling blocks for implicit*seconds).
    private int implicitWait = 0;
    private int pageLoadTimeout = 60;
    private int scriptTimeout = 30;
    
    // Execution Configuration
    private Path projectPath;
    private Path testCasePath;
    private Path testSuitePath;
    private Path reportPath;
    private Path screenshotPath;
    private String executionProfile = "default";
    private boolean takeScreenshotOnFailure = true;
    private boolean takeScreenshotOnSuccess = false;
    private int retryFailedTests = 0;
    private boolean failFast = false;
    
    // Browser Options
    private String browserBinaryPath;
    private String driverPath;
    private Map<String, Object> browserCapabilities;
    private Map<String, String> browserArguments;
    
    // Remote WebDriver
    private boolean useRemoteWebDriver = false;
    private String remoteWebDriverUrl;

    // Logging
    private LogLevel logLevel = LogLevel.INFO;

    // Mobile / Appium Configuration
    private MobilePlatform mobilePlatform = MobilePlatform.ANDROID;
    private String mobileDeviceId;
    private String mobilePlatformVersion;
    private String mobileAppFile;
    private String mobileAppPackage;
    private String mobileAppActivity;
    private String mobileAutomationName;
    private String appiumServerUrl;
    private int appiumServerPort = 0; // 0 = auto-pick a free port when auto-starting
    private boolean mobileNoReset = true;
    private boolean mobileFullReset = false;
    private boolean mobileAutoGrantPermissions = true;
    private int mobileNewCommandTimeout = 300;
    private Map<String, Object> mobileCapabilities;

    public enum BrowserType {
        CHROME,
        FIREFOX,
        EDGE,
        SAFARI
    }

    public enum MobilePlatform {
        ANDROID,
        IOS
    }
    
    public enum LogLevel {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }
    
    public RunConfiguration() {
        this.browserCapabilities = new HashMap<>();
        this.browserArguments = new HashMap<>();
        this.mobileCapabilities = new HashMap<>();
    }
    
    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final RunConfiguration config = new RunConfiguration();
        
        public Builder browserType(BrowserType type) {
            config.browserType = type;
            return this;
        }
        
        public Builder headless(boolean headless) {
            config.headless = headless;
            return this;
        }
        
        public Builder implicitWait(int seconds) {
            config.implicitWait = seconds;
            return this;
        }
        
        public Builder pageLoadTimeout(int seconds) {
            config.pageLoadTimeout = seconds;
            return this;
        }
        
        public Builder scriptTimeout(int seconds) {
            config.scriptTimeout = seconds;
            return this;
        }
        
        public Builder projectPath(Path path) {
            config.projectPath = path;
            return this;
        }
        
        public Builder testCasePath(Path path) {
            config.testCasePath = path;
            return this;
        }
        
        public Builder testSuitePath(Path path) {
            config.testSuitePath = path;
            return this;
        }
        
        public Builder reportPath(Path path) {
            config.reportPath = path;
            return this;
        }
        
        public Builder screenshotPath(Path path) {
            config.screenshotPath = path;
            return this;
        }
        
        public Builder takeScreenshotOnFailure(boolean take) {
            config.takeScreenshotOnFailure = take;
            return this;
        }
        
        public Builder takeScreenshotOnSuccess(boolean take) {
            config.takeScreenshotOnSuccess = take;
            return this;
        }
        
        public Builder retryFailedTests(int count) {
            config.retryFailedTests = count;
            return this;
        }
        
        public Builder failFast(boolean fail) {
            config.failFast = fail;
            return this;
        }
        
        public Builder browserBinaryPath(String path) {
            config.browserBinaryPath = path;
            return this;
        }
        
        public Builder driverPath(String path) {
            config.driverPath = path;
            return this;
        }
        
        public Builder addCapability(String key, Object value) {
            config.browserCapabilities.put(key, value);
            return this;
        }
        
        public Builder addBrowserArgument(String key, String value) {
            config.browserArguments.put(key, value);
            return this;
        }
        
        public Builder useRemoteWebDriver(boolean use) {
            config.useRemoteWebDriver = use;
            return this;
        }
        
        public Builder remoteWebDriverUrl(String url) {
            config.remoteWebDriverUrl = url;
            return this;
        }
        
        public Builder logLevel(LogLevel level) {
            config.logLevel = level;
            return this;
        }
        
        public Builder executionProfile(String profile) {
            config.executionProfile = profile;
            return this;
        }

        public Builder mobilePlatform(MobilePlatform platform) {
            config.mobilePlatform = platform;
            return this;
        }

        public Builder mobileDeviceId(String deviceId) {
            config.mobileDeviceId = deviceId;
            return this;
        }

        public Builder mobilePlatformVersion(String version) {
            config.mobilePlatformVersion = version;
            return this;
        }

        public Builder mobileAppFile(String appFile) {
            config.mobileAppFile = appFile;
            return this;
        }

        public Builder mobileAppPackage(String appPackage) {
            config.mobileAppPackage = appPackage;
            return this;
        }

        public Builder mobileAppActivity(String appActivity) {
            config.mobileAppActivity = appActivity;
            return this;
        }

        public Builder mobileAutomationName(String automationName) {
            config.mobileAutomationName = automationName;
            return this;
        }

        public Builder appiumServerUrl(String url) {
            config.appiumServerUrl = url;
            return this;
        }

        public Builder appiumServerPort(int port) {
            config.appiumServerPort = port;
            return this;
        }

        public Builder mobileNoReset(boolean noReset) {
            config.mobileNoReset = noReset;
            return this;
        }

        public Builder mobileFullReset(boolean fullReset) {
            config.mobileFullReset = fullReset;
            return this;
        }

        public Builder mobileAutoGrantPermissions(boolean autoGrant) {
            config.mobileAutoGrantPermissions = autoGrant;
            return this;
        }

        public Builder mobileNewCommandTimeout(int seconds) {
            config.mobileNewCommandTimeout = seconds;
            return this;
        }

        public Builder addMobileCapability(String key, Object value) {
            config.mobileCapabilities.put(key, value);
            return this;
        }

        public RunConfiguration build() {
            return config;
        }
    }
    
    // Getters and Setters
    public BrowserType getBrowserType() {
        return browserType;
    }
    
    public void setBrowserType(BrowserType browserType) {
        this.browserType = browserType;
    }
    
    public boolean isHeadless() {
        return headless;
    }
    
    public void setHeadless(boolean headless) {
        this.headless = headless;
    }
    
    public int getImplicitWait() {
        return implicitWait;
    }
    
    public void setImplicitWait(int implicitWait) {
        this.implicitWait = implicitWait;
    }
    
    public int getPageLoadTimeout() {
        return pageLoadTimeout;
    }
    
    public void setPageLoadTimeout(int pageLoadTimeout) {
        this.pageLoadTimeout = pageLoadTimeout;
    }
    
    public int getScriptTimeout() {
        return scriptTimeout;
    }
    
    public void setScriptTimeout(int scriptTimeout) {
        this.scriptTimeout = scriptTimeout;
    }
    
    public Path getProjectPath() {
        return projectPath;
    }
    
    public void setProjectPath(Path projectPath) {
        this.projectPath = projectPath;
    }
    
    public Path getTestCasePath() {
        return testCasePath;
    }
    
    public void setTestCasePath(Path testCasePath) {
        this.testCasePath = testCasePath;
    }
    
    public Path getTestSuitePath() {
        return testSuitePath;
    }
    
    public void setTestSuitePath(Path testSuitePath) {
        this.testSuitePath = testSuitePath;
    }
    
    public Path getReportPath() {
        return reportPath;
    }
    
    public void setReportPath(Path reportPath) {
        this.reportPath = reportPath;
    }
    
    public Path getScreenshotPath() {
        return screenshotPath;
    }
    
    public void setScreenshotPath(Path screenshotPath) {
        this.screenshotPath = screenshotPath;
    }
    
    public boolean isTakeScreenshotOnFailure() {
        return takeScreenshotOnFailure;
    }
    
    public void setTakeScreenshotOnFailure(boolean takeScreenshotOnFailure) {
        this.takeScreenshotOnFailure = takeScreenshotOnFailure;
    }
    
    public boolean isTakeScreenshotOnSuccess() {
        return takeScreenshotOnSuccess;
    }
    
    public void setTakeScreenshotOnSuccess(boolean takeScreenshotOnSuccess) {
        this.takeScreenshotOnSuccess = takeScreenshotOnSuccess;
    }
    
    public int getRetryFailedTests() {
        return retryFailedTests;
    }
    
    public void setRetryFailedTests(int retryFailedTests) {
        this.retryFailedTests = retryFailedTests;
    }
    
    public boolean isFailFast() {
        return failFast;
    }
    
    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }
    
    public String getBrowserBinaryPath() {
        return browserBinaryPath;
    }
    
    public void setBrowserBinaryPath(String browserBinaryPath) {
        this.browserBinaryPath = browserBinaryPath;
    }
    
    public String getDriverPath() {
        return driverPath;
    }
    
    public void setDriverPath(String driverPath) {
        this.driverPath = driverPath;
    }
    
    public Map<String, Object> getBrowserCapabilities() {
        return browserCapabilities;
    }
    
    public void setBrowserCapabilities(Map<String, Object> browserCapabilities) {
        this.browserCapabilities = browserCapabilities;
    }
    
    public Map<String, String> getBrowserArguments() {
        return browserArguments;
    }
    
    public void setBrowserArguments(Map<String, String> browserArguments) {
        this.browserArguments = browserArguments;
    }
    
    public boolean isUseRemoteWebDriver() {
        return useRemoteWebDriver;
    }
    
    public void setUseRemoteWebDriver(boolean useRemoteWebDriver) {
        this.useRemoteWebDriver = useRemoteWebDriver;
    }
    
    public String getRemoteWebDriverUrl() {
        return remoteWebDriverUrl;
    }
    
    public void setRemoteWebDriverUrl(String remoteWebDriverUrl) {
        this.remoteWebDriverUrl = remoteWebDriverUrl;
    }
    
    public LogLevel getLogLevel() {
        return logLevel;
    }
    
    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }
    
    public String getExecutionProfile() {
        return executionProfile;
    }

    public void setExecutionProfile(String executionProfile) {
        this.executionProfile = executionProfile;
    }

    // ==================== Mobile / Appium Getters and Setters ====================

    public MobilePlatform getMobilePlatform() {
        return mobilePlatform;
    }

    public void setMobilePlatform(MobilePlatform mobilePlatform) {
        this.mobilePlatform = mobilePlatform;
    }

    public String getMobileDeviceId() {
        return mobileDeviceId;
    }

    public void setMobileDeviceId(String mobileDeviceId) {
        this.mobileDeviceId = mobileDeviceId;
    }

    public String getMobilePlatformVersion() {
        return mobilePlatformVersion;
    }

    public void setMobilePlatformVersion(String mobilePlatformVersion) {
        this.mobilePlatformVersion = mobilePlatformVersion;
    }

    public String getMobileAppFile() {
        return mobileAppFile;
    }

    public void setMobileAppFile(String mobileAppFile) {
        this.mobileAppFile = mobileAppFile;
    }

    public String getMobileAppPackage() {
        return mobileAppPackage;
    }

    public void setMobileAppPackage(String mobileAppPackage) {
        this.mobileAppPackage = mobileAppPackage;
    }

    public String getMobileAppActivity() {
        return mobileAppActivity;
    }

    public void setMobileAppActivity(String mobileAppActivity) {
        this.mobileAppActivity = mobileAppActivity;
    }

    public String getMobileAutomationName() {
        return mobileAutomationName;
    }

    public void setMobileAutomationName(String mobileAutomationName) {
        this.mobileAutomationName = mobileAutomationName;
    }

    public String getAppiumServerUrl() {
        return appiumServerUrl;
    }

    public void setAppiumServerUrl(String appiumServerUrl) {
        this.appiumServerUrl = appiumServerUrl;
    }

    public int getAppiumServerPort() {
        return appiumServerPort;
    }

    public void setAppiumServerPort(int appiumServerPort) {
        this.appiumServerPort = appiumServerPort;
    }

    public boolean isMobileNoReset() {
        return mobileNoReset;
    }

    public void setMobileNoReset(boolean mobileNoReset) {
        this.mobileNoReset = mobileNoReset;
    }

    public boolean isMobileFullReset() {
        return mobileFullReset;
    }

    public void setMobileFullReset(boolean mobileFullReset) {
        this.mobileFullReset = mobileFullReset;
    }

    public boolean isMobileAutoGrantPermissions() {
        return mobileAutoGrantPermissions;
    }

    public void setMobileAutoGrantPermissions(boolean mobileAutoGrantPermissions) {
        this.mobileAutoGrantPermissions = mobileAutoGrantPermissions;
    }

    public int getMobileNewCommandTimeout() {
        return mobileNewCommandTimeout;
    }

    public void setMobileNewCommandTimeout(int mobileNewCommandTimeout) {
        this.mobileNewCommandTimeout = mobileNewCommandTimeout;
    }

    public Map<String, Object> getMobileCapabilities() {
        return mobileCapabilities;
    }

    public void setMobileCapabilities(Map<String, Object> mobileCapabilities) {
        this.mobileCapabilities = mobileCapabilities;
    }
}
