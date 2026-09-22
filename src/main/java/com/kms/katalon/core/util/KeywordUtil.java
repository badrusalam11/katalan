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
    
    /** As in Katalon: the step fails but the test case keeps running, then ends FAILED. */
    public static void markFailed(String message) {
        logger.error("[FAILED] " + message);
        com.katalan.core.context.ExecutionContext.getCurrent().recordContinuedFailure(message);
    }

    /** As in Katalon: fails and stops the current test case only - the suite continues. */
    public static void markFailedAndStop(String message) {
        logger.error("[FAILED & STOP] " + message);
        throw new com.kms.katalon.core.exception.StepFailedException(message);
    }
    
    public static void markWarning(String message) {
        logger.warn("[WARNING] " + message);
    }
    
    public static void markError(String message) {
        logger.error("[ERROR] " + message);
    }
}
