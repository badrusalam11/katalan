package com.katalan.reporting;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * PDF Report Generator for Katalan
 * Reads execution0.log and generates a PDF report
 */
public class PDFReportGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(PDFReportGenerator.class);
    
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter
            .ofPattern("dd-MM-yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    
    private final Path reportDir;
    private final Path executionLogPath;
    
    // Statistics
    private int totalTests = 0;
    private int passedTests = 0;
    private int failedTests = 0;
    private int errorTests = 0;
    private long totalDuration = 0;
    
    // Test case details
    private java.util.List<TestCaseInfo> testCases = new ArrayList<>();
    
    public PDFReportGenerator(Path reportDir) {
        this.reportDir = reportDir;
        this.executionLogPath = reportDir.resolve("execution0.log");
    }
    
    /**
     * Generate PDF report from execution0.log
     */
    public Path generateReport() throws Exception {
        if (!executionLogPath.toFile().exists()) {
            throw new IllegalStateException("execution0.log not found at: " + executionLogPath);
        }
        
        logger.info("📄 Generating PDF report from: {}", executionLogPath);
        
        // Parse execution0.log
        parseExecutionLog();
        
    // Determine timestamp from reportDir name (last segment)
    String dirName = reportDir.getFileName().toString();
    String pdfName = dirName + ".pdf";
    Path pdfPath = reportDir.resolve(pdfName);
        createPDF(pdfPath);
        
        logger.info("✅ PDF report generated: {}", pdfPath);
        return pdfPath;
    }
    
    /**
     * Parse execution0.log XML to extract test information
     */
    private void parseExecutionLog() throws Exception {
        File logFile = executionLogPath.toFile();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        
        // Disable validation - execution0.log references logger.dtd which doesn't exist
        factory.setValidating(false);
        factory.setNamespaceAware(false);
        
        // Security: Disable external entity resolution to prevent XXE attacks
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        
        DocumentBuilder builder = factory.newDocumentBuilder();
        
        // Provide empty EntityResolver so parser doesn't try to load logger.dtd
        builder.setEntityResolver((publicId, systemId) -> {
            // Return empty input stream for any DTD/entity request
            return new org.xml.sax.InputSource(new java.io.StringReader(""));
        });
        
        org.w3c.dom.Document doc = builder.parse(logFile);
        
        NodeList records = doc.getElementsByTagName("record");
        
        TestCaseInfo currentTest = null;
        long testStartTime = 0;
        
        for (int i = 0; i < records.getLength(); i++) {
            Element record = (Element) records.item(i);
            
            String level = getElementText(record, "level");
            String message = getElementText(record, "message");
            String millis = getElementText(record, "millis");
            
            // Parse test case start
            if (message.startsWith("Start Test Case :")) {
                String testCaseId = message.substring("Start Test Case : ".length()).trim();
                currentTest = new TestCaseInfo();
                currentTest.testCaseId = testCaseId;
                currentTest.status = "PASSED"; // Default to PASSED
                testStartTime = millis != null ? Long.parseLong(millis) : 0;
                totalTests++;
            }
            // Parse test case end
            else if (message.startsWith("End Test Case :") && currentTest != null) {
                long endTime = millis != null ? Long.parseLong(millis) : 0;
                currentTest.duration = endTime - testStartTime;
                totalDuration += currentTest.duration;
                
                // Count by status
                switch (currentTest.status) {
                    case "PASSED":
                        passedTests++;
                        break;
                    case "FAILED":
                        failedTests++;
                        break;
                    case "ERROR":
                        errorTests++;
                        break;
                }
                
                testCases.add(currentTest);
                currentTest = null;
            }
            // Parse failures/errors
            else if (currentTest != null && ("FAILED".equals(level) || "ERROR".equals(level))) {
                currentTest.status = level;
                if (currentTest.errorMessage == null) {
                    currentTest.errorMessage = message;
                } else {
                    currentTest.errorMessage += "\n" + message;
                }
            }
            // Parse step messages
            else if (currentTest != null && ("INFO".equals(level) || "PASSED".equals(level))) {
                currentTest.steps.add(message);
            }
        }
    }
    
    /**
     * Create PDF document
     */
    private void createPDF(Path pdfPath) throws Exception {
        PdfWriter writer = new PdfWriter(pdfPath.toFile());
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);
        
        // Add title
        Paragraph title = new Paragraph("Test Execution Report")
                .setFontSize(24)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER);
        document.add(title);
        
        // Add timestamp
        Paragraph timestamp = new Paragraph("Generated: " + DISPLAY_FORMATTER.format(Instant.now()))
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20);
        document.add(timestamp);
        
        // Add summary statistics
        addSummarySection(document);
        
        // Add test case details
        addTestCaseDetails(document);

    // Add screenshots section (collect images under reportDir)
    addScreenshotsSection(document);
        
        document.close();
    }
    
    /**
     * Add summary section with statistics
     */
    private void addSummarySection(Document document) {
        Paragraph sectionTitle = new Paragraph("Summary")
                .setFontSize(18)
                .setBold()
                .setMarginTop(10)
                .setMarginBottom(10);
        document.add(sectionTitle);
        
        // Create summary table
        Table table = new Table(UnitValue.createPercentArray(new float[]{3, 1}))
                .useAllAvailableWidth()
                .setMarginBottom(20);
        
        // Header
        table.addHeaderCell(createHeaderCell("Metric"));
        table.addHeaderCell(createHeaderCell("Value"));
        
        // Data rows
        table.addCell(createCell("Total Tests"));
        table.addCell(createCell(String.valueOf(totalTests)));
        
        table.addCell(createCell("Passed"));
        table.addCell(createPassedCell(String.valueOf(passedTests)));
        
        table.addCell(createCell("Failed"));
        table.addCell(createFailedCell(String.valueOf(failedTests)));
        
        table.addCell(createCell("Error"));
        table.addCell(createErrorCell(String.valueOf(errorTests)));
        
        table.addCell(createCell("Total Duration"));
        table.addCell(createCell(formatDuration(totalDuration)));
        
        double passRate = totalTests > 0 ? (passedTests * 100.0 / totalTests) : 0;
        table.addCell(createCell("Pass Rate"));
        table.addCell(createCell(String.format("%.2f%%", passRate)));
        
        document.add(table);
    }
    
    /**
     * Add test case details section
     */
    private void addTestCaseDetails(Document document) {
        if (testCases.isEmpty()) {
            return;
        }
        
        Paragraph sectionTitle = new Paragraph("Test Case Details")
                .setFontSize(18)
                .setBold()
                .setMarginTop(20)
                .setMarginBottom(10);
        document.add(sectionTitle);
        
        for (TestCaseInfo testCase : testCases) {
            addTestCaseSection(document, testCase);
        }
    }

    /**
     * Attach screenshots found under the report directory. Searches for images
     * directly under reportDir and inside a "screenshots" folder.
     */
    private void addScreenshotsSection(Document document) {
        java.util.List<java.nio.file.Path> images = new ArrayList<>();
        try {
            java.nio.file.Path screenshotsDir = reportDir.resolve("screenshots");
            // Collect png/jpg files from reportDir
            java.nio.file.Files.list(reportDir)
                .filter(p -> {
                    String s = p.getFileName().toString().toLowerCase();
                    return s.endsWith(".png") || s.endsWith(".jpg") || s.endsWith(".jpeg");
                })
                .forEach(images::add);

            if (java.nio.file.Files.exists(screenshotsDir)) {
                java.nio.file.Files.list(screenshotsDir)
                    .filter(p -> {
                        String s = p.getFileName().toString().toLowerCase();
                        return s.endsWith(".png") || s.endsWith(".jpg") || s.endsWith(".jpeg");
                    })
                    .forEach(images::add);
            }
        } catch (Exception e) {
            // ignore
        }

        if (images.isEmpty()) return;

        Paragraph sectionTitle = new Paragraph("Screenshots")
                .setFontSize(16)
                .setBold()
                .setMarginTop(20)
                .setMarginBottom(10);
        document.add(sectionTitle);

        for (java.nio.file.Path img : images) {
            try {
                com.itextpdf.io.image.ImageData data = com.itextpdf.io.image.ImageDataFactory.create(img.toAbsolutePath().toString());
                com.itextpdf.layout.element.Image pdfImg = new com.itextpdf.layout.element.Image(data);
                pdfImg.setAutoScale(true);
                document.add(new Paragraph(img.getFileName().toString()).setFontSize(9).setItalic());
                document.add(pdfImg.setMarginBottom(10));
            } catch (Exception e) {
                // ignore individual image failures
            }
        }
    }
    
    /**
     * Add individual test case section
     */
    private void addTestCaseSection(Document document, TestCaseInfo testCase) {
        // Test case header
        Paragraph testCaseName = new Paragraph(testCase.testCaseId)
                .setFontSize(14)
                .setBold()
                .setMarginTop(15)
                .setMarginBottom(5);
        
        // Add status badge
        if ("PASSED".equals(testCase.status)) {
            testCaseName.setFontColor(new DeviceRgb(0, 128, 0));
        } else if ("FAILED".equals(testCase.status)) {
            testCaseName.setFontColor(new DeviceRgb(220, 53, 69));
        } else if ("ERROR".equals(testCase.status)) {
            testCaseName.setFontColor(new DeviceRgb(255, 193, 7));
        }
        
        document.add(testCaseName);
        
        // Test case details
        Table detailTable = new Table(UnitValue.createPercentArray(new float[]{2, 4}))
                .useAllAvailableWidth()
                .setMarginBottom(10);
        
        detailTable.addCell(createCell("Status"));
        detailTable.addCell(createStatusCell(testCase.status));
        
        detailTable.addCell(createCell("Duration"));
        detailTable.addCell(createCell(formatDuration(testCase.duration)));
        
        document.add(detailTable);
        
        // Add error message if exists
        if (testCase.errorMessage != null && !testCase.errorMessage.isEmpty()) {
            Paragraph errorLabel = new Paragraph("Error Message:")
                    .setFontSize(11)
                    .setBold()
                    .setMarginTop(5);
            document.add(errorLabel);
            
            Paragraph errorMsg = new Paragraph(testCase.errorMessage)
                    .setFontSize(10)
                    .setFontColor(ColorConstants.RED)
                    .setMarginLeft(10)
                    .setMarginBottom(10);
            document.add(errorMsg);
        }
        
        // Add steps (collapsed, only show first few)
        if (!testCase.steps.isEmpty() && testCase.steps.size() <= 10) {
            Paragraph stepsLabel = new Paragraph("Steps:")
                    .setFontSize(11)
                    .setBold()
                    .setMarginTop(5);
            document.add(stepsLabel);
            
            com.itextpdf.layout.element.List stepList = new com.itextpdf.layout.element.List()
                    .setMarginLeft(15)
                    .setMarginBottom(10)
                    .setFontSize(9);
            
            for (int i = 0; i < Math.min(testCase.steps.size(), 10); i++) {
                stepList.add(new ListItem(testCase.steps.get(i)));
            }
            
            if (testCase.steps.size() > 10) {
                stepList.add(new ListItem(String.format("... and %d more steps", testCase.steps.size() - 10)));
            }
            
            document.add(stepList);
        }
    }
    
    // Helper methods for cell creation
    private Cell createHeaderCell(String text) {
        return new Cell()
                .add(new Paragraph(text).setBold())
                .setBackgroundColor(new DeviceRgb(52, 58, 64))
                .setFontColor(ColorConstants.WHITE)
                .setPadding(8)
                .setTextAlignment(TextAlignment.LEFT);
    }
    
    private Cell createCell(String text) {
        return new Cell()
                .add(new Paragraph(text))
                .setPadding(8)
                .setBorder(Border.NO_BORDER);
    }
    
    private Cell createPassedCell(String text) {
        Cell cell = createCell(text);
        cell.setFontColor(new DeviceRgb(0, 128, 0));
        cell.setBold();
        return cell;
    }
    
    private Cell createFailedCell(String text) {
        Cell cell = createCell(text);
        cell.setFontColor(new DeviceRgb(220, 53, 69));
        cell.setBold();
        return cell;
    }
    
    private Cell createErrorCell(String text) {
        Cell cell = createCell(text);
        cell.setFontColor(new DeviceRgb(255, 193, 7));
        cell.setBold();
        return cell;
    }
    
    private Cell createStatusCell(String status) {
        Cell cell = createCell(status);
        if ("PASSED".equals(status)) {
            cell.setFontColor(new DeviceRgb(0, 128, 0));
        } else if ("FAILED".equals(status)) {
            cell.setFontColor(new DeviceRgb(220, 53, 69));
        } else if ("ERROR".equals(status)) {
            cell.setFontColor(new DeviceRgb(255, 193, 7));
        }
        cell.setBold();
        return cell;
    }
    
    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        
        if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    private String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            Node node = nodes.item(0);
            return node.getTextContent();
        }
        return null;
    }
    
    /**
     * Test case information holder
     */
    private static class TestCaseInfo {
        String testCaseId;
        String status;
        long duration;
        String errorMessage;
        java.util.List<String> steps = new ArrayList<>();
    }
}
