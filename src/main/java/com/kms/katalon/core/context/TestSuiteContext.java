package com.kms.katalon.core.context;

/**
 * Katalon-compatible {@code TestSuiteContext}.
 * <p>Provides information about the currently executing test suite to
 * Test Listener methods annotated with
 * {@link com.kms.katalon.core.annotation.BeforeTestSuite},
 * {@link com.kms.katalon.core.annotation.AfterTestSuite},
 * {@link com.kms.katalon.core.annotation.SetUp} or
 * {@link com.kms.katalon.core.annotation.TearDown}.</p>
 */
public class TestSuiteContext {

    private String testSuiteId;
    private String testSuiteStatus = "NOT_RUN";
    private String message;
    private Throwable testSuiteError;
    private String reportLocation;

    public TestSuiteContext() {}

    public TestSuiteContext(String testSuiteId) {
        this.testSuiteId = testSuiteId;
    }

    public String getTestSuiteId() {
        return testSuiteId;
    }

    public void setTestSuiteId(String testSuiteId) {
        this.testSuiteId = testSuiteId;
    }

    public String getTestSuiteStatus() {
        return testSuiteStatus;
    }

    public void setTestSuiteStatus(String testSuiteStatus) {
        this.testSuiteStatus = testSuiteStatus;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Throwable getTestSuiteError() {
        return testSuiteError;
    }

    public void setTestSuiteError(Throwable testSuiteError) {
        this.testSuiteError = testSuiteError;
    }

    public String getReportLocation() {
        return reportLocation;
    }

    public void setReportLocation(String reportLocation) {
        this.reportLocation = reportLocation;
    }
}
