package com.katalan.reporting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Read report generation settings from settings/external/com.katalon.plugin.report.properties
 * 
 * Example properties file:
 * <pre>
 * generatePDF=true
 * generateHTML=false
 * generateCSV=false
 * </pre>
 */
public class ReportSettings {
    
    private static final Logger logger = LoggerFactory.getLogger(ReportSettings.class);
    
    private boolean generatePDF = false;
    private boolean generateHTML = true;  // Default to true (backward compatibility)
    private boolean generateCSV = false;
    
    /**
     * Load report settings from project path.
     * Looks for: settings/external/com.katalon.plugin.report.properties
     */
    public static ReportSettings load(Path projectPath) {
        ReportSettings settings = new ReportSettings();
        Path settingsFile = projectPath.resolve("settings/external/com.katalon.plugin.report.properties");
        
        if (!Files.exists(settingsFile)) {
            logger.debug("Report settings file not found at {}, using defaults", settingsFile);
            return settings;
        }
        
        try (FileInputStream fis = new FileInputStream(settingsFile.toFile())) {
            Properties props = new Properties();
            props.load(fis);
            
            settings.generatePDF = Boolean.parseBoolean(props.getProperty("generatePDF", "false"));
            settings.generateHTML = Boolean.parseBoolean(props.getProperty("generateHTML", "true"));
            settings.generateCSV = Boolean.parseBoolean(props.getProperty("generateCSV", "false"));
            
            logger.info("📋 Report settings loaded: PDF={}, HTML={}, CSV={}", 
                       settings.generatePDF, settings.generateHTML, settings.generateCSV);
            
        } catch (IOException e) {
            logger.warn("Failed to load report settings from {}: {}", settingsFile, e.getMessage());
        }
        
        return settings;
    }
    
    /**
     * Create ReportSettings from a comma-separated format string.
     * Useful for CLI override: --report-format=pdf,html,csv
     * 
     * Recognized formats (case-insensitive): pdf, html, csv
     * Unknown formats are ignored with a warning.
     * 
     * @param formats Comma-separated list of formats (e.g., "pdf,html")
     * @return ReportSettings with only the specified formats enabled
     */
    public static ReportSettings fromString(String formats) {
        ReportSettings settings = new ReportSettings();
        // Start with everything disabled - only enable what's requested
        settings.generatePDF = false;
        settings.generateHTML = false;
        settings.generateCSV = false;
        
        if (formats == null || formats.trim().isEmpty()) {
            return settings;
        }
        
        for (String format : formats.split(",")) {
            String f = format.trim().toLowerCase();
            switch (f) {
                case "pdf":
                    settings.generatePDF = true;
                    break;
                case "html":
                    settings.generateHTML = true;
                    break;
                case "csv":
                    settings.generateCSV = true;
                    break;
                case "":
                    // skip empty entries
                    break;
                default:
                    logger.warn("Unknown report format: '{}' (supported: pdf, html, csv)", f);
            }
        }
        
        logger.info("📋 Report settings from CLI: PDF={}, HTML={}, CSV={}", 
                   settings.generatePDF, settings.generateHTML, settings.generateCSV);
        
        return settings;
    }
    
    public boolean isGeneratePDF() {
        return generatePDF;
    }
    
    public boolean isGenerateHTML() {
        return generateHTML;
    }
    
    public boolean isGenerateCSV() {
        return generateCSV;
    }
}
