package com.kms.katalon.entity.global.enums;

/**
 * Katalon Studio's com.kms.katalon.entity.global.enums.VariableValueType (shipped in
 * com.kms.katalon.entity_*.jar), so scripts importing it compile under katalan.
 */
public enum VariableValueType {
    STRING("String"),
    NUMBER("Number");

    private final String name;

    VariableValueType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
