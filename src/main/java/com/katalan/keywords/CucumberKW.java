package com.katalan.keywords;

import com.katalan.core.context.ExecutionContext;
import com.katalan.core.model.TestObject;
import com.katalan.core.compat.GlobalVariable;
import io.cucumber.core.cli.Main;
import io.cucumber.java.en.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CucumberKW - Cucumber BDD Keywords compatible with Katalon's CucumberBuiltinKeywords
 * 
 * Usage in Groovy scripts:
 *   CucumberKW.runFeatureFile('Include/features/operations/Plus.feature')
 *   CucumberKW.runFeatureFolder('Include/features')
 *   CucumberKW.runWithCucumberRunner(Runner.class)
 */
public class CucumberKW {
    
    private static final Logger logger = LoggerFactory.getLogger(CucumberKW.class);
    
    // Thread-local to store the project path
    private static final ThreadLocal<Path> projectPathHolder = new ThreadLocal<>();
    
    /**
     * Set the project path for Cucumber execution
     */
    public static void setProjectPath(Path projectPath) {
        projectPathHolder.set(projectPath);
    }
    
    /**
     * Get the project path
     */
    public static Path getProjectPath() {
        Path projectPath = projectPathHolder.get();
        if (projectPath == null) {
            ExecutionContext ctx = ExecutionContext.getCurrent();
            if (ctx != null) {
                projectPath = ctx.getProjectPath();
            }
        }
        return projectPath;
    }
    
    /**
     * Run a single feature file
     * @param featureFile Relative path to the feature file from project root
     * @return Result status (0 = success, non-zero = failure)
     */
    public static int runFeatureFile(String featureFile) {
        return runFeatureFileWithTags(featureFile, null);
    }
    
    /**
     * Run a single feature file with tag filter
     * @param featureFile Relative path to the feature file from project root
     * @param tags Cucumber tag expression (e.g., "@smoke", "@regression and not @slow")
     * @return Result status (0 = success, non-zero = failure)
     */
    public static int runFeatureFileWithTags(String featureFile, String tags) {
        logger.info("Running feature file: {} with tags: {}", featureFile, tags != null ? tags : "(none)");
        
        Path projectPath = getProjectPath();
        if (projectPath == null) {
            logger.error("Project path not set. Cannot run feature file.");
            throw new RuntimeException("Project path not set for Cucumber execution");
        }
        
        // Resolve the feature file path
        Path featurePath = projectPath.resolve(featureFile);
        if (!Files.exists(featurePath)) {
            logger.error("Feature file not found: {}", featurePath);
            throw new RuntimeException("Feature file not found: " + featurePath);
        }
        
        // Mark current test case as BDD in the execution context.
        // Store the path relative to project root (e.g. "Include/features/.../file.feature")
        // so reports can show portable paths. KatalanBDDExecutor will overwrite this with
        // its own relativization later, but we set it here as an early signal.
        ExecutionContext ctx = ExecutionContext.getCurrent();
        if (ctx != null) {
            ctx.setProperty("isBddTest", true);
            String storedPath = featureFile; // already relative as passed by user
            try {
                Path absFeature = featurePath.toAbsolutePath();
                Path absProject = projectPath.toAbsolutePath();
                storedPath = absProject.relativize(absFeature).toString().replace('\\', '/');
            } catch (Exception ignored) { /* keep storedPath as-is */ }
            ctx.setProperty("featureFile", storedPath);
            if (tags != null) {
                ctx.setProperty("cucumberTags", tags);
            }
        }
        
        // Find step definitions in Include/scripts/groovy
        Path stepsPath = projectPath.resolve("Include").resolve("scripts").resolve("groovy");
        
        // Build Cucumber arguments
        List<String> args = new ArrayList<>();
        args.add(featurePath.toString());
        
        // Add glue path if exists
        if (Files.exists(stepsPath)) {
            args.add("--glue");
            args.add(stepsPath.toString());
        }
        
        // Add tag filter if specified
        if (tags != null && !tags.isEmpty()) {
            args.add("--tags");
            args.add(tags);
        }
        
        // Add plugin for output
        args.add("--plugin");
        args.add("pretty");
        
        logger.info("Cucumber args: {}", args);
        
        try {
            // Generate and store cucumber report timestamp for this feature execution
            // IMPORTANT: This timestamp will be used both in the log message AND when generating the actual report
            com.katalan.core.context.ExecutionContext context = com.katalan.core.context.ExecutionContext.getCurrent();
            String cucumberTimestamp = context.getCucumberReportTimestamp();
            
            // Emit Katalon-style INFO record so execution0.log matches Katalon
            // Studio's "Starting run keyword runFeatureFile: '...' and extract
            // report to folder: '...'..." entry.
            try {
                String reportFolderProp = System.getProperty("reportFolder");
                if (reportFolderProp == null || reportFolderProp.isEmpty()) {
                    reportFolderProp = com.kms.katalon.core.configuration.RunConfiguration.getReportFolder();
                }
                String cucumberReportFolder = reportFolderProp != null && !reportFolderProp.isEmpty()
                        ? reportFolderProp.replace("\\", "/") + "/cucumber_report/" + cucumberTimestamp
                        : "cucumber_report/" + cucumberTimestamp;
                String infoMsg = "Starting run keyword runFeatureFile: '" + featureFile
                        + "' and extract report to folder: '" + cucumberReportFolder + "'...";
                com.katalan.core.logging.XmlKeywordLogger.getInstance()
                        .logMessage("INFO", infoMsg, java.util.Collections.emptyMap());
            } catch (Throwable ignored) {
                // Non-fatal: logging only
            }

            // Run Cucumber with our custom runtime that includes Groovy support
            int failed = runCucumberWithGroovyGlue(featurePath, stepsPath, projectPath, tags);
            
            // NOTE: DO NOT clear timestamp here - it will be cleared by report generator after reports are written
            // context.clearCucumberReportTimestamp(); // REMOVED - cleared in finally block of report generation
            
            if (failed > 0) {
                throw new RuntimeException("Cucumber execution finished with " + failed + " failed scenario(s)");
            }
            return failed;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to run feature file: {}", featureFile, e);
            throw new RuntimeException("Cucumber execution failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Run a folder containing feature files
     * @param featureFolder Relative path to the feature folder from project root
     * @return Result status (0 = success, non-zero = failure)
     */
    public static int runFeatureFolder(String featureFolder) {
        logger.info("Running feature folder: {}", featureFolder);
        
        Path projectPath = getProjectPath();
        if (projectPath == null) {
            logger.error("Project path not set. Cannot run feature folder.");
            throw new RuntimeException("Project path not set for Cucumber execution");
        }
        
        Path folderPath = projectPath.resolve(featureFolder);
        if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
            logger.error("Feature folder not found: {}", folderPath);
            throw new RuntimeException("Feature folder not found: " + folderPath);
        }
        
        try {
            // Find all .feature files in the folder
            List<Path> featureFiles = Files.walk(folderPath)
                .filter(p -> p.toString().endsWith(".feature"))
                .collect(java.util.stream.Collectors.toList());
            
            if (featureFiles.isEmpty()) {
                logger.warn("No feature files found in: {}", folderPath);
                return 0;
            }
            
            int result = 0;
            for (Path featureFile : featureFiles) {
                String relativePath = projectPath.relativize(featureFile).toString();
                int fileResult = runFeatureFile(relativePath);
                if (fileResult != 0) {
                    result = fileResult;
                }
            }
            
            return result;
        } catch (IOException e) {
            logger.error("Failed to scan feature folder: {}", featureFolder, e);
            throw new RuntimeException("Failed to scan feature folder: " + e.getMessage(), e);
        }
    }
    
    /**
     * Run Cucumber with a specific runner class
     * @param runner The Cucumber runner class
     * @return Result status
     */
    public static int runWithCucumberRunner(Class<?> runner) {
        logger.info("Running with Cucumber runner: {}", runner.getName());
        // For now, just log - implementing JUnit runner integration would require more work
        logger.warn("runWithCucumberRunner not fully implemented - use runFeatureFile instead");
        return 0;
    }
    
    /**
     * Internal method to run Cucumber with Groovy step definitions
     */
    private static int runCucumberWithGroovyGlue(Path featurePath, Path stepsPath, Path projectPath, String tags) {
        logger.info("Running Cucumber with Groovy glue from: {} with tags: {}", stepsPath, tags != null ? tags : "(none)");
        
        try {
            // Get the execution context for access to the script executor
            ExecutionContext ctx = ExecutionContext.getCurrent();
            if (ctx == null) {
                throw new RuntimeException("No execution context available");
            }
            
            // Read the feature file
            String featureContent = Files.readString(featurePath);
            logger.debug("Feature content:\n{}", featureContent);
            
            // Parse feature file and execute steps using our own BDD executor
            KatalanBDDExecutor bddExecutor = new KatalanBDDExecutor(ctx, projectPath, stepsPath);
            
            // Set tags filter if specified
            if (tags != null && !tags.isEmpty()) {
                bddExecutor.setTagFilter(tags);
            }
            
            return bddExecutor.executeFeature(featurePath);
            
        } catch (Exception e) {
            logger.error("Cucumber execution failed", e);
            throw new RuntimeException("Cucumber execution failed: " + e.getMessage(), e);
        }
    }
}
