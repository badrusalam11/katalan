package com.kms.katalon.core.context;

import java.util.Map;

/**
 * Katalon-compatible {@code TestCaseContext}.
 * <p>Provides information about the currently executing test case to
 * Test Listener methods annotated with
 * {@link com.kms.katalon.core.annotation.BeforeTestCase} or
 * {@link com.kms.katalon.core.annotation.AfterTestCase}.</p>
 *
 * <p>This is a concrete class (not an interface as in the original
 * Katalon API) so that listener scripts written for Katalon continue
 * to compile and run unchanged in Katalan.</p>
 */
public class TestCaseContext {

    private String testCaseId;
    private String testCaseStatus = "NOT_RUN";
    private String message;
    private Throwable testCaseError;
    private Map<String, Object> testCaseVariables;

    public TestCaseContext() {}

    public TestCaseContext(String testCaseId) {
        this.testCaseId = testCaseId;
    }

    public String getTestCaseId() {
        return testCaseId;
    }

    public void setTestCaseId(String testCaseId) {
        this.testCaseId = testCaseId;
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

    public Throwable getTestCaseError() {
        return testCaseError;
    }

    public void setTestCaseError(Throwable testCaseError) {
        this.testCaseError = testCaseError;
    }

    public Map<String, Object> getTestCaseVariables() {
        return testCaseVariables;
    }

    public void setTestCaseVariables(Map<String, Object> testCaseVariables) {
        this.testCaseVariables = testCaseVariables;
    }
}
