package com.katalan.core.engine;

import com.katalan.core.model.TestCase;
import com.katalan.core.model.TestSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for Katalon Test Suite files (.ts) and Test Case files
 */
public class TestSuiteParser {
    
    private static final Logger logger = LoggerFactory.getLogger(TestSuiteParser.class);
    
    private final Path projectPath;
    
    public TestSuiteParser(Path projectPath) {
        this.projectPath = projectPath;
    }
    
    /**
     * Parse a Katalon Test Suite file (.ts)
     */
    public TestSuite parseTestSuite(Path suitePath) throws IOException {
        logger.info("Parsing test suite: {}", suitePath);
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(suitePath.toFile());
            doc.getDocumentElement().normalize();
            
            Element root = doc.getDocumentElement();
            TestSuite suite = new TestSuite();
            suite.setSuitePath(suitePath);
            
            // Get suite name
            String name = getElementText(root, "name");
            if (name == null) {
                name = suitePath.getFileName().toString().replace(".ts", "");
            }
            suite.setName(name);
            suite.setId(name.replaceAll("\\s+", "_").toLowerCase());
            
            // Get description
            suite.setDescription(getElementText(root, "description"));
            
            // Parse test case links
            NodeList testCaseLinks = root.getElementsByTagName("testCaseLink");
            int skipped = 0;
            for (int i = 0; i < testCaseLinks.getLength(); i++) {
                Element link = (Element) testCaseLinks.item(i);
                
                // Respect <isRun>false</isRun> flag - skip disabled test cases
                String isRunText = getElementText(link, "isRun");
                if (isRunText != null && "false".equalsIgnoreCase(isRunText.trim())) {
                    skipped++;
                    logger.debug("Skipping disabled test case: {}", getElementText(link, "testCaseId"));
                    continue;
                }
                
                // Get test case ID (path)
                String testCaseId = getElementText(link, "testCaseId");
                if (testCaseId != null) {
                    TestCase testCase = loadTestCase(testCaseId);
                    if (testCase != null) {
                        suite.addTestCase(testCase);
                    }
                }
            }
            if (skipped > 0) {
                logger.info("Skipped {} test case(s) marked isRun=false", skipped);
            }
            
            logger.info("Parsed test suite: {} with {} test cases", name, suite.getTestCases().size());
            return suite;
            
        } catch (Exception e) {
            logger.error("Failed to parse test suite: {}", suitePath, e);
            throw new IOException("Failed to parse test suite: " + suitePath, e);
        }
    }
    
    /**
     * Load a test case from Katalon project
     */
    public TestCase loadTestCase(String testCaseId) {
        logger.debug("Loading test case: {}", testCaseId);
        
        // Katalon test case ID format: "Test Cases/folder/TestName"
        // Convert to path: projectPath/Scripts/folder/TestName/Script1234567890.groovy
        
        String relativePath = testCaseId;
        if (relativePath.startsWith("Test Cases/")) {
            relativePath = relativePath.substring("Test Cases/".length());
        }
        
        // First try to load metadata from .tc file (for variables)
        Path testCasePath = projectPath.resolve("Test Cases").resolve(relativePath + ".tc");
        if (Files.exists(testCasePath)) {
            try {
                return parseTestCaseMetadata(testCasePath, testCaseId);
            } catch (Exception e) {
                logger.warn("Failed to parse test case metadata: {}", testCaseId, e);
            }
        }
        
        // Fallback: Just load the script file
        Path scriptsPath = projectPath.resolve("Scripts").resolve(relativePath);
        if (Files.exists(scriptsPath) && Files.isDirectory(scriptsPath)) {
            // Find the .groovy file in the folder
            try {
                Path groovyScript = Files.walk(scriptsPath, 1)
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".groovy"))
                        .findFirst()
                        .orElse(null);
                
                if (groovyScript != null) {
                    TestCase testCase = new TestCase();
                    testCase.setName(relativePath);  // Use relativePath (without prefix) for console log
                    testCase.setId(testCaseId);  // Keep full ID with prefix for testCaseBinding
                    testCase.setScriptPath(groovyScript);
                    testCase.setScriptContent(Files.readString(groovyScript));
                    return testCase;
                }
            } catch (IOException e) {
                logger.warn("Failed to load test case script: {}", testCaseId, e);
            }
        }
        
        logger.warn("Test case not found: {}", testCaseId);
        return null;
    }
    
    /**
     * Parse test case metadata file (.tc)
     */
    private TestCase parseTestCaseMetadata(Path tcPath, String testCaseId) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(tcPath.toFile());
        doc.getDocumentElement().normalize();
        
        Element root = doc.getDocumentElement();
        TestCase testCase = new TestCase();
        
        String name = getElementText(root, "name");
        if (name == null) {
            name = extractTestCaseName(testCaseId);
        }
        
        // Strip "Test Cases/" prefix from testCaseId to get relative path for console log
        String testCaseName = testCaseId;
        if (testCaseName.startsWith("Test Cases/")) {
            testCaseName = testCaseName.substring("Test Cases/".length());
        }
        
        testCase.setName(testCaseName);  // Use relative path (without prefix) for console log
        testCase.setId(testCaseId);  // Keep full ID with prefix for testCaseBinding
        testCase.setDescription(getElementText(root, "description"));
        
        // Parse variables from .tc file
        NodeList variables = root.getElementsByTagName("variable");
        for (int i = 0; i < variables.getLength(); i++) {
            Element varElement = (Element) variables.item(i);
            String varName = getElementText(varElement, "name");
            String defaultValue = getElementText(varElement, "defaultValue");
            
            if (varName != null) {
                Object parsedValue = parseGroovyValue(defaultValue);
                testCase.addVariable(varName, parsedValue);
                logger.debug("Loaded test case variable: {} = {}", varName, parsedValue);
            }
        }
        
        // Find associated script
        String relativePath = testCaseId;
        if (relativePath.startsWith("Test Cases/")) {
            relativePath = relativePath.substring("Test Cases/".length());
        }
        Path scriptsPath = projectPath.resolve("Scripts").resolve(relativePath);
        if (Files.exists(scriptsPath) && Files.isDirectory(scriptsPath)) {
            Path groovyScript = Files.walk(scriptsPath, 1)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".groovy"))
                    .findFirst()
                    .orElse(null);
            
            if (groovyScript != null) {
                testCase.setScriptPath(groovyScript);
                testCase.setScriptContent(Files.readString(groovyScript));
            }
        }
        
        return testCase;
    }
    
    /**
     * Parse Groovy-style value string (e.g., 'string', 10, true, GlobalVariable.xxx)
     */
    private Object parseGroovyValue(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        
        value = value.trim();
        
        // String literals
        if ((value.startsWith("'") && value.endsWith("'")) ||
            (value.startsWith("\"") && value.endsWith("\""))) {
            return value.substring(1, value.length() - 1);
        }
        
        // Boolean
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        
        // Null
        if ("null".equalsIgnoreCase(value)) return null;
        
        // GlobalVariable reference - evaluate it
        if (value.startsWith("GlobalVariable.")) {
            String varName = value.substring("GlobalVariable.".length());
            Object globalValue = com.katalan.core.compat.GlobalVariable.get(varName);
            if (globalValue != null) {
                logger.debug("Resolved GlobalVariable.{} = {}", varName, globalValue);
                return globalValue;
            }
            // If not found, log and return the expression as-is (might be resolved later)
            logger.debug("GlobalVariable.{} not found yet, will be resolved at runtime", varName);
            return new GlobalVariableReference(varName);
        }
        
        // Number
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            // Return as string
        }
        
        return value;
    }
    
    /**
     * Wrapper class for deferred GlobalVariable resolution
     */
    public static class GlobalVariableReference {
        private final String variableName;
        
        public GlobalVariableReference(String variableName) {
            this.variableName = variableName;
        }
        
        public String getVariableName() {
            return variableName;
        }
        
        public Object resolve() {
            return com.katalan.core.compat.GlobalVariable.get(variableName);
        }
        
        @Override
        public String toString() {
            Object value = resolve();
            return value != null ? value.toString() : "GlobalVariable." + variableName;
        }
    }
    
    /**
     * Load all test cases from a folder
     */
    public List<TestCase> loadTestCasesFromFolder(Path folder) throws IOException {
        List<TestCase> testCases = new ArrayList<>();
        
        if (!Files.exists(folder)) {
            logger.warn("Test cases folder not found: {}", folder);
            return testCases;
        }
        
        Files.walk(folder)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".groovy"))
                .forEach(p -> {
                    try {
                        TestCase testCase = new TestCase();
                        testCase.setName(p.getFileName().toString().replace(".groovy", ""));
                        testCase.setScriptPath(p);
                        testCase.setScriptContent(Files.readString(p));
                        testCases.add(testCase);
                    } catch (Exception e) {
                        logger.warn("Failed to load test case: {}", p, e);
                    }
                });
        
        return testCases;
    }
    
    /**
     * Create a test suite from a list of test case paths
     */
    public TestSuite createSuiteFromTestCases(String suiteName, List<Path> testCasePaths) throws IOException {
        TestSuite suite = new TestSuite(suiteName);
        
        for (Path path : testCasePaths) {
            TestCase testCase = new TestCase();
            testCase.setName(path.getFileName().toString().replace(".groovy", ""));
            testCase.setScriptPath(path);
            testCase.setScriptContent(Files.readString(path));
            suite.addTestCase(testCase);
        }
        
        return suite;
    }
    
    /**
     * Create a test suite from a single test case
     */
    public TestSuite createSuiteFromTestCase(Path testCasePath) throws IOException {
        String suiteName = testCasePath.getFileName().toString().replace(".groovy", "");
        return createSuiteFromTestCases(suiteName, List.of(testCasePath));
    }
    
    /**
     * Get text content of a child element
     */
    private String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return null;
    }
    
    /**
     * Extract test case name from ID
     */
    private String extractTestCaseName(String testCaseId) {
        String[] parts = testCaseId.replace("\\", "/").split("/");
        return parts[parts.length - 1];
    }
}
