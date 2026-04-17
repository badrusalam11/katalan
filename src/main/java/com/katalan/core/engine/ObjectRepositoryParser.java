package com.katalan.core.engine;

import com.katalan.core.model.TestObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Parser for Katalon Object Repository files (.rs files)
 */
public class ObjectRepositoryParser {
    
    private static final Logger logger = LoggerFactory.getLogger(ObjectRepositoryParser.class);
    
    /**
     * Parse a Katalon test object file (.rs)
     */
    public static TestObject parseTestObject(Path path) throws IOException {
        logger.debug("Parsing test object: {}", path);
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(path.toFile());
            doc.getDocumentElement().normalize();
            
            TestObject testObject = new TestObject();
            Element root = doc.getDocumentElement();
            
            // Get name
            String name = getElementText(root, "name");
            testObject.setName(name);
            testObject.setObjectId(name);
            
            // Parse selection method
            String selectionMethod = getElementText(root, "selectorMethod");
            if (selectionMethod != null) {
                testObject.setSelectorMethod(parseSelectionMethod(selectionMethod));
            }
            
            // Parse selectors from selectorCollection (newer format)
            NodeList selectorCollection = root.getElementsByTagName("selectorCollection");
            if (selectorCollection.getLength() > 0) {
                Node selectorCollectionNode = selectorCollection.item(0);
                NodeList entries = ((Element) selectorCollectionNode).getElementsByTagName("entry");
                
                Map<String, String> selectors = new HashMap<>();
                for (int i = 0; i < entries.getLength(); i++) {
                    Element entry = (Element) entries.item(i);
                    String key = getElementText(entry, "key");
                    String value = getElementText(entry, "value");
                    if (key != null && value != null) {
                        selectors.put(key, value);
                    }
                }
                
                // Set primary selector based on selection method
                if (selectionMethod != null && selectors.containsKey(selectionMethod)) {
                    testObject.setSelectorValue(selectors.get(selectionMethod));
                } else if (!selectors.isEmpty()) {
                    // Default to first available
                    String firstKey = selectors.keySet().iterator().next();
                    testObject.setSelectorMethod(parseSelectionMethod(firstKey));
                    testObject.setSelectorValue(selectors.get(firstKey));
                }
            }
            
            // Parse web element properties (Katalon standard format)
            NodeList webElementProps = root.getElementsByTagName("webElementProperties");
            String bestSelector = null;
            TestObject.SelectorMethod bestMethod = null;
            int priority = Integer.MAX_VALUE;
            
            for (int i = 0; i < webElementProps.getLength(); i++) {
                Element prop = (Element) webElementProps.item(i);
                String propName = getElementText(prop, "name");
                String propValue = getElementText(prop, "value");
                String isSelected = getElementText(prop, "isSelected");
                
                if (propName != null && propValue != null) {
                    testObject.addProperty(propName, propValue);
                    
                    // Only use selected properties for locators
                    if ("true".equalsIgnoreCase(isSelected)) {
                        // Priority: id > name > xpath > css > others
                        int currentPriority = getPropertyPriority(propName);
                        if (currentPriority < priority) {
                            priority = currentPriority;
                            bestMethod = parseSelectionMethod(propName);
                            bestSelector = propValue;
                        }
                    }
                }
            }
            
            // Set the best selector if found and not already set
            if (testObject.getSelectorValue() == null && bestSelector != null) {
                testObject.setSelectorMethod(bestMethod);
                testObject.setSelectorValue(bestSelector);
            }
            
            // Fallback: generate xpath from id/name if no selector found
            if (testObject.getSelectorValue() == null) {
                String id = testObject.getProperties().get("id");
                String nameAttr = testObject.getProperties().get("name");
                String tag = testObject.getProperties().get("tag");
                
                if (id != null && !id.isEmpty()) {
                    testObject.setSelectorMethod(TestObject.SelectorMethod.ID);
                    testObject.setSelectorValue(id);
                } else if (nameAttr != null && !nameAttr.isEmpty()) {
                    testObject.setSelectorMethod(TestObject.SelectorMethod.NAME);
                    testObject.setSelectorValue(nameAttr);
                } else if (tag != null && !tag.isEmpty()) {
                    testObject.setSelectorMethod(TestObject.SelectorMethod.TAG_NAME);
                    testObject.setSelectorValue(tag);
                }
            }
            
            logger.debug("Parsed test object: {} -> {} = {}", name, 
                    testObject.getSelectorMethod(), testObject.getSelectorValue());
            
            return testObject;
            
        } catch (Exception e) {
            logger.error("Failed to parse test object: {}", path, e);
            throw new IOException("Failed to parse test object: " + path, e);
        }
    }
    
    /**
     * Get priority for property type (lower is better)
     */
    private static int getPropertyPriority(String propName) {
        switch (propName.toLowerCase()) {
            case "id": return 1;
            case "name": return 2;
            case "xpath": return 3;
            case "css": return 4;
            case "class": return 5;
            case "text": return 6;
            case "tag": return 7;
            default: return 10;
        }
    }
    
    /**
     * Load all test objects from Object Repository folder
     */
    public static Map<String, TestObject> loadObjectRepository(Path projectPath) throws IOException {
        Map<String, TestObject> repository = new HashMap<>();
        Path objectRepoPath = projectPath.resolve("Object Repository");
        
        if (!Files.exists(objectRepoPath)) {
            logger.warn("Object Repository folder not found: {}", objectRepoPath);
            return repository;
        }
        
        Files.walk(objectRepoPath)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".rs"))
                .forEach(p -> {
                    try {
                        TestObject obj = parseTestObject(p);
                        // Create path relative to Object Repository
                        String relativePath = objectRepoPath.relativize(p).toString();
                        // Remove .rs extension
                        relativePath = relativePath.substring(0, relativePath.length() - 3);
                        // Normalize path separators
                        relativePath = relativePath.replace("\\", "/");
                        
                        repository.put(relativePath, obj);
                        repository.put("Object Repository/" + relativePath, obj);
                        logger.debug("Loaded test object: {}", relativePath);
                    } catch (Exception e) {
                        logger.warn("Failed to load test object: {}", p, e);
                    }
                });
        
        logger.info("Loaded {} test objects from Object Repository", repository.size() / 2);
        return repository;
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
    
    /**
     * Parse selection method string to enum
     */
    private static TestObject.SelectorMethod parseSelectionMethod(String method) {
        if (method == null) {
            return TestObject.SelectorMethod.XPATH;
        }
        
        switch (method.toUpperCase()) {
            case "XPATH":
                return TestObject.SelectorMethod.XPATH;
            case "CSS":
            case "CSS_SELECTOR":
                return TestObject.SelectorMethod.CSS;
            case "ID":
                return TestObject.SelectorMethod.ID;
            case "NAME":
                return TestObject.SelectorMethod.NAME;
            case "CLASS_NAME":
                return TestObject.SelectorMethod.CLASS_NAME;
            case "LINK_TEXT":
                return TestObject.SelectorMethod.LINK_TEXT;
            case "PARTIAL_LINK_TEXT":
                return TestObject.SelectorMethod.PARTIAL_LINK_TEXT;
            case "TAG_NAME":
                return TestObject.SelectorMethod.TAG_NAME;
            default:
                return TestObject.SelectorMethod.XPATH;
        }
    }
}
