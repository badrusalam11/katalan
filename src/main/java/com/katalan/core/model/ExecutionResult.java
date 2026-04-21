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
}
