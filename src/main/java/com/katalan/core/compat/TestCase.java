package com.katalan.core.compat;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Katalon-compatible TestCase class
 * Represents a test case with its metadata and variables
 */
public class TestCase {
    
    private String testCaseId;
    private String testCaseName;
    private String description;
    private Path scriptPath;
    private Map<String, Object> variables;
    
    public TestCase() {
        this.variables = new HashMap<>();
    }
    
    public TestCase(String testCaseId, String testCaseName) {
        this();
        this.testCaseId = testCaseId;
        this.testCaseName = testCaseName;
    }
    
    public TestCase(String testCaseId, String testCaseName, Path scriptPath) {
        this(testCaseId, testCaseName);
        this.scriptPath = scriptPath;
    }
    
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
    
    public Map<String, Object> getVariables() {
        return variables;
    }
    
    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }
    
    public void setVariable(String name, Object value) {
        this.variables.put(name, value);
    }
    
    public Object getVariable(String name) {
        return this.variables.get(name);
    }
    
    @Override
    public String toString() {
        return "TestCase{" +
                "testCaseId='" + testCaseId + '\'' +
                ", testCaseName='" + testCaseName + '\'' +
                '}';
    }
}
