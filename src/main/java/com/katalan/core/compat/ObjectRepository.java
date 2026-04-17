package com.katalan.core.compat;

import com.katalan.core.context.ExecutionContext;
import com.katalan.core.engine.ObjectRepositoryParser;
import com.katalan.core.model.TestObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * ObjectRepository - provides static findTestObject method for Katalon compatibility
 * This class is used by custom keywords to find test objects
 */
public class ObjectRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(ObjectRepository.class);
    private static ExecutionContext context;
    
    /**
     * Set the execution context (called during engine initialization)
     */
    public static void setContext(ExecutionContext ctx) {
        context = ctx;
    }
    
    /**
     * Find a test object by path
     * @param path The path to the test object (e.g., "Pages/MyPage/myElement")
     * @return TestObject or null if not found
     */
    public static TestObject findTestObject(String path) {
        if (context == null) {
            logger.warn("ObjectRepository context not set");
            return null;
        }
        
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
    
    /**
     * Find a test object by path with variable substitution
     * @param path The path to the test object
     * @param variables Variables to substitute in the selector
     * @return TestObject or null if not found
     */
    public static TestObject findTestObject(String path, Map<String, Object> variables) {
        TestObject obj = findTestObject(path);
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
}
