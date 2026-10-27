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
     * Katalon's parameterized test object: every {@code ${name}} in the object's properties and
     * selectors is replaced by {@code variables[name]}, e.g. a mobile object whose locator is
     * {@code ${set}} looked up with {@code findTestObject('Mobile/Custom/xpath', [set: xpath])}.
     */
    public static TestObject findTestObject(String objectId, java.util.Map<String, Object> variables) {
        TestObject to = findTestObject(objectId, (Object[]) null);
        if (to != null && variables != null && !variables.isEmpty()) {
            applyVariables(to, variables);
        }
        return to;
    }

    private static void applyVariables(TestObject testObject, java.util.Map<String, Object> variables) {
        for (TestObjectProperty prop : testObject.getProperties()) {
            prop.setValue(substitute(prop.getValue(), variables));
        }
        for (SelectorMethod method : new java.util.ArrayList<>(testObject.getSelectorCollection().keySet())) {
            testObject.setSelectorValue(method, substitute(testObject.getSelectorValue(method), variables));
        }
    }

    private static String substitute(String value, java.util.Map<String, Object> variables) {
        if (value == null || value.indexOf('$') < 0) {
            return value;
        }
        for (java.util.Map.Entry<String, Object> e : variables.entrySet()) {
            String replacement = e.getValue() != null ? e.getValue().toString() : "";
            value = value.replace("${" + e.getKey() + "}", replacement);
        }
        return value;
    }

    /**
     * Find a TestObject by its ID/path with parameterized values
     */
    public static TestObject findTestObject(String objectId, Object... params) {
        if (params != null && params.length == 1 && params[0] instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> variables = (java.util.Map<String, Object>) params[0];
            return findTestObject(objectId, variables);
        }
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
