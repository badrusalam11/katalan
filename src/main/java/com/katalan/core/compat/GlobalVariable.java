package com.katalan.core.compat;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Katalon-compatible GlobalVariable class
 * Provides access to global variables defined in execution profiles
 * 
 * In Katalon, GlobalVariable is accessed as:
 *   GlobalVariable.variableName
 * 
 * In katalan, we store variables in a map and provide dynamic access
 */
public class GlobalVariable {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalVariable.class);
    
    // Static storage for all global variables
    private static final Map<String, Object> variables = new ConcurrentHashMap<>();
    
    // Static fields for common variables (for direct field access compatibility)
    public static String G_Url = "";
    public static String G_ShortTimeOut = "5";
    public static String G_LongTimeOut = "30";
    public static String G_Timeout = "10";
    public static String G_Browser = "Chrome";
    
    // Healthcare example specific variables
    public static String sAppUrl = "";
    public static String sUsername = "";
    public static String sPassword = "";
    public static int iTimeOut = 10;
    
    // Calculator project variables
    public static String baseUrl = "";
    
    /**
     * Set a global variable
     */
    public static void set(String name, Object value) {
        variables.put(name, value);
        logger.info("Set GlobalVariable.{} = {}", name, value);
        
        // Also set known static fields for direct access compatibility
        try {
            setStaticField(name, value);
            logger.info("Set static field GlobalVariable.{} successfully", name);
        } catch (Exception e) {
            // Field doesn't exist as static, just use map
            logger.debug("No static field for: {}", name);
        }
    }
    
    /**
     * Get a global variable
     */
    public static Object get(String name) {
        // Special handling for metaClass - return the Groovy MetaClass
        if ("metaClass".equals(name)) {
            return getGroovyMetaClass();
        }
        
        Object value = variables.get(name);
        if (value == null) {
            // Try static field
            try {
                java.lang.reflect.Field field = GlobalVariable.class.getDeclaredField(name);
                value = field.get(null);
            } catch (Exception e) {
                logger.warn("GlobalVariable not found: {}", name);
            }
        }
        return value;
    }
    
    /**
     * Get a global variable with default value
     */
    public static Object get(String name, Object defaultValue) {
        Object value = get(name);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Get a global variable as String
     */
    public static String getString(String name) {
        Object value = get(name);
        return value != null ? String.valueOf(value) : null;
    }
    
    /**
     * Get a global variable as String with default
     */
    public static String getString(String name, String defaultValue) {
        String value = getString(name);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Get a global variable as Integer
     */
    public static Integer getInt(String name) {
        Object value = get(name);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * Get a global variable as Integer with default
     */
    public static int getInt(String name, int defaultValue) {
        Integer value = getInt(name);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Check if a global variable exists
     */
    public static boolean exists(String name) {
        if (variables.containsKey(name)) return true;
        try {
            GlobalVariable.class.getDeclaredField(name);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }
    
    /**
     * Load all variables from a map
     */
    public static void loadAll(Map<String, Object> vars) {
        for (Map.Entry<String, Object> entry : vars.entrySet()) {
            set(entry.getKey(), entry.getValue());
        }
        logger.info("Loaded {} global variables", vars.size());
    }
    
    /**
     * Clear all global variables
     */
    public static void clear() {
        variables.clear();
        // Reset static fields to defaults
        G_Url = "";
        G_ShortTimeOut = "5";
        G_LongTimeOut = "30";
        G_Timeout = "10";
        G_Browser = "Chrome";
        sAppUrl = "";
        sUsername = "";
        sPassword = "";
        iTimeOut = 10;
        baseUrl = "";
        logger.debug("Cleared all global variables");
    }
    
    /**
     * Get all variables as map
     */
    public static Map<String, Object> getAll() {
        Map<String, Object> all = new ConcurrentHashMap<>(variables);
        // Add static fields
        all.put("G_Url", G_Url);
        all.put("G_ShortTimeOut", G_ShortTimeOut);
        all.put("G_LongTimeOut", G_LongTimeOut);
        all.put("G_Timeout", G_Timeout);
        all.put("G_Browser", G_Browser);
        all.put("sAppUrl", sAppUrl);
        all.put("sUsername", sUsername);
        all.put("sPassword", sPassword);
        all.put("iTimeOut", iTimeOut);
        return all;
    }
    
    /**
     * Get all variables as map (alias for getAll)
     */
    public static Map<String, Object> getAllVariables() {
        return getAll();
    }
    
    /**
     * Set a static field by name
     */
    private static void setStaticField(String name, Object value) throws Exception {
        java.lang.reflect.Field field = GlobalVariable.class.getDeclaredField(name);
        field.setAccessible(true);
        
        Class<?> fieldType = field.getType();
        if (fieldType == String.class) {
            field.set(null, String.valueOf(value));
        } else if (fieldType == int.class || fieldType == Integer.class) {
            if (value instanceof Number) {
                field.setInt(null, ((Number) value).intValue());
            } else {
                field.setInt(null, Integer.parseInt(String.valueOf(value)));
            }
        } else if (fieldType == long.class || fieldType == Long.class) {
            if (value instanceof Number) {
                field.setLong(null, ((Number) value).longValue());
            } else {
                field.setLong(null, Long.parseLong(String.valueOf(value)));
            }
        } else if (fieldType == boolean.class || fieldType == Boolean.class) {
            if (value instanceof Boolean) {
                field.setBoolean(null, (Boolean) value);
            } else {
                field.setBoolean(null, Boolean.parseBoolean(String.valueOf(value)));
            }
        } else if (fieldType == double.class || fieldType == Double.class) {
            if (value instanceof Number) {
                field.setDouble(null, ((Number) value).doubleValue());
            } else {
                field.setDouble(null, Double.parseDouble(String.valueOf(value)));
            }
        } else {
            field.set(null, value);
        }
    }
    
    // ==========================================
    // Groovy MetaClass Support
    // ==========================================
    
    /**
     * Get the Groovy MetaClass for this class.
     * This is called when Groovy code accesses GlobalVariable.metaClass
     */
    private static Object getGroovyMetaClass() {
        try {
            Class<?> groovySystemClass = Class.forName("groovy.lang.GroovySystem");
            Object metaClassRegistry = groovySystemClass.getMethod("getMetaClassRegistry").invoke(null);
            Object metaClass = metaClassRegistry.getClass()
                .getMethod("getMetaClass", Class.class)
                .invoke(metaClassRegistry, GlobalVariable.class);
            logger.debug("Returning MetaClass for GlobalVariable");
            return metaClass;
        } catch (Exception e) {
            logger.warn("Could not get MetaClass: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * This method is called by Groovy when accessing a property that doesn't exist.
     * It allows us to intercept dynamic property access.
     */
    public static Object $static_propertyMissing(String name) {
        if ("metaClass".equals(name)) {
            return getGroovyMetaClass();
        }
        
        // For other properties, delegate to get()
        return get(name);
    }
}
