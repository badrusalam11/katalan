package com.katalan.keywords;

import com.katalan.core.context.ExecutionContext;
import com.katalan.core.exception.StepFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KeywordUtil - Compatible with Katalon's KeywordUtil
 * 
 * Usage in Groovy scripts:
 *   KeywordUtil.logInfo("Message")
 *   KeywordUtil.markFailed("Test failed because...")
 */
public class KeywordUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(KeywordUtil.class);
    
    /**
     * Log info message
     */
    public static void logInfo(String message) {
        logger.info(message);
    }
    
    /**
     * Log warning message
     */
    public static void logWarning(String message) {
        logger.warn(message);
    }
    
    /**
     * Log error message
     */
    public static void logError(String message) {
        logger.error(message);
    }
    
    /**
     * Mark current step as passed
     */
    public static void markPassed(String message) {
        logger.info("[PASSED] {}", message);
    }
    
    /**
     * Mark current step as failed
     */
    public static void markFailed(String message) {
        logger.error("[FAILED] {}", message);
        throw new StepFailedException(message);
    }
    
    /**
     * Mark current step as failed with exception
     */
    public static void markFailedAndStop(String message) {
        logger.error("[FAILED AND STOP] {}", message);
        ExecutionContext.getCurrent().requestStop();
        throw new StepFailedException(message);
    }
    
    /**
     * Mark current step as warning
     */
    public static void markWarning(String message) {
        logger.warn("[WARNING] {}", message);
    }
    
    /**
     * Mark current step as error
     */
    public static void markError(String message) {
        logger.error("[ERROR] {}", message);
        throw new StepFailedException(message);
    }
    
    /**
     * Mark current step as error and stop
     */
    public static void markErrorAndStop(String message) {
        logger.error("[ERROR AND STOP] {}", message);
        ExecutionContext.getCurrent().requestStop();
        throw new StepFailedException(message);
    }
    
    /**
     * Concatenate strings
     */
    public static String concatenate(Object... args) {
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            sb.append(arg != null ? arg.toString() : "null");
        }
        return sb.toString();
    }
}
