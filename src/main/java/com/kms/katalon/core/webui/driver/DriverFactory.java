package com.kms.katalon.core.webui.driver;

import com.katalan.core.context.ExecutionContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Katalon compatibility class for DriverFactory
 * This provides access to the WebDriver instance.
 */
public class DriverFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(DriverFactory.class);
    
    /**
     * Get the current WebDriver instance.
     * 
     * IMPORTANT: This method throws an exception if WebDriver is not initialized.
     * CSWeb library relies on this exception to determine if it needs to call openBrowser(url).
     * If this returns null without exception, CSWeb skips URL navigation!
     */
    public static WebDriver getWebDriver() {
        ExecutionContext ctx = ExecutionContext.getCurrent();
        if (ctx != null) {
            WebDriver driver = ctx.getWebDriver();
            if (driver != null) {
                return driver;
            }
        }
        // Throw exception so CSWeb knows browser is not opened yet
        throw new IllegalStateException("WebDriver is not initialized");
    }
    
    /**
     * Get the current WebDriver instance without throwing exception.
     * Returns null if not initialized.
     */
    public static WebDriver getWebDriverOrNull() {
        ExecutionContext ctx = ExecutionContext.getCurrent();
        if (ctx != null) {
            return ctx.getWebDriver();
        }
        return null;
    }
    
    /**
     * Change the WebDriver instance
     */
    public static void changeWebDriver(WebDriver driver) {
        ExecutionContext ctx = ExecutionContext.getCurrent();
        if (ctx != null) {
            ctx.setWebDriver(driver);
        }
    }
    
    /**
     * Close the WebDriver
     */
    public static void closeWebDriver() {
        WebDriver driver = getWebDriverOrNull();
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception e) {
                logger.warn("Error closing WebDriver: {}", e.getMessage());
            }
        }
        ExecutionContext ctx = ExecutionContext.getCurrent();
        if (ctx != null) {
            ctx.setWebDriver(null);
        }
    }
    
    /**
     * Get the browser type
     */
    public static String getExecutedBrowser() {
        ExecutionContext ctx = ExecutionContext.getCurrent();
        if (ctx != null && ctx.getRunConfiguration() != null) {
            return ctx.getRunConfiguration().getBrowserType().name();
        }
        return "CHROME";
    }
    
    /**
     * Check if WebDriver is available
     */
    public static boolean isWebDriverAvailable() {
        WebDriver driver = getWebDriverOrNull();
        if (driver == null) {
            return false;
        }
        try {
            // Try to access session to check if driver is still valid
            if (driver instanceof RemoteWebDriver) {
                ((RemoteWebDriver) driver).getSessionId();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get session ID of current WebDriver
     */
    public static String getSessionId() {
        WebDriver driver = getWebDriverOrNull();
        if (driver instanceof RemoteWebDriver) {
            return ((RemoteWebDriver) driver).getSessionId().toString();
        }
        return null;
    }
    
    /**
     * Get alert if present
     */
    public static org.openqa.selenium.Alert getAlert() {
        WebDriver driver = getWebDriverOrNull();
        if (driver != null) {
            try {
                return driver.switchTo().alert();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
    
    /**
     * Get current window handle
     */
    public static String getCurrentWindowHandle() {
        WebDriver driver = getWebDriverOrNull();
        if (driver != null) {
            return driver.getWindowHandle();
        }
        return null;
    }
    
    /**
     * Get all window handles
     */
    public static java.util.Set<String> getWindowHandles() {
        WebDriver driver = getWebDriverOrNull();
        if (driver != null) {
            return driver.getWindowHandles();
        }
        return java.util.Collections.emptySet();
    }
}
