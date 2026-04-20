package com.katalan.core.compat;

import com.katalan.core.context.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * TestCaseFinder - Static utility to find test cases
 * Used by step definitions in BDD tests
 */
public class TestCaseFinder {
    
    private static final Logger logger = LoggerFactory.getLogger(TestCaseFinder.class);
    
    /**
     * Find a test case by path
     * @param path The test case path (e.g., "Test Cases/common/My Test Case")
     * @return TestCase object or null if not found
     */
    public static TestCase findTestCase(String path) {
        ExecutionContext context = ExecutionContext.getCurrent();
        if (context == null) {
            logger.warn("No execution context available for findTestCase: {}", path);
            return null;
        }
        
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
                return new TestCase(normalizedPath, testCaseName, scriptFile);
            }
        } catch (IOException e) {
            logger.warn("Error listing test case directory: {}", scriptsDir, e);
        }
        
        logger.warn("No groovy script found in test case directory: {}", scriptsDir);
        return null;
    }
}
