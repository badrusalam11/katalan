package com.kms.katalon.core.testcase;

/**
 * Katalon compatibility class for TestCaseFactory
 */
public class TestCaseFactory {
    
    /**
     * Find a test case by ID
     */
    public static TestCase findTestCase(String testCaseId) {
        return new TestCase(testCaseId);
    }
    
    public static TestCase findTestCase(String testCaseId, int retryCount) {
        return new TestCase(testCaseId);
    }
}
