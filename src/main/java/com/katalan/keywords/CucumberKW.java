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
        logger.info("Running feature file: {}", featureFile);
        
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
        
        // Add plugin for output
        args.add("--plugin");
        args.add("pretty");
        
        logger.info("Cucumber args: {}", args);
        
        try {
            // Run Cucumber with our custom runtime that includes Groovy support
            return runCucumberWithGroovyGlue(featurePath, stepsPath, projectPath);
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
    private static int runCucumberWithGroovyGlue(Path featurePath, Path stepsPath, Path projectPath) {
        logger.info("Running Cucumber with Groovy glue from: {}", stepsPath);
        
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
            return bddExecutor.executeFeature(featurePath);
            
        } catch (Exception e) {
            logger.error("Cucumber execution failed", e);
            throw new RuntimeException("Cucumber execution failed: " + e.getMessage(), e);
        }
    }
}
