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
     * Create Groovy binding with Katalon-compatible variables and methods
     */
    private Binding createBinding() {
        Binding binding = new Binding();
        
        // Add WebUI class
        binding.setVariable("WebUI", WebUI.class);
        
        // Add GlobalVariable class (for static field access like GlobalVariable.varName)
        binding.setVariable("GlobalVariable", GlobalVariable.class);
        
        // Add FailureHandling enum
        binding.setVariable("FailureHandling", FailureHandling.class);
        
        // Add KeywordUtil
        binding.setVariable("KeywordUtil", KeywordUtil.class);
        
        // Add findTestObject method
        binding.setVariable("findTestObject", new FindTestObjectClosure(context));
        
        // Add findTestCase method
        binding.setVariable("findTestCase", new FindTestCaseClosure(context, this));
        
        // Add common imports as variables
        binding.setVariable("Keys", org.openqa.selenium.Keys.class);
        
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
        
        // Create class loader with project's keywords
        GroovyClassLoader classLoader = new GroovyClassLoader(getClass().getClassLoader(), config);
        
        // Add custom keywords path if exists
        if (projectPath != null) {
            Path keywordsPath = projectPath.resolve("Keywords");
            if (Files.exists(keywordsPath)) {
                try {
                    classLoader.addClasspath(keywordsPath.toString());
                } catch (Exception e) {
                    logger.warn("Could not add Keywords path to classpath: {}", e.getMessage());
                }
            }
        }
        
        return new GroovyShell(classLoader, binding, config);
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
        
        // FailureHandling - all variations
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
        
        // Static imports - findTestObject, findCheckpoint, findTestCase, findTestData
        result = result.replaceAll(
            "import static com\\.kms\\.katalon\\.core\\.testobject\\.ObjectRepository\\.findTestObject.*",
            "// findTestObject available as binding"
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
            "import com\\.kms\\.katalon\\.core\\.cucumber\\..*",
            "// Cucumber integration not supported"
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
            "def findTestData(name) { /*stub*/ null }\n";
        
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
}
