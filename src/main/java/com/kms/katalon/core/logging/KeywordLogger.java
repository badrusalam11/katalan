package com.kms.katalon.core.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Katalon-compatible {@code KeywordLogger} stub. Delegates to SLF4J so
 * listeners / keywords written against Katalon Studio continue to work.
 */
public class KeywordLogger {

    private final Logger logger;

    public KeywordLogger() {
        this.logger = LoggerFactory.getLogger("KeywordLogger");
    }

    public KeywordLogger(String name) {
        this.logger = LoggerFactory.getLogger(name == null ? "KeywordLogger" : name);
    }

    public KeywordLogger(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz == null ? KeywordLogger.class : clazz);
    }

    public static KeywordLogger getInstance() {
        return new KeywordLogger();
    }

    public static KeywordLogger getInstance(Class<?> clazz) {
        return new KeywordLogger(clazz);
    }

    public void logInfo(String message) { if (message != null) logger.info(message); }
    public void logInfo(String message, Throwable t) { logger.info(message, t); }

    public void logWarning(String message) { if (message != null) logger.warn(message); }
    public void logWarning(String message, Throwable t) { logger.warn(message, t); }

    public void logError(String message) { if (message != null) logger.error(message); }
    public void logError(String message, Throwable t) { logger.error(message, t); }

    public void logDebug(String message) { if (message != null) logger.debug(message); }
    public void logDebug(String message, Throwable t) { logger.debug(message, t); }

    public void logPassed(String message) { logger.info("[PASSED] {}", message); }
    public void logFailed(String message) { logger.error("[FAILED] {}", message); }
    public void logNotRun(String message)  { logger.info("[NOT RUN] {}", message); }
    public void logSkipped(String message)  { logger.info("[SKIPPED] {}", message); }

    /** Katalon-style log message with arbitrary attribute map — ignored in katalan. */
    public void logMessage(Object level, String message) {
        if (message != null) logger.info(message);
    }

    public void close() { /* no-op */ }
    public void startSuite(String name, java.util.Map<?, ?> attrs) { /* no-op */ }
    public void endSuite(String name, java.util.Map<?, ?> attrs) { /* no-op */ }
    public void startTest(String name, java.util.Map<?, ?> attrs) { /* no-op */ }
    public void endTest(String name, java.util.Map<?, ?> attrs) { /* no-op */ }
    public void startKeyword(String name, java.util.Map<?, ?> attrs, int nestedLevel) { /* no-op */ }
    public void endKeyword(String name, java.util.Map<?, ?> attrs, int nestedLevel) { /* no-op */ }
}
