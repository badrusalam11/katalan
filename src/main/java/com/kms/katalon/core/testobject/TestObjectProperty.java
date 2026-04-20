package com.kms.katalon.core.testobject;

/**
 * Katalon API Compatibility - TestObjectProperty
 */
public class TestObjectProperty {
    
    private String name;
    private ConditionType condition;
    private String value;
    private boolean isActive;
    
    public TestObjectProperty() {
        this.isActive = true;
    }
    
    public TestObjectProperty(String name, ConditionType condition, String value) {
        this();
        this.name = name;
        this.condition = condition;
        this.value = value;
    }
    
    public TestObjectProperty(String name, ConditionType condition, String value, boolean isActive) {
        this(name, condition, value);
        this.isActive = isActive;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public ConditionType getCondition() {
        return condition;
    }
    
    public void setCondition(ConditionType condition) {
        this.condition = condition;
    }
    
    public String getValue() {
        return value;
    }
    
    public void setValue(String value) {
        this.value = value;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        isActive = active;
    }
    
    @Override
    public String toString() {
        return "TestObjectProperty{" +
                "name='" + name + '\'' +
                ", condition=" + condition +
                ", value='" + value + '\'' +
                ", isActive=" + isActive +
                '}';
    }
}
