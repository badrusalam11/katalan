package com.katalan.core.engine;

import com.katalan.core.context.ExecutionContext;
import com.katalan.core.model.TestObject;
import com.katalan.core.compat.FailureHandling;
import com.katalan.core.compat.GlobalVariable;
import com.katalan.core.compat.TestCase;
import com.katalan.keywords.KeywordUtil;
import com.katalan.keywords.WebUI;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Groovy Script Executor - Executes Katalon-compatible Groovy scripts
 */
public class GroovyScriptExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(GroovyScriptExecutor.class);
    
    private final GroovyShell shell;
    private final Binding binding;
    private final ExecutionContext context;
    private final Path projectPath;
    
    public GroovyScriptExecutor(ExecutionContext context) {
        this.context = context;
        this.projectPath = context.getProjectPath();
        this.binding = createBinding();
        this.shell = createShell();
    }
    
    /**
     * Refresh global variables in binding (call after GlobalVariable is loaded)
     */
    public void refreshGlobalVariables() {
        Map<String, Object> globalVars = GlobalVariable.getAllVariables();
        if (globalVars != null) {
            for (Map.Entry<String, Object> entry : globalVars.entrySet()) {
                binding.setVariable(entry.getKey(), entry.getValue());
            }
        }
    }
    
    /**
     * Create Groovy binding with Katalon-compatible variables and methods
     */
    private Binding createBinding() {
        Binding binding = new Binding();
        
        // Add WebUI class
        binding.setVariable("WebUI", WebUI.class);
        
        // Add GlobalVariable class (for static field access like GlobalVariable.varName)
        binding.setVariable("GlobalVariable", GlobalVariable.class);
        
        // Add FailureHandling enum - use Katalan internal version for compatibility with custom JARs
        binding.setVariable("FailureHandling", com.katalan.core.compat.FailureHandling.class);
        
        // Add KeywordUtil
        binding.setVariable("KeywordUtil", KeywordUtil.class);
        
        // Add findTestObject method
        binding.setVariable("findTestObject", new FindTestObjectClosure(context));
        
        // Add findTestCase method
        binding.setVariable("findTestCase", new FindTestCaseClosure(context, this));
        
        // Add common imports as variables
        binding.setVariable("Keys", org.openqa.selenium.Keys.class);
        
        // Add CustomKeywords handler
        binding.setVariable("CustomKeywords", new CustomKeywordsClosure(context));
        
        // Add CucumberKW for BDD support
        binding.setVariable("CucumberKW", new CucumberKWClosure(context));
        
        // Add all global variables directly to binding for direct access (e.g. firstName instead of GlobalVariable.firstName)
        Map<String, Object> globalVars = GlobalVariable.getAllVariables();
        if (globalVars != null) {
            for (Map.Entry<String, Object> entry : globalVars.entrySet()) {
                binding.setVariable(entry.getKey(), entry.getValue());
            }
        }
        
        return binding;
    }
    
    /**
     * Create configured GroovyShell
     */
    private GroovyShell createShell() {
        CompilerConfiguration config = new CompilerConfiguration();
        
        // Add default imports
        ImportCustomizer importCustomizer = new ImportCustomizer();
        
        // katalan native imports
        importCustomizer.addStarImports(
            "com.katalan.keywords",
            "com.katalan.core.model",
            "com.katalan.core.compat",
            "org.openqa.selenium",
            "org.openqa.selenium.support.ui"
        );
        importCustomizer.addStaticStars(
            "com.katalan.keywords.WebUI",
            "com.katalan.keywords.KeywordUtil"
        );
        
        // Katalon compatibility imports - core katalan classes
        importCustomizer.addImports(
            "com.katalan.keywords.WebUI",
            "com.katalan.core.model.TestObject",
            "com.katalan.core.compat.FailureHandling",
            "com.katalan.core.compat.GlobalVariable",
            "com.katalan.core.compat.TestCase",
            "com.katalan.core.compat.TestCaseContext"
        );
        
        config.addCompilationCustomizers(importCustomizer);
        
        // Create class loader with project's keywords and libraries
        GroovyClassLoader classLoader = new GroovyClassLoader(getClass().getClassLoader(), config);
        
        // Add custom keywords path if exists
        if (projectPath != null) {
            Path keywordsPath = projectPath.resolve("Keywords");
            if (Files.exists(keywordsPath)) {
                try {
                    classLoader.addClasspath(keywordsPath.toString());
                    logger.debug("Added Keywords path to classpath: {}", keywordsPath);
                } catch (Exception e) {
                    logger.warn("Could not add Keywords path to classpath: {}", e.getMessage());
                }
            }
            
            // Add Include/scripts/groovy folder (shared groovy step-def/helper classes)
            Path includeGroovyPath = projectPath.resolve("Include").resolve("scripts").resolve("groovy");
            if (Files.exists(includeGroovyPath)) {
                try {
                    classLoader.addClasspath(includeGroovyPath.toString());
                    logger.debug("Added Include/scripts/groovy path to classpath: {}", includeGroovyPath);
                } catch (Exception e) {
                    logger.warn("Could not add Include/scripts/groovy path to classpath: {}", e.getMessage());
                }
            }
            
            // Add JAR files from Drivers folder (custom libraries)
            Path driversPath = projectPath.resolve("Drivers");
            if (Files.exists(driversPath)) {
                loadJarsFromDirectory(classLoader, driversPath);
            }
            
            // Add JAR files from Libs folder
            Path libsPath = projectPath.resolve("Libs");
            if (Files.exists(libsPath)) {
                loadJarsFromDirectory(classLoader, libsPath);
            }
            
            // Add JAR files from bin/lib folder (Katalon structure)
            Path binLibPath = projectPath.resolve("bin").resolve("lib");
            if (Files.exists(binLibPath)) {
                loadJarsFromDirectory(classLoader, binLibPath);
            }
        }
        
        return new GroovyShell(classLoader, binding, config);
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
                        logger.info("Added JAR to classpath: {}", jarPath.getFileName());
                    } catch (Exception e) {
                        logger.warn("Could not add JAR to classpath: {} - {}", jarPath, e.getMessage());
                    }
                });
        } catch (IOException e) {
            logger.warn("Could not scan directory for JARs: {} - {}", directory, e.getMessage());
        }
    }
    
    /**
     * Execute a Groovy script file
     */
    public Object executeScript(Path scriptPath) throws IOException {
        logger.info("Executing script: {}", scriptPath);
        String scriptContent = Files.readString(scriptPath);
        return executeScript(scriptContent, scriptPath.getFileName().toString());
    }
    
    /**
     * Execute a Groovy script from string
     */
    public Object executeScript(String scriptContent) {
        return executeScript(scriptContent, "InlineScript");
    }
    
    /**
     * Execute a Groovy script with name
     */
    public Object executeScript(String scriptContent, String scriptName) {
        logger.debug("Executing script: {}", scriptName);
        try {
            // Preprocess script to handle Katalon imports
            String processedScript = preprocessKatalonScript(scriptContent);
            
            // Parse and run the script
            Script script = shell.parse(processedScript, scriptName);
            return script.run();
        } catch (Exception e) {
            logger.error("Script execution failed: {}", e.getMessage(), e);
            throw new RuntimeException("Script execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute a Groovy script file with variables (for callTestCase)
     */
    public Object executeScriptWithVariables(Path scriptPath, Map<String, Object> variables) throws IOException {
        logger.info("Executing script with variables: {}", scriptPath);
        String scriptContent = Files.readString(scriptPath);
        
        // Add variables to binding before execution
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                binding.setVariable(entry.getKey(), entry.getValue());
            }
        }
        
        return executeScript(scriptContent, scriptPath.getFileName().toString());
    }
    
    /**
     * Preprocess Katalon script to convert Katalon imports to katalan equivalents
     */
    private String preprocessKatalonScript(String script) {
        // Replace Katalon imports with katalan equivalents
        String result = script;
        
        // WebUI keywords - all variations
        result = result.replace(
            "import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI",
            "import com.katalan.keywords.WebUI"
        );
        result = result.replace(
            "import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords",
            "import com.katalan.keywords.WebUI"
        );
        
        // Mobile keywords (comment out - not supported)
        result = result.replaceAll(
            "import com\\.kms\\.katalon\\.core\\.mobile\\.keyword\\.MobileBuiltInKeywords.*",
            "// Mobile keywords not supported"
        );
        
        // WebService keywords (comment out - not supported)
        result = result.replaceAll(
            "import com\\.kms\\.katalon\\.core\\.webservice\\.keyword\\.WSBuiltInKeywords.*",
            "// WebService keywords not supported"
        );
        
        // Windows keywords (comment out - not supported)
        result = result.replaceAll(
            "import com\\.kms\\.katalon\\.core\\.windows\\.keyword\\.WindowsBuiltinKeywords.*",
            "// Windows keywords not supported"
        );
        
        // GlobalVariable
        result = result.replace(
            "import internal.GlobalVariable as GlobalVariable",
            "import com.katalan.core.compat.GlobalVariable"
        );
        result = result.replace(
            "import internal.GlobalVariable",
            "import com.katalan.core.compat.GlobalVariable"
        );
        
        // FailureHandling - convert to Katalan internal for compatibility with custom JARs
        result = result.replace(
            "import com.kms.katalon.core.model.FailureHandling as FailureHandling",
            "import com.katalan.core.compat.FailureHandling"
        );
        result = result.replace(
            "import com.kms.katalon.core.model.FailureHandling",
            "import com.katalan.core.compat.FailureHandling"
        );
        
        // TestObject - all variations
        result = result.replaceAll(
            "import com\\.kms\\.katalon\\.core\\.testobject\\.TestObject.*",
            "import com.katalan.core.model.TestObject"
        );
        result = result.replaceAll(
            "import com\\.kms\\.katalon\\.core\\.testobject\\.ObjectRepository.*",
            "// ObjectRepository handled by findTestObject binding"
        );
        
        // Static imports - findTestObject, findWindowsObject, findCheckpoint, findTestCase, findTestData
        result = result.replaceAll(
            "import static com\\.kms\\.katalon\\.core\\.testobject\\.ObjectRepository\\.findTestObject.*",
            "// findTestObject available as binding"
        );
        result = result.replaceAll(
            "import static com\\.kms\\.katalon\\.core\\.testobject\\.ObjectRepository\\.findWindowsObject.*",
            "// findWindowsObject not supported (Windows testing)"
        );
        result = result.replaceAll(
            "import static com\\.kms\\.katalon\\.core\\.checkpoint\\.CheckpointFactory\\..*",
            "// Checkpoint functions not supported"
        );
        result = result.replaceAll(
            "import static com\\.kms\\.katalon\\.core\\.testcase\\.TestCaseFactory\\..*",
            "// TestCaseFactory functions not supported"
        );
        result = result.replaceAll(
            "import static com\\.kms\\.katalon\\.core\\.testdata\\.TestDataFactory\\..*",
            "// TestDataFactory functions not supported"
        );
        
        // CucumberKW - replace with katalan Cucumber support
        result = result.replace(
            "import com.kms.katalon.core.cucumber.keyword.CucumberBuiltinKeywords as CucumberKW",
            "import com.katalan.keywords.CucumberKW"
        );
        result = result.replaceAll(
            "import com\\.kms\\.katalon\\.core\\.cucumber\\.keyword\\.CucumberBuiltinKeywords.*",
            "import com.katalan.keywords.CucumberKW"
        );
        
        // TestNG keywords (comment out - not supported)
        result = result.replaceAll(
            "import com\\.kms\\.katalon\\.core\\.testng\\.keyword\\.TestNGBuiltinKeywords.*",
            "// TestNG keywords not supported by Katalan"
        );
        
        // Comment out all unsupported com.kms.katalon imports that weren't handled above
        result = result.replaceAll(
            "import com\\.kms\\.katalon\\.core\\.checkpoint\\..*",
            "// Checkpoint not supported"
        );
        result = result.replaceAll(
            "import com\\.kms\\.katalon\\.core\\.testcase\\..*",
            "// TestCase imports not supported"
        );
        result = result.replaceAll(
            "import com\\.kms\\.katalon\\.core\\.testdata\\..*",
            "// TestData imports not supported"
        );
        result = result.replaceAll(
            "import com\\.kms\\.katalon\\.core\\.annotation\\..*",
            "// Annotations not needed"
        );
        result = result.replaceAll(
            "import com\\.kms\\.katalon\\.core\\.util\\..*",
            "// Katalon util not supported"
        );
        result = result.replaceAll(
            "import com\\.kms\\.katalon\\.core\\.configuration\\..*",
            "// Katalon configuration not supported"
        );
        result = result.replaceAll(
            "import com\\.kms\\.katalon\\.core\\.context\\..*",
            "// Katalon context not supported"
        );
        
        // Add stub methods for unsupported functions at the top of the script 
        // (after imports, before class/code)
        String stubMethods = "\n// katalan stubs for unsupported Katalon methods\n" +
            "def findCheckpoint(name) { /*stub*/ null }\n" +
            "def findTestData(name) { /*stub*/ null }\n" +
            "def findWindowsObject(name) { /*stub*/ null }\n" +
            "// TestNGKW stub class for unsupported TestNG keywords\n" +
            "class TestNGKW {\n" +
            "    static def runFeatureFile(String featureFile) { println \"[WARN] TestNGKW.runFeatureFile not supported: ${featureFile}\"; return null }\n" +
            "    static def runFeatureFolder(String featureFolder) { println \"[WARN] TestNGKW.runFeatureFolder not supported: ${featureFolder}\"; return null }\n" +
            "    static def runWithTestNGRunner(Object... args) { println \"[WARN] TestNGKW.runWithTestNGRunner not supported\"; return null }\n" +
            "    static def methodMissing(String name, args) { println \"[WARN] TestNGKW.${name} not supported\"; return null }\n" +
            "}\n";
        
        // Insert stubs after the last import statement
        int lastImportIndex = result.lastIndexOf("import ");
        if (lastImportIndex > 0) {
            int lineEnd = result.indexOf("\n", lastImportIndex);
            if (lineEnd > 0) {
                result = result.substring(0, lineEnd + 1) + stubMethods + result.substring(lineEnd + 1);
            }
        }
        
        logger.debug("Preprocessed script imports");
        return result;
    }
    
    /**
     * Execute a Groovy script with additional variables
     */
    public Object executeScript(String scriptContent, Map<String, Object> variables) {
        // Add variables to binding
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            binding.setVariable(entry.getKey(), entry.getValue());
        }
        return executeScript(scriptContent);
    }
    
    /**
     * Execute a Groovy script file with additional variables
     */
    public Object executeScript(Path scriptPath, Map<String, Object> variables) throws IOException {
        // Add variables to binding
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            binding.setVariable(entry.getKey(), entry.getValue());
        }
        return executeScript(scriptPath);
    }
    
    /**
     * Set a variable in the script binding
     */
    public void setVariable(String name, Object value) {
        binding.setVariable(name, value);
    }
    
    /**
     * Get a variable from the script binding
     */
    public Object getVariable(String name) {
        return binding.getVariable(name);
    }
    
    /**
     * Get the binding
     */
    public Binding getBinding() {
        return binding;
    }
    
    /**
     * Closure for findTestObject - mimics Katalon's findTestObject
     */
    public static class FindTestObjectClosure extends groovy.lang.Closure<TestObject> {
        
        private final ExecutionContext context;
        
        public FindTestObjectClosure(ExecutionContext context) {
            super(null);
            this.context = context;
        }
        
        public TestObject call(String path) {
            // Try to find in object repository
            TestObject obj = context.findTestObject(path);
            if (obj != null) {
                return obj;
            }
            
            // If not found, try to load from file
            Path projectPath = context.getProjectPath();
            if (projectPath != null) {
                Path objectPath = projectPath.resolve("Object Repository").resolve(path + ".rs");
                if (Files.exists(objectPath)) {
                    try {
                        return ObjectRepositoryParser.parseTestObject(objectPath);
                    } catch (Exception e) {
                        logger.warn("Failed to parse test object: {}", path, e);
                    }
                }
            }
            
            logger.warn("Test object not found: {}", path);
            return null;
        }
        
        public TestObject call(String path, Map<String, Object> variables) {
            TestObject obj = call(path);
            if (obj != null && variables != null) {
                // Replace variables in selector value
                String selectorValue = obj.getSelectorValue();
                for (Map.Entry<String, Object> entry : variables.entrySet()) {
                    String placeholder = "${" + entry.getKey() + "}";
                    selectorValue = selectorValue.replace(placeholder, String.valueOf(entry.getValue()));
                }
                obj.setSelectorValue(selectorValue);
            }
            return obj;
        }
        
        private static final Logger logger = LoggerFactory.getLogger(FindTestObjectClosure.class);
    }
    
    /**
     * Closure for findTestCase - mimics Katalon's findTestCase
     */
    public static class FindTestCaseClosure extends groovy.lang.Closure<TestCase> {
        
        private final ExecutionContext context;
        private final GroovyScriptExecutor executor;
        
        public FindTestCaseClosure(ExecutionContext context, GroovyScriptExecutor executor) {
            super(null);
            this.context = context;
            this.executor = executor;
        }
        
        public TestCase call(String path) {
            Path projectPath = context.getProjectPath();
            if (projectPath == null) {
                logger.warn("Project path not set, cannot find test case: {}", path);
                return null;
            }
            
            // Find the script path: Scripts/<path>/<ScriptXXX.groovy>
            Path scriptsDir = projectPath.resolve("Scripts").resolve(path);
            if (!Files.exists(scriptsDir) || !Files.isDirectory(scriptsDir)) {
                logger.warn("Test case directory not found: {}", scriptsDir);
                return null;
            }
            
            // Find the .groovy script file in the directory
            try {
                Path scriptFile = Files.list(scriptsDir)
                    .filter(p -> p.toString().endsWith(".groovy"))
                    .findFirst()
                    .orElse(null);
                
                if (scriptFile == null) {
                    logger.warn("No .groovy script found in: {}", scriptsDir);
                    return null;
                }
                
                // Extract test case name from path (last part)
                String testCaseName = Path.of(path).getFileName().toString();
                TestCase testCase = new TestCase(path, testCaseName, scriptFile);
                
                // Store executor in context for callTestCase to use
                context.setProperty("executor", executor);
                
                logger.debug("Found test case '{}' at: {}", testCaseName, scriptFile);
                return testCase;
            } catch (IOException e) {
                logger.error("Error finding test case: {}", path, e);
                return null;
            }
        }
        
        private static final Logger logger = LoggerFactory.getLogger(FindTestCaseClosure.class);
    }
    
    /**
     * CustomKeywords handler - enables calling custom keywords using Katalon syntax:
     * CustomKeywords.'package.Class.method'(args)
     */
    public static class CustomKeywordsClosure extends groovy.lang.GroovyObjectSupport {
        
        private static final Logger logger = LoggerFactory.getLogger(CustomKeywordsClosure.class);
        private final ExecutionContext context;
        private GroovyShell keywordShell;
        private final Map<String, Class<?>> loadedKeywordClasses = new java.util.HashMap<>();
        
        public CustomKeywordsClosure(ExecutionContext context) {
            this.context = context;
        }
        
        /**
         * Called when CustomKeywords.'package.Class.method'() is invoked
         * In Groovy, this syntax calls invokeMethod with the string as method name
         */
        @Override
        public Object invokeMethod(String name, Object args) {
            // name is like "sample.Login.loginIntoApplicationWithGlobalVariable"
            logger.debug("CustomKeywords invokeMethod called: {}", name);
            Object[] argsArray = args instanceof Object[] ? (Object[]) args : new Object[]{args};
            return executeKeyword(name, argsArray);
        }
        
        /**
         * Called when CustomKeywords.'package.Class.method' is accessed as property
         */
        @Override
        public Object getProperty(String property) {
            // property is like "sample.Login.loginIntoApplication"
            logger.debug("CustomKeywords getProperty requested: {}", property);
            return new KeywordMethodInvoker(property, context, this);
        }
        
        /**
         * Execute a custom keyword
         */
        public Object executeKeyword(String fullPath, Object[] args) {
            // Parse the full path: package.Class.method
            int lastDot = fullPath.lastIndexOf('.');
            if (lastDot < 0) {
                throw new RuntimeException("Invalid custom keyword format: " + fullPath);
            }
            
            String packageAndClass = fullPath.substring(0, lastDot);
            String methodName = fullPath.substring(lastDot + 1);
            
            logger.info("Executing custom keyword: {}.{}", packageAndClass, methodName);
            
            try {
                Class<?> keywordClass = loadKeywordClass(packageAndClass);
                if (keywordClass == null) {
                    throw new RuntimeException("Could not load custom keyword class: " + packageAndClass);
                }
                
                // Find the method
                java.lang.reflect.Method method = findMethod(keywordClass, methodName, args);
                if (method == null) {
                    throw new RuntimeException("Method not found: " + methodName + " in " + packageAndClass);
                }
                
                // Invoke the method (static methods don't need an instance)
                if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                    return method.invoke(null, args);
                } else {
                    // For instance methods, create an instance
                    Object instance = keywordClass.getDeclaredConstructor().newInstance();
                    return method.invoke(instance, args);
                }
            } catch (Exception e) {
                logger.error("Failed to execute custom keyword: {}", fullPath, e);
                if (e.getCause() != null) {
                    throw new RuntimeException("Custom keyword failed: " + fullPath + " - " + e.getCause().getMessage(), e.getCause());
                }
                throw new RuntimeException("Custom keyword failed: " + fullPath + " - " + e.getMessage(), e);
            }
        }
        
        private java.lang.reflect.Method findMethod(Class<?> clazz, String methodName, Object[] args) {
            for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    // Check parameter count match (allowing for Groovy's flexible matching)
                    if (method.getParameterCount() == args.length) {
                        return method;
                    }
                    // Also check for varargs or default parameters
                    if (args.length == 0 && method.getParameterCount() == 0) {
                        return method;
                    }
                }
            }
            // If exact match not found, try to find any method with the name
            for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    return method;
                }
            }
            return null;
        }

        /**
         * Pre-load all custom keyword classes to enable cross-references
         * Uses topological sorting to handle dependencies
         */
        private void preloadAllKeywordClasses() {
            if (keywordShell != null) {
                return; // Already loaded
            }
            
            Path projectPath = context.getProjectPath();
            if (projectPath == null) {
                return;
            }
            
            Path keywordsPath = projectPath.resolve("Keywords");
            if (!Files.exists(keywordsPath)) {
                return;
            }
            
            keywordShell = createKeywordShell();
            
            try {
                // Collect all .groovy files
                java.util.List<Path> groovyFiles = Files.walk(keywordsPath)
                    .filter(p -> p.toString().endsWith(".groovy"))
                    .collect(java.util.stream.Collectors.toList());
                
                // Try to load all files - retry if there are dependencies
                java.util.Set<Path> failedFiles = new java.util.HashSet<>(groovyFiles);
                int maxRetries = 5;
                
                for (int retry = 0; retry < maxRetries && !failedFiles.isEmpty(); retry++) {
                    java.util.Set<Path> stillFailed = new java.util.HashSet<>();
                    
                    for (Path groovyFile : failedFiles) {
                        try {
                            String scriptContent = Files.readString(groovyFile);
                            scriptContent = preprocessKeywordScript(scriptContent);
                            
                            // Parse the class (this adds it to the classloader)
                            Class<?> clazz = keywordShell.getClassLoader().parseClass(scriptContent, groovyFile.getFileName().toString());
                            
                            // Calculate the package.Class name from the file path
                            Path relativePath = keywordsPath.relativize(groovyFile);
                            String classPath = relativePath.toString()
                                .replace(".groovy", "")
                                .replace("/", ".")
                                .replace("\\", ".");
                            
                            loadedKeywordClasses.put(classPath, clazz);
                            logger.debug("Loaded keyword class: {} (retry {})", classPath, retry);
                        } catch (Exception e) {
                            // Will retry on next iteration
                            stillFailed.add(groovyFile);
                            if (retry == maxRetries - 1) {
                                logger.warn("Failed to load keyword file after {} retries: {} - {}", maxRetries, groovyFile, e.getMessage());
                            }
                        }
                    }
                    failedFiles = stillFailed;
                }
                
                logger.info("Loaded {} keyword classes", loadedKeywordClasses.size());
            } catch (IOException e) {
                logger.warn("Failed to walk keywords directory", e);
            }
        }

        /**
         * Load and compile custom keyword classes from Keywords folder
         */
        private Class<?> loadKeywordClass(String packageAndClass) {
            // Ensure all keyword classes are pre-loaded
            preloadAllKeywordClasses();
            
            if (loadedKeywordClasses.containsKey(packageAndClass)) {
                return loadedKeywordClasses.get(packageAndClass);
            }
            
            logger.warn("Keyword class not found after preloading: {}", packageAndClass);
            return null;
        }
        
        private GroovyShell createKeywordShell() {
            CompilerConfiguration config = new CompilerConfiguration();
            ImportCustomizer importCustomizer = new ImportCustomizer();
            
            // Add necessary imports for keywords
            importCustomizer.addStarImports(
                "com.katalan.keywords",
                "com.katalan.core.model",
                "com.katalan.core.compat",
                "org.openqa.selenium",
                "org.openqa.selenium.support.ui"
            );
            importCustomizer.addStaticStars(
                "com.katalan.keywords.WebUI"
            );
            importCustomizer.addImports(
                "com.katalan.keywords.WebUI",
                "com.katalan.core.model.TestObject",
                "com.katalan.core.compat.FailureHandling",
                "com.katalan.core.compat.GlobalVariable"
            );
            
            config.addCompilationCustomizers(importCustomizer);
            
            // Create binding with findTestObject
            Binding keywordBinding = new Binding();
            keywordBinding.setVariable("WebUI", com.katalan.keywords.WebUI.class);
            keywordBinding.setVariable("GlobalVariable", com.katalan.core.compat.GlobalVariable.class);
            keywordBinding.setVariable("FailureHandling", com.katalan.core.compat.FailureHandling.class);
            keywordBinding.setVariable("findTestObject", new FindTestObjectClosure(context));
            
            GroovyClassLoader classLoader = new GroovyClassLoader(getClass().getClassLoader(), config);
            return new GroovyShell(classLoader, keywordBinding, config);
        }
        
        private String preprocessKeywordScript(String script) {
            String result = script;
            
            // Replace Katalon static import for findTestObject
            result = result.replaceAll(
                "import static com\\.kms\\.katalon\\.core\\.testobject\\.ObjectRepository\\.findTestObject",
                "import static com.katalan.core.compat.ObjectRepository.findTestObject"
            );
            
            // Replace Katalon imports
            result = result.replace(
                "import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI",
                "import com.katalan.keywords.WebUI"
            );
            result = result.replace(
                "import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords",
                "import com.katalan.keywords.WebUI"
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
            
            // Replace WebUiCommonHelper and DriverFactory
            result = result.replace(
                "import com.kms.katalon.core.webui.common.WebUiCommonHelper",
                "import com.katalan.core.compat.WebUiCommonHelper"
            );
            result = result.replace(
                "import com.kms.katalon.core.webui.driver.DriverFactory",
                "import com.katalan.core.compat.DriverFactory"
            );
            
            // Comment out unsupported static imports (but not findTestObject which is already replaced)
            result = result.replaceAll(
                "import static com\\.kms\\.katalon\\.core\\.(?!testobject\\.ObjectRepository\\.findTestObject).*",
                "// $0"
            );
            result = result.replaceAll(
                "import com\\.kms\\.katalon\\.core\\.(?!webui|model).*",
                "// $0"
            );
            result = result.replaceAll(
                "import com\\.kms\\.katalon\\.core\\.testobject\\..*",
                "import com.katalan.core.model.TestObject"
            );
            // Replace Katalon annotation imports with Katalan Keyword
            result = result.replaceAll(
                "import com\\.kms\\.katalon\\.core\\.annotation\\.Keyword",
                "import com.katalan.core.compat.Keyword"
            );
            result = result.replaceAll(
                "import com\\.kms\\.katalon\\.core\\.annotation\\..*",
                "// Annotation import skipped"
            );
            
            return result;
        }
        
        /**
         * Inner class to handle method invocation on custom keywords
         * Used when accessing CustomKeywords as a property before calling method
         */
        public static class KeywordMethodInvoker extends groovy.lang.Closure<Object> {
            
            private static final Logger logger = LoggerFactory.getLogger(KeywordMethodInvoker.class);
            private final String fullPath;
            private final ExecutionContext context;
            private final CustomKeywordsClosure parent;
            
            public KeywordMethodInvoker(String fullPath, ExecutionContext context, CustomKeywordsClosure parent) {
                super(null);
                this.fullPath = fullPath;
                this.context = context;
                this.parent = parent;
            }
            
            /**
             * Called when the keyword method is invoked
             * fullPath is like "sample.Login.loginIntoApplication"
             */
            public Object call(Object... args) {
                return parent.executeKeyword(fullPath, args);
            }
        }
    }
    
    /**
     * Closure for CucumberKW - enables running Cucumber/BDD feature files
     */
    public static class CucumberKWClosure extends groovy.lang.GroovyObjectSupport {
        
        private static final Logger logger = LoggerFactory.getLogger(CucumberKWClosure.class);
        private final ExecutionContext context;
        
        public CucumberKWClosure(ExecutionContext context) {
            this.context = context;
        }
        
        /**
         * Run a feature file
         */
        public int runFeatureFile(String featureFile) {
            logger.info("CucumberKW.runFeatureFile called: {}", featureFile);
            
            // Set project path for CucumberKW
            com.katalan.keywords.CucumberKW.setProjectPath(context.getProjectPath());
            
            return com.katalan.keywords.CucumberKW.runFeatureFile(featureFile);
        }
        
        /**
         * Run a folder of feature files
         */
        public int runFeatureFolder(String featureFolder) {
            logger.info("CucumberKW.runFeatureFolder called: {}", featureFolder);
            
            com.katalan.keywords.CucumberKW.setProjectPath(context.getProjectPath());
            
            return com.katalan.keywords.CucumberKW.runFeatureFolder(featureFolder);
        }
        
        /**
         * Handle method invocation dynamically
         */
        @Override
        public Object invokeMethod(String name, Object args) {
            Object[] argsArray = args instanceof Object[] ? (Object[]) args : new Object[]{args};
            
            if ("runFeatureFile".equals(name) && argsArray.length > 0) {
                return runFeatureFile(String.valueOf(argsArray[0]));
            } else if ("runFeatureFolder".equals(name) && argsArray.length > 0) {
                return runFeatureFolder(String.valueOf(argsArray[0]));
            }
            
            logger.warn("Unknown CucumberKW method: {}", name);
            return null;
        }
    }
}
