package com.katalan.cli;

import com.katalan.core.config.RunConfiguration;
import com.katalan.core.engine.KatalanEngine;
import com.katalan.core.model.ExecutionResult;
import com.katalan.core.model.TestSuite;
import com.katalan.core.engine.TestSuiteParser;
import com.katalan.reporting.HtmlReportGenerator;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * katalan CLI - Command Line Interface for katalan Runner
 * 
 * Usage:
 *   katalan run -p /path/to/project -ts "Test Suites/MySuite"
 *   katalan run -tc /path/to/TestCase.groovy
 *   katalan run -p /path/to/project -ts "Test Suites/MySuite" --headless
 */
@Command(
    name = "katalan",
    mixinStandardHelpOptions = true,
    version = "katalan Runner 1.0.0",
    description = "Unofficial Katalon Test Runner - Execute Katalon automation scripts independently",
    subcommands = {
        KatalanCLI.RunCommand.class,
        KatalanCLI.InfoCommand.class
    }
)
public class KatalanCLI implements Callable<Integer> {
    
    public static void main(String[] args) {
        int exitCode = new CommandLine(new KatalanCLI()).execute(args);
        System.exit(exitCode);
    }
    
    @Override
    public Integer call() {
        // Show help when called without subcommand
        CommandLine.usage(this, System.out);
        return 0;
    }
    
    /**
     * Run subcommand - Execute tests
     */
    @Command(
        name = "run",
        description = "Run test cases or test suites",
        mixinStandardHelpOptions = true
    )
    static class RunCommand implements Callable<Integer> {
        
        @Option(names = {"-p", "--project"}, description = "Path to Katalon project folder")
        private Path projectPath;
        
        @Option(names = {"-ts", "--test-suite"}, description = "Test suite to run (relative path from project)")
        private String testSuite;
        
        @Option(names = {"-tc", "--test-case"}, description = "Test case file(s) to run (.groovy)")
        private List<Path> testCases;
        
        @Option(names = {"-b", "--browser"}, description = "Browser to use (chrome, firefox, edge, safari)", defaultValue = "chrome")
        private String browser;
        
        @Option(names = {"--headless"}, description = "Run browser in headless mode")
        private boolean headless;
        
        @Option(names = {"-r", "--report"}, description = "Report output folder", defaultValue = "reports")
        private Path reportPath;
        
        @Option(names = {"--screenshot-on-failure"}, description = "Take screenshot on test failure", defaultValue = "true")
        private boolean screenshotOnFailure;
        
        @Option(names = {"--screenshot-on-success"}, description = "Take screenshot on test success")
        private boolean screenshotOnSuccess;
        
        @Option(names = {"--retry"}, description = "Number of retries for failed tests", defaultValue = "0")
        private int retryCount;
        
        @Option(names = {"--fail-fast"}, description = "Stop execution on first failure")
        private boolean failFast;
        
        @Option(names = {"--timeout"}, description = "Implicit wait timeout in seconds", defaultValue = "30")
        private int timeout;
        
        @Option(names = {"--profile"}, description = "Execution profile name", defaultValue = "default")
        private String profile;
        
        @Option(names = {"--remote-url"}, description = "Remote WebDriver URL (for Selenium Grid)")
        private String remoteUrl;
        
        @Option(names = {"-v", "--verbose"}, description = "Enable verbose logging")
        private boolean verbose;
        
        @Override
        public Integer call() {
            printBanner();
            
            try {
                // Validate inputs
                if (testSuite == null && (testCases == null || testCases.isEmpty())) {
                    System.err.println("Error: Please specify either --test-suite or --test-case");
                    return 1;
                }
                
                // Build configuration
                RunConfiguration.Builder configBuilder = RunConfiguration.builder()
                        .browserType(parseBrowserType(browser))
                        .headless(headless)
                        .implicitWait(timeout)
                        .pageLoadTimeout(60)
                        .scriptTimeout(timeout)
                        .reportPath(reportPath)
                        .takeScreenshotOnFailure(screenshotOnFailure)
                        .takeScreenshotOnSuccess(screenshotOnSuccess)
                        .retryFailedTests(retryCount)
                        .failFast(failFast)
                        .executionProfile(profile);
                
                if (projectPath != null) {
                    configBuilder.projectPath(projectPath);
                }
                
                if (remoteUrl != null && !remoteUrl.isEmpty()) {
                    configBuilder.useRemoteWebDriver(true)
                            .remoteWebDriverUrl(remoteUrl);
                }
                
                RunConfiguration config = configBuilder.build();
                
                // Create engine
                KatalanEngine engine = new KatalanEngine(config);
                
                try {
                    // Initialize
                    System.out.println("🚀 Initializing katalan Engine...");
                    engine.initialize();
                    
                    ExecutionResult result;
                    
                    if (testSuite != null) {
                        // Run test suite
                        System.out.println("📋 Running test suite: " + testSuite);
                        Path suitePath = resolveSuitePath(projectPath, testSuite);
                        result = engine.executeTestSuite(suitePath);
                    } else {
                        // Run test cases
                        System.out.println("📋 Running " + testCases.size() + " test case(s)...");
                        TestSuiteParser parser = new TestSuiteParser(projectPath);
                        TestSuite suite = parser.createSuiteFromTestCases("CLI Test Suite", testCases);
                        result = engine.executeTestSuite(suite);
                    }
                    
                    // Generate report
                    System.out.println("\n📊 Generating HTML report...");
                    HtmlReportGenerator reportGenerator = new HtmlReportGenerator(reportPath);
                    reportGenerator.generateReport(result);
                    
                    // Print summary
                    printSummary(result);
                    
                    // Return exit code based on results
                    return result.getFailedTests() > 0 || result.getErrorTests() > 0 ? 1 : 0;
                    
                } finally {
                    engine.shutdown();
                }
                
            } catch (Exception e) {
                System.err.println("❌ Error: " + e.getMessage());
                if (verbose) {
                    e.printStackTrace();
                }
                return 1;
            }
        }
        
        /**
         * Resolve test suite path with multiple fallback strategies
         */
        private Path resolveSuitePath(Path projectPath, String testSuite) throws IOException {
            // Try different path combinations
            Path[] candidates = new Path[] {
                // 1. Direct path with .ts extension
                projectPath.resolve(testSuite + ".ts"),
                // 2. Direct path as-is (maybe already has extension)
                projectPath.resolve(testSuite),
                // 3. Under Test Suites folder with .ts
                projectPath.resolve("Test Suites").resolve(testSuite + ".ts"),
                // 4. Under Test Suites folder as-is
                projectPath.resolve("Test Suites").resolve(testSuite),
                // 5. If testSuite starts with "Test Suites/", don't duplicate
                testSuite.startsWith("Test Suites/") || testSuite.startsWith("Test Suites\\") 
                    ? projectPath.resolve(testSuite.substring("Test Suites/".length()) + ".ts")
                    : null,
            };
            
            for (Path candidate : candidates) {
                if (candidate != null && Files.exists(candidate)) {
                    return candidate;
                }
            }
            
            // If nothing found, return the most likely path for better error message
            Path defaultPath = projectPath.resolve(testSuite);
            if (!testSuite.endsWith(".ts")) {
                defaultPath = projectPath.resolve(testSuite + ".ts");
            }
            throw new IOException("Test suite not found: " + testSuite + 
                "\nSearched in: " + projectPath.resolve("Test Suites"));
        }
        
        private void printBanner() {
            System.out.println();
            System.out.println("╔═══════════════════════════════════════════════════════════╗");
            System.out.println("║                                                           ║");
            System.out.println("║   🧪 KATALAN RUNNER v1.0.0                                ║");
            System.out.println("║   Unofficial Katalon Test Runner                          ║");
            System.out.println("║                                                           ║");
            System.out.println("╚═══════════════════════════════════════════════════════════╝");
            System.out.println();
        }
        
        private void printSummary(ExecutionResult result) {
            System.out.println();
            System.out.println("╔═══════════════════════════════════════════════════════════╗");
            System.out.println("║                    EXECUTION SUMMARY                      ║");
            System.out.println("╠═══════════════════════════════════════════════════════════╣");
            System.out.printf("║  Total Tests:    %-40d║%n", result.getTotalTests());
            System.out.printf("║  ✅ Passed:      %-40d║%n", result.getPassedTests());
            System.out.printf("║  ❌ Failed:      %-40d║%n", result.getFailedTests());
            System.out.printf("║  💥 Errors:      %-40d║%n", result.getErrorTests());
            System.out.printf("║  ⏭️ Skipped:     %-40d║%n", result.getSkippedTests());
            System.out.printf("║  Pass Rate:      %-40.1f%%║%n", result.getPassRate());
            System.out.printf("║  Duration:       %-40s║%n", formatDuration(result.getDuration().toMillis()));
            System.out.println("╠═══════════════════════════════════════════════════════════╣");
            System.out.printf("║  Report:         %-40s║%n", reportPath.resolve("index.html"));
            System.out.println("╚═══════════════════════════════════════════════════════════╝");
            System.out.println();
            
            if (result.getFailedTests() > 0 || result.getErrorTests() > 0) {
                System.out.println("❌ Some tests failed!");
            } else {
                System.out.println("✅ All tests passed!");
            }
        }
        
        private String formatDuration(long millis) {
            long seconds = millis / 1000;
            long ms = millis % 1000;
            
            if (seconds < 60) {
                return String.format("%d.%03ds", seconds, ms);
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
        
        private RunConfiguration.BrowserType parseBrowserType(String browser) {
            switch (browser.toLowerCase()) {
                case "firefox":
                    return RunConfiguration.BrowserType.FIREFOX;
                case "edge":
                    return RunConfiguration.BrowserType.EDGE;
                case "safari":
                    return RunConfiguration.BrowserType.SAFARI;
                case "chrome":
                default:
                    return RunConfiguration.BrowserType.CHROME;
            }
        }
    }
    
    /**
     * Info subcommand - Show system info
     */
    @Command(
        name = "info",
        description = "Show system and environment information"
    )
    static class InfoCommand implements Callable<Integer> {
        
        @Override
        public Integer call() {
            System.out.println();
            System.out.println("╔═══════════════════════════════════════════════════════════╗");
            System.out.println("║                   KATALAN SYSTEM INFO                     ║");
            System.out.println("╚═══════════════════════════════════════════════════════════╝");
            System.out.println();
            System.out.println("katalan Runner:    1.0.0");
            System.out.println("Java Version:      " + System.getProperty("java.version"));
            System.out.println("Java Home:         " + System.getProperty("java.home"));
            System.out.println("OS Name:           " + System.getProperty("os.name"));
            System.out.println("OS Version:        " + System.getProperty("os.version"));
            System.out.println("OS Arch:           " + System.getProperty("os.arch"));
            System.out.println("User Home:         " + System.getProperty("user.home"));
            System.out.println("Working Dir:       " + System.getProperty("user.dir"));
            System.out.println();
            
            return 0;
        }
    }
}
