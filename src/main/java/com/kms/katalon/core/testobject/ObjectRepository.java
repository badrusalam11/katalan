package com.kms.katalon.core.testobject;

import com.katalan.core.context.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Katalon API Compatibility - ObjectRepository
 * 
 * Provides access to test objects defined in Object Repository
 */
public class ObjectRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(ObjectRepository.class);
    
    /**
     * Find a TestObject by its ID/path in the Object Repository
     */
    public static TestObject findTestObject(String objectId) {
        return findTestObject(objectId, (Object[]) null);
    }
    
    /**
     * Find a TestObject by its ID/path with parameterized values
     */
    public static TestObject findTestObject(String objectId, Object... params) {
        try {
            ExecutionContext context = ExecutionContext.getCurrent();
            if (context == null) {
                logger.warn("No execution context available, creating basic TestObject for: {}", objectId);
                return new TestObject(objectId);
            }
            
            // Get from Katalan's object repository
            com.katalan.core.model.TestObject katalanObj = context.getTestObject(objectId);
            if (katalanObj != null) {
                TestObject to = TestObject.fromKatalanTestObject(katalanObj);
                
                // Apply parameters if provided
                if (params != null && params.length > 0) {
                    applyParameters(to, params);
                }
                
                return to;
            }
            
            logger.warn("TestObject not found in repository: {}", objectId);
            return new TestObject(objectId);
            
        } catch (Exception e) {
            logger.error("Error finding TestObject: " + objectId, e);
            return new TestObject(objectId);
        }
    }
    
    /**
     * Apply parameterized values to TestObject properties
     */
    private static void applyParameters(TestObject testObject, Object[] params) {
        if (params == null || params.length == 0) return;
        
        // Parameters are typically key-value pairs or indexed replacements
        // For simple indexed replacement: ${1}, ${2}, etc.
        for (TestObjectProperty prop : testObject.getProperties()) {
            String value = prop.getValue();
            if (value != null) {
                for (int i = 0; i < params.length; i++) {
                    String paramValue = params[i] != null ? params[i].toString() : "";
                    value = value.replace("${" + (i + 1) + "}", paramValue);
                }
                prop.setValue(value);
            }
        }
        
        // Also update selector collection
        for (SelectorMethod method : testObject.getSelectorCollection().keySet()) {
            String value = testObject.getSelectorValue(method);
            if (value != null) {
                for (int i = 0; i < params.length; i++) {
                    String paramValue = params[i] != null ? params[i].toString() : "";
                    value = value.replace("${" + (i + 1) + "}", paramValue);
                }
                testObject.setSelectorValue(method, value);
            }
        }
    }
}
