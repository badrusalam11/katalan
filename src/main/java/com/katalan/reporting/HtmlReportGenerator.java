package com.katalan.reporting;

import com.katalan.core.model.*;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * HTML Report Generator - Generates beautiful HTML reports
 */
public class HtmlReportGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(HtmlReportGenerator.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    
    private final Path reportPath;
    private Configuration freemarkerConfig;
    
    public HtmlReportGenerator(Path reportPath) {
        this.reportPath = reportPath;
        initFreemarker();
    }
    
    private void initFreemarker() {
        freemarkerConfig = new Configuration(Configuration.VERSION_2_3_32);
        freemarkerConfig.setClassForTemplateLoading(getClass(), "/templates");
        freemarkerConfig.setDefaultEncoding("UTF-8");
        freemarkerConfig.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        freemarkerConfig.setLogTemplateExceptions(false);
    }
    
    /**
     * Generate HTML report from execution result
     */
    public void generateReport(ExecutionResult result) throws IOException {
        logger.info("Generating HTML report");
        
        Files.createDirectories(reportPath);
        
        // Generate main report
        generateMainReport(result);
        
        // Generate suite reports
        for (TestSuiteResult suiteResult : result.getSuiteResults()) {
            generateSuiteReport(suiteResult);
        }
        
        // Generate CSS file
        generateCssFile();
        
        logger.info("HTML report generated at: {}", reportPath);
    }
    
    /**
     * Generate main report page
     */
    private void generateMainReport(ExecutionResult result) throws IOException {
        Map<String, Object> model = new HashMap<>();
        model.put("result", result);
        model.put("startTime", formatTime(result.getStartTime()));
        model.put("endTime", formatTime(result.getEndTime()));
        model.put("duration", formatDuration(result.getDuration()));
        model.put("passRate", String.format("%.1f", result.getPassRate()));
        model.put("generatedAt", formatTime(Instant.now()));
        
        String html = generateFromTemplate(model);
        Files.writeString(reportPath.resolve("index.html"), html);
    }
    
    /**
     * Generate suite report page
     */
    private void generateSuiteReport(TestSuiteResult suiteResult) throws IOException {
        Map<String, Object> model = new HashMap<>();
        model.put("suite", suiteResult);
        model.put("startTime", formatTime(suiteResult.getStartTime()));
        model.put("endTime", formatTime(suiteResult.getEndTime()));
        model.put("duration", formatDuration(suiteResult.getDuration()));
        model.put("passRate", String.format("%.1f", suiteResult.getPassRate()));
        
        String html = generateSuiteTemplate(model);
        Files.writeString(reportPath.resolve(suiteResult.getSuiteId() + ".html"), html);
    }
    
    /**
     * Generate CSS file
     */
    private void generateCssFile() throws IOException {
        String css = getEmbeddedCss();
        Files.writeString(reportPath.resolve("style.css"), css);
    }
    
    /**
     * Generate main report HTML
     */
    private String generateFromTemplate(Map<String, Object> model) {
        ExecutionResult result = (ExecutionResult) model.get("result");
        
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>katalan Test Report</title>\n");
        html.append("    <link rel=\"stylesheet\" href=\"style.css\">\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"container\">\n");
        html.append("        <header>\n");
        html.append("            <h1>🧪 katalan Test Report</h1>\n");
        html.append("            <p class=\"subtitle\">").append(model.get("generatedAt")).append("</p>\n");
        html.append("        </header>\n");
        html.append("\n");
        html.append("        <section class=\"summary\">\n");
        html.append("            <div class=\"summary-card total\">\n");
        html.append("                <span class=\"number\">").append(result.getTotalTests()).append("</span>\n");
        html.append("                <span class=\"label\">Total Tests</span>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"summary-card passed\">\n");
        html.append("                <span class=\"number\">").append(result.getPassedTests()).append("</span>\n");
        html.append("                <span class=\"label\">Passed</span>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"summary-card failed\">\n");
        html.append("                <span class=\"number\">").append(result.getFailedTests()).append("</span>\n");
        html.append("                <span class=\"label\">Failed</span>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"summary-card error\">\n");
        html.append("                <span class=\"number\">").append(result.getErrorTests()).append("</span>\n");
        html.append("                <span class=\"label\">Errors</span>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"summary-card skipped\">\n");
        html.append("                <span class=\"number\">").append(result.getSkippedTests()).append("</span>\n");
        html.append("                <span class=\"label\">Skipped</span>\n");
        html.append("            </div>\n");
        html.append("        </section>\n");
        html.append("\n");
        html.append("        <section class=\"info\">\n");
        html.append("            <div class=\"info-row\">\n");
        html.append("                <span class=\"info-label\">Pass Rate:</span>\n");
        html.append("                <span class=\"info-value\">").append(model.get("passRate")).append("%</span>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"info-row\">\n");
        html.append("                <span class=\"info-label\">Duration:</span>\n");
        html.append("                <span class=\"info-value\">").append(model.get("duration")).append("</span>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"info-row\">\n");
        html.append("                <span class=\"info-label\">Start Time:</span>\n");
        html.append("                <span class=\"info-value\">").append(model.get("startTime")).append("</span>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"info-row\">\n");
        html.append("                <span class=\"info-label\">End Time:</span>\n");
        html.append("                <span class=\"info-value\">").append(model.get("endTime")).append("</span>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"info-row\">\n");
        html.append("                <span class=\"info-label\">Browser:</span>\n");
        html.append("                <span class=\"info-value\">").append(result.getBrowserName() != null ? result.getBrowserName() : "N/A").append("</span>\n");
        html.append("            </div>\n");
        html.append("        </section>\n");
        html.append("\n");
        html.append("        <section class=\"progress-section\">\n");
        html.append("            <div class=\"progress-bar\">\n");
        
        double passPercent = result.getTotalTests() > 0 ? (double) result.getPassedTests() / result.getTotalTests() * 100 : 0;
        double failPercent = result.getTotalTests() > 0 ? (double) result.getFailedTests() / result.getTotalTests() * 100 : 0;
        double errorPercent = result.getTotalTests() > 0 ? (double) result.getErrorTests() / result.getTotalTests() * 100 : 0;
        
        html.append("                <div class=\"progress passed\" style=\"width: ").append(String.format("%.1f", passPercent)).append("%\"></div>\n");
        html.append("                <div class=\"progress failed\" style=\"width: ").append(String.format("%.1f", failPercent)).append("%\"></div>\n");
        html.append("                <div class=\"progress error\" style=\"width: ").append(String.format("%.1f", errorPercent)).append("%\"></div>\n");
        html.append("            </div>\n");
        html.append("        </section>\n");
        html.append("\n");
        html.append("        <section class=\"suites\">\n");
        html.append("            <h2>Test Suites</h2>\n");
        html.append("            <table>\n");
        html.append("                <thead>\n");
        html.append("                    <tr>\n");
        html.append("                        <th>Suite Name</th>\n");
        html.append("                        <th>Total</th>\n");
        html.append("                        <th>Passed</th>\n");
        html.append("                        <th>Failed</th>\n");
        html.append("                        <th>Duration</th>\n");
        html.append("                        <th>Pass Rate</th>\n");
        html.append("                    </tr>\n");
        html.append("                </thead>\n");
        html.append("                <tbody>\n");
        
        for (TestSuiteResult suite : result.getSuiteResults()) {
            String rowClass = suite.isSuccess() ? "passed" : "failed";
            html.append("                    <tr class=\"").append(rowClass).append("\">\n");
            html.append("                        <td><a href=\"").append(suite.getSuiteId()).append(".html\">").append(suite.getSuiteName()).append("</a></td>\n");
            html.append("                        <td>").append(suite.getTotalTests()).append("</td>\n");
            html.append("                        <td>").append(suite.getPassedTests()).append("</td>\n");
            html.append("                        <td>").append(suite.getFailedTests() + suite.getErrorTests()).append("</td>\n");
            html.append("                        <td>").append(formatDuration(suite.getDuration())).append("</td>\n");
            html.append("                        <td>").append(String.format("%.1f", suite.getPassRate())).append("%</td>\n");
            html.append("                    </tr>\n");
        }
        
        html.append("                </tbody>\n");
        html.append("            </table>\n");
        html.append("        </section>\n");
        html.append("\n");
        html.append("        <footer>\n");
        html.append("            <p>Generated by katalan Runner v1.0.0</p>\n");
        html.append("        </footer>\n");
        html.append("    </div>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        
        return html.toString();
    }
    
    /**
     * Generate suite report HTML
     */
    private String generateSuiteTemplate(Map<String, Object> model) {
        TestSuiteResult suite = (TestSuiteResult) model.get("suite");
        
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>").append(suite.getSuiteName()).append(" - katalan Report</title>\n");
        html.append("    <link rel=\"stylesheet\" href=\"style.css\">\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"container\">\n");
        html.append("        <header>\n");
        html.append("            <h1>📋 ").append(suite.getSuiteName()).append("</h1>\n");
        html.append("            <p><a href=\"index.html\">← Back to Summary</a></p>\n");
        html.append("        </header>\n");
        html.append("\n");
        html.append("        <section class=\"summary\">\n");
        html.append("            <div class=\"summary-card total\">\n");
        html.append("                <span class=\"number\">").append(suite.getTotalTests()).append("</span>\n");
        html.append("                <span class=\"label\">Total</span>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"summary-card passed\">\n");
        html.append("                <span class=\"number\">").append(suite.getPassedTests()).append("</span>\n");
        html.append("                <span class=\"label\">Passed</span>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"summary-card failed\">\n");
        html.append("                <span class=\"number\">").append(suite.getFailedTests()).append("</span>\n");
        html.append("                <span class=\"label\">Failed</span>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"summary-card error\">\n");
        html.append("                <span class=\"number\">").append(suite.getErrorTests()).append("</span>\n");
        html.append("                <span class=\"label\">Errors</span>\n");
        html.append("            </div>\n");
        html.append("        </section>\n");
        html.append("\n");
        html.append("        <section class=\"test-cases\">\n");
        html.append("            <h2>Test Cases</h2>\n");
        html.append("            <table>\n");
        html.append("                <thead>\n");
        html.append("                    <tr>\n");
        html.append("                        <th>Status</th>\n");
        html.append("                        <th>Test Case</th>\n");
        html.append("                        <th>Duration</th>\n");
        html.append("                        <th>Retries</th>\n");
        html.append("                    </tr>\n");
        html.append("                </thead>\n");
        html.append("                <tbody>\n");
        
        for (TestCaseResult tc : suite.getTestCaseResults()) {
            String statusClass = tc.getStatus().name().toLowerCase();
            String statusIcon = getStatusIcon(tc.getStatus());
            html.append("                    <tr class=\"").append(statusClass).append("\">\n");
            html.append("                        <td>").append(statusIcon).append(" ").append(tc.getStatus()).append("</td>\n");
            html.append("                        <td>").append(tc.getTestCaseName()).append("</td>\n");
            html.append("                        <td>").append(tc.getDurationFormatted()).append("</td>\n");
            html.append("                        <td>").append(tc.getRetryAttempt()).append("</td>\n");
            html.append("                    </tr>\n");
            
            // Add error message row if failed
            if (tc.getStatus() == TestCase.TestCaseStatus.FAILED || tc.getStatus() == TestCase.TestCaseStatus.ERROR) {
                html.append("                    <tr class=\"error-detail\">\n");
                html.append("                        <td colspan=\"4\">\n");
                html.append("                            <div class=\"error-message\">\n");
                html.append("                                <strong>Error:</strong> ").append(escapeHtml(tc.getErrorMessage())).append("\n");
                if (tc.getStackTrace() != null) {
                    html.append("                                <details>\n");
                    html.append("                                    <summary>Stack Trace</summary>\n");
                    html.append("                                    <pre>").append(escapeHtml(tc.getStackTrace())).append("</pre>\n");
                    html.append("                                </details>\n");
                }
                html.append("                            </div>\n");
                html.append("                        </td>\n");
                html.append("                    </tr>\n");
            }
            
            // Add screenshot row if available
            if (!tc.getScreenshotPaths().isEmpty()) {
                html.append("                    <tr class=\"screenshot-row\">\n");
                html.append("                        <td colspan=\"4\">\n");
                for (String screenshotPath : tc.getScreenshotPaths()) {
                    html.append("                            <img src=\"").append(screenshotPath).append("\" class=\"screenshot\" alt=\"Screenshot\">\n");
                }
                html.append("                        </td>\n");
                html.append("                    </tr>\n");
            }
        }
        
        html.append("                </tbody>\n");
        html.append("            </table>\n");
        html.append("        </section>\n");
        html.append("\n");
        html.append("        <footer>\n");
        html.append("            <p>Generated by katalan Runner v1.0.0</p>\n");
        html.append("        </footer>\n");
        html.append("    </div>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        
        return html.toString();
    }
    
    /**
     * Get status icon
     */
    private String getStatusIcon(TestCase.TestCaseStatus status) {
        switch (status) {
            case PASSED:
                return "✅";
            case FAILED:
                return "❌";
            case ERROR:
                return "💥";
            case SKIPPED:
                return "⏭️";
            case RUNNING:
                return "🔄";
            default:
                return "⏸️";
        }
    }
    
    /**
     * Get embedded CSS
     */
    private String getEmbeddedCss() {
        return "/* katalan Report Styles */\n" +
                ":root {\n" +
                "    --color-passed: #28a745;\n" +
                "    --color-failed: #dc3545;\n" +
                "    --color-error: #fd7e14;\n" +
                "    --color-skipped: #6c757d;\n" +
                "    --color-primary: #007bff;\n" +
                "    --color-bg: #f8f9fa;\n" +
                "    --color-card: #ffffff;\n" +
                "    --color-text: #212529;\n" +
                "    --shadow: 0 2px 8px rgba(0,0,0,0.1);\n" +
                "}\n" +
                "\n" +
                "* {\n" +
                "    box-sizing: border-box;\n" +
                "    margin: 0;\n" +
                "    padding: 0;\n" +
                "}\n" +
                "\n" +
                "body {\n" +
                "    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;\n" +
                "    background-color: var(--color-bg);\n" +
                "    color: var(--color-text);\n" +
                "    line-height: 1.6;\n" +
                "}\n" +
                "\n" +
                ".container {\n" +
                "    max-width: 1200px;\n" +
                "    margin: 0 auto;\n" +
                "    padding: 20px;\n" +
                "}\n" +
                "\n" +
                "header {\n" +
                "    text-align: center;\n" +
                "    margin-bottom: 30px;\n" +
                "    padding: 30px;\n" +
                "    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
                "    color: white;\n" +
                "    border-radius: 12px;\n" +
                "    box-shadow: var(--shadow);\n" +
                "}\n" +
                "\n" +
                "header h1 {\n" +
                "    font-size: 2.5em;\n" +
                "    margin-bottom: 10px;\n" +
                "}\n" +
                "\n" +
                "header a {\n" +
                "    color: white;\n" +
                "    text-decoration: none;\n" +
                "}\n" +
                "\n" +
                ".subtitle {\n" +
                "    opacity: 0.9;\n" +
                "}\n" +
                "\n" +
                ".summary {\n" +
                "    display: flex;\n" +
                "    gap: 20px;\n" +
                "    margin-bottom: 30px;\n" +
                "    flex-wrap: wrap;\n" +
                "    justify-content: center;\n" +
                "}\n" +
                "\n" +
                ".summary-card {\n" +
                "    background: var(--color-card);\n" +
                "    padding: 25px 35px;\n" +
                "    border-radius: 12px;\n" +
                "    text-align: center;\n" +
                "    box-shadow: var(--shadow);\n" +
                "    min-width: 140px;\n" +
                "}\n" +
                "\n" +
                ".summary-card .number {\n" +
                "    display: block;\n" +
                "    font-size: 2.5em;\n" +
                "    font-weight: bold;\n" +
                "}\n" +
                "\n" +
                ".summary-card .label {\n" +
                "    color: #666;\n" +
                "    font-size: 0.9em;\n" +
                "    text-transform: uppercase;\n" +
                "    letter-spacing: 1px;\n" +
                "}\n" +
                "\n" +
                ".summary-card.total .number { color: var(--color-primary); }\n" +
                ".summary-card.passed .number { color: var(--color-passed); }\n" +
                ".summary-card.failed .number { color: var(--color-failed); }\n" +
                ".summary-card.error .number { color: var(--color-error); }\n" +
                ".summary-card.skipped .number { color: var(--color-skipped); }\n" +
                "\n" +
                ".info {\n" +
                "    background: var(--color-card);\n" +
                "    padding: 20px 30px;\n" +
                "    border-radius: 12px;\n" +
                "    margin-bottom: 30px;\n" +
                "    box-shadow: var(--shadow);\n" +
                "}\n" +
                "\n" +
                ".info-row {\n" +
                "    display: flex;\n" +
                "    justify-content: space-between;\n" +
                "    padding: 8px 0;\n" +
                "    border-bottom: 1px solid #eee;\n" +
                "}\n" +
                "\n" +
                ".info-row:last-child {\n" +
                "    border-bottom: none;\n" +
                "}\n" +
                "\n" +
                ".info-label {\n" +
                "    font-weight: 500;\n" +
                "    color: #666;\n" +
                "}\n" +
                "\n" +
                ".progress-section {\n" +
                "    margin-bottom: 30px;\n" +
                "}\n" +
                "\n" +
                ".progress-bar {\n" +
                "    display: flex;\n" +
                "    height: 20px;\n" +
                "    border-radius: 10px;\n" +
                "    overflow: hidden;\n" +
                "    background: #eee;\n" +
                "}\n" +
                "\n" +
                ".progress {\n" +
                "    height: 100%;\n" +
                "}\n" +
                "\n" +
                ".progress.passed { background: var(--color-passed); }\n" +
                ".progress.failed { background: var(--color-failed); }\n" +
                ".progress.error { background: var(--color-error); }\n" +
                "\n" +
                ".suites, .test-cases {\n" +
                "    background: var(--color-card);\n" +
                "    padding: 20px 30px;\n" +
                "    border-radius: 12px;\n" +
                "    margin-bottom: 30px;\n" +
                "    box-shadow: var(--shadow);\n" +
                "}\n" +
                "\n" +
                ".suites h2, .test-cases h2 {\n" +
                "    margin-bottom: 20px;\n" +
                "    padding-bottom: 10px;\n" +
                "    border-bottom: 2px solid #eee;\n" +
                "}\n" +
                "\n" +
                "table {\n" +
                "    width: 100%;\n" +
                "    border-collapse: collapse;\n" +
                "}\n" +
                "\n" +
                "th, td {\n" +
                "    padding: 12px 15px;\n" +
                "    text-align: left;\n" +
                "    border-bottom: 1px solid #eee;\n" +
                "}\n" +
                "\n" +
                "th {\n" +
                "    background: #f8f9fa;\n" +
                "    font-weight: 600;\n" +
                "    color: #666;\n" +
                "    text-transform: uppercase;\n" +
                "    font-size: 0.85em;\n" +
                "    letter-spacing: 0.5px;\n" +
                "}\n" +
                "\n" +
                "tr.passed td:first-child { border-left: 4px solid var(--color-passed); }\n" +
                "tr.failed td:first-child { border-left: 4px solid var(--color-failed); }\n" +
                "tr.error td:first-child { border-left: 4px solid var(--color-error); }\n" +
                "tr.skipped td:first-child { border-left: 4px solid var(--color-skipped); }\n" +
                "\n" +
                "tr:hover {\n" +
                "    background: #f8f9fa;\n" +
                "}\n" +
                "\n" +
                "a {\n" +
                "    color: var(--color-primary);\n" +
                "    text-decoration: none;\n" +
                "}\n" +
                "\n" +
                "a:hover {\n" +
                "    text-decoration: underline;\n" +
                "}\n" +
                "\n" +
                ".error-detail {\n" +
                "    background: #fff3f3 !important;\n" +
                "}\n" +
                "\n" +
                ".error-message {\n" +
                "    padding: 15px;\n" +
                "    background: #ffebee;\n" +
                "    border-radius: 8px;\n" +
                "    font-family: monospace;\n" +
                "    font-size: 0.9em;\n" +
                "    overflow-x: auto;\n" +
                "}\n" +
                "\n" +
                ".error-message pre {\n" +
                "    margin-top: 10px;\n" +
                "    padding: 10px;\n" +
                "    background: #1e1e1e;\n" +
                "    color: #e0e0e0;\n" +
                "    border-radius: 6px;\n" +
                "    overflow-x: auto;\n" +
                "    font-size: 0.85em;\n" +
                "}\n" +
                "\n" +
                "details summary {\n" +
                "    cursor: pointer;\n" +
                "    color: var(--color-failed);\n" +
                "    margin-top: 10px;\n" +
                "}\n" +
                "\n" +
                ".screenshot {\n" +
                "    max-width: 100%;\n" +
                "    max-height: 400px;\n" +
                "    border-radius: 8px;\n" +
                "    box-shadow: var(--shadow);\n" +
                "    margin: 10px 0;\n" +
                "}\n" +
                "\n" +
                "footer {\n" +
                "    text-align: center;\n" +
                "    padding: 20px;\n" +
                "    color: #666;\n" +
                "    font-size: 0.9em;\n" +
                "}\n" +
                "\n" +
                "@media (max-width: 768px) {\n" +
                "    .summary {\n" +
                "        flex-direction: column;\n" +
                "    }\n" +
                "    \n" +
                "    .summary-card {\n" +
                "        min-width: auto;\n" +
                "    }\n" +
                "    \n" +
                "    table {\n" +
                "        font-size: 0.9em;\n" +
                "    }\n" +
                "    \n" +
                "    th, td {\n" +
                "        padding: 8px 10px;\n" +
                "    }\n" +
                "}\n";
    }
    
    /**
     * Format Instant to string
     */
    private String formatTime(Instant instant) {
        if (instant == null) {
            return "N/A";
        }
        return DATE_FORMATTER.format(instant);
    }
    
    /**
     * Format Duration to string
     */
    private String formatDuration(Duration duration) {
        if (duration == null) {
            return "N/A";
        }
        
        long seconds = duration.getSeconds();
        long millis = duration.toMillis() % 1000;
        
        if (seconds < 60) {
            return String.format("%d.%03ds", seconds, millis);
        }
        
        long minutes = seconds / 60;
        seconds = seconds % 60;
        
        if (minutes < 60) {
            return String.format("%dm %ds", minutes, seconds);
        }
        
        long hours = minutes / 60;
        minutes = minutes % 60;
        return String.format("%dh %dm %ds", hours, minutes, seconds);
    }
    
    /**
     * Escape HTML special characters
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
