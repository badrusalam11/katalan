package com.katalan.keywords;

import com.katalan.core.context.ExecutionContext;
import com.katalan.core.compat.GlobalVariable;
import com.katalan.core.compat.FailureHandling;
import com.katalan.core.compat.TestCase;
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
    
    public KatalanBDDExecutor(ExecutionContext context, Path projectPath, Path stepsPath) {
        this.context = context;
        this.projectPath = projectPath;
        this.stepsPath = stepsPath;
        this.stepDefinitions = new ArrayList<>();
        this.stepInstances = new HashMap<>();
        this.groovyShell = createGroovyShell();
        
        // Store this executor in context so WebUI.callTestCase can use it
        context.setProperty("executor", this);
        
        // Load step definitions
        loadStepDefinitions();
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
     * Preprocess test case script (same as step definition preprocessing)
     */
    private String preprocessTestCaseScript(String script) {
        return preprocessStepDefinitionScript(script);
    }

    /**
     * Execute a feature file
     */
    public int executeFeature(Path featurePath) throws IOException {
        logger.info("Executing feature: {}", featurePath);
        
        String featureContent = Files.readString(featurePath);
        List<Scenario> scenarios = parseFeature(featureContent);
        
        int failedCount = 0;
        for (Scenario scenario : scenarios) {
            logger.info("Executing scenario: {}", scenario.name);
            
            try {
                executeScenario(scenario);
                logger.info("Scenario PASSED: {}", scenario.name);
            } catch (Exception e) {
                logger.error("Scenario FAILED: {} - {}", scenario.name, e.getMessage());
                failedCount++;
            }
        }
        
        return failedCount;
    }
    
    /**
     * Execute a single scenario
     */
    private void executeScenario(Scenario scenario) {
        for (Step step : scenario.steps) {
            executeStep(step);
        }
    }
    
    /**
     * Execute a single step
     */
    private void executeStep(Step step) {
        logger.info("  {} {}", step.keyword, step.text);
        
        // Find matching step definition
        StepMatch match = findStepDefinition(step.text);
        if (match == null) {
            throw new RuntimeException("No step definition found for: " + step.keyword + " " + step.text);
        }
        
        try {
            // Invoke the step definition method
            match.invoke();
            logger.debug("    Step passed");
        } catch (Exception e) {
            logger.error("    Step failed: {}", e.getMessage());
            throw new RuntimeException("Step failed: " + step.keyword + " " + step.text, e);
        }
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
        try {
            String content = Files.readString(groovyFile);
            content = preprocessStepDefinitionScript(content);
            
            Class<?> clazz = groovyShell.getClassLoader().parseClass(content, groovyFile.getFileName().toString());
            Object instance = clazz.getDeclaredConstructor().newInstance();
            
            // Store instance for later invocation
            stepInstances.put(clazz.getName(), instance);
            
            // Find all methods with step annotations
            for (Method method : clazz.getDeclaredMethods()) {
                for (Annotation annotation : method.getAnnotations()) {
                    String annotationType = annotation.annotationType().getSimpleName();
                    if (isStepAnnotation(annotationType)) {
                        String patternStr = getAnnotationValue(annotation);
                        if (patternStr != null) {
                            // Convert Cucumber expression to regex
                            Pattern pattern = convertToRegex(patternStr);
                            stepDefinitions.add(new StepDefinition(pattern, method, instance, annotationType));
                            logger.debug("Loaded step: {} {} -> {}.{}", annotationType, patternStr, 
                                clazz.getSimpleName(), method.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load step definitions from: {} - {}", groovyFile, e.getMessage());
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
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            if (line.startsWith("Feature:")) {
                // Feature declaration, skip
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
                currentScenario = new Scenario(name);
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
                    currentScenario.steps.add(new Step(keyword, text));
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
            Scenario scenario = new Scenario(outline.name + " - " + example.values());
            for (Step step : outline.steps) {
                String expandedText = step.text;
                for (Map.Entry<String, String> entry : example.entrySet()) {
                    expandedText = expandedText.replace("<" + entry.getKey() + ">", entry.getValue());
                }
                scenario.steps.add(new Step(step.keyword, expandedText));
            }
            expanded.add(scenario);
        }
        
        return expanded;
    }
    
    private String preprocessStepDefinitionScript(String script) {
        String result = script;
        
        // Replace Katalon imports with katalan equivalents
        result = result.replace(
            "import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI",
            "import com.katalan.keywords.WebUI"
        );
        result = result.replaceAll(
            "import com\\.kms\\.katalon\\.core\\.webui\\.keyword\\.WebUiBuiltInKeywords.*",
            "import com.katalan.keywords.WebUI"
        );
        
        // Replace static findTestObject import with katalan version
        result = result.replaceAll(
            "import static com\\.kms\\.katalon\\.core\\.testobject\\.ObjectRepository\\.findTestObject",
            "import static com.katalan.core.compat.ObjectRepository.findTestObject"
        );
        
        // Replace static findTestCase import
        result = result.replaceAll(
            "import static com\\.kms\\.katalon\\.core\\.testcase\\.TestCaseFactory\\.findTestCase",
            "import static com.katalan.core.compat.TestCaseFinder.findTestCase"
        );
        
        result = result.replace(
            "import internal.GlobalVariable as GlobalVariable",
            "import com.katalan.core.compat.GlobalVariable"
        );
        result = result.replace(
            "import internal.GlobalVariable",
            "import com.katalan.core.compat.GlobalVariable"
        );
        result = result.replace(
            "import com.kms.katalon.core.model.FailureHandling as FailureHandling",
            "import com.katalan.core.compat.FailureHandling"
        );
        result = result.replace(
            "import com.kms.katalon.core.model.FailureHandling",
            "import com.katalan.core.compat.FailureHandling"
        );
        result = result.replace(
            "import com.kms.katalon.core.webui.driver.DriverFactory",
            "import com.katalan.core.compat.DriverFactory"
        );
        
        // Convert old Cucumber API imports to new io.cucumber imports
        result = result.replace(
            "import cucumber.api.java.en.Given",
            "import io.cucumber.java.en.Given"
        );
        result = result.replace(
            "import cucumber.api.java.en.When",
            "import io.cucumber.java.en.When"
        );
        result = result.replace(
            "import cucumber.api.java.en.Then",
            "import io.cucumber.java.en.Then"
        );
        result = result.replace(
            "import cucumber.api.java.en.And",
            "import io.cucumber.java.en.And"
        );
        result = result.replace(
            "import cucumber.api.java.en.But",
            "import io.cucumber.java.en.But"
        );
        
        // Comment out apache commons lang (old version, not lang3)
        result = result.replaceAll(
            "import org\\.apache\\.commons\\.lang\\.([A-Z].*)",
            "// import org.apache.commons.lang.$1 - not available"
        );
        
        // Comment out Katalon utility imports that we don't have
        result = result.replaceAll(
            "import com\\.kms\\.katalon\\.util\\..*",
            "// $0 - not available"
        );
        
        // Replace static findTestObject imports
        result = result.replaceAll(
            "import static com\\.kms\\.katalon\\.core\\.testobject\\.ObjectRepository\\.findTestObject",
            "import static com.katalan.core.compat.ObjectRepository.findTestObject"
        );
        
        // Comment out unsupported Katalon imports (exceptions, mobile, etc.)
        result = result.replaceAll(
            "import com\\.kms\\.katalon\\.core\\.webui\\.exception\\..*",
            "// $0 - not supported"
        );
        result = result.replaceAll(
            "import com\\.kms\\.katalon\\.core\\.mobile\\..*",
            "// $0 - not supported"
        );
        result = result.replaceAll(
            "import static com\\.kms\\.katalon\\.core\\.(?!testobject\\.ObjectRepository\\.findTestObject).*",
            "// $0"
        );
        result = result.replaceAll(
            "import com\\.kms\\.katalon\\.core\\.(?!webui\\.keyword|model|testobject\\.TestObject).*",
            "// $0"
        );
        result = result.replaceAll(
            "import com\\.kms\\.katalon\\.core\\.testobject\\.(?!TestObject).*",
            "// $0"
        );
        result = result.replaceAll(
            "import com\\.kms\\.katalon\\.core\\.testobject\\.TestObject.*",
            "import com.katalan.core.model.TestObject"
        );
        
        return result;
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
        return new GroovyShell(classLoader, binding, config);
    }
    
    // Inner classes
    
    private static class Scenario {
        String name;
        List<Step> steps = new ArrayList<>();
        
        Scenario(String name) {
            this.name = name;
        }
    }
    
    private static class Step {
        String keyword;
        String text;
        
        Step(String keyword, String text) {
            this.keyword = keyword;
            this.text = text;
        }
    }
    
    private static class StepDefinition {
        Pattern pattern;
        Method method;
        Object instance;
        String keyword;
        
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
            definition.method.invoke(definition.instance, parameters.toArray());
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
