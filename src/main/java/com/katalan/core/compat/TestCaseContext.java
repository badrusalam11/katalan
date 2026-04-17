package com.katalan.core.compat;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Katalon-compatible TestCaseContext class
 * Provides context information about the current test case execution
 */
public class TestCaseContext {
    
    private String testCaseId;
    private String testCaseName;
    private String testCaseStatus;
    private String message;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Map<String, Object> attributes;
    
    public TestCaseContext() {
        this.attributes = new HashMap<>();
        this.startTime = LocalDateTime.now();
        this.testCaseStatus = "NOT_RUN";
    }
    
    public TestCaseContext(String testCaseId) {
        this();
        this.testCaseId = testCaseId;
        this.testCaseName = testCaseId;
    }
    
    // Status constants
    public static final String PASSED = "PASSED";
    public static final String FAILED = "FAILED";
    public static final String ERROR = "ERROR";
    public static final String INCOMPLETE = "INCOMPLETE";
    public static final String NOT_RUN = "NOT_RUN";
    
    // Getters and setters
    public String getTestCaseId() {
        return testCaseId;
    }
    
    public void setTestCaseId(String testCaseId) {
        this.testCaseId = testCaseId;
    }
    
    public String getTestCaseName() {
        return testCaseName;
    }
    
    public void setTestCaseName(String testCaseName) {
        this.testCaseName = testCaseName;
    }
    
    public String getTestCaseStatus() {
        return testCaseStatus;
    }
    
    public void setTestCaseStatus(String testCaseStatus) {
        this.testCaseStatus = testCaseStatus;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }
    
    public LocalDateTime getEndTime() {
        return endTime;
    }
    
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
    
    public Map<String, Object> getAttributes() {
        return attributes;
    }
    
    public void setAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }
    
    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }
    
    /**
     * Mark test as passed
     */
    public void markPassed() {
        this.testCaseStatus = PASSED;
        this.endTime = LocalDateTime.now();
    }
    
    /**
     * Mark test as failed
     */
    public void markFailed(String message) {
        this.testCaseStatus = FAILED;
        this.message = message;
        this.endTime = LocalDateTime.now();
    }
    
    /**
     * Mark test as error
     */
    public void markError(String message) {
        this.testCaseStatus = ERROR;
        this.message = message;
        this.endTime = LocalDateTime.now();
    }
    
    /**
     * Check if test passed
     */
    public boolean isPassed() {
        return PASSED.equals(testCaseStatus);
    }
    
    /**
     * Check if test failed
     */
    public boolean isFailed() {
        return FAILED.equals(testCaseStatus) || ERROR.equals(testCaseStatus);
    }
    
    /**
     * Get execution duration in milliseconds
     */
    public long getDurationMs() {
        if (startTime == null) return 0;
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        return java.time.Duration.between(startTime, end).toMillis();
    }
    
    @Override
    public String toString() {
        return "TestCaseContext{" +
                "testCaseId='" + testCaseId + '\'' +
                ", status='" + testCaseStatus + '\'' +
                ", duration=" + getDurationMs() + "ms" +
                '}';
    }
}
