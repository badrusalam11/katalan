package com.katalan.core.model;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Katalon Test Suite
 */
public class TestSuite {
    
    private String id;
    private String name;
    private String description;
    private Path suitePath;
    private List<TestCase> testCases;
    private Instant startTime;
    private Instant endTime;
    private int parallel;
    private boolean failFast;
    
    public TestSuite() {
        this.testCases = new ArrayList<>();
        this.parallel = 1;
        this.failFast = false;
    }
    
    public TestSuite(String name) {
        this();
        this.name = name;
        this.id = name.replaceAll("\\s+", "_").toLowerCase();
    }
    
    /**
     * Add a test case to the suite
     */
    public void addTestCase(TestCase testCase) {
        this.testCases.add(testCase);
    }
    
    /**
     * Get total execution duration
     */
    public Duration getDuration() {
        if (startTime == null) {
            return Duration.ZERO;
        }
        Instant end = endTime != null ? endTime : Instant.now();
        return Duration.between(startTime, end);
    }
    
    /**
     * Get count of passed tests
     */
    public long getPassedCount() {
        return testCases.stream()
                .filter(tc -> tc.getStatus() == TestCase.TestCaseStatus.PASSED)
                .count();
    }
    
    /**
     * Get count of failed tests
     */
    public long getFailedCount() {
        return testCases.stream()
                .filter(tc -> tc.getStatus() == TestCase.TestCaseStatus.FAILED)
                .count();
    }
    
    /**
     * Get count of error tests
     */
    public long getErrorCount() {
        return testCases.stream()
                .filter(tc -> tc.getStatus() == TestCase.TestCaseStatus.ERROR)
                .count();
    }
    
    /**
     * Get count of skipped tests
     */
    public long getSkippedCount() {
        return testCases.stream()
                .filter(tc -> tc.getStatus() == TestCase.TestCaseStatus.SKIPPED)
                .count();
    }
    
    /**
     * Get total test count
     */
    public int getTotalCount() {
        return testCases.size();
    }
    
    /**
     * Check if all tests passed
     */
    public boolean isSuccess() {
        return testCases.stream()
                .allMatch(tc -> tc.getStatus() == TestCase.TestCaseStatus.PASSED 
                        || tc.getStatus() == TestCase.TestCaseStatus.SKIPPED);
    }
    
    /**
     * Get pass rate percentage
     */
    public double getPassRate() {
        if (testCases.isEmpty()) {
            return 0.0;
        }
        return (double) getPassedCount() / getTotalCount() * 100;
    }
    
    /**
     * Mark suite as started
     */
    public void markStarted() {
        this.startTime = Instant.now();
    }
    
    /**
     * Mark suite as completed
     */
    public void markCompleted() {
        this.endTime = Instant.now();
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
    
    public Path getSuitePath() {
        return suitePath;
    }
    
    public void setSuitePath(Path suitePath) {
        this.suitePath = suitePath;
    }
    
    public List<TestCase> getTestCases() {
        return testCases;
    }
    
    public void setTestCases(List<TestCase> testCases) {
        this.testCases = testCases;
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
    
    public int getParallel() {
        return parallel;
    }
    
    public void setParallel(int parallel) {
        this.parallel = parallel;
    }
    
    public boolean isFailFast() {
        return failFast;
    }
    
    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }
    
    @Override
    public String toString() {
        return "TestSuite{" +
                "name='" + name + '\'' +
                ", testCases=" + testCases.size() +
                ", passed=" + getPassedCount() +
                ", failed=" + getFailedCount() +
                '}';
    }
}
