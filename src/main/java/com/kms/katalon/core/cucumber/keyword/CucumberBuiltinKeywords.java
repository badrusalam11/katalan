package com.kms.katalon.core.cucumber.keyword;

import com.kms.katalon.core.model.FailureHandling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Katalon compatibility stub for CucumberBuiltinKeywords
 * In Katalan, BDD execution is handled via KatalanBDDExecutor.
 */
public class CucumberBuiltinKeywords {
    
    private static final Logger logger = LoggerFactory.getLogger(CucumberBuiltinKeywords.class);
    
    public static void runFeatureFile(String featurePath) {
        logger.warn("CucumberKW.runFeatureFile({}) - not implemented; run feature files as test suites directly", featurePath);
    }
    
    public static void runFeatureFile(String featurePath, FailureHandling fh) {
        runFeatureFile(featurePath);
    }
    
    public static void runFeatureFileWithTags(String featurePath, String tags) {
        logger.warn("CucumberKW.runFeatureFileWithTags({}, {}) - not implemented", featurePath, tags);
    }
    
    public static void runFeatureFileWithTags(String featurePath, String tags, FailureHandling fh) {
        runFeatureFileWithTags(featurePath, tags);
    }
    
    public static void runFeatureFolder(String featureFolder) {
        logger.warn("CucumberKW.runFeatureFolder is not fully implemented");
    }
    
    public static void runFeatureFolder(String featureFolder, FailureHandling fh) {
        runFeatureFolder(featureFolder);
    }
    
    public static void runFeatureFolderWithTags(String featureFolder, String tags) {
        logger.warn("CucumberKW.runFeatureFolderWithTags({}, {}) - not implemented", featureFolder, tags);
    }
    
    public static void runFeatureFolderWithTags(String featureFolder, String tags, FailureHandling fh) {
        runFeatureFolderWithTags(featureFolder, tags);
    }
}
