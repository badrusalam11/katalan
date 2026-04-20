package com.katalan.core.context;

import com.katalan.core.config.RunConfiguration;
import com.katalan.core.model.ExecutionResult;
import com.katalan.core.model.TestObject;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * Execution context that holds the current state during test execution
 * Similar to Katalon's internal context
 */
public class ExecutionContext {
    
    private static final Logger logger = LoggerFactory.getLogger(ExecutionContext.class);
    
    // Thread-local context for parallel execution support
    private static final ThreadLocal<ExecutionContext> contextHolder = new ThreadLocal<>();
    
    private WebDriver webDriver;
    private RunConfiguration runConfiguration;
    private ExecutionResult executionResult;
    private Map<String, Object> globalVariables;
    private Map<String, Object> executionVariables;
    private Map<String, TestObject> objectRepository;
    private Stack<Map<String, Object>> variableStack;
    private Path projectPath;
    private Path currentTestCasePath;
    private String currentTestCaseName;
    private String currentTestSuiteName;
    private Map<String, Object> properties;
    private boolean shouldStop;
    
    public ExecutionContext() {
        this.globalVariables = new HashMap<>();
        this.executionVariables = new HashMap<>();
        this.objectRepository = new HashMap<>();
        this.variableStack = new Stack<>();
        this.properties = new HashMap<>();
        this.shouldStop = false;
    }
    
    public ExecutionContext(RunConfiguration config) {
        this();
        this.runConfiguration = config;
        this.projectPath = config.getProjectPath();
    }
    
    /**
     * Get the current thread's execution context
     */
    public static ExecutionContext getCurrent() {
        ExecutionContext context = contextHolder.get();
        if (context == null) {
            context = new ExecutionContext();
            contextHolder.set(context);
        }
        return context;
    }
    
    /**
     * Set the current thread's execution context
     */
    public static void setCurrent(ExecutionContext context) {
        contextHolder.set(context);
    }
    
    /**
     * Clear the current thread's execution context
     */
    public static void clearCurrent() {
        contextHolder.remove();
    }
    
    /**
     * Push a new variable scope (for nested calls)
     */
    public void pushVariableScope() {
        variableStack.push(new HashMap<>(executionVariables));
    }
    
    /**
     * Pop the variable scope
     */
    public void popVariableScope() {
        if (!variableStack.isEmpty()) {
            executionVariables = variableStack.pop();
        }
    }
    
    /**
     * Set a variable in the current execution scope
     */
    public void setVariable(String name, Object value) {
        executionVariables.put(name, value);
    }
    
    /**
     * Get a variable from the current execution scope
     */
    public Object getVariable(String name) {
        if (executionVariables.containsKey(name)) {
            return executionVariables.get(name);
        }
        return globalVariables.get(name);
    }
    
    /**
     * Set a global variable
     */
    public void setGlobalVariable(String name, Object value) {
        globalVariables.put(name, value);
    }
    
    /**
     * Get a global variable
     */
    public Object getGlobalVariable(String name) {
        return globalVariables.get(name);
    }
    
    /**
     * Register a test object in the repository
     */
    public void registerTestObject(String id, TestObject testObject) {
        objectRepository.put(id, testObject);
    }
    
    /**
     * Get a test object from the repository
     */
    public TestObject getTestObject(String id) {
        return objectRepository.get(id);
    }
    
    /**
     * Find test object by path (Katalon style: "Object Repository/Page/element")
     */
    public TestObject findTestObject(String path) {
        // First try exact match
        if (objectRepository.containsKey(path)) {
            return objectRepository.get(path);
        }
        
        // Try normalized path
        String normalized = path.replace("\\", "/");
        if (objectRepository.containsKey(normalized)) {
            return objectRepository.get(normalized);
        }
        
        // Try without "Object Repository/" prefix
        if (normalized.startsWith("Object Repository/")) {
            String withoutPrefix = normalized.substring("Object Repository/".length());
            if (objectRepository.containsKey(withoutPrefix)) {
                return objectRepository.get(withoutPrefix);
            }
        }
        
        logger.warn("Test object not found: {}", path);
        return null;
    }
    
    /**
     * Request to stop execution
     */
    public void requestStop() {
        this.shouldStop = true;
    }
    
    /**
     * Check if execution should stop
     */
    public boolean shouldStop() {
        return shouldStop;
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        if (webDriver != null) {
            try {
                webDriver.quit();
            } catch (Exception e) {
                logger.warn("Error closing WebDriver: {}", e.getMessage());
            }
            webDriver = null;
        }
        clearCurrent();
    }
    
    // Getters and Setters
    public WebDriver getWebDriver() {
        return webDriver;
    }
    
    public void setWebDriver(WebDriver webDriver) {
        this.webDriver = webDriver;
    }
    
    public RunConfiguration getRunConfiguration() {
        return runConfiguration;
    }
    
    public void setRunConfiguration(RunConfiguration runConfiguration) {
        this.runConfiguration = runConfiguration;
    }
    
    public ExecutionResult getExecutionResult() {
        return executionResult;
    }
    
    public void setExecutionResult(ExecutionResult executionResult) {
        this.executionResult = executionResult;
    }
    
    public Map<String, Object> getGlobalVariables() {
        return globalVariables;
    }
    
    public void setGlobalVariables(Map<String, Object> globalVariables) {
        this.globalVariables = globalVariables;
    }
    
    public Map<String, Object> getExecutionVariables() {
        return executionVariables;
    }
    
    public void setExecutionVariables(Map<String, Object> executionVariables) {
        this.executionVariables = executionVariables;
    }
    
    public Map<String, TestObject> getObjectRepository() {
        return objectRepository;
    }
    
    public void setObjectRepository(Map<String, TestObject> objectRepository) {
        this.objectRepository = objectRepository;
    }
    
    public Path getProjectPath() {
        return projectPath;
    }
    
    public void setProjectPath(Path projectPath) {
        this.projectPath = projectPath;
    }
    
    public Path getCurrentTestCasePath() {
        return currentTestCasePath;
    }
    
    public void setCurrentTestCasePath(Path currentTestCasePath) {
        this.currentTestCasePath = currentTestCasePath;
    }
    
    public String getCurrentTestCaseName() {
        return currentTestCaseName;
    }
    
    public void setCurrentTestCaseName(String currentTestCaseName) {
        this.currentTestCaseName = currentTestCaseName;
    }
    
    public String getCurrentTestSuiteName() {
        return currentTestSuiteName;
    }
    
    public void setCurrentTestSuiteName(String currentTestSuiteName) {
        this.currentTestSuiteName = currentTestSuiteName;
    }
    
    /**
     * Set an internal property (for framework use)
     */
    public void setProperty(String key, Object value) {
        this.properties.put(key, value);
    }
    
    /**
     * Get an internal property
     */
    public Object getProperty(String key) {
        return this.properties.get(key);
    }
    
    /**
     * Get a unique session ID for this execution
     */
    public String getSessionId() {
        String sessionId = (String) getProperty("sessionId");
        if (sessionId == null) {
            sessionId = "katalan-" + System.currentTimeMillis();
            setProperty("sessionId", sessionId);
        }
        return sessionId;
    }
    
    /**
     * Get the project directory
     */
    public Path getProjectDir() {
        return projectPath;
    }
}
