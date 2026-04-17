package com.katalan.core.model;

import org.openqa.selenium.By;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a Katalon Test Object - similar to Object Repository entries
 */
public class TestObject {
    
    private String objectId;
    private String name;
    private Map<String, String> properties;
    private SelectorMethod selectorMethod;
    private String selectorValue;
    
    public enum SelectorMethod {
        XPATH,
        CSS,
        ID,
        NAME,
        CLASS_NAME,
        LINK_TEXT,
        PARTIAL_LINK_TEXT,
        TAG_NAME
    }
    
    public TestObject() {
        this.properties = new HashMap<>();
    }
    
    public TestObject(String name) {
        this();
        this.name = name;
        this.objectId = name;
    }
    
    public TestObject(String name, SelectorMethod method, String value) {
        this(name);
        this.selectorMethod = method;
        this.selectorValue = value;
    }
    
    /**
     * Create a TestObject using XPath
     */
    public static TestObject xpath(String name, String xpath) {
        return new TestObject(name, SelectorMethod.XPATH, xpath);
    }
    
    /**
     * Create a TestObject using CSS selector
     */
    public static TestObject css(String name, String css) {
        return new TestObject(name, SelectorMethod.CSS, css);
    }
    
    /**
     * Create a TestObject using ID
     */
    public static TestObject id(String name, String id) {
        return new TestObject(name, SelectorMethod.ID, id);
    }
    
    /**
     * Convert to Selenium By locator
     */
    public By toSeleniumBy() {
        if (selectorMethod == null || selectorValue == null) {
            throw new IllegalStateException("Selector method and value must be set");
        }
        
        switch (selectorMethod) {
            case XPATH:
                return By.xpath(selectorValue);
            case CSS:
                return By.cssSelector(selectorValue);
            case ID:
                return By.id(selectorValue);
            case NAME:
                return By.name(selectorValue);
            case CLASS_NAME:
                return By.className(selectorValue);
            case LINK_TEXT:
                return By.linkText(selectorValue);
            case PARTIAL_LINK_TEXT:
                return By.partialLinkText(selectorValue);
            case TAG_NAME:
                return By.tagName(selectorValue);
            default:
                throw new IllegalArgumentException("Unknown selector method: " + selectorMethod);
        }
    }
    
    // Getters and Setters
    public String getObjectId() {
        return objectId;
    }
    
    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Map<String, String> getProperties() {
        return properties;
    }
    
    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }
    
    public void addProperty(String key, String value) {
        this.properties.put(key, value);
    }
    
    public SelectorMethod getSelectorMethod() {
        return selectorMethod;
    }
    
    public void setSelectorMethod(SelectorMethod selectorMethod) {
        this.selectorMethod = selectorMethod;
    }
    
    public String getSelectorValue() {
        return selectorValue;
    }
    
    public void setSelectorValue(String selectorValue) {
        this.selectorValue = selectorValue;
    }
    
    @Override
    public String toString() {
        return "TestObject{" +
                "name='" + name + '\'' +
                ", selectorMethod=" + selectorMethod +
                ", selectorValue='" + selectorValue + '\'' +
                '}';
    }
}
