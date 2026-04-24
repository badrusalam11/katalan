package com.katalan.core.compat;

import com.katalan.core.context.ExecutionContext;
import com.katalan.core.model.TestObject;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * WebUiCommonHelper - compatibility class for Katalon's WebUiCommonHelper
 * Provides utility methods for WebUI operations
 */
public class WebUiCommonHelper {
    
    private static final Logger logger = LoggerFactory.getLogger(WebUiCommonHelper.class);
    
    /**
     * Find web element from TestObject
     */
    public static WebElement findWebElement(TestObject testObject, int timeout) {
        WebDriver driver = DriverFactory.getWebDriver();
        if (driver == null) {
            throw new RuntimeException("WebDriver not initialized");
        }
        
        By locator = createLocator(testObject);
        
        // Immediate check - try to find element without wait first
        try {
            WebElement element = driver.findElement(locator);
            if (element != null) {
                return element;
            }
        } catch (NoSuchElementException ignored) {
            // Element not immediately available, proceed with wait
        }
        
        // Fast polling with 100ms interval for responsive wait
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeout), Duration.ofMillis(100));
        return wait.until(ExpectedConditions.presenceOfElementLocated(locator));
    }
    
    /**
     * Find web elements from TestObject
     */
    public static List<WebElement> findWebElements(TestObject testObject, int timeout) {
        WebDriver driver = DriverFactory.getWebDriver();
        if (driver == null) {
            throw new RuntimeException("WebDriver not initialized");
        }
        
        By locator = createLocator(testObject);
        
        // Immediate check - try to find elements without wait first
        try {
            List<WebElement> elements = driver.findElements(locator);
            if (elements != null && !elements.isEmpty()) {
                return elements;
            }
        } catch (Exception ignored) {
            // Elements not immediately available, proceed with wait
        }
        
        // Fast polling with 100ms interval for responsive wait
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeout), Duration.ofMillis(100));
        wait.until(ExpectedConditions.presenceOfElementLocated(locator));
        return driver.findElements(locator);
    }
    
    /**
     * Check if element is clickable
     */
    public static boolean checkElementClickable(TestObject testObject, int timeout) {
        try {
            WebDriver driver = DriverFactory.getWebDriver();
            if (driver == null) {
                return false;
            }
            
            By locator = createLocator(testObject);
            
            // Immediate check - try to check if element is clickable without wait first
            try {
                WebElement element = driver.findElement(locator);
                if (element != null && element.isDisplayed() && element.isEnabled()) {
                    return true; // Early return on success
                }
            } catch (NoSuchElementException | StaleElementReferenceException ignored) {
                // Element not immediately clickable, proceed with wait
            }
            
            // Fast polling with 100ms interval for responsive wait
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeout), Duration.ofMillis(100));
            wait.until(ExpectedConditions.elementToBeClickable(locator));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if element is visible
     */
    public static boolean checkElementVisible(TestObject testObject, int timeout) {
        try {
            WebDriver driver = DriverFactory.getWebDriver();
            if (driver == null) {
                return false;
            }
            
            By locator = createLocator(testObject);
            
            // Immediate check - try to check visibility without wait first
            try {
                WebElement element = driver.findElement(locator);
                if (element != null && element.isDisplayed()) {
                    return true; // Early return on success
                }
            } catch (NoSuchElementException | StaleElementReferenceException ignored) {
                // Element not immediately visible, proceed with wait
            }
            
            // Fast polling with 100ms interval for responsive wait
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeout), Duration.ofMillis(100));
            wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Create Selenium locator from TestObject
     */
    private static By createLocator(TestObject testObject) {
        String selectorValue = testObject.getSelectorValue();
        
        switch (testObject.getSelectorMethod()) {
            case XPATH:
                return By.xpath(selectorValue);
            case CSS:
                return By.cssSelector(selectorValue);
            case ID:
                return By.id(selectorValue);
            case NAME:
                return By.name(selectorValue);
            case CLASS_NAME:
                return By.className(selectorValue);
            case TAG_NAME:
                return By.tagName(selectorValue);
            case LINK_TEXT:
                return By.linkText(selectorValue);
            case PARTIAL_LINK_TEXT:
                return By.partialLinkText(selectorValue);
            default:
                return By.xpath(selectorValue);
        }
    }
}
