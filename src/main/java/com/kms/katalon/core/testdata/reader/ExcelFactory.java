package com.kms.katalon.core.testdata.reader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.MessageFormat;

import com.kms.katalon.core.testdata.ExcelData;

/**
 * Port of Katalon's ExcelFactory. Katalon also tries an HTML-table provider for .xls files that
 * are really HTML exports; katalan only reads real workbooks.
 */
public class ExcelFactory {

    private static final ExcelProvider[] providers = new ExcelProvider[] { new WorbookProvider() };

    public static ExcelData getExcelDataWithDefaultSheet(String fullFilePath, String sheetName, boolean hasHeaders)
            throws IOException {
        ExcelData excelData = getExcelData(fullFilePath, hasHeaders);
        excelData.changeSheet(sheetName);
        return excelData;
    }

    public static ExcelData getExcelData(String fullFilePath, boolean hasHeaders) throws IOException {
        File inputFile = new File(fullFilePath);
        if (!inputFile.exists()) {
            throw new FileNotFoundException(fullFilePath);
        }

        ExcelData excel = null;
        for (ExcelProvider provider : providers) {
            try {
                excel = provider.getExcelData(fullFilePath, hasHeaders);
                if (excel != null) {
                    break;
                }
            } catch (Exception ex) {
                // continue
            }
        }

        if (excel == null) {
            throw new IllegalArgumentException(MessageFormat.format("File ''{0}'' is unsupported", fullFilePath));
        }
        return excel;
    }
}
