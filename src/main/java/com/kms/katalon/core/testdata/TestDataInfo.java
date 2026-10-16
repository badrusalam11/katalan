package com.kms.katalon.core.testdata;

public class TestDataInfo {
    private final String key;

    private final String info;

    public TestDataInfo(String key, String info) {
        this.key = key;
        this.info = info;
    }

    public String getInfo() {
        return info;
    }

    public String getKey() {
        return key;
    }
}
