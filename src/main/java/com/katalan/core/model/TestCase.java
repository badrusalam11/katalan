package com.katalan.core.model;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a Katalon Test Case
 */
public class TestCase {
    
    private String id;
    private String name;
    private String description;
    private Path scriptPath;
    private String scriptContent;
    private Map<String, Object> variables;
    private TestCaseStatus status;
    private Instant startTime;
    private Instant endTime;
    private String errorMessage;
    private String stackTrace;
    private int retryCount;
    private int maxRetries;
    
    public enum TestCaseStatus {
        NOT_RUN,
        RUNNING,
        PASSED,
        FAILED,
        ERROR,
        SKIPPED
    }
    
    public TestCase() {
        this.variables = new HashMap<>();
        this.status = TestCaseStatus.NOT_RUN;
        this.maxRetries = 0;
        this.retryCount = 0;
    }
    
    public TestCase(String name) {
        this();
        this.name = name;
        this.id = name.replaceAll("\\s+", "_").toLowerCase();
    }
    
    public TestCase(String name, Path scriptPath) {
        this(name);
        this.scriptPath = scriptPath;
    }
    
    /**
     * Get execution duration
     */
    public Duration getDuration() {
        if (startTime == null) {
            return Duration.ZERO;
        }
        Instant end = endTime != null ? endTime : Instant.now();
        return Duration.between(startTime, end);
    }
    
    /**
     * Mark test as started
     */
    public void markStarted() {
        this.startTime = Instant.now();
        this.status = TestCaseStatus.RUNNING;
    }
    
    /**
     * Mark test as passed
     */
    public void markPassed() {
        this.endTime = Instant.now();
        this.status = TestCaseStatus.PASSED;
    }
    
    /**
     * Mark test as failed
     */
    public void markFailed(String errorMessage, String stackTrace) {
        this.endTime = Instant.now();
        this.status = TestCaseStatus.FAILED;
        this.errorMessage = errorMessage;
        this.stackTrace = stackTrace;
    }
    
    /**
     * Mark test as error
     */
    public void markError(String errorMessage, String stackTrace) {
        this.endTime = Instant.now();
        this.status = TestCaseStatus.ERROR;
        this.errorMessage = errorMessage;
        this.stackTrace = stackTrace;
    }
    
    /**
     * Mark test as skipped
     */
    public void markSkipped(String reason) {
        this.endTime = Instant.now();
        this.status = TestCaseStatus.SKIPPED;
        this.errorMessage = reason;
    }
    
    /**
     * Check if retry is possible
     */
    public boolean canRetry() {
        return retryCount < maxRetries;
    }
    
    /**
     * Increment retry counter
     */
    public void incrementRetry() {
        this.retryCount++;
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Path getScriptPath() {
        return scriptPath;
    }
    
    public void setScriptPath(Path scriptPath) {
        this.scriptPath = scriptPath;
    }
    
    public String getScriptContent() {
        return scriptContent;
    }
    
    public void setScriptContent(String scriptContent) {
        this.scriptContent = scriptContent;
    }
    
    public Map<String, Object> getVariables() {
        return variables;
    }
    
    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }
    
    public void addVariable(String key, Object value) {
        this.variables.put(key, value);
    }
    
    public TestCaseStatus getStatus() {
        return status;
    }
    
    public void setStatus(TestCaseStatus status) {
        this.status = status;
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
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public String getStackTrace() {
        return stackTrace;
    }
    
    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }
    
    public int getRetryCount() {
        return retryCount;
    }
    
    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }
    
    public int getMaxRetries() {
        return maxRetries;
    }
    
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
    
    @Override
    public String toString() {
        return "TestCase{" +
                "name='" + name + '\'' +
                ", status=" + status +
                ", duration=" + getDuration().toMillis() + "ms" +
                '}';
    }
}
