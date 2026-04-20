package com.kms.katalon.core.model;

/**
 * Katalon compatibility enum for FailureHandling
 * This mirrors the Katalon Studio's FailureHandling enum.
 */
public enum FailureHandling {
    /**
     * Stop execution immediately when a failure occurs
     */
    STOP_ON_FAILURE,
    
    /**
     * Continue execution but mark the test as failed
     */
    CONTINUE_ON_FAILURE,
    
    /**
     * Continue execution and don't affect test status (optional step)
     */
    OPTIONAL;
    
    /**
     * Convert to Katalan's FailureHandling
     */
    public com.katalan.core.compat.FailureHandling toKatalan() {
        switch (this) {
            case STOP_ON_FAILURE:
                return com.katalan.core.compat.FailureHandling.STOP_ON_FAILURE;
            case CONTINUE_ON_FAILURE:
                return com.katalan.core.compat.FailureHandling.CONTINUE_ON_FAILURE;
            case OPTIONAL:
                return com.katalan.core.compat.FailureHandling.OPTIONAL;
            default:
                return com.katalan.core.compat.FailureHandling.STOP_ON_FAILURE;
        }
    }
    
    /**
     * Convert from Katalan's FailureHandling
     */
    public static FailureHandling fromKatalan(com.katalan.core.compat.FailureHandling katalan) {
        if (katalan == null) {
            return STOP_ON_FAILURE;
        }
        switch (katalan) {
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
}
