package com.kms.katalon.core.testdata;

import java.io.IOException;
import java.util.List;

/**
 * Katalon's TestData contract (com.kms.katalon.core.testdata.TestData). Row and column indexes
 * are 1-based, and getValue always returns a String - scripts rely on that (e.g. `?.trim()`).
 */
public interface TestData {
    int BASE_INDEX = 1;

    String getValue(String columnName, int rowIndex) throws IOException;

    String getValue(int columnIndex, int rowIndex) throws IOException;

    Object getObjectValue(String columnName, int rowIndex) throws IOException;

    Object getObjectValue(int columnIndex, int rowIndex) throws IOException;

    TestDataType getType();

    String getSourceUrl();

    String[] getColumnNames() throws IOException;

    int getRowNumbers() throws IOException;

    int getColumnNumbers() throws IOException;

    boolean hasHeaders();

    void activeHeaders(boolean active) throws IOException;

    TestDataInfo getDataInfo();

    List<List<Object>> getAllData() throws IOException;

    String getProperty(String key);

    void setProperty(String key, String value);
}
