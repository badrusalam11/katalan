package com.kms.katalon.core.webui.exception;

/**
 * Katalon compatibility exception for WebElementNotFoundException
 */
public class WebElementNotFoundException extends RuntimeException {
    
    public WebElementNotFoundException() {
        super();
    }
    
    public WebElementNotFoundException(String message) {
        super(message);
    }
    
    public WebElementNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public WebElementNotFoundException(Throwable cause) {
        super(cause);
    }
}
