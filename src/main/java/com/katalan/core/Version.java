package com.katalan.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Version information loaded from version.properties (Maven filtered)
 */
public class Version {
    private static final String VERSION;
    private static final String NAME;
    private static final String DESCRIPTION;
    
    static {
        Properties props = new Properties();
        try (InputStream in = Version.class.getResourceAsStream("/version.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            // Ignore, use defaults
        }
        
        VERSION = props.getProperty("version", "unknown");
        NAME = props.getProperty("name", "katalan Runner");
        DESCRIPTION = props.getProperty("description", "Unofficial Katalon Test Runner");
    }
    
    public static String getVersion() {
        return VERSION;
    }
    
    public static String getName() {
        return NAME;
    }
    
    public static String getDescription() {
        return DESCRIPTION;
    }
    
    public static String getFullVersion() {
        return NAME + " " + VERSION;
    }
}
