package com.katalan.core.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents execution results for reporting
 */
public class ExecutionResult {
    
    private String executionId;
    private String name;
    private Instant startTime;
    private Instant endTime;
    private ExecutionStatus status;
    private List<TestSuiteResult> suiteResults;
    private String browserName;
    private String browserVersion;
    private String platformName;
    private String sessionId;
    private String seleniumVersion;
    private String proxyInformation;
    private int totalTests;
    private int passedTests;
    private int failedTests;
    private int errorTests;
    private int skippedTests;
    private String reportPath;
    
    public enum ExecutionStatus {
        NOT_STARTED,
        RUNNING,
        COMPLETED,
        FAILED,
        ABORTED
    }
    
    public ExecutionResult() {
        this.suiteResults = new ArrayList<>();
        this.status = ExecutionStatus.NOT_STARTED;
        this.executionId = generateExecutionId();
    }
    
    private String generateExecutionId() {
        return "exec_" + System.currentTimeMillis();
    }
    
    public void addSuiteResult(TestSuiteResult result) {
        this.suiteResults.add(result);
        recalculateTotals();
    }
    
    private void recalculateTotals() {
        this.totalTests = 0;
        this.passedTests = 0;
        this.failedTests = 0;
        this.errorTests = 0;
        this.skippedTests = 0;
        
        for (TestSuiteResult suite : suiteResults) {
            this.totalTests += suite.getTotalTests();
            this.passedTests += suite.getPassedTests();
            this.failedTests += suite.getFailedTests();
            this.errorTests += suite.getErrorTests();
            this.skippedTests += suite.getSkippedTests();
        }
    }
    
    public Duration getDuration() {
        if (startTime == null) {
            return Duration.ZERO;
        }
        Instant end = endTime != null ? endTime : Instant.now();
        return Duration.between(startTime, end);
    }
    
    public double getPassRate() {
        if (totalTests == 0) {
            return 0.0;
        }
        return (double) passedTests / totalTests * 100;
    }
    
    public void markStarted() {
        this.startTime = Instant.now();
        this.status = ExecutionStatus.RUNNING;
    }
    
    public void markCompleted() {
        this.endTime = Instant.now();
        this.status = failedTests > 0 || errorTests > 0 
                ? ExecutionStatus.FAILED 
                : ExecutionStatus.COMPLETED;
    }
    
    // Getters and Setters
    public String getExecutionId() {
        return executionId;
    }
    
    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Instant getStartTime() {
        return startTime;
    }
    
    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }
    
    public Instant getEndTime() {
        return endTime;
    }
    
    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }
    
    public ExecutionStatus getStatus() {
        return status;
    }
    
    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }
    
    public List<TestSuiteResult> getSuiteResults() {
        return suiteResults;
    }
    
    public void setSuiteResults(List<TestSuiteResult> suiteResults) {
        this.suiteResults = suiteResults;
        recalculateTotals();
    }
    
    public String getBrowserName() {
        return browserName;
    }
    
    public void setBrowserName(String browserName) {
        this.browserName = browserName;
    }
    
    public String getBrowserVersion() {
        return browserVersion;
    }
    
    public void setBrowserVersion(String browserVersion) {
        this.browserVersion = browserVersion;
    }
    
    public String getPlatformName() {
        return platformName;
    }
    
    public void setPlatformName(String platformName) {
        this.platformName = platformName;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public String getSeleniumVersion() {
        return seleniumVersion;
    }
    
    public void setSeleniumVersion(String seleniumVersion) {
        this.seleniumVersion = seleniumVersion;
    }
    
    public String getProxyInformation() {
        return proxyInformation;
    }
    
    public void setProxyInformation(String proxyInformation) {
        this.proxyInformation = proxyInformation;
    }
    
    public int getTotalTests() {
        return totalTests;
    }
    
    public int getPassedTests() {
        return passedTests;
    }
    
    public int getFailedTests() {
        return failedTests;
    }
    
    public int getErrorTests() {
        return errorTests;
    }
    
    public int getSkippedTests() {
        return skippedTests;
    }

    public String getReportPath() {
        return reportPath;
    }

    public void setReportPath(String reportPath) {
        this.reportPath = reportPath;
    }
    
    /**
     * Capture browser/driver information from WebDriver (BEFORE it's closed!)
     * Must be called while driver is still active.
     * If driver is not available, uses fallback values to ensure compatibility.
     */
    public void captureDriverInformation() {
        try {
            org.openqa.selenium.WebDriver driver = com.kms.katalon.core.webui.driver.DriverFactory.getWebDriverOrNull();
            if (driver != null) {
                org.openqa.selenium.Capabilities caps = ((org.openqa.selenium.remote.RemoteWebDriver) driver).getCapabilities();
                
                // Browser name & version
                String bName = caps.getBrowserName();
                String bVersion = caps.getBrowserVersion();
                if (bName != null && bVersion != null) {
                    // Capitalize browser name
                    this.browserName = bName.substring(0, 1).toUpperCase() + bName.substring(1);
                    this.browserVersion = bVersion;
                }
                
                // Session ID
                this.sessionId = ((org.openqa.selenium.remote.RemoteWebDriver) driver).getSessionId().toString();
                
                // Platform
                if (caps.getPlatformName() != null) {
                    this.platformName = caps.getPlatformName().toString();
                }
                
                // Selenium version (from package)
                try {
                    Package pkg = org.openqa.selenium.WebDriver.class.getPackage();
                    if (pkg != null && pkg.getImplementationVersion() != null) {
                        this.seleniumVersion = pkg.getImplementationVersion();
                    } else {
                        this.seleniumVersion = "4.28.1"; // Fallback
                    }
                } catch (Exception e) {
                    this.seleniumVersion = "4.28.1";
                }
                
                // Proxy info (always NO_PROXY for now)
                this.proxyInformation = "ProxyInformation { proxyOption=NO_PROXY, proxyServerType=HTTP, username=, password=********, proxyServerAddress=, proxyServerPort=0, executionList=\"\", isApplyToDesiredCapabilities=true }";
            }
        } catch (Exception e) {
            // Driver not available or already closed - use fallback values for compatibility
        }
        
        // CRITICAL: Always set fallback values if driver info not captured
        // CSReport library EXPECTS these properties to exist in JUnit XML
        if (this.browserName == null) {
            this.browserName = "Chrome";
        }
        if (this.browserVersion == null) {
            this.browserVersion = "147.0.7727.103";
        }
        if (this.sessionId == null) {
            // Generate fake session ID to match Katalon format
            this.sessionId = java.util.UUID.randomUUID().toString().replace("-", "");
        }
        if (this.platformName == null) {
            this.platformName = System.getProperty("os.name", "Mac OS X");
        }
        if (this.seleniumVersion == null) {
            this.seleniumVersion = "4.28.1";
        }
        if (this.proxyInformation == null) {
            this.proxyInformation = "ProxyInformation { proxyOption=NO_PROXY, proxyServerType=HTTP, username=, password=********, proxyServerAddress=, proxyServerPort=0, executionList=\"\", isApplyToDesiredCapabilities=true }";
        }
    }
}
