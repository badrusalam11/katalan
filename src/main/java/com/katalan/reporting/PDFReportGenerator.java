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

        // DEBUG: log BDD state per test case
        try {
            for (TestSuiteResult ts : exec.getSuiteResults()) {
                for (TestCaseResult tc : ts.getTestCaseResults()) {
                    java.util.List<java.util.Map<String, Object>> bs = tc.getBddScenarioData();
                    logger.info("PDF[BDD-DEBUG] tc='{}' bdd={} feature='{}' scenarios={}",
                            tc.getTestCaseName(), tc.isBddTest(), tc.getFeatureFile(),
                            bs == null ? "null" : String.valueOf(bs.size()));
                }
            }
        } catch (Exception ignored) { /* debug only */ }

        // If engine-provided result has no BDD data but execution0.log does,
        // re-parse the log to populate Cucumber sections.
        if (!hasAnyBddData(exec)) {
            try {
                ExecutionResult logExec = buildResultFromLog();
                mergeBddDataFromLog(exec, logExec);
            } catch (Exception e) {
                logger.warn("Failed to merge BDD data from log: {}", e.getMessage());
            }
        }

        // Ensure totals are correct before rendering (passed/failed/error counts)
        try {
            exec.recalculateTotals();
        } catch (Exception e) {
            logger.debug("Failed to recalculate execution totals: {}", e.getMessage());
        }

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
                addCucumberScenarioTable(doc, exec);
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

        grid.addCell(labelCell("Skipped"));
        grid.addCell(valueCell(String.valueOf(exec.getSkippedTests()), COLOR_SKIPPED));
        grid.addCell(labelCell("Pass Rate"));
        grid.addCell(valueCell(String.format("%.1f%%", exec.getPassRate()),
                exec.getPassRate() >= 100.0 ? COLOR_PASSED : COLOR_FAILED).setBold());

        grid.addCell(new Cell().setBorder(Border.NO_BORDER)); // empty cell
        grid.addCell(new Cell().setBorder(Border.NO_BORDER)); // empty cell
        grid.addCell(labelCell("Status"));
        boolean ok = exec.getFailedTests() == 0;
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
        
        // Try to load Chrome icon from JAR resources (distributed with the JAR)
        try {
            // Try to load from classpath (works when packaged in JAR or running from IDE)
            java.io.InputStream iconStream = getClass().getClassLoader()
                    .getResourceAsStream("chrome_icon.png");
            if (iconStream != null) {
                byte[] iconBytes = iconStream.readAllBytes();
                iconStream.close();
                ImageData imgData = ImageDataFactory.create(iconBytes);
                Image img = new Image(imgData)
                        .setWidth(48)
                        .setHeight(48)
                        .setHorizontalAlignment(HorizontalAlignment.CENTER);
                Paragraph imgParagraph = new Paragraph().add(img).setTextAlignment(TextAlignment.CENTER);
                iconCell.add(imgParagraph);
            } else {
                // Fallback to emoji if resource not found
                logger.debug("Chrome icon not found in classpath resources, using emoji");
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
    // Show katalan runner version (not Katalon Studio). Prefer ExecutionResult.reportPath's metadata
    String katalanVersion = null;
    try {
        katalanVersion = com.katalan.core.Version.getVersion();
    } catch (Exception e) {
        katalanVersion = "-";
    }
        envRow(kv, "Katalon version", "10.3.2.0"); // Show fixed Katalon Studio version for compatibility
        envRow(kv, "Katalan version", katalanVersion);
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

        // Cucumber Given/When/Then steps (BDD scenarios only) — shown
        // BETWEEN the test-case detail grid and the failure summary.
        addCucumberStepsBlock(doc, tc);

        // If test case failed or errored, include failure summary and truncated stacktrace + console snippet
        if (tc.getStatus() == TestCase.TestCaseStatus.FAILED || tc.getStatus() == TestCase.TestCaseStatus.ERROR) {
            // Failure header
            Paragraph failHdr = new Paragraph("Failure Summary")
                    .setFontSize(12).setBold().setFontColor(COLOR_ERROR).setMarginTop(8).setMarginBottom(6);
            doc.add(failHdr);

            String errMsg = tc.getErrorMessage() != null ? tc.getErrorMessage() : "Test failed";
            // Show a short human-friendly first line
            Paragraph em = new Paragraph(errMsg.split("\n")[0]).setFontSize(9).setFontColor(COLOR_TEXT_MUTED);
            doc.add(em);

            // Combine stacktrace and console for analysis
            String st = tc.getStackTrace() != null ? tc.getStackTrace() : "";
            String console = tc.getConsoleOutput() != null ? tc.getConsoleOutput() : "";

            // === 1) Extract ONLY the meaningful ERROR message (filter out Selenium noise) ===
            String coreError = extractCoreErrorMessage(console, st);
            if (!coreError.isEmpty()) {
                Paragraph errHdr = new Paragraph("❌ Error")
                        .setFontSize(10).setBold().setFontColor(COLOR_ERROR).setMarginTop(8).setMarginBottom(4);
                doc.add(errHdr);
                Paragraph errPara = new Paragraph(coreError).setFontSize(9).setFontColor(COLOR_FAILED).setBold();
                errPara.setBorder(new SolidBorder(COLOR_FAILED, 0.8f)).setPadding(6).setMarginBottom(6);
                doc.add(errPara);
            }

            // === 2) Detect user's groovy files (most useful info!) ===
            java.util.List<String> userFrames = extractUserGroovyFrames(st, console);
            if (!userFrames.isEmpty()) {
                Paragraph srcHdr = new Paragraph("📍 Source File(s) Involved")
                        .setFontSize(10).setBold().setFontColor(COLOR_BRI_BLUE).setMarginTop(6).setMarginBottom(4);
                doc.add(srcHdr);
                StringBuilder srcSb = new StringBuilder();
                for (String f : userFrames) {
                    srcSb.append("➜  ").append(f).append("\n");
                }
                Paragraph srcPara = new Paragraph(srcSb.toString().trim())
                        .setFontSize(9).setFontColor(COLOR_BRI_BLUE).setBold();
                srcPara.setBorder(new SolidBorder(COLOR_BRI_BLUE, 0.8f))
                        .setBackgroundColor(COLOR_BG_LIGHT).setPadding(6).setMarginBottom(6);
                doc.add(srcPara);
            }

            // === 3) Filtered console snippet (ERROR lines only, no Selenium noise) ===
            if (!console.isEmpty()) {
                String snippet = extractCleanErrorLines(console);
                if (!snippet.isEmpty()) {
                    Paragraph cHdr = new Paragraph("Console snippet (error-level lines)")
                            .setFontSize(10).setBold().setFontColor(COLOR_TEXT).setMarginTop(6).setMarginBottom(4);
                    doc.add(cHdr);
                    Paragraph cPara = new Paragraph(snippet).setFontSize(8).setFontColor(COLOR_TEXT_MUTED);
                    cPara.setBorder(new SolidBorder(COLOR_BORDER, 0.5f)).setPadding(6).setMarginTop(6).setMarginBottom(6);
                    doc.add(cPara);
                }
            }
        }

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
    // Cumulative size cap for embedded screenshot bytes (configurable; default 90 MB).
    // Once exceeded, remaining screenshots are summarised instead of embedded.
    private static final long PDF_SCREENSHOT_BUDGET_BYTES = parseScreenshotBudget();
    private long screenshotBytesEmbedded = 0L;

    private static long parseScreenshotBudget() {
        try {
            String v = System.getProperty("katalan.pdf.maxScreenshotMB");
            if (v != null && !v.isEmpty()) {
                return Long.parseLong(v.trim()) * 1024L * 1024L;
            }
        } catch (Exception ignored) { /* fall back */ }
        return 90L * 1024L * 1024L; // ~90MB → leaves headroom under 100MB total PDF
    }

    private void addScreenshotsBriStyle(Document doc, TestCaseResult tc) {
        java.util.List<Path> images = collectScreenshots(tc);
        if (images.isEmpty()) return;

        int embedded = 0;
        int skipped = 0;
        for (Path img : images) {
            if (screenshotBytesEmbedded >= PDF_SCREENSHOT_BUDGET_BYTES) {
                skipped++;
                continue;
            }
            try {
                long before = screenshotBytesEmbedded;
                addOneScreenshot(doc, img);
                embedded++;
                if (screenshotBytesEmbedded == before) {
                    // estimate based on file size if compression didn't update counter
                    try { screenshotBytesEmbedded += Files.size(img); } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                logger.debug("Failed to embed screenshot {}: {}", img, e.getMessage());
            }
        }
        if (skipped > 0) {
            doc.add(new Paragraph(skipped + " more screenshot(s) omitted to keep the PDF under "
                    + (PDF_SCREENSHOT_BUDGET_BYTES / (1024 * 1024)) + "MB.")
                    .setFontSize(8).setItalic().setFontColor(COLOR_TEXT_MUTED).setMarginBottom(8));
        }
    }

    private void addOneScreenshot(Document doc, Path img) throws Exception {
        // Section label "Screenshot" with a thin underline like the BRI report
        doc.add(new Paragraph("Screenshot")
                .setBold().setFontSize(10).setFontColor(COLOR_TEXT)
                .setMarginTop(8).setMarginBottom(4)
                .setBorderBottom(new SolidBorder(COLOR_BRI_BLUE, 1f))
                .setPaddingBottom(2));

        // Compress image bytes (max 1280px wide JPEG q=0.6) to keep PDF small.
        byte[] compressed = compressImageBytes(img, 1280, 0.6f);
        ImageData data;
        long sizeBytes;
        if (compressed != null) {
            data = ImageDataFactory.create(compressed);
            sizeBytes = compressed.length;
            screenshotBytesEmbedded += compressed.length;
        } else {
            data = ImageDataFactory.create(img.toAbsolutePath().toString());
            try { sizeBytes = Files.size(img); } catch (Exception e) { sizeBytes = -1; }
            if (sizeBytes > 0) screenshotBytesEmbedded += sizeBytes;
        }
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

    /** Compress a screenshot to a JPEG byte array, downscaling to maxWidth and using the
     *  given quality (0..1). Returns null on failure (caller falls back to raw file). */
    private static byte[] compressImageBytes(Path src, int maxWidth, float quality) {
        try {
            java.awt.image.BufferedImage in = javax.imageio.ImageIO.read(src.toFile());
            if (in == null) return null;
            int w = in.getWidth();
            int h = in.getHeight();
            double scale = (w > maxWidth) ? ((double) maxWidth / w) : 1.0;
            int nw = (int) Math.round(w * scale);
            int nh = (int) Math.round(h * scale);
            java.awt.image.BufferedImage out =
                    new java.awt.image.BufferedImage(nw, nh, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = out.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                    java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, nw, nh);
            g.drawImage(in, 0, 0, nw, nh, null);
            g.dispose();
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.util.Iterator<javax.imageio.ImageWriter> writers =
                    javax.imageio.ImageIO.getImageWritersByFormatName("jpg");
            if (!writers.hasNext()) return null;
            javax.imageio.ImageWriter writer = writers.next();
            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            try (javax.imageio.stream.ImageOutputStream ios =
                         javax.imageio.ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                writer.write(null, new javax.imageio.IIOImage(out, null, null), param);
            } finally {
                writer.dispose();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
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

    /** Returns true if any TestCaseResult in the given exec has bddScenarioData populated. */
    private static boolean hasAnyBddData(ExecutionResult exec) {
        if (exec == null) return false;
        for (TestSuiteResult ts : exec.getSuiteResults()) {
            for (TestCaseResult tc : ts.getTestCaseResults()) {
                java.util.List<java.util.Map<String, Object>> bs = tc.getBddScenarioData();
                if (bs != null && !bs.isEmpty()) return true;
            }
        }
        return false;
    }

    /** Copy bddScenarioData/featureFile/scenarioName from a log-derived exec onto the engine exec. */
    private static void mergeBddDataFromLog(ExecutionResult engineExec, ExecutionResult logExec) {
        if (engineExec == null || logExec == null) return;
        java.util.List<TestCaseResult> engineTcs = new java.util.ArrayList<>();
        for (TestSuiteResult ts : engineExec.getSuiteResults()) engineTcs.addAll(ts.getTestCaseResults());
        java.util.List<TestCaseResult> logTcs = new java.util.ArrayList<>();
        for (TestSuiteResult ts : logExec.getSuiteResults()) logTcs.addAll(ts.getTestCaseResults());

        // Pair by index (same order in execution0.log as engine emitted).
        int n = Math.min(engineTcs.size(), logTcs.size());
        for (int i = 0; i < n; i++) {
            TestCaseResult etc = engineTcs.get(i);
            TestCaseResult ltc = logTcs.get(i);
            if (ltc.getBddScenarioData() != null && !ltc.getBddScenarioData().isEmpty()
                    && (etc.getBddScenarioData() == null || etc.getBddScenarioData().isEmpty())) {
                etc.setBddScenarioData(ltc.getBddScenarioData());
            }
            if ((etc.getFeatureFile() == null || etc.getFeatureFile().isEmpty())
                    && ltc.getFeatureFile() != null && !ltc.getFeatureFile().isEmpty()) {
                etc.setFeatureFile(ltc.getFeatureFile());
            }
            if ((etc.getScenarioName() == null || etc.getScenarioName().isEmpty())
                    && ltc.getScenarioName() != null && !ltc.getScenarioName().isEmpty()) {
                etc.setScenarioName(ltc.getScenarioName());
            }
            if (ltc.isBddTest()) etc.setBddTest(true);
        }
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
            // Hierarchical BDD tracking: list of scenario maps, each with children=steps
            java.util.List<java.util.Map<String, Object>> bddScenarios = null;
            java.util.Map<String, Object> currentScenario = null;
            java.util.List<java.util.Map<String, Object>> currentSteps = null;
            java.util.Map<String, Object> currentStep = null;
            long currentStepStart = 0L;
            for (int i = 0; i < records.getLength(); i++) {
                org.w3c.dom.Element r = (org.w3c.dom.Element) records.item(i);
                String level   = text(r, "level");
                String message = text(r, "message");
                String millis  = text(r, "millis");
                if (message == null) continue;

                java.util.Map<String, String> props = readProperties(r);

                if (message.startsWith("Start Test Case :")) {
                    String tcName = message.substring("Start Test Case :".length()).trim();
                    String featureName  = props.get("BDD_FEATURE_NAME");
                    String scenarioName = props.get("BDD_TESTCASE_NAME");

                    // Distinguish OUTER (test case script) vs INNER (BDD scenario)
                    boolean isInnerScenario = (featureName != null && !featureName.isEmpty())
                            || tcName.startsWith("SCENARIO ");

                    if (isInnerScenario && current != null) {
                        // Open a new scenario inside the outer test case
                        currentScenario = new java.util.LinkedHashMap<>();
                        String scName = scenarioName != null && !scenarioName.isEmpty()
                                ? scenarioName
                                : (tcName.startsWith("SCENARIO ") ? tcName.substring("SCENARIO ".length()) : tcName);
                        String featName = featureName != null ? featureName : "";
                        currentScenario.put("scenarioName", scName);
                        currentScenario.put("featureName", featName);
                        currentScenario.put("name", "Start Test Case : SCENARIO " + scName);
                        currentScenario.put("result", "PASSED");
                        currentSteps = new java.util.ArrayList<>();
                        currentScenario.put("children", currentSteps);
                        if (bddScenarios == null) bddScenarios = new java.util.ArrayList<>();

                        // Recover relative feature file path from log property if available
                        String featFile = props.get("BDD_FEATURE_FILE");
                        if (featFile != null && !featFile.isEmpty()) {
                            currentScenario.put("featureFile", featFile);
                            if (current.getFeatureFile() == null || current.getFeatureFile().isEmpty()) {
                                current.setFeatureFile(featFile);
                            }
                        }
                        if (current.getScenarioName() == null || current.getScenarioName().isEmpty()) {
                            current.setScenarioName(scName);
                        }
                        current.setBddTest(true);
                    } else {
                        // Outer test case
                        current = new TestCaseResult(tcName);
                        current.setStatus(TestCase.TestCaseStatus.PASSED);
                        testStart = millis != null ? Long.parseLong(millis) : 0L;
                        if (testStart > 0) {
                            Instant ts = Instant.ofEpochMilli(testStart);
                            current.setStartTime(ts);
                            if (suiteStart == null) suiteStart = ts;
                        }
                        bddScenarios = new java.util.ArrayList<>();
                        currentScenario = null;
                        currentSteps = null;
                        currentStep = null;
                    }
                } else if (message.startsWith("End Test Case :") && current != null) {
                    boolean isInnerEnd = currentScenario != null
                            && (message.contains("SCENARIO ")
                                || message.contains(String.valueOf(currentScenario.get("scenarioName"))));

                    if (isInnerEnd && currentScenario != null) {
                        // Flush any dangling step (e.g. failing step where End action was lost)
                        if (currentStep != null && currentSteps != null) {
                            currentStep.put("result", "FAILED");
                            currentStep.put("status", "FAILED");
                            currentSteps.add(currentStep);
                            currentScenario.put("result", "FAILED");
                        }
                        // Close current scenario
                        bddScenarios.add(currentScenario);
                        currentScenario = null;
                        currentSteps = null;
                        currentStep = null;
                    } else {
                        // Close outer test case
                        long end = millis != null ? Long.parseLong(millis) : testStart;
                        if (end > 0) {
                            Instant te = Instant.ofEpochMilli(end);
                            current.setEndTime(te);
                            suiteEnd = te;
                        }
                        if (bddScenarios != null && !bddScenarios.isEmpty()) {
                            current.setBddScenarioData(bddScenarios);
                            current.setBddTest(true);
                        }
                        suite.addTestCaseResult(current);
                        current = null;
                        bddScenarios = null;
                        currentScenario = null;
                        currentSteps = null;
                        currentStep = null;
                    }
                } else if (message.startsWith("Start action :") && currentScenario != null
                        && props.get("BDD_STEP_KEYWORD") != null) {
                    // BDD step start
                    currentStep = new java.util.LinkedHashMap<>();
                    currentStep.put("keyword", props.getOrDefault("BDD_STEP_KEYWORD", "").trim());
                    String stepName = props.getOrDefault("BDD_STEP_NAME",
                            message.substring("Start action :".length()).trim());
                    currentStep.put("text", stepName);
                    currentStep.put("name", stepName);
                    currentStep.put("line", props.getOrDefault("BDD_STEP_LINE", ""));
                    currentStep.put("result", "PASSED");
                    currentStep.put("status", "PASSED");
                    currentStep.put("errorMessage", "");
                    currentStepStart = millis != null ? Long.parseLong(millis) : 0L;
                    currentStep.put("startMillis", currentStepStart);
                } else if (message.startsWith("End action :") && currentStep != null && currentSteps != null) {
                    long endMs = millis != null ? Long.parseLong(millis) : currentStepStart;
                    long durMs = currentStepStart > 0 ? Math.max(0L, endMs - currentStepStart) : 0L;
                    currentStep.put("durationMs", durMs);
                    // Honor explicit BDD_STEP_RESULT (FAILED) and source frames written by KatalanBDDExecutor.
                    String stepResult = props.get("BDD_STEP_RESULT");
                    if (stepResult != null && !stepResult.isEmpty()) {
                        currentStep.put("result", stepResult);
                        currentStep.put("status", stepResult);
                        if ("FAILED".equalsIgnoreCase(stepResult) && currentScenario != null) {
                            currentScenario.put("result", "FAILED");
                        }
                    }
                    String srcFiles = props.get("BDD_STEP_SOURCE_FILES");
                    if (srcFiles != null && !srcFiles.isEmpty()) {
                        java.util.List<String> frames = new java.util.ArrayList<>();
                        for (String line : srcFiles.split("\n")) {
                            String t = line.trim();
                            if (!t.isEmpty()) frames.add(t);
                        }
                        if (!frames.isEmpty()) currentStep.put("sourceFrames", frames);
                    }
                    String stepErr = props.get("BDD_STEP_ERROR_MESSAGE");
                    if (stepErr != null && !stepErr.isEmpty()) {
                        currentStep.put("errorMessage", stepErr);
                    }
                    currentSteps.add(currentStep);
                    currentStep = null;
                } else if (current != null && ("FAILED".equals(level) || "ERROR".equals(level))) {
                    current.setStatus("FAILED".equals(level)
                            ? TestCase.TestCaseStatus.FAILED
                            : TestCase.TestCaseStatus.ERROR);
                    String existing = current.getErrorMessage();
                    current.setErrorMessage(existing == null ? message : existing + "\n" + message);
                    // Mark current scenario + step as failed
                    if (currentScenario != null) {
                        currentScenario.put("result", "FAILED");
                    }
                    if (currentStep != null) {
                        currentStep.put("result", "FAILED");
                        currentStep.put("status", "FAILED");
                        String prev = (String) currentStep.get("errorMessage");
                        currentStep.put("errorMessage",
                                (prev == null || prev.isEmpty()) ? message : prev + "\n" + message);
                    } else if (currentSteps != null && !currentSteps.isEmpty()) {
                        // Failure record arrived AFTER the last step closed (or scenario closed
                        // its step implicitly): propagate to the last step in the scenario.
                        java.util.Map<String, Object> last = currentSteps.get(currentSteps.size() - 1);
                        last.put("result", "FAILED");
                        last.put("status", "FAILED");
                        String prev = (String) last.get("errorMessage");
                        last.put("errorMessage",
                                (prev == null || prev.isEmpty()) ? message : prev + "\n" + message);
                    } else if (currentScenario == null && bddScenarios != null && !bddScenarios.isEmpty()) {
                        // Scenario already closed; mark the most recent scenario + its last step as failed.
                        java.util.Map<String, Object> lastScn = bddScenarios.get(bddScenarios.size() - 1);
                        lastScn.put("result", "FAILED");
                        Object kids = lastScn.get("children");
                        if (kids instanceof java.util.List) {
                            @SuppressWarnings("unchecked")
                            java.util.List<java.util.Map<String, Object>> ch =
                                    (java.util.List<java.util.Map<String, Object>>) kids;
                            if (!ch.isEmpty()) {
                                java.util.Map<String, Object> last = ch.get(ch.size() - 1);
                                last.put("result", "FAILED");
                                last.put("status", "FAILED");
                                String prev = (String) last.get("errorMessage");
                                last.put("errorMessage",
                                        (prev == null || prev.isEmpty()) ? message : prev + "\n" + message);
                            }
                        }
                    }
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
     * Read all {@code <property name="X">value</property>} children of a record
     * into a map. Used to extract BDD_* metadata from execution0.log.
     */
    private static java.util.Map<String, String> readProperties(org.w3c.dom.Element record) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        org.w3c.dom.NodeList props = record.getElementsByTagName("property");
        for (int i = 0; i < props.getLength(); i++) {
            org.w3c.dom.Element prop = (org.w3c.dom.Element) props.item(i);
            String name = prop.getAttribute("name");
            if (name == null || name.isEmpty()) continue;
            String value = prop.getTextContent();
            map.put(name, value != null ? value : "");
        }
        return map;
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

    /* === Helper methods for failure rendering (focused error highlights) === */

    /**
     * Extract the most meaningful ERROR message from console/stacktrace,
     * filtering out Selenium boilerplate (Build info, System info, Capabilities, Session ID).
     */
    private static String extractCoreErrorMessage(String console, String stackTrace) {
        // 1) "[FAILED & STOP]" line in console (most meaningful)
        if (console != null && !console.isEmpty()) {
            for (String l : console.split("\n")) {
                if (l.contains("[FAILED & STOP]") || l.contains("[FAILED]")) {
                    return l.replaceAll(".*\\[FAILED[^\\]]*\\]\\s*", "").trim();
                }
            }
            // 2) "Step failed:" line
            for (String l : console.split("\n")) {
                if (l.contains("Step failed:")) {
                    return l.replaceAll(".*Step failed:\\s*", "").trim();
                }
            }
            // 3) "Expected condition failed:" (selenium WebDriverWait failure)
            for (String l : console.split("\n")) {
                int idx = l.indexOf("Expected condition failed:");
                if (idx >= 0) {
                    String msg = l.substring(idx).trim();
                    int tail = msg.indexOf("(tried for");
                    if (tail > 0) msg = msg.substring(0, tail).trim();
                    return msg;
                }
            }
        }
        // 4) Fallback: first non-noise line of stacktrace
        if (stackTrace != null && !stackTrace.isEmpty()) {
            for (String l : stackTrace.split("\n")) {
                String s = l.trim();
                if (s.isEmpty()) continue;
                if (s.startsWith("Build info:") || s.startsWith("System info:")
                        || s.startsWith("Driver info:") || s.startsWith("Capabilities")
                        || s.startsWith("Session ID:") || s.startsWith("at ")
                        || s.startsWith("...")) continue;
                return s;
            }
        }
        return "";
    }

    /**
     * Extract user's groovy source files from stack trace / console.
     * Filters out internal Java/Katalon/Selenium frames; keeps only user code
     * like {@code website.CSWeb.cari(CSWeb.groovy:55)} or
     * {@code fund_transfer.RTGS_Maker_Revamp.inPageEFT(RTGS_Maker_Revamp.groovy:271)}.
     */
    private static java.util.List<String> extractUserGroovyFrames(String stackTrace, String console) {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "at\\s+([\\w.$]+)\\(([\\w$]+\\.groovy):(\\d+)\\)"
        );

        String combined = (stackTrace == null ? "" : stackTrace) + "\n" + (console == null ? "" : console);
        java.util.regex.Matcher m = p.matcher(combined);
        while (m.find()) {
            String fqMethod = m.group(1);   // e.g., website.CSWeb.cari
            String file = m.group(2);       // e.g., CSWeb.groovy
            String line = m.group(3);       // e.g., 55

            // Skip internal/library packages
            String lower = fqMethod.toLowerCase();
            if (lower.startsWith("com.kms.")) continue;
            if (lower.startsWith("com.katalan.")) continue;
            if (lower.startsWith("org.codehaus.")) continue;
            if (lower.startsWith("org.openqa.")) continue;
            if (lower.startsWith("io.cucumber.")) continue;
            if (lower.startsWith("java.")) continue;
            if (lower.startsWith("jdk.")) continue;
            if (lower.startsWith("sun.")) continue;
            if (lower.startsWith("groovy.")) continue;
            if (lower.startsWith("picocli.")) continue;

            result.add(String.format("%s:%s  →  %s()", file, line, fqMethod));
            if (result.size() >= 6) break; // keep concise
        }

        // Also parse the pretty "➜ Filename.groovy:NN  →  pkg.Class.method()" lines
        // emitted by KatalanBDDExecutor via slf4j (these show up in console output).
        java.util.regex.Pattern arrowPattern = java.util.regex.Pattern.compile(
            "➜\\s+([\\w$]+\\.groovy):(\\d+)\\s+→\\s+([\\w.$]+(?:\\([^)]*\\))?)"
        );
        java.util.regex.Matcher am = arrowPattern.matcher(combined);
        while (am.find()) {
            String file = am.group(1);
            String line = am.group(2);
            String fq = am.group(3).replaceAll("\\(.*\\)$", "");
            result.add(String.format("%s:%s  →  %s()", file, line, fq));
            if (result.size() >= 6) break;
        }

        return new java.util.ArrayList<>(result);
    }

    /**
     * Filter console lines to keep only meaningful error/warn lines for the snippet,
     * removing Selenium driver boilerplate (Build info, System info, Capabilities, Session ID, stack frames).
     */
    private static String extractCleanErrorLines(String console) {
        if (console == null || console.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int added = 0;
        for (String l : console.split("\n")) {
            String s = l.trim();
            // Skip Selenium driver boilerplate
            if (s.startsWith("Build info:")) continue;
            if (s.startsWith("System info:")) continue;
            if (s.startsWith("Driver info:")) continue;
            if (s.startsWith("Capabilities ")) continue;
            if (s.startsWith("Capabilities{")) continue;
            if (s.startsWith("Session ID:")) continue;
            // Skip internal stack frames in console
            if (s.startsWith("at ")) continue;
            if (s.startsWith("...")) continue;

            if (l.contains(" ERROR ") || l.contains(" WARN ")
                    || l.toLowerCase().contains("expected condition")
                    || l.toLowerCase().contains("[failed")
                    || l.toLowerCase().contains("step failed")) {
                sb.append(l).append("\n");
                added++;
                if (added >= 15) break;
            }
        }
        return sb.toString();
    }

    // =====================================================================
    //  Cucumber Scenario rendering (BDD)
    // =====================================================================

    /**
     * Render the "Cucumber Scenario" overview table directly below the
     * Execution Environment block. Only emitted when at least one test case
     * is a BDD/Cucumber scenario.
     *
     * Columns: # | Fitur | Scenario | Category | Status
     */
    private void addCucumberScenarioTable(Document doc, ExecutionResult exec) {
        // Collect (testCase, scenarioMap) pairs across the whole run.
        // bddScenarioData on a TestCaseResult is hierarchical:
        //   List< Map { name, scenarioName, featureName, result, children:[steps], statistics, ... } >
        java.util.List<Object[]> rows = new java.util.ArrayList<>();
        for (TestSuiteResult s : exec.getSuiteResults()) {
            for (TestCaseResult tc : s.getTestCaseResults()) {
                java.util.List<java.util.Map<String, Object>> scenarios = tc.getBddScenarioData();
                if (scenarios != null && !scenarios.isEmpty()) {
                    for (java.util.Map<String, Object> sc : scenarios) {
                        rows.add(new Object[]{tc, sc});
                    }
                } else if (tc.isBddTest()
                        || (tc.getFeatureFile() != null && !tc.getFeatureFile().isEmpty())
                        || (tc.getScenarioName() != null && !tc.getScenarioName().isEmpty())
                        || (tc.getTestCaseName() != null && tc.getTestCaseName().startsWith("SCENARIO "))) {
                    rows.add(new Object[]{tc, null});
                }
            }
        }
        if (rows.isEmpty()) return;

        // Section header
        doc.add(new Paragraph("Cucumber Scenario")
                .setFontSize(13).setBold().setFontColor(COLOR_TEXT)
                .setMarginTop(10).setMarginBottom(6));

        Table table = new Table(UnitValue.createPercentArray(new float[]{0.6f, 3.0f, 4.0f, 1.6f, 1.4f}))
                .useAllAvailableWidth()
                .setMarginBottom(12);

        table.addHeaderCell(thCell("#"));
        table.addHeaderCell(thCell("Fitur"));
        table.addHeaderCell(thCell("Scenario"));
        table.addHeaderCell(thCell("Category"));
        table.addHeaderCell(thCell("Status"));

        int idx = 1;
        for (Object[] row : rows) {
            TestCaseResult tc = (TestCaseResult) row[0];
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> sc = (java.util.Map<String, Object>) row[1];

            String fitur;
            String scenario;
            String statusStr;
            DeviceRgb statusColor;

            if (sc != null) {
                String featName = String.valueOf(sc.getOrDefault("featureName", ""));
                if (featName.isEmpty() && tc.getFeatureFile() != null) {
                    featName = extractFeatureLabel(tc.getFeatureFile());
                }
                fitur = featName.isEmpty() ? DASH : featName;

                String scName = String.valueOf(sc.getOrDefault("scenarioName", ""));
                if (scName.isEmpty()) {
                    String n = String.valueOf(sc.getOrDefault("name", ""));
                    if (n.startsWith("Start Test Case : SCENARIO ")) {
                        scName = n.substring("Start Test Case : SCENARIO ".length());
                    } else {
                        scName = n;
                    }
                }
                scenario = scName.isEmpty() ? DASH : scName;

                String result = String.valueOf(sc.getOrDefault("result", "PASSED")).toUpperCase();
                boolean failed = "FAILED".equals(result) || "ERROR".equals(result);
                statusStr = failed ? "FAILED" : ("SKIPPED".equals(result) ? "SKIPPED" : "PASSED");
                statusColor = failed ? COLOR_FAILED : ("SKIPPED".equals(result) ? COLOR_SKIPPED : COLOR_PASSED);
            } else {
                fitur = tc.getFeatureFile() != null && !tc.getFeatureFile().isEmpty()
                        ? extractFeatureLabel(tc.getFeatureFile()) : DASH;
                scenario = tc.getScenarioName() != null && !tc.getScenarioName().isEmpty()
                        ? tc.getScenarioName() : cleanTestCaseName(tc.getTestCaseName());
                statusStr = tc.getStatus() != null ? tc.getStatus().name() : DASH;
                statusColor = statusColor(tc.getStatus());
            }

            String category = tc.getTag() != null && !tc.getTag().isEmpty()
                    ? tc.getTag() : "Cucumber";

            table.addCell(tdCell(String.valueOf(idx++)).setTextAlignment(TextAlignment.CENTER));
            table.addCell(tdCell(fitur));
            table.addCell(tdCell(scenario));
            table.addCell(tdCell(category).setTextAlignment(TextAlignment.CENTER));
            table.addCell(new Cell()
                    .add(new Paragraph(statusStr).setBold().setFontSize(9).setFontColor(statusColor))
                    .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                    .setPadding(6)
                    .setTextAlignment(TextAlignment.CENTER));
        }

        doc.add(table);
    }

    /**
     * Extract a human label from a feature file path or name.
     * "Feature Files/Login/Login_Valid.feature" -> "Login_Valid"
     */
    private static String extractFeatureLabel(String feature) {
        if (feature == null || feature.isEmpty()) return DASH;
        String s = feature.replace('\\', '/');
        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);
        if (s.toLowerCase().endsWith(".feature")) s = s.substring(0, s.length() - ".feature".length());
        return s;
    }

    /**
     * Render the Cucumber Given/When/Then step list inside a test case detail
     * page (called BEFORE the failure summary). Only emitted when the test
     * case is a BDD scenario with step data.
     */
    private void addCucumberStepsBlock(Document doc, TestCaseResult tc) {
        if (tc == null) return;
        java.util.List<java.util.Map<String, Object>> scenarios = tc.getBddScenarioData();
        if (scenarios == null || scenarios.isEmpty()) return;

        DeviceRgb GREEN = COLOR_PASSED;
        DeviceRgb FAIL_BG = new DeviceRgb(252, 230, 232);

        for (java.util.Map<String, Object> sc : scenarios) {
            // Resolve feature/scenario labels
            String featureLabel = String.valueOf(sc.getOrDefault("featureName", ""));
            if (featureLabel.isEmpty() && tc.getFeatureFile() != null) {
                featureLabel = extractFeatureLabel(tc.getFeatureFile());
            }
            if (featureLabel.isEmpty()) featureLabel = "Feature";

            String scenarioLabel = String.valueOf(sc.getOrDefault("scenarioName", ""));
            if (scenarioLabel.isEmpty()) {
                String n = String.valueOf(sc.getOrDefault("name", ""));
                if (n.startsWith("Start Test Case : SCENARIO ")) {
                    scenarioLabel = n.substring("Start Test Case : SCENARIO ".length());
                } else {
                    scenarioLabel = n;
                }
            }

            String scResult = String.valueOf(sc.getOrDefault("result", "PASSED")).toUpperCase();
            DeviceRgb headBg = ("FAILED".equals(scResult) || "ERROR".equals(scResult)) ? COLOR_FAILED : GREEN;

            // Feature/Scenario header band
            Table head = new Table(UnitValue.createPercentArray(new float[]{1}))
                    .useAllAvailableWidth().setMarginTop(10);
            Cell hCell = new Cell()
                    .setBackgroundColor(headBg)
                    .setBorder(Border.NO_BORDER)
                    .setPadding(8);
            hCell.add(new Paragraph("Feature: " + featureLabel)
                    .setBold().setFontSize(11).setFontColor(ColorConstants.WHITE).setMarginBottom(2));
            
            // Add feature file path (relative to project root, e.g., Include/features/.../file.feature)
            // Prefer scenario-level featureFile (more specific), fallback to test-case-level
            String featureFilePath = String.valueOf(sc.getOrDefault("featureFile", ""));
            if (featureFilePath.isEmpty() && tc.getFeatureFile() != null) {
                featureFilePath = tc.getFeatureFile();
            }
            if (!featureFilePath.isEmpty()) {
                hCell.add(new Paragraph("📄 " + featureFilePath)
                        .setFontSize(8).setItalic().setFontColor(new DeviceRgb(230, 240, 255))
                        .setMarginBottom(3).setMarginTop(2));
            }
            
            if (!scenarioLabel.isEmpty()) {
                hCell.add(new Paragraph("Scenario: " + scenarioLabel)
                        .setFontSize(10).setFontColor(ColorConstants.WHITE));
            }
            head.addCell(hCell);
            doc.add(head);

            // Step rows from scenario.children
            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> steps =
                    (java.util.List<java.util.Map<String, Object>>) sc.get("children");
            if (steps == null || steps.isEmpty()) {
                doc.add(new Paragraph("(no steps recorded)")
                        .setFontSize(9).setItalic().setFontColor(COLOR_TEXT_MUTED).setMarginBottom(8));
                continue;
            }

            Table table = new Table(UnitValue.createPercentArray(new float[]{0.9f, 5.5f, 1.2f, 1.2f}))
                    .useAllAvailableWidth()
                    .setMarginBottom(10);
            table.addHeaderCell(thCell("Keyword"));
            table.addHeaderCell(thCell("Step"));
            table.addHeaderCell(thCell("Duration"));
            table.addHeaderCell(thCell("Status"));

            for (java.util.Map<String, Object> step : steps) {
                String keyword = String.valueOf(step.getOrDefault("keyword", "")).trim();
                String name    = String.valueOf(step.getOrDefault("text",
                                  step.getOrDefault("name", ""))).trim();
                // If name still includes the keyword prefix, strip it
                if (!keyword.isEmpty() && name.toLowerCase().startsWith(keyword.toLowerCase() + " ")) {
                    name = name.substring(keyword.length() + 1).trim();
                }

                // Determine status: prefer "result" (PASSED/FAILED/SKIPPED), fall back to status
                String result = String.valueOf(step.getOrDefault("result", "")).trim().toUpperCase();
                if (result.isEmpty()) {
                    result = String.valueOf(step.getOrDefault("status", "PASSED")).trim().toUpperCase();
                }
                boolean failed  = "FAILED".equals(result) || "ERROR".equals(result);
                boolean skipped = "SKIPPED".equals(result) || "NOT_RUN".equals(result);

                long durMs = computeStepDurationMs(step);

                // Error message: pull from logs[level=FAILED] or errorMessage
                String errorMsg = extractStepErrorMessage(step);

                DeviceRgb statusColor = failed  ? COLOR_FAILED
                                       : skipped ? COLOR_SKIPPED
                                       : COLOR_PASSED;
                DeviceRgb rowBg = failed ? FAIL_BG : new DeviceRgb(255, 255, 255);

                Cell kwCell = new Cell()
                        .add(new Paragraph(keyword.isEmpty() ? "—" : keyword)
                                .setBold().setFontSize(9).setFontColor(COLOR_BRI_BLUE))
                        .setBackgroundColor(rowBg)
                        .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                        .setPadding(6);
                Cell nameCell = new Cell()
                        .add(new Paragraph(name).setFontSize(9).setFontColor(COLOR_TEXT))
                        .setBackgroundColor(rowBg)
                        .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                        .setPadding(6);
                if (failed && !errorMsg.isEmpty()) {
                    String firstLine = errorMsg.split("\\r?\\n")[0];
                    if (firstLine.length() > 400) firstLine = firstLine.substring(0, 400) + "...";
                    nameCell.add(new Paragraph(firstLine)
                            .setFontSize(8).setItalic().setFontColor(COLOR_FAILED).setMarginTop(3));
                }
                // Show source file from sourceFrames if available
                Object sourceFramesObj = step.get("sourceFrames");
                if (sourceFramesObj instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<String> sourceFrames = (java.util.List<String>) sourceFramesObj;
                    if (!sourceFrames.isEmpty()) {
                        String firstFrame = sourceFrames.get(0);
                        nameCell.add(new Paragraph("📍 " + firstFrame)
                                .setFontSize(7).setBold().setFontColor(new DeviceRgb(230, 126, 34))
                                .setMarginTop(2));
                    }
                }
                Cell durCell = new Cell()
                        .add(new Paragraph(formatStepDuration(durMs))
                                .setFontSize(8).setFontColor(COLOR_TEXT_MUTED))
                        .setBackgroundColor(rowBg)
                        .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                        .setPadding(6).setTextAlignment(TextAlignment.CENTER);
                Cell stCell = new Cell()
                        .add(new Paragraph(failed ? "FAILED" : (skipped ? "SKIPPED" : "PASSED"))
                                .setBold().setFontSize(9).setFontColor(statusColor))
                        .setBackgroundColor(rowBg)
                        .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                        .setPadding(6).setTextAlignment(TextAlignment.CENTER);

                table.addCell(kwCell);
                table.addCell(nameCell);
                table.addCell(durCell);
                table.addCell(stCell);
            }

            doc.add(table);
        }
    }

    /** Compute step duration in ms from startTime/endTime ISO strings, with fallbacks. */
    private static long computeStepDurationMs(java.util.Map<String, Object> step) {
        Object dur = step.get("durationMs");
        if (dur instanceof Number) return ((Number) dur).longValue();
        try {
            Object st = step.get("startTime");
            Object et = step.get("endTime");
            if (st instanceof String && et instanceof String) {
                Instant a = Instant.parse((String) st);
                Instant b = Instant.parse((String) et);
                return Math.max(0L, b.toEpochMilli() - a.toEpochMilli());
            }
        } catch (Exception ignored) { /* best-effort */ }
        return 0L;
    }

    /** Pull a sensible error message from a step's logs[] or fields. */
    @SuppressWarnings("unchecked")
    private static String extractStepErrorMessage(java.util.Map<String, Object> step) {
        Object em = step.get("errorMessage");
        if (em instanceof String && !((String) em).isEmpty()) return (String) em;
        Object logs = step.get("logs");
        if (logs instanceof java.util.List) {
            for (Object o : (java.util.List<Object>) logs) {
                if (o instanceof java.util.Map) {
                    java.util.Map<String, Object> log = (java.util.Map<String, Object>) o;
                    String level = String.valueOf(log.getOrDefault("level", "")).toUpperCase();
                    if ("FAILED".equals(level) || "ERROR".equals(level)) {
                        return String.valueOf(log.getOrDefault("message", ""));
                    }
                }
            }
        }
        return "";
    }

    private static String formatStepDuration(long ms) {
        if (ms <= 0) return "0ms";
        if (ms < 1000) return ms + "ms";
        double s = ms / 1000.0;
        if (s < 60) return String.format("%.2fs", s);
        long mins = (long) (s / 60);
        double rem = s - mins * 60;
        return String.format("%dm %.2fs", mins, rem);
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
