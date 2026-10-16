package com.kms.katalon.core.testdata.reader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import com.kms.katalon.core.testdata.ExcelData;

/** Port of Katalon's (sic) WorbookProvider: reads .xls/.xlsx through POI's WorkbookFactory. */
public class WorbookProvider implements ExcelProvider {
    @Override
    public ExcelData getExcelData(String sourceUrl, boolean hasHeaders) throws IOException {
        File excelFile = new File(sourceUrl);
        if (!excelFile.exists()) {
            throw new FileNotFoundException(excelFile.toString());
        }
        try (FileInputStream fis = new FileInputStream(excelFile)) {
            return new SheetPOI(sourceUrl, hasHeaders, WorkbookFactory.create(fis));
        } catch (InvalidFormatException e) {
            return null;
        }
    }
}
