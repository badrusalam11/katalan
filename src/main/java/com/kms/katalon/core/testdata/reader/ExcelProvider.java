package com.kms.katalon.core.testdata.reader;

import java.io.IOException;

import com.kms.katalon.core.testdata.ExcelData;

public interface ExcelProvider {
    ExcelData getExcelData(String sourceUrl, boolean hasHeaders) throws IOException;
}
