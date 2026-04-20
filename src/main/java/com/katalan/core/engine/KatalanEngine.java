package com.katalan.core.engine;

import com.katalan.core.config.RunConfiguration;
import com.katalan.core.context.ExecutionContext;
import com.katalan.core.driver.WebDriverFactory;
import com.katalan.core.model.*;
import com.katalan.core.exception.StepFailedException;
import com.katalan.core.compat.GlobalVariable;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Main katalan Engine - Orchestrates test execution
 */
public class KatalanEngine {
    
    private static final Logger logger = LoggerFactory.getLogger(KatalanEngine.class);
    
    private final RunConfiguration config;
    private final ExecutionContext context;
    private final GroovyScriptExecutor scriptExecutor;
    private final TestSuiteParser suiteParser;
    private ExecutionResult executionResult;
    
    public KatalanEngine(RunConfiguration config) {
        this.config = config;
        this.context = new ExecutionContext(config);
        ExecutionContext.setCurrent(context);
        // Set context for ObjectRepository static methods
        com.katalan.core.compat.ObjectRepository.setContext(context);
        this.scriptExecutor = new GroovyScriptExecutor(context);
        this.suiteParser = new TestSuiteParser(config.getProjectPath());
        this.executionResult = new ExecutionResult();
    }
    
    /**
     * Initialize the engine
     */
    public void initialize() throws IOException {
        logger.info("Initializing katalan Engine");
        
        // Load Object Repository
        if (config.getProjectPath() != null && Files.exists(config.getProjectPath())) {
            Map<String, TestObject> repository = ObjectRepositoryParser.loadObjectRepository(config.getProjectPath());
            context.setObjectRepository(repository);
            
            // Load Global Variables from Profiles
            loadGlobalVariables();
        }
        
        // Create WebDriver
        WebDriver driver = WebDriverFactory.createDriver(config);
        context.setWebDriver(driver);
        
        logger.info("katalan Engine initialized");
    }
    
    /**
     * Load global variables from execution profile
     */
    private void loadGlobalVariables() {
        try {
            Path profilesPath = config.getProjectPath().resolve("Profiles");
            if (!Files.exists(profilesPath)) {
                logger.debug("No Profiles directory found");
                return;
            }
            
            // Determine which profile to load
            String profileName = config.getExecutionProfile();
            if (profileName == null || profileName.isEmpty()) {
                profileName = "default";
            }
            
            Path profilePath = profilesPath.resolve(profileName + ".glbl");
            if (!Files.exists(profilePath)) {
                // Try without extension
                profilePath = profilesPath.resolve(profileName);
            }
            
            if (Files.exists(profilePath)) {
                Map<String, Object> variables = GlobalVariableLoader.loadFromProfile(profilePath);
                GlobalVariable.loadAll(variables);
                
                // Also add to script executor binding
                for (Map.Entry<String, Object> entry : variables.entrySet()) {
                    scriptExecutor.setVariable(entry.getKey(), entry.getValue());
                }
                
                logger.info("Loaded {} global variables from profile: {}", 
                        variables.size(), profilePath.getFileName());
            } else {
                logger.debug("Profile not found: {}", profilePath);
            }
            
        } catch (Exception e) {
            logger.warn("Failed to load global variables: {}", e.getMessage());
        }
    }
    
    /**
     * Execute a test suite
     */
    public ExecutionResult executeTestSuite(TestSuite suite) {
        logger.info("Executing test suite: {}", suite.getName());
        
        executionResult.setName(suite.getName());
        executionResult.markStarted();
        context.setCurrentTestSuiteName(suite.getName());
        
        TestSuiteResult suiteResult = new TestSuiteResult(suite.getName());
        suiteResult.markStarted();
        
        suite.markStarted();
        
        for (TestCase testCase : suite.getTestCases()) {
            if (context.shouldStop()) {
                logger.warn("Execution stopped by request");
                break;
            }
            
            TestCaseResult tcResult = executeTestCase(testCase);
            suiteResult.addTestCaseResult(tcResult);
            
            // Clean up browser after each test case for test isolation
            cleanupBrowserAfterTestCase();
            
            // Fail fast mode
            if (config.isFailFast() && tcResult.getStatus() != TestCase.TestCaseStatus.PASSED) {
                logger.warn("Fail fast mode: stopping execution after failure");
                break;
            }
        }
        
        suite.markCompleted();
        suiteResult.markCompleted();
        
        executionResult.addSuiteResult(suiteResult);
        executionResult.markCompleted();
        
        logger.info("Test suite completed: {} - Passed: {}, Failed: {}, Errors: {}",
                suite.getName(),
                suiteResult.getPassedTests(),
                suiteResult.getFailedTests(),
                suiteResult.getErrorTests());
        
        return executionResult;
    }
    
    /**
     * Execute a test suite from file
     */
    public ExecutionResult executeTestSuite(Path suitePath) throws IOException {
        TestSuite suite = suiteParser.parseTestSuite(suitePath);
        return executeTestSuite(suite);
    }
    
    /**
     * Execute a single test case
     */
    public TestCaseResult executeTestCase(TestCase testCase) {
        logger.info("Executing test case: {}", testCase.getName());
        
        TestCaseResult result = new TestCaseResult(testCase.getName());
        result.markStarted();
        testCase.markStarted();
        
        context.setCurrentTestCaseName(testCase.getName());
        context.setCurrentTestCasePath(testCase.getScriptPath());
        
        int attempts = 0;
        int maxAttempts = config.getRetryFailedTests() + 1;
        
        while (attempts < maxAttempts) {
            attempts++;
            
            try {
                // Add test case variables to script (resolve GlobalVariableReferences)
                if (testCase.getVariables() != null) {
                    for (Map.Entry<String, Object> var : testCase.getVariables().entrySet()) {
                        Object value = var.getValue();
                        // Resolve GlobalVariableReference to actual value
                        if (value instanceof TestSuiteParser.GlobalVariableReference) {
                            value = ((TestSuiteParser.GlobalVariableReference) value).resolve();
                            logger.debug("Resolved test case variable {} = {}", var.getKey(), value);
                        }
                        scriptExecutor.setVariable(var.getKey(), value);
                    }
                }
                
                // Execute the script
                if (testCase.getScriptContent() != null) {
                    scriptExecutor.executeScript(testCase.getScriptContent(), testCase.getName());
                } else if (testCase.getScriptPath() != null) {
                    scriptExecutor.executeScript(testCase.getScriptPath());
                } else {
                    throw new IllegalStateException("No script content or path provided for test case: " + testCase.getName());
                }
                
                // Check if this was a BDD test and capture the info
                Object isBddTest = context.getProperty("isBddTest");
                if (Boolean.TRUE.equals(isBddTest)) {
                    result.setBddTest(true);
                    Object featureFile = context.getProperty("featureFile");
                    if (featureFile != null) {
                        result.setFeatureFile(featureFile.toString());
                    }
                    
                    // Capture hierarchical BDD scenario data
                    Object bddScenarioData = context.getProperty("bddScenarioData");
                    if (bddScenarioData instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> scenarioData = (List<Map<String, Object>>) bddScenarioData;
                        result.setBddScenarioData(scenarioData);
                        logger.debug("Captured {} BDD scenarios for {}", scenarioData.size(), testCase.getName());
                    }
                    
                    // Clear BDD tracking for next test case
                    context.setProperty("isBddTest", null);
                    context.setProperty("featureFile", null);
                    context.setProperty("bddScenarioData", null);
                }
                
                // Test passed
                result.markPassed();
                testCase.markPassed();
                
                if (config.isTakeScreenshotOnSuccess()) {
                    takeScreenshot(result, "success");
                }
                
                logger.info("Test case PASSED: {}", testCase.getName());
                break;
                
            } catch (StepFailedException e) {
                handleTestFailure(testCase, result, e, attempts, maxAttempts);
                if (result.getStatus() == TestCase.TestCaseStatus.PASSED) {
                    break; // Retry succeeded
                }
            } catch (Exception e) {
                handleTestError(testCase, result, e, attempts, maxAttempts);
                if (result.getStatus() == TestCase.TestCaseStatus.PASSED) {
                    break; // Retry succeeded
                }
            }
        }
        
        result.setRetryAttempt(attempts - 1);
        return result;
    }
    
    /**
     * Execute a single test case from file
     */
    public TestCaseResult executeTestCase(Path testCasePath) throws IOException {
        TestCase testCase = new TestCase();
        testCase.setName(testCasePath.getFileName().toString().replace(".groovy", ""));
        testCase.setScriptPath(testCasePath);
        testCase.setScriptContent(Files.readString(testCasePath));
        return executeTestCase(testCase);
    }
    
    /**
     * Execute a Groovy script directly
     */
    public Object executeScript(String script) {
        return scriptExecutor.executeScript(script);
    }
    
    /**
     * Execute a Groovy script file directly
     */
    public Object executeScript(Path scriptPath) throws IOException {
        return scriptExecutor.executeScript(scriptPath);
    }
    
    /**
     * Handle test case failure
     */
    private void handleTestFailure(TestCase testCase, TestCaseResult result, 
                                    StepFailedException e, int attempt, int maxAttempts) {
        String errorMessage = e.getMessage();
        String stackTrace = getStackTraceString(e);
        
        if (attempt < maxAttempts) {
            logger.warn("Test case FAILED (attempt {}/{}): {} - Retrying...", 
                    attempt, maxAttempts, testCase.getName());
            testCase.incrementRetry();
        } else {
            logger.error("Test case FAILED: {} - {}", testCase.getName(), errorMessage);
            result.markFailed(errorMessage, stackTrace);
            testCase.markFailed(errorMessage, stackTrace);
            
            if (config.isTakeScreenshotOnFailure()) {
                takeScreenshot(result, "failure");
            }
        }
    }
    
    /**
     * Handle test case error
     */
    private void handleTestError(TestCase testCase, TestCaseResult result, 
                                  Exception e, int attempt, int maxAttempts) {
        String errorMessage = e.getMessage();
        String stackTrace = getStackTraceString(e);
        
        if (attempt < maxAttempts) {
            logger.warn("Test case ERROR (attempt {}/{}): {} - Retrying...", 
                    attempt, maxAttempts, testCase.getName());
            testCase.incrementRetry();
        } else {
            logger.error("Test case ERROR: {} - {}", testCase.getName(), errorMessage, e);
            result.markError(errorMessage, stackTrace);
            testCase.markError(errorMessage, stackTrace);
            
            if (config.isTakeScreenshotOnFailure()) {
                takeScreenshot(result, "error");
            }
        }
    }
    
    /**
     * Take screenshot and add to result
     */
    private void takeScreenshot(TestCaseResult result, String suffix) {
        try {
            WebDriver driver = context.getWebDriver();
            if (driver != null && driver instanceof TakesScreenshot) {
                String filename = result.getTestCaseName().replaceAll("[^a-zA-Z0-9_-]", "_") 
                        + "_" + suffix + "_" + Instant.now().toEpochMilli();
                
                byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
                
                Path screenshotDir = config.getScreenshotPath();
                if (screenshotDir == null) {
                    screenshotDir = config.getReportPath() != null 
                            ? config.getReportPath().resolve("screenshots")
                            : Path.of("screenshots");
                }
                Files.createDirectories(screenshotDir);
                
                Path screenshotPath = screenshotDir.resolve(filename + ".png");
                Files.write(screenshotPath, screenshot);
                result.addScreenshot(screenshotPath.toString());
                
                logger.debug("Screenshot saved: {}", screenshotPath);
            }
        } catch (Exception ex) {
            logger.warn("Failed to take screenshot: {}", ex.getMessage());
        }
    }
    
    /**
     * Get stack trace as string
     */
    private String getStackTraceString(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
    
    /**
     * Get the execution context
     */
    public ExecutionContext getContext() {
        return context;
    }
    
    /**
     * Get the execution result
     */
    public ExecutionResult getExecutionResult() {
        return executionResult;
    }
    
    /**
     * Clean up browser after each test case for test isolation
     */
    private void cleanupBrowserAfterTestCase() {
        WebDriver driver = context.getWebDriver();
        if (driver != null) {
            try {
                driver.quit();
                logger.debug("Browser cleaned up after test case");
            } catch (Exception e) {
                logger.warn("Failed to cleanup browser: {}", e.getMessage());
            }
            context.setWebDriver(null);
        }
    }
    
    /**
     * Shutdown the engine
     */
    public void shutdown() {
        logger.info("Shutting down katalan Engine");
        context.cleanup();
    }
}
