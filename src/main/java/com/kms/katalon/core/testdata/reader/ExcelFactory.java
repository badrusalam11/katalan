package com.kms.katalon.core.testdata.reader;

import com.kms.katalon.core.testdata.TestData;

/**
 * Katalon compatibility class for ExcelFactory
 */
public class ExcelFactory {
    
    public static TestData getExcelDataWithDefaultSheet(String filePath, String sheetName, boolean hasHeader) {
        return new TestData(filePath);
    }
    
    public static TestData getExcelData(String filePath, String sheetName) {
        return new TestData(filePath);
    }
    
    public static TestData getExcelData(String filePath, String sheetName, String range, boolean hasHeader) {
        return new TestData(filePath);
    }
}
