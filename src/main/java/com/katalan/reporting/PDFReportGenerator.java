package com.katalan.reporting;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.katalan.core.model.ExecutionResult;
import com.katalan.core.model.TestCase;
import com.katalan.core.model.TestCaseResult;
import com.katalan.core.model.TestSuiteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * BRI-style automation test report PDF generator.
 */
public class PDFReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(PDFReportGenerator.class);

    private static final DateTimeFormatter DATE_FMT    = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_ID_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("id", "ID")).withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DAY_ID_FMT  = DateTimeFormatter.ofPattern("EEEE", new Locale("id", "ID")).withZone(ZoneId.systemDefault());

    private static final String DASH = "-";

    private static final DeviceRgb COLOR_BRI_BLUE   = new DeviceRgb(0, 47, 108);
    private static final DeviceRgb COLOR_PASSED     = new DeviceRgb(40, 167, 69);
    private static final DeviceRgb COLOR_FAILED     = new DeviceRgb(220, 53, 69);
    private static final DeviceRgb COLOR_ERROR      = new DeviceRgb(253, 126, 20);
    private static final DeviceRgb COLOR_SKIPPED    = new DeviceRgb(108, 117, 125);
    private static final DeviceRgb COLOR_INCOMPLETE = new DeviceRgb(255, 193, 7);
    private static final DeviceRgb COLOR_BG_LIGHT   = new DeviceRgb(243, 245, 249);
    private static final DeviceRgb COLOR_TBL_HEADER = new DeviceRgb(54, 65, 87);
    private static final DeviceRgb COLOR_BORDER     = new DeviceRgb(200, 207, 217);
    private static final DeviceRgb COLOR_TEXT       = new DeviceRgb(33, 37, 41);
    private static final DeviceRgb COLOR_TEXT_MUTED = new DeviceRgb(108, 117, 125);

    private final Path reportDir;
    private final ExecutionResult result;

    /** Cache: test case name (as written in {@code execution0.log}) -> ordered list of attachment filenames. */
    private java.util.Map<String, java.util.List<String>> attachmentsByTestCase;

    public PDFReportGenerator(Path reportDir, ExecutionResult result) {
        this.reportDir = reportDir;
        this.result = result;
    }

    public PDFReportGenerator(Path reportDir) {
        this(reportDir, null);
    }

    public Path generateReport() throws Exception {
        Files.createDirectories(reportDir);
        String dirName = reportDir.getFileName() != null ? reportDir.getFileName().toString() : "report";
        Path pdfPath = reportDir.resolve(dirName + ".pdf");

        ExecutionResult exec = this.result != null ? this.result : buildResultFromLog();

        logger.info("Generating PDF report: {}", pdfPath);

        try (PdfWriter writer = new PdfWriter(pdfPath.toFile());
             PdfDocument pdf = new PdfDocument(writer)) {

            pdf.setDefaultPageSize(PageSize.A4);
            pdf.addEventHandler(PdfDocumentEvent.END_PAGE, new FooterHandler());

            try (Document doc = new Document(pdf, PageSize.A4)) {
                doc.setMargins(40, 40, 50, 40);
                doc.setFontColor(COLOR_TEXT);
                doc.setFontSize(10);

                // Parse all .tc files first to populate description and tags
                for (TestSuiteResult suite : exec.getSuiteResults()) {
                    for (TestCaseResult tc : suite.getTestCaseResults()) {
                        parseTcFile(tc);
                    }
                }

                addTitle(doc);
                addNarrative(doc, exec);
                addInfoGrid(doc, exec);
                addTestCaseTable(doc, exec);
                addExecutionEnvironment(doc, exec);
                addTestCaseDetailPages(doc, exec);
            }
        }

        logger.info("PDF report generated: {}", pdfPath);
        return pdfPath;
    }

    private void addTitle(Document doc) {
        doc.add(new Paragraph("AUTOMATION TEST REPORT")
                .setFontSize(18)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(COLOR_TEXT)
                .setMarginTop(4)
                .setMarginBottom(10)
                .setUnderline());
    }

    private void addNarrative(Document doc, ExecutionResult exec) {
        Instant t = exec.getStartTime() != null ? exec.getStartTime() : Instant.now();
        String day = DAY_ID_FMT.format(t);
        if (!day.isEmpty()) day = Character.toUpperCase(day.charAt(0)) + day.substring(1);
        String formatted = DATE_ID_FMT.format(t);

        Paragraph p = new Paragraph()
                .setFontSize(10)
                .setMarginBottom(10)
                .setTextAlignment(TextAlignment.JUSTIFIED)
                .add("Pada hari ini, ")
                .add(boldText(day))
                .add(", tanggal ")
                .add(boldText(formatted))
                .add(" telah dilakukan kegiatan Regression Test menggunakan Automation "
                        + "dengan detail sebagai berikut :");
        doc.add(p);
    }

    private static Text boldText(String text) {
        return new Text(text).setBold();
    }

    private void addInfoGrid(Document doc, ExecutionResult exec) {
        String executor = resolveExecutor();

        String suiteId = exec.getSuiteResults().isEmpty()
                ? (exec.getName() != null ? exec.getName() : "Test Suite")
                : exec.getSuiteResults().get(0).getSuiteName();
        
        // Prefer test suite path from CLI system property (e.g., "Test Suites/Regresion/...")
        String sysTestSuite = System.getProperty("katalan.testSuite");
        if (sysTestSuite != null && !sysTestSuite.isEmpty()) {
            suiteId = sysTestSuite;
        }

        String environment = System.getProperty("katalan.profile", "default");

        String start   = exec.getStartTime() != null ? DATE_FMT.format(exec.getStartTime()) : DASH;
        String end     = exec.getEndTime()   != null ? DATE_FMT.format(exec.getEndTime())   : DASH;
        String elapsed = formatElapsed(exec.getDuration());

        Table grid = new Table(UnitValue.createPercentArray(new float[]{1.2f, 2.3f, 1.2f, 2.3f}))
                .useAllAvailableWidth()
                .setMarginBottom(14);

        grid.addCell(labelCell("Executor"));
        grid.addCell(valueCell(executor, COLOR_TEXT));
        grid.addCell(labelCell("Failed"));
        grid.addCell(valueCell(String.valueOf(exec.getFailedTests()), COLOR_FAILED).setBold());

        grid.addCell(labelCell("ID"));
        grid.addCell(valueCell(suiteId, COLOR_TEXT));
        grid.addCell(labelCell("Incomplete"));
        grid.addCell(valueCell("0", COLOR_INCOMPLETE).setBold());

        grid.addCell(labelCell("Environment"));
        grid.addCell(valueCell(environment, COLOR_TEXT));
        grid.addCell(labelCell("Start"));
        grid.addCell(valueCell(start, COLOR_TEXT));

        grid.addCell(labelCell("Total"));
        grid.addCell(valueCell(String.valueOf(exec.getTotalTests()), COLOR_TEXT).setBold());
        grid.addCell(labelCell("End"));
        grid.addCell(valueCell(end, COLOR_TEXT));

        grid.addCell(labelCell("Passed"));
        grid.addCell(valueCell(String.valueOf(exec.getPassedTests()), COLOR_PASSED).setBold());
        grid.addCell(labelCell("Elapsed"));
        grid.addCell(valueCell(elapsed, COLOR_TEXT));

        grid.addCell(labelCell("Error"));
        grid.addCell(valueCell(String.valueOf(exec.getErrorTests()), COLOR_ERROR).setBold());
        grid.addCell(labelCell("Pass Rate"));
        grid.addCell(valueCell(String.format("%.1f%%", exec.getPassRate()),
                exec.getPassRate() >= 100.0 ? COLOR_PASSED : COLOR_FAILED).setBold());

        grid.addCell(labelCell("Skipped"));
        grid.addCell(valueCell(String.valueOf(exec.getSkippedTests()), COLOR_SKIPPED));
        grid.addCell(labelCell("Status"));
        boolean ok = exec.getFailedTests() == 0 && exec.getErrorTests() == 0;
        grid.addCell(valueCell(ok ? "PASSED" : "FAILED", ok ? COLOR_PASSED : COLOR_FAILED).setBold());

        doc.add(grid);
    }

    private Cell labelCell(String text) {
        return new Cell()
                .add(new Paragraph(text).setBold().setFontSize(9).setFontColor(COLOR_TEXT))
                .setBackgroundColor(COLOR_BG_LIGHT)
                .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                .setPadding(5);
    }

    private Cell valueCell(String text, DeviceRgb color) {
        return new Cell()
                .add(new Paragraph(text == null || text.isEmpty() ? DASH : text)
                        .setFontSize(9).setFontColor(color))
                .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                .setPadding(5);
    }

    private void addTestCaseTable(Document doc, ExecutionResult exec) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{0.6f, 4.5f, 3.5f, 1.4f}))
                .useAllAvailableWidth()
                .setMarginBottom(14);

        table.addHeaderCell(thCell("#"));
        table.addHeaderCell(thCell("ID Testcase"));
        table.addHeaderCell(thCell("Description"));
        table.addHeaderCell(thCell("Status"));

        int idx = 1;
        for (TestSuiteResult s : exec.getSuiteResults()) {
            for (TestCaseResult tc : s.getTestCaseResults()) {
                String dur = tc.getDurationFormatted() != null
                        ? tc.getDurationFormatted()
                        : formatDuration(tc.getDuration());

                table.addCell(tdCell(String.valueOf(idx++)).setTextAlignment(TextAlignment.CENTER));

                // Show test case ID (Test Cases/BRICAMS/Transfer Fund/...) with duration
                Cell idCell = new Cell()
                        .setPadding(6)
                        .setBorder(new SolidBorder(COLOR_BORDER, 0.5f));
                idCell.add(new Paragraph()
                        .setFontSize(9)
                        .add(new Text(buildTestCaseId(tc)))
                        .add(new Text("  (" + dur + ")").setFontSize(8).setFontColor(COLOR_TEXT_MUTED)));
                table.addCell(idCell);

                // Show description from .tc file or dash
                String desc = tc.getDescription() != null && !tc.getDescription().isEmpty()
                        ? tc.getDescription()
                        : DASH;
                table.addCell(tdCell(desc));

                table.addCell(statusCell(tc.getStatus()));
            }
        }

        doc.add(table);
    }

    private Cell thCell(String text) {
        return new Cell()
                .add(new Paragraph(text).setBold().setFontSize(9).setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(COLOR_TBL_HEADER)
                .setBorder(Border.NO_BORDER)
                .setPadding(7)
                .setTextAlignment(TextAlignment.CENTER);
    }

    private Cell tdCell(String text) {
        return new Cell()
                .add(new Paragraph(text == null || text.isEmpty() ? DASH : text).setFontSize(9))
                .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                .setPadding(6);
    }

    private Cell statusCell(TestCase.TestCaseStatus status) {
        DeviceRgb c = statusColor(status);
        String label = status != null ? status.name() : DASH;
        return new Cell()
                .add(new Paragraph(label).setBold().setFontSize(9).setFontColor(c))
                .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                .setPadding(6)
                .setTextAlignment(TextAlignment.CENTER);
    }

    private void addExecutionEnvironment(Document doc, ExecutionResult exec) {
        String hostName;
        try { hostName = InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { hostName = "localhost"; }

        String os = (System.getProperty("os.name", "") + " " + System.getProperty("os.version", "")
                + " " + System.getProperty("os.arch", "")).trim();
        String javaVersion = System.getProperty("java.version", "");
        String groovyVersion;
        try { groovyVersion = groovy.lang.GroovySystem.getVersion(); }
        catch (Throwable t) { groovyVersion = "—"; }

        String browser = "Chrome";  // Default browser
        if (exec.getBrowserName() != null && !exec.getBrowserName().isEmpty()) {
            browser = exec.getBrowserName();
            if (exec.getBrowserVersion() != null) browser += " " + exec.getBrowserVersion();
        } else {
            // Fallback to system property set by CLI
            String sysBrowser = System.getProperty("katalan.browser");
            if (sysBrowser != null && !sysBrowser.isEmpty()) {
                // Capitalize first letter
                browser = sysBrowser.substring(0, 1).toUpperCase() + sysBrowser.substring(1).toLowerCase();
            }
        }

        String repo = System.getProperty("katalan.repository",
                System.getenv().getOrDefault("KATALAN_REPO",
                        System.getProperty("user.dir", "")));

        Table outer = new Table(UnitValue.createPercentArray(new float[]{1, 3}))
                .useAllAvailableWidth()
                .setBackgroundColor(COLOR_BG_LIGHT)
                .setMarginTop(6)
                .setMarginBottom(10);

        Cell iconCell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setPadding(10)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setTextAlignment(TextAlignment.CENTER);
        
        // Try to load Chrome icon from assets
        try {
            Path chromeIcon = Paths.get("/Users/computer/Documents/repository/katalan/assets/image/chrome_icon.png");
            if (Files.exists(chromeIcon)) {
                ImageData imgData = ImageDataFactory.create(chromeIcon.toString());
                Image img = new Image(imgData)
                        .setWidth(48)
                        .setHeight(48)
                        .setHorizontalAlignment(HorizontalAlignment.CENTER);
                Paragraph imgParagraph = new Paragraph().add(img).setTextAlignment(TextAlignment.CENTER);
                iconCell.add(imgParagraph);
            } else {
                // Fallback to emoji if image not found
                iconCell.add(new Paragraph("\uD83C\uDF10")
                        .setFontSize(48)
                        .setTextAlignment(TextAlignment.CENTER));
            }
        } catch (Exception e) {
            // Fallback to emoji on error
            logger.warn("Failed to load Chrome icon, using emoji: {}", e.getMessage());
            iconCell.add(new Paragraph("\uD83C\uDF10")
                    .setFontSize(48)
                    .setTextAlignment(TextAlignment.CENTER));
        }
        
        iconCell.add(new Paragraph(browser.split(" ")[0])
                .setFontSize(9).setBold().setFontColor(COLOR_TEXT_MUTED)
                .setTextAlignment(TextAlignment.CENTER));
        outer.addCell(iconCell);

        Cell rightCell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setPadding(10);

        rightCell.add(new Paragraph("Execution Environment")
                .setFontSize(16).setBold().setFontColor(COLOR_TEXT).setMarginBottom(8));

        Table kv = new Table(UnitValue.createPercentArray(new float[]{1, 2}))
                .useAllAvailableWidth();
        envRow(kv, "Katalon version", "10.3.2.0");
        envRow(kv, "Host name",       hostName);
        envRow(kv, "Local OS",        os);
        envRow(kv, "Browser",         browser);
        envRow(kv, "Java version",    javaVersion);
        envRow(kv, "Groovy version",  groovyVersion);
        envRow(kv, "Repository",      abbrev(repo, 60));
        rightCell.add(kv);

        outer.addCell(rightCell);
        doc.add(outer);
    }

    private void envRow(Table t, String k, String v) {
        t.addCell(new Cell()
                .add(new Paragraph(k).setBold().setFontSize(9).setFontColor(COLOR_TEXT))
                .setBorder(Border.NO_BORDER)
                .setPaddingTop(2).setPaddingBottom(2));
        t.addCell(new Cell()
                .add(new Paragraph(v == null || v.isEmpty() ? DASH : v).setFontSize(9).setFontColor(COLOR_TEXT_MUTED))
                .setBorder(Border.NO_BORDER)
                .setPaddingTop(2).setPaddingBottom(2));
    }

    // =====================================================================
    //  Detail pages (one per test case, BRI-style)
    // =====================================================================

    private void addTestCaseDetailPages(Document doc, ExecutionResult exec) {
        int idx = 0;
        // Flatten all test cases with their suite for cross-suite navigation
        java.util.List<TestCaseResult> all = new java.util.ArrayList<>();
        for (TestSuiteResult s : exec.getSuiteResults()) {
            all.addAll(s.getTestCaseResults());
        }
        if (all.isEmpty()) return;

        for (TestCaseResult tc : all) {
            doc.add(new AreaBreak());
            String prev = idx > 0 ? all.get(idx - 1).getTestCaseName() : null;
            String next = idx < all.size() - 1 ? all.get(idx + 1).getTestCaseName() : null;
            addTestCaseDetailPage(doc, tc, prev, next);
            idx++;
        }
    }

    private void addTestCaseDetailPage(Document doc, TestCaseResult tc, String prev, String next) {
        // Parse .tc file to get description and tag
        parseTcFile(tc);
        
        // 1) Blue header bar with test case name (cleaned)
        Table head = new Table(UnitValue.createPercentArray(new float[]{1}))
                .useAllAvailableWidth();
        Cell headCell = new Cell()
                .add(new Paragraph(nvl(cleanTestCaseName(tc.getTestCaseName())))
                        .setBold().setFontSize(13).setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(COLOR_BRI_BLUE)
                .setPadding(10)
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.CENTER);
        head.addCell(headCell);
        doc.add(head);

        // 2) Details grid (matches BRI layout: ID, Description, Tag, Start/End, Status/Elapsed)
        Table grid = new Table(UnitValue.createPercentArray(new float[]{1.4f, 3, 1.2f, 2.4f}))
                .useAllAvailableWidth();

        String id = buildTestCaseId(tc);
        String description = buildDescription(tc);
        String tag = extractTag(tc);
        String start = tc.getStartTime() != null ? DATE_FMT.format(tc.getStartTime()) : DASH;
        String end   = tc.getEndTime()   != null ? DATE_FMT.format(tc.getEndTime())   : DASH;
        String elapsed = formatElapsed(tc.getDuration());

        // ID row (full-width value)
        grid.addCell(detailLabel("ID"));
        grid.addCell(detailValueWide(id, 3));

        // Description row (full-width value, multi-line)
        grid.addCell(detailLabel("Description"));
        grid.addCell(detailValueWide(description, 3));

        // Tag row (full-width)
        grid.addCell(detailLabel("Tag"));
        grid.addCell(detailValueWide(tag, 3));

        // Start | End
        grid.addCell(detailLabel("Start"));
        grid.addCell(detailValue(start, false));
        grid.addCell(detailLabel("End"));
        grid.addCell(detailValue(end, false));

        // Status | Elapsed
        grid.addCell(detailLabel("Status"));
        grid.addCell(detailStatusValue(tc.getStatus()));
        grid.addCell(detailLabel("Elapsed"));
        grid.addCell(detailValue(elapsed, false));

        doc.add(grid);

        // 3) Sebelumnya / current / Selanjutnya navigation
        Table nav = new Table(UnitValue.createPercentArray(new float[]{1.4f, 4.2f, 1.4f}))
                .useAllAvailableWidth()
                .setMarginBottom(12);

        Cell prevCell = new Cell()
                .add(new Paragraph(prev != null ? "« " + truncateName(cleanTestCaseName(prev)) : "Sebelumnya")
                        .setFontSize(8).setItalic())
                .setBackgroundColor(COLOR_BG_LIGHT)
                .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                .setPadding(8)
                .setTextAlignment(TextAlignment.LEFT)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setFontColor(prev != null ? COLOR_TEXT : COLOR_TEXT_MUTED);

        Cell curCell = new Cell()
                .add(new Paragraph(nvl(cleanTestCaseName(tc.getTestCaseName()))).setBold().setFontColor(COLOR_BRI_BLUE).setFontSize(10))
                .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                .setPadding(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);

        Cell nextCell = new Cell()
                .add(new Paragraph(next != null ? truncateName(cleanTestCaseName(next)) + " »" : "Selanjutnya")
                        .setFontSize(8).setItalic())
                .setBackgroundColor(COLOR_BG_LIGHT)
                .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                .setPadding(8)
                .setTextAlignment(TextAlignment.RIGHT)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setFontColor(next != null ? COLOR_TEXT : COLOR_TEXT_MUTED);

        nav.addCell(prevCell);
        nav.addCell(curCell);
        nav.addCell(nextCell);
        doc.add(nav);

        // 4) Failure block (if any)
        if (tc.getStatus() == TestCase.TestCaseStatus.FAILED
                || tc.getStatus() == TestCase.TestCaseStatus.ERROR) {
            addFailureSection(doc, tc);
        }

        // 5) Screenshots (with Information sidebar — BRI style)
        addScreenshotsBriStyle(doc, tc);
    }

    private void addFailureSection(Document doc, TestCaseResult tc) {
        DeviceRgb sc = statusColor(tc.getStatus());

        if (tc.getErrorMessage() != null && !tc.getErrorMessage().isEmpty()) {
            Table errTbl = new Table(UnitValue.createPercentArray(new float[]{1}))
                    .useAllAvailableWidth().setMarginBottom(6);
            Cell errCell = new Cell()
                    .setBackgroundColor(new DeviceRgb(255, 244, 244))
                    .setBorder(Border.NO_BORDER)
                    .setBorderLeft(new SolidBorder(sc, 3f))
                    .setPadding(8);
            errCell.add(new Paragraph("Error Message")
                    .setBold().setFontSize(9).setFontColor(sc).setMarginBottom(2));
            errCell.add(new Paragraph(truncate(tc.getErrorMessage(), 1500))
                    .setFontSize(9).setFontColor(COLOR_TEXT));
            errTbl.addCell(errCell);
            doc.add(errTbl);
        }

        if (tc.getStackTrace() != null && !tc.getStackTrace().isEmpty()) {
            Table stTbl = new Table(UnitValue.createPercentArray(new float[]{1}))
                    .useAllAvailableWidth().setMarginBottom(8);
            Cell stCell = new Cell()
                    .setBackgroundColor(new DeviceRgb(245, 245, 245))
                    .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                    .setPadding(6);
            stCell.add(new Paragraph("Stack Trace")
                    .setBold().setFontSize(8).setFontColor(COLOR_TEXT_MUTED).setMarginBottom(2));
            stCell.add(new Paragraph(truncate(tc.getStackTrace(), 4000))
                    .setFontSize(7).setFontColor(COLOR_TEXT));
            stTbl.addCell(stCell);
            doc.add(stTbl);
        }
    }

    /**
     * BRI-style screenshot block: "Screenshot" label, an Information sidebar
     * (file name, size, dimension, created time) on the left and the image
     * on the right, with the absolute path printed below.
     */
    private void addScreenshotsBriStyle(Document doc, TestCaseResult tc) {
        java.util.List<Path> images = collectScreenshots(tc);
        if (images.isEmpty()) return;

        int count = 0;
        for (Path img : images) {
            if (count >= 8) {
                doc.add(new Paragraph("... " + (images.size() - count) + " more screenshot(s) omitted")
                        .setFontSize(8).setItalic().setFontColor(COLOR_TEXT_MUTED));
                break;
            }
            try {
                addOneScreenshot(doc, img);
                count++;
            } catch (Exception e) {
                logger.debug("Failed to embed screenshot {}: {}", img, e.getMessage());
            }
        }
    }

    private void addOneScreenshot(Document doc, Path img) throws Exception {
        // Section label "Screenshot" with a thin underline like the BRI report
        doc.add(new Paragraph("Screenshot")
                .setBold().setFontSize(10).setFontColor(COLOR_TEXT)
                .setMarginTop(8).setMarginBottom(4)
                .setBorderBottom(new SolidBorder(COLOR_BRI_BLUE, 1f))
                .setPaddingBottom(2));

        ImageData data = ImageDataFactory.create(img.toAbsolutePath().toString());
        long sizeBytes;
        try { sizeBytes = Files.size(img); } catch (Exception e) { sizeBytes = -1; }
        String created;
        try {
            java.nio.file.attribute.BasicFileAttributes attrs = Files.readAttributes(
                    img, java.nio.file.attribute.BasicFileAttributes.class);
            Instant t = attrs.creationTime().toInstant();
            created = DateTimeFormatter.ofPattern("d MMMM yyyy HH:mm", new Locale("id", "ID"))
                    .withZone(ZoneId.systemDefault()).format(t);
        } catch (Exception e) {
            created = DASH;
        }

        Table row = new Table(UnitValue.createPercentArray(new float[]{1.6f, 4f}))
                .useAllAvailableWidth();

        // Left: Information panel
        Cell info = new Cell()
                .setBackgroundColor(new DeviceRgb(232, 238, 247))
                .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                .setPadding(10)
                .setVerticalAlignment(VerticalAlignment.TOP);
        info.add(new Paragraph("Information")
                .setBold().setFontSize(10).setFontColor(COLOR_BRI_BLUE).setMarginBottom(8));
        info.add(infoLine("File Name", img.getFileName().toString()));
        info.add(infoLine("Size", formatBytes(sizeBytes)));
        info.add(infoLine("Image Dimension",
                ((int) data.getWidth()) + "x" + ((int) data.getHeight()) + "px"));
        info.add(infoLine("Created Time", created));

        // Right: image
        Cell imgCell = new Cell()
                .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                .setPadding(4)
                .setTextAlignment(TextAlignment.CENTER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
        Image pdfImg = new Image(data);
        pdfImg.setAutoScale(true);
        pdfImg.setMaxHeight(260f);
        pdfImg.setHorizontalAlignment(HorizontalAlignment.CENTER);
        imgCell.add(pdfImg);

        row.addCell(info);
        row.addCell(imgCell);
        doc.add(row);

        // Absolute path footer line
        doc.add(new Paragraph(img.toAbsolutePath().toString())
                .setFontSize(7).setFontColor(COLOR_TEXT_MUTED)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(2).setMarginBottom(8));
    }

    private Paragraph infoLine(String label, String value) {
        Paragraph p = new Paragraph();
        p.add(new Text(label + ":\n").setFontSize(8).setFontColor(COLOR_TEXT_MUTED));
        p.add(new Text(value).setFontSize(9).setBold().setFontColor(COLOR_TEXT));
        p.setMarginBottom(6);
        return p;
    }

    private java.util.List<Path> collectScreenshots(TestCaseResult tc) {
        java.util.LinkedHashSet<Path> images = new java.util.LinkedHashSet<>();

        // 1) Screenshot paths explicitly attached to the test case result
        if (tc.getScreenshotPaths() != null) {
            for (String s : tc.getScreenshotPaths()) {
                Path p = resolveImagePath(s);
                if (p != null) images.add(p);
            }
        }

        // 2) Authoritative source: parse execution0.log for <property name="attachment">
        //    records that occur between "Start Test Case : <name>" / "End Test Case : <name>"
        if (images.isEmpty()) {
            java.util.List<String> attachments = lookupAttachments(tc.getTestCaseName());
            for (String fileName : attachments) {
                Path p = resolveImagePath(fileName);
                if (p != null) images.add(p);
            }
        }

        // 3) Last-resort fallback: walk reportDir for images whose name contains the slug
        if (images.isEmpty()) {
            String slug = sanitizeSlug(tc.getTestCaseName());
            try {
                Files.walk(reportDir)
                        .filter(Files::isRegularFile)
                        .filter(p -> isImage(p.getFileName().toString()))
                        .filter(p -> slug == null || slug.isEmpty()
                                || p.getFileName().toString().toLowerCase().contains(slug)
                                || p.toString().toLowerCase().contains(slug))
                        .sorted()
                        .forEach(images::add);
            } catch (Exception ignore) { /* best-effort */ }
        }

        return new java.util.ArrayList<>(images);
    }

    /**
     * Find attachment filenames belonging to the given test case in
     * {@code execution0.log}. Matching is forgiving: tries an exact match
     * first, then suffix / contains matches against the names found in
     * {@code Start Test Case : ...} markers (Katalon emits the full path,
     * the in-memory result usually keeps just the last segment or the SCENARIO
     * label for BDD tests).
     */
    private java.util.List<String> lookupAttachments(String testCaseName) {
        if (testCaseName == null || testCaseName.isEmpty()) return java.util.Collections.emptyList();
        java.util.Map<String, java.util.List<String>> idx = parseAttachmentsIndex();
        if (idx.isEmpty()) return java.util.Collections.emptyList();

        // 1. exact match
        java.util.List<String> hit = idx.get(testCaseName);
        if (hit != null) return hit;

        // 2. suffix match (log key ends with the requested name, or vice versa)
        String needle = testCaseName.trim();
        for (java.util.Map.Entry<String, java.util.List<String>> e : idx.entrySet()) {
            String key = e.getKey();
            if (key.endsWith(needle) || needle.endsWith(key)) return e.getValue();
        }

        // 3. contains match (handles "TC01 - foo" vs "Test Cases/.../TC01 - foo")
        String lcNeedle = needle.toLowerCase();
        for (java.util.Map.Entry<String, java.util.List<String>> e : idx.entrySet()) {
            String lcKey = e.getKey().toLowerCase();
            if (lcKey.contains(lcNeedle) || lcNeedle.contains(lcKey)) return e.getValue();
        }

        // 4. slug match (alphanumeric only)
        String slugNeedle = sanitizeSlug(needle);
        if (slugNeedle != null && !slugNeedle.isEmpty()) {
            for (java.util.Map.Entry<String, java.util.List<String>> e : idx.entrySet()) {
                String slugKey = sanitizeSlug(e.getKey());
                if (slugKey == null) continue;
                if (slugKey.contains(slugNeedle) || slugNeedle.contains(slugKey)) return e.getValue();
            }
        }

        return java.util.Collections.emptyList();
    }

    /**
     * Lazily parse {@code execution0.log} once and build a map of
     * test-case-name -&gt; list of attachment filenames in order of appearance.
     *
     * <p>The Katalon XmlKeywordLogger emits each screenshot as:</p>
     * <pre>{@code
     *   <record>
     *     ...
     *     <message>Taking screenshot successfully</message>
     *     ...
     *     <property name="attachment">1777518963693.png</property>
     *   </record>
     * }</pre>
     *
     * <p>And brackets every test case with</p>
     * <pre>{@code <message>Start Test Case : <name></message> ... <message>End Test Case : <name></message>}</pre>
     */
    private synchronized java.util.Map<String, java.util.List<String>> parseAttachmentsIndex() {
        if (attachmentsByTestCase != null) return attachmentsByTestCase;
        java.util.Map<String, java.util.List<String>> idx = new java.util.LinkedHashMap<>();
        Path log = reportDir.resolve("execution0.log");
        if (!Files.exists(log)) {
            attachmentsByTestCase = idx;
            return idx;
        }
        try {
            javax.xml.parsers.DocumentBuilderFactory factory =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setValidating(false);
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((p, sId) -> new org.xml.sax.InputSource(new java.io.StringReader("")));
            org.w3c.dom.Document xml = builder.parse(log.toFile());

            org.w3c.dom.NodeList records = xml.getElementsByTagName("record");
            // Stack of currently-open test case names (handles nested SCENARIO inside Test Case).
            java.util.Deque<String> open = new java.util.ArrayDeque<>();

            for (int i = 0; i < records.getLength(); i++) {
                org.w3c.dom.Element rec = (org.w3c.dom.Element) records.item(i);
                String message = text(rec, "message");

                if (message != null && message.startsWith("Start Test Case :")) {
                    String name = message.substring("Start Test Case :".length()).trim();
                    open.push(name);
                    idx.computeIfAbsent(name, k -> new java.util.ArrayList<>());
                } else if (message != null && message.startsWith("End Test Case :")) {
                    if (!open.isEmpty()) open.pop();
                } else {
                    // Look for <property name="attachment">FILENAME.png</property>
                    org.w3c.dom.NodeList props = rec.getElementsByTagName("property");
                    for (int j = 0; j < props.getLength(); j++) {
                        org.w3c.dom.Element prop = (org.w3c.dom.Element) props.item(j);
                        if ("attachment".equals(prop.getAttribute("name"))) {
                            String file = prop.getTextContent();
                            if (file == null || file.trim().isEmpty()) continue;
                            file = file.trim();
                            // Attribute attachment to every currently-open test case
                            // (BDD: scenario inside test case -> screenshot belongs to both).
                            if (open.isEmpty()) {
                                idx.computeIfAbsent("__orphan__", k -> new java.util.ArrayList<>()).add(file);
                            } else {
                                for (String tcName : open) {
                                    idx.computeIfAbsent(tcName, k -> new java.util.ArrayList<>()).add(file);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse execution0.log for screenshot attachments: {}", e.getMessage());
        }
        attachmentsByTestCase = idx;
        return idx;
    }

    // ----- detail-grid helpers -----

    private Cell detailLabel(String text) {
        return new Cell()
                .add(new Paragraph(text).setBold().setFontSize(9).setFontColor(COLOR_TEXT))
                .setBackgroundColor(COLOR_BG_LIGHT)
                .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                .setPadding(8)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private Cell detailValue(String text, boolean bold) {
        Paragraph p = new Paragraph(text == null || text.isEmpty() ? DASH : text)
                .setFontSize(9).setFontColor(COLOR_TEXT);
        if (bold) p.setBold();
        return new Cell()
                .add(p)
                .setBackgroundColor(ColorConstants.WHITE)
                .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                .setPadding(8)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private Cell detailValueWide(String text, int colspan) {
        Paragraph p = new Paragraph(text == null || text.isEmpty() ? DASH : text)
                .setFontSize(9).setFontColor(COLOR_TEXT);
        return new Cell(1, colspan)
                .add(p)
                .setBackgroundColor(ColorConstants.WHITE)
                .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                .setPadding(8)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private Cell detailStatusValue(TestCase.TestCaseStatus status) {
        DeviceRgb c = statusColor(status);
        String name = status != null ? status.name() : DASH;
        return new Cell()
                .add(new Paragraph(name).setBold().setFontSize(10).setFontColor(c))
                .setBackgroundColor(ColorConstants.WHITE)
                .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                .setPadding(8)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private String buildTestCaseId(TestCaseResult tc) {
        // Use test case ID which contains "Test Cases/BRICAMS/Transfer Fund/..."
        if (tc.getTestCaseId() != null && !tc.getTestCaseId().isEmpty()) {
            return tc.getTestCaseId();
        }
        // Fallback to test case name
        if (tc.getTestCaseName() != null) {
            return "Test Cases/" + tc.getTestCaseName();
        }
        return DASH;
    }

    private String buildDescription(TestCaseResult tc) {
        // Use description from .tc file if available
        if (tc.getDescription() != null && !tc.getDescription().isEmpty()) {
            return tc.getDescription();
        }
        // Fallback: derive from BDD info or status
        try {
            if (tc.getFeatureFile() != null || tc.getScenarioName() != null) {
                StringBuilder sb = new StringBuilder();
                if (tc.getFeatureFile() != null)  sb.append("feature = ").append(tc.getFeatureFile()).append('\n');
                if (tc.getScenarioName() != null) sb.append("scenario = ").append(tc.getScenarioName());
                return sb.toString();
            }
        } catch (Throwable ignore) { /* model may differ */ }
        return DASH;
    }

    private String extractTag(TestCaseResult tc) {
        // Use tag from .tc file if available
        if (tc.getTag() != null && !tc.getTag().isEmpty()) {
            return tc.getTag();
        }
        // Fallback: try to extract from description
        try {
            String d = tc.getDescription();
            if (d != null) {
                for (String line : d.split("\\r?\\n")) {
                    String l = line.trim();
                    if (l.toLowerCase().startsWith("tag")) return l;
                    if (l.startsWith("@")) return l;
                }
            }
        } catch (Throwable ignore) { /* model may not expose getDescription */ }
        return DASH;
    }

    private String truncateName(String s) {
        if (s == null) return "";
        return s.length() > 40 ? s.substring(0, 37) + "..." : s;
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) return DASH;
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.3f MB", mb);
        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
    }

    private DeviceRgb statusColor(TestCase.TestCaseStatus status) {
        if (status == null) return COLOR_SKIPPED;
        switch (status) {
            case PASSED:  return COLOR_PASSED;
            case FAILED:  return COLOR_FAILED;
            case ERROR:   return COLOR_ERROR;
            case SKIPPED: return COLOR_SKIPPED;
            default:      return COLOR_TEXT_MUTED;
        }
    }

    private String formatElapsed(Duration d) {
        if (d == null) return "0s";
        long secs = d.getSeconds();
        long mins = secs / 60;
        secs = secs % 60;
        long hrs = mins / 60;
        mins = mins % 60;
        if (hrs > 0) return String.format("%dh %dm %ds", hrs, mins, secs);
        return String.format("%dm - %ds", mins, secs);
    }

    private String formatDuration(Duration d) {
        if (d == null) return "0s";
        long ms = d.toMillis();
        if (ms < 0) ms = 0;
        long secs = ms / 1000;
        long millis = ms % 1000;
        if (secs < 60) return String.format("%d.%03ds", secs, millis);
        long mins = secs / 60;
        secs = secs % 60;
        return String.format("%dm %d.%03ds", mins, secs, millis);
    }

    private static String nvl(String s) { return s == null || s.isEmpty() ? DASH : s; }

    private static String firstLine(String s) {
        if (s == null || s.isEmpty()) return DASH;
        int nl = s.indexOf('\n');
        String first = nl < 0 ? s : s.substring(0, nl);
        return first.length() > 200 ? first.substring(0, 200) + "..." : first;
    }

    private static String abbrev(String s, int max) {
        if (s == null || s.isEmpty()) return DASH;
        return s.length() > max ? "..." + s.substring(s.length() - max) : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "\n... (truncated)" : s;
    }

    private static boolean isImage(String name) {
        if (name == null) return false;
        String l = name.toLowerCase();
        return l.endsWith(".png") || l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".gif");
    }

    private static String sanitizeSlug(String name) {
        if (name == null) return null;
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "_");
    }

    private Path resolveImagePath(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            Path p = Paths.get(s);
            if (p.isAbsolute() && Files.exists(p)) return p;
            Path rel = reportDir.resolve(s);
            if (Files.exists(rel)) return rel;
            Path rel2 = reportDir.resolve("screenshots").resolve(p.getFileName().toString());
            if (Files.exists(rel2)) return rel2;
            Path cwd = Paths.get(System.getProperty("user.dir", ".")).resolve(s);
            if (Files.exists(cwd)) return cwd;
        } catch (Exception ignore) { /* fall-through */ }
        return null;
    }

    private ExecutionResult buildResultFromLog() {
        ExecutionResult er = new ExecutionResult();
        er.setName(reportDir.getFileName() != null ? reportDir.getFileName().toString() : "Report");
        Path log = reportDir.resolve("execution0.log");
        if (!Files.exists(log)) {
            er.setStartTime(Instant.now());
            er.setEndTime(Instant.now());
            return er;
        }
        try {
            javax.xml.parsers.DocumentBuilderFactory factory =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setValidating(false);
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((p, sId) -> new org.xml.sax.InputSource(new java.io.StringReader("")));
            org.w3c.dom.Document xml = builder.parse(log.toFile());

            TestSuiteResult suite = new TestSuiteResult(er.getName());
            Instant suiteStart = null;
            Instant suiteEnd = null;

            org.w3c.dom.NodeList records = xml.getElementsByTagName("record");
            TestCaseResult current = null;
            long testStart = 0;
            for (int i = 0; i < records.getLength(); i++) {
                org.w3c.dom.Element r = (org.w3c.dom.Element) records.item(i);
                String level   = text(r, "level");
                String message = text(r, "message");
                String millis  = text(r, "millis");
                if (message == null) continue;

                if (message.startsWith("Start Test Case :")) {
                    String tcName = message.substring("Start Test Case :".length()).trim();
                    current = new TestCaseResult(tcName);
                    current.setStatus(TestCase.TestCaseStatus.PASSED);
                    testStart = millis != null ? Long.parseLong(millis) : 0L;
                    if (testStart > 0) {
                        Instant ts = Instant.ofEpochMilli(testStart);
                        current.setStartTime(ts);
                        if (suiteStart == null) suiteStart = ts;
                    }
                } else if (message.startsWith("End Test Case :") && current != null) {
                    long end = millis != null ? Long.parseLong(millis) : testStart;
                    if (end > 0) {
                        Instant te = Instant.ofEpochMilli(end);
                        current.setEndTime(te);
                        suiteEnd = te;
                    }
                    suite.addTestCaseResult(current);
                    current = null;
                } else if (current != null && ("FAILED".equals(level) || "ERROR".equals(level))) {
                    current.setStatus("FAILED".equals(level)
                            ? TestCase.TestCaseStatus.FAILED
                            : TestCase.TestCaseStatus.ERROR);
                    String existing = current.getErrorMessage();
                    current.setErrorMessage(existing == null ? message : existing + "\n" + message);
                }
            }

            if (suiteStart != null) suite.setStartTime(suiteStart);
            if (suiteEnd   != null) suite.setEndTime(suiteEnd);
            er.setStartTime(suiteStart != null ? suiteStart : Instant.now());
            er.setEndTime(suiteEnd != null ? suiteEnd : Instant.now());
            er.addSuiteResult(suite);
        } catch (Exception e) {
            logger.warn("Failed to parse execution0.log for PDF: {}", e.getMessage());
            if (er.getStartTime() == null) er.setStartTime(Instant.now());
            if (er.getEndTime()   == null) er.setEndTime(Instant.now());
        }
        return er;
    }

    private static String text(org.w3c.dom.Element parent, String tag) {
        org.w3c.dom.NodeList n = parent.getElementsByTagName(tag);
        return n.getLength() > 0 ? n.item(0).getTextContent() : null;
    }
    
    /**
     * Parse .tc file to extract description and tag
     */
    private void parseTcFile(TestCaseResult tc) {
        String testCaseId = tc.getTestCaseId();
        if (testCaseId == null || testCaseId.isEmpty()) {
            return;
        }
        
        try {
            // Build path to .tc file from test case ID (e.g., "Test Cases/BRICAMS/Transfer Fund/...")
            // Use project root (user.dir) - reports may be deeply nested under Reports/<ts>/<sub>/...
            Path projectRoot = Paths.get(System.getProperty("user.dir", ""));
            Path tcFilePath = projectRoot.resolve(testCaseId + ".tc");
            
            if (!Files.exists(tcFilePath)) {
                logger.warn("Test case file not found: {}", tcFilePath);
                return;
            }
            
            // Parse XML
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document tcDoc = builder.parse(tcFilePath.toFile());
            
            org.w3c.dom.Element root = tcDoc.getDocumentElement();
            
            // Extract description
            String description = text(root, "description");
            if (description != null && !description.trim().isEmpty()) {
                tc.setDescription(description.trim());
            }
            
            // Extract tag
            String tag = text(root, "tag");
            if (tag != null && !tag.trim().isEmpty()) {
                tc.setTag(tag.trim());
            }
            
        } catch (Exception e) {
            logger.warn("Failed to parse .tc file for {}: {}", tc.getTestCaseName(), e.getMessage());
        }
    }
    
    /**
     * Clean test case name by removing "SCENARIO " prefix if present
     */
    private String cleanTestCaseName(String name) {
        if (name == null) return "";
        if (name.startsWith("SCENARIO ")) {
            return name.substring(9);
        }
        return name;
    }
    
    /**
     * Resolve the executor (tester) name from prioritized sources:
     *   1. CLI: -g_nama_tester=value (stored in GlobalVariable)
     *   2. <projectDir>/MyActmo.txt -> Tester_Name
     *   3. ~/.katalon/session.properties -> fullName
     *   4. System user.name (final fallback)
     */
    private String resolveExecutor() {
        // 1. CLI -g_nama_tester via GlobalVariable
        try {
            Object val = com.katalan.core.compat.GlobalVariable.get("nama_tester");
            if (val != null && !val.toString().trim().isEmpty()) {
                String name = val.toString().trim();
                logger.info("👤 Executor resolved from CLI -g_nama_tester: {}", name);
                return name;
            }
        } catch (Throwable ignored) { /* GlobalVariable not initialized */ }

        // 2. <projectDir>/MyActmo.txt -> Tester_Name
        try {
            Path projectRoot = Paths.get(System.getProperty("user.dir", ""));
            Path actmoFile = projectRoot.resolve("MyActmo.txt");
            if (Files.exists(actmoFile)) {
                Properties props = new Properties();
                try (java.io.InputStream is = Files.newInputStream(actmoFile)) {
                    props.load(is);
                }
                String name = props.getProperty("Tester_Name");
                if (name != null && !name.trim().isEmpty()) {
                    logger.info("👤 Executor resolved from MyActmo.txt: {}", name.trim());
                    return name.trim();
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to read MyActmo.txt for executor: {}", e.getMessage());
        }

        // 3. ~/.katalon/session.properties -> fullName
        String fullName = readKatalonSessionFullName();
        if (fullName != null && !fullName.isEmpty()) {
            logger.info("👤 Executor resolved from session.properties: {}", fullName);
            return fullName;
        }

        // 4. Final fallback
        return System.getProperty("user.name", "Unknown");
    }

    /**
     * Read Katalon session.properties to extract fullName.
     */
    private String readKatalonSessionFullName() {
        try {
            String userHome = System.getProperty("user.home");
            Path[] candidates = new Path[] {
                    Paths.get(userHome, ".katalon", "session.properties"),
                    System.getenv("USERPROFILE") != null
                            ? Paths.get(System.getenv("USERPROFILE"), ".katalon", "session.properties")
                            : null
            };
            for (Path p : candidates) {
                if (p == null) continue;
                if (Files.exists(p)) {
                    String content = Files.readString(p);
                    // Format: "fullName"\:"Muhamad Badru Salam"
                    java.util.regex.Pattern ptn =
                            java.util.regex.Pattern.compile("\"fullName\"\\\\:\"([^\"]+)\"");
                    java.util.regex.Matcher m = ptn.matcher(content);
                    if (m.find()) {
                        return m.group(1);
                    }
                }
            }
        } catch (Exception ignored) { /* fall through */ }
        return null;
    }

    /**
     * Get relative test case ID from script path
     * E.g., "Test Cases/BRICAMS/Transfer Fund/BRI to BRI/Standart TC/TC01 - Transaksi..."
     */
    private String getTestCaseId(TestCaseResult tc) {
        if (tc.getScriptPath() != null && !tc.getScriptPath().isEmpty()) {
            return tc.getScriptPath();
        }
        // Fallback to test case ID
        return tc.getTestCaseId() != null ? tc.getTestCaseId() : tc.getTestCaseName();
    }

    private static class FooterHandler implements IEventHandler {
        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfDocument pdf = docEvent.getDocument();
            PdfPage page = docEvent.getPage();
            int pageNum = pdf.getPageNumber(page);
            int total = pdf.getNumberOfPages();
            Rectangle ps = page.getPageSize();
            try (Canvas canvas = new Canvas(new PdfCanvas(page),
                    new Rectangle(ps.getLeft() + 36, ps.getBottom() + 18, ps.getWidth() - 72, 20))) {
                Paragraph p = new Paragraph(
                        "Automation Test Report    |    Page " + pageNum + " / " + total)
                        .setFontSize(8)
                        .setFontColor(new DeviceRgb(108, 117, 125))
                        .setTextAlignment(TextAlignment.CENTER);
                canvas.add(p);
            } catch (Exception ignored) { /* footer is best-effort */ }
        }
    }
}
