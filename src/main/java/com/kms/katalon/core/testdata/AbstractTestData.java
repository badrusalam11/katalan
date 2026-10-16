package com.kms.katalon.core.testdata;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;

/**
 * Port of Katalon's AbstractTestData: validates 1-based indexes and turns cell values into
 * strings with ObjectUtils.toString (null becomes "").
 */
public abstract class AbstractTestData implements TestData {
    protected String sourceUrl;

    protected boolean hasHeaders;

    protected Map<String, String> properties;

    protected AbstractTestData(String sourceUrl, boolean hasHeaders) {
        this.sourceUrl = sourceUrl;
        this.hasHeaders = hasHeaders;
        this.properties = new HashMap<String, String>();
    }

    @Override
    public final String getSourceUrl() {
        return sourceUrl;
    }

    @Override
    public final boolean hasHeaders() {
        return hasHeaders;
    }

    protected void verifyRowIndex(int rowIndex) throws IOException {
        int rowNumber = getRowNumbers();
        if (rowIndex > rowNumber || rowIndex < BASE_INDEX) {
            throw new IllegalArgumentException(MessageFormat.format(
                    "Row index ''{0}'' for test data ''{1}'' is invalid. Row index must be between 1 and {2}",
                    rowIndex, getSourceUrl(), rowNumber));
        }
    }

    protected void verifyColumnIndex(int columnIndex) throws IOException {
        int columnNumber = getColumnNumbers();
        if (columnIndex > columnNumber || columnIndex < BASE_INDEX) {
            throw new IllegalArgumentException(MessageFormat.format(
                    "Column index ''{0}'' for test data ''{1}'' is invalid. Column index must be between 1 and {2}",
                    columnIndex, getSourceUrl(), columnNumber));
        }
    }

    protected void verifyColumnName(String columnName) throws IOException {
        String[] columnNames = getColumnNames();
        for (String name : columnNames) {
            if (name != null && name.equals(columnName)) {
                return;
            }
        }
        throw new IllegalArgumentException(MessageFormat.format(
                "Column name ''{0}'' for test data ''{1}'' is invalid. Available column names: {2}", columnName,
                getSourceUrl(), getAvailableColumnNames(columnNames)));
    }

    @Override
    public final String getValue(int columnIndex, int rowIndex) throws IOException {
        return ObjectUtils.toString(getObjectValue(columnIndex, rowIndex));
    }

    @Override
    public final String getValue(String columnName, int rowIndex) throws IOException {
        return ObjectUtils.toString(getObjectValue(columnName, rowIndex));
    }

    @Override
    public final Object getObjectValue(int columnIndex, int rowIndex) throws IOException {
        verifyColumnIndex(columnIndex);
        verifyRowIndex(rowIndex);
        return internallyGetValue(columnIndex - BASE_INDEX, rowIndex - BASE_INDEX);
    }

    @Override
    public final Object getObjectValue(String columnName, int rowIndex) throws IOException {
        verifyRowIndex(rowIndex);
        verifyColumnName(columnName);
        return internallyGetValue(columnName, rowIndex - BASE_INDEX);
    }

    protected abstract Object internallyGetValue(int columnIndex, int rowIndex) throws IOException;

    protected abstract Object internallyGetValue(String columnName, int rowIndex) throws IOException;

    protected int getHeaderRowIdx() {
        return hasHeaders ? 1 : 0;
    }

    @Override
    public void activeHeaders(boolean active) throws IOException {
        hasHeaders = active;
    }

    private String getAvailableColumnNames(String[] columnNames) {
        if (!hasHeaders || ArrayUtils.isEmpty(columnNames)) {
            return "{}";
        }
        List<String> validColumNames = new ArrayList<String>();
        for (String columnName : columnNames) {
            if (StringUtils.isNotEmpty(columnName)) {
                validColumNames.add(columnName);
            }
        }
        return ArrayUtils.toString(validColumNames.toArray(new String[0]));
    }

    @Override
    public TestDataInfo getDataInfo() {
        return null;
    }

    @Override
    public String getProperty(String key) {
        return properties.get(key);
    }

    @Override
    public void setProperty(String key, String value) {
        properties.put(key, value);
    }
}
