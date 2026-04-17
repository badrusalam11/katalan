package com.katalan.core.compat;

import com.katalan.core.context.ExecutionContext;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * DriverFactory - compatibility class for Katalon's DriverFactory
 * Provides access to the current WebDriver instance
 */
public class DriverFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(DriverFactory.class);
    
    /**
     * Get the current WebDriver instance
     */
    public static WebDriver getWebDriver() {
        ExecutionContext context = ExecutionContext.getCurrent();
        if (context == null) {
            logger.warn("No execution context available");
            return null;
        }
        return context.getWebDriver();
    }
    
    /**
     * Get the alert (compatibility method)
     */
    public static org.openqa.selenium.Alert getAlert() {
        WebDriver driver = getWebDriver();
        if (driver != null) {
            return driver.switchTo().alert();
        }
        return null;
    }
    
    /**
     * Execute JavaScript
     */
    public static Object executeScript(String script, Object... args) {
        WebDriver driver = getWebDriver();
        if (driver instanceof JavascriptExecutor) {
            return ((JavascriptExecutor) driver).executeScript(script, args);
        }
        return null;
    }
}
