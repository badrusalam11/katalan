package com.kms.katalon.core.testdata;

import java.io.File;
import java.text.MessageFormat;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.kms.katalon.core.configuration.RunConfiguration;
import com.kms.katalon.core.testdata.reader.ExcelFactory;

/**
 * Katalon's findTestData: reads "Data Files/<id>.dat" and opens the data source it points to.
 * Excel data files are supported; other drivers (CSV, internal, database) are not yet.
 */
public class TestDataFactory {

    private static final String TEST_DATA_ID_PREFIX = "Data Files/";

    public static String getTestDataId(String testDataRelativeId) {
        if (testDataRelativeId == null || testDataRelativeId.startsWith(TEST_DATA_ID_PREFIX)) {
            return testDataRelativeId;
        }
        return TEST_DATA_ID_PREFIX + testDataRelativeId;
    }

    public static TestData findTestData(String testDataRelativeId) {
        if (testDataRelativeId == null) {
            throw new IllegalArgumentException("Test data id is null");
        }
        String testDataId = getTestDataId(testDataRelativeId);
        try {
            return internallyFindTestData(new File(RunConfiguration.getProjectDir()).getAbsolutePath(), testDataId);
        } catch (Exception e) {
            throw new IllegalArgumentException(MessageFormat.format("Cannot find test data ''{0}'' because of ''{1}''",
                    testDataId, e.getMessage()), e);
        }
    }

    private static TestData internallyFindTestData(String projectDir, String testDataId) throws Exception {
        File dataFile = new File(projectDir, testDataId + ".dat");
        if (!dataFile.exists()) {
            throw new IllegalArgumentException(MessageFormat.format("Test data ''{0}'' does not exist", testDataId));
        }
        Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(dataFile).getDocumentElement();
        String driver = text(root, "driver");
        if (TestDataType.fromValue(driver) != TestDataType.EXCEL_FILE) {
            throw new UnsupportedOperationException("katalan does not support '" + driver + "' test data yet");
        }
        String sourceUrl = text(root, "dataSourceUrl");
        String sheetName = text(root, "sheetName");
        if (sourceUrl == null || sheetName == null) {
            throw new IllegalArgumentException("Missing element " + (sourceUrl == null ? "dataSourceUrl" : "sheetName"));
        }
        String containsHeaders = text(root, "containsHeaders");
        boolean hasHeaders = containsHeaders == null || Boolean.parseBoolean(containsHeaders);
        if (Boolean.parseBoolean(text(root, "isInternalPath"))) {
            sourceUrl = new File(projectDir, sourceUrl).getAbsolutePath();
        }
        return ExcelFactory.getExcelDataWithDefaultSheet(sourceUrl, sheetName, hasHeaders);
    }

    private static String text(Element root, String tag) {
        NodeList nodes = root.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }
}
