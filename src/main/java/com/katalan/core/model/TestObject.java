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
    // Cached Selenium By locator to avoid repeated allocations
    private transient org.openqa.selenium.By cachedBy;
    
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
        // Return cached By if already computed and selector hasn't changed
        if (cachedBy != null) return cachedBy;

        switch (selectorMethod) {
            case XPATH:
                cachedBy = By.xpath(selectorValue);
                break;
            case CSS:
                cachedBy = By.cssSelector(selectorValue);
                break;
            case ID:
                cachedBy = By.id(selectorValue);
                break;
            case NAME:
                cachedBy = By.name(selectorValue);
                break;
            case CLASS_NAME:
                cachedBy = By.className(selectorValue);
                break;
            case LINK_TEXT:
                cachedBy = By.linkText(selectorValue);
                break;
            case PARTIAL_LINK_TEXT:
                cachedBy = By.partialLinkText(selectorValue);
                break;
            case TAG_NAME:
                cachedBy = By.tagName(selectorValue);
                break;
            default:
                throw new IllegalArgumentException("Unknown selector method: " + selectorMethod);
        }
        return cachedBy;
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
        this.cachedBy = null;
    }
    
    public String getSelectorValue() {
        return selectorValue;
    }
    
    public void setSelectorValue(String selectorValue) {
        this.selectorValue = selectorValue;
        this.cachedBy = null;
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
