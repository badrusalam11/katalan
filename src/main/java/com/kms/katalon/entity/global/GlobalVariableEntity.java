package com.kms.katalon.entity.global;

import java.io.Serializable;
import java.util.Objects;

import com.kms.katalon.entity.global.enums.VariableValueType;

/**
 * Katalon Studio's com.kms.katalon.entity.global.GlobalVariableEntity (shipped in
 * com.kms.katalon.entity_*.jar). Katalon puts that jar on every script's classpath, so test
 * cases import it even when they never use it (e.g. nds-automation-testing-new's
 * "00 - Local Approval") - without it the whole test case fails with "unable to resolve class".
 */
public class GlobalVariableEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String ZERO_NUMBER = "0";

    public static final String EMPTY_STRING = "''";

    private String name;

    private String initValue;

    private String protectedValue;

    private String description;

    private boolean isProtected;

    private VariableValueType valueType;

    public GlobalVariableEntity() {
        this("", EMPTY_STRING);
    }

    public GlobalVariableEntity(String name, String initValue) {
        setName(name);
        setInitValue(initValue);
        setProtectedValue(initValue);
        setDescription("");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInitValue() {
        return initValue;
    }

    public String getProtectedValue() {
        return protectedValue;
    }

    public String getValue() {
        return isProtected ? getProtectedValue() : initValue;
    }

    public void setInitValue(String initValue) {
        this.initValue = initValue;
    }

    public void setProtectedValue(String protectedValue) {
        this.protectedValue = protectedValue;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isProtected() {
        return isProtected;
    }

    public void setProtected(boolean isProtected) {
        this.isProtected = isProtected;
    }

    public VariableValueType getValueType() {
        return valueType;
    }

    public void setValueType(VariableValueType valueType) {
        this.valueType = valueType;
    }

    public boolean isDefaultValue() {
        return EMPTY_STRING.equals(getValue()) || ZERO_NUMBER.equals(getValue());
    }

    public String getDefaultValue() {
        return valueType == VariableValueType.NUMBER ? ZERO_NUMBER : EMPTY_STRING;
    }

    @Override
    public GlobalVariableEntity clone() {
        GlobalVariableEntity copy = new GlobalVariableEntity(getName(), getInitValue());
        copy.setDescription(getDescription());
        copy.setProtected(isProtected());
        copy.setProtectedValue(getProtectedValue());
        copy.setValueType(getValueType());
        return copy;
    }

    public boolean equals(GlobalVariableEntity other) {
        return other != null && Objects.equals(getName(), other.getName()) && equalsWithoutName(other);
    }

    public boolean equalsWithoutName(GlobalVariableEntity other) {
        return other != null && Objects.equals(getValue(), other.getValue())
                && Objects.equals(getDescription(), other.getDescription());
    }
}
