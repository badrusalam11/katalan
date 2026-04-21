package com.kms.katalon.core.testdata;

import java.util.ArrayList;
import java.util.List;

/**
 * Katalon compatibility class for TestData
 */
public class TestData {
    private String dataId;
    private List<List<Object>> data = new ArrayList<>();
    private List<String> columnNames = new ArrayList<>();
    
    public TestData() {}
    
    public TestData(String dataId) {
        this.dataId = dataId;
    }
    
    public String getDataId() {
        return dataId;
    }
    
    public int getRowNumbers() {
        return data.size();
    }
    
    public int getColumnNumbers() {
        return columnNames.size();
    }
    
    public Object getValue(int columnIndex, int rowIndex) {
        if (rowIndex - 1 < data.size() && columnIndex - 1 < data.get(rowIndex - 1).size()) {
            return data.get(rowIndex - 1).get(columnIndex - 1);
        }
        return null;
    }
    
    public Object getValue(String columnName, int rowIndex) {
        int columnIndex = columnNames.indexOf(columnName) + 1;
        if (columnIndex > 0) {
            return getValue(columnIndex, rowIndex);
        }
        return null;
    }
    
    public List<String> getColumnNames() {
        return columnNames;
    }
    
    public List<List<Object>> getAllData() {
        return data;
    }
}
