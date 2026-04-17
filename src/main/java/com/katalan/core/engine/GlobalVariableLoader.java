package com.katalan.core.engine;

import com.katalan.core.compat.GlobalVariable;
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
import java.util.HashMap;
import java.util.Map;

/**
 * Loads GlobalVariables from Katalon Profiles (.glbl files)
 */
public class GlobalVariableLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalVariableLoader.class);
    
    /**
     * Load global variables from Katalon project
     * @param projectPath Path to Katalon project root
     * @param profileName Profile name (default is "default")
     * @return Map of global variables
     */
    public static Map<String, Object> loadGlobalVariables(Path projectPath, String profileName) throws IOException {
        Map<String, Object> variables = new HashMap<>();
        
        // Try Profiles folder first (standard Katalon location)
        Path profilePath = projectPath.resolve("Profiles").resolve(profileName + ".glbl");
        
        if (!Files.exists(profilePath)) {
            // Try root level GlobalVariables.glbl
            profilePath = projectPath.resolve("GlobalVariables.glbl");
        }
        
        if (!Files.exists(profilePath)) {
            logger.warn("No global variables file found for profile: {}", profileName);
            return variables;
        }
        
        return loadFromFile(profilePath);
    }
    
    /**
     * Load global variables from a specific profile file
     * Alias for loadFromFile for API compatibility
     */
    public static Map<String, Object> loadFromProfile(Path profilePath) throws IOException {
        return loadFromFile(profilePath);
    }
    
    /**
     * Load global variables from a specific file
     */
    public static Map<String, Object> loadFromFile(Path filePath) throws IOException {
        Map<String, Object> variables = new HashMap<>();
        
        logger.info("Loading global variables from: {}", filePath);
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(filePath.toFile());
            doc.getDocumentElement().normalize();
            
            // Parse GlobalVariableEntity elements
            NodeList entities = doc.getElementsByTagName("GlobalVariableEntity");
            
            for (int i = 0; i < entities.getLength(); i++) {
                Element entity = (Element) entities.item(i);
                String name = getElementText(entity, "name");
                String initValue = getElementText(entity, "initValue");
                
                if (name != null && initValue != null) {
                    Object value = parseValue(initValue);
                    variables.put(name, value);
                    GlobalVariable.set(name, value);
                    logger.debug("Loaded global variable: {} = {}", name, value);
                }
            }
            
            logger.info("Loaded {} global variables", variables.size());
            
        } catch (Exception e) {
            logger.error("Failed to load global variables from: {}", filePath, e);
            throw new IOException("Failed to load global variables", e);
        }
        
        return variables;
    }
    
    /**
     * Parse Groovy-style value string to Java object
     */
    private static Object parseValue(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        
        value = value.trim();
        
        // String literal (single or double quotes)
        if ((value.startsWith("'") && value.endsWith("'")) ||
            (value.startsWith("\"") && value.endsWith("\""))) {
            return value.substring(1, value.length() - 1);
        }
        
        // Boolean
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        
        // Null
        if ("null".equalsIgnoreCase(value)) {
            return null;
        }
        
        // Number
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            // Not a number, return as string
        }
        
        // Default: return as-is
        return value;
    }
    
    /**
     * Get text content of a child element
     */
    private static String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return null;
    }
}
