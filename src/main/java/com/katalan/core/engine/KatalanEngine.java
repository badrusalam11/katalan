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
    private final TestListenerRegistry listenerRegistry;
    private final ConsoleOutputCapturer consoleCapturer;
    private ExecutionResult executionResult;
    
    public KatalanEngine(RunConfiguration config) {
        this.config = config;
        this.context = new ExecutionContext(config);
        ExecutionContext.setCurrent(context);
        // Set context for ObjectRepository static methods
        com.katalan.core.compat.ObjectRepository.setContext(context);
        this.scriptExecutor = new GroovyScriptExecutor(context);
        this.suiteParser = new TestSuiteParser(config.getProjectPath());
        this.listenerRegistry = new TestListenerRegistry();
        this.consoleCapturer = new ConsoleOutputCapturer();
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

            // Append project JARs (Drivers/, Libs/, bin/lib/) to the SYSTEM
            // classloader so that keywords which internally spin up their own
            // `new GroovyShell()` (e.g. denstoo.reporting.CSReport) can resolve
            // project-local classes the same way they do inside Katalon Studio.
            SystemClasspathAppender.appendProjectJars(config.getProjectPath());
        }
        
        // DO NOT create WebDriver here!
        // CSWeb library checks DriverFactory.getWebDriver() to determine if it needs to open browser.
        // If driver exists, CSWeb skips URL navigation and only logs "Starting Chrome driver".
        // By not creating driver here, CSWeb will properly call WebUI.openBrowser(url).
        
        // Load Test Listeners (Katalon-compatible lifecycle hooks)
        try {
            listenerRegistry.loadListeners(config.getProjectPath(), scriptExecutor.getGroovyClassLoader());
        } catch (Exception e) {
            logger.warn("Failed to load Test Listeners: {}", e.getMessage());
        }
        
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
            
            // Propagate profile name to Katalon RunConfiguration compat so scripts that
            // call RunConfiguration.getExecutionProfile() see the actual CLI-passed value.
            com.kms.katalon.core.configuration.RunConfiguration.setExecutionProfile(profileName);
            
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
        suiteResult.setSuitePath(suite.getSuitePath());
        suiteResult.markStarted();
        
        suite.markStarted();
        
        // Initialize XmlKeywordLogger for this test run
        com.katalan.core.logging.XmlKeywordLogger kwLogger = com.katalan.core.logging.XmlKeywordLogger.getInstance();
        kwLogger.reset();
        
        // Log suite start with properties
        java.util.Map<String, String> suiteProps = new java.util.LinkedHashMap<>();
        suiteProps.put("rerunTestFailImmediately", "false");
        suiteProps.put("retryCount", "0");
        suiteProps.put("name", suite.getName());
        suiteProps.put("description", "");
        suiteProps.put("id", "Test Suites/" + suite.getId());
        kwLogger.startSuite(suite.getName(), "Test Suites/" + suite.getId(), suiteProps);
        
        // Log RUN_DATA (environment/runtime info)
        kwLogger.logRunData("userFullName", resolveUserFullName());
        kwLogger.logRunData("projectName", resolveProjectName());
        kwLogger.logRunData("hostName", resolveHostName());
        kwLogger.logRunData("os", System.getProperty("os.name") + " " + 
                System.getProperty("sun.arch.data.model", "64") + "bit");
        kwLogger.logRunData("hostAddress", "127.0.0.1");
        kwLogger.logRunData("katalonVersion", "10.3.2.0");
        
        // Add suiteResult to executionResult EARLY so prepareReportDirectory()
        // can determine correct folder structure (e.g. "Testing QIMSol" not "Test Suite")
        executionResult.addSuiteResult(suiteResult);
        
        // Prepare Katalon-style nested report folder BEFORE test cases run
        // This creates the folder structure + 4 essential files:
        // 1. execution.properties (full config)
        // 2. execution0.log (XML header, will be appended live during test execution)
        // 3. testCaseBinding (list of test cases to execute)
        // 4. console0.log (empty, will be appended during test execution)
        Path generatedReportPath = null;
        String absReportFolder = null;
        try {
            com.katalan.reporting.KatalonReportGenerator katalanReporter =
                    new com.katalan.reporting.KatalonReportGenerator(config.getProjectPath());
            generatedReportPath = katalanReporter.prepareReportDirectory(executionResult, suite);
            absReportFolder = generatedReportPath.toAbsolutePath().normalize()
                    .toString().replace("\\", "/");
            executionResult.setReportPath(absReportFolder);
            com.kms.katalon.core.configuration.RunConfiguration.setReportFolder(absReportFolder);
            System.setProperty("reportFolder", absReportFolder);
            
            // Set screenshot path to report directory so screenshots are saved there
            config.setScreenshotPath(generatedReportPath);
            
            logger.info("Report directory ready at: {}", generatedReportPath);
        } catch (Throwable t) {
            logger.error("Failed to prepare report directory: {}", t.toString(), t);
        }
        
        // Inject reportFolder into listener libraries
        if (absReportFolder != null) {
            injectReportFolderIntoListenerLibs(absReportFolder);
        } else {
            String fallbackFolder = config.getReportPath() != null 
                ? config.getReportPath().toAbsolutePath().toString() 
                : System.getProperty("user.dir") + "/Reports";
            injectReportFolderIntoListenerLibs(fallbackFolder);
            logger.warn("Using fallback reportFolder for injection: {}", fallbackFolder);
        }
        
        // ----- Test Listener: @BeforeTestSuite / @SetUp -----
        com.kms.katalon.core.context.TestSuiteContext suiteCtx =
                new com.kms.katalon.core.context.TestSuiteContext("Test Suites/" + suite.getName());
        suiteCtx.setTestSuiteStatus("RUNNING");
        if (absReportFolder != null) {
            suiteCtx.setReportLocation(absReportFolder);
        } else if (config.getReportPath() != null) {
            suiteCtx.setReportLocation(config.getReportPath().toString());
        }
        
        // Inject into listener classloaders before @BeforeTestSuite
        String finalReportFolder = absReportFolder != null 
            ? absReportFolder 
            : (config.getReportPath() != null 
                ? config.getReportPath().toAbsolutePath().toString() 
                : System.getProperty("user.dir") + "/Reports");
        listenerRegistry.injectReportFolderIntoListeners(finalReportFolder);
        
        try {
            listenerRegistry.invokeBeforeTestSuite(suiteCtx);
        } catch (Exception e) {
            logger.error("@BeforeTestSuite listener error: {}", e.getMessage(), e);
        }
        
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
        
        // Note: suiteResult was already added to executionResult earlier (line 175)
        // before prepareReportDirectory() was called, so we don't add it again here
        executionResult.markCompleted();
        
        // CRITICAL: Capture driver information NOW (before driver is closed!)
        // JUnit report generation needs browser/session/selenium info,
        // but driver gets closed during @AfterTestSuite (CSReport closes it)
        executionResult.captureDriverInformation();
        
        // CRITICAL: FLUSH execution0.log NOW before @AfterTestSuite!
        // Custom report listeners (CSReport, PdfGenerator) need to READ execution0.log,
        // but XmlKeywordLogger buffers all records in memory until generateReport().
        // We must write execution0.log NOW so listeners can parse it.
        if (generatedReportPath != null) {
            try {
                com.katalan.reporting.KatalonReportGenerator katalanReporter =
                        new com.katalan.reporting.KatalonReportGenerator(config.getProjectPath());
                katalanReporter.flushExecutionLog(generatedReportPath, executionResult);
                logger.info("Flushed execution0.log with {} log records before @AfterTestSuite", 
                    com.katalan.core.logging.XmlKeywordLogger.getInstance().getRecords().size());
            } catch (Exception e) {
                logger.warn("Failed to flush execution0.log: {}", e.getMessage());
            }
        }
        
        // Note: testCaseBinding was already generated at the beginning (in prepareReportDirectory)
        // so we don't need to update it here. It contains the list of test cases from the suite.
        
        // ----- Test Listener: @AfterTestSuite / @TearDown -----
        suiteCtx.setTestSuiteStatus(suiteResult.isSuccess() ? "PASSED" : "FAILED");
        
        // Re-inject into listener classloaders BEFORE invoking @AfterTestSuite
        // This ensures any code in listeners that accesses CSReport.reportFolder will work
        // (finalReportFolder already declared earlier at line 217)
        listenerRegistry.injectReportFolderIntoListeners(finalReportFolder);
        
        try {
            listenerRegistry.invokeAfterTestSuite(suiteCtx);
        } catch (Exception e) {
            logger.error("@AfterTestSuite listener error: {}", e.getMessage(), e);
        }
        
        // Log suite end
        java.util.Map<String, String> endSuiteProps = new java.util.LinkedHashMap<>();
        endSuiteProps.put("name", suite.getName());
        endSuiteProps.put("id", "Test Suites/" + suite.getId());
        kwLogger.endSuite(suite.getName(), "Test Suites/" + suite.getId(), endSuiteProps);
        
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
        
        TestCaseResult result = new TestCaseResult(testCase);  // Use TestCase constructor to preserve ID
        result.markStarted();
        testCase.markStarted();
        
        context.setCurrentTestCaseName(testCase.getName());
        context.setCurrentTestCasePath(testCase.getScriptPath());
        
        // Log test case start
        com.katalan.core.logging.XmlKeywordLogger kwLogger = com.katalan.core.logging.XmlKeywordLogger.getInstance();
        java.util.Map<String, String> testProps = new java.util.LinkedHashMap<>();
        testProps.put("name", testCase.getId()); // Use full ID with "Test Cases/" prefix
        testProps.put("description", testCase.getDescription() != null ? testCase.getDescription() : "");
        testProps.put("id", testCase.getId()); // Already includes "Test Cases/" prefix
        kwLogger.startTest(testCase.getName(), testCase.getId(), testProps); // getId() already has prefix
        
        // ----- Test Listener: @BeforeTestCase / @SetupTestCase -----
        com.kms.katalon.core.context.TestCaseContext tcCtx =
                new com.kms.katalon.core.context.TestCaseContext(testCase.getId()); // Already has prefix
        tcCtx.setTestCaseStatus("RUNNING");
        if (testCase.getVariables() != null) {
            java.util.Map<String, Object> resolvedVars = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Object> var : testCase.getVariables().entrySet()) {
                Object v = var.getValue();
                if (v instanceof TestSuiteParser.GlobalVariableReference) {
                    try { v = ((TestSuiteParser.GlobalVariableReference) v).resolve(); } catch (Exception ignored) {}
                }
                resolvedVars.put(var.getKey(), v);
            }
            tcCtx.setTestCaseVariables(resolvedVars);
        }
        try {
            listenerRegistry.invokeBeforeTestCase(tcCtx);
        } catch (Exception e) {
            logger.error("@BeforeTestCase listener error: {}", e.getMessage(), e);
        }
        
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
                
                // START capturing console output for this test case
                consoleCapturer.startCapture();
                
                // Parse source file BEFORE execution to populate keyword logs structure
                if (testCase.getScriptPath() != null && java.nio.file.Files.exists(testCase.getScriptPath())) {
                    try {
                        com.katalan.core.logging.GroovySourceParser sourceParser = 
                            new com.katalan.core.logging.GroovySourceParser();
                        sourceParser.parseAndLogTestCase(testCase.getScriptPath(), kwLogger);
                        logger.debug("Pre-parsed source file for keyword logging: {}", testCase.getScriptPath());
                    } catch (Exception parseEx) {
                        logger.warn("Could not pre-parse source for keyword logging: {}", parseEx.getMessage());
                    }
                }
                
                try {
                    // Execute the script
                    if (testCase.getScriptContent() != null) {
                        scriptExecutor.executeScript(testCase.getScriptContent(), testCase.getName());
                    } else if (testCase.getScriptPath() != null) {
                        scriptExecutor.executeScript(testCase.getScriptPath());
                    } else {
                        throw new IllegalStateException("No script content or path provided for test case: " + testCase.getName());
                    }
                } finally {
                    // STOP capturing and save the output
                    String capturedOutput = consoleCapturer.stopCapture();
                    result.setConsoleOutput(capturedOutput);
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
        
        // ----- Test Listener: @AfterTestCase / @TearDownTestCase -----
        tcCtx.setTestCaseStatus(String.valueOf(result.getStatus()));
        tcCtx.setMessage(result.getErrorMessage());
        try {
            listenerRegistry.invokeAfterTestCase(tcCtx);
        } catch (Exception e) {
            logger.error("@AfterTestCase listener error: {}", e.getMessage(), e);
        }
        
        // Log test end
        java.util.Map<String, String> endTestProps = new java.util.LinkedHashMap<>();
        endTestProps.put("name", testCase.getId()); // Use full ID with "Test Cases/" prefix
        endTestProps.put("id", testCase.getId()); // Already includes "Test Cases/" prefix
        kwLogger.endTest(testCase.getName(), testCase.getId(), endTestProps); // getId() already has prefix
        
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
     * Get the Test Listener registry (Katalon-compatible lifecycle hooks)
     */
    public TestListenerRegistry getListenerRegistry() {
        return listenerRegistry;
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

    // --------------------------------------------------------------------
    // Listener-library compatibility: pre-populate `reportFolder` static
    // fields on well-known third-party Katalon listener classes so scripts
    // that run inside fresh GroovyShell instances (via groovy.util.Eval.me)
    // still see the correct per-run report directory.
    // --------------------------------------------------------------------

    /** Known <className, fieldName> pairs that hold the current report folder. */
    private static final String[][] KNOWN_REPORT_FOLDER_FIELDS = new String[][] {
        {"denstoo.reporting.CSReport",              "reportFolder"},
        {"com.itextpdf.text.pdf.PdfGenerator",      "reportFolder"},
        {"denstoo.reporting.PdfGenerator",          "reportFolder"},
    };

    /**
     * Best-effort: set the static {@code reportFolder} field on any of the
     * well-known listener-library classes that happen to be on the classpath.
     * Missing classes / fields are silently ignored.
     *
     * <p><b>Why we iterate multiple classloaders</b>: each Test Listener is
     * compiled by its own {@link groovy.lang.GroovyClassLoader} (see
     * {@link TestListenerRegistry}).  When that listener calls into a library
     * JAR (e.g. {@code denstoo.reporting.CSReport}) the library class is
     * loaded by that same GroovyClassLoader, which holds its OWN static field
     * instance.  The thread-context classloader and the system classloader hold
     * a <em>different</em> copy of the class whose static field we would have
     * injected before — so the listener's copy stays {@code null}.</p>
     *
     * <p>By collecting classloaders from {@link TestListenerRegistry#getListenerClassLoaders()}
     * we inject into every class instance that will actually be used at
     * runtime.</p>
     */
    private void injectReportFolderIntoListenerLibs(String reportFolder) {
        if (reportFolder == null) return;

        // Collect every classloader we want to try, deduped.
        java.util.LinkedHashSet<ClassLoader> cls = new java.util.LinkedHashSet<>();
        // Listener-specific classloaders FIRST — these are the ones that actually
        // loaded the JAR libs (CSReport, PdfGenerator, …) used at runtime.
        cls.addAll(listenerRegistry.getListenerClassLoaders());
        // Fallbacks: context CL, engine CL, system CL.
        ClassLoader ctxCl = Thread.currentThread().getContextClassLoader();
        if (ctxCl != null) cls.add(ctxCl);
        cls.add(KatalanEngine.class.getClassLoader());
        ClassLoader sysCl = ClassLoader.getSystemClassLoader();
        if (sysCl != null) cls.add(sysCl);
        cls.remove(null);

        for (String[] pair : KNOWN_REPORT_FOLDER_FIELDS) {
            String className = pair[0];
            String fieldName = pair[1];
            for (ClassLoader cl : cls) {
                try {
                    Class<?> klass = Class.forName(className, true, cl);
                    java.lang.reflect.Field f = klass.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    f.set(null, reportFolder);
                    logger.info("Injected reportFolder into {}.{} (via {})",
                            className, fieldName, cl.getClass().getSimpleName());
                    // Also attempt to populate a minimal `report` list structure
                    // (List<Map>) that some listener scripts (inside their
                    // GroovyShell) expect to exist and contain reportFolder.
                    try {
                        java.lang.reflect.Field rf = klass.getDeclaredField("report");
                        rf.setAccessible(true);
                        java.util.List<java.util.Map<String,Object>> rlist = new java.util.ArrayList<>();
                        java.util.Map<String,Object> rmap = new java.util.HashMap<>();
                        rmap.put("reportFolder", reportFolder);
                        rlist.add(rmap);
                        rf.set(null, rlist);
                        logger.info("Injected minimal report List into {}.report (via {})",
                                className, cl.getClass().getSimpleName());
                    } catch (NoSuchFieldException nsf) {
                        // not all listener classes have a `report` field; ignore
                    } catch (Throwable t) {
                        logger.debug("Could not set {}.report via {}: {}",
                                className, cl.getClass().getSimpleName(), t.toString());
                    }
                } catch (ClassNotFoundException e) {
                    // library not present in this loader, skip
                } catch (NoSuchFieldException e) {
                    logger.debug("{} has no field {} in loader {} (skipped)",
                            className, fieldName, cl.getClass().getSimpleName());
                } catch (Throwable t) {
                    logger.debug("Could not set {}.{} via {}: {}",
                            className, fieldName, cl.getClass().getSimpleName(), t.toString());
                }
            }
        }
    }
    
    /**
     * Resolve user full name from environment variable or system properties
     */
    private String resolveUserFullName() {
        // First, try reading Katalon session.properties (if present)
        String userFullName = readKatalonSessionFullName();
        if (userFullName != null && !userFullName.isEmpty()) {
            return userFullName;
        }

        userFullName = System.getenv("KATALAN_USER_FULL_NAME");
        if (userFullName != null && !userFullName.isEmpty()) {
            return userFullName;
        }
        userFullName = System.getProperty("user.fullname");
        if (userFullName != null && !userFullName.isEmpty()) {
            return userFullName;
        }
        return System.getProperty("user.name", "katalan");
    }

    /**
     * Try to read Katalon session.properties to extract the logged-in user's fullName.
     * Supports macOS/Linux: ~/.katalon/session.properties
     * and Windows: C:\\Users\\<username>\\.katalon\\session.properties
     */
    private String readKatalonSessionFullName() {
        try {
            String userHome = System.getProperty("user.home");
            java.nio.file.Path[] candidates = new java.nio.file.Path[] {
                    java.nio.file.Paths.get(userHome, ".katalon", "session.properties"),
                    // Windows fallback using USERPROFILE env
                    (System.getenv("USERPROFILE") != null)
                            ? java.nio.file.Paths.get(System.getenv("USERPROFILE"), ".katalon", "session.properties")
                            : null
            };

            for (java.nio.file.Path p : candidates) {
                if (p == null) continue;
                if (java.nio.file.Files.exists(p)) {
                    String content = java.nio.file.Files.readString(p);
                    // Use regex to extract fullName from JSON-like property format
                    // Format: "fullName"\:"Muhamad Badru Salam"
                    // Pattern explained: \" = literal quote, \\\\ = one backslash, : = colon
                    java.util.regex.Pattern ptn = java.util.regex.Pattern.compile("\"fullName\"\\\\:\"([^\"]+)\"");
                    java.util.regex.Matcher m = ptn.matcher(content);
                    if (m.find()) {
                        String fullName = m.group(1);
                        if (fullName != null && !fullName.isEmpty()) {
                            logger.debug("Read fullName from {}: {}", p, fullName);
                            return fullName;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not read Katalon session.properties: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Resolve project name from .prj file
     */
    private String resolveProjectName() {
        if (config.getProjectPath() == null) {
            return "katalan";
        }
        try {
            java.nio.file.Path projectDir = config.getProjectPath();
            java.util.List<java.nio.file.Path> prjFiles = java.nio.file.Files.list(projectDir)
                    .filter(p -> p.toString().endsWith(".prj"))
                    .collect(java.util.stream.Collectors.toList());
            if (!prjFiles.isEmpty()) {
                String content = java.nio.file.Files.readString(prjFiles.get(0));
                int nameStart = content.indexOf("<name>");
                int nameEnd = content.indexOf("</name>");
                if (nameStart != -1 && nameEnd != -1 && nameEnd > nameStart) {
                    return content.substring(nameStart + 6, nameEnd);
                }
            }
        } catch (Exception e) {
            logger.debug("Could not resolve project name: {}", e.getMessage());
        }
        return "katalan";
    }
    
    /**
     * Resolve hostname with username prefix
     */
    private String resolveHostName() {
        try {
            String rawHost = java.net.InetAddress.getLoopbackAddress().getHostName();
            String userName = System.getProperty("user.name", "");
            return (userName == null || userName.isEmpty()) ? rawHost : userName + " - " + rawHost;
        } catch (Exception e) {
            return "localhost";
        }
    }
}

