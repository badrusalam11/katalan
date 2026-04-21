package com.kms.katalon.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Katalon compatibility class for KeywordUtil
 */
public class KeywordUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(KeywordUtil.class);
    
    public static void logInfo(String message) {
        logger.info(message);
    }
    
    public static void markPassed(String message) {
        logger.info("[PASSED] " + message);
    }
    
    public static void markFailed(String message) {
        logger.error("[FAILED] " + message);
        throw new AssertionError(message);
    }
    
    public static void markFailedAndStop(String message) {
        logger.error("[FAILED & STOP] " + message);
        throw new RuntimeException(message);
    }
    
    public static void markWarning(String message) {
        logger.warn("[WARNING] " + message);
    }
    
    public static void markError(String message) {
        logger.error("[ERROR] " + message);
    }
}
