package internal;

import java.util.Map;

/**
 * Katalon compatibility class: internal.GlobalVariable
 * 
 * Katalon scripts import this via: import internal.GlobalVariable as GlobalVariable
 * This class delegates to com.katalan.core.compat.GlobalVariable.
 * 
 * Properties are accessed dynamically via Groovy's property access (getProperty).
 */
public class GlobalVariable {

    /**
     * Called by Groovy when a static property is read and no matching
     * field/getter exists on the class. Enables: GlobalVariable.anyName
     */
    public static Object propertyMissing(String name) {
        return com.katalan.core.compat.GlobalVariable.get(name);
    }

    /**
     * Called by Groovy when a static property is written and no matching
     * field/setter exists on the class. Enables: GlobalVariable.anyName = value
     */
    public static Object propertyMissing(String name, Object value) {
        com.katalan.core.compat.GlobalVariable.set(name, value);
        return value;
    }

    /** Explicit getter for Java callers and Groovy's getProperty fallback */
    public static Object get(String name) {
        return com.katalan.core.compat.GlobalVariable.get(name);
    }

    /** Explicit setter */
    public static void set(String name, Object value) {
        com.katalan.core.compat.GlobalVariable.set(name, value);
    }

    /** Get all global variables */
    public static Map<String, Object> getAllVariables() {
        return com.katalan.core.compat.GlobalVariable.getAllVariables();
    }
}
