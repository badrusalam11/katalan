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
     * Convert from Katalon's FailureHandling
     */
    public static FailureHandling fromKatalon(com.kms.katalon.core.model.FailureHandling katalon) {
        if (katalon == null) {
            return STOP_ON_FAILURE;
        }
        switch (katalon) {
            case STOP_ON_FAILURE:
                return STOP_ON_FAILURE;
            case CONTINUE_ON_FAILURE:
                return CONTINUE_ON_FAILURE;
            case OPTIONAL:
                return OPTIONAL;
            default:
                return STOP_ON_FAILURE;
        }
    }
    
    /**
     * Convert to Katalon's FailureHandling
     */
    public com.kms.katalon.core.model.FailureHandling toKatalon() {
        switch (this) {
            case STOP_ON_FAILURE:
                return com.kms.katalon.core.model.FailureHandling.STOP_ON_FAILURE;
            case CONTINUE_ON_FAILURE:
                return com.kms.katalon.core.model.FailureHandling.CONTINUE_ON_FAILURE;
            case OPTIONAL:
                return com.kms.katalon.core.model.FailureHandling.OPTIONAL;
            default:
                return com.kms.katalon.core.model.FailureHandling.STOP_ON_FAILURE;
        }
    }
}
