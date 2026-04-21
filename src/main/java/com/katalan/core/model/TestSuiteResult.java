package com.katalan.core.model;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents results from a test suite execution
 */
public class TestSuiteResult {
    
    private String suiteId;
    private String suiteName;
    /** Absolute path to the .ts source file (if loaded from disk). */
    private Path suitePath;
    private Instant startTime;
    private Instant endTime;
    private List<TestCaseResult> testCaseResults;
    private int totalTests;
    private int passedTests;
    private int failedTests;
    private int errorTests;
    private int skippedTests;
    
    public TestSuiteResult() {
        this.testCaseResults = new ArrayList<>();
    }
    
    public TestSuiteResult(String suiteName) {
        this();
        this.suiteName = suiteName;
        this.suiteId = suiteName.replaceAll("\\s+", "_").toLowerCase();
    }
    
    public void addTestCaseResult(TestCaseResult result) {
        this.testCaseResults.add(result);
        recalculateTotals();
    }
    
    private void recalculateTotals() {
        this.totalTests = testCaseResults.size();
        this.passedTests = 0;
        this.failedTests = 0;
        this.errorTests = 0;
        this.skippedTests = 0;
        
        for (TestCaseResult tcr : testCaseResults) {
            switch (tcr.getStatus()) {
                case PASSED:
                    passedTests++;
                    break;
                case FAILED:
                    failedTests++;
                    break;
                case ERROR:
                    errorTests++;
                    break;
                case SKIPPED:
                    skippedTests++;
                    break;
                default:
                    break;
            }
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
    
    public boolean isSuccess() {
        return failedTests == 0 && errorTests == 0;
    }
    
    public void markStarted() {
        this.startTime = Instant.now();
    }
    
    public void markCompleted() {
        this.endTime = Instant.now();
    }
    
    // Getters and Setters
    public String getSuiteId() {
        return suiteId;
    }
    
    public void setSuiteId(String suiteId) {
        this.suiteId = suiteId;
    }
    
    public String getSuiteName() {
        return suiteName;
    }
    
    public void setSuiteName(String suiteName) {
        this.suiteName = suiteName;
    }
    
    public Path getSuitePath() {
        return suitePath;
    }
    
    public void setSuitePath(Path suitePath) {
        this.suitePath = suitePath;
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
    
    public List<TestCaseResult> getTestCaseResults() {
        return testCaseResults;
    }
    
    public void setTestCaseResults(List<TestCaseResult> testCaseResults) {
        this.testCaseResults = testCaseResults;
        recalculateTotals();
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
}
