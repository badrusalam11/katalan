package com.kms.katalon.core.testdata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.kms.katalon.core.testdata.reader.SpreadSheet;

public abstract class ExcelData extends AbstractTestData implements SpreadSheet {

    public ExcelData(String sourceUrl, boolean hasHeaders) throws IOException {
        super(sourceUrl, hasHeaders);
    }

    public abstract int getMaxColumn(int rowIndex) throws IOException;

    @Override
    public final TestDataType getType() {
        return TestDataType.EXCEL_FILE;
    }

    @Override
    public List<List<Object>> getAllData() throws IOException {
        List<List<Object>> data = new ArrayList<List<Object>>();
        int colNum = getColumnNumbers();
        int rowNum = getRowNumbers();
        for (int y = BASE_INDEX; y <= rowNum; y++) {
            List<Object> row = new ArrayList<Object>();
            for (int x = BASE_INDEX; x <= colNum; x++) {
                row.add(getObjectValue(x, y));
            }
            data.add(row);
        }
        return data;
    }

    public abstract String getSheetName();
}
