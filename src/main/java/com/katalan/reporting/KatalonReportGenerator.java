package com.katalan.reporting;

import com.katalan.core.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Katalon-style Report Generator
 * 
 * Generates reports in the same format as Katalon Studio:
 * Reports/<timestamp>/<TestSuiteName>/<timestamp>/
 *   - <timestamp>.html (main HTML report with embedded execution data)
 *   - <timestamp>.csv (CSV report)
 *   - JUnit_Report.xml
 *   - execution.properties
 *   - execution.uuid
 *   - console0.log
 *   - execution0.log
 *   - testCaseBinding
 *   - tsc_id.txt
 *   - cucumber_report/ (for BDD tests)
 */
public class KatalonReportGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(KatalonReportGenerator.class);
    
    // Katalon timestamp format: yyyyMMdd_HHmmss
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault());
    
    // Log timestamp format: yyyy-MM-dd HH:mm:ss.SSS
    private static final DateTimeFormatter LOG_TIMESTAMP_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());
            
    // Report date format: dd-MM-yyyy HH:mm:ss
    private static final DateTimeFormatter REPORT_DATE_FORMATTER = DateTimeFormatter
            .ofPattern("dd-MM-yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    
    // JUnit timestamp format
    private static final DateTimeFormatter JUNIT_TIMESTAMP_FORMATTER = DateTimeFormatter
            .ofPattern("dd-MM-yyyy'T'HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    
    // ISO timestamp format for JSON (like Katalon)
    private static final DateTimeFormatter ISO_TIMESTAMP_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            .withZone(ZoneId.systemDefault());

    // ISO-8601 UTC timestamp (e.g. 2026-04-22T04:08:50.006Z) — used for
    // the JUnit <testsuite timestamp="..."> attribute, matching Katalon's format.
    private static final DateTimeFormatter JUNIT_ISO_UTC_TIMESTAMP_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneId.of("UTC"));

    // ISO-8601 UTC timestamp with microsecond precision (e.g. 2026-04-21T05:55:04.241869Z)
    // — used for <date> inside execution0.log <record> elements (Katalon XmlKeywordLogger format).
    private static final DateTimeFormatter LOG_RECORD_DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
            .withZone(ZoneId.of("UTC"));

    // Katalon XmlKeywordLogger class name, emitted as <class> in every record.
    private static final String LOG_RECORD_CLASS = "com.kms.katalon.core.logging.XmlKeywordLogger";
    
    private final Path projectPath;
    private final String katalanVersion = "10.3.2.0";  // Match Katalon Studio version exactly
    private Path currentReportDir;
    
    public KatalonReportGenerator(Path projectPath) {
        this.projectPath = projectPath;
    }
    
    /**
     * Compute the Katalon-style per-run report directory without generating
     * any files. Format: {@code Reports/<timestamp>/<SuiteRelativePath>/<timestamp>}.
     * Where {@code SuiteRelativePath} mirrors the suite's location under
     * {@code Test Suites/} (without the {@code .ts} extension). For example,
     * a suite at {@code Test Suites/Regresion/Nomi/BRI to BRI.ts} produces
     * {@code Reports/<timestamp>/Regresion/Nomi/BRI to BRI/<timestamp>}.
     */
    public Path computeReportDirectory(ExecutionResult result) {
        String timestamp = TIMESTAMP_FORMATTER.format(result.getStartTime());
        String suiteRelative = resolveSuiteRelativePath(result);
        Path reportsBaseDir = projectPath.resolve("Reports");
        Path timestampDir = reportsBaseDir.resolve(timestamp);
        // resolve() with a string containing '/' creates nested sub-directories
        Path suiteDir = timestampDir;
        for (String part : suiteRelative.split("/")) {
            if (!part.isEmpty()) suiteDir = suiteDir.resolve(part);
        }
        return suiteDir.resolve(timestamp);
    }
    
    /**
     * Resolve the suite's path relative to the {@code Test Suites/} folder
     * (Katalon-style), stripped of the {@code .ts} extension.
     * <p>
     * Falls back to the suite's flat name if the absolute path is unavailable
     * or does not sit under a {@code Test Suites} ancestor.
     */
    private String resolveSuiteRelativePath(ExecutionResult result) {
        if (result.getSuiteResults().isEmpty()) return "Test Suite";
        com.katalan.core.model.TestSuiteResult sr = result.getSuiteResults().get(0);
        String fallback = sr.getSuiteName() != null ? sr.getSuiteName() : "Test Suite";
        Path sp = sr.getSuitePath();
        if (sp == null) return fallback;
        // Walk up the path to find the "Test Suites" ancestor
        Path cursor = sp.toAbsolutePath().normalize();
        Path testSuitesDir = null;
        for (Path p = cursor.getParent(); p != null; p = p.getParent()) {
            if ("Test Suites".equals(p.getFileName() == null ? null : p.getFileName().toString())) {
                testSuitesDir = p;
                break;
            }
        }
        if (testSuitesDir == null) return fallback;
        Path relative = testSuitesDir.relativize(cursor);
        String rel = relative.toString().replace('\\', '/');
        if (rel.endsWith(".ts")) rel = rel.substring(0, rel.length() - 3);
        return rel.isEmpty() ? fallback : rel;
    }
    
    /**
     * Pre-create the Katalon-style per-run report folder and write the
     * minimum files required by @AfterTestSuite listeners (execution.properties,
     * execution.uuid, execution0.log, testCaseBinding, console0.log).
     * This must be called BEFORE test cases run because:
     * 1. Test cases need to WRITE to execution0.log during execution (live logging)
     * 2. @AfterTestSuite listeners (CSReport, PdfGenerator) need to READ these files
     *
     * @param result ExecutionResult (with suiteResult already added)
     * @param suite TestSuite definition (for generating testCaseBinding)
     * @return the per-run report directory (created on disk)
     */
    public Path prepareReportDirectory(ExecutionResult result, com.katalan.core.model.TestSuite suite) throws IOException {
        Path reportDir = computeReportDirectory(result);
        Files.createDirectories(reportDir);
        this.currentReportDir = reportDir;
        
        String suiteRelative = resolveSuiteRelativePath(result);
        
        // CRITICAL: Generate these 4 files BEFORE test cases run!
        // Custom report listeners (CSReport, PdfGenerator) parse these files.
        try { generateExecutionProperties(reportDir, result, suiteRelative); } catch (Exception e) {
            logger.warn("Failed to write execution.properties early: {}", e.getMessage());
        }
        try { generateExecutionUuid(reportDir); } catch (Exception e) {
            logger.warn("Failed to write execution.uuid early: {}", e.getMessage());
        }
        // Create EMPTY console0.log - will be appended during test execution
        try { 
            Files.writeString(reportDir.resolve("console0.log"), ""); 
        } catch (Exception e) {
            logger.warn("Failed to create console0.log early: {}", e.getMessage());
        }
        // Create execution0.log with XML header - will be appended during test execution
        try { 
            String logHeader = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                              "<!DOCTYPE log SYSTEM \"logger.dtd\">\n" +
                              "<log>\n";
            Files.writeString(reportDir.resolve("execution0.log"), logHeader);
        } catch (Exception e) {
            logger.warn("Failed to create execution0.log early: {}", e.getMessage());
        }
        // Generate testCaseBinding from TestSuite definition (list of test cases to run)
        try { 
            generateTestCaseBindingFromSuite(reportDir, suite);
        } catch (Exception e) {
            logger.warn("Failed to create testCaseBinding early: {}", e.getMessage());
        }
        
        logger.info("Prepared Katalon-style report directory with 4 essential files: {}", reportDir);
        return reportDir;
    }
    
    /**
     * Generate Katalon-style reports
     * 
     * @param result The execution result
     * @return The path to the generated report folder
     */
    public Path generateReport(ExecutionResult result) throws IOException {
        String timestamp = TIMESTAMP_FORMATTER.format(result.getStartTime());
        
        // Create Katalon-style folder structure:
        // Reports/<timestamp>/<SuiteRelativePath>/<timestamp>/
        String suiteRelative = resolveSuiteRelativePath(result);
        
        Path reportDir = computeReportDirectory(result);
        
        Files.createDirectories(reportDir);
        this.currentReportDir = reportDir;
        
        logger.info("Generating Katalon-style reports in: {}", reportDir);
        
        // Generate all report files
        generateHtmlReport(reportDir, timestamp, result);
        generateCsvReport(reportDir, timestamp, result);
        generateExecutionProperties(reportDir, result, suiteRelative);
        generateExecutionUuid(reportDir);
        generateConsoleLog(reportDir, result);  // Generate console FIRST
        generateExecutionLog(reportDir, result);
        generateJUnitReport(reportDir, result);  // Generate JUnit AFTER console so we can read it
        generateTestCaseBinding(reportDir, result);
        generateTscIdFile(reportDir);
        
        // Generate cucumber_report folder for BDD tests
        generateCucumberReports(reportDir, result);
        
        logger.info("Katalon-style reports generated successfully at: {}", reportDir);
        
        return reportDir;
    }
    
    /**
     * Generate main HTML report
     */
    private void generateHtmlReport(Path reportDir, String timestamp, ExecutionResult result) throws IOException {
        String html = buildKatalonHtml(result, timestamp);
        Files.writeString(reportDir.resolve(timestamp + ".html"), html);
    }
    
    /**
     * Build Katalon-style HTML report with embedded execution data
     * Uses the exact same viewer as Katalon Studio for identical look and feel
     */
    private String buildKatalonHtml(ExecutionResult result, String timestamp) {
        StringBuilder html = new StringBuilder();
        
        String suiteName = result.getSuiteResults().isEmpty() ? "Test Suite" 
                : result.getSuiteResults().get(0).getSuiteName();
        
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("  <head>\n");
        html.append("    <meta charset=\"utf-8\" />\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />\n");
        html.append("    <meta name=\"theme-color\" content=\"#000000\" />\n");
        html.append("    <link\n");
        html.append("      rel=\"shortcut icon\"\n");
        html.append("      type=\"image/ico\"\n");
        html.append("      href=\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAAACXBIWXMAABYlAAAWJQFJUiTwAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAACVSURBVHgB7ZZLDYAwDEA7lCxIwQE2MMEwgQ0coASSgZFBELD1e6LvujbvnZYCOH8ngIA+xjkUSLWZ88pVRweGcgysAC05K0BTTg7QlpMCLOToACs5KsBSjgqQyN/dpTXD/gcw8uPOqTVnEoCVmwRQ5OoBVLlqAEeuFsCVqwRI5N8+CBn3tdTet2GyuQe08AAP8ADHeQDVLz0wSoDWuwAAAABJRU5ErkJggg==\"\n");
        html.append("    />\n");
        html.append("\n");
        html.append("    <title>Test Suite Report</title>\n");
        // Embed Katalon's SolidJS-based report viewer (minified)
        html.append("    <script type=\"module\" crossorigin>");
        html.append(getKatalonViewerScript());
        html.append("</script>\n");
        html.append("  </head>\n");
        html.append("  <body>\n");
        html.append("    <noscript>You need to enable JavaScript to run this app.</noscript>\n");
        html.append("    <div id=\"root\"></div>\n");
        html.append("    \n");
        
        // Embed main execution data - Katalon format
        html.append("<script id='main'>\n");
        html.append("window.addEventListener('DOMContentLoaded', () => {loadExecutionData('main', ");
        html.append(buildMainExecutionDataJson(result, suiteName));
        html.append(")})\n");
        html.append("</script>\n\n");
        
        // Embed test case data - Katalon format  
        int tcIndex = 0;
        for (TestSuiteResult suiteResult : result.getSuiteResults()) {
            for (TestCaseResult tcResult : suiteResult.getTestCaseResults()) {
                html.append("<script id='").append(tcIndex).append("'>\n");
                html.append("window.addEventListener('DOMContentLoaded', () => {loadExecutionData('").append(tcIndex).append("', ");
                html.append(buildTestCaseDataJson(tcResult));
                html.append(")})\n");
                html.append("</script>\n\n");
                tcIndex++;
            }
        }
        
        html.append("  </body>\n");
        html.append("</html>\n");
        
        return html.toString();
    }
    
    /**
     * Load Katalon's report viewer script from resources
     */
    private String getKatalonViewerScript() {
        try (InputStream is = getClass().getResourceAsStream("/report-viewer.js")) {
            if (is != null) {
                return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            logger.warn("Failed to load report-viewer.js, falling back to simple viewer", e);
        }
        // Fallback to simple viewer if resource not found
        return getKatalonRenderScript();
    }
    
    /**
     * Build main execution data JSON (like Katalon's entity structure)
     */
    private String buildMainExecutionDataJson(ExecutionResult result, String suiteName) {
        Map<String, Object> data = new LinkedHashMap<>();
        
        // Entity section
        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("entityId", "Test Suites/" + suiteName);
        
        // Context
        Map<String, Object> context = new LinkedHashMap<>();
        Map<String, Object> local = new LinkedHashMap<>();
        local.put("hostName", getHostname());
        local.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        local.put("katalonVersion", katalanVersion);
        context.put("local", local);
        
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("deviceName", "");
        target.put("deviceOSVersion", "");
        target.put("browserName", result.getBrowserName() != null ? result.getBrowserName() : "Chrome");
        context.put("target", target);
        context.put("executedBy", System.getProperty("user.name"));
        entity.put("context", context);
        
        // Statistics
        Map<String, Object> statistics = new LinkedHashMap<>();
        statistics.put("total", result.getTotalTests());
        statistics.put("passed", result.getPassedTests());
        statistics.put("failed", result.getFailedTests());
        statistics.put("errored", result.getErrorTests());
        statistics.put("warned", 0);
        statistics.put("skipped", result.getSkippedTests());
        statistics.put("notRun", 0);
        statistics.put("incomplete", 0);
        entity.put("statistics", statistics);
        
        entity.put("type", "TEST_SUITE");
        entity.put("name", suiteName);
        entity.put("description", "");
        entity.put("retryCount", 0);
        entity.put("status", "COMPLETED");
        entity.put("result", result.getFailedTests() > 0 || result.getErrorTests() > 0 ? "FAILED" : "PASSED");
        entity.put("startTime", ISO_TIMESTAMP_FORMATTER.format(ZonedDateTime.ofInstant(result.getStartTime(), ZoneId.systemDefault())));
        entity.put("endTime", ISO_TIMESTAMP_FORMATTER.format(ZonedDateTime.ofInstant(result.getEndTime(), ZoneId.systemDefault())));
        
        // Children (test cases)
        List<Map<String, Object>> children = new ArrayList<>();
        int index = 1;
        for (TestSuiteResult suiteResult : result.getSuiteResults()) {
            for (TestCaseResult tcResult : suiteResult.getTestCaseResults()) {
                Map<String, Object> child = new LinkedHashMap<>();
                child.put("entityId", tcResult.getTestCaseName());
                child.put("dataBinding", Collections.emptyList());
                child.put("dataIterationName", "");
                
                Map<String, Object> tcStats = new LinkedHashMap<>();
                tcStats.put("total", tcResult.getStepResults().size());
                tcStats.put("passed", (int) tcResult.getStepResults().stream().filter(s -> s.isPassed()).count());
                tcStats.put("failed", (int) tcResult.getStepResults().stream().filter(s -> !s.isPassed()).count());
                tcStats.put("errored", 0);
                tcStats.put("warned", 0);
                tcStats.put("skipped", 0);
                tcStats.put("notRun", 0);
                tcStats.put("incomplete", 0);
                child.put("statistics", tcStats);
                
                child.put("type", "TEST_CASE");
                child.put("name", tcResult.getTestCaseName());
                child.put("description", "");
                child.put("retryCount", 0);
                child.put("status", "COMPLETED");
                child.put("result", tcResult.getStatus().name());
                child.put("startTime", ISO_TIMESTAMP_FORMATTER.format(ZonedDateTime.ofInstant(tcResult.getStartTime(), ZoneId.systemDefault())));
                child.put("endTime", ISO_TIMESTAMP_FORMATTER.format(ZonedDateTime.ofInstant(tcResult.getEndTime(), ZoneId.systemDefault())));
                child.put("index", index);
                child.put("startIndex", 0);
                
                children.add(child);
                index++;
            }
        }
        entity.put("children", children);
        entity.put("index", 0);
        entity.put("startIndex", 1);
        entity.put("logs", Collections.emptyList());
        
        data.put("entity", entity);
        
        // Project section
        Map<String, Object> project = new LinkedHashMap<>();
        project.put("name", projectPath.getFileName().toString());
        data.put("project", project);
        
        return toJson(data);
    }
    
    /**
     * Build test case data JSON
     */
    private String buildTestCaseDataJson(TestCaseResult tcResult) {
        Map<String, Object> data = new LinkedHashMap<>();
        
        List<Map<String, Object>> children = new ArrayList<>();
        
        // Check if this is a BDD test with hierarchical scenario data
        if (tcResult.isBddTest() && tcResult.getBddScenarioData() != null && !tcResult.getBddScenarioData().isEmpty()) {
            // Build BDD hierarchical structure: runFeatureFile step containing scenarios
            Map<String, Object> runFeatureStep = new LinkedHashMap<>();
            runFeatureStep.put("type", "TEST_STEP");
            runFeatureStep.put("name", "runFeatureFile(\"" + tcResult.getFeatureFile() + "\")");
            runFeatureStep.put("description", "");
            runFeatureStep.put("retryCount", 0);
            runFeatureStep.put("status", "COMPLETED");
            runFeatureStep.put("result", tcResult.getStatus() == TestCase.TestCaseStatus.PASSED ? "PASSED" : "FAILED");
            runFeatureStep.put("startTime", ISO_TIMESTAMP_FORMATTER.format(ZonedDateTime.ofInstant(tcResult.getStartTime(), ZoneId.systemDefault())));
            runFeatureStep.put("endTime", ISO_TIMESTAMP_FORMATTER.format(ZonedDateTime.ofInstant(tcResult.getEndTime(), ZoneId.systemDefault())));
            
            // Add scenario data as children of runFeatureFile
            runFeatureStep.put("children", tcResult.getBddScenarioData());
            runFeatureStep.put("index", 0);
            runFeatureStep.put("startIndex", 0);
            
            // Logs for runFeatureFile
            List<Map<String, Object>> runLogs = new ArrayList<>();
            Map<String, Object> startLog = new LinkedHashMap<>();
            startLog.put("time", ISO_TIMESTAMP_FORMATTER.format(ZonedDateTime.ofInstant(tcResult.getStartTime(), ZoneId.systemDefault())));
            startLog.put("level", "INFO");
            startLog.put("message", "Starting run keyword runFeatureFile: '" + tcResult.getFeatureFile() + "'...");
            runLogs.add(startLog);
            
            Map<String, Object> endLog = new LinkedHashMap<>();
            endLog.put("time", ISO_TIMESTAMP_FORMATTER.format(ZonedDateTime.ofInstant(tcResult.getEndTime(), ZoneId.systemDefault())));
            endLog.put("level", tcResult.getStatus() == TestCase.TestCaseStatus.PASSED ? "PASSED" : "FAILED");
            endLog.put("message", "Feature file: '" + tcResult.getFeatureFile() + "' was " + 
                    (tcResult.getStatus() == TestCase.TestCaseStatus.PASSED ? "passed" : "failed"));
            runLogs.add(endLog);
            
            runFeatureStep.put("logs", runLogs);
            
            children.add(runFeatureStep);
        } else {
            // Fallback to flat step results for non-BDD tests
            for (TestCaseResult.StepResult step : tcResult.getStepResults()) {
                Map<String, Object> stepData = new LinkedHashMap<>();
                stepData.put("type", "TEST_STEP");
                stepData.put("name", step.getStepName());
                stepData.put("description", step.getDescription() != null ? step.getDescription() : "");
                stepData.put("retryCount", 0);
                stepData.put("status", "COMPLETED");
                stepData.put("result", step.isPassed() ? "PASSED" : "FAILED");
                
                Instant stepStart = step.getStartTime() != null ? step.getStartTime() : tcResult.getStartTime();
                Instant stepEnd = step.getEndTime() != null ? step.getEndTime() : tcResult.getEndTime();
                stepData.put("startTime", ISO_TIMESTAMP_FORMATTER.format(ZonedDateTime.ofInstant(stepStart, ZoneId.systemDefault())));
                stepData.put("endTime", ISO_TIMESTAMP_FORMATTER.format(ZonedDateTime.ofInstant(stepEnd, ZoneId.systemDefault())));
                stepData.put("children", Collections.emptyList());
                stepData.put("index", step.getStepNumber() - 1);
                stepData.put("startIndex", 0);
                
                // Logs
                List<Map<String, Object>> logs = new ArrayList<>();
                Map<String, Object> log = new LinkedHashMap<>();
                log.put("time", ISO_TIMESTAMP_FORMATTER.format(ZonedDateTime.ofInstant(stepEnd, ZoneId.systemDefault())));
                log.put("level", step.isPassed() ? "PASSED" : "FAILED");
                log.put("message", step.isPassed() ? 
                        (step.getDescription() != null ? step.getDescription() : step.getStepName()) :
                        (step.getErrorMessage() != null ? step.getErrorMessage() : "Step failed"));
                logs.add(log);
                stepData.put("logs", logs);
                
                children.add(stepData);
            }
        }
        
        data.put("children", children);
        data.put("logs", Collections.emptyList());
        
        return toJson(data);
    }
    
    /**
     * Generate CSV report
     */
    private void generateCsvReport(Path reportDir, String timestamp, ExecutionResult result) throws IOException {
        StringBuilder csv = new StringBuilder();
        
        // Header
        csv.append("Suite/Test/Step Name,Browser,Description,Tag,Start time,End time,Duration,Status\n");
        
        for (TestSuiteResult suiteResult : result.getSuiteResults()) {
            // Suite row
            csv.append(suiteResult.getSuiteName()).append(",");
            csv.append(result.getBrowserName() != null ? result.getBrowserName() : "Chrome").append(",");
            csv.append(",,"); // Description, Tag
            csv.append(REPORT_DATE_FORMATTER.format(suiteResult.getStartTime())).append(",");
            csv.append(REPORT_DATE_FORMATTER.format(suiteResult.getEndTime())).append(",");
            csv.append(formatDurationCsv(suiteResult.getDuration())).append(",");
            csv.append(suiteResult.isSuccess() ? "PASSED" : "FAILED").append("\n");
            
            // Empty row
            csv.append(",,,,,,,\n");
            
            for (TestCaseResult tcResult : suiteResult.getTestCaseResults()) {
                // Test case row
                csv.append(tcResult.getTestCaseName()).append(",");
                csv.append(result.getBrowserName() != null ? result.getBrowserName() : "Chrome").append(",");
                csv.append(",,"); // Description, Tag
                csv.append(REPORT_DATE_FORMATTER.format(tcResult.getStartTime())).append(",");
                csv.append(REPORT_DATE_FORMATTER.format(tcResult.getEndTime())).append(",");
                csv.append(tcResult.getDurationFormatted()).append(",");
                csv.append(tcResult.getStatus()).append("\n");
            }
        }
        
        Files.writeString(reportDir.resolve(timestamp + ".csv"), csv.toString());
    }
    
    /**
     * Generate JUnit XML report (Katalon-compatible format)
     */
    private void generateJUnitReport(Path reportDir, ExecutionResult result) throws IOException {
        StringBuilder xml = new StringBuilder();

        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");

        String suiteName = result.getSuiteResults().isEmpty() ? "Test Suite"
                : result.getSuiteResults().get(0).getSuiteName();

        double totalTime = result.getDuration() != null ? result.getDuration().toMillis() / 1000.0 : 0;

        xml.append("<testsuites name=\"").append(escapeXml(suiteName)).append("\" ");
        xml.append("time=\"").append(String.format("%.3f", totalTime)).append("\" ");
        xml.append("tests=\"").append(result.getTotalTests()).append("\" ");
        xml.append("failures=\"").append(result.getFailedTests()).append("\" ");
        xml.append("errors=\"").append(result.getErrorTests()).append("\">\n");

        // Pre-compute shared values used by properties block
        String suiteRelative = resolveSuiteRelativePath(result);
        String suiteId = "Test Suites/" + suiteRelative;
        String reportFolder = reportDir.toAbsolutePath().normalize().toString().replace("\\", "/");
        String logFiles = reportFolder + "/execution0.log, " + reportFolder + "/console0.log";
        String rawHost = getHostname();
        String userName = System.getProperty("user.name", "");
        String hostNameProp = (userName == null || userName.isEmpty())
                ? rawHost
                : userName + " - " + rawHost;
        String hostAddress = getHostAddress();
        String osBitness = System.getProperty("sun.arch.data.model", "64") + "bit";
        String osLabel = System.getProperty("os.name") + " " + osBitness;

        for (TestSuiteResult suiteResult : result.getSuiteResults()) {
            double suiteTime = suiteResult.getDuration() != null ? suiteResult.getDuration().toMillis() / 1000.0 : 0;

            xml.append("   <testsuite name=\"").append(escapeXml(suiteResult.getSuiteName())).append("\" ");
            xml.append("tests=\"").append(suiteResult.getTotalTests()).append("\" ");
            xml.append("failures=\"").append(suiteResult.getFailedTests()).append("\" ");
            xml.append("errors=\"").append(suiteResult.getErrorTests()).append("\" ");
            xml.append("time=\"").append(String.format("%.3f", suiteTime)).append("\" ");
            xml.append("skipped=\"").append(suiteResult.getSkippedTests()).append("\" ");
            xml.append("timestamp=\"")
               .append(JUNIT_ISO_UTC_TIMESTAMP_FORMATTER.format(suiteResult.getStartTime()))
               .append("\" ");
            xml.append("hostname=\"").append(escapeXml(hostNameProp)).append("\" ");
            xml.append("id=\"").append(escapeXml(suiteId)).append("\">\n");

            // Properties (Katalon-compatible layout - EXACT order from Katalon 10.3.2)
            xml.append("      <properties>\n");
            xml.append("         <property name=\"deviceName\" value=\"\"/>\n");
            xml.append("         <property name=\"devicePlatform\"/>\n");
            xml.append("         <property name=\"logFolder\" value=\"")
               .append(escapeXml(reportFolder)).append("\"/>\n");
            xml.append("         <property name=\"logFiles\" value=\"")
               .append(escapeXml(logFiles)).append("\"/>\n");
            
            // Collect all screenshot attachments from the report directory
            String attachments = collectScreenshotAttachments(reportDir);
            xml.append("         <property name=\"attachments\" value=\"")
               .append(escapeXml(attachments)).append("\"/>\n");
            
            // userFullName comes BEFORE hostName in Katalon
            xml.append("         <property name=\"userFullName\" value=\"")
               .append(escapeXml(resolveUserFullName())).append("\"/>\n");
            
            xml.append("         <property name=\"hostName\" value=\"")
               .append(escapeXml(hostNameProp)).append("\"/>\n");
            xml.append("         <property name=\"hostAddress\" value=\"")
               .append(escapeXml(hostAddress)).append("\"/>\n");
            xml.append("         <property name=\"projectName\" value=\"")
               .append(escapeXml(resolveProjectName())).append("\"/>\n");
            xml.append("         <property name=\"os\" value=\"")
               .append(escapeXml(osLabel)).append("\"/>\n");
            xml.append("         <property name=\"katalonVersion\" value=\"")
               .append(escapeXml(katalanVersion)).append("\"/>\n");
            
            // Browser version (e.g. "Chrome 147.0.7727.57") - from ExecutionResult
            String browserInfo = getBrowserInfo(result);
            if (browserInfo != null && !browserInfo.isEmpty()) {
                xml.append("         <property name=\"browser\" value=\"")
                   .append(escapeXml(browserInfo)).append("\"/>\n");
            }
            
            // SessionId (WebDriver session ID from ExecutionResult)
            String sessionId = result.getSessionId();
            if (sessionId != null && !sessionId.isEmpty()) {
                xml.append("         <property name=\"sessionId\" value=\"")
                   .append(escapeXml(sessionId)).append("\"/>\n");
            }
            
            // Selenium version - from ExecutionResult
            if (browserInfo != null && !browserInfo.isEmpty()) {
                String seleniumVer = result.getSeleniumVersion();
                if (seleniumVer != null && !seleniumVer.isEmpty()) {
                    xml.append("         <property name=\"seleniumVersion\" value=\"")
                       .append(escapeXml(seleniumVer)).append("\"/>\n");
                }
            }
            
            // Proxy information - from ExecutionResult
            String proxyInfo = result.getProxyInformation();
            if (proxyInfo != null && !proxyInfo.isEmpty()) {
                xml.append("         <property name=\"proxyInformation\" value=\"")
                   .append(escapeXml(proxyInfo)).append("\"/>\n");
            }
            
            // Platform (e.g. "Mac OS X") - from ExecutionResult
            String platformName = result.getPlatformName();
            if (platformName != null && !platformName.isEmpty()) {
                xml.append("         <property name=\"platform\" value=\"")
                   .append(escapeXml(platformName)).append("\"/>\n");
            }
            
            xml.append("      </properties>\n");

            for (TestCaseResult tcResult : suiteResult.getTestCaseResults()) {
                double tcTime = tcResult.getDuration().toMillis() / 1000.0;
                
                // Use testCaseId (full path with "Test Cases/" prefix) for JUnit XML
                String testCaseFullPath = tcResult.getTestCaseId();

                xml.append("      <testcase name=\"").append(escapeXml(testCaseFullPath)).append("\" ");
                xml.append("time=\"").append(String.format("%.3f", tcTime)).append("\" ");
                xml.append("classname=\"").append(escapeXml(testCaseFullPath)).append("\" ");
                xml.append("status=\"").append(tcResult.getStatus()).append("\">\n");

                if (tcResult.getStatus() == TestCase.TestCaseStatus.FAILED) {
                    xml.append("         <failure message=\"").append(escapeXml(tcResult.getErrorMessage())).append("\">\n");
                    if (tcResult.getStackTrace() != null) {
                        xml.append(escapeXml(tcResult.getStackTrace())).append("\n");
                    }
                    xml.append("         </failure>\n");
                } else if (tcResult.getStatus() == TestCase.TestCaseStatus.ERROR) {
                    // Katalon format: "Test Cases/X FAILED.\nReason:\n<error>\n<stacktrace>"
                    // Build full error message with stack trace
                    StringBuilder fullError = new StringBuilder();
                    fullError.append(testCaseFullPath).append(" FAILED.\n");
                    fullError.append("Reason:\n");
                    
                    // Extract root cause from stack trace (skip RuntimeException wrapper)
                    String errorMsg = tcResult.getErrorMessage();
                    String stackTrace = tcResult.getStackTrace();
                    
                    // If error message starts with "Script execution failed:", extract the actual error
                    if (errorMsg != null && errorMsg.startsWith("Script execution failed: ")) {
                        errorMsg = errorMsg.substring("Script execution failed: ".length());
                    }
                    
                    if (errorMsg != null) {
                        fullError.append(errorMsg);
                    }
                    
                    // Extract "Caused by:" section from stack trace if present
                    if (stackTrace != null && !stackTrace.isEmpty()) {
                        if (errorMsg != null && !errorMsg.endsWith("\n")) {
                            fullError.append("\n");
                        }
                        
                        // Find "Caused by:" section which contains the real root cause
                        if (stackTrace.contains("Caused by: ")) {
                            String causedBySection = stackTrace.substring(stackTrace.indexOf("Caused by: "));
                            fullError.append(causedBySection);
                        } else {
                            fullError.append(stackTrace);
                        }
                    }
                    
                    // Escape XML and encode newlines as &#xa;
                    String finalErrorMsg = escapeXml(fullError.toString()).replace("\n", "&#xa;");
                    xml.append("         <error type=\"ERROR\" message=\"").append(finalErrorMsg).append("\"/>\n");
                } else if (tcResult.getStatus() == TestCase.TestCaseStatus.SKIPPED) {
                    xml.append("         <skipped/>\n");
                }

                // Build testcase log once to reuse for system-out and system-err
                String testCaseLog = buildTestCaseLog(tcResult);
                
                // System output (test steps log)
                xml.append("         <system-out><![CDATA[");
                xml.append(testCaseLog);
                xml.append("]]></system-out>\n");
                
                // System error: For ERROR testcases, extract only ERROR-level lines from log
                // (Katalon duplicates error content in system-err)
                xml.append("         <system-err><![CDATA[");
                if (tcResult.getStatus() == TestCase.TestCaseStatus.ERROR) {
                    xml.append(extractErrorLines(testCaseLog));
                }
                xml.append("]]></system-err>\n");

                xml.append("      </testcase>\n");
            }

            // Suite-level <system-out>/<system-err> (Katalon emits listener logs
            // here, e.g. beforeSuite/afterTestSuite actions). We emit at minimum
            // the TEST_SUITE status marker in the same format so downstream
            // tooling can parse it.
            String suiteLog = buildSuiteLog(suiteResult);
            xml.append("      <system-out><![CDATA[");
            xml.append(suiteLog);
            xml.append("]]></system-out>\n");
            
            // Suite system-err: Extract ERROR-level lines from suite log
            xml.append("      <system-err><![CDATA[");
            xml.append(extractErrorLines(suiteLog));
            xml.append("]]></system-err>\n");

            xml.append("   </testsuite>\n");
        }

        xml.append("</testsuites>\n");

        Files.writeString(reportDir.resolve("JUnit_Report.xml"), xml.toString());
    }
    
    /**
     * Generate execution.properties file (JSON format, matching Katalon Studio 10.3.2).
     * Despite the .properties extension, Katalon writes this file as JSON.
     * Custom scripts (PdfGenerator, CSReport) parse it with JsonSlurper.
     * 
     * CRITICAL: Property order MUST match Katalon exactly for compatibility.
     */
    private void generateExecutionProperties(Path reportDir, ExecutionResult result, String suiteName) throws IOException {
        Map<String, Object> properties = new LinkedHashMap<>();
        
        String reportFolder = reportDir.toAbsolutePath().normalize().toString().replace("\\", "/");
        String browserName = result.getBrowserName() != null ? result.getBrowserName() : "Chrome";
        
        // projectDir: MUST be absolute path
        String projectDir = projectPath.toAbsolutePath().normalize().toString().replace("\\", "/");
        
        // projectName: from .prj file (NOT folder name)
        String projectName = findProjectName(projectPath);
        
        String userFullName = resolveUserFullName();
        String systemUserName = System.getProperty("user.name", "");
        String rawHostName = getHostname();
        // hostName uses SYSTEM username, not fullName
        String hostNameWithUser = systemUserName.isEmpty() ? rawHostName : systemUserName + " - " + rawHostName;
        String osBitness = System.getProperty("sun.arch.data.model", "64") + "bit";
        String os = System.getProperty("os.name") + " " + osBitness;
        String hostAddress = getHostAddress();
        
        // sourcePath: MUST be absolute path to .ts file
        String sourcePath = projectPath.toAbsolutePath().normalize()
                .resolve("Test Suites").resolve(suiteName + ".ts")
                .toString().replace("\\", "/");
        String executionProfile = com.kms.katalon.core.configuration.RunConfiguration.getExecutionProfile();
        
        // ===== EXACT Katalon property order (CRITICAL!) =====
        // 1. Name (browser)
        properties.put("Name", browserName);
        
        // 2. userFullName
        properties.put("userFullName", userFullName);
        
        // 3. projectName
        properties.put("projectName", projectName);
        
        // 4. projectDir
        properties.put("projectDir", projectDir);
        
        // 5. host object
        Map<String, Object> host = new LinkedHashMap<>();
        host.put("hostName", hostNameWithUser);
        host.put("os", os);
        host.put("hostPort", 0); // Katalon sets this dynamically
        host.put("hostAddress", hostAddress);
        properties.put("host", host);
        
        // 6. execution object
        Map<String, Object> execution = new LinkedHashMap<>();
        Map<String, Object> general = new LinkedHashMap<>();
        
        general.put("autoApplyNeighborXpaths", false);
        general.put("ignorePageLoadTimeoutException", false);
        general.put("timeCapsuleEnabled", false);
        general.put("executionProfile", executionProfile);
        
        // excludeKeywords - Katalon has long list of verify/wait keywords
        java.util.List<String> excludeKeywords = java.util.Arrays.asList(
            "verifyOptionNotPresentByLabel", "verifyOptionNotPresentByValue", "verifyOptionNotSelectedByIndex",
            "verifyOptionNotSelectedByLabel", "verifyOptionNotSelectedByValue", "verifyOptionPresentByLabel",
            "verifyOptionPresentByValue", "verifyOptionSelectedByIndex", "verifyOptionSelectedByLabel",
            "verifyOptionSelectedByValue", "verifyOptionsPresent", "verifyTextNotPresent", "verifyTextPresent",
            "verifyElementAttributeValue", "verifyElementChecked", "verifyElementClickable", "verifyElementHasAttribute",
            "verifyElementNotChecked", "verifyElementNotClickable", "verifyElementNotHasAttribute", "verifyElementNotPresent",
            "verifyElementNotVisibleInViewport", "verifyElementNotVisible", "verifyElementPresent", "verifyElementText",
            "verifyElementVisibleInViewport", "verifyElementVisible", "waitForElementAttributeValue", "waitForElementClickable",
            "waitForElementHasAttribute", "waitForElementNotClickable", "waitForElementNotHasAttribute",
            "waitForElementNotPresent", "waitForElementNotVisible", "waitForElementPresent", "waitForElementVisible"
        );
        general.put("excludeKeywords", excludeKeywords);
        
        general.put("canvasTextExtractionEnabled", false);
        general.put("flutterAppTestingEnabled", false);
        
        // xpathsPriority
        java.util.List<Map<String, Object>> xpathsPriority = new java.util.ArrayList<>();
        xpathsPriority.add(createPair("xpath:attributes", true));
        xpathsPriority.add(createPair("xpath:idRelative", true));
        xpathsPriority.add(createPair("dom:name", true));
        xpathsPriority.add(createPair("xpath:link", true));
        xpathsPriority.add(createPair("xpath:neighbor", true));
        xpathsPriority.add(createPair("xpath:href", true));
        xpathsPriority.add(createPair("xpath:img", true));
        xpathsPriority.add(createPair("xpath:position", true));
        xpathsPriority.add(createPair("xpath:customAttributes", true));
        general.put("xpathsPriority", xpathsPriority);
        
        general.put("timeout", 30);
        general.put("actionDelay", 0);
        
        // methodsPriorityOrder
        java.util.List<Map<String, Object>> methodsPriority = new java.util.ArrayList<>();
        methodsPriority.add(createPair("XPATH", true));
        methodsPriority.add(createPair("SMART_LOCATOR", true));
        methodsPriority.add(createPair("BASIC", true));
        methodsPriority.add(createPair("CSS", true));
        methodsPriority.add(createPair("IMAGE", true));
        general.put("methodsPriorityOrder", methodsPriority);
        
        // proxy (as JSON string!)
        general.put("proxy", "{\"proxyOption\":\"NO_PROXY\",\"proxyServerType\":\"HTTP\",\"username\":\"\",\"password\":\"\",\"proxyServerAddress\":\"\",\"proxyServerPort\":0,\"exceptionList\":\"\",\"applyToDesiredCapabilities\":true}");
        
        general.put("defaultFailureHandling", "STOP_ON_FAILURE");
        general.put("terminateDriverAfterTestCase", false);
        general.put("defaultPageLoadTimeout", 30);
        general.put("closedShadowDOMEnabled", false);
        
        // report object
        Map<String, Object> report = new LinkedHashMap<>();
        Map<String, Object> takeScreenshot = new LinkedHashMap<>();
        takeScreenshot.put("enable", false);
        report.put("takeScreenshotSettings", takeScreenshot);
        
        Map<String, Object> videoRecorder = new LinkedHashMap<>();
        videoRecorder.put("enable", false);
        videoRecorder.put("useBrowserRecorder", true);
        videoRecorder.put("videoFormat", "WEBM");
        videoRecorder.put("videoQuality", "LOW");
        videoRecorder.put("recordAllTestCases", true);
        report.put("videoRecorderSettings", videoRecorder);
        
        report.put("screenCaptureOption", false);
        report.put("reportFolder", reportFolder);
        general.put("report", report);
        
        general.put("enablePageLoadTimeout", false);
        general.put("terminateDriverAfterTestSuite", false);
        general.put("useActionDelayInSecond", "SECONDS");
        general.put("testDataInfo", new LinkedHashMap<>());
        general.put("selfHealingEnabled", false);
        execution.put("general", general);
        
        // drivers object
        Map<String, Object> drivers = new LinkedHashMap<>();
        Map<String, Object> driversSystem = new LinkedHashMap<>();
        Map<String, Object> webUiDriver = new LinkedHashMap<>();
        // chromeDriverPath - hardcoded Katalon path for compatibility
        webUiDriver.put("chromeDriverPath", "/Applications/Katalon Studio.app/Contents/Eclipse/configuration/resources/drivers/chromedriver_mac/chromedriver");
        webUiDriver.put("browserType", "CHROME_DRIVER");
        driversSystem.put("WebUI", webUiDriver);
        drivers.put("system", driversSystem);
        
        Map<String, Object> driversPrefs = new LinkedHashMap<>();
        driversPrefs.put("WebUI", new LinkedHashMap<>());
        drivers.put("preferences", driversPrefs);
        execution.put("drivers", drivers);
        
        execution.put("globalSmartWaitEnabled", false);
        execution.put("smartLocatorEnabled", false);
        execution.put("smartLocatorSettingDefaultEnabled", true);
        execution.put("logTestSteps", true);
        execution.put("hideHostname", false);
        properties.put("execution", execution);
        
        // 7. executedEntity
        properties.put("executedEntity", "TestSuite");
        
        // 8. id
        properties.put("id", "Test Suites/" + suiteName);
        
        // 9. name (just the suite name, without folder path)
        String justSuiteName = suiteName.contains("/") ? suiteName.substring(suiteName.lastIndexOf('/') + 1) : suiteName;
        properties.put("name", justSuiteName);
        
        // 10. description
        properties.put("description", "");
        
        // 11. source
        properties.put("source", sourcePath);
        
        // 12. sessionServer.host & port
        properties.put("sessionServer.host", "localhost");
        properties.put("sessionServer.port", 0);
        
        // 13. isDebugLaunchMode
        properties.put("isDebugLaunchMode", false);
        
        // 14. logbackConfigFileLocation
        properties.put("logbackConfigFileLocation", "/Applications/Katalon Studio.app/Contents/Eclipse/configuration/org.eclipse.osgi/125/0/.cp/resources/logback/logback-console.xml");
        
        // 15. katalon.versionNumber (WITHOUT .0 suffix)
        properties.put("katalon.versionNumber", "10.3.2");
        
        // 16. katalon.buildNumber
        properties.put("katalon.buildNumber", "0");
        
        // 17. runningMode (GUI not CLI!)
        properties.put("runningMode", "GUI");
        
        // 18. pluginTestListeners
        properties.put("pluginTestListeners", new java.util.ArrayList<>());
        
        // 19. allow* flags
        properties.put("allowUsingSelfHealing", false);
        properties.put("allowUsingTimeCapsule", true);
        properties.put("allowCustomizeRequestTimeout", false);
        properties.put("allowCustomizeRequestResponseSizeLimit", false);
        properties.put("allowMobileImageBasedTesting", false);
        
        // 20. maxFailedTests
        properties.put("maxFailedTests", -1);
        
        // 21. testops
        properties.put("testops", new LinkedHashMap<>());
        
        // Write as JSON
        String json = toJson(properties);
        Files.writeString(reportDir.resolve("execution.properties"), json);
    }
    
    /**
     * Generate execution.uuid file
     */
    private void generateExecutionUuid(Path reportDir) throws IOException {
        String uuid = UUID.randomUUID().toString();
        Files.writeString(reportDir.resolve("execution.uuid"), uuid);
    }
    
    /**
     * Generate console log file
     */
    private void generateConsoleLog(Path reportDir, ExecutionResult result) throws IOException {
        StringBuilder log = new StringBuilder();
        
        String suiteName = result.getSuiteResults().isEmpty() ? "Test Suite" 
                : result.getSuiteResults().get(0).getSuiteName();
        
        // Suite start
        log.append(LOG_TIMESTAMP_FORMATTER.format(result.getStartTime()));
        log.append(" INFO  c.k.katalan.core.main.TestSuiteExecutor  - START ").append(suiteName).append("\n");
        
        log.append(LOG_TIMESTAMP_FORMATTER.format(result.getStartTime()));
        log.append(" INFO  c.k.katalan.core.main.TestSuiteExecutor  - projectName = ").append(projectPath.getFileName()).append("\n");
        
        log.append(LOG_TIMESTAMP_FORMATTER.format(result.getStartTime()));
        log.append(" INFO  c.k.katalan.core.main.TestSuiteExecutor  - hostName = ").append(getHostname()).append("\n");
        
        log.append(LOG_TIMESTAMP_FORMATTER.format(result.getStartTime()));
        log.append(" INFO  c.k.katalan.core.main.TestSuiteExecutor  - os = ").append(System.getProperty("os.name")).append("\n");
        
        log.append(LOG_TIMESTAMP_FORMATTER.format(result.getStartTime()));
        log.append(" INFO  c.k.katalan.core.main.TestSuiteExecutor  - katalanVersion = ").append(katalanVersion).append("\n");
        
        for (TestSuiteResult suiteResult : result.getSuiteResults()) {
            for (TestCaseResult tcResult : suiteResult.getTestCaseResults()) {
                log.append(LOG_TIMESTAMP_FORMATTER.format(tcResult.getStartTime()));
                log.append(" INFO  c.k.katalan.core.main.TestCaseExecutor   - --------------------\n");
                
                log.append(LOG_TIMESTAMP_FORMATTER.format(tcResult.getStartTime()));
                log.append(" INFO  c.k.katalan.core.main.TestCaseExecutor   - START Test Cases/").append(tcResult.getTestCaseName()).append("\n");
                
                // Insert the CAPTURED CONSOLE OUTPUT from test execution
                String consoleOutput = tcResult.getConsoleOutput();
                if (consoleOutput != null && !consoleOutput.trim().isEmpty()) {
                    log.append(consoleOutput);
                    // Ensure it ends with newline
                    if (!consoleOutput.endsWith("\n")) {
                        log.append("\n");
                    }
                }
                
                log.append(LOG_TIMESTAMP_FORMATTER.format(tcResult.getEndTime()));
                log.append(" INFO  c.k.katalan.core.main.TestCaseExecutor   - END Test Cases/").append(tcResult.getTestCaseName());
                log.append(" - ").append(tcResult.getStatus()).append("\n");
            }
        }
        
        log.append(LOG_TIMESTAMP_FORMATTER.format(result.getEndTime()));
        log.append(" INFO  c.k.katalan.core.main.TestSuiteExecutor  - END ").append(suiteName).append("\n");
        
        Files.writeString(reportDir.resolve("console0.log"), log.toString());
    }
    
    /**
     * Generate execution0.log in Katalon's XmlKeywordLogger format.
     *
     * Structure:
     *   <?xml version="1.0" encoding="UTF-8" standalone="no"?>
     *   <!DOCTYPE log SYSTEM "logger.dtd">
     *   <log>
     *     <record>... startSuite ...</record>
     *     <record>... logRunData  (userFullName, projectName, hostName, os, hostAddress, katalonVersion) ...</record>
     *     for each test case:
     *       <record>... startTest ...</record>
     *       for each step:
     *         <record>... startKeyword (Start action : <stepName>) ...</record>
     *         [<record>... logMessage PASSED/FAILED ...</record>]
     *         <record>... endKeyword (End action : <stepName>) ...</record>
     *       <record>... endTest ...</record>
     *     <record>... endSuite ...</record>
     *   </log>
     */
    private void generateExecutionLog(Path reportDir, ExecutionResult result) throws IOException {
        StringBuilder log = new StringBuilder();
        log.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
        log.append("<!DOCTYPE log SYSTEM \"logger.dtd\">\n");
        log.append("<log>\n");

        // Get buffered records from XmlKeywordLogger
        com.katalan.core.logging.XmlKeywordLogger logger = com.katalan.core.logging.XmlKeywordLogger.getInstance();
        java.util.List<com.katalan.core.logging.XmlKeywordLogger.LogRecord> records = logger.getRecords();
        
        // Emit all buffered records
        for (com.katalan.core.logging.XmlKeywordLogger.LogRecord rec : records) {
            log.append("<record>\n");
            log.append("  <date>").append(LOG_RECORD_DATE_FORMATTER.format(rec.timestamp)).append("</date>\n");
            log.append("  <millis>").append(rec.timestamp.toEpochMilli()).append("</millis>\n");
            log.append("  <nanos>").append(rec.timestamp.getNano() % 1_000_000L).append("</nanos>\n");
            log.append("  <sequence>").append(rec.sequence).append("</sequence>\n");
            log.append("  <level>").append(rec.level).append("</level>\n");
            log.append("  <class>").append(LOG_RECORD_CLASS).append("</class>\n");
            log.append("  <method>").append(rec.method).append("</method>\n");
            log.append("  <thread>1</thread>\n");
            log.append("  <message>").append(escapeXml(rec.message != null ? rec.message : "")).append("</message>\n");
            
            // Add <exception> element for FAILED records (Katalon format)
            if ("FAILED".equals(rec.level) && rec.properties != null) {
                String exceptionMessage = rec.properties.get("failed.exception.message");
                String exceptionStackTrace = rec.properties.get("failed.exception.stacktrace");
                
                if (exceptionMessage != null && !exceptionMessage.isEmpty()) {
                    log.append("  <exception>\n");
                    log.append("    <message>").append(escapeXml(exceptionMessage)).append("</message>\n");
                    
                    // Parse stacktrace and generate <frame> elements
                    if (exceptionStackTrace != null && !exceptionStackTrace.isEmpty()) {
                        String[] lines = exceptionStackTrace.split("\n");
                        for (String line : lines) {
                            line = line.trim();
                            // Match "at com.example.Class.method(File.java:123)" format
                            if (line.startsWith("at ")) {
                                String frameInfo = line.substring(3); // Remove "at "
                                int methodEnd = frameInfo.indexOf('(');
                                if (methodEnd > 0) {
                                    String classAndMethod = frameInfo.substring(0, methodEnd);
                                    int lastDot = classAndMethod.lastIndexOf('.');
                                    String className = lastDot > 0 ? classAndMethod.substring(0, lastDot) : classAndMethod;
                                    String methodName = lastDot > 0 ? classAndMethod.substring(lastDot + 1) : "";
                                    
                                    // Extract line number from (File.java:123)
                                    String lineInfo = frameInfo.substring(methodEnd);
                                    int colonIndex = lineInfo.lastIndexOf(':');
                                    String lineNumber = "";
                                    if (colonIndex > 0) {
                                        String lineNumPart = lineInfo.substring(colonIndex + 1);
                                        int parenIndex = lineNumPart.indexOf(')');
                                        if (parenIndex > 0) {
                                            lineNumber = lineNumPart.substring(0, parenIndex);
                                        }
                                    }
                                    
                                    log.append("    <frame>\n");
                                    log.append("      <class>").append(escapeXml(className)).append("</class>\n");
                                    log.append("      <method>").append(escapeXml(methodName)).append("</method>\n");
                                    if (!lineNumber.isEmpty() && !lineNumber.equals("Native Method")) {
                                        try {
                                            log.append("      <line>").append(lineNumber).append("</line>\n");
                                        } catch (NumberFormatException ignored) {
                                            // Skip if not a number
                                        }
                                    }
                                    log.append("    </frame>\n");
                                }
                            }
                        }
                    }
                    
                    log.append("  </exception>\n");
                }
            }
            
            log.append("  <nestedLevel>").append(rec.nestedLevel).append("</nestedLevel>\n");
            log.append("  <escapedJava>false</escapedJava>\n");
            if (rec.properties != null) {
                for (java.util.Map.Entry<String, String> e : rec.properties.entrySet()) {
                    log.append("  <property name=\"").append(escapeXml(e.getKey())).append("\">")
                       .append(escapeXml(e.getValue() != null ? e.getValue() : ""))
                       .append("</property>\n");
                }
            }
            log.append("</record>\n");
        }

        log.append("</log>\n");
        Files.writeString(reportDir.resolve("execution0.log"), log.toString());
    }

    /**
     * FLUSH execution0.log BEFORE @AfterTestSuite runs.
     * Custom report listeners (CSReport, PdfGenerator) need to READ execution0.log,
     * so we must write all buffered XmlKeywordLogger records NOW.
     */
    public void flushExecutionLog(Path reportDir, ExecutionResult result) throws IOException {
        generateExecutionLog(reportDir, result);
    }
    
    /**
     * FLUSH cucumber reports (cucumber.json, k-cucumber.json, cucumber.xml) BEFORE @AfterTestSuite runs.
     * Custom report listeners (CSReport) need to READ these files,
     * so we must write them NOW before listeners execute.
     */
    public void flushCucumberReports(Path reportDir, ExecutionResult result) throws IOException {
        generateCucumberReports(reportDir, result);
    }

    /**
     * Append a single Katalon-style <record> entry to the execution log buffer.
     */
    private void appendLogRecord(StringBuilder log, int[] seq, Instant when,
                                 String level, String method, int thread,
                                 String message, int nestedLevel,
                                 Map<String, String> properties) {
        Instant ts = when != null ? when : Instant.now();
        long millis = ts.toEpochMilli();
        // Remaining sub-millisecond portion expressed as nanoseconds (Katalon does the same).
        long nanos = (ts.getNano() % 1_000_000L);

        log.append("<record>\n");
        log.append("  <date>").append(LOG_RECORD_DATE_FORMATTER.format(ts)).append("</date>\n");
        log.append("  <millis>").append(millis).append("</millis>\n");
        log.append("  <nanos>").append(nanos).append("</nanos>\n");
        log.append("  <sequence>").append(seq[0]++).append("</sequence>\n");
        log.append("  <level>").append(level).append("</level>\n");
        log.append("  <class>").append(LOG_RECORD_CLASS).append("</class>\n");
        log.append("  <method>").append(method).append("</method>\n");
        log.append("  <thread>").append(thread).append("</thread>\n");
        log.append("  <message>").append(escapeXml(message != null ? message : "")).append("</message>\n");
        log.append("  <nestedLevel>").append(nestedLevel).append("</nestedLevel>\n");
        log.append("  <escapedJava>false</escapedJava>\n");
        if (properties != null) {
            for (Map.Entry<String, String> e : properties.entrySet()) {
                log.append("  <property name=\"").append(escapeXml(e.getKey())).append("\">")
                   .append(escapeXml(e.getValue() != null ? e.getValue() : ""))
                   .append("</property>\n");
            }
        }
        log.append("</record>\n");
    }

    /**
     * Emit a RUN_DATA record in Katalon's exact format:
     *   <level>RUN_DATA</level>
     *   <method>logMessage</method>
     *   <message>name = value</message>
     *   <property name="KEY">VALUE</property>
     * (i.e. a single property whose XML attribute name IS the data key and whose
     * body is the value — NOT two properties named "name"/"value".)
     */
    private void appendRunDataRecord(StringBuilder log, int[] seq, Instant when,
                                     String name, String value) {
        String safeValue = value != null ? value : "";
        Map<String, String> props = new LinkedHashMap<>();
        props.put(name, safeValue);
        appendLogRecord(log, seq, when, "RUN_DATA", "logMessage", 1,
                name + " = " + safeValue, 0, props);
    }
    
    /**
     * Generate testCaseBinding file from TestSuite definition (BEFORE test cases run).
     * This lists all test cases that WILL BE executed, not the results.
     */
    public void generateTestCaseBindingFromSuite(Path reportDir, com.katalan.core.model.TestSuite suite) throws IOException {
        StringBuilder binding = new StringBuilder();
        
        for (com.katalan.core.model.TestCase testCase : suite.getTestCases()) {
            String tcId = testCase.getId() != null ? testCase.getId() : testCase.getName();
            // Ensure ID starts with "Test Cases/" if it doesn't already
            if (!tcId.startsWith("Test Cases/")) {
                tcId = "Test Cases/" + tcId;
            }
            binding.append("{\"testCaseName\":\"").append(escapeJson(tcId))
                   .append("\",\"testCaseId\":\"").append(escapeJson(tcId))
                   .append("\",\"iterationVariableName\":\"\"}\n");
        }
        
        Files.writeString(reportDir.resolve("testCaseBinding"), binding.toString());
    }
    
    /**
     * Generate testCaseBinding file from ExecutionResult (AFTER test cases complete).
     * This is kept for compatibility but generateTestCaseBindingFromSuite() should be preferred.
     */
    private void generateTestCaseBinding(Path reportDir, ExecutionResult result) throws IOException {
        StringBuilder binding = new StringBuilder();
        
        for (TestSuiteResult suiteResult : result.getSuiteResults()) {
            for (TestCaseResult tcResult : suiteResult.getTestCaseResults()) {
                String tcName = tcResult.getTestCaseName() == null ? "" : tcResult.getTestCaseName();
                String tcId = tcResult.getTestCaseId() != null ? tcResult.getTestCaseId() : tcName;
                binding.append("{\"testCaseName\":\"").append(escapeJson(tcId))
                       .append("\",\"testCaseId\":\"").append(escapeJson(tcId))
                       .append("\",\"iterationVariableName\":\"\"}\n");
            }
        }
        
        Files.writeString(reportDir.resolve("testCaseBinding"), binding.toString());
    }
    
    /**
     * Update testCaseBinding file AFTER test cases have completed.
     * Called by KatalanEngine after test case loop finishes but before @AfterTestSuite.
     */
    public void updateTestCaseBinding(Path reportDir, ExecutionResult result) throws IOException {
        generateTestCaseBinding(reportDir, result);
    }
    
    /**
     * Escape a string for safe inclusion in a JSON value.
     */
    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
    
    /**
     * Generate tsc_id.txt file (empty for non-test suite collection runs)
     */
    private void generateTscIdFile(Path reportDir) throws IOException {
        Files.writeString(reportDir.resolve("tsc_id.txt"), "");
    }
    
    /**
     * Build test case log for JUnit system-out (Katalon format)
     * Format: DD-MM-YYYYTHH:MM:SS - [LEVEL][STATUS] - message: detail
     */
    private String buildTestCaseLog(TestCaseResult tcResult) {
        // Read from console0.log (already generated) and extract logs for this test case
        Path consoleLog = currentReportDir.resolve("console0.log");
        if (!Files.exists(consoleLog)) {
            // Fallback: no console log yet
            return tcResult.getTestCaseName() + "\n";
        }
        
        try {
            String consoleContent = Files.readString(consoleLog);
            StringBuilder tcLog = new StringBuilder();
            
            // First line: TEST_CASE entry (Katalon always starts with this)
            String tcName = tcResult.getTestCaseName();
            // tcName is just "0 - Catatan Badru", need to add "Test Cases/" prefix for display
            String tcPath = "Test Cases/" + tcName;
            String tcStatus = tcResult.getStatus().toString();
            String tcTimestamp = JUNIT_TIMESTAMP_FORMATTER.format(ZonedDateTime.ofInstant(tcResult.getStartTime(), ZoneId.systemDefault()));
            tcLog.append(tcTimestamp).append(" - [TEST_CASE][").append(tcStatus).append("] - ")
                 .append(tcPath).append(": ").append(tcPath).append("\n\n");
            
            // Parse console log and extract lines between START and END of this test case
            // Console log has: "START Test Cases/0 - Catatan Badru" so search for full path
            String startMarker = "START Test Cases/" + tcName;
            String endMarker = "END Test Cases/" + tcName;
            
            boolean inTestCase = false;
            String[] lines = consoleContent.split("\n");
            
            for (String line : lines) {
                if (line.contains(startMarker)) {
                    inTestCase = true;
                    continue;  // Don't include the START line itself
                }
                if (line.contains(endMarker)) {
                    break;  // Stop at END
                }
                if (inTestCase) {
                    // Convert console log format to JUnit format
                    // Console: "2026-04-22 13:37:42.189 DEBUG testcase.0 - Catatan Badru - 1: CSReport.export..."
                    // JUnit:   "22-04-2026T13:37:42 - [TEST_STEP][PASSED] - CSReport.export...: ..."
                    
                    String junitLine = convertConsoleToJUnitFormat(line);
                    if (junitLine != null) {
                        tcLog.append(junitLine).append("\n");
                        // Add extra newline after TEST_STEP entries for readability (Katalon format)
                        if (junitLine.contains("[TEST_STEP]")) {
                            tcLog.append("\n");
                        }
                    }
                }
            }
            
            return tcLog.toString();
            
        } catch (Exception e) {
            // Fallback on error
            return tcResult.getTestCaseName() + "\n";
        }
    }
    
    /**
     * Convert console log line to JUnit system-out format matching Katalon Studio
     */
    private String convertConsoleToJUnitFormat(String consoleLine) {
        // Skip empty lines
        if (consoleLine == null || consoleLine.trim().isEmpty()) {
            return null;
        }
        
        // Parse console format variations:
        // 1. "2026-04-22 13:37:42.189 DEBUG testcase.0 - Catatan Badru - 1: CSReport.export..." -> TEST_STEP
        // 2. "2026-04-22 13:37:55.861 INFO c.k.k.c.keyword.builtin.CommentKeyword - /Users/..." -> MESSAGE
        // 3. "Starting compile report /Users/..." (plain println output) -> MESSAGE
        
        // Check if line has timestamp pattern
        if (!consoleLine.matches("^\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+.*")) {
            // Plain output (no timestamp) - treat as MESSAGE
            // Don't add timestamp, keep original Katalon behavior
            return consoleLine.trim();
        }
        
        // Parse: "2026-04-22 13:37:42.189 LEVEL logger - message"
        String[] parts = consoleLine.split(" - ", 2);
        if (parts.length < 2) {
            return consoleLine.trim();  // Return as-is if can't parse
        }
        
        String headerPart = parts[0];  // "2026-04-22 13:37:42.189 DEBUG testcase.0"
        String messagePart = parts[1].trim();
        
        // Extract timestamp from header
        String[] headerTokens = headerPart.trim().split("\\s+");
        if (headerTokens.length < 3) {
            return consoleLine.trim();
        }
        
        String date = headerTokens[0];  // "2026-04-22"
        String time = headerTokens[1];  // "13:37:42.189"
        String level = headerTokens[2];  // "INFO", "DEBUG", "ERROR"
        String logger = headerTokens.length > 3 ? headerTokens[3] : "";  // "testcase.0" or "c.k.k.c.keyword..."
        
        // Convert to JUnit timestamp format: "22-04-2026T13:37:42"
        String[] dateParts = date.split("-");
        String junitDate = dateParts[2] + "-" + dateParts[1] + "-" + dateParts[0];  // DD-MM-YYYY
        String junitTime = time.substring(0, 8);  // HH:MM:SS (strip milliseconds)
        String junitTimestamp = junitDate + "T" + junitTime;
        
        // Determine category based on logger and message content
        String category = "MESSAGE";
        String status = "INFO";
        
        if (level.equals("DEBUG") && logger.startsWith("testcase.")) {
            // This is a TEST_STEP from Groovy AST transformation
            // Format: "1: CSReport.exportKatalonReports(...)"
            category = "TEST_STEP";
            status = "PASSED";
            
            // Extract the step message (remove step number prefix if present)
            if (messagePart.matches("^\\d+:\\s+.*")) {
                messagePart = messagePart.replaceFirst("^\\d+:\\s+", "");
            }
        } else if (logger.contains("CommentKeyword") || logger.contains("KeywordUtil")) {
            // KeywordUtil.logInfo() calls
            category = "MESSAGE";
            status = "INFO";
        } else if (level.equals("ERROR")) {
            category = "MESSAGE";
            status = "ERROR";
        } else if (level.equals("WARN")) {
            category = "MESSAGE";
            status = "WARNING";
        }
        
        // Format: "22-04-2026T13:37:42 - [CATEGORY][STATUS] - message"
        // For MESSAGE category, check if message already has detail after colon
        String detail = "null";
        if (messagePart.contains(": ")) {
            // Message already has detail part, use it
            return junitTimestamp + " - [" + category + "][" + status + "] - " + messagePart;
        } else {
            // Add ": null" suffix for Katalon compatibility
            return junitTimestamp + " - [" + category + "][" + status + "] - " + messagePart + ": " + detail;
        }
    }
    
    private String getCurrentTimeJunitFormat() {
        return JUNIT_TIMESTAMP_FORMATTER.format(ZonedDateTime.now(ZoneId.systemDefault()));
    }

    /**
     * Build suite-level log for the JUnit <testsuite><system-out>
     * block. Katalon writes beforeSuite/afterTestSuite listener actions here.
     */
    private String buildSuiteLog(TestSuiteResult suiteResult) {
        // Read from console0.log (already generated) and extract suite-level logs
        Path consoleLog = currentReportDir.resolve("console0.log");
        if (!Files.exists(consoleLog)) {
            return "";
        }
        
        try {
            String consoleContent = Files.readString(consoleLog);
            StringBuilder suiteLog = new StringBuilder();
            
            // Extract lines BEFORE first "START Test Cases/" and AFTER last "END Test Cases/"
            String[] lines = consoleContent.split("\n");
            boolean inTestCase = false;
            boolean seenAnyTest = false;
            
            for (String line : lines) {
                if (line.contains("START Test Cases/")) {
                    inTestCase = true;
                    seenAnyTest = true;
                    continue;
                }
                if (line.contains("END Test Cases/")) {
                    inTestCase = false;
                    continue;
                }
                
                // Include lines outside test cases (suite-level listeners)
                if (!inTestCase) {
                    String junitLine = convertConsoleToJUnitFormat(line);
                    if (junitLine != null) {
                        suiteLog.append(junitLine).append("\n\n");
                    }
                }
            }
            
            return suiteLog.toString();
            
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Extract ERROR-level lines from log content.
     * Used for <system-err> sections in JUnit XML (Katalon compatibility).
     * Katalon puts ERROR-level messages in system-err while keeping full log in system-out.
     */
    private String extractErrorLines(String logContent) {
        if (logContent == null || logContent.isEmpty()) {
            return "";
        }
        
        StringBuilder errorLog = new StringBuilder();
        String[] lines = logContent.split("\n");
        boolean inErrorBlock = false;
        
        for (String line : lines) {
            // Detect ERROR-level log lines: contains [ERROR] or [TEST_CASE][ERROR] or [TEST_SUITE][ERROR]
            if (line.contains("[ERROR]")) {
                errorLog.append(line).append("\n");
                inErrorBlock = true;
            } else if (inErrorBlock && !line.trim().isEmpty() && !line.matches("^\\d{2}-\\d{2}-\\d{4}T.*")) {
                // Continue multi-line error message (stack trace, etc)
                errorLog.append(line).append("\n");
            } else if (line.matches("^\\d{2}-\\d{2}-\\d{4}T.*") && !line.contains("[ERROR]")) {
                // New timestamped line that's not ERROR = end of error block
                inErrorBlock = false;
            }
        }
        
        return errorLog.toString();
    }
    
    /**
     * Get Katalon-style CSS
     */
    private String getKatalonCss() {
        return "        :root {\n" +
                "            --color-passed: #00c853;\n" +
                "            --color-failed: #ff1744;\n" +
                "            --color-error: #ff9100;\n" +
                "            --color-skipped: #78909c;\n" +
                "            --color-primary: #5d54a4;\n" +
                "            --color-secondary: #7c3aed;\n" +
                "            --color-bg: #1e1e2e;\n" +
                "            --color-card: #2d2d44;\n" +
                "            --color-text: #ffffff;\n" +
                "            --color-text-secondary: #a0a0a0;\n" +
                "        }\n" +
                "        * { box-sizing: border-box; margin: 0; padding: 0; }\n" +
                "        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: var(--color-bg); color: var(--color-text); min-height: 100vh; }\n" +
                "        .katalon-report { max-width: 1400px; margin: 0 auto; padding: 20px; }\n" +
                "        .report-header { display: flex; justify-content: space-between; align-items: center; padding: 20px 0; border-bottom: 1px solid #3d3d5c; margin-bottom: 20px; }\n" +
                "        .header-left { display: flex; align-items: center; gap: 15px; }\n" +
                "        .logo { width: 48px; height: 48px; background: linear-gradient(135deg, #5d54a4 0%, #7c3aed 100%); border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 24px; font-weight: bold; color: white; }\n" +
                "        .title-section h1 { font-size: 1.5em; margin-bottom: 4px; }\n" +
                "        .title-section p { color: var(--color-text-secondary); font-size: 0.9em; }\n" +
                "        .result-badge { padding: 8px 20px; border-radius: 20px; font-weight: 600; font-size: 0.9em; }\n" +
                "        .result-badge.passed { background: rgba(0,200,83,0.2); color: #00c853; border: 1px solid #00c853; }\n" +
                "        .result-badge.failed { background: rgba(255,23,68,0.2); color: #ff1744; border: 1px solid #ff1744; }\n" +
                "        .stats-bar { display: flex; gap: 15px; margin-bottom: 20px; flex-wrap: wrap; }\n" +
                "        .stat-item { background: var(--color-card); padding: 20px 30px; border-radius: 12px; text-align: center; min-width: 120px; }\n" +
                "        .stat-item .num { display: block; font-size: 2em; font-weight: bold; margin-bottom: 4px; }\n" +
                "        .stat-item .label { color: var(--color-text-secondary); font-size: 0.85em; text-transform: uppercase; letter-spacing: 0.5px; }\n" +
                "        .stat-item.total .num { color: var(--color-text); }\n" +
                "        .stat-item.passed .num { color: var(--color-passed); }\n" +
                "        .stat-item.failed .num { color: var(--color-failed); }\n" +
                "        .stat-item.errored .num { color: var(--color-error); }\n" +
                "        .stat-item.skipped .num { color: var(--color-skipped); }\n" +
                "        .section { background: var(--color-card); border-radius: 12px; padding: 20px; margin-bottom: 20px; }\n" +
                "        .section h2 { font-size: 1.1em; margin-bottom: 15px; padding-bottom: 10px; border-bottom: 1px solid #3d3d5c; }\n" +
                "        .env-section { }\n" +
                "        .env-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 15px; }\n" +
                "        .env-item { display: flex; flex-direction: column; }\n" +
                "        .env-item .label { color: var(--color-text-secondary); font-size: 0.8em; margin-bottom: 4px; text-transform: uppercase; letter-spacing: 0.5px; }\n" +
                "        .env-item .value { font-size: 0.95em; }\n" +
                "        .test-case { background: #252538; border-radius: 8px; margin-bottom: 10px; overflow: hidden; }\n" +
                "        .tc-header { display: flex; align-items: center; padding: 15px 20px; cursor: pointer; gap: 12px; }\n" +
                "        .tc-header:hover { background: #2d2d44; }\n" +
                "        .tc-status { width: 12px; height: 12px; border-radius: 50%; flex-shrink: 0; }\n" +
                "        .tc-status.passed { background: var(--color-passed); box-shadow: 0 0 8px var(--color-passed); }\n" +
                "        .tc-status.failed { background: var(--color-failed); box-shadow: 0 0 8px var(--color-failed); }\n" +
                "        .tc-status.error { background: var(--color-error); box-shadow: 0 0 8px var(--color-error); }\n" +
                "        .tc-status.skipped { background: var(--color-skipped); }\n" +
                "        .tc-name { flex: 1; font-weight: 500; }\n" +
                "        .tc-duration { color: var(--color-text-secondary); font-size: 0.85em; }\n" +
                "        .tc-toggle { color: var(--color-text-secondary); font-size: 0.8em; }\n" +
                "        .tc-body { padding: 0 20px 20px 20px; display: none; }\n" +
                "        .steps-table { width: 100%; border-collapse: collapse; }\n" +
                "        .steps-table th { text-align: left; padding: 10px; color: var(--color-text-secondary); font-weight: 500; font-size: 0.85em; border-bottom: 1px solid #3d3d5c; }\n" +
                "        .steps-table td { padding: 10px; border-bottom: 1px solid #2d2d44; }\n" +
                "        .step-status { padding: 3px 10px; border-radius: 12px; font-size: 0.75em; font-weight: 500; }\n" +
                "        .step-status.passed { background: rgba(0,200,83,0.2); color: #00c853; }\n" +
                "        .step-status.failed { background: rgba(255,23,68,0.2); color: #ff1744; }\n" +
                "        .step-log td { padding: 0; }\n" +
                "        .error-msg { background: rgba(255,23,68,0.1); border-left: 3px solid #ff1744; padding: 10px 15px; margin: 5px 0; font-size: 0.85em; color: #ff8a80; white-space: pre-wrap; word-break: break-word; }\n" +
                "        .report-footer { text-align: center; padding: 20px; color: var(--color-text-secondary); font-size: 0.85em; }\n";
    }
    
    // Utility methods
    private String formatDuration(Duration duration) {
        if (duration == null) return "0s";
        long seconds = duration.getSeconds();
        long millis = duration.toMillis() % 1000;
        if (seconds < 60) {
            return String.format("%d.%03ds", seconds, millis);
        }
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%dm %d.%03ds", minutes, seconds, millis);
    }
    
    private String formatDurationCsv(Duration duration) {
        if (duration == null) return "0s";
        long seconds = duration.getSeconds();
        long millis = duration.toMillis() % 1000;
        return String.format("%d.%03ds", seconds, millis);
    }
    
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
    
    /**
     * Helper method to create left/right pair for xpathsPriority and methodsPriorityOrder
     */
    private Map<String, Object> createPair(String left, boolean right) {
        Map<String, Object> pair = new LinkedHashMap<>();
        pair.put("left", left);
        pair.put("right", right);
        return pair;
    }
    
    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
    
    private String getHostname() {
        // Katalon emits "localhost" here (loopback hostname), not the LAN
        // machine name. Mirror that behaviour for byte-identical reports.
        try {
            return java.net.InetAddress.getLoopbackAddress().getHostName();
        } catch (Exception e) {
            return "localhost";
        }
    }

    private String getHostAddress() {
        // Katalon emits "127.0.0.1" (loopback), not the LAN interface IP.
        try {
            return java.net.InetAddress.getLoopbackAddress().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    /**
     * Resolve the project name the way Katalon does: read the <name> element
     * from the project's <code>.prj</code> file. Falls back to the directory
     * name when no <code>.prj</code> file (or <name> tag) is found.
     */
    private String resolveProjectName() {
        try {
            if (projectPath != null && Files.isDirectory(projectPath)) {
                try (java.util.stream.Stream<Path> stream = Files.list(projectPath)) {
                    java.util.Optional<Path> prj = stream
                            .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".prj"))
                            .findFirst();
                    if (prj.isPresent()) {
                        String xml = Files.readString(prj.get());
                        java.util.regex.Matcher m = java.util.regex.Pattern
                                .compile("<name>([^<]*)</name>")
                                .matcher(xml);
                        if (m.find()) {
                            String name = m.group(1).trim();
                            if (!name.isEmpty()) return name;
                        }
                    }
                }
            }
        } catch (Exception ignored) { /* fall through */ }
        return projectPath != null && projectPath.getFileName() != null
                ? projectPath.getFileName().toString()
                : "";
    }

    /**
     * Resolve the "full name" of the running user. Prefers the
     * KATALAN_USER_FULL_NAME environment variable (so it can be configured to
     * match the Katalon license holder), then the {@code user.fullname}
     * system property, and finally falls back to {@code user.name}.
     */
    private String resolveUserFullName() {
        // Prefer Katalon session.properties fullName when available
        try {
            String userHome = System.getProperty("user.home");
            java.nio.file.Path[] candidates = new java.nio.file.Path[] {
                    java.nio.file.Paths.get(userHome, ".katalon", "session.properties"),
                    (System.getenv("USERPROFILE") != null) ? java.nio.file.Paths.get(System.getenv("USERPROFILE"), ".katalon", "session.properties") : null
            };
            for (java.nio.file.Path p : candidates) {
                if (p == null) continue;
                if (java.nio.file.Files.exists(p)) {
                    String content = java.nio.file.Files.readString(p);
                    // Format: "fullName"\:"Muhamad Badru Salam"
                    // Pattern explained: \" = literal quote, \\\\ = one backslash, : = colon
                    java.util.regex.Pattern ptn = java.util.regex.Pattern.compile("\"fullName\"\\\\:\"([^\"]+)\"");
                    java.util.regex.Matcher m = ptn.matcher(content);
                    if (m.find()) {
                        String fullName = m.group(1);
                        System.out.println("✅ [KatalonReportGenerator] Read fullName from session.properties: " + fullName);
                        return fullName;
                    } else {
                        System.out.println("⚠️ [KatalonReportGenerator] Pattern did not match in: " + p);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("❌ [KatalonReportGenerator] Error reading session.properties: " + e.getMessage());
        }

        String env = System.getenv("KATALAN_USER_FULL_NAME");
        if (env != null && !env.isEmpty()) return env;
        String prop = System.getProperty("user.fullname");
        if (prop != null && !prop.isEmpty()) return prop;
        return System.getProperty("user.name", "");
    }
    
    /**
     * Get browser information (name + version) from ExecutionResult
     * Format: "Chrome 147.0.7727.57"
     */
    private String getBrowserInfo(ExecutionResult result) {
        if (result.getBrowserName() != null && result.getBrowserVersion() != null) {
            return result.getBrowserName() + " " + result.getBrowserVersion();
        }
        return "";
    }
    
    /**
     * Collect all screenshot attachments (.png files) from the report directory.
     * Returns a comma-separated list of absolute paths to screenshots.
     */
    private String collectScreenshotAttachments(Path reportDir) {
        List<String> screenshots = new ArrayList<>();
        try {
            // Find all .png files in the report directory (not in subdirectories)
            if (Files.exists(reportDir) && Files.isDirectory(reportDir)) {
                try (java.util.stream.Stream<Path> files = Files.list(reportDir)) {
                    files.filter(f -> Files.isRegularFile(f) && f.toString().toLowerCase().endsWith(".png"))
                         .sorted() // Sort by filename
                         .forEach(f -> screenshots.add(f.toAbsolutePath().toString()));
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to collect screenshot attachments from {}: {}", reportDir, e.getMessage());
        }
        
        // Return comma-separated list (Katalon format)
        return String.join(", ", screenshots);
    }
    
    /**
     * Get WebDriver session ID (DEPRECATED - use ExecutionResult.getSessionId())
     */
    private String getWebDriverSessionId() {
        try {
            org.openqa.selenium.WebDriver driver = com.kms.katalon.core.webui.driver.DriverFactory.getWebDriverOrNull();
            if (driver != null) {
                return ((org.openqa.selenium.remote.RemoteWebDriver) driver).getSessionId().toString();
            }
        } catch (Exception e) {
            // Driver not available
        }
        return "";
    }
    
    /**
     * Get Selenium version from dependencies
     */
    private String getSeleniumVersion() {
        try {
            // Try to get version from Selenium's package
            Package pkg = org.openqa.selenium.WebDriver.class.getPackage();
            if (pkg != null && pkg.getImplementationVersion() != null) {
                return pkg.getImplementationVersion();
            }
            // Fallback: common Selenium 4.x version
            return "4.28.1";
        } catch (Exception e) {
            return "4.28.1";
        }
    }
    
    /**
     * Get proxy information string (Katalon format)
     */
    private String getProxyInformation() {
        // Format: "ProxyInformation { proxyOption=NO_PROXY, proxyServerType=HTTP, username=, password=********, proxyServerAddress=, proxyServerPort=0, executionList=\"\", isApplyToDesiredCapabilities=true }"
        return "ProxyInformation { proxyOption=NO_PROXY, proxyServerType=HTTP, username=, password=********, proxyServerAddress=, proxyServerPort=0, executionList=\"\", isApplyToDesiredCapabilities=true }";
    }
    
    /**
     * Simple JSON serialization (without external library)
     */
    private String toJson(Object obj) {
        return toJson(obj, 0);
    }
    
    private String toJson(Object obj, int indent) {
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + escapeJsonString((String) obj) + "\"";
        if (obj instanceof Number) return obj.toString();
        if (obj instanceof Boolean) return obj.toString();
        
        if (obj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) obj;
            if (map.isEmpty()) return "{}";
            
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            String sep = "";
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                sb.append(sep).append("\"").append(escapeJsonString(entry.getKey())).append("\":");
                sb.append(toJson(entry.getValue(), indent + 1));
                sep = ",";
            }
            sb.append("}");
            return sb.toString();
        }
        
        if (obj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) obj;
            if (list.isEmpty()) return "[]";
            
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            String sep = "";
            for (Object item : list) {
                sb.append(sep).append(toJson(item, indent + 1));
                sep = ",";
            }
            sb.append("]");
            return sb.toString();
        }
        
        return "\"" + obj.toString() + "\"";
    }
    
    private String escapeJsonString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    
    /**
     * Get Katalon-style JavaScript render script
     */
    private String getKatalonRenderScript() {
        return "// Global execution data store\n" +
                "window.executionData = window.executionData || {};\n" +
                "\n" +
                "// Katalon-compatible loadExecutionData function\n" +
                "function loadExecutionData(id, data) {\n" +
                "    window.executionData[id] = data;\n" +
                "    // Render after main data is loaded\n" +
                "    if (id === 'main') {\n" +
                "        setTimeout(renderReport, 0);\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "function renderReport() {\n" +
                "    const root = document.getElementById('root');\n" +
                "    const mainData = window.executionData['main'];\n" +
                "    if (!mainData || !mainData.entity) { root.innerHTML = '<p>No data</p>'; return; }\n" +
                "    const entity = mainData.entity;\n" +
                "    const stats = entity.statistics || {};\n" +
                "    const ctx = entity.context || {};\n" +
                "    const local = ctx.local || {};\n" +
                "    const target = ctx.target || {};\n" +
                "    \n" +
                "    let html = '<div class=\"katalon-report\">';\n" +
                "    \n" +
                "    // Header\n" +
                "    html += '<div class=\"report-header\">';\n" +
                "    html += '<div class=\"header-left\">';\n" +
                "    html += '<div class=\"logo\">K</div>';\n" +
                "    html += '<div class=\"title-section\">';\n" +
                "    html += '<h1>' + escapeHtml(entity.name) + '</h1>';\n" +
                "    html += '<p>' + (mainData.project ? mainData.project.name : '') + '</p>';\n" +
                "    html += '</div></div>';\n" +
                "    html += '<div class=\"header-right\">';\n" +
                "    html += '<span class=\"result-badge ' + entity.result.toLowerCase() + '\">' + entity.result + '</span>';\n" +
                "    html += '</div></div>';\n" +
                "    \n" +
                "    // Statistics\n" +
                "    html += '<div class=\"stats-bar\">';\n" +
                "    html += '<div class=\"stat-item total\"><span class=\"num\">' + (stats.total || 0) + '</span><span class=\"label\">Total</span></div>';\n" +
                "    html += '<div class=\"stat-item passed\"><span class=\"num\">' + (stats.passed || 0) + '</span><span class=\"label\">Passed</span></div>';\n" +
                "    html += '<div class=\"stat-item failed\"><span class=\"num\">' + (stats.failed || 0) + '</span><span class=\"label\">Failed</span></div>';\n" +
                "    html += '<div class=\"stat-item errored\"><span class=\"num\">' + (stats.errored || 0) + '</span><span class=\"label\">Error</span></div>';\n" +
                "    html += '<div class=\"stat-item skipped\"><span class=\"num\">' + (stats.skipped || 0) + '</span><span class=\"label\">Skipped</span></div>';\n" +
                "    html += '</div>';\n" +
                "    \n" +
                "    // Environment Info\n" +
                "    html += '<div class=\"section env-section\">';\n" +
                "    html += '<h2>Environment</h2>';\n" +
                "    html += '<div class=\"env-grid\">';\n" +
                "    html += '<div class=\"env-item\"><span class=\"label\">Host</span><span class=\"value\">' + escapeHtml(local.hostName || '') + '</span></div>';\n" +
                "    html += '<div class=\"env-item\"><span class=\"label\">OS</span><span class=\"value\">' + escapeHtml(local.os || '') + '</span></div>';\n" +
                "    html += '<div class=\"env-item\"><span class=\"label\">Browser</span><span class=\"value\">' + escapeHtml(target.browserName || 'Chrome') + '</span></div>';\n" +
                "    html += '<div class=\"env-item\"><span class=\"label\">Katalan</span><span class=\"value\">' + escapeHtml(local.katalonVersion || '') + '</span></div>';\n" +
                "    html += '<div class=\"env-item\"><span class=\"label\">Start</span><span class=\"value\">' + formatTime(entity.startTime) + '</span></div>';\n" +
                "    html += '<div class=\"env-item\"><span class=\"label\">End</span><span class=\"value\">' + formatTime(entity.endTime) + '</span></div>';\n" +
                "    html += '</div></div>';\n" +
                "    \n" +
                "    // Test Cases\n" +
                "    html += '<div class=\"section\">';\n" +
                "    html += '<h2>Test Cases</h2>';\n" +
                "    const children = entity.children || [];\n" +
                "    children.forEach(function(tc, i) {\n" +
                "        const tcData = window.executionData[String(tc.index)] || {children: [], logs: []};\n" +
                "        html += '<div class=\"test-case ' + tc.result.toLowerCase() + '\" id=\"tc-' + i + '\">';\n" +
                "        html += '<div class=\"tc-header\" onclick=\"toggleTC(' + i + ')\">';\n" +
                "        html += '<span class=\"tc-status ' + tc.result.toLowerCase() + '\"></span>';\n" +
                "        html += '<span class=\"tc-name\">' + escapeHtml(tc.name) + '</span>';\n" +
                "        html += '<span class=\"tc-duration\">' + calcDuration(tc.startTime, tc.endTime) + '</span>';\n" +
                "        html += '<span class=\"tc-toggle\">▼</span>';\n" +
                "        html += '</div>';\n" +
                "        html += '<div class=\"tc-body\" id=\"tc-body-' + i + '\">';\n" +
                "        \n" +
                "        // Steps\n" +
                "        const steps = tcData.children || [];\n" +
                "        if (steps.length > 0) {\n" +
                "            html += '<table class=\"steps-table\">';\n" +
                "            html += '<thead><tr><th>#</th><th>Step</th><th>Status</th><th>Time</th></tr></thead>';\n" +
                "            html += '<tbody>';\n" +
                "            steps.forEach(function(step, j) {\n" +
                "                html += '<tr class=\"step ' + step.result.toLowerCase() + '\">';\n" +
                "                html += '<td>' + (j + 1) + '</td>';\n" +
                "                html += '<td>' + escapeHtml(step.name) + '</td>';\n" +
                "                html += '<td><span class=\"step-status ' + step.result.toLowerCase() + '\">' + step.result + '</span></td>';\n" +
                "                html += '<td>' + calcDuration(step.startTime, step.endTime) + '</td>';\n" +
                "                html += '</tr>';\n" +
                "                // Step logs\n" +
                "                if (step.logs && step.logs.length > 0) {\n" +
                "                    step.logs.forEach(function(log) {\n" +
                "                        if (log.level === 'FAILED' && log.message) {\n" +
                "                            html += '<tr class=\"step-log\"><td colspan=\"4\"><pre class=\"error-msg\">' + escapeHtml(log.message) + '</pre></td></tr>';\n" +
                "                        }\n" +
                "                    });\n" +
                "                }\n" +
                "            });\n" +
                "            html += '</tbody></table>';\n" +
                "        }\n" +
                "        html += '</div></div>';\n" +
                "    });\n" +
                "    html += '</div>';\n" +
                "    \n" +
                "    // Footer\n" +
                "    html += '<div class=\"report-footer\">Generated by Katalan Runner</div>';\n" +
                "    html += '</div>';\n" +
                "    \n" +
                "    root.innerHTML = html;\n" +
                "}\n" +
                "\n" +
                "function toggleTC(i) {\n" +
                "    const body = document.getElementById('tc-body-' + i);\n" +
                "    const toggle = document.querySelector('#tc-' + i + ' .tc-toggle');\n" +
                "    if (body.style.display === 'none') {\n" +
                "        body.style.display = 'block';\n" +
                "        toggle.textContent = '▼';\n" +
                "    } else {\n" +
                "        body.style.display = 'none';\n" +
                "        toggle.textContent = '▶';\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "function escapeHtml(s) {\n" +
                "    if (!s) return '';\n" +
                "    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');\n" +
                "}\n" +
                "\n" +
                "function formatTime(iso) {\n" +
                "    if (!iso) return '';\n" +
                "    try {\n" +
                "        const d = new Date(iso);\n" +
                "        return d.toLocaleString();\n" +
                "    } catch (e) { return iso; }\n" +
                "}\n" +
                "\n" +
                "function calcDuration(start, end) {\n" +
                "    if (!start || !end) return '0s';\n" +
                "    try {\n" +
                "        const ms = new Date(end) - new Date(start);\n" +
                "        if (ms < 1000) return ms + 'ms';\n" +
                "        return (ms / 1000).toFixed(2) + 's';\n" +
                "    } catch (e) { return '0s'; }\n" +
                "}\n";
    }
    
    /**
     * Generate cucumber_report folder for BDD tests
     */
    private void generateCucumberReports(Path reportDir, ExecutionResult result) throws IOException {
        // Detect BDD tests based on various indicators
        List<TestCaseResult> bddTestCases = new ArrayList<>();
        
        for (TestSuiteResult suiteResult : result.getSuiteResults()) {
            for (TestCaseResult tcResult : suiteResult.getTestCaseResults()) {
                if (isBddTestCase(tcResult)) {
                    bddTestCases.add(tcResult);
                }
            }
        }
        
        // CRITICAL: Only create cucumber_report folder if there are BDD tests
        // Katalon does NOT create this folder for non-BDD tests
        if (bddTestCases.isEmpty()) {
            return;
        }
        
        Path cucumberDir = reportDir.resolve("cucumber_report");
        Files.createDirectories(cucumberDir);
        
        // Get the cucumber report timestamp from ExecutionContext
        // This ensures we use the same timestamp that was logged in execution0.log
        com.katalan.core.context.ExecutionContext context = com.katalan.core.context.ExecutionContext.getCurrent();
        String timestampId = context.getCucumberReportTimestamp();
        
        // Generate one folder per BDD test case/feature using the shared timestamp
        for (TestCaseResult tcResult : bddTestCases) {
            Path featureReportDir = cucumberDir.resolve(timestampId);
            Files.createDirectories(featureReportDir);
            
            // Check if files already exist (already flushed before @AfterTestSuite)
            // If so, skip generation to avoid duplication
            if (Files.exists(featureReportDir.resolve("cucumber.json")) &&
                Files.exists(featureReportDir.resolve("k-cucumber.json")) &&
                Files.exists(featureReportDir.resolve("cucumber.xml"))) {
                logger.debug("Cucumber reports already exist for {}, skipping", tcResult.getTestCaseName());
                continue;
            }
            
            generateCucumberJson(featureReportDir, tcResult);
            generateCucumberXml(featureReportDir, tcResult);
            generateKCucumberJson(featureReportDir, tcResult);
            generateCucumberHtml(featureReportDir, tcResult);
        }
        
        // Clear the cucumber report timestamp after all reports are generated
        // This ensures the same timestamp is used throughout the entire report generation process
        context.clearCucumberReportTimestamp();
    }
    
    /**
     * Detect if a test case is a BDD test
     */
    private boolean isBddTestCase(TestCaseResult tcResult) {
        // Check explicit BDD flag
        if (tcResult.isBddTest()) {
            return true;
        }
        
        // Check if feature file is set
        if (tcResult.getFeatureFile() != null) {
            return true;
        }
        
        // Check if test name contains "feature" 
        String name = tcResult.getTestCaseName().toLowerCase();
        if (name.contains("feature") || name.contains("bdd") || name.contains("cucumber")) {
            return true;
        }
        
        // Check if steps contain Given/When/Then keywords (common BDD pattern)
        List<TestCaseResult.StepResult> steps = tcResult.getStepResults();
        if (steps != null && !steps.isEmpty()) {
            int bddStepCount = 0;
            for (TestCaseResult.StepResult step : steps) {
                String stepName = step.getStepName();
                if (stepName != null) {
                    String lowerStep = stepName.toLowerCase();
                    if (lowerStep.startsWith("given ") || lowerStep.startsWith("when ") || 
                        lowerStep.startsWith("then ") || lowerStep.startsWith("and ") ||
                        lowerStep.startsWith("but ")) {
                        bddStepCount++;
                    }
                }
            }
            // If majority of steps are BDD-style, consider it a BDD test
            if (bddStepCount > 0 && bddStepCount >= steps.size() / 2) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Build the absolute filesystem URI for a BDD feature file.
     * Guarantees the returned path is absolute (projectPath + relative feature path)
     * and normalized (no ./ or ../ segments).
     */
    private String buildAbsoluteFeatureUri(TestCaseResult tcResult, FeatureMetadata metadata) {
        // Step 1: pick best source for feature file path
        String raw = tcResult.getFeatureFile();
        if (raw == null || raw.isEmpty()) {
            raw = metadata.absoluteFeatureFilePath;
        }
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        
        // Step 2: strip leading ./ or .\ repeatedly
        String cleaned = raw;
        while (cleaned.startsWith("./") || cleaned.startsWith(".\\")) {
            cleaned = cleaned.substring(2);
        }
        
        Path featurePath = Paths.get(cleaned);
        
        // Step 3: if not absolute, glue with projectPath (also forced absolute)
        if (!featurePath.isAbsolute()) {
            Path base = projectPath != null ? projectPath.toAbsolutePath() : Paths.get("").toAbsolutePath();
            featurePath = base.resolve(cleaned);
        }
        
        // Step 4: force absolute + normalize
        return featurePath.toAbsolutePath().normalize().toString();
    }
    
    /**
     * Generate cucumber.json file (standard Cucumber JSON format without UUIDs)
     */
    private void generateCucumberJson(Path featureDir, TestCaseResult tcResult) throws IOException {
        List<Map<String, Object>> features = new ArrayList<>();
        Map<String, Object> feature = new LinkedHashMap<>();
        
        // Parse .feature file for metadata
        FeatureMetadata metadata = parseFeatureFile(tcResult);
        
        // FORCE full absolute URI: projectPath + feature relative path, always absolute.
        String absoluteUri = buildAbsoluteFeatureUri(tcResult, metadata);
        
        feature.put("line", metadata.featureLine);
        // Use the same buildKCucumberElements but strip UUID fields for standard cucumber.json
        List<Map<String, Object>> elements = buildKCucumberElements(tcResult, metadata);
        stripUuidsFromElements(elements);
        feature.put("elements", elements);
        feature.put("name", metadata.featureName);
        feature.put("description", metadata.featureDescription);
        feature.put("id", metadata.featureName.toLowerCase().replace(" ", "-").replaceAll("[^a-z0-9\\-]", ""));
        feature.put("keyword", "Feature");
        feature.put("uri", absoluteUri);  // Use forced absolute URI
        feature.put("tags", metadata.featureTags);
        
        features.add(feature);
        Files.writeString(featureDir.resolve("cucumber.json"), toJson(features));
    }
    
    /**
     * Remove UUID fields from cucumber elements for standard cucumber.json format
     */
    private void stripUuidsFromElements(List<Map<String, Object>> elements) {
        for (Map<String, Object> element : elements) {
            element.remove("BDD_TESTRUN_UUID");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> steps = (List<Map<String, Object>>) element.get("steps");
            if (steps != null) {
                for (Map<String, Object> step : steps) {
                    step.remove("BDD_STEP_UUID");
                }
            }
        }
    }
    
    /**
     * Build Cucumber elements (scenarios) from test case result
     */
    private List<Map<String, Object>> buildCucumberElements(TestCaseResult tcResult) {
        List<Map<String, Object>> elements = new ArrayList<>();
        Map<String, Object> scenario = new LinkedHashMap<>();
        
        scenario.put("line", 3);
        scenario.put("name", tcResult.getScenarioName() != null ? tcResult.getScenarioName() : tcResult.getTestCaseName());
        scenario.put("description", "");
        scenario.put("id", tcResult.getTestCaseName().toLowerCase().replace(" ", "-") + ";scenario");
        scenario.put("type", "scenario");
        scenario.put("keyword", "Scenario");
        scenario.put("tags", new ArrayList<>());
        
        List<Map<String, Object>> steps = new ArrayList<>();
        int lineNum = 4;
        for (TestCaseResult.StepResult stepResult : tcResult.getStepResults()) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("line", lineNum++);
            
            // Determine keyword from step name
            String stepName = stepResult.getStepName();
            String keyword = "Given ";
            if (stepName.toLowerCase().startsWith("given ")) {
                keyword = "Given ";
                stepName = stepName.substring(6);
            } else if (stepName.toLowerCase().startsWith("when ")) {
                keyword = "When ";
                stepName = stepName.substring(5);
            } else if (stepName.toLowerCase().startsWith("then ")) {
                keyword = "Then ";
                stepName = stepName.substring(5);
            } else if (stepName.toLowerCase().startsWith("and ")) {
                keyword = "And ";
                stepName = stepName.substring(4);
            }
            
            step.put("name", stepName);
            step.put("keyword", keyword);
            
            // Result
            Map<String, Object> result = new LinkedHashMap<>();
            long durationNanos = 0;
            if (stepResult.getStartTime() != null && stepResult.getEndTime() != null) {
                durationNanos = Duration.between(stepResult.getStartTime(), stepResult.getEndTime()).toNanos();
            }
            result.put("duration", durationNanos);
            result.put("status", stepResult.isPassed() ? "passed" : "failed");
            if (!stepResult.isPassed() && stepResult.getErrorMessage() != null) {
                result.put("error_message", stepResult.getErrorMessage());
            }
            step.put("result", result);
            
            // Match
            Map<String, Object> match = new LinkedHashMap<>();
            match.put("location", "StepDefinitions." + stepName.replace(" ", "_").toLowerCase() + "()");
            step.put("match", match);
            
            steps.add(step);
        }
        scenario.put("steps", steps);
        
        elements.add(scenario);
        return elements;
    }
    
    /**
     * Generate cucumber.xml (JUnit XML format for Cucumber - Katalon style)
     */
    private void generateCucumberXml(Path featureDir, TestCaseResult tcResult) throws IOException {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
        
        long durationMs = tcResult.getEndTime() != null && tcResult.getStartTime() != null ?
                Duration.between(tcResult.getStartTime(), tcResult.getEndTime()).toMillis() : 0;
        double durationSec = durationMs / 1000.0;
        
        int failed = (tcResult.getStatus() == TestCase.TestCaseStatus.FAILED || 
                      tcResult.getStatus() == TestCase.TestCaseStatus.ERROR) ? 1 : 0;
        int skipped = tcResult.getStatus() == TestCase.TestCaseStatus.SKIPPED ? 1 : 0;
        
        // Katalon uses "cucumber.runtime.formatter.JUnitFormatter" as testsuite name
        xml.append("<testsuite failures=\"").append(failed).append("\" ");
        xml.append("name=\"cucumber.runtime.formatter.JUnitFormatter\" ");
        xml.append("skipped=\"").append(skipped).append("\" ");
        xml.append("tests=\"1\" ");
        xml.append("time=\"").append(String.format("%.6f", durationSec)).append("\">\n");
        
        // Extract test suite name from test case path (e.g., "Test Suites/Regresion/Nomi/BRI to BRI" -> "BRI to BRI")
        String testSuiteName = extractTestSuiteName(tcResult.getTestCaseName());
        String scenarioName = tcResult.getScenarioName() != null ? tcResult.getScenarioName() : testSuiteName;
        
        xml.append("    <testcase classname=\"").append(escapeXml(testSuiteName)).append("\" ");
        xml.append("name=\"").append(escapeXml(scenarioName)).append("\" ");
        xml.append("time=\"").append(String.format("%.6f", durationSec)).append("\"");
        
        if (tcResult.getStatus() == TestCase.TestCaseStatus.FAILED || tcResult.getStatus() == TestCase.TestCaseStatus.ERROR) {
            xml.append(">\n");
            
            // Build failure message with full error and stack trace
            String errorMsg = tcResult.getErrorMessage() != null ? tcResult.getErrorMessage() : "Test failed";
            String stackTrace = tcResult.getStackTrace() != null ? tcResult.getStackTrace() : "";
            
            xml.append("        <failure message=\"").append(escapeXmlAttribute(errorMsg));
            if (!stackTrace.isEmpty()) {
                xml.append("&#10;").append(escapeXmlAttribute(stackTrace));
            }
            xml.append("\">");
            
            // Build CDATA section with step summary (Katalon style)
            xml.append("<![CDATA[");
            buildStepSummary(xml, tcResult);
            xml.append("\n\nStackTrace:\n").append(errorMsg);
            if (!stackTrace.isEmpty()) {
                xml.append("\n").append(stackTrace);
            }
            xml.append("]]>");
            
            xml.append("</failure>\n");
            xml.append("    </testcase>\n");
        } else if (tcResult.getStatus() == TestCase.TestCaseStatus.SKIPPED) {
            xml.append(">\n        <skipped/>\n    </testcase>\n");
        } else {
            xml.append("/>\n");
        }
        
        xml.append("</testsuite>\n");
        
        Files.writeString(featureDir.resolve("cucumber.xml"), xml.toString());
    }
    
    /**
     * Extract test suite name from test case path
     * E.g., "Test Cases/BRICAMS/Transfer Fund/BRI to BRI/Standart TC/TC01..." -> "BRI to BRI"
     * The test suite name is usually the last folder before "Standart TC" or before the TC file
     */
    private String extractTestSuiteName(String testCasePath) {
        if (testCasePath == null) return "Unknown";
        
        String[] parts = testCasePath.split("/");
        
        // Look for part before "Standart TC" or TC file
        for (int i = parts.length - 1; i >= 0; i--) {
            String part = parts[i];
            
            // Skip TC files
            if (part.startsWith("TC") && part.contains("-")) {
                continue;
            }
            
            // Skip "Standart TC", "Standard TC" folders
            if (part.contains("Standart") || part.contains("Standard")) {
                // Return previous part
                if (i > 0) {
                    return parts[i - 1];
                }
            }
            
            // If we find a specific pattern like "BRI to BRI", "VACA to VACA", return it
            if (part.contains(" to ") && i < parts.length - 1) {
                return part;
            }
        }
        
        // Fallback: return last folder before TC file (skip if starts with "TC" or contains "Standart")
        for (int i = parts.length - 2; i >= 0; i--) {
            String part = parts[i];
            if (!part.startsWith("TC") && !part.contains("Standart") && !part.contains("Standard")) {
                return part;
            }
        }
        
        return parts.length >= 2 ? parts[parts.length - 2] : testCasePath;
    }
    
    /**
     * Build step summary for CDATA section (Katalon style)
     * Format: "Given Step name.......passed" or "When Step name.......failed"
     */
    private void buildStepSummary(StringBuilder xml, TestCaseResult tcResult) {
        List<Map<String, Object>> bddScenarios = tcResult.getBddScenarioData();
        
        if (bddScenarios == null || bddScenarios.isEmpty()) {
            return;
        }
        
        for (Map<String, Object> scenario : bddScenarios) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> steps = (List<Map<String, Object>>) scenario.get("children");
            
            if (steps == null || steps.isEmpty()) {
                continue;
            }
            
            for (Map<String, Object> step : steps) {
                String stepName = (String) step.get("name");
                String result = (String) step.get("result");
                
                if (stepName == null) continue;
                
                // Extract keyword from step name (e.g., "Given Login..." -> keyword="Given", name="Login...")
                String keyword = "";
                String cleanName = stepName;
                
                for (String kw : new String[]{"Given ", "When ", "Then ", "And ", "But "}) {
                    if (stepName.startsWith(kw)) {
                        keyword = kw.trim();
                        cleanName = stepName.substring(kw.length());
                        break;
                    }
                }
                
                // Build line: "Given Step name.......passed" (pad to ~80 chars)
                String line = keyword + " " + cleanName;
                int dotsNeeded = Math.max(1, 80 - line.length() - 6); // 6 for "passed" or "failed"
                xml.append(line);
                for (int i = 0; i < dotsNeeded; i++) {
                    xml.append(".");
                }
                
                String status = "PASSED".equalsIgnoreCase(result) ? "passed" : "failed";
                xml.append(status).append("\n");
            }
        }
    }
    
    /**
     * Escape XML attribute values (for use in attribute="..." context)
     */
    private String escapeXmlAttribute(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;")
                   .replace("\n", "&#10;")
                   .replace("\r", "&#13;")
                   .replace("\t", "&#9;");
    }
    
    /**
     * Generate k-cucumber.json (Katalon enhanced Cucumber JSON with UUIDs)
     */
    private void generateKCucumberJson(Path featureDir, TestCaseResult tcResult) throws IOException {
        List<Map<String, Object>> features = new ArrayList<>();
        Map<String, Object> feature = new LinkedHashMap<>();
        
        // Parse .feature file for metadata
        FeatureMetadata metadata = parseFeatureFile(tcResult);
        
        // FORCE full absolute URI: projectPath + feature relative path, always absolute.
        String absoluteUri = buildAbsoluteFeatureUri(tcResult, metadata);
        
        feature.put("line", metadata.featureLine);
        feature.put("elements", buildKCucumberElements(tcResult, metadata));
        feature.put("name", metadata.featureName);
        feature.put("description", metadata.featureDescription);
        feature.put("id", metadata.featureName.toLowerCase().replace(" ", "-").replaceAll("[^a-z0-9\\-]", ""));
        feature.put("keyword", "Feature");
        feature.put("uri", absoluteUri);  // Use forced absolute URI
        feature.put("tags", metadata.featureTags);
        
        features.add(feature);
        Files.writeString(featureDir.resolve("k-cucumber.json"), toJson(features));
    }
    
    /**
     * Build Katalon-enhanced Cucumber elements with UUIDs
     */
    private List<Map<String, Object>> buildKCucumberElements(TestCaseResult tcResult, FeatureMetadata metadata) {
        List<Map<String, Object>> elements = new ArrayList<>();
        
        // If we have hierarchical BDD scenario data, use it
        if (tcResult.getBddScenarioData() != null && !tcResult.getBddScenarioData().isEmpty()) {
            logger.debug("Building k-cucumber elements from BDD hierarchical data ({} scenarios)", 
                tcResult.getBddScenarioData().size());
            
            for (Map<String, Object> scenarioData : tcResult.getBddScenarioData()) {
                Object childrenObj = scenarioData.get("children");
                
                Map<String, Object> scenario = new LinkedHashMap<>();
                
                String testRunUuid = UUID.randomUUID().toString();
                scenario.put("BDD_TESTRUN_UUID", testRunUuid);
                
                String scenarioName = (String) scenarioData.getOrDefault("name", tcResult.getTestCaseName());
                // Strip "Start Test Case : SCENARIO " prefix if present
                if (scenarioName.startsWith("Start Test Case : SCENARIO ")) {
                    scenarioName = scenarioName.substring("Start Test Case : SCENARIO ".length());
                }
                
                // Match scenario name with metadata to get line number and keyword
                ScenarioInfo scenarioInfo = metadata.findScenario(scenarioName);
                scenario.put("line", scenarioInfo.line);
                scenario.put("name", scenarioName);
                scenario.put("description", "");
                
                // Create ID from feature ID + scenario name
                String scenarioId = metadata.featureName.toLowerCase().replace(" ", "-").replaceAll("[^a-z0-9\\-]", "")
                    + ";" + scenarioName.toLowerCase().replace(" ", "-").replaceAll("[^a-z0-9\\-;]", "");
                if (scenarioInfo.isOutline) {
                    scenarioId += ";;" + (scenarioInfo.exampleIndex + 2); // +2 because examples start at row 2 (after header)
                }
                scenario.put("id", scenarioId);
                
                scenario.put("type", "scenario");
                scenario.put("keyword", scenarioInfo.keyword);
                
                // Combine feature tags + scenario tags (Katalon inherits feature tags)
                List<Map<String, Object>> combinedTags = new ArrayList<>(metadata.featureTags);
                combinedTags.addAll(scenarioInfo.tags);
                scenario.put("tags", combinedTags);
                
                // Extract steps from scenario children
                List<Map<String, Object>> steps = new ArrayList<>();
                if (childrenObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> children = (List<Map<String, Object>>) childrenObj;
                    
                    // Match steps with scenario steps from metadata
                    List<StepInfo> metadataSteps = scenarioInfo.steps;
                    for (int i = 0; i < children.size(); i++) {
                        Map<String, Object> stepData = children.get(i);
                        StepInfo stepInfo = i < metadataSteps.size() ? metadataSteps.get(i) : null;
                        int lineNum = stepInfo != null ? stepInfo.line : (scenarioInfo.line + i + 1);
                        
                        Map<String, Object> step = buildCucumberStepFromData(stepData, lineNum, stepInfo);
                        if (step != null) {
                            steps.add(step);
                        }
                    }
                }
                scenario.put("steps", steps);
                elements.add(scenario);
            }
            
            return elements;
        }
        
        // Fallback to flat step results if no BDD hierarchical data
        logger.debug("Building k-cucumber elements from flat step results ({} steps)", 
            tcResult.getStepResults().size());
        
        Map<String, Object> scenario = new LinkedHashMap<>();
        
        String testRunUuid = UUID.randomUUID().toString();
        
        scenario.put("BDD_TESTRUN_UUID", testRunUuid);
        scenario.put("line", 3);
        scenario.put("name", tcResult.getScenarioName() != null ? tcResult.getScenarioName() : tcResult.getTestCaseName());
        scenario.put("description", "");
        scenario.put("id", tcResult.getTestCaseName().toLowerCase().replace(" ", "-") + ";scenario");
        scenario.put("type", "scenario");
        scenario.put("keyword", "Scenario");
        scenario.put("tags", new ArrayList<>());
        
        List<Map<String, Object>> steps = new ArrayList<>();
        int lineNum = 4;
        for (TestCaseResult.StepResult stepResult : tcResult.getStepResults()) {
            Map<String, Object> step = new LinkedHashMap<>();
            
            step.put("BDD_STEP_UUID", UUID.randomUUID().toString());
            step.put("line", lineNum++);
            
            // Determine keyword from step name
            String stepName = stepResult.getStepName();
            String keyword = "Given ";
            if (stepName.toLowerCase().startsWith("given ")) {
                keyword = "Given ";
                stepName = stepName.substring(6);
            } else if (stepName.toLowerCase().startsWith("when ")) {
                keyword = "When ";
                stepName = stepName.substring(5);
            } else if (stepName.toLowerCase().startsWith("then ")) {
                keyword = "Then ";
                stepName = stepName.substring(5);
            } else if (stepName.toLowerCase().startsWith("and ")) {
                keyword = "And ";
                stepName = stepName.substring(4);
            }
            
            step.put("name", stepName);
            step.put("keyword", keyword);
            
            // Result
            Map<String, Object> result = new LinkedHashMap<>();
            long durationNanos = 0;
            if (stepResult.getStartTime() != null && stepResult.getEndTime() != null) {
                durationNanos = Duration.between(stepResult.getStartTime(), stepResult.getEndTime()).toNanos();
            }
            result.put("duration", durationNanos);
            result.put("status", stepResult.isPassed() ? "passed" : "failed");
            if (!stepResult.isPassed() && stepResult.getErrorMessage() != null) {
                result.put("error_message", stepResult.getErrorMessage());
            }
            step.put("result", result);
            
            // Match
            Map<String, Object> match = new LinkedHashMap<>();
            match.put("location", "StepDefinitions." + stepName.replace(" ", "_").toLowerCase() + "()");
            step.put("match", match);
            
            steps.add(step);
        }
        scenario.put("steps", steps);
        
        elements.add(scenario);
        return elements;
    }
    
    /**
     * Build a Cucumber step JSON object from hierarchical step data
     */
    private Map<String, Object> buildCucumberStepFromData(Map<String, Object> stepData, int lineNum, StepInfo stepInfo) {
        Map<String, Object> step = new LinkedHashMap<>();
        
        step.put("BDD_STEP_UUID", UUID.randomUUID().toString());
        step.put("line", lineNum);
        
        // Extract step name and keyword
        String stepName = (String) stepData.getOrDefault("name", "Unknown step");
        String keyword = "Given ";
        if (stepName.toLowerCase().startsWith("given ")) {
            keyword = "Given ";
            stepName = stepName.substring(6);
        } else if (stepName.toLowerCase().startsWith("when ")) {
            keyword = "When ";
            stepName = stepName.substring(5);
        } else if (stepName.toLowerCase().startsWith("then ")) {
            keyword = "Then ";
            stepName = stepName.substring(5);
        } else if (stepName.toLowerCase().startsWith("and ")) {
            keyword = "And ";
            stepName = stepName.substring(4);
        } else if (stepName.toLowerCase().startsWith("but ")) {
            keyword = "But ";
            stepName = stepName.substring(4);
        }
        
        step.put("name", stepName);
        step.put("keyword", keyword);
        
        // Build result object
        Map<String, Object> result = new LinkedHashMap<>();
        String status = (String) stepData.getOrDefault("result", "PASSED");
        
        // Map status to Cucumber format (passed/failed/skipped)
        if ("SKIPPED".equalsIgnoreCase(status)) {
            result.put("status", "skipped");
        } else if ("FAILED".equalsIgnoreCase(status)) {
            result.put("status", "failed");
        } else {
            result.put("status", "passed");
        }
        
        // Calculate duration from startTime and endTime
        String startTimeStr = (String) stepData.get("startTime");
        String endTimeStr = (String) stepData.get("endTime");
        long durationNanos = 0;
        if (startTimeStr != null && endTimeStr != null) {
            try {
                Instant start = Instant.parse(startTimeStr.replace("T", "T").replaceAll("([+-]\\d{2})(\\d{2})$", "$1:$2"));
                Instant end = Instant.parse(endTimeStr.replace("T", "T").replaceAll("([+-]\\d{2})(\\d{2})$", "$1:$2"));
                durationNanos = Duration.between(start, end).toNanos();
            } catch (Exception e) {
                logger.warn("Failed to parse step timestamps: {} - {}", startTimeStr, endTimeStr);
            }
        }
        result.put("duration", durationNanos);
        
        // Add error message if step failed
        Object logsObj = stepData.get("logs");
        if (!status.equalsIgnoreCase("PASSED") && logsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> logs = (List<Map<String, Object>>) logsObj;
            for (Map<String, Object> log : logs) {
                String level = (String) log.get("level");
                if ("FAILED".equalsIgnoreCase(level)) {
                    result.put("error_message", log.get("message"));
                    break;
                }
            }
        }
        
        step.put("result", result);
        
        // Match (with arguments extraction)
        Map<String, Object> match = new LinkedHashMap<>();
        
        // Try to get match info from step data (stored by BDDExecutor)
        Object matchInfoObj = stepData.get("matchInfo");
        if (matchInfoObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> matchInfo = (Map<String, Object>) matchInfoObj;
            
            // Get method and parameters from matchInfo
            Object methodObj = matchInfo.get("method");
            Object parametersObj = matchInfo.get("parameters");
            
            if (methodObj instanceof java.lang.reflect.Method) {
                java.lang.reflect.Method method = (java.lang.reflect.Method) methodObj;
                
                // Build location with full method signature
                String className = method.getDeclaringClass().getSimpleName();
                String methodName = method.getName();
                
                // Build parameter types string
                StringBuilder locationBuilder = new StringBuilder();
                locationBuilder.append(className).append(".").append(methodName).append("(");
                
                Class<?>[] paramTypes = method.getParameterTypes();
                for (int i = 0; i < paramTypes.length; i++) {
                    if (i > 0) locationBuilder.append(",");
                    locationBuilder.append(paramTypes[i].getSimpleName());
                }
                locationBuilder.append(")");
                
                match.put("location", locationBuilder.toString());
                
                // Extract arguments with offsets
                if (parametersObj instanceof List && stepInfo != null && stepInfo.text != null) {
                    @SuppressWarnings("unchecked")
                    List<Object> parameters = (List<Object>) parametersObj;
                    
                    List<Map<String, Object>> arguments = extractArgumentsWithOffsets(stepName, stepInfo.text, parameters);
                    if (!arguments.isEmpty()) {
                        match.put("arguments", arguments);
                    }
                }
            } else {
                // Fallback if method not available
                match.put("location", "StepDefinitions." + stepName.replace(" ", "_").toLowerCase() + "()");
            }
        } else {
            // Fallback: use old method for backward compatibility
            match.put("location", "StepDefinitions." + stepName.replace(" ", "_").toLowerCase() + "()");
            
            // Extract arguments from step name (parameters that were captured)
            // Compare stepName with stepInfo.text to find placeholders like <role>, <fitur>
            List<Map<String, Object>> arguments = new ArrayList<>();
            if (stepInfo != null && stepInfo.text != null) {
                arguments = extractStepArguments(stepName, stepInfo.text);
            }
            if (!arguments.isEmpty()) {
                match.put("arguments", arguments);
            }
        }
        
        step.put("match", match);
        
        return step;
    }
    
    /**
     * Extract arguments with their offsets from executed step vs step definition
     * Uses actual parameter values from execution
     */
    private List<Map<String, Object>> extractArgumentsWithOffsets(String executedStep, String stepDefinition, List<Object> parameters) {
        List<Map<String, Object>> arguments = new ArrayList<>();
        
        // Find placeholders in step definition (e.g., <role>, <fitur>)
        java.util.regex.Pattern placeholderPattern = java.util.regex.Pattern.compile("<([^>]+)>");
        java.util.regex.Matcher matcher = placeholderPattern.matcher(stepDefinition);
        
        int paramIndex = 0;
        int searchFrom = 0;
        
        while (matcher.find() && paramIndex < parameters.size()) {
            Object paramValue = parameters.get(paramIndex);
            String paramString = paramValue != null ? paramValue.toString() : "";
            
            // Find where this parameter appears in the executed step
            int offset = executedStep.indexOf(paramString, searchFrom);
            if (offset >= 0) {
                Map<String, Object> arg = new LinkedHashMap<>();
                arg.put("val", paramString);
                arg.put("offset", offset);
                arguments.add(arg);
                
                searchFrom = offset + paramString.length();
            }
            
            paramIndex++;
        }
        
        return arguments;
    }
    
    /**
     * Extract arguments from step execution vs step definition
     * Example: "Login qcash role Maker dengan All Fitur" vs "Login qcash role <role> dengan <fitur>"
     * Returns: [{"val": "Maker", "offset": 17}, {"val": "All Fitur", "offset": 30}]
     */
    private List<Map<String, Object>> extractStepArguments(String executedStep, String stepDefinition) {
        List<Map<String, Object>> arguments = new ArrayList<>();
        
        // Find placeholders in step definition (e.g., <role>, <fitur>)
        java.util.regex.Pattern placeholderPattern = java.util.regex.Pattern.compile("<([^>]+)>");
        java.util.regex.Matcher matcher = placeholderPattern.matcher(stepDefinition);
        
        int defIndex = 0;
        int execIndex = 0;
        
        while (matcher.find()) {
            String placeholder = matcher.group(0); // e.g., "<role>"
            int placeholderStart = matcher.start();
            
            // Match text before placeholder
            String textBefore = stepDefinition.substring(defIndex, placeholderStart);
            execIndex = executedStep.indexOf(textBefore, execIndex);
            if (execIndex < 0) break;
            execIndex += textBefore.length();
            
            // Find end of argument value in executed step
            // Look for next word boundary or end of string
            int argStart = execIndex;
            int argEnd = execIndex;
            
            // Find what comes after this placeholder in definition
            int nextPlaceholderIndex = defIndex;
            java.util.regex.Matcher nextMatcher = placeholderPattern.matcher(stepDefinition.substring(matcher.end()));
            String textAfter = "";
            if (nextMatcher.find()) {
                textAfter = stepDefinition.substring(matcher.end(), matcher.end() + nextMatcher.start()).trim();
            } else {
                textAfter = stepDefinition.substring(matcher.end()).trim();
            }
            
            // Find where textAfter appears in executed step
            if (!textAfter.isEmpty()) {
                argEnd = executedStep.indexOf(textAfter, argStart);
                if (argEnd < 0) argEnd = executedStep.length();
            } else {
                argEnd = executedStep.length();
            }
            
            String argValue = executedStep.substring(argStart, argEnd).trim();
            
            if (!argValue.isEmpty()) {
                Map<String, Object> arg = new LinkedHashMap<>();
                arg.put("val", argValue);
                arg.put("offset", argStart);
                arguments.add(arg);
            }
            
            defIndex = matcher.end();
            execIndex = argEnd;
        }
        
        return arguments;
    }
    
    /**
     * Generate Cucumber HTML report folder
     */
    private void generateCucumberHtml(Path featureDir, TestCaseResult tcResult) throws IOException {
        Path htmlDir = featureDir.resolve("html");
        Files.createDirectories(htmlDir);
        
        // index.html
        Files.writeString(htmlDir.resolve("index.html"), buildCucumberIndexHtml());
        
        // style.css
        Files.writeString(htmlDir.resolve("style.css"), getCucumberCss());
        
        // formatter.js
        Files.writeString(htmlDir.resolve("formatter.js"), getCucumberFormatterJs());
        
        // report.js (generated with test data)
        Files.writeString(htmlDir.resolve("report.js"), buildCucumberReportJs(tcResult));
        
        // jquery (minimal inline version)
        Files.writeString(htmlDir.resolve("jquery-1.8.2.min.js"), getMinimalJquery());
    }
    
    /**
     * Build Cucumber index.html
     */
    private String buildCucumberIndexHtml() {
        return "<!DOCTYPE html>\n" +
               "<html>\n" +
               "<head>\n" +
               "  <meta charset=\"utf-8\">\n" +
               "  <title>Cucumber Report</title>\n" +
               "  <link rel=\"stylesheet\" href=\"style.css\">\n" +
               "</head>\n" +
               "<body>\n" +
               "  <div class=\"cucumber-report\"></div>\n" +
               "  <script src=\"jquery-1.8.2.min.js\"></script>\n" +
               "  <script src=\"formatter.js\"></script>\n" +
               "  <script src=\"report.js\"></script>\n" +
               "</body>\n" +
               "</html>\n";
    }
    
    /**
     * Get Cucumber CSS styles
     */
    private String getCucumberCss() {
        return "body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; background: #f5f5f5; margin: 0; padding: 20px; }\n" +
               ".cucumber-report { max-width: 1200px; margin: 0 auto; background: white; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); overflow: hidden; }\n" +
               ".feature { border-bottom: 1px solid #eee; }\n" +
               ".feature-header { background: #5d54a4; color: white; padding: 20px; }\n" +
               ".feature-header h2 { margin: 0 0 5px 0; }\n" +
               ".feature-header .keyword { font-weight: normal; opacity: 0.8; }\n" +
               ".scenario { border-bottom: 1px solid #eee; }\n" +
               ".scenario-header { padding: 15px 20px; background: #f8f9fa; cursor: pointer; display: flex; align-items: center; gap: 10px; }\n" +
               ".scenario-header:hover { background: #e9ecef; }\n" +
               ".scenario-header .keyword { color: #666; font-weight: 500; }\n" +
               ".scenario-header .name { flex: 1; }\n" +
               ".scenario-header .status { padding: 4px 12px; border-radius: 12px; font-size: 12px; font-weight: 500; }\n" +
               ".scenario-header .status.passed { background: #d4edda; color: #155724; }\n" +
               ".scenario-header .status.failed { background: #f8d7da; color: #721c24; }\n" +
               ".scenario-body { padding: 0 20px 20px 20px; display: none; }\n" +
               ".scenario.expanded .scenario-body { display: block; }\n" +
               ".step { padding: 8px 0; border-bottom: 1px solid #f0f0f0; display: flex; align-items: flex-start; gap: 10px; }\n" +
               ".step:last-child { border-bottom: none; }\n" +
               ".step .keyword { color: #5d54a4; font-weight: 500; min-width: 60px; }\n" +
               ".step .name { flex: 1; }\n" +
               ".step .status { width: 10px; height: 10px; border-radius: 50%; margin-top: 5px; }\n" +
               ".step .status.passed { background: #28a745; }\n" +
               ".step .status.failed { background: #dc3545; }\n" +
               ".step .duration { color: #999; font-size: 12px; }\n" +
               ".step .error { background: #fff5f5; color: #721c24; padding: 10px; border-radius: 4px; margin-top: 5px; font-family: monospace; font-size: 12px; white-space: pre-wrap; }\n" +
               ".summary { padding: 20px; background: #f8f9fa; display: flex; gap: 20px; }\n" +
               ".summary-item { text-align: center; }\n" +
               ".summary-item .num { display: block; font-size: 24px; font-weight: bold; }\n" +
               ".summary-item .label { color: #666; font-size: 12px; }\n" +
               ".summary-item.passed .num { color: #28a745; }\n" +
               ".summary-item.failed .num { color: #dc3545; }\n";
    }
    
    /**
     * Get Cucumber formatter.js
     */
    private String getCucumberFormatterJs() {
        return "var defined = { 'undefined': 'undefined' };\n" +
               "var CucumberHTML = {};\n" +
               "CucumberHTML.DOMFormatter = function(rootNode) {\n" +
               "    this.rootNode = rootNode;\n" +
               "};\n" +
               "CucumberHTML.DOMFormatter.prototype.uri = function(uri) { this.currentUri = uri; };\n" +
               "CucumberHTML.DOMFormatter.prototype.feature = function(feature) {\n" +
               "    var div = document.createElement('div');\n" +
               "    div.className = 'feature';\n" +
               "    div.innerHTML = '<div class=\"feature-header\"><h2><span class=\"keyword\">' + feature.keyword + ':</span> ' + feature.name + '</h2></div><div class=\"feature-body\"></div>';\n" +
               "    this.currentFeature = div;\n" +
               "    this.featureBody = div.querySelector('.feature-body');\n" +
               "    this.rootNode.appendChild(div);\n" +
               "};\n" +
               "CucumberHTML.DOMFormatter.prototype.scenario = function(scenario) {\n" +
               "    var div = document.createElement('div');\n" +
               "    div.className = 'scenario';\n" +
               "    div.innerHTML = '<div class=\"scenario-header\"><span class=\"keyword\">' + scenario.keyword + ':</span><span class=\"name\">' + scenario.name + '</span><span class=\"status\"></span></div><div class=\"scenario-body\"></div>';\n" +
               "    div.querySelector('.scenario-header').onclick = function() { div.classList.toggle('expanded'); };\n" +
               "    this.currentScenario = div;\n" +
               "    this.scenarioBody = div.querySelector('.scenario-body');\n" +
               "    this.scenarioStatus = div.querySelector('.status');\n" +
               "    this.scenarioResult = 'passed';\n" +
               "    this.featureBody.appendChild(div);\n" +
               "};\n" +
               "CucumberHTML.DOMFormatter.prototype.step = function(step) {\n" +
               "    var div = document.createElement('div');\n" +
               "    div.className = 'step';\n" +
               "    div.innerHTML = '<span class=\"status\"></span><span class=\"keyword\">' + step.keyword + '</span><span class=\"name\">' + step.name + '</span><span class=\"duration\"></span>';\n" +
               "    this.currentStep = div;\n" +
               "    this.scenarioBody.appendChild(div);\n" +
               "};\n" +
               "CucumberHTML.DOMFormatter.prototype.match = function(match) { };\n" +
               "CucumberHTML.DOMFormatter.prototype.result = function(result) {\n" +
               "    var status = this.currentStep.querySelector('.status');\n" +
               "    status.className = 'status ' + result.status;\n" +
               "    if (result.duration) {\n" +
               "        var ms = result.duration / 1000000;\n" +
               "        this.currentStep.querySelector('.duration').textContent = ms < 1000 ? Math.round(ms) + 'ms' : (ms/1000).toFixed(2) + 's';\n" +
               "    }\n" +
               "    if (result.status === 'failed') {\n" +
               "        this.scenarioResult = 'failed';\n" +
               "        if (result.error_message) {\n" +
               "            var err = document.createElement('div');\n" +
               "            err.className = 'error';\n" +
               "            err.textContent = result.error_message;\n" +
               "            this.currentStep.appendChild(err);\n" +
               "        }\n" +
               "    }\n" +
               "    this.scenarioStatus.className = 'status ' + this.scenarioResult;\n" +
               "    this.scenarioStatus.textContent = this.scenarioResult;\n" +
               "};\n" +
               "CucumberHTML.DOMFormatter.prototype.embedding = function(mime, data) { };\n" +
               "CucumberHTML.DOMFormatter.prototype.before = function(before) { };\n" +
               "CucumberHTML.DOMFormatter.prototype.after = function(after) { };\n" +
               "CucumberHTML.DOMFormatter.prototype.eof = function() { };\n" +
               "CucumberHTML.DOMFormatter.prototype.done = function() { };\n";
    }
    
    /**
     * Build Cucumber report.js with test data
     */
    private String buildCucumberReportJs(TestCaseResult tcResult) {
        StringBuilder js = new StringBuilder();
        js.append("$(document).ready(function() {\n");
        js.append("  var formatter = new CucumberHTML.DOMFormatter($('.cucumber-report')[0]);\n");
        
        // Feature
        js.append("  formatter.feature({\"keyword\": \"Feature\", \"name\": \"").append(escapeJsonString(tcResult.getTestCaseName())).append("\"});\n");
        
        // Scenario
        String scenarioName = tcResult.getScenarioName() != null ? tcResult.getScenarioName() : tcResult.getTestCaseName();
        js.append("  formatter.scenario({\"keyword\": \"Scenario\", \"name\": \"").append(escapeJsonString(scenarioName)).append("\"});\n");
        
        // Steps
        for (TestCaseResult.StepResult stepResult : tcResult.getStepResults()) {
            String stepName = stepResult.getStepName();
            String keyword = "Given ";
            if (stepName.toLowerCase().startsWith("given ")) {
                keyword = "Given ";
                stepName = stepName.substring(6);
            } else if (stepName.toLowerCase().startsWith("when ")) {
                keyword = "When ";
                stepName = stepName.substring(5);
            } else if (stepName.toLowerCase().startsWith("then ")) {
                keyword = "Then ";
                stepName = stepName.substring(5);
            } else if (stepName.toLowerCase().startsWith("and ")) {
                keyword = "And ";
                stepName = stepName.substring(4);
            }
            
            js.append("  formatter.step({\"keyword\": \"").append(keyword).append("\", \"name\": \"").append(escapeJsonString(stepName)).append("\"});\n");
            js.append("  formatter.match({\"location\": \"\"});\n");
            
            long durationNanos = 0;
            if (stepResult.getStartTime() != null && stepResult.getEndTime() != null) {
                durationNanos = Duration.between(stepResult.getStartTime(), stepResult.getEndTime()).toNanos();
            }
            
            js.append("  formatter.result({\"duration\": ").append(durationNanos);
            js.append(", \"status\": \"").append(stepResult.isPassed() ? "passed" : "failed").append("\"");
            if (!stepResult.isPassed() && stepResult.getErrorMessage() != null) {
                js.append(", \"error_message\": \"").append(escapeJsonString(stepResult.getErrorMessage())).append("\"");
            }
            js.append("});\n");
        }
        
        js.append("});\n");
        return js.toString();
    }
    
    /**
     * Get minimal jQuery substitute
     */
    private String getMinimalJquery() {
        return "// Minimal jQuery-like for Cucumber report\n" +
               "(function(w,d){\n" +
               "  w.$ = function(s) {\n" +
               "    var el = typeof s === 'string' ? d.querySelector(s) : s;\n" +
               "    return {\n" +
               "      0: el,\n" +
               "      ready: function(fn) { if (d.readyState !== 'loading') fn(); else d.addEventListener('DOMContentLoaded', fn); }\n" +
               "    };\n" +
               "  };\n" +
               "  w.$.ready = function(fn) { $(d).ready(fn); };\n" +
               "})(window, document);\n";
    }
    
    /**
     * Find project name from .prj file in project directory.
     * Katalon projects have a .prj file with the project name (e.g., "bricams-addons-katalon.prj").
     */
    private String findProjectName(Path projectPath) {
        try {
            // List all .prj files in project root
            java.util.stream.Stream<Path> prjFiles = Files.list(projectPath)
                    .filter(path -> path.toString().endsWith(".prj"));
            
            // Get first .prj file
            java.util.Optional<Path> prjFile = prjFiles.findFirst();
            
            if (prjFile.isPresent()) {
                // Extract filename without .prj extension
                String fileName = prjFile.get().getFileName().toString();
                return fileName.substring(0, fileName.length() - 4); // Remove ".prj"
            }
        } catch (IOException e) {
            // Fallback to folder name if can't read directory
        }
        
        // Fallback: use folder name
        return projectPath.getFileName().toString();
    }
    
    /**
     * Parse .feature file to extract metadata (feature name, tags, scenarios, etc.)
     */
    private FeatureMetadata parseFeatureFile(TestCaseResult tcResult) {
        FeatureMetadata metadata = new FeatureMetadata();
        
        // Get absolute feature file path
        String featureFileRelative = tcResult.getFeatureFile();
        if (featureFileRelative == null || featureFileRelative.isEmpty()) {
            logger.warn("No feature file path in test result");
            metadata.featureName = tcResult.getTestCaseName();
            metadata.absoluteFeatureFilePath = "";
            return metadata;
        }
        
        // Clean up path - remove leading ./ or .\ 
        String cleanPath = featureFileRelative;
        while (cleanPath.startsWith("./") || cleanPath.startsWith(".\\")) {
            cleanPath = cleanPath.substring(2);
        }
        
        // Convert to absolute path
        Path featurePath = null;
        if (Paths.get(cleanPath).isAbsolute()) {
            featurePath = Paths.get(cleanPath).normalize();
            metadata.absoluteFeatureFilePath = featurePath.toString();
        } else {
            // Resolve relative to project path and NORMALIZE to remove . and ..
            if (projectPath != null) {
                featurePath = projectPath.resolve(cleanPath).normalize();
                metadata.absoluteFeatureFilePath = featurePath.toString();
            } else {
                metadata.absoluteFeatureFilePath = cleanPath;
            }
        }
        
        // Parse the feature file
        if (featurePath != null && Files.exists(featurePath)) {
            try {
                List<String> lines = Files.readAllLines(featurePath);
                parseFeatureFileLines(lines, metadata);
            } catch (IOException e) {
                logger.warn("Failed to read feature file: {}", featurePath, e);
                metadata.featureName = tcResult.getTestCaseName();
            }
        } else {
            logger.warn("Feature file not found: {}", featurePath);
            metadata.featureName = tcResult.getTestCaseName();
        }
        
        return metadata;
    }
    
    /**
     * Parse .feature file lines to extract metadata
     */
    private void parseFeatureFileLines(List<String> lines, FeatureMetadata metadata) {
        List<String> currentTags = new ArrayList<>();
        ScenarioInfo currentScenario = null;
        boolean inFeature = false;
        boolean inScenario = false;
        boolean inExamples = false;
        int exampleRowCount = 0;
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            int lineNumber = i + 1;
            
            // Skip empty lines and comments
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            // Parse tags
            if (line.startsWith("@")) {
                String[] tags = line.split("\\s+");
                for (String tag : tags) {
                    if (tag.startsWith("@")) {
                        currentTags.add(tag);
                    }
                }
                continue;
            }
            
            // Parse Feature
            if (line.startsWith("Feature:")) {
                metadata.featureLine = lineNumber;
                metadata.featureName = line.substring("Feature:".length()).trim();
                metadata.featureTags = convertTagsToMaps(currentTags, lineNumber - 1);
                currentTags.clear();
                inFeature = true;
                continue;
            }
            
            // Feature description (lines after Feature: before first Scenario)
            // PRESERVE ORIGINAL INDENT (don't use trimmed line)
            if (inFeature && !inScenario && !line.startsWith("Scenario") && !line.startsWith("@")) {
                String originalLine = lines.get(i); // Get original line with indent
                if (!originalLine.trim().isEmpty()) {
                    if (metadata.featureDescription.isEmpty()) {
                        metadata.featureDescription = originalLine;
                    } else {
                        metadata.featureDescription += "\n" + originalLine;
                    }
                }
                continue;
            }
            
            // Parse Scenario or Scenario Outline
            if (line.startsWith("Scenario:") || line.startsWith("Scenario Outline:")) {
                // Save previous scenario
                if (currentScenario != null) {
                    metadata.scenarios.add(currentScenario);
                }
                
                // Create new scenario
                currentScenario = new ScenarioInfo();
                currentScenario.line = lineNumber;
                currentScenario.isOutline = line.startsWith("Scenario Outline:");
                currentScenario.keyword = currentScenario.isOutline ? "Scenario Outline" : "Scenario";
                
                String scenarioPrefix = currentScenario.isOutline ? "Scenario Outline:" : "Scenario:";
                currentScenario.name = line.substring(scenarioPrefix.length()).trim();
                currentScenario.tags = convertTagsToMaps(currentTags, lineNumber - 1);
                currentTags.clear();
                
                inScenario = true;
                inExamples = false;
                exampleRowCount = 0;
                continue;
            }
            
            // Parse Examples section
            if (line.startsWith("Examples:")) {
                inExamples = true;
                exampleRowCount = 0;
                continue;
            }
            
            // Skip example rows (header + data rows)
            if (inExamples) {
                if (line.startsWith("|")) {
                    exampleRowCount++;
                    // If this is a data row (not header), clone scenario for this example
                    if (exampleRowCount > 1 && currentScenario != null && currentScenario.isOutline) {
                        ScenarioInfo exampleScenario = new ScenarioInfo();
                        exampleScenario.line = lineNumber; // Use actual Examples row line number
                        exampleScenario.name = currentScenario.name;
                        exampleScenario.keyword = currentScenario.keyword;
                        exampleScenario.isOutline = true;
                        exampleScenario.exampleIndex = exampleRowCount - 2; // -2 because first is header
                        exampleScenario.tags = new ArrayList<>(currentScenario.tags);
                        exampleScenario.steps = new ArrayList<>(currentScenario.steps);
                        metadata.scenarios.add(exampleScenario);
                    }
                }
                continue;
            }
            
            // Parse steps (Given, When, Then, And, But)
            if (inScenario && (line.startsWith("Given ") || line.startsWith("When ") || 
                              line.startsWith("Then ") || line.startsWith("And ") || line.startsWith("But "))) {
                if (currentScenario != null) {
                    StepInfo step = new StepInfo();
                    step.line = lineNumber;
                    
                    // Extract keyword
                    if (line.startsWith("Given ")) {
                        step.keyword = "Given ";
                        step.text = line.substring(6);
                    } else if (line.startsWith("When ")) {
                        step.keyword = "When ";
                        step.text = line.substring(5);
                    } else if (line.startsWith("Then ")) {
                        step.keyword = "Then ";
                        step.text = line.substring(5);
                    } else if (line.startsWith("And ")) {
                        step.keyword = "And ";
                        step.text = line.substring(4);
                    } else if (line.startsWith("But ")) {
                        step.keyword = "But ";
                        step.text = line.substring(4);
                    }
                    
                    currentScenario.steps.add(step);
                }
            }
        }
        
        // Save last scenario
        if (currentScenario != null) {
            metadata.scenarios.add(currentScenario);
        }
        
        // Set defaults if not parsed
        if (metadata.featureName == null || metadata.featureName.isEmpty()) {
            metadata.featureName = "Unknown Feature";
        }
    }
    
    /**
     * Convert tag strings to Cucumber tag maps
     */
    private List<Map<String, Object>> convertTagsToMaps(List<String> tagStrings, int lineNumber) {
        List<Map<String, Object>> tags = new ArrayList<>();
        for (int i = 0; i < tagStrings.size(); i++) {
            Map<String, Object> tag = new LinkedHashMap<>();
            tag.put("name", tagStrings.get(i));
            tag.put("type", "Tag");
            
            Map<String, Integer> location = new LinkedHashMap<>();
            location.put("line", lineNumber);
            location.put("column", i == 0 ? 1 : (tagStrings.get(i - 1).length() + 2)); // Estimate column
            tag.put("location", location);
            
            tags.add(tag);
        }
        return tags;
    }
    
    /**
     * Feature metadata parsed from .feature file
     */
    private static class FeatureMetadata {
        String featureName = "";
        String featureDescription = "";
        int featureLine = 1;
        String absoluteFeatureFilePath = "";
        List<Map<String, Object>> featureTags = new ArrayList<>();
        List<ScenarioInfo> scenarios = new ArrayList<>();
        
        /**
         * Find scenario info by matching name
         */
        ScenarioInfo findScenario(String scenarioName) {
            // Try exact match first
            for (ScenarioInfo scenario : scenarios) {
                if (scenario.name.equals(scenarioName)) {
                    return scenario;
                }
            }
            
            // Try partial match (scenario name might have parameters substituted)
            for (ScenarioInfo scenario : scenarios) {
                if (scenarioName.contains(scenario.name) || scenario.name.contains(scenarioName)) {
                    return scenario;
                }
            }
            
            // Fallback: return default
            ScenarioInfo fallback = new ScenarioInfo();
            fallback.line = 3;
            fallback.keyword = "Scenario";
            fallback.name = scenarioName;
            return fallback;
        }
    }
    
    /**
     * Scenario metadata
     */
    private static class ScenarioInfo {
        int line = 3;
        String name = "";
        String keyword = "Scenario";
        boolean isOutline = false;
        int exampleIndex = 0; // For Scenario Outline, which example row (0-based)
        List<Map<String, Object>> tags = new ArrayList<>();
        List<StepInfo> steps = new ArrayList<>();
    }
    
    /**
     * Step metadata
     */
    private static class StepInfo {
        int line = 4;
        String keyword = "Given ";
        String text = "";
    }
}
