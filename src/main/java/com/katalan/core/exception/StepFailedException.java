package com.katalan.core.exception;

/**
 * Exception thrown when a test step fails
 */
public class StepFailedException extends RuntimeException {
    
    public StepFailedException(String message) {
        super(message);
    }
    
    public StepFailedException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public StepFailedException(Throwable cause) {
        super(cause);
    }
}
