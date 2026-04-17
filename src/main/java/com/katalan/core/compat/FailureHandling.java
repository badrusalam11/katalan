package com.katalan.core.compat;

/**
 * Katalon-compatible FailureHandling enum
 * Defines how test execution should handle keyword failures
 */
public enum FailureHandling {
    
    /**
     * Stop execution on failure and mark test as failed
     */
    STOP_ON_FAILURE,
    
    /**
     * Continue execution on failure but still mark test as failed
     */
    CONTINUE_ON_FAILURE,
    
    /**
     * Continue execution on failure and mark test as passed (optional step)
     */
    OPTIONAL;
    
    /**
     * Get the default failure handling mode
     */
    public static FailureHandling getDefault() {
        return STOP_ON_FAILURE;
    }
    
    /**
     * Check if execution should stop on failure
     */
    public boolean shouldStopOnFailure() {
        return this == STOP_ON_FAILURE;
    }
    
    /**
     * Check if failure should be reported
     */
    public boolean shouldReportFailure() {
        return this != OPTIONAL;
    }
}
