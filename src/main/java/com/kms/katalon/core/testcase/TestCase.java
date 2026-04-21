package com.kms.katalon.core.testcase;

/**
 * Katalon compatibility class for TestCase
 */
public class TestCase {
    private String testCaseId;
    private String name;
    
    public TestCase() {}
    
    public TestCase(String testCaseId) {
        this.testCaseId = testCaseId;
        this.name = testCaseId;
    }
    
    public String getTestCaseId() {
        return testCaseId;
    }
    
    public void setTestCaseId(String testCaseId) {
        this.testCaseId = testCaseId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
}
