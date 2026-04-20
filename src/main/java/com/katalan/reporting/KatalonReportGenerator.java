package com.katalan.reporting;

import com.katalan.core.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
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
    
    private final Path projectPath;
    private final String katalanVersion = "1.0.0";
    private Path currentReportDir;
    
    public KatalonReportGenerator(Path projectPath) {
        this.projectPath = projectPath;
    }
    
    /**
     * Generate Katalon-style reports
     * 
     * @param result The execution result
     * @return The path to the generated report folder
     */
    public Path generateReport(ExecutionResult result) throws IOException {
        String timestamp = TIMESTAMP_FORMATTER.format(result.getStartTime());
        
        // Create Katalon-style folder structure: Reports/<timestamp>/<SuiteName>/<timestamp>/
        String suiteName = result.getSuiteResults().isEmpty() ? "Test Suite" 
                : result.getSuiteResults().get(0).getSuiteName();
        
        Path reportsBaseDir = projectPath.resolve("Reports");
        Path timestampDir = reportsBaseDir.resolve(timestamp);
        Path suiteDir = timestampDir.resolve(suiteName);
        Path reportDir = suiteDir.resolve(timestamp);
        
        Files.createDirectories(reportDir);
        this.currentReportDir = reportDir;
        
        logger.info("Generating Katalon-style reports in: {}", reportDir);
        
        // Generate all report files
        generateHtmlReport(reportDir, timestamp, result);
        generateCsvReport(reportDir, timestamp, result);
        generateJUnitReport(reportDir, result);
        generateExecutionProperties(reportDir, result, suiteName);
        generateExecutionUuid(reportDir);
        generateConsoleLog(reportDir, result);
        generateExecutionLog(reportDir, result);
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
     * Generate JUnit XML report
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
        
        for (TestSuiteResult suiteResult : result.getSuiteResults()) {
            double suiteTime = suiteResult.getDuration() != null ? suiteResult.getDuration().toMillis() / 1000.0 : 0;
            
            xml.append("   <testsuite name=\"").append(escapeXml(suiteResult.getSuiteName())).append("\" ");
            xml.append("tests=\"").append(suiteResult.getTotalTests()).append("\" ");
            xml.append("failures=\"").append(suiteResult.getFailedTests()).append("\" ");
            xml.append("errors=\"").append(suiteResult.getErrorTests()).append("\" ");
            xml.append("time=\"").append(String.format("%.3f", suiteTime)).append("\" ");
            xml.append("skipped=\"").append(suiteResult.getSkippedTests()).append("\" ");
            xml.append("timestamp=\"").append(JUNIT_TIMESTAMP_FORMATTER.format(suiteResult.getStartTime())).append("\" ");
            xml.append("hostname=\"").append(escapeXml(getHostname())).append("\" ");
            xml.append("id=\"").append(escapeXml(suiteResult.getSuiteId())).append("\">\n");
            
            // Properties
            xml.append("      <properties>\n");
            xml.append("         <property name=\"os\" value=\"").append(escapeXml(System.getProperty("os.name"))).append("\"/>\n");
            xml.append("         <property name=\"katalanVersion\" value=\"").append(katalanVersion).append("\"/>\n");
            xml.append("         <property name=\"browser\" value=\"").append(escapeXml(result.getBrowserName() != null ? result.getBrowserName() : "Chrome")).append("\"/>\n");
            xml.append("         <property name=\"platform\" value=\"").append(escapeXml(System.getProperty("os.name"))).append("\"/>\n");
            xml.append("      </properties>\n");
            
            for (TestCaseResult tcResult : suiteResult.getTestCaseResults()) {
                double tcTime = tcResult.getDuration().toMillis() / 1000.0;
                
                xml.append("      <testcase name=\"").append(escapeXml(tcResult.getTestCaseName())).append("\" ");
                xml.append("time=\"").append(String.format("%.3f", tcTime)).append("\" ");
                xml.append("classname=\"").append(escapeXml(tcResult.getTestCaseName())).append("\" ");
                xml.append("status=\"").append(tcResult.getStatus()).append("\">\n");
                
                if (tcResult.getStatus() == TestCase.TestCaseStatus.FAILED) {
                    xml.append("         <failure message=\"").append(escapeXml(tcResult.getErrorMessage())).append("\">\n");
                    if (tcResult.getStackTrace() != null) {
                        xml.append(escapeXml(tcResult.getStackTrace())).append("\n");
                    }
                    xml.append("         </failure>\n");
                } else if (tcResult.getStatus() == TestCase.TestCaseStatus.ERROR) {
                    xml.append("         <error message=\"").append(escapeXml(tcResult.getErrorMessage())).append("\">\n");
                    if (tcResult.getStackTrace() != null) {
                        xml.append(escapeXml(tcResult.getStackTrace())).append("\n");
                    }
                    xml.append("         </error>\n");
                } else if (tcResult.getStatus() == TestCase.TestCaseStatus.SKIPPED) {
                    xml.append("         <skipped/>\n");
                }
                
                // System output (test steps log)
                xml.append("         <system-out><![CDATA[");
                xml.append(buildTestCaseLog(tcResult));
                xml.append("]]></system-out>\n");
                
                xml.append("      </testcase>\n");
            }
            
            xml.append("   </testsuite>\n");
        }
        
        xml.append("</testsuites>\n");
        
        Files.writeString(reportDir.resolve("JUnit_Report.xml"), xml.toString());
    }
    
    /**
     * Generate execution.properties file
     */
    private void generateExecutionProperties(Path reportDir, ExecutionResult result, String suiteName) throws IOException {
        Map<String, Object> properties = new LinkedHashMap<>();
        
        properties.put("Name", result.getBrowserName() != null ? result.getBrowserName() : "Chrome");
        properties.put("projectName", projectPath.getFileName().toString());
        properties.put("projectDir", projectPath.toString().replace("\\", "/"));
        
        // Host info
        Map<String, Object> host = new LinkedHashMap<>();
        host.put("hostName", getHostname());
        host.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        host.put("hostAddress", getHostAddress());
        properties.put("host", host);
        
        // Execution settings
        Map<String, Object> execution = new LinkedHashMap<>();
        Map<String, Object> general = new LinkedHashMap<>();
        general.put("timeout", 30);
        general.put("actionDelay", 0);
        general.put("defaultFailureHandling", "STOP_ON_FAILURE");
        general.put("reportFolder", reportDir.toString().replace("\\", "/"));
        execution.put("general", general);
        properties.put("execution", execution);
        
        properties.put("executedEntity", "TestSuite");
        properties.put("id", "Test Suites/" + suiteName);
        properties.put("name", suiteName);
        properties.put("source", projectPath.resolve("Test Suites").resolve(suiteName + ".ts").toString().replace("\\", "/"));
        properties.put("katalon.versionNumber", katalanVersion);
        properties.put("runningMode", "CLI");
        
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
                log.append(" INFO  c.k.katalan.core.main.TestCaseExecutor   - START ").append(tcResult.getTestCaseName()).append("\n");
                
                // Log each step
                for (TestCaseResult.StepResult step : tcResult.getStepResults()) {
                    Instant stepTime = step.getStartTime() != null ? step.getStartTime() : tcResult.getStartTime();
                    log.append(LOG_TIMESTAMP_FORMATTER.format(stepTime));
                    log.append(" DEBUG testcase.").append(tcResult.getTestCaseName().replace("/", "."));
                    log.append("                   - ").append(step.getStepNumber()).append(": ").append(step.getDescription() != null ? step.getDescription() : step.getStepName()).append("\n");
                    
                    Instant stepEndTime = step.getEndTime() != null ? step.getEndTime() : tcResult.getEndTime();
                    if (step.isPassed()) {
                        log.append(LOG_TIMESTAMP_FORMATTER.format(stepEndTime));
                        log.append(" INFO  [MESSAGE][PASSED] - ").append(step.getDescription() != null ? step.getDescription() : step.getStepName()).append("\n");
                    } else if (step.getErrorMessage() != null) {
                        log.append(LOG_TIMESTAMP_FORMATTER.format(stepEndTime));
                        log.append(" ERROR [MESSAGE][FAILED] - ").append(step.getErrorMessage()).append("\n");
                    }
                }
                
                log.append(LOG_TIMESTAMP_FORMATTER.format(tcResult.getEndTime()));
                log.append(" INFO  c.k.katalan.core.main.TestCaseExecutor   - END ").append(tcResult.getTestCaseName());
                log.append(" - ").append(tcResult.getStatus()).append("\n");
            }
        }
        
        log.append(LOG_TIMESTAMP_FORMATTER.format(result.getEndTime()));
        log.append(" INFO  c.k.katalan.core.main.TestSuiteExecutor  - END ").append(suiteName).append("\n");
        
        Files.writeString(reportDir.resolve("console0.log"), log.toString());
    }
    
    /**
     * Generate execution log file (similar to console but more detailed)
     */
    private void generateExecutionLog(Path reportDir, ExecutionResult result) throws IOException {
        StringBuilder log = new StringBuilder();
        
        log.append("================================================================================\n");
        log.append("                           KATALAN EXECUTION LOG\n");
        log.append("================================================================================\n\n");
        
        log.append("Start Time: ").append(REPORT_DATE_FORMATTER.format(result.getStartTime())).append("\n");
        log.append("End Time: ").append(REPORT_DATE_FORMATTER.format(result.getEndTime())).append("\n");
        log.append("Duration: ").append(formatDuration(result.getDuration())).append("\n");
        log.append("Total: ").append(result.getTotalTests()).append(" | ");
        log.append("Passed: ").append(result.getPassedTests()).append(" | ");
        log.append("Failed: ").append(result.getFailedTests()).append(" | ");
        log.append("Errors: ").append(result.getErrorTests()).append("\n\n");
        
        for (TestSuiteResult suiteResult : result.getSuiteResults()) {
            log.append("--------------------------------------------------------------------------------\n");
            log.append("Suite: ").append(suiteResult.getSuiteName()).append("\n");
            log.append("--------------------------------------------------------------------------------\n\n");
            
            for (TestCaseResult tcResult : suiteResult.getTestCaseResults()) {
                log.append("  Test Case: ").append(tcResult.getTestCaseName()).append("\n");
                log.append("  Status: ").append(tcResult.getStatus()).append("\n");
                log.append("  Duration: ").append(tcResult.getDurationFormatted()).append("\n");
                
                if (tcResult.getErrorMessage() != null && !tcResult.getErrorMessage().isEmpty()) {
                    log.append("  Error: ").append(tcResult.getErrorMessage()).append("\n");
                }
                
                log.append("\n");
            }
        }
        
        log.append("================================================================================\n");
        
        Files.writeString(reportDir.resolve("execution0.log"), log.toString());
    }
    
    /**
     * Generate testCaseBinding file
     */
    private void generateTestCaseBinding(Path reportDir, ExecutionResult result) throws IOException {
        StringBuilder binding = new StringBuilder();
        
        for (TestSuiteResult suiteResult : result.getSuiteResults()) {
            for (TestCaseResult tcResult : suiteResult.getTestCaseResults()) {
                binding.append(tcResult.getTestCaseName()).append("=").append(tcResult.getStatus()).append("\n");
            }
        }
        
        Files.writeString(reportDir.resolve("testCaseBinding"), binding.toString());
    }
    
    /**
     * Generate tsc_id.txt file (empty for non-test suite collection runs)
     */
    private void generateTscIdFile(Path reportDir) throws IOException {
        Files.writeString(reportDir.resolve("tsc_id.txt"), "");
    }
    
    /**
     * Build test case log for JUnit system-out
     */
    private String buildTestCaseLog(TestCaseResult tcResult) {
        StringBuilder log = new StringBuilder();
        
        log.append(JUNIT_TIMESTAMP_FORMATTER.format(tcResult.getStartTime()));
        log.append(" - [TEST_CASE][").append(tcResult.getStatus()).append("] - ");
        log.append(tcResult.getTestCaseName()).append(": ").append(tcResult.getTestCaseName()).append("\n\n");
        
        for (TestCaseResult.StepResult step : tcResult.getStepResults()) {
            Instant stepTime = step.getStartTime() != null ? step.getStartTime() : tcResult.getStartTime();
            log.append(JUNIT_TIMESTAMP_FORMATTER.format(stepTime));
            log.append(" - [TEST_STEP][").append(step.isPassed() ? "PASSED" : "FAILED").append("] - ");
            log.append(step.getDescription() != null ? step.getDescription() : step.getStepName()).append(": ");
            log.append(step.isPassed() ? "Step passed" : (step.getErrorMessage() != null ? step.getErrorMessage() : "Step failed"));
            log.append("\n\n");
        }
        
        return log.toString();
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
    
    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
    
    private String getHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    private String getHostAddress() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
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
        
        Path cucumberDir = reportDir.resolve("cucumber_report");
        Files.createDirectories(cucumberDir);
        
        if (bddTestCases.isEmpty()) {
            // Create empty cucumber_report folder to match Katalon behavior
            return;
        }
        
        // Generate one folder per BDD test case/feature
        for (TestCaseResult tcResult : bddTestCases) {
            String timestampId = String.valueOf(System.currentTimeMillis());
            Path featureReportDir = cucumberDir.resolve(timestampId);
            Files.createDirectories(featureReportDir);
            
            generateCucumberJson(featureReportDir, tcResult);
            generateCucumberXml(featureReportDir, tcResult);
            generateKCucumberJson(featureReportDir, tcResult);
            generateCucumberHtml(featureReportDir, tcResult);
            
            // Small delay to ensure unique timestamps
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        }
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
     * Generate cucumber.json file (standard Cucumber JSON format)
     */
    private void generateCucumberJson(Path featureDir, TestCaseResult tcResult) throws IOException {
        List<Map<String, Object>> features = new ArrayList<>();
        Map<String, Object> feature = new LinkedHashMap<>();
        
        feature.put("line", 1);
        feature.put("elements", buildCucumberElements(tcResult));
        feature.put("name", tcResult.getTestCaseName());
        feature.put("description", tcResult.getDescription() != null ? tcResult.getDescription() : "");
        feature.put("id", tcResult.getTestCaseName().toLowerCase().replace(" ", "-"));
        feature.put("keyword", "Feature");
        feature.put("uri", tcResult.getFeatureFile() != null ? tcResult.getFeatureFile() : "");
        
        List<Map<String, Object>> tags = new ArrayList<>();
        feature.put("tags", tags);
        
        features.add(feature);
        Files.writeString(featureDir.resolve("cucumber.json"), toJson(features));
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
     * Generate cucumber.xml (JUnit XML format for Cucumber)
     */
    private void generateCucumberXml(Path featureDir, TestCaseResult tcResult) throws IOException {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        
        long durationMs = tcResult.getEndTime() != null && tcResult.getStartTime() != null ?
                Duration.between(tcResult.getStartTime(), tcResult.getEndTime()).toMillis() : 0;
        
        int failed = tcResult.getStatus() == TestCase.TestCaseStatus.FAILED ? 1 : 0;
        int skipped = tcResult.getStatus() == TestCase.TestCaseStatus.SKIPPED ? 1 : 0;
        
        xml.append("<testsuite name=\"").append(escapeXml(tcResult.getTestCaseName())).append("\" ");
        xml.append("failures=\"").append(failed).append("\" ");
        xml.append("skipped=\"").append(skipped).append("\" ");
        xml.append("time=\"").append(String.format("%.3f", durationMs / 1000.0)).append("\" ");
        xml.append("tests=\"1\">\n");
        
        xml.append("  <testcase classname=\"").append(escapeXml(tcResult.getTestCaseName())).append("\" ");
        xml.append("name=\"").append(escapeXml(tcResult.getScenarioName() != null ? tcResult.getScenarioName() : tcResult.getTestCaseName())).append("\" ");
        xml.append("time=\"").append(String.format("%.3f", durationMs / 1000.0)).append("\"");
        
        if (tcResult.getStatus() == TestCase.TestCaseStatus.FAILED) {
            xml.append(">\n");
            xml.append("    <failure message=\"").append(escapeXml(tcResult.getErrorMessage())).append("\">");
            if (tcResult.getStackTrace() != null) {
                xml.append(escapeXml(tcResult.getStackTrace()));
            }
            xml.append("</failure>\n");
            xml.append("  </testcase>\n");
        } else if (tcResult.getStatus() == TestCase.TestCaseStatus.SKIPPED) {
            xml.append(">\n    <skipped/>\n  </testcase>\n");
        } else {
            xml.append("/>\n");
        }
        
        xml.append("</testsuite>\n");
        
        Files.writeString(featureDir.resolve("cucumber.xml"), xml.toString());
    }
    
    /**
     * Generate k-cucumber.json (Katalon enhanced Cucumber JSON with UUIDs)
     */
    private void generateKCucumberJson(Path featureDir, TestCaseResult tcResult) throws IOException {
        List<Map<String, Object>> features = new ArrayList<>();
        Map<String, Object> feature = new LinkedHashMap<>();
        
        feature.put("line", 1);
        feature.put("elements", buildKCucumberElements(tcResult));
        feature.put("name", tcResult.getTestCaseName());
        feature.put("description", tcResult.getDescription() != null ? tcResult.getDescription() : "");
        feature.put("id", tcResult.getTestCaseName().toLowerCase().replace(" ", "-"));
        feature.put("keyword", "Feature");
        feature.put("uri", tcResult.getFeatureFile() != null ? tcResult.getFeatureFile() : "");
        feature.put("tags", new ArrayList<>());
        
        features.add(feature);
        Files.writeString(featureDir.resolve("k-cucumber.json"), toJson(features));
    }
    
    /**
     * Build Katalon-enhanced Cucumber elements with UUIDs
     */
    private List<Map<String, Object>> buildKCucumberElements(TestCaseResult tcResult) {
        List<Map<String, Object>> elements = new ArrayList<>();
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
}
