package com.katalan.keywords;

import com.katalan.core.context.ExecutionContext;
import com.katalan.core.compat.GlobalVariable;
import com.katalan.core.compat.FailureHandling;
import com.katalan.core.compat.TestCase;
import com.katalan.core.model.TestCaseResult;
import com.katalan.core.logging.XmlKeywordLogger;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BDD Executor for Katalon-style Cucumber feature files
 * Parses Gherkin feature files and executes Groovy step definitions
 */
public class KatalanBDDExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(KatalanBDDExecutor.class);
    
    private final ExecutionContext context;
    private final Path projectPath;
    private final Path stepsPath;
    private final List<StepDefinition> stepDefinitions;
    private final GroovyShell groovyShell;
    private final Map<String, Object> stepInstances;
    
    // Hierarchical step tracking for reports (Katalon-style structure)
    private final List<Map<String, Object>> scenarioDataList = new ArrayList<>();
    
    // Tag filter for scenario filtering
    private String tagFilter;
    
    // Current feature name (for BDD logging)
    private String currentFeatureName;
    
    public KatalanBDDExecutor(ExecutionContext context, Path projectPath, Path stepsPath) {
        this.context = context;
        this.projectPath = projectPath;
        this.stepsPath = stepsPath;
        this.stepDefinitions = new ArrayList<>();
        this.stepInstances = new HashMap<>();
        this.groovyShell = createGroovyShell();
        this.tagFilter = null;
        
        // Store this executor in context so WebUI.callTestCase can use it
        context.setProperty("executor", this);
        
        // Load step definitions
        loadStepDefinitions();
    }
    
    /**
     * Set tag filter for scenario filtering
     * @param tags Cucumber tag expression (e.g., "@smoke", "@regression and not @slow")
     */
    public void setTagFilter(String tags) {
        this.tagFilter = tags;
        logger.info("Tag filter set to: {}", tags);
    }
    
    /**
     * Get the current tag filter
     */
    public String getTagFilter() {
        return tagFilter;
    }
    
    /**
     * Evaluate whether a scenario matches the tag filter expression.
     * Supports: simple "@tag", "@a and @b", "@a or @b", "not @a", and parentheses removed.
     * Falls back to containment check if expression parsing is too complex.
     */
    private boolean scenarioMatchesTagFilter(Scenario scenario, String filter) {
        String expr = filter.trim();
        if (expr.isEmpty()) return true;
        List<String> tags = scenario.tags;
        
        // Simple fast path: single tag (most common case, e.g. "@TC01")
        if (expr.matches("@[A-Za-z0-9_\\-]+")) {
            return tags.contains(expr);
        }
        
        // Very small boolean-expression evaluator: tokenize, then evaluate
        try {
            // Normalize parentheses by tokenizing
            String normalized = expr.replace("(", " ( ").replace(")", " ) ");
            String[] toks = normalized.trim().split("\\s+");
            // Build postfix using shunting-yard for 'and'/'or'/'not'
            List<String> output = new ArrayList<>();
            Deque<String> ops = new ArrayDeque<>();
            java.util.Map<String, Integer> prec = new HashMap<>();
            prec.put("not", 3); prec.put("and", 2); prec.put("or", 1);
            for (String t : toks) {
                String lt = t.toLowerCase();
                if (t.startsWith("@")) {
                    output.add(t);
                } else if (lt.equals("not") || lt.equals("and") || lt.equals("or")) {
                    while (!ops.isEmpty() && !ops.peek().equals("(")
                            && prec.getOrDefault(ops.peek(), 0) >= prec.get(lt)) {
                        output.add(ops.pop());
                    }
                    ops.push(lt);
                } else if (t.equals("(")) {
                    ops.push(t);
                } else if (t.equals(")")) {
                    while (!ops.isEmpty() && !ops.peek().equals("(")) output.add(ops.pop());
                    if (!ops.isEmpty()) ops.pop();
                }
            }
            while (!ops.isEmpty()) output.add(ops.pop());
            // Evaluate postfix
            Deque<Boolean> stack = new ArrayDeque<>();
            for (String t : output) {
                if (t.startsWith("@")) {
                    stack.push(tags.contains(t));
                } else if (t.equals("not")) {
                    stack.push(!stack.pop());
                } else if (t.equals("and")) {
                    boolean b = stack.pop(), a = stack.pop(); stack.push(a && b);
                } else if (t.equals("or")) {
                    boolean b = stack.pop(), a = stack.pop(); stack.push(a || b);
                }
            }
            return stack.isEmpty() ? true : stack.peek();
        } catch (Exception e) {
            logger.warn("Failed to evaluate tag filter '{}': {} - defaulting to include", filter, e.getMessage());
            return true;
        }
    }
    
    /**
     * Execute a test case script (called from WebUI.callTestCase)
     */
    public void executeTestCase(TestCase testCase, Map<String, Object> variables) throws Exception {
        logger.info("BDD Executor: executing test case: {}", testCase.getTestCaseName());
        
        Path scriptPath = testCase.getScriptPath();
        if (scriptPath == null || !Files.exists(scriptPath)) {
            throw new RuntimeException("Script file not found for test case: " + testCase.getTestCaseName());
        }
        
        // Read and preprocess the script
        String scriptContent = Files.readString(scriptPath);
        String processedScript = preprocessTestCaseScript(scriptContent);
        
        // Set up binding with variables
        Binding binding = new Binding();
        binding.setVariable("WebUI", WebUI.class);
        binding.setVariable("GlobalVariable", GlobalVariable.class);
        binding.setVariable("FailureHandling", FailureHandling.class);
        binding.setVariable("findTestObject", new FindTestObjectBinding(context));
        binding.setVariable("findTestCase", new FindTestCaseBinding(context));
        binding.setVariable("CustomKeywords", new CustomKeywordsClosure(context, projectPath));
        
        // Add test case variables
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                binding.setVariable(entry.getKey(), entry.getValue());
            }
        }
        
        // Add global variables
        Map<String, Object> globalVars = GlobalVariable.getAllVariables();
        if (globalVars != null) {
            for (Map.Entry<String, Object> entry : globalVars.entrySet()) {
                binding.setVariable(entry.getKey(), entry.getValue());
            }
        }
        
        // Create a shell for this execution
        CompilerConfiguration config = new CompilerConfiguration();
        ImportCustomizer imports = new ImportCustomizer();
        imports.addStarImports("com.katalan.keywords", "com.katalan.core.model", "com.katalan.core.compat");
        imports.addStaticStars("com.katalan.core.compat.ObjectRepository", "com.katalan.core.compat.TestCaseFinder");
        config.addCompilationCustomizers(imports);
        
        GroovyClassLoader classLoader = new GroovyClassLoader(getClass().getClassLoader(), config);
        
        // Add JAR files from Drivers folder (custom libraries)
        addProjectLibrariesToClassLoader(classLoader);
        
        GroovyShell testShell = new GroovyShell(classLoader, binding, config);
        
        try {
            Script script = testShell.parse(processedScript);
            script.run();
            logger.info("Test case completed: {}", testCase.getTestCaseName());
        } catch (Exception e) {
            logger.error("Test case failed: {} - {}", testCase.getTestCaseName(), e.getMessage());
            throw e;
        }
    }
    
    /**
     * Add project libraries (from Drivers, Libs folders) to classloader
     */
    private void addProjectLibrariesToClassLoader(GroovyClassLoader classLoader) {
        if (projectPath == null) return;
        
        // Add Keywords folder so that imports like `import website.CSWeb`
        // or `import support.ExcelLocator` can be resolved from Keywords/website/CSWeb.groovy etc.
        // Preprocess source files to fix Groovy 4 parser incompatibilities (e.g. `((x) as T)`).
        Path keywordsPath = projectPath.resolve("Keywords");
        if (Files.exists(keywordsPath)) {
            try {
                Path processed = com.katalan.core.compat.GroovySourcePreprocessor
                        .createPreprocessedCopy(keywordsPath, "keywords");
                classLoader.addClasspath(processed.toString());
                logger.info("Added Keywords path to classpath (preprocessed): {}", processed);
            } catch (Exception e) {
                logger.warn("Could not add Keywords path: {}", e.getMessage());
            }
        }
        
        // Add Include/scripts/groovy folder (step definitions and shared groovy classes)
        Path includeGroovyPath = projectPath.resolve("Include").resolve("scripts").resolve("groovy");
        if (Files.exists(includeGroovyPath)) {
            try {
                Path processed = com.katalan.core.compat.GroovySourcePreprocessor
                        .createPreprocessedCopy(includeGroovyPath, "include");
                classLoader.addClasspath(processed.toString());
                logger.info("Added Include/scripts/groovy path to classpath (preprocessed): {}", processed);
            } catch (Exception e) {
                logger.warn("Could not add Include/scripts/groovy path: {}", e.getMessage());
            }
        }
        
        // Load from Drivers folder
        Path driversPath = projectPath.resolve("Drivers");
        if (Files.exists(driversPath)) {
            loadJarsFromDirectory(classLoader, driversPath);
        }
        
        // Load from Libs folder
        Path libsPath = projectPath.resolve("Libs");
        if (Files.exists(libsPath)) {
            loadJarsFromDirectory(classLoader, libsPath);
        }
        
        // Load from bin/lib folder
        Path binLibPath = projectPath.resolve("bin").resolve("lib");
        if (Files.exists(binLibPath)) {
            loadJarsFromDirectory(classLoader, binLibPath);
        }
    }
    
    /**
     * Load all JAR files from a directory into the classloader
     */
    private void loadJarsFromDirectory(GroovyClassLoader classLoader, Path directory) {
        try {
            Files.walk(directory, 1)
                .filter(p -> p.toString().toLowerCase().endsWith(".jar"))
                .forEach(jarPath -> {
                    try {
                        classLoader.addURL(jarPath.toUri().toURL());
                        logger.debug("Added JAR to classpath: {}", jarPath.getFileName());
                    } catch (Exception e) {
                        logger.warn("Could not add JAR to classpath: {} - {}", jarPath, e.getMessage());
                    }
                });
        } catch (IOException e) {
            logger.warn("Could not scan directory for JARs: {} - {}", directory, e.getMessage());
        }
    }
    
    /**
     * Preprocess test case script (same as step definition preprocessing)
     */
    private String preprocessTestCaseScript(String script) {
        return preprocessStepDefinitionScript(script);
    }

    /**
     * Result of executing a scenario - contains steps and optional exception
     */
    private static class ScenarioExecutionResult {
        final List<Map<String, Object>> steps;
        final Exception exception;
        
        ScenarioExecutionResult(List<Map<String, Object>> steps, Exception exception) {
            this.steps = steps;
            this.exception = exception;
        }
    }
    
    /**
     * Execute a feature file
     */
    public int executeFeature(Path featurePath) throws IOException {
        logger.info("Executing feature: {}", featurePath);
        
        // Reset hierarchical tracking
        scenarioDataList.clear();
        
        String featureContent = Files.readString(featurePath);
        
        // Extract feature name from content
        this.currentFeatureName = extractFeatureName(featureContent);
        
        List<Scenario> scenarios = parseFeature(featureContent);
        
        int failedCount = 0;
        int scenarioIndex = 0;
        for (Scenario scenario : scenarios) {
            // Apply tag filter - skip scenarios that don't match
            if (tagFilter != null && !tagFilter.isEmpty() && !scenarioMatchesTagFilter(scenario, tagFilter)) {
                logger.debug("Skipping scenario (tag filter '{}' no match): {}", tagFilter, scenario.name);
                continue;
            }
            logger.info("Executing scenario: {}", scenario.name);
            
            Map<String, Object> scenarioData = new LinkedHashMap<>();
            Instant scenarioStart = Instant.now();
            
            // Execute scenario and get result with steps
            ScenarioExecutionResult result = executeScenario(scenario);
            List<Map<String, Object>> stepDataList = result.steps;
            
            if (result.exception == null) {
                // Scenario passed - build success scenario data
                scenarioData.put("entityId", "");
                scenarioData.put("dataIterationName", "");
                Map<String, Object> stats = new LinkedHashMap<>();
                stats.put("total", stepDataList.size());
                stats.put("passed", stepDataList.size());
                stats.put("failed", 0);
                stats.put("errored", 0);
                stats.put("warned", 0);
                stats.put("skipped", 0);
                stats.put("notRun", 0);
                stats.put("incomplete", 0);
                scenarioData.put("statistics", stats);
                scenarioData.put("type", "TEST_CASE");
                scenarioData.put("name", "Start Test Case : SCENARIO " + scenario.name);
                scenarioData.put("description", "");
                scenarioData.put("retryCount", 0);
                scenarioData.put("status", "COMPLETED");
                scenarioData.put("result", "PASSED");
                scenarioData.put("startTime", formatInstant(scenarioStart));
                scenarioData.put("endTime", formatInstant(Instant.now()));
                scenarioData.put("children", stepDataList);
                scenarioData.put("index", scenarioIndex++);
                scenarioData.put("startIndex", 0);
                scenarioData.put("logs", Collections.emptyList());
                
                logger.info("Scenario PASSED: {}", scenario.name);
            } else {
                // Scenario failed - build failure scenario data with steps that were executed
                scenarioData.put("entityId", "");
                scenarioData.put("dataIterationName", "");
                Map<String, Object> stats = new LinkedHashMap<>();
                stats.put("total", stepDataList.size());
                stats.put("passed", (int) stepDataList.stream().filter(s -> "PASSED".equals(s.get("result"))).count());
                stats.put("failed", (int) stepDataList.stream().filter(s -> "FAILED".equals(s.get("result"))).count());
                stats.put("errored", 0);
                stats.put("warned", 0);
                stats.put("skipped", 0);
                stats.put("notRun", 0);
                stats.put("incomplete", 0);
                scenarioData.put("statistics", stats);
                scenarioData.put("type", "TEST_CASE");
                scenarioData.put("name", "Start Test Case : SCENARIO " + scenario.name);
                scenarioData.put("description", "");
                scenarioData.put("retryCount", 0);
                scenarioData.put("status", "COMPLETED");
                scenarioData.put("result", "FAILED");
                scenarioData.put("startTime", formatInstant(scenarioStart));
                scenarioData.put("endTime", formatInstant(Instant.now()));
                scenarioData.put("children", stepDataList); // Include all executed steps!
                scenarioData.put("index", scenarioIndex++);
                scenarioData.put("startIndex", 0);
                
                List<Map<String, Object>> errorLogs = new ArrayList<>();
                Map<String, Object> errorLog = new LinkedHashMap<>();
                errorLog.put("time", formatInstant(Instant.now()));
                errorLog.put("level", "FAILED");
                errorLog.put("message", result.exception.getMessage());
                errorLogs.add(errorLog);
                scenarioData.put("logs", errorLogs);
                
                logger.error("Scenario FAILED: {} - {}", scenario.name, result.exception.getMessage());
                failedCount++;
            }
            
            scenarioDataList.add(scenarioData);
        }
        
        // Store hierarchical data in context for the report generator
        context.setProperty("bddScenarioData", new ArrayList<>(scenarioDataList));
        
        return failedCount;
    }
    
    /**
     * Execute a single scenario and return execution result with steps and exception (if any)
     */
    private ScenarioExecutionResult executeScenario(Scenario scenario) {
        List<Map<String, Object>> stepDataList = new ArrayList<>();
        
        // Log BDD scenario start (Katalon-style)
        XmlKeywordLogger kwLogger = XmlKeywordLogger.getInstance();
        String scenarioUuid = UUID.randomUUID().toString();
        kwLogger.startBddScenario(scenario.name, currentFeatureName != null ? currentFeatureName : "Unknown Feature", scenario.lineNumber, scenarioUuid);
        
        int stepIndex = 0;
        Exception failedException = null;
        boolean scenarioFailed = false;
        
        for (Step step : scenario.steps) {
            Map<String, Object> stepData;
            
            if (scenarioFailed) {
                // Previous step failed - mark remaining steps as SKIPPED
                stepData = createSkippedStepData(step, stepIndex++);
            } else {
                // executeStep now always returns step data (never throws)
                stepData = executeStep(step, stepIndex++);
                
                // Check if step failed
                String result = (String) stepData.get("result");
                if ("FAILED".equals(result)) {
                    // Step failed - mark scenario as failed and skip remaining steps
                    String stepName = step.keyword + " " + step.text;
                    String errorMsg = "Step failed: " + stepName;
                    
                    // Extract error message from logs if available
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> logs = (List<Map<String, Object>>) stepData.get("logs");
                    if (logs != null && !logs.isEmpty()) {
                        Object msg = logs.get(0).get("message");
                        if (msg != null) {
                            errorMsg = msg.toString();
                        }
                    }
                    
                    failedException = new RuntimeException(errorMsg);
                    scenarioFailed = true; // Mark to skip remaining steps
                }
            }
            
            stepDataList.add(stepData);
        }
        
        // Log BDD scenario end
        kwLogger.endBddScenario(scenario.name);
        
        return new ScenarioExecutionResult(stepDataList, failedException);
    }
    
    /**
     * Create step data for skipped step
     */
    private Map<String, Object> createSkippedStepData(Step step, int stepIndex) {
        // Still emit BDD start/end records so execution0.log shows every
        // step (even the ones that never ran because a previous step failed).
        // Katalon Studio does the same.
        XmlKeywordLogger kwLogger = XmlKeywordLogger.getInstance();
        String stepUuid = UUID.randomUUID().toString();
        kwLogger.startBddStep(step.keyword, step.text, step.lineNumber, stepUuid);
        kwLogger.endBddStep(step.keyword, step.text);
        
        Map<String, Object> stepData = new LinkedHashMap<>();
        stepData.put("name", step.keyword + " " + step.text);
        stepData.put("result", "SKIPPED");
        stepData.put("startTime", Instant.now().toString());
        stepData.put("endTime", Instant.now().toString());
        stepData.put("logs", new ArrayList<>());
        return stepData;
    }
    
    /**
     * Execute a single step and return step data
     */
    private Map<String, Object> executeStep(Step step, int index) {
        String stepName = step.text;
        logger.info("  {} {}", step.keyword, stepName);
        
        // Log BDD step start (Katalon-style)
        XmlKeywordLogger kwLogger = XmlKeywordLogger.getInstance();
        String stepUuid = UUID.randomUUID().toString();
        kwLogger.startBddStep(step.keyword, stepName, step.lineNumber, stepUuid);
        
        Map<String, Object> stepData = new LinkedHashMap<>();
        Instant stepStart = Instant.now();
        
        // Find matching step definition
        StepMatch match = findStepDefinition(step.text);
        if (match == null) {
            kwLogger.endBddStep(step.keyword, stepName);
            
            stepData.put("type", "TEST_STEP");
            stepData.put("name", stepName);
            stepData.put("description", "");
            stepData.put("retryCount", 0);
            stepData.put("status", "COMPLETED");
            stepData.put("result", "FAILED");
            stepData.put("startTime", formatInstant(stepStart));
            stepData.put("endTime", formatInstant(Instant.now()));
            stepData.put("children", Collections.emptyList());
            stepData.put("index", index);
            stepData.put("startIndex", 0);
            
            List<Map<String, Object>> logs = new ArrayList<>();
            Map<String, Object> log = new LinkedHashMap<>();
            log.put("time", formatInstant(Instant.now()));
            log.put("level", "FAILED");
            log.put("message", "No step definition found for: " + step.keyword + " " + stepName);
            logs.add(log);
            stepData.put("logs", logs);
            
            // Return step data instead of throwing - let caller handle the failure
            return stepData;
        }
        
        try {
            // Invoke the step definition method
            match.invoke();
            
            kwLogger.endBddStep(step.keyword, stepName);
            
            stepData.put("type", "TEST_STEP");
            stepData.put("name", stepName);
            stepData.put("description", "");
            stepData.put("retryCount", 0);
            stepData.put("status", "COMPLETED");
            stepData.put("result", "PASSED");
            stepData.put("startTime", formatInstant(stepStart));
            stepData.put("endTime", formatInstant(Instant.now()));
            stepData.put("children", Collections.emptyList());
            stepData.put("index", index);
            stepData.put("startIndex", 0);
            
            // Store step match information for report generation
            Map<String, Object> matchInfo = new LinkedHashMap<>();
            matchInfo.put("method", match.definition.method);
            matchInfo.put("pattern", match.definition.pattern.pattern());
            matchInfo.put("parameters", match.parameters);
            stepData.put("matchInfo", matchInfo);
            
            List<Map<String, Object>> logs = new ArrayList<>();
            Map<String, Object> log = new LinkedHashMap<>();
            log.put("time", formatInstant(Instant.now()));
            log.put("level", "PASSED");
            log.put("message", stepName);
            logs.add(log);
            stepData.put("logs", logs);
            
            logger.debug("    Step passed");
            return stepData;
        } catch (Throwable e) {
            // Unwrap InvocationTargetException and similar wrappers to get the real cause
            Throwable root = e;
            while (root != null && (root instanceof java.lang.reflect.InvocationTargetException
                    || (root.getMessage() == null && root.getCause() != null && root.getCause() != root))) {
                if (root.getCause() == null || root.getCause() == root) break;
                root = root.getCause();
            }
            String rootMsg = root != null ? root.getMessage() : null;
            if (rootMsg == null && root != null) rootMsg = root.toString();
            if (rootMsg == null) rootMsg = "Step failed (no message)";
            String exType = root != null ? root.getClass().getName() : e.getClass().getName();

            stepData.put("type", "TEST_STEP");
            stepData.put("name", stepName);
            stepData.put("description", "");
            stepData.put("retryCount", 0);
            stepData.put("status", "COMPLETED");
            stepData.put("result", "FAILED");
            stepData.put("startTime", formatInstant(stepStart));
            stepData.put("endTime", formatInstant(Instant.now()));
            stepData.put("children", Collections.emptyList());
            stepData.put("index", index);
            stepData.put("startIndex", 0);
            
            List<Map<String, Object>> logs = new ArrayList<>();
            Map<String, Object> log = new LinkedHashMap<>();
            log.put("time", formatInstant(Instant.now()));
            log.put("level", "FAILED");
            log.put("message", exType + ": " + rootMsg);
            logs.add(log);
            stepData.put("logs", logs);
            
            logger.error("    Step failed: [{}] {}", exType, rootMsg);
            if (root != null) {
                // Print first few stack frames to help debugging
                StackTraceElement[] trace = root.getStackTrace();
                int limit = Math.min(trace.length, 8);
                for (int i = 0; i < limit; i++) {
                    logger.error("      at {}", trace[i]);
                }
            }
            
            // Return step data instead of throwing - let caller handle the failure
            return stepData;
        }
    }
    
    /**
     * Extract feature name from feature file content
     */
    private String extractFeatureName(String featureContent) {
        if (featureContent == null) {
            return "Unknown Feature";
        }
        
        for (String line : featureContent.split("\n")) {
            line = line.trim();
            if (line.startsWith("Feature:")) {
                String name = line.substring(8).trim();
                // Remove any trailing comments
                int commentIdx = name.indexOf('#');
                if (commentIdx > 0) {
                    name = name.substring(0, commentIdx).trim();
                }
                return name;
            }
        }
        return "Unknown Feature";
    }
    
    /**
     * Format Instant to ISO timestamp string
     */
    private String formatInstant(Instant instant) {
        return java.time.ZonedDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
    }
    
    /**
     * Find a matching step definition for the given step text
     */
    private StepMatch findStepDefinition(String stepText) {
        for (StepDefinition def : stepDefinitions) {
            Matcher matcher = def.pattern.matcher(stepText);
            if (matcher.matches()) {
                // Extract groups as parameters
                List<Object> params = new ArrayList<>();
                for (int i = 1; i <= matcher.groupCount(); i++) {
                    String group = matcher.group(i);
                    // Try to convert to appropriate type
                    params.add(parseParameter(group, def.method.getParameterTypes()[i - 1]));
                }
                return new StepMatch(def, params);
            }
        }
        return null;
    }
    
    /**
     * Parse a parameter string to the expected type
     */
    private Object parseParameter(String value, Class<?> type) {
        if (type == int.class || type == Integer.class) {
            return Integer.parseInt(value);
        } else if (type == long.class || type == Long.class) {
            return Long.parseLong(value);
        } else if (type == double.class || type == Double.class) {
            return Double.parseDouble(value);
        } else if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(value);
        }
        return value;
    }
    
    /**
     * Load all step definitions from the steps path
     */
    private void loadStepDefinitions() {
        if (!Files.exists(stepsPath)) {
            logger.warn("Steps path does not exist: {}", stepsPath);
            return;
        }
        
        try {
            Files.walk(stepsPath)
                .filter(p -> p.toString().endsWith(".groovy"))
                .forEach(this::loadStepDefinitionFile);
            
            logger.info("Loaded {} step definitions", stepDefinitions.size());
        } catch (IOException e) {
            logger.error("Failed to load step definitions", e);
        }
    }
    
    /**
     * Load step definitions from a single Groovy file
     */
    private void loadStepDefinitionFile(Path groovyFile) {
        Class<?> clazz = null;
        try {
            String content = Files.readString(groovyFile);
            content = preprocessStepDefinitionScript(content);
            clazz = groovyShell.getClassLoader().parseClass(content, groovyFile.getFileName().toString());
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null && e.getCause() != null) msg = e.getCause().toString();
            if (msg == null) msg = e.toString();
            logger.warn("Failed to parse step definitions from: {} - {}", groovyFile, msg);
            return;
        }

        // Scan annotations on Class (no instantiation needed). Instance is created lazily
        // at step invocation time to avoid field-initializer errors (e.g. GlobalVariable not set yet).
        int count = 0;
        try {
            for (Method method : clazz.getDeclaredMethods()) {
                for (Annotation annotation : method.getAnnotations()) {
                    String annotationType = annotation.annotationType().getSimpleName();
                    if (isStepAnnotation(annotationType)) {
                        String patternStr = getAnnotationValue(annotation);
                        if (patternStr != null) {
                            Pattern pattern = convertToRegex(patternStr);
                            StepDefinition sd = new StepDefinition(pattern, method, null, annotationType);
                            sd.ownerClass = clazz;
                            stepDefinitions.add(sd);
                            count++;
                            logger.debug("Loaded step: {} {} -> {}.{}", annotationType, patternStr,
                                clazz.getSimpleName(), method.getName());
                        }
                    }
                }
            }
            logger.debug("Registered {} step(s) from {}", count, groovyFile.getFileName());
        } catch (Throwable t) {
            logger.warn("Failed to scan step annotations from: {} - {}", groovyFile, t.toString());
        }
    }
    
    private boolean isStepAnnotation(String name) {
        return name.equals("Given") || name.equals("When") || name.equals("Then") || name.equals("And") || name.equals("But");
    }
    
    private String getAnnotationValue(Annotation annotation) {
        try {
            Method valueMethod = annotation.annotationType().getMethod("value");
            return (String) valueMethod.invoke(annotation);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Convert Cucumber expression to regex pattern
     * Handles {int}, {string}, {word}, (.*) etc.
     */
    private Pattern convertToRegex(String expression) {
        String regex = expression;
        
        // Replace Cucumber expressions with regex
        regex = regex.replace("{int}", "(\\d+)");
        regex = regex.replace("{long}", "(\\d+)");
        regex = regex.replace("{float}", "([\\d.]+)");
        regex = regex.replace("{double}", "([\\d.]+)");
        regex = regex.replace("{string}", "\"([^\"]*)\"");
        regex = regex.replace("{word}", "(\\w+)");
        
        // Handle generic {xxx} patterns
        regex = regex.replaceAll("\\{[^}]+\\}", "(.+?)");
        
        // Escape special regex characters (except what we already replaced)
        // But be careful not to escape our capture groups
        
        return Pattern.compile("^" + regex + "$");
    }
    
    /**
     * Parse a Gherkin feature file into scenarios
     */
    private List<Scenario> parseFeature(String content) {
        List<Scenario> scenarios = new ArrayList<>();
        
        String[] lines = content.split("\n");
        Scenario currentScenario = null;
        List<Map<String, String>> examplesData = null;
        List<String> examplesHeaders = null;
        boolean inExamples = false;
        List<String> pendingTags = new ArrayList<>();
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            if (line.startsWith("Feature:")) {
                // Feature declaration, skip
                continue;
            }
            
            // Tag line - collect tags for next scenario
            if (line.startsWith("@")) {
                for (String tok : line.split("\\s+")) {
                    if (tok.startsWith("@")) {
                        pendingTags.add(tok);
                    }
                }
                continue;
            }
            
            if (line.startsWith("Scenario Outline:") || line.startsWith("Scenario:")) {
                // Save previous scenario if exists
                if (currentScenario != null) {
                    if (examplesData != null && !examplesData.isEmpty()) {
                        // Expand scenario outline
                        scenarios.addAll(expandScenarioOutline(currentScenario, examplesData));
                    } else {
                        scenarios.add(currentScenario);
                    }
                }
                
                String name = line.contains(":") ? line.substring(line.indexOf(":") + 1).trim() : line;
                currentScenario = new Scenario(name, i + 1); // i+1 for 1-based line numbers
                currentScenario.tags.addAll(pendingTags);
                pendingTags.clear();
                examplesData = null;
                examplesHeaders = null;
                inExamples = false;
                continue;
            }
            
            if (line.startsWith("Examples:")) {
                inExamples = true;
                examplesData = new ArrayList<>();
                continue;
            }
            
            if (inExamples && line.startsWith("|")) {
                List<String> cells = parseTableRow(line);
                if (examplesHeaders == null) {
                    examplesHeaders = cells;
                } else {
                    Map<String, String> row = new HashMap<>();
                    for (int j = 0; j < Math.min(examplesHeaders.size(), cells.size()); j++) {
                        row.put(examplesHeaders.get(j), cells.get(j));
                    }
                    examplesData.add(row);
                }
                continue;
            }
            
            // Step line
            if (currentScenario != null) {
                String keyword = "";
                String text = line;
                
                for (String kw : new String[]{"Given", "When", "Then", "And", "But"}) {
                    if (line.startsWith(kw + " ")) {
                        keyword = kw;
                        text = line.substring(kw.length() + 1);
                        break;
                    }
                }
                
                if (!keyword.isEmpty()) {
                    currentScenario.steps.add(new Step(keyword, text, i + 1)); // i+1 for 1-based line numbers
                }
            }
        }
        
        // Add last scenario
        if (currentScenario != null) {
            if (examplesData != null && !examplesData.isEmpty()) {
                scenarios.addAll(expandScenarioOutline(currentScenario, examplesData));
            } else {
                scenarios.add(currentScenario);
            }
        }
        
        return scenarios;
    }
    
    private List<String> parseTableRow(String line) {
        List<String> cells = new ArrayList<>();
        String[] parts = line.split("\\|");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                cells.add(trimmed);
            }
        }
        return cells;
    }
    
    private List<Scenario> expandScenarioOutline(Scenario outline, List<Map<String, String>> examplesData) {
        List<Scenario> expanded = new ArrayList<>();
        
        for (Map<String, String> example : examplesData) {
            Scenario scenario = new Scenario(outline.name, outline.lineNumber);
            scenario.tags.addAll(outline.tags);
            for (Step step : outline.steps) {
                String expandedText = step.text;
                for (Map.Entry<String, String> entry : example.entrySet()) {
                    expandedText = expandedText.replace("<" + entry.getKey() + ">", entry.getValue());
                }
                scenario.steps.add(new Step(step.keyword, expandedText, step.lineNumber));
            }
            expanded.add(scenario);
        }
        
        return expanded;
    }
    
    private String preprocessStepDefinitionScript(String script) {
        // All transformations are shared with classpath preprocessing.
        // We rely on the kms.* compatibility stubs bundled in katalan-runner
        // to delegate to com.katalan.keywords.WebUI transparently, so we
        // must NOT rewrite kms.* imports here - that would break class
        // inheritance (e.g. CSWeb extends WebUI loses disableSmartWait
        // and openBrowser(url, FailureHandling) overloads).
        return com.katalan.core.compat.GroovySourcePreprocessor.preprocess(script);
    }
    
    private GroovyShell createGroovyShell() {
        CompilerConfiguration config = new CompilerConfiguration();
        ImportCustomizer importCustomizer = new ImportCustomizer();
        
        // Add necessary imports
        importCustomizer.addStarImports(
            "com.katalan.keywords",
            "com.katalan.core.model",
            "com.katalan.core.compat"
        );
        
        // Add static imports for findTestObject and findTestCase
        importCustomizer.addStaticStars(
            "com.katalan.core.compat.ObjectRepository",
            "com.katalan.core.compat.TestCaseFinder"
        );
        
        // Only add io.cucumber imports (Cucumber 7.x)
        importCustomizer.addImports(
            "io.cucumber.java.en.Given",
            "io.cucumber.java.en.When",
            "io.cucumber.java.en.Then",
            "io.cucumber.java.en.And",
            "io.cucumber.java.en.But"
        );
        
        config.addCompilationCustomizers(importCustomizer);
        
        Binding binding = new Binding();
        binding.setVariable("WebUI", WebUI.class);
        binding.setVariable("GlobalVariable", GlobalVariable.class);
        binding.setVariable("FailureHandling", FailureHandling.class);
        binding.setVariable("findTestObject", new FindTestObjectBinding(context));
        binding.setVariable("findTestCase", new FindTestCaseBinding(context));
        
        GroovyClassLoader classLoader = new GroovyClassLoader(getClass().getClassLoader(), config);
        
        // Add project-specific paths and libraries so that imports like
        // `import website.CSWeb` or `import support.ExcelLocator` can be resolved
        // from the project's Keywords/, Include/scripts/groovy/, Drivers/, Libs/, bin/lib folders.
        addProjectLibrariesToClassLoader(classLoader);
        
        return new GroovyShell(classLoader, binding, config);
    }
    
    // Inner classes
    
    private static class Scenario {
        String name;
        int lineNumber;
        List<Step> steps = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        
        Scenario(String name, int lineNumber) {
            this.name = name;
            this.lineNumber = lineNumber;
        }
    }
    
    private static class Step {
        String keyword;
        String text;
        int lineNumber;
        
        Step(String keyword, String text, int lineNumber) {
            this.keyword = keyword;
            this.text = text;
            this.lineNumber = lineNumber;
        }
    }
    
    private static class StepDefinition {
        Pattern pattern;
        Method method;
        Object instance;
        String keyword;
        Class<?> ownerClass;
        
        StepDefinition(Pattern pattern, Method method, Object instance, String keyword) {
            this.pattern = pattern;
            this.method = method;
            this.instance = instance;
            this.keyword = keyword;
        }
    }
    
    private static class StepMatch {
        StepDefinition definition;
        List<Object> parameters;
        
        StepMatch(StepDefinition definition, List<Object> parameters) {
            this.definition = definition;
            this.parameters = parameters;
        }
        
        void invoke() throws Exception {
            Object target = definition.instance;
            if (target == null && definition.ownerClass != null) {
                // Lazy instantiation: only create when the step is actually executed,
                // so that GlobalVariables/profiles are already initialized.
                try {
                    target = definition.ownerClass.getDeclaredConstructor().newInstance();
                    definition.instance = target; // cache for next invocations
                } catch (Throwable t) {
                    Throwable root = t;
                    while (root.getCause() != null) root = root.getCause();
                    throw new RuntimeException("Failed to instantiate step definition class '"
                            + definition.ownerClass.getName() + "': " + root, t);
                }
            }
            definition.method.invoke(target, parameters.toArray());
        }
    }
    
    /**
     * Binding closure for findTestObject
     */
    public static class FindTestObjectBinding extends groovy.lang.Closure<com.katalan.core.model.TestObject> {
        private final ExecutionContext context;
        
        public FindTestObjectBinding(ExecutionContext context) {
            super(null);
            this.context = context;
        }
        
        public com.katalan.core.model.TestObject call(String path) {
            return context.findTestObject(path);
        }
    }
    
    /**
     * Binding closure for findTestCase
     */
    public static class FindTestCaseBinding extends groovy.lang.Closure<com.katalan.core.compat.TestCase> {
        private static final Logger logger = LoggerFactory.getLogger(FindTestCaseBinding.class);
        private final ExecutionContext context;
        
        public FindTestCaseBinding(ExecutionContext context) {
            super(null);
            this.context = context;
        }
        
        public com.katalan.core.compat.TestCase call(String path) {
            // Return a test case that can be used with WebUI.callTestCase
            Path projectPath = context.getProjectPath();
            if (projectPath == null) {
                logger.warn("Project path not set for findTestCase: {}", path);
                return null;
            }
            
            // Strip "Test Cases/" prefix if present (Katalon uses this convention)
            String normalizedPath = path;
            if (normalizedPath.startsWith("Test Cases/")) {
                normalizedPath = normalizedPath.substring("Test Cases/".length());
            }
            
            Path scriptsDir = projectPath.resolve("Scripts").resolve(normalizedPath);
            logger.debug("Looking for test case at: {}", scriptsDir);
            
            if (!Files.exists(scriptsDir) || !Files.isDirectory(scriptsDir)) {
                logger.warn("Test case directory not found: {}", scriptsDir);
                return null;
            }
            
            try {
                Path scriptFile = Files.list(scriptsDir)
                    .filter(p -> p.toString().endsWith(".groovy"))
                    .findFirst()
                    .orElse(null);
                
                if (scriptFile != null) {
                    String testCaseName = Path.of(normalizedPath).getFileName().toString();
                    logger.debug("Found test case '{}' at: {}", testCaseName, scriptFile);
                    return new com.katalan.core.compat.TestCase(normalizedPath, testCaseName, scriptFile);
                }
            } catch (IOException e) {
                logger.warn("Error listing test case directory: {}", scriptsDir, e);
            }
            logger.warn("No groovy script found in test case directory: {}", scriptsDir);
            return null;
        }
    }
    
    /**
     * CustomKeywords handler - enables calling custom keywords using Katalon syntax:
     * CustomKeywords.'package.Class.method'(args)
     */
    public static class CustomKeywordsClosure extends groovy.lang.GroovyObjectSupport {
        
        private static final Logger logger = LoggerFactory.getLogger(CustomKeywordsClosure.class);
        private final ExecutionContext context;
        private final Path projectPath;
        private final Map<String, Object> keywordInstances = new HashMap<>();
        
        public CustomKeywordsClosure(ExecutionContext context, Path projectPath) {
            this.context = context;
            this.projectPath = projectPath;
        }
        
        /**
         * Called when CustomKeywords.'package.Class.method' is accessed as property
         */
        public Object getProperty(String property) {
            logger.debug("CustomKeywords getProperty requested: {}", property);
            return new KeywordMethodInvoker(property, context, projectPath, this);
        }
        
        /**
         * Called when CustomKeywords.'package.Class.method'() is invoked
         */
        @Override
        public Object invokeMethod(String name, Object args) {
            logger.debug("CustomKeywords invokeMethod called: {}", name);
            Object[] argsArray = args instanceof Object[] ? (Object[]) args : new Object[] { args };
            return executeKeyword(name, argsArray);
        }
        
        private Object executeKeyword(String fullPath, Object[] args) {
            logger.info("Executing custom keyword: {}", fullPath);
            
            // Parse the full path: ClassName.methodName
            int lastDot = fullPath.lastIndexOf('.');
            if (lastDot < 0) {
                throw new RuntimeException("Invalid keyword path: " + fullPath);
            }
            
            String className = fullPath.substring(0, lastDot);
            String methodName = fullPath.substring(lastDot + 1);
            
            try {
                // Get or create keyword instance
                Object instance = keywordInstances.computeIfAbsent(className, k -> createKeywordInstance(k));
                if (instance == null) {
                    throw new RuntimeException("Failed to create keyword instance: " + className);
                }
                
                // Find and invoke the method
                Class<?> clazz = instance.getClass();
                java.lang.reflect.Method method = findMethod(clazz, methodName, args);
                if (method == null) {
                    throw new RuntimeException("Method not found: " + methodName + " in " + className);
                }
                
                method.setAccessible(true);
                return method.invoke(instance, args);
            } catch (Exception e) {
                logger.error("Error executing custom keyword: {} - {}", fullPath, e.getMessage());
                throw new RuntimeException("Custom keyword execution failed: " + e.getMessage(), e);
            }
        }
        
        private Object createKeywordInstance(String className) {
            logger.debug("Creating keyword instance: {}", className);
            
            // Look for keyword file in Keywords folder
            Path keywordsDir = projectPath.resolve("Keywords");
            Path keywordFile = keywordsDir.resolve(className + ".groovy");
            
            if (!Files.exists(keywordFile)) {
                // Try with different case/paths
                try {
                    Optional<Path> found = Files.walk(keywordsDir)
                        .filter(p -> p.toString().endsWith(className + ".groovy"))
                        .findFirst();
                    if (found.isPresent()) {
                        keywordFile = found.get();
                    }
                } catch (IOException e) {
                    logger.warn("Error searching for keyword file: {}", className, e);
                }
            }
            
            if (!Files.exists(keywordFile)) {
                logger.error("Keyword file not found: {}", keywordFile);
                return null;
            }
            
            try {
                String script = Files.readString(keywordFile);
                
                // Preprocess the script
                script = preprocessKeywordScript(script);
                
                // Compile and instantiate
                CompilerConfiguration config = new CompilerConfiguration();
                ImportCustomizer imports = new ImportCustomizer();
                imports.addStarImports("com.katalan.keywords", "com.katalan.core.model", "com.katalan.core.compat");
                imports.addStaticStars("com.katalan.core.compat.ObjectRepository");
                config.addCompilationCustomizers(imports);
                
                Binding binding = new Binding();
                binding.setVariable("WebUI", WebUI.class);
                binding.setVariable("GlobalVariable", GlobalVariable.class);
                binding.setVariable("findTestObject", new FindTestObjectBinding(context));
                
                GroovyClassLoader classLoader = new GroovyClassLoader(getClass().getClassLoader(), config);
                
                // Add Keywords folder, Include/scripts/groovy folder, and project JAR libraries
                // so the keyword script can resolve imports like `import website.CSWeb` or
                // `import support.ExcelLocator` from other files in Keywords folder.
                if (projectPath != null) {
                    Path kwPath = projectPath.resolve("Keywords");
                    if (Files.exists(kwPath)) {
                        try {
                            Path processed = com.katalan.core.compat.GroovySourcePreprocessor
                                    .createPreprocessedCopy(kwPath, "keywords");
                            classLoader.addClasspath(processed.toString());
                        } catch (Exception ignored) {}
                    }
                    Path incGroovyPath = projectPath.resolve("Include").resolve("scripts").resolve("groovy");
                    if (Files.exists(incGroovyPath)) {
                        try {
                            Path processed = com.katalan.core.compat.GroovySourcePreprocessor
                                    .createPreprocessedCopy(incGroovyPath, "include");
                            classLoader.addClasspath(processed.toString());
                        } catch (Exception ignored) {}
                    }
                    for (String libDir : new String[]{"Drivers", "Libs"}) {
                        Path dir = projectPath.resolve(libDir);
                        if (Files.exists(dir)) {
                            try {
                                Files.walk(dir, 1)
                                    .filter(p -> p.toString().toLowerCase().endsWith(".jar"))
                                    .forEach(jp -> {
                                        try { classLoader.addURL(jp.toUri().toURL()); } catch (Exception ignored) {}
                                    });
                            } catch (IOException ignored) {}
                        }
                    }
                    Path binLib = projectPath.resolve("bin").resolve("lib");
                    if (Files.exists(binLib)) {
                        try {
                            Files.walk(binLib, 1)
                                .filter(p -> p.toString().toLowerCase().endsWith(".jar"))
                                .forEach(jp -> {
                                    try { classLoader.addURL(jp.toUri().toURL()); } catch (Exception ignored) {}
                                });
                        } catch (IOException ignored) {}
                    }
                }
                
                GroovyShell shell = new GroovyShell(classLoader, binding, config);
                
                Class<?> clazz = shell.getClassLoader().parseClass(script);
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                logger.error("Failed to create keyword instance: {} - {}", className, e.getMessage(), e);
                return null;
            }
        }
        
        private String preprocessKeywordScript(String script) {
            String result = script;
            
            // Replace Katalon imports
            result = result.replace(
                "import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI",
                "import com.katalan.keywords.WebUI"
            );
            result = result.replaceAll(
                "import static com\\.kms\\.katalon\\.core\\.testobject\\.ObjectRepository\\.findTestObject",
                "import static com.katalan.core.compat.ObjectRepository.findTestObject"
            );
            result = result.replaceAll(
                "import com\\.kms\\.katalon\\.core\\.annotation\\..*",
                "// $0"
            );
            result = result.replaceAll(
                "import com\\.kms\\.katalon\\.core\\.webui\\.keyword\\.WebUiBuiltInKeywords.*",
                "import com.katalan.keywords.WebUI"
            );
            
            // Replace FailureHandling import specifically
            result = result.replaceAll(
                "import com\\.kms\\.katalon\\.core\\.model\\.FailureHandling.*",
                "import com.katalan.core.compat.FailureHandling"
            );
            
            // Replace TestObject import specifically
            result = result.replaceAll(
                "import com\\.kms\\.katalon\\.core\\.testobject\\.TestObject.*",
                "import com.katalan.core.model.TestObject"
            );
            
            // Comment out other Katalon imports except WebUI and model
            result = result.replaceAll(
                "import com\\.kms\\.katalon\\.core\\.(?!webui\\.keyword|model|testobject\\.TestObject).*",
                "// $0"
            );
            result = result.replace(
                "import internal.GlobalVariable",
                "import com.katalan.core.compat.GlobalVariable"
            );
            
            // Remove @Keyword annotations (not supported)
            result = result.replaceAll("@Keyword\\s*", "");
            
            // Comment out static imports that we don't support
            result = result.replaceAll(
                "import static com\\.kms\\.katalon\\.core\\.(?!testobject\\.ObjectRepository\\.findTestObject).*",
                "// $0"
            );
            
            return result;
        }
        
        private java.lang.reflect.Method findMethod(Class<?> clazz, String methodName, Object[] args) {
            for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == args.length) {
                    return method;
                }
            }
            return null;
        }
        
        /**
         * Helper class for invoking keyword methods
         */
        public static class KeywordMethodInvoker extends groovy.lang.Closure<Object> {
            private final String fullPath;
            private final ExecutionContext context;
            private final Path projectPath;
            private final CustomKeywordsClosure parent;
            
            public KeywordMethodInvoker(String fullPath, ExecutionContext context, Path projectPath, CustomKeywordsClosure parent) {
                super(null);
                this.fullPath = fullPath;
                this.context = context;
                this.projectPath = projectPath;
                this.parent = parent;
            }
            
            @Override
            public Object call(Object... args) {
                return parent.executeKeyword(fullPath, args);
            }
        }
    }
}
