package com.kms.katalon.core.testobject;

import org.openqa.selenium.By;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Katalon API Compatibility - TestObject
 * 
 * This class provides compatibility with Katalon's TestObject API.
 * It wraps or provides equivalent functionality to Katalan's TestObject.
 */
public class TestObject {
    
    private String objectId;
    private String cachedWebElementId;
    private List<TestObjectProperty> properties;
    private TestObject parentObject;
    private String parentObjectShadowRootProperty;
    private SelectorMethod selectorMethod;
    private Map<SelectorMethod, String> selectorCollection;
    private boolean useRelativeImagePath;
    private String imagePath;
    
    public TestObject() {
        this.properties = new ArrayList<>();
        this.selectorCollection = new HashMap<>();
    }
    
    public TestObject(String objectId) {
        this();
        this.objectId = objectId;
    }
    
    // Convert to Katalan TestObject
    public com.katalan.core.model.TestObject toKatalanTestObject() {
        com.katalan.core.model.TestObject katalanObj = new com.katalan.core.model.TestObject(this.objectId);
        
        // Copy selector if available
        if (selectorMethod != null && selectorCollection.containsKey(selectorMethod)) {
            String selectorValue = selectorCollection.get(selectorMethod);
            switch (selectorMethod) {
                case XPATH:
                    katalanObj.setSelectorMethod(com.katalan.core.model.TestObject.SelectorMethod.XPATH);
                    break;
                case CSS:
                    katalanObj.setSelectorMethod(com.katalan.core.model.TestObject.SelectorMethod.CSS);
                    break;
                case BASIC:
                default:
                    // Try to find xpath or css from properties
                    for (TestObjectProperty prop : properties) {
                        if ("xpath".equalsIgnoreCase(prop.getName())) {
                            katalanObj.setSelectorMethod(com.katalan.core.model.TestObject.SelectorMethod.XPATH);
                            katalanObj.setSelectorValue(prop.getValue());
                            return katalanObj;
                        }
                    }
                    break;
            }
            katalanObj.setSelectorValue(selectorValue);
        } else {
            // Fallback: try to build selector from properties
            for (TestObjectProperty prop : properties) {
                if ("xpath".equalsIgnoreCase(prop.getName()) && prop.isActive()) {
                    katalanObj.setSelectorMethod(com.katalan.core.model.TestObject.SelectorMethod.XPATH);
                    katalanObj.setSelectorValue(prop.getValue());
                    break;
                } else if ("css".equalsIgnoreCase(prop.getName()) && prop.isActive()) {
                    katalanObj.setSelectorMethod(com.katalan.core.model.TestObject.SelectorMethod.CSS);
                    katalanObj.setSelectorValue(prop.getValue());
                    break;
                } else if ("id".equalsIgnoreCase(prop.getName()) && prop.isActive()) {
                    katalanObj.setSelectorMethod(com.katalan.core.model.TestObject.SelectorMethod.ID);
                    katalanObj.setSelectorValue(prop.getValue());
                    break;
                }
            }
        }
        
        return katalanObj;
    }
    
    // Static factory to create from Katalan TestObject
    public static TestObject fromKatalanTestObject(com.katalan.core.model.TestObject katalanObj) {
        if (katalanObj == null) return null;
        
        TestObject to = new TestObject(katalanObj.getObjectId());
        
        if (katalanObj.getSelectorMethod() != null && katalanObj.getSelectorValue() != null) {
            switch (katalanObj.getSelectorMethod()) {
                case XPATH:
                    to.setSelectorMethod(SelectorMethod.XPATH);
                    to.setSelectorValue(SelectorMethod.XPATH, katalanObj.getSelectorValue());
                    break;
                case CSS:
                    to.setSelectorMethod(SelectorMethod.CSS);
                    to.setSelectorValue(SelectorMethod.CSS, katalanObj.getSelectorValue());
                    break;
                case ID:
                    to.addProperty(new TestObjectProperty("id", ConditionType.EQUALS, katalanObj.getSelectorValue()));
                    break;
                case NAME:
                    to.addProperty(new TestObjectProperty("name", ConditionType.EQUALS, katalanObj.getSelectorValue()));
                    break;
                default:
                    break;
            }
        }
        
        return to;
    }
    
    // Katalon API methods
    public String getObjectId() {
        return objectId;
    }
    
    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }
    
    public List<TestObjectProperty> getProperties() {
        return properties;
    }
    
    public void setProperties(List<TestObjectProperty> properties) {
        this.properties = properties;
    }
    
    public TestObject addProperty(TestObjectProperty property) {
        this.properties.add(property);
        return this;
    }
    
    public TestObject addProperty(String name, ConditionType condition, String value) {
        this.properties.add(new TestObjectProperty(name, condition, value));
        return this;
    }
    
    public TestObject addProperty(String name, ConditionType condition, String value, boolean isActive) {
        TestObjectProperty prop = new TestObjectProperty(name, condition, value);
        prop.setActive(isActive);
        this.properties.add(prop);
        return this;
    }
    
    public TestObjectProperty findProperty(String name) {
        for (TestObjectProperty prop : properties) {
            if (name.equals(prop.getName())) {
                return prop;
            }
        }
        return null;
    }
    
    public String findPropertyValue(String name) {
        TestObjectProperty prop = findProperty(name);
        return prop != null ? prop.getValue() : null;
    }
    
    public String findPropertyValue(String name, boolean activeOnly) {
        for (TestObjectProperty prop : properties) {
            if (name.equals(prop.getName())) {
                if (!activeOnly || prop.isActive()) {
                    return prop.getValue();
                }
            }
        }
        return null;
    }
    
    public TestObject getParentObject() {
        return parentObject;
    }
    
    public void setParentObject(TestObject parentObject) {
        this.parentObject = parentObject;
    }
    
    public String getParentObjectShadowRootProperty() {
        return parentObjectShadowRootProperty;
    }
    
    public void setParentObjectShadowRootProperty(String parentObjectShadowRootProperty) {
        this.parentObjectShadowRootProperty = parentObjectShadowRootProperty;
    }
    
    public SelectorMethod getSelectorMethod() {
        return selectorMethod;
    }
    
    public void setSelectorMethod(SelectorMethod selectorMethod) {
        this.selectorMethod = selectorMethod;
    }
    
    public Map<SelectorMethod, String> getSelectorCollection() {
        return selectorCollection;
    }
    
    public void setSelectorCollection(Map<SelectorMethod, String> selectorCollection) {
        this.selectorCollection = selectorCollection;
    }
    
    public void setSelectorValue(SelectorMethod method, String value) {
        this.selectorCollection.put(method, value);
    }
    
    public String getSelectorValue(SelectorMethod method) {
        return this.selectorCollection.get(method);
    }
    
    public String getCachedWebElementId() {
        return cachedWebElementId;
    }
    
    public void setCachedWebElementId(String cachedWebElementId) {
        this.cachedWebElementId = cachedWebElementId;
    }
    
    public boolean isUseRelativeImagePath() {
        return useRelativeImagePath;
    }
    
    public void setUseRelativeImagePath(boolean useRelativeImagePath) {
        this.useRelativeImagePath = useRelativeImagePath;
    }
    
    public String getImagePath() {
        return imagePath;
    }
    
    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }
    
    // Convert to Selenium By locator
    public By toSeleniumBy() {
        // First try selector collection
        if (selectorMethod != null && selectorCollection.containsKey(selectorMethod)) {
            String value = selectorCollection.get(selectorMethod);
            switch (selectorMethod) {
                case XPATH:
                    return By.xpath(value);
                case CSS:
                    return By.cssSelector(value);
                default:
                    break;
            }
        }
        
        // Fallback to properties
        for (TestObjectProperty prop : properties) {
            if (!prop.isActive()) continue;
            
            String name = prop.getName().toLowerCase();
            String value = prop.getValue();
            
            switch (name) {
                case "xpath":
                    return By.xpath(value);
                case "css":
                    return By.cssSelector(value);
                case "id":
                    return By.id(value);
                case "name":
                    return By.name(value);
                case "class":
                case "classname":
                    return By.className(value);
                case "linktext":
                    return By.linkText(value);
                case "partiallinktext":
                    return By.partialLinkText(value);
                case "tagname":
                case "tag":
                    return By.tagName(value);
            }
        }
        
        throw new IllegalStateException("Cannot create Selenium By locator from TestObject: " + objectId);
    }
    
    @Override
    public String toString() {
        return "TestObject{" +
                "objectId='" + objectId + '\'' +
                ", properties=" + properties.size() +
                ", selectorMethod=" + selectorMethod +
                '}';
    }
}
