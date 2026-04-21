package com.kms.katalon.core.testdata;

/**
 * Katalon compatibility class for TestDataFactory
 */
public class TestDataFactory {
    
    public static TestData findTestData(String dataId) {
        return new TestData(dataId);
    }
}
