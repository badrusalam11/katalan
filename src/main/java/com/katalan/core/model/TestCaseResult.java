package com.katalan.core.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents results from a test case execution
 */
public class TestCaseResult {
    
    private String testCaseId;
    private String testCaseName;
    private Instant startTime;
    private Instant endTime;
    private TestCase.TestCaseStatus status;
    private String errorMessage;
    private String stackTrace;
    private List<StepResult> stepResults;
    private List<String> screenshotPaths;
    private int retryAttempt;
    
    public TestCaseResult() {
        this.stepResults = new ArrayList<>();
        this.screenshotPaths = new ArrayList<>();
        this.status = TestCase.TestCaseStatus.NOT_RUN;
        this.retryAttempt = 0;
    }
    
    public TestCaseResult(String testCaseName) {
        this();
        this.testCaseName = testCaseName;
        this.testCaseId = testCaseName.replaceAll("\\s+", "_").toLowerCase();
    }
    
    public TestCaseResult(TestCase testCase) {
        this();
        this.testCaseId = testCase.getId();
        this.testCaseName = testCase.getName();
        this.startTime = testCase.getStartTime();
        this.endTime = testCase.getEndTime();
        this.status = testCase.getStatus();
        this.errorMessage = testCase.getErrorMessage();
        this.stackTrace = testCase.getStackTrace();
        this.retryAttempt = testCase.getRetryCount();
    }
    
    public void addStepResult(StepResult stepResult) {
        this.stepResults.add(stepResult);
    }
    
    public void addScreenshot(String path) {
        this.screenshotPaths.add(path);
    }
    
    public Duration getDuration() {
        if (startTime == null) {
            return Duration.ZERO;
        }
        Instant end = endTime != null ? endTime : Instant.now();
        return Duration.between(startTime, end);
    }
    
    public String getDurationFormatted() {
        Duration duration = getDuration();
        long seconds = duration.getSeconds();
        long millis = duration.toMillis() % 1000;
        
        if (seconds < 60) {
            return String.format("%d.%03ds", seconds, millis);
        }
        
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%dm %d.%03ds", minutes, seconds, millis);
    }
    
    public void markStarted() {
        this.startTime = Instant.now();
        this.status = TestCase.TestCaseStatus.RUNNING;
    }
    
    public void markPassed() {
        this.endTime = Instant.now();
        this.status = TestCase.TestCaseStatus.PASSED;
    }
    
    public void markFailed(String errorMessage, String stackTrace) {
        this.endTime = Instant.now();
        this.status = TestCase.TestCaseStatus.FAILED;
        this.errorMessage = errorMessage;
        this.stackTrace = stackTrace;
    }
    
    public void markError(String errorMessage, String stackTrace) {
        this.endTime = Instant.now();
        this.status = TestCase.TestCaseStatus.ERROR;
        this.errorMessage = errorMessage;
        this.stackTrace = stackTrace;
    }
    
    public void markSkipped(String reason) {
        this.endTime = Instant.now();
        this.status = TestCase.TestCaseStatus.SKIPPED;
        this.errorMessage = reason;
    }
    
    // Getters and Setters
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
    
    public TestCase.TestCaseStatus getStatus() {
        return status;
    }
    
    public void setStatus(TestCase.TestCaseStatus status) {
        this.status = status;
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
    
    public List<StepResult> getStepResults() {
        return stepResults;
    }
    
    public void setStepResults(List<StepResult> stepResults) {
        this.stepResults = stepResults;
    }
    
    public List<String> getScreenshotPaths() {
        return screenshotPaths;
    }
    
    public void setScreenshotPaths(List<String> screenshotPaths) {
        this.screenshotPaths = screenshotPaths;
    }
    
    public int getRetryAttempt() {
        return retryAttempt;
    }
    
    public void setRetryAttempt(int retryAttempt) {
        this.retryAttempt = retryAttempt;
    }
    
    /**
     * Nested class for step-level results
     */
    public static class StepResult {
        private int stepNumber;
        private String stepName;
        private String keyword;
        private String description;
        private Instant startTime;
        private Instant endTime;
        private boolean passed;
        private String errorMessage;
        private String screenshotPath;
        
        public StepResult() {}
        
        public StepResult(int stepNumber, String stepName) {
            this.stepNumber = stepNumber;
            this.stepName = stepName;
        }
        
        public Duration getDuration() {
            if (startTime == null) {
                return Duration.ZERO;
            }
            Instant end = endTime != null ? endTime : Instant.now();
            return Duration.between(startTime, end);
        }
        
        // Getters and Setters
        public int getStepNumber() {
            return stepNumber;
        }
        
        public void setStepNumber(int stepNumber) {
            this.stepNumber = stepNumber;
        }
        
        public String getStepName() {
            return stepName;
        }
        
        public void setStepName(String stepName) {
            this.stepName = stepName;
        }
        
        public String getKeyword() {
            return keyword;
        }
        
        public void setKeyword(String keyword) {
            this.keyword = keyword;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
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
        
        public boolean isPassed() {
            return passed;
        }
        
        public void setPassed(boolean passed) {
            this.passed = passed;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
        
        public String getScreenshotPath() {
            return screenshotPath;
        }
        
        public void setScreenshotPath(String screenshotPath) {
            this.screenshotPath = screenshotPath;
        }
    }
}
