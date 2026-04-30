package com.katalan.keywords;

import com.katalan.core.context.ExecutionContext;
import com.katalan.core.model.TestObject;
import com.katalan.core.exception.StepFailedException;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WebUI Keywords - Compatible with Katalon's WebUI built-in keywords
 * 
 * Usage in Groovy scripts:
 *   WebUI.openBrowser(url)
 *   WebUI.click(findTestObject('Page/button'))
 *   WebUI.setText(findTestObject('Page/input'), 'text')
 */
public class WebUI {
    
    private static final Logger logger = LoggerFactory.getLogger(WebUI.class);
    
    // ==================== Browser Keywords ====================
    
    /**
     * Open browser and navigate to URL
     */
    public static void openBrowser(String url) {
        logger.info("🌐 openBrowser() called with URL: {}", url);
        ExecutionContext context = ExecutionContext.getCurrent();
        
        // CRITICAL: ALWAYS create NEW browser instance on every openBrowser() call!
        // DO NOT reuse existing driver. Each openBrowser() = new Chrome session.
        // All browsers stay alive until JVM shutdown, then DriverCleanupManager kills ALL.
        logger.info("✅ Creating NEW browser instance (previous driver will stay alive)");
        
        WebDriver driver = com.katalan.core.driver.WebDriverFactory.createDriver(context.getRunConfiguration());
        context.setWebDriver(driver);
        logger.info("✅ NEW browser created and set as current driver");
        
        // Only navigate if URL is provided (non-empty)
        if (url != null && !url.trim().isEmpty()) {
            logger.info("📍 Navigating to: {}", url);
            driver.get(url);
        } else {
            logger.info("📍 No URL provided - browser opened without navigation");
        }
    }
    
    /**
     * Check if WebDriver session is closed.
     * Avoids calling driver.getTitle() which performs a network round-trip to
     * the browser - a cheap session-id / window-handle check is enough to
     * detect a dead session without doing any real work on a live one.
     */
    private static boolean isDriverClosed(WebDriver driver) {
        try {
            if (driver instanceof org.openqa.selenium.remote.RemoteWebDriver) {
                org.openqa.selenium.remote.RemoteWebDriver rwd =
                        (org.openqa.selenium.remote.RemoteWebDriver) driver;
                if (rwd.getSessionId() == null) return true;
            }
            // Cheap check: getWindowHandles returns instantly on live sessions,
            // throws NoSuchSessionException / WebDriverException on dead ones.
            driver.getWindowHandles();
            return false;
        } catch (Exception e) {
            return true;
        }
    }
    
    /**
     * Navigate to URL
     */
    public static void navigateToUrl(String url) {
        logger.info("Navigating to URL: {}", url);
        WebDriver driver = getDriver();
        driver.get(url);
        
        // Wait for page to be fully loaded (especially critical in headless mode)
        try {
            // Wait for document.readyState === 'complete'
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30), Duration.ofMillis(100));
            wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                .executeScript("return document.readyState").equals("complete"));
            
            // Additional check: wait for body to be present
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
            
            logger.debug("Page fully loaded: {}", url);
        } catch (Exception e) {
            logger.warn("Timeout waiting for page load, continuing anyway: {}", e.getMessage());
        }
    }

    // ==================== Smart Wait (no-op stubs) ====================
    public static void enableSmartWait() {
        logger.debug("enableSmartWait() called - no-op");
    }
    public static void enableSmartWait(Object flowControl) {
        logger.debug("enableSmartWait(flowControl) called - no-op");
    }
    public static void disableSmartWait() {
        logger.debug("disableSmartWait() called - no-op");
    }
    public static void disableSmartWait(Object flowControl) {
        logger.debug("disableSmartWait(flowControl) called - no-op");
    }
    
    /**
     * Close browser
     */
    public static void closeBrowser() {
        logger.info("Closing browser");
        WebDriver driver = getDriver();
        if (driver != null) {
            driver.quit();
        }
    }
    
    /**
     * Close current window
     */
    public static void closeWindowIndex(int index) {
        logger.info("Closing window at index: {}", index);
        WebDriver driver = getDriver();
        String[] handles = driver.getWindowHandles().toArray(new String[0]);
        if (index < handles.length) {
            driver.switchTo().window(handles[index]).close();
        }
    }
    
    /**
     * Maximize window
     */
    public static void maximizeWindow() {
        logger.info("Maximizing window");
        getDriver().manage().window().maximize();
    }
    
    /**
     * Set window size
     */
    public static void setWindowSize(int width, int height) {
        logger.info("Setting window size: {}x{}", width, height);
        getDriver().manage().window().setSize(new Dimension(width, height));
    }
    
    /**
     * Refresh the page
     */
    public static void refresh() {
        logger.info("Refreshing page");
        getDriver().navigate().refresh();
    }
    
    /**
     * Navigate back
     */
    public static void back() {
        logger.info("Navigating back");
        getDriver().navigate().back();
    }
    
    /**
     * Navigate forward
     */
    public static void forward() {
        logger.info("Navigating forward");
        getDriver().navigate().forward();
    }
    
    /**
     * Get current URL
     */
    public static String getUrl() {
        return getDriver().getCurrentUrl();
    }
    
    /**
     * Get page title
     */
    public static String getWindowTitle() {
        return getDriver().getTitle();
    }
    
    // ==================== Click Keywords ====================
    
    /**
     * Click on an element
     */
    public static void click(TestObject testObject) {
        click(testObject, 30);
    }
    
    /**
     * Click on an element with timeout
     */
    public static void click(TestObject testObject, int timeout) {
        logger.info("Clicking on: {}", describe(testObject));
        WebDriver driver = getDriver();
        By by = testObject.toSeleniumBy();
        
        // Use WebDriverWait with fast polling (100ms) - trusts Selenium's optimization
        // No immediate check needed - WebDriverWait handles early return internally
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeout), Duration.ofMillis(100));
        
        // Wait for element to be clickable
        WebElement element = wait.until(ExpectedConditions.elementToBeClickable(by));
        
        // Scroll into view to avoid "not interactable" when the element is off-screen
        try {
            ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center', inline:'center'});", element);
        } catch (Exception ignored) {}
        
        // Attempt click with fallback to JS click
        try {
            element.click();
        } catch (ElementNotInteractableException e) {
            logger.warn("Native click failed ({}), retrying with JS click", e.getClass().getSimpleName());
            try {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
            } catch (Exception jsEx) {
                throw e; // rethrow original
            }
        }
    }
    
    /**
     * Double click on an element
     */
    public static void doubleClick(TestObject testObject) {
        doubleClick(testObject, 30);
    }
    
    /**
     * Double click on an element with timeout
     */
    public static void doubleClick(TestObject testObject, int timeout) {
    logger.info("Double clicking on: {}", describe(testObject));
        WebElement element = waitForElement(testObject, timeout);
        new Actions(getDriver()).doubleClick(element).perform();
    }
    
    /**
     * Right click on an element
     */
    public static void rightClick(TestObject testObject) {
        rightClick(testObject, 30);
    }
    
    /**
     * Right click on an element with timeout
     */
    public static void rightClick(TestObject testObject, int timeout) {
    logger.info("Right clicking on: {}", describe(testObject));
        WebElement element = waitForElement(testObject, timeout);
        new Actions(getDriver()).contextClick(element).perform();
    }
    
    /**
     * Click on element using JavaScript
     */
    public static void clickUsingJavaScript(TestObject testObject) {
        clickUsingJavaScript(testObject, 30);
    }
    
    /**
     * Click on element using JavaScript with timeout
     */
    public static void clickUsingJavaScript(TestObject testObject, int timeout) {
    logger.info("JS Click on: {}", describe(testObject));
        WebElement element = waitForElement(testObject, timeout);
        executeJavaScript("arguments[0].click();", element);
    }
    
    // ==================== Text Input Keywords ====================
    
    /**
     * Set text to an input field
     */
    public static void setText(TestObject testObject, String text) {
        setText(testObject, text, 30);
    }
    
    /**
     * Set text to an input field with timeout.
     *
     * Approach mirrors Katalon's setText behaviour but is hardened for React/Angular
     * controlled inputs: after the native sendKeys we dispatch `input` and `change`
     * events via JS so frameworks that listen only for synthetic events (e.g. React's
     * onChange) see the updated value and enable submit buttons.
     */
    public static void setText(TestObject testObject, String text, int timeout) {
    logger.info("Setting text '{}' to: {}", text, describe(testObject));
        WebElement element = waitForElement(testObject, timeout);
        try {
            element.clear();
        } catch (Exception ignore) { /* some inputs throw on clear - non-fatal */ }
        element.sendKeys(text);
        // Dispatch input+change so React/Angular-controlled forms register the value
        try {
            ((JavascriptExecutor) getDriver()).executeScript(
                "var el = arguments[0];" +
                "try { el.dispatchEvent(new Event('input', {bubbles:true})); } catch(e){}" +
                "try { el.dispatchEvent(new Event('change', {bubbles:true})); } catch(e){}" +
                "try { el.dispatchEvent(new Event('blur', {bubbles:true})); } catch(e){}",
                element);
        } catch (Exception ignore) { /* best-effort */ }
    }
    
    /**
     * Clear text from an input field
     */
    public static void clearText(TestObject testObject) {
        clearText(testObject, 30);
    }
    
    /**
     * Clear text from an input field with timeout
     */
    public static void clearText(TestObject testObject, int timeout) {
    logger.info("Clearing text from: {}", describe(testObject));
        WebElement element = waitForElement(testObject, timeout);
        element.clear();
    }
    
    /**
     * Send keys to an element
     */
    public static void sendKeys(TestObject testObject, Keys... keys) {
        sendKeys(testObject, 30, keys);
    }
    
    /**
     * Send keys (text string) to an element - Katalon compatibility
     */
    public static void sendKeys(TestObject testObject, String text) {
        sendKeys(testObject, 30, text);
    }
    
    /**
     * Send keys (text string) to an element with timeout - Katalon compatibility
     */
    public static void sendKeys(TestObject testObject, int timeout, String text) {
    logger.info("Sending text to: {}", describe(testObject));
        WebElement element = waitForElement(testObject, timeout);
        element.sendKeys(text);
    }
    
    /**
     * Send keys to an element with timeout
     */
    public static void sendKeys(TestObject testObject, int timeout, Keys... keys) {
    logger.info("Sending keys to: {}", describe(testObject));
        WebElement element = waitForElement(testObject, timeout);
        for (Keys key : keys) {
            element.sendKeys(key);
        }
    }
    
    /**
     * Upload file
     */
    public static void uploadFile(TestObject testObject, String filePath) {
        uploadFile(testObject, filePath, 30);
    }
    
    /**
     * Upload file with timeout
     */
    public static void uploadFile(TestObject testObject, String filePath, int timeout) {
    logger.info("Uploading file '{}' to: {}", filePath, describe(testObject));
        WebElement element = waitForElement(testObject, timeout);
        element.sendKeys(filePath);
    }
    
    // ==================== Get Element Data Keywords ====================
    
    /**
     * Get text from an element
     */
    public static String getText(TestObject testObject) {
        return getText(testObject, 30);
    }
    
    /**
     * Get text from an element with timeout
     */
    public static String getText(TestObject testObject, int timeout) {
        WebElement element = waitForElement(testObject, timeout);
        return element.getText();
    }
    
    /**
     * Get attribute value from an element
     */
    public static String getAttribute(TestObject testObject, String attributeName) {
        return getAttribute(testObject, attributeName, 30);
    }
    
    /**
     * Get attribute value from an element with timeout
     */
    public static String getAttribute(TestObject testObject, String attributeName, int timeout) {
        WebElement element = waitForElement(testObject, timeout);
        return element.getAttribute(attributeName);
    }
    
    /**
     * Get CSS value from an element
     */
    public static String getCssValue(TestObject testObject, String cssProperty) {
        return getCssValue(testObject, cssProperty, 30);
    }
    
    /**
     * Get CSS value from an element with timeout
     */
    public static String getCssValue(TestObject testObject, String cssProperty, int timeout) {
        WebElement element = waitForElement(testObject, timeout);
        return element.getCssValue(cssProperty);
    }
    
    // ==================== Select/Dropdown Keywords ====================
    
    /**
     * Select option by visible text
     */
    public static void selectOptionByLabel(TestObject testObject, String label) {
        selectOptionByLabel(testObject, label, false, 30);
    }
    
    /**
     * Select option by visible text (3-parameter version for Katalon compatibility)
     */
    public static void selectOptionByLabel(TestObject testObject, String label, Boolean isRegex) {
        selectOptionByLabel(testObject, label, isRegex != null && isRegex, 30);
    }
    
    /**
     * Select option by visible text with regex support
     */
    public static void selectOptionByLabel(TestObject testObject, String label, boolean isRegex, int timeout) {
    logger.info("Selecting option '{}' from: {}", label, describe(testObject));
        WebElement element = waitForElement(testObject, timeout);
        Select select = new Select(element);
        if (isRegex) {
            for (WebElement option : select.getOptions()) {
                if (option.getText().matches(label)) {
                    select.selectByVisibleText(option.getText());
                    return;
                }
            }
        } else {
            select.selectByVisibleText(label);
        }
    }
    
    /**
     * Select option by value
     */
    public static void selectOptionByValue(TestObject testObject, String value) {
        selectOptionByValue(testObject, value, false, 30);
    }
    
    /**
     * Select option by value with regex support
     */
    public static void selectOptionByValue(TestObject testObject, String value, boolean isRegex, int timeout) {
    logger.info("Selecting option by value '{}' from: {}", value, describe(testObject));
        WebElement element = waitForElement(testObject, timeout);
        Select select = new Select(element);
        if (isRegex) {
            for (WebElement option : select.getOptions()) {
                if (option.getAttribute("value").matches(value)) {
                    select.selectByValue(option.getAttribute("value"));
                    return;
                }
            }
        } else {
            select.selectByValue(value);
        }
    }
    
    /**
     * Select option by index
     */
    public static void selectOptionByIndex(TestObject testObject, int index) {
        selectOptionByIndex(testObject, index, 30);
    }
    
    /**
     * Select option by index with timeout
     */
    public static void selectOptionByIndex(TestObject testObject, int index, int timeout) {
    logger.info("Selecting option by index {} from: {}", index, describe(testObject));
        WebElement element = waitForElement(testObject, timeout);
        Select select = new Select(element);
        select.selectByIndex(index);
    }
    
    /**
     * Deselect all options
     */
    public static void deselectAllOption(TestObject testObject) {
        deselectAllOption(testObject, 30);
    }
    
    /**
     * Deselect all options with timeout
     */
    public static void deselectAllOption(TestObject testObject, int timeout) {
    logger.info("Deselecting all options from: {}", describe(testObject));
        WebElement element = waitForElement(testObject, timeout);
        Select select = new Select(element);
        select.deselectAll();
    }
    
    /**
     * Get number of options in a select
     */
    public static int getNumberOfSelectedOption(TestObject testObject) {
        return getNumberOfSelectedOption(testObject, 30);
    }
    
    /**
     * Get number of options in a select with timeout
     */
    public static int getNumberOfSelectedOption(TestObject testObject, int timeout) {
        WebElement element = waitForElement(testObject, timeout);
        Select select = new Select(element);
        return select.getAllSelectedOptions().size();
    }
    
    /**
     * Get number of total options in a select
     */
    public static int getNumberOfTotalOption(TestObject testObject) {
        return getNumberOfTotalOption(testObject, 30);
    }
    
    /**
     * Get number of total options in a select with timeout
     */
    public static int getNumberOfTotalOption(TestObject testObject, int timeout) {
        WebElement element = waitForElement(testObject, timeout);
        Select select = new Select(element);
        return select.getOptions().size();
    }
    
    // ==================== Checkbox/Radio Keywords ====================
    
    /**
     * Check a checkbox
     */
    public static void check(TestObject testObject) {
        check(testObject, 30);
    }
    
    /**
     * Check a checkbox with timeout
     */
    public static void check(TestObject testObject, int timeout) {
    logger.info("Checking checkbox: {}", describe(testObject));
        WebElement element = waitForElement(testObject, timeout);
        if (!element.isSelected()) {
            element.click();
        }
    }
    
    /**
     * Uncheck a checkbox
     */
    public static void uncheck(TestObject testObject) {
        uncheck(testObject, 30);
    }
    
    /**
     * Uncheck a checkbox with timeout
     */
    public static void uncheck(TestObject testObject, int timeout) {
    logger.info("Unchecking checkbox: {}", describe(testObject));
        WebElement element = waitForElement(testObject, timeout);
        if (element.isSelected()) {
            element.click();
        }
    }
    
    // ==================== Wait Keywords ====================
    
    /**
     * Wait for element to be present
     */
    public static void waitForElementPresent(TestObject testObject, int timeout) {
        waitForElementPresent(testObject, timeout, null);
    }
    
    /**
     * Wait for element to be present with failure handling
     * Uses 50ms polling for fast detection (consistent with other waits)
     */
    public static void waitForElementPresent(TestObject testObject, int timeout, Object failureHandling) {
        logger.info("Waiting for element present: {}", describe(testObject));
        try {
            WebDriver driver = getDriver();
            By locator = testObject.toSeleniumBy();
            
            // Use 50ms polling for fast detection (consistent with waitForElement)
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeout), Duration.ofMillis(50));
            wait.until(ExpectedConditions.presenceOfElementLocated(locator));
        } catch (Exception e) {
            com.katalan.core.compat.FailureHandling mode = toFailureHandling(failureHandling);
            if (mode == com.katalan.core.compat.FailureHandling.STOP_ON_FAILURE) {
                throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
            }
            // CONTINUE_ON_FAILURE or OPTIONAL - log and return
            if (mode == com.katalan.core.compat.FailureHandling.CONTINUE_ON_FAILURE) {
                logger.warn("waitForElementPresent failed: {}", e.getMessage());
            } else {
                logger.debug("waitForElementPresent failed (optional): {}", e.getMessage());
            }
        }
    }
    
    /**
     * Wait for element to be visible
     */
    public static void waitForElementVisible(TestObject testObject, int timeout) {
        waitForElementVisible(testObject, timeout, null);
    }
    
    /**
     * Wait for element to be visible with failure handling
     * Uses 50ms polling for fast detection (consistent with other waits)
     */
    public static void waitForElementVisible(TestObject testObject, int timeout, Object failureHandling) {
        logger.info("Waiting for element visible: {}", describe(testObject));
        try {
            WebDriver driver = getDriver();
            By locator = testObject.toSeleniumBy();
            
            // Use 50ms polling for fast detection (consistent with waitForElement)
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeout), Duration.ofMillis(50));
            wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
        } catch (Exception e) {
            com.katalan.core.compat.FailureHandling mode = toFailureHandling(failureHandling);
            if (mode == com.katalan.core.compat.FailureHandling.STOP_ON_FAILURE) {
                throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
            }
            // CONTINUE_ON_FAILURE or OPTIONAL - log and return
            if (mode == com.katalan.core.compat.FailureHandling.CONTINUE_ON_FAILURE) {
                logger.warn("waitForElementVisible failed: {}", e.getMessage());
            } else {
                logger.debug("waitForElementVisible failed (optional): {}", e.getMessage());
            }
        }
    }
    
    /**
     * Wait for element to be clickable
     */
    public static void waitForElementClickable(TestObject testObject, int timeout) {
        waitForElementClickable(testObject, timeout, null);
    }
    
    /**
     * Wait for element to be clickable with failure handling
     * Uses 50ms polling for fast detection (consistent with other waits)
     */
    public static void waitForElementClickable(TestObject testObject, int timeout, Object failureHandling) {
        logger.info("Waiting for element clickable: {}", describe(testObject));
        try {
            WebDriver driver = getDriver();
            By locator = testObject.toSeleniumBy();
            
            // Use 50ms polling for fast detection (consistent with waitForElement)
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeout), Duration.ofMillis(50));
            wait.until(ExpectedConditions.elementToBeClickable(locator));
        } catch (Exception e) {
            com.katalan.core.compat.FailureHandling mode = toFailureHandling(failureHandling);
            if (mode == com.katalan.core.compat.FailureHandling.STOP_ON_FAILURE) {
                throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
            }
            // CONTINUE_ON_FAILURE or OPTIONAL - log and return
            if (mode == com.katalan.core.compat.FailureHandling.CONTINUE_ON_FAILURE) {
                logger.warn("waitForElementClickable failed: {}", e.getMessage());
            } else {
                logger.debug("waitForElementClickable failed (optional): {}", e.getMessage());
            }
        }
    }
    
    /**
     * Wait for element to not be present
     * Optimized to detect quickly if element never appears
     */
    public static void waitForElementNotPresent(TestObject testObject, int timeout) {
        waitForElementNotPresent(testObject, timeout, null);
    }
    
    /**
     * Wait for element to not be present with failure handling
     */
    public static void waitForElementNotPresent(TestObject testObject, int timeout, Object failureHandling) {
        logger.info("Waiting for element not present: {}", testObject.getName());
        try {
            WebDriver driver = getDriver();
            By locator = testObject.toSeleniumBy();
            
            // OPTIMIZATION: Quick check if element already not present
            try {
                List<WebElement> elements = driver.findElements(locator);
                if (elements.isEmpty()) {
                    logger.debug("Element already not present, returning immediately");
                    return;
                }
            } catch (Exception e) {
                // Element not found - that's what we want
                logger.debug("Element not found (exception), returning immediately");
                return;
            }
            
            // Element is present, need to wait for it to disappear
            // Use shorter polling interval for faster detection
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeout), Duration.ofMillis(50));
            try {
                wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
            } catch (TimeoutException e) {
                // Log warning like Katalon does when element still present after timeout
                logger.warn("Element still present after {}s timeout: {} located by {}", 
                           timeout, testObject.getName(), locator);
                throw e;
            }
        } catch (Exception e) {
            com.katalan.core.compat.FailureHandling mode = toFailureHandling(failureHandling);
            if (mode == com.katalan.core.compat.FailureHandling.STOP_ON_FAILURE) {
                throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
            }
            // CONTINUE_ON_FAILURE or OPTIONAL - log and return
            if (mode == com.katalan.core.compat.FailureHandling.CONTINUE_ON_FAILURE) {
                logger.warn("waitForElementNotPresent failed: {}", e.getMessage());
            } else {
                logger.debug("waitForElementNotPresent failed (optional): {}", e.getMessage());
            }
        }
    }
    
    /**
     * Wait for page load
     */
    public static void waitForPageLoad(int timeout) {
        logger.info("Waiting for page load");
        WebDriverWait wait = new WebDriverWait(getDriver(), Duration.ofSeconds(timeout));
        wait.until(driver -> {
            String state = (String) ((JavascriptExecutor) driver).executeScript("return document.readyState");
            return "complete".equals(state);
        });
    }
    
    /**
     * Delay execution
     */
    public static void delay(int seconds) {
        try {
            logger.info("Delaying for {} seconds", seconds);
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    // ==================== Verification Keywords ====================
    
    /**
     * Verify element is present (with Object timeout for Katalon compatibility)
     */
    public static boolean verifyElementPresent(TestObject testObject, Object timeout) {
        return verifyElementPresent(testObject, toIntTimeout(timeout));
    }
    
    /**
     * Verify element is present
     */
    public static boolean verifyElementPresent(TestObject testObject, int timeout) {
        return verifyElementPresent(testObject, timeout, null);
    }
    
    /**
     * Verify element is present with failure handling
     */
    public static boolean verifyElementPresent(TestObject testObject, int timeout, Object failureHandling) {
        try {
            waitForElementPresent(testObject, timeout);
            return true;
        } catch (Exception e) {
            com.katalan.core.compat.FailureHandling mode = toFailureHandling(failureHandling);
            handleVerifyFailure("verifyElementPresent", e, mode);
            return false;
        }
    }
    
    /**
     * Verify element is visible (with Object timeout for Katalon compatibility)
     */
    public static boolean verifyElementVisible(TestObject testObject, Object timeout) {
        return verifyElementVisible(testObject, toIntTimeout(timeout));
    }
    
    /**
     * Verify element is visible
     */
    public static boolean verifyElementVisible(TestObject testObject, int timeout) {
        return verifyElementVisible(testObject, timeout, null);
    }
    
    /**
     * Verify element is visible with failure handling
     */
    public static boolean verifyElementVisible(TestObject testObject, int timeout, Object failureHandling) {
        try {
            waitForElementVisible(testObject, timeout);
            return true;
        } catch (Exception e) {
            com.katalan.core.compat.FailureHandling mode = toFailureHandling(failureHandling);
            handleVerifyFailure("verifyElementVisible", e, mode);
            return false;
        }
    }
    
    /**
     * Verify element is not present (with Object timeout for Katalon compatibility)
     */
    public static boolean verifyElementNotPresent(TestObject testObject, Object timeout) {
        return verifyElementNotPresent(testObject, toIntTimeout(timeout));
    }
    
    /**
     * Verify element is not present
     */
    public static boolean verifyElementNotPresent(TestObject testObject, int timeout) {
        return verifyElementNotPresent(testObject, timeout, null);
    }
    
    /**
     * Verify element is not present with failure handling (Katalon compatibility)
     * Supports OPTIONAL and STOP_ON_FAILURE constants
     */
    public static boolean verifyElementNotPresent(TestObject testObject, int timeout, Object failureHandling) {
        try {
            waitForElementNotPresent(testObject, timeout);
            return true;
        } catch (Exception e) {
            com.katalan.core.compat.FailureHandling mode = toFailureHandling(failureHandling);
            handleVerifyFailure("verifyElementNotPresent", e, mode);
            return false;
        }
    }
    
    /**
     * Verify text is present
     */
    public static boolean verifyTextPresent(String text, boolean isRegex) {
        return verifyTextPresent(text, isRegex, null);
    }
    
    /**
     * Verify text is present with failure handling
     */
    public static boolean verifyTextPresent(String text, boolean isRegex, Object failureHandling) {
        try {
            String pageSource = getDriver().getPageSource();
            boolean result;
            if (isRegex) {
                result = pageSource.matches("(?s).*" + text + ".*");
            } else {
                result = pageSource.contains(text);
            }
            if (!result) {
                throw new AssertionError("Text '" + text + "' not found in page source");
            }
            return true;
        } catch (Exception e) {
            com.katalan.core.compat.FailureHandling mode = toFailureHandling(failureHandling);
            return handleVerifyFailure("verifyTextPresent", e, mode);
        }
    }
    
    /**
     * Verify that actual text matches expected text (Katalon compatibility)
     */
    public static boolean verifyMatch(String actualText, String expectedText, boolean isRegex) {
        logger.info("Verifying match - Expected: '{}', Actual: '{}', IsRegex: {}", expectedText, actualText, isRegex);
        boolean result;
        if (isRegex) {
            result = actualText != null && actualText.matches(expectedText);
        } else {
            result = (actualText != null && actualText.equals(expectedText)) || 
                     (actualText == null && expectedText == null);
        }
        if (!result) {
            logger.warn("Verify match failed - Expected: '{}', Actual: '{}'", expectedText, actualText);
        }
        return result;
    }
    
    /**
     * Verify that actual text matches expected text with failure handling
     */
    public static boolean verifyMatch(String actualText, String expectedText, boolean isRegex, Object failureHandling) {
        return verifyMatch(actualText, expectedText, isRegex);
    }
    
    /**
     * Verify that actual text does not match expected text (Katalon compatibility)
     */
    public static boolean verifyNotMatch(String actualText, String expectedText, boolean isRegex) {
        return !verifyMatch(actualText, expectedText, isRegex);
    }
    
    /**
     * Verify element text
     */
    public static boolean verifyElementText(TestObject testObject, String expectedText) {
        return verifyElementText(testObject, expectedText, false, 30, null);
    }
    
    /**
     * Verify element text with regex support
     */
    public static boolean verifyElementText(TestObject testObject, String expectedText, boolean isRegex, int timeout) {
        return verifyElementText(testObject, expectedText, isRegex, timeout, null);
    }
    
    /**
     * Verify element text with regex support and failure handling
     */
    public static boolean verifyElementText(TestObject testObject, String expectedText, boolean isRegex, int timeout, Object failureHandling) {
        try {
            String actualText = getText(testObject, timeout);
            boolean result;
            if (isRegex) {
                result = actualText.matches(expectedText);
            } else {
                result = actualText.equals(expectedText);
            }
            if (!result) {
                throw new AssertionError("Expected text '" + expectedText + "' but got '" + actualText + "'");
            }
            return true;
        } catch (Exception e) {
            com.katalan.core.compat.FailureHandling mode = toFailureHandling(failureHandling);
            return handleVerifyFailure("verifyElementText", e, mode);
        }
    }
    
    /**
     * Verify element attribute value
     */
    public static boolean verifyElementAttributeValue(TestObject testObject, String attributeName, 
                                                       String expectedValue, int timeout) {
        return verifyElementAttributeValue(testObject, attributeName, expectedValue, timeout, null);
    }
    
    /**
     * Verify element attribute value with failure handling
     */
    public static boolean verifyElementAttributeValue(TestObject testObject, String attributeName, 
                                                       String expectedValue, int timeout, Object failureHandling) {
        try {
            String actualValue = getAttribute(testObject, attributeName, timeout);
            if (!expectedValue.equals(actualValue)) {
                throw new AssertionError("Expected attribute '" + attributeName + "' value '" + expectedValue + 
                                       "' but got '" + actualValue + "'");
            }
            return true;
        } catch (Exception e) {
            com.katalan.core.compat.FailureHandling mode = toFailureHandling(failureHandling);
            return handleVerifyFailure("verifyElementAttributeValue", e, mode);
        }
    }
    
    /**
     * Verify checkbox is checked
     */
    public static boolean verifyElementChecked(TestObject testObject, int timeout) {
        return verifyElementChecked(testObject, timeout, null);
    }
    
    /**
     * Verify checkbox is checked with failure handling
     */
    public static boolean verifyElementChecked(TestObject testObject, int timeout, Object failureHandling) {
        try {
            WebElement element = waitForElement(testObject, timeout);
            if (!element.isSelected()) {
                throw new AssertionError("Element is not checked: " + describe(testObject));
            }
            return true;
        } catch (Exception e) {
            com.katalan.core.compat.FailureHandling mode = toFailureHandling(failureHandling);
            return handleVerifyFailure("verifyElementChecked", e, mode);
        }
    }
    
    // ==================== Frame Keywords ====================
    
    /**
     * Switch to frame by index
     */
    public static void switchToFrame(int index) {
        logger.info("Switching to frame by index: {}", index);
        getDriver().switchTo().frame(index);
    }
    
    /**
     * Switch to frame by name or ID
     */
    public static void switchToFrame(String nameOrId) {
        logger.info("Switching to frame: {}", nameOrId);
        getDriver().switchTo().frame(nameOrId);
    }
    
    /**
     * Switch to frame by WebElement
     */
    public static void switchToFrame(TestObject testObject) {
        switchToFrame(testObject, 30);
    }
    
    /**
     * Switch to frame by TestObject with timeout
     */
    public static void switchToFrame(TestObject testObject, int timeout) {
        logger.info("Switching to frame: {}", testObject.getName());
        WebElement element = waitForElement(testObject, timeout);
        getDriver().switchTo().frame(element);
    }
    
    /**
     * Switch to default content
     */
    public static void switchToDefaultContent() {
        logger.info("Switching to default content");
        getDriver().switchTo().defaultContent();
    }
    
    /**
     * Switch to parent frame
     */
    public static void switchToParentFrame() {
        logger.info("Switching to parent frame");
        getDriver().switchTo().parentFrame();
    }
    
    // ==================== Window Keywords ====================
    
    /**
     * Switch to window by title
     */
    public static void switchToWindowTitle(String title) {
        logger.info("Switching to window with title: {}", title);
        WebDriver driver = getDriver();
        for (String handle : driver.getWindowHandles()) {
            driver.switchTo().window(handle);
            if (driver.getTitle().equals(title)) {
                return;
            }
        }
        throw new StepFailedException("Window with title '" + title + "' not found");
    }
    
    /**
     * Switch to window by index
     */
    public static void switchToWindowIndex(int index) {
        logger.info("Switching to window at index: {}", index);
        WebDriver driver = getDriver();
        String[] handles = driver.getWindowHandles().toArray(new String[0]);
        if (index < handles.length) {
            driver.switchTo().window(handles[index]);
        } else {
            throw new StepFailedException("Window at index " + index + " not found");
        }
    }
    
    /**
     * Switch to window by URL
     */
    public static void switchToWindowUrl(String url) {
        logger.info("Switching to window with URL: {}", url);
        WebDriver driver = getDriver();
        for (String handle : driver.getWindowHandles()) {
            driver.switchTo().window(handle);
            if (driver.getCurrentUrl().contains(url)) {
                return;
            }
        }
        throw new StepFailedException("Window with URL '" + url + "' not found");
    }
    
    /**
     * Get number of windows
     */
    public static int getWindowCount() {
        return getDriver().getWindowHandles().size();
    }
    
    // ==================== Alert Keywords ====================
    
    /**
     * Accept alert
     */
    public static void acceptAlert() {
        logger.info("Accepting alert");
        getDriver().switchTo().alert().accept();
    }
    
    /**
     * Dismiss alert
     */
    public static void dismissAlert() {
        logger.info("Dismissing alert");
        getDriver().switchTo().alert().dismiss();
    }
    
    /**
     * Get alert text
     */
    public static String getAlertText() {
        return getDriver().switchTo().alert().getText();
    }
    
    /**
     * Set alert text
     */
    public static void setAlertText(String text) {
        logger.info("Setting alert text: {}", text);
        getDriver().switchTo().alert().sendKeys(text);
    }
    
    /**
     * Wait for alert
     */
    public static void waitForAlert(int timeout) {
        logger.info("Waiting for alert");
        WebDriverWait wait = new WebDriverWait(getDriver(), Duration.ofSeconds(timeout));
        wait.until(ExpectedConditions.alertIsPresent());
    }
    
    // ==================== Scroll Keywords ====================
    
    /**
     * Scroll to element
     */
    public static void scrollToElement(TestObject testObject) {
        scrollToElement(testObject, 30);
    }
    
    /**
     * Scroll to element with timeout
     */
    public static void scrollToElement(TestObject testObject, int timeout) {
        logger.info("Scrolling to element: {}", testObject.getName());
        WebElement element = waitForElement(testObject, timeout);
        executeJavaScript("arguments[0].scrollIntoView(true);", element);
    }
    
    /**
     * Scroll to position
     */
    public static void scrollToPosition(int x, int y) {
        logger.info("Scrolling to position: ({}, {})", x, y);
        executeJavaScript("window.scrollTo(" + x + ", " + y + ");");
    }
    
    /**
     * Scroll to top
     */
    public static void scrollToTop() {
        scrollToPosition(0, 0);
    }
    
    /**
     * Scroll to bottom
     */
    public static void scrollToBottom() {
        executeJavaScript("window.scrollTo(0, document.body.scrollHeight);");
    }
    
    // ==================== Mouse Actions Keywords ====================
    
    /**
     * Mouse over element
     */
    public static void mouseOver(TestObject testObject) {
        mouseOver(testObject, 30);
    }
    
    /**
     * Mouse over element with timeout
     */
    public static void mouseOver(TestObject testObject, int timeout) {
        logger.info("Mouse over: {}", testObject.getName());
        WebElement element = waitForElement(testObject, timeout);
        new Actions(getDriver()).moveToElement(element).perform();
    }
    
    /**
     * Drag and drop
     */
    public static void dragAndDrop(TestObject source, TestObject target) {
        dragAndDrop(source, target, 30);
    }
    
    /**
     * Drag and drop with timeout
     */
    public static void dragAndDrop(TestObject source, TestObject target, int timeout) {
        logger.info("Drag and drop from {} to {}", source.getName(), target.getName());
        WebElement sourceElement = waitForElement(source, timeout);
        WebElement targetElement = waitForElement(target, timeout);
        new Actions(getDriver()).dragAndDrop(sourceElement, targetElement).perform();
    }
    
    /**
     * Focus on element
     */
    public static void focus(TestObject testObject) {
        focus(testObject, 30);
    }
    
    /**
     * Focus on element with timeout
     */
    public static void focus(TestObject testObject, int timeout) {
        logger.info("Focusing on: {}", testObject.getName());
        WebElement element = waitForElement(testObject, timeout);
        new Actions(getDriver()).moveToElement(element).click().perform();
    }
    
    // ==================== JavaScript Keywords ====================
    
    /**
     * Execute JavaScript
     */
    public static Object executeJavaScript(String script, Object... args) {
        logger.debug("Executing JavaScript: {}", script);
        JavascriptExecutor js = (JavascriptExecutor) getDriver();
        return js.executeScript(script, args);
    }
    
    /**
     * Execute async JavaScript
     */
    public static Object executeAsyncJavaScript(String script, Object... args) {
        logger.debug("Executing async JavaScript: {}", script);
        JavascriptExecutor js = (JavascriptExecutor) getDriver();
        return js.executeAsyncScript(script, args);
    }
    
    // ==================== Screenshot Keywords ====================
    
    /**
     * Take screenshot
     */
    public static String takeScreenshot() {
        // Use timestamp in milliseconds as filename (Katalon format)
        long timestamp = System.currentTimeMillis();
        return takeScreenshotInternal(String.valueOf(timestamp), "com.kms.katalon.core.webui.keyword.builtin.TakeScreenshotKeyword.takeScreenshot");
    }
    
    /**
     * Take screenshot with filename
     */
    public static String takeScreenshot(String filename) {
        // If filename is provided, use timestamp-based naming (Katalon format)
        long timestamp = System.currentTimeMillis();
        return takeScreenshotInternal(String.valueOf(timestamp), "com.kms.katalon.core.webui.keyword.builtin.TakeScreenshotKeyword.takeScreenshot");
    }
    
    /**
     * Internal screenshot method with logging
     */
    private static String takeScreenshotInternal(String filename, String methodName) {
        logger.info("Taking screenshot: {}", filename);
        try {
            TakesScreenshot ts = (TakesScreenshot) getDriver();
            File source = ts.getScreenshotAs(OutputType.FILE);
            Path destination = Path.of(getScreenshotPath(), filename + ".png");
            Files.createDirectories(destination.getParent());

            // Submit copy to background executor to avoid blocking test thread on IO
            AsyncScreenshotWriter.getInstance().submitCopy(source.toPath(), destination);
            
            // Log to XmlKeywordLogger with attachment property (Katalon format)
            com.katalan.core.logging.XmlKeywordLogger kwLogger = 
                com.katalan.core.logging.XmlKeywordLogger.getInstance();
            Map<String, String> props = new java.util.LinkedHashMap<>();
            props.put("attachment", filename + ".png");
            props.put("testops-method-name", methodName);
            props.put("testops-execution-stacktrace", "");
            kwLogger.logMessage("PASSED", "Taking screenshot successfully", props);
            
            return destination.toString();
        } catch (IOException e) {
            logger.error("Failed to take screenshot", e);
            
            // Log failure to XmlKeywordLogger
            com.katalan.core.logging.XmlKeywordLogger kwLogger = 
                com.katalan.core.logging.XmlKeywordLogger.getInstance();
            Map<String, String> props = new java.util.LinkedHashMap<>();
            props.put("testops-method-name", methodName);
            props.put("testops-execution-stacktrace", e.toString());
            kwLogger.logMessage("FAILED", "Failed to take screenshot: " + e.getMessage(), props);
            
            throw new RuntimeException("Failed to take screenshot", e);
        }
    }
    
    /**
     * Take element screenshot
     */
    public static String takeElementScreenshot(TestObject testObject, String filename) {
        logger.info("Taking element screenshot: {}", filename);
        try {
            // Use timestamp for element screenshots too
            long timestamp = System.currentTimeMillis();
            String screenshotFilename = String.valueOf(timestamp);
            
            WebElement element = findElement(testObject);
            File source = element.getScreenshotAs(OutputType.FILE);
            Path destination = Path.of(getScreenshotPath(), screenshotFilename + ".png");
            Files.createDirectories(destination.getParent());
            Files.copy(source.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
            
            // Log to XmlKeywordLogger with attachment property
            com.katalan.core.logging.XmlKeywordLogger kwLogger = 
                com.katalan.core.logging.XmlKeywordLogger.getInstance();
            Map<String, String> props = new java.util.LinkedHashMap<>();
            props.put("attachment", screenshotFilename + ".png");
            props.put("testops-method-name", "com.kms.katalon.core.webui.keyword.builtin.TakeElementScreenshotKeyword.takeElementScreenshot");
            props.put("testops-execution-stacktrace", "");
            kwLogger.logMessage("PASSED", "Taking element screenshot successfully", props);
            
            return destination.toString();
        } catch (IOException e) {
            logger.error("Failed to take element screenshot", e);
            
            // Log failure to XmlKeywordLogger
            com.katalan.core.logging.XmlKeywordLogger kwLogger = 
                com.katalan.core.logging.XmlKeywordLogger.getInstance();
            Map<String, String> props = new java.util.LinkedHashMap<>();
            props.put("testops-method-name", "com.kms.katalon.core.webui.keyword.builtin.TakeElementScreenshotKeyword.takeElementScreenshot");
            props.put("testops-execution-stacktrace", e.toString());
            kwLogger.logMessage("FAILED", "Failed to take element screenshot: " + e.getMessage(), props);
            
            throw new RuntimeException("Failed to take element screenshot", e);
        }
    }
    
    /**
     * Take screenshot as checkpoint (Katalon Visual Testing compatibility)
     * This is a simplified version that just takes a screenshot
     */
    public static String takeScreenshotAsCheckpoint(String checkpointName) {
        logger.info("Taking screenshot checkpoint: {}", checkpointName);
        // Use timestamp for checkpoint screenshots
        long timestamp = System.currentTimeMillis();
        return takeScreenshotInternal(String.valueOf(timestamp), "com.kms.katalon.core.webui.keyword.builtin.TakeScreenshotAsCheckpointKeyword.takeScreenshotAsCheckpoint");
    }
    
    /**
     * Take full page screenshot as checkpoint (Katalon Visual Testing compatibility)
     * This is a simplified version that takes a regular screenshot
     */
    public static String takeFullPageScreenshotAsCheckpoint(String checkpointName, List<TestObject> ignoredElements) {
        logger.info("Taking full page screenshot checkpoint: {} (ignoring {} elements)", checkpointName, 
            ignoredElements != null ? ignoredElements.size() : 0);
        // Use timestamp for full page checkpoint screenshots
        long timestamp = System.currentTimeMillis();
        return takeScreenshotInternal(String.valueOf(timestamp), "com.kms.katalon.core.webui.keyword.builtin.TakeFullPageScreenshotAsCheckpointKeyword.takeFullPageScreenshotAsCheckpoint");
    }
    
    /**
     * Take full page screenshot as checkpoint (no ignored elements)
     */
    public static String takeFullPageScreenshotAsCheckpoint(String checkpointName) {
        return takeFullPageScreenshotAsCheckpoint(checkpointName, null);
    }
    
    // ==================== Cookie Keywords ====================
    
    /**
     * Get all cookies
     */
    public static Set<Cookie> getAllCookies() {
        return getDriver().manage().getCookies();
    }
    
    /**
     * Get cookie by name
     */
    public static Cookie getCookieByName(String name) {
        return getDriver().manage().getCookieNamed(name);
    }
    
    /**
     * Add cookie
     */
    public static void addCookie(Cookie cookie) {
        logger.info("Adding cookie: {}", cookie.getName());
        getDriver().manage().addCookie(cookie);
    }
    
    /**
     * Delete cookie by name
     */
    public static void deleteCookie(String name) {
        logger.info("Deleting cookie: {}", name);
        getDriver().manage().deleteCookieNamed(name);
    }
    
    /**
     * Delete all cookies
     */
    public static void deleteAllCookies() {
        logger.info("Deleting all cookies");
        getDriver().manage().deleteAllCookies();
    }
    
    // ==================== Helper Methods ====================
    
    // ==================== Utility Keywords ====================
    
    /**
     * Add comment to the test log (Katalon compatibility)
     */
    public static void comment(String message) {
        logger.info("[COMMENT] {}", message);
    }
    
    /**
     * Add comment to the test log with variable
     */
    public static void comment(String message, Object... args) {
        logger.info("[COMMENT] {}", String.format(message.replace("{}", "%s"), args));
    }
    
    /**
     * Call another test case (Katalon compatibility)
     */
    public static void callTestCase(Object testCaseObj, java.util.Map<String, Object> variables) {
        callTestCase(testCaseObj, variables, null);
    }
    
    /**
     * Call another test case with failure handling
     */
    public static void callTestCase(Object testCaseObj, java.util.Map<String, Object> variables, Object failureHandling) {
        if (testCaseObj == null) {
            logger.warn("callTestCase: testCase is null (findTestCase may have failed)");
            throw new RuntimeException("Test case not found - findTestCase returned null");
        }
        
        // Check if it's our TestCase class
        if (!(testCaseObj instanceof com.katalan.core.compat.TestCase)) {
            logger.warn("callTestCase: invalid testCase type: {}", testCaseObj.getClass().getName());
            throw new RuntimeException("Invalid test case object type");
        }
        
        com.katalan.core.compat.TestCase testCase = (com.katalan.core.compat.TestCase) testCaseObj;
        logger.info("Calling test case: {}", testCase.getTestCaseName());
        
        ExecutionContext context = ExecutionContext.getCurrent();
        Object executorObj = context.getProperty("executor");
        
        if (executorObj == null) {
            logger.error("No executor available in context for callTestCase");
            throw new RuntimeException("Cannot execute nested test case - no executor available");
        }
        
        try {
            // Push variable scope for nested execution
            context.pushVariableScope();
            
            // Set variables for the called test case
            if (variables != null) {
                for (java.util.Map.Entry<String, Object> entry : variables.entrySet()) {
                    context.setVariable(entry.getKey(), entry.getValue());
                }
            }
            
            // Read and execute the script
            java.nio.file.Path scriptPath = testCase.getScriptPath();
            if (scriptPath == null || !java.nio.file.Files.exists(scriptPath)) {
                throw new RuntimeException("Script file not found for test case: " + testCase.getTestCaseName());
            }
            
            // Check if executor is KatalanBDDExecutor and use its executeTestCase method
            if (executorObj instanceof KatalanBDDExecutor) {
                KatalanBDDExecutor bddExecutor = (KatalanBDDExecutor) executorObj;
                bddExecutor.executeTestCase(testCase, variables);
            } else {
                // Use reflection to call executeScriptWithVariables for GroovyScriptExecutor
                java.lang.reflect.Method executeMethod = executorObj.getClass().getMethod("executeScriptWithVariables", 
                    java.nio.file.Path.class, java.util.Map.class);
                executeMethod.invoke(executorObj, scriptPath, variables);
            }
            
            logger.info("Test case completed: {}", testCase.getTestCaseName());
        } catch (java.lang.reflect.InvocationTargetException e) {
            logger.error("Called test case failed: {} - {}", testCase.getTestCaseName(), e.getCause().getMessage());
            throw new RuntimeException("Called test case failed: " + e.getCause().getMessage(), e.getCause());
        } catch (Exception e) {
            logger.error("Error calling test case: {} - {}", testCase.getTestCaseName(), e.getMessage());
            throw new RuntimeException("Error calling test case: " + e.getMessage(), e);
        } finally {
            // Pop variable scope
            context.popVariableScope();
        }
    }
    
    // ==================== Private Helper Methods ====================
    
    /**
     * Convert Katalon TestObject to katalan TestObject if needed
     */
    private static TestObject convertTestObject(Object testObjectParam) {
        if (testObjectParam == null) {
            throw new IllegalArgumentException("TestObject cannot be null");
        }
        
        // Already our TestObject
        if (testObjectParam instanceof TestObject) {
            return (TestObject) testObjectParam;
        }
        
        // Katalon TestObject - need to convert
        if (testObjectParam instanceof com.kms.katalon.core.testobject.TestObject) {
            com.kms.katalon.core.testobject.TestObject katalonTO = 
                (com.kms.katalon.core.testobject.TestObject) testObjectParam;
            
            // Create new katalan TestObject
            TestObject katalanTO = new TestObject(katalonTO.getObjectId());
            
            // Copy XPath selector (most common)
            String xpath = katalonTO.findPropertyValue("xpath", false);
            if (xpath != null) {
                katalanTO.setSelectorMethod(TestObject.SelectorMethod.XPATH);
                katalanTO.setSelectorValue(xpath);
            } else {
                // Try CSS selector
                String css = katalonTO.findPropertyValue("css", false);
                if (css != null) {
                    katalanTO.setSelectorMethod(TestObject.SelectorMethod.CSS);
                    katalanTO.setSelectorValue(css);
                } else {
                    // Try ID
                    String id = katalonTO.findPropertyValue("id", false);
                    if (id != null) {
                        katalanTO.setSelectorMethod(TestObject.SelectorMethod.ID);
                        katalanTO.setSelectorValue(id);
                    }
                }
            }
            
            return katalanTO;
        }
        
        throw new IllegalArgumentException("Unsupported TestObject type: " + testObjectParam.getClass().getName());
    }
    
    /**
     * Convert Object timeout to int (for Katalon compatibility where timeout can be String or Number)
     */
    private static int toIntTimeout(Object timeout) {
        if (timeout == null) {
            return 30; // default timeout
        }
        if (timeout instanceof Number) {
            return ((Number) timeout).intValue();
        }
        if (timeout instanceof String) {
            try {
                return Integer.parseInt((String) timeout);
            } catch (NumberFormatException e) {
                return 30; // default if parse fails
            }
        }
        return 30; // default
    }
    
    /**
     * Convert failureHandling Object to enum with context-aware defaults
     * Wait keywords default to STOP_ON_FAILURE (blocking operations)
     * Verify keywords default to CONTINUE_ON_FAILURE (validation checks)
     */
    private static com.katalan.core.compat.FailureHandling toFailureHandling(Object failureHandling) {
        if (failureHandling == null) {
            // Default varies by keyword type - caller should pass explicit default if needed
            return com.katalan.core.compat.FailureHandling.STOP_ON_FAILURE;
        }
        if (failureHandling instanceof com.katalan.core.compat.FailureHandling) {
            return (com.katalan.core.compat.FailureHandling) failureHandling;
        }
        if (failureHandling instanceof com.kms.katalon.core.model.FailureHandling) {
            return com.katalan.core.compat.FailureHandling.fromKatalon(
                (com.kms.katalon.core.model.FailureHandling) failureHandling);
        }
        // Try to parse string
        if (failureHandling instanceof String) {
            try {
                return com.katalan.core.compat.FailureHandling.valueOf((String) failureHandling);
            } catch (IllegalArgumentException e) {
                return com.katalan.core.compat.FailureHandling.STOP_ON_FAILURE;
            }
        }
        return com.katalan.core.compat.FailureHandling.STOP_ON_FAILURE;
    }
    
    /**
     * Handle exception according to failure handling mode
     * Returns true if exception was handled (not thrown), false if it should be re-thrown
     */
    private static boolean handleVerifyFailure(String methodName, Exception exception, 
                                               com.katalan.core.compat.FailureHandling mode) {
        switch (mode) {
            case STOP_ON_FAILURE:
                // Re-throw - will stop execution
                if (exception instanceof RuntimeException) {
                    throw (RuntimeException) exception;
                }
                throw new RuntimeException(methodName + " failed", exception);
                
            case CONTINUE_ON_FAILURE:
                // Log warning and continue
                logger.warn("{} failed: {}", methodName, exception.getMessage());
                return false;
                
            case OPTIONAL:
                // Log debug and continue silently
                logger.debug("{} failed (optional): {}", methodName, exception.getMessage());
                return false;
                
            default:
                // Default: continue on failure
                logger.warn("{} failed: {}", methodName, exception.getMessage());
                return false;
        }
    }
    
    private static WebDriver getDriver() {
        WebDriver driver = ExecutionContext.getCurrent().getWebDriver();
        if (driver == null) {
            throw new IllegalStateException("WebDriver is not initialized. Please call openBrowser first.");
        }
        return driver;
    }

    /**
     * Describe a TestObject for logging: prefer name, fall back to selector.
     * Optimized for performance - minimal string operations.
     */
    private static String describe(TestObject testObject) {
        if (testObject == null) return "<null>";
        String name = testObject.getName();
        // Quick path - avoid unnecessary calls if name exists
        return (name != null && !name.isEmpty()) ? name : String.valueOf(testObject.getSelectorValue());
    }
    
    /**
     * Wait for Element with Smart Wait (Katalon-compatible)
     * - Quick check for immediate return if element already present
     * - Auto-retries on StaleElementReferenceException  
     * - Fast 50ms polling for responsive UI interactions
     * - Simple presence check (no visibility/enabled requirements)
     */
    private static WebElement waitForElement(TestObject testObject, int timeout) {
        WebDriver driver = getDriver();
        By locator = testObject.toSeleniumBy();
        
        // OPTIMIZATION: Quick check if element already present
        // This avoids creating WebDriverWait when element is already there
        try {
            WebElement element = driver.findElement(locator);
            // Element found immediately - return it
            return element;
        } catch (NoSuchElementException ignored) {
            // Element not present yet, proceed to wait
        }
        
        // Element not immediately available, use WebDriverWait with fast polling
        // Use 50ms polling for faster detection (was 100ms)
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeout), Duration.ofMillis(50));
        
        // Wait for presence with auto-retry on stale elements
        return wait.until(driver1 -> {
            try {
                return driver1.findElement(locator);
            } catch (StaleElementReferenceException e) {
                // Auto-retry on stale - return null to continue polling
                return null;
            }
        });
    }
    
    private static WebElement findElement(TestObject testObject) {
        return getDriver().findElement(testObject.toSeleniumBy());
    }
    
    private static List<WebElement> findElements(TestObject testObject) {
        return getDriver().findElements(testObject.toSeleniumBy());
    }
    
    private static String getScreenshotPath() {
        ExecutionContext context = ExecutionContext.getCurrent();
        if (context.getRunConfiguration() != null && 
            context.getRunConfiguration().getScreenshotPath() != null) {
            return context.getRunConfiguration().getScreenshotPath().toString();
        }
        return "screenshots";
    }
    
    // ==================== Additional Keywords (CSWeb compatibility) ====================
    
    /**
     * Click element with offset (x, y coordinates from element's top-left corner)
     */
    public static void clickOffset(TestObject testObject, int offsetX, int offsetY) {
        clickOffset(testObject, offsetX, offsetY, 30);
    }
    
    /**
     * Click element with offset - accepts any TestObject type
     */
    public static void clickOffset(Object testObjectParam, int offsetX, int offsetY) {
        clickOffset(convertTestObject(testObjectParam), offsetX, offsetY, 30);
    }
    
    /**
     * Click element with offset and timeout - accepts any TestObject type
     */
    public static void clickOffset(Object testObjectParam, int offsetX, int offsetY, int timeout) {
        clickOffset(convertTestObject(testObjectParam), offsetX, offsetY, timeout);
    }
    
    /**
     * Click element with offset and timeout
     */
    public static void clickOffset(TestObject testObject, int offsetX, int offsetY, int timeout) {
        logger.info("Clicking on {} with offset ({}, {})", describe(testObject), offsetX, offsetY);
        WebElement element = waitForElement(testObject, timeout);
        new Actions(getDriver()).moveToElement(element, offsetX, offsetY).click().perform();
    }
    
    /**
     * Enhanced click with retry mechanism for flaky elements
     */
    public static void enhancedClick(TestObject testObject) {
        enhancedClick(testObject, 30);
    }
    
    /**
     * Enhanced click - accepts any TestObject type (Katalon or katalan)
     */
    public static void enhancedClick(Object testObjectParam) {
        enhancedClick(convertTestObject(testObjectParam), 30);
    }
    
    /**
     * Enhanced click with timeout - accepts any TestObject type
     */
    public static void enhancedClick(Object testObjectParam, int timeout) {
        enhancedClick(convertTestObject(testObjectParam), timeout);
    }
    
    /**
     * Enhanced click with retry mechanism and timeout
     */
    public static void enhancedClick(TestObject testObject, int timeout) {
        logger.info("Enhanced clicking on: {}", describe(testObject));
        WebDriver driver = getDriver();
        By by = testObject.toSeleniumBy();
        
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeout), Duration.ofMillis(100));
                WebElement element = wait.until(ExpectedConditions.elementToBeClickable(by));
                
                // Scroll into view
                ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({block:'center', inline:'center'});", element);
                
                try {
                    element.click();
                    return; // Success
                } catch (ElementClickInterceptedException e) {
                    // Try JS click as fallback
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
                    return;
                }
            } catch (StaleElementReferenceException e) {
                if (i == maxRetries - 1) throw e;
                logger.debug("Stale element, retrying... ({}/{})", i + 1, maxRetries);
                delay(1);
            }
        }
    }
    
    /**
     * Find single web element using TestObject
     */
    public static WebElement findWebElement(TestObject testObject) {
        return findWebElement(testObject, 30);
    }
    
    /**
     * Find single web element - accepts any TestObject type
     */
    public static WebElement findWebElement(Object testObjectParam) {
        return findWebElement(convertTestObject(testObjectParam), 30);
    }
    
    /**
     * Find single web element with timeout - accepts any TestObject type
     */
    public static WebElement findWebElement(Object testObjectParam, int timeout) {
        return findWebElement(convertTestObject(testObjectParam), timeout);
    }
    
    /**
     * Find single web element with timeout
     */
    public static WebElement findWebElement(TestObject testObject, int timeout) {
        return com.katalan.core.compat.WebUiCommonHelper.findWebElement(testObject, timeout);
    }
    
    /**
     * Find multiple web elements using TestObject
     */
    public static List<WebElement> findWebElements(TestObject testObject) {
        return findWebElements(testObject, 30);
    }
    
    /**
     * Find multiple web elements - accepts any TestObject type
     */
    public static List<WebElement> findWebElements(Object testObjectParam) {
        return findWebElements(convertTestObject(testObjectParam), 30);
    }
    
    /**
     * Find multiple web elements with timeout - accepts any TestObject type
     */
    public static List<WebElement> findWebElements(Object testObjectParam, int timeout) {
        return findWebElements(convertTestObject(testObjectParam), timeout);
    }
    
    /**
     * Find multiple web elements with timeout
     */
    public static List<WebElement> findWebElements(TestObject testObject, int timeout) {
        return com.katalan.core.compat.WebUiCommonHelper.findWebElements(testObject, timeout);
    }
    
    /**
     * Wait for page loading indicators to disappear (e.g., spinners, loading text)
     */
    public static void loading() {
        loading(30);
    }
    
    /**
     * Wait for page loading indicators to disappear with timeout
     */
    public static void loading(int timeout) {
        logger.info("Waiting for page to finish loading...");
        WebDriver driver = getDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeout), Duration.ofMillis(100));
        
        // Wait for document ready state
        wait.until(webDriver -> ((JavascriptExecutor) webDriver)
            .executeScript("return document.readyState").equals("complete"));
        
        // Wait for common loading indicators to disappear
        try {
            wait.until(ExpectedConditions.invisibilityOfElementLocated(
                By.xpath("//*[contains(text(),'Loading') or contains(text(),'loading') or " +
                        "contains(@class,'loading') or contains(@class,'spinner')]")));
        } catch (TimeoutException e) {
            // Loading indicators may not exist, that's OK
            logger.debug("No loading indicators found or they disappeared");
        }
    }
    
    /**
     * Get network/proxy information (placeholder for BrowserMob proxy integration)
     */
    public static Object network() {
        logger.debug("network() called - returns null (BrowserMob proxy not integrated)");
        return null;
    }
    
    /**
     * Take full page screenshot (scrolling through entire page)
     */
    public static String takeFullPageScreenshot() {
        return takeFullPageScreenshot(null);
    }
    
    /**
     * Take full page screenshot with filename
     */
    public static String takeFullPageScreenshot(String filename) {
        logger.info("Taking full page screenshot");
        
        if (filename == null || filename.isEmpty()) {
            filename = "fullpage_" + System.currentTimeMillis();
        }
        
        // For now, use regular screenshot - full page screenshot requires more complex implementation
        // TODO: Implement actual full-page screenshot by stitching multiple screenshots
        return takeScreenshot(filename);
    }
    
    /**
     * Verify element is clickable
     */
    public static boolean verifyElementClickable(TestObject testObject, int timeout) {
        return verifyElementClickable(testObject, timeout, null);
    }
    
    /**
     * Verify element is clickable - accepts any TestObject type
     */
    public static boolean verifyElementClickable(Object testObjectParam, int timeout) {
        return verifyElementClickable(convertTestObject(testObjectParam), timeout, null);
    }
    
    /**
     * Verify element is clickable with failure handling - accepts any TestObject type
     */
    public static boolean verifyElementClickable(Object testObjectParam, int timeout, Object failureHandling) {
        return verifyElementClickable(convertTestObject(testObjectParam), timeout, failureHandling);
    }
    
    /**
     * Verify element is clickable with failure handling
     */
    public static boolean verifyElementClickable(TestObject testObject, int timeout, Object failureHandling) {
        logger.info("Verifying element is clickable: {}", describe(testObject));
        try {
            boolean result = com.katalan.core.compat.WebUiCommonHelper.checkElementClickable(testObject, timeout);
            if (!result) {
                throw new RuntimeException("Element is not clickable: " + describe(testObject));
            }
            return true;
        } catch (Exception e) {
            return !handleVerifyFailure("verifyElementClickable", e, toFailureHandling(failureHandling));
        }
    }
    
    /**
     * Verify element has attribute
     */
    public static boolean verifyElementHasAttribute(TestObject testObject, String attributeName, int timeout) {
        return verifyElementHasAttribute(testObject, attributeName, timeout, null);
    }
    
    /**
     * Verify element has attribute with failure handling
     */
    public static boolean verifyElementHasAttribute(TestObject testObject, String attributeName, 
                                                    int timeout, Object failureHandling) {
        logger.info("Verifying element has attribute '{}': {}", attributeName, describe(testObject));
        try {
            WebElement element = waitForElement(testObject, timeout);
            String value = element.getAttribute(attributeName);
            if (value == null) {
                throw new RuntimeException("Element does not have attribute '" + attributeName + "': " + describe(testObject));
            }
            return true;
        } catch (Exception e) {
            return !handleVerifyFailure("verifyElementHasAttribute", e, toFailureHandling(failureHandling));
        }
    }
    
    /**
     * Verify element is in viewport
     */
    public static boolean verifyElementInViewport(TestObject testObject, int timeout) {
        return verifyElementInViewport(testObject, timeout, null);
    }
    
    /**
     * Verify element is in viewport with failure handling
     */
    public static boolean verifyElementInViewport(TestObject testObject, int timeout, Object failureHandling) {
        logger.info("Verifying element is in viewport: {}", describe(testObject));
        try {
            WebElement element = waitForElement(testObject, timeout);
            Boolean inViewport = (Boolean) ((JavascriptExecutor) getDriver()).executeScript(
                "var elem = arguments[0];" +
                "var rect = elem.getBoundingClientRect();" +
                "return (rect.top >= 0 && rect.left >= 0 && " +
                "rect.bottom <= (window.innerHeight || document.documentElement.clientHeight) && " +
                "rect.right <= (window.innerWidth || document.documentElement.clientWidth));",
                element
            );
            if (!inViewport) {
                throw new RuntimeException("Element is not in viewport: " + describe(testObject));
            }
            return true;
        } catch (Exception e) {
            return !handleVerifyFailure("verifyElementInViewport", e, toFailureHandling(failureHandling));
        }
    }
    
    /**
     * Verify element is NOT clickable
     */
    public static boolean verifyElementNotClickable(TestObject testObject, int timeout) {
        return verifyElementNotClickable(testObject, timeout, null);
    }
    
    /**
     * Verify element is NOT clickable with failure handling
     */
    public static boolean verifyElementNotClickable(TestObject testObject, int timeout, Object failureHandling) {
        logger.info("Verifying element is NOT clickable: {}", describe(testObject));
        try {
            boolean isClickable = com.katalan.core.compat.WebUiCommonHelper.checkElementClickable(testObject, timeout);
            if (isClickable) {
                throw new RuntimeException("Element is clickable (expected not clickable): " + describe(testObject));
            }
            return true;
        } catch (Exception e) {
            return !handleVerifyFailure("verifyElementNotClickable", e, toFailureHandling(failureHandling));
        }
    }
    
    /**
     * Verify element does NOT have attribute
     */
    public static boolean verifyElementNotHasAttribute(TestObject testObject, String attributeName, int timeout) {
        return verifyElementNotHasAttribute(testObject, attributeName, timeout, null);
    }
    
    /**
     * Verify element does NOT have attribute with failure handling
     */
    public static boolean verifyElementNotHasAttribute(TestObject testObject, String attributeName, 
                                                       int timeout, Object failureHandling) {
        logger.info("Verifying element does NOT have attribute '{}': {}", attributeName, describe(testObject));
        try {
            WebElement element = waitForElement(testObject, timeout);
            String value = element.getAttribute(attributeName);
            if (value != null) {
                throw new RuntimeException("Element has attribute '" + attributeName + "' (expected not to have): " + 
                                         describe(testObject));
            }
            return true;
        } catch (Exception e) {
            return !handleVerifyFailure("verifyElementNotHasAttribute", e, toFailureHandling(failureHandling));
        }
    }
    
    /**
     * Verify element is NOT visible
     */
    public static boolean verifyElementNotVisible(TestObject testObject, int timeout) {
        return verifyElementNotVisible(testObject, timeout, null);
    }
    
    /**
     * Verify element is NOT visible with failure handling
     */
    public static boolean verifyElementNotVisible(TestObject testObject, int timeout, Object failureHandling) {
        logger.info("Verifying element is NOT visible: {}", describe(testObject));
        try {
            WebDriver driver = getDriver();
            By by = testObject.toSeleniumBy();
            
            try {
                WebElement element = driver.findElement(by);
                if (element.isDisplayed()) {
                    throw new RuntimeException("Element is visible (expected not visible): " + describe(testObject));
                }
            } catch (NoSuchElementException e) {
                // Element doesn't exist - that's considered not visible
                return true;
            }
            return true;
        } catch (Exception e) {
            return !handleVerifyFailure("verifyElementNotVisible", e, toFailureHandling(failureHandling));
        }
    }
    
    /**
     * Verify two values are equal
     */
    public static boolean verifyEqual(Object actual, Object expected) {
        return verifyEqual(actual, expected, null);
    }
    
    /**
     * Verify two values are equal with failure handling
     */
    public static boolean verifyEqual(Object actual, Object expected, Object failureHandling) {
        logger.info("Verifying {} equals {}", actual, expected);
        try {
            if (actual == null && expected == null) {
                return true;
            }
            if (actual == null || expected == null) {
                throw new RuntimeException("Values not equal: " + actual + " != " + expected);
            }
            if (!actual.equals(expected)) {
                throw new RuntimeException("Values not equal: " + actual + " != " + expected);
            }
            return true;
        } catch (Exception e) {
            return !handleVerifyFailure("verifyEqual", e, toFailureHandling(failureHandling));
        }
    }
    
    /**
     * Verify the last downloaded file exists and matches criteria
     */
    public static boolean verifyFileLastDownload(String downloadPath) {
        return verifyFileLastDownload(downloadPath, null, null);
    }
    
    /**
     * Verify the last downloaded file with expected filename pattern
     */
    public static boolean verifyFileLastDownload(String downloadPath, String expectedFilenamePattern, Object failureHandling) {
        logger.info("Verifying last downloaded file in: {}", downloadPath);
        try {
            File dir = new File(downloadPath);
            if (!dir.exists() || !dir.isDirectory()) {
                throw new RuntimeException("Download directory does not exist: " + downloadPath);
            }
            
            File[] files = dir.listFiles();
            if (files == null || files.length == 0) {
                throw new RuntimeException("No files found in download directory: " + downloadPath);
            }
            
            // Find the most recently modified file
            File latestFile = null;
            long latestTime = 0;
            for (File file : files) {
                if (file.isFile() && file.lastModified() > latestTime) {
                    latestTime = file.lastModified();
                    latestFile = file;
                }
            }
            
            if (latestFile == null) {
                throw new RuntimeException("No files found in download directory: " + downloadPath);
            }
            
            logger.info("Latest downloaded file: {}", latestFile.getName());
            
            // Check filename pattern if provided
            if (expectedFilenamePattern != null && !expectedFilenamePattern.isEmpty()) {
                if (!latestFile.getName().matches(expectedFilenamePattern)) {
                    throw new RuntimeException("File name does not match pattern. Expected: " + 
                                             expectedFilenamePattern + ", Actual: " + latestFile.getName());
                }
            }
            
            return true;
        } catch (Exception e) {
            return !handleVerifyFailure("verifyFileLastDownload", e, toFailureHandling(failureHandling));
        }
    }
    
    /**
     * Wait for element to be NOT clickable
     */
    public static void waitForElementNotClickable(TestObject testObject, int timeout) {
        waitForElementNotClickable(testObject, timeout, null);
    }
    
    /**
     * Wait for element to be NOT clickable with failure handling
     */
    public static void waitForElementNotClickable(TestObject testObject, int timeout, Object failureHandling) {
        logger.info("Waiting for element to be NOT clickable: {}", describe(testObject));
        try {
            WebDriver driver = getDriver();
            By by = testObject.toSeleniumBy();
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeout), Duration.ofMillis(100));
            
            wait.until(webDriver -> {
                try {
                    WebElement element = webDriver.findElement(by);
                    return !element.isDisplayed() || !element.isEnabled();
                } catch (NoSuchElementException | StaleElementReferenceException e) {
                    return true; // Element not present = not clickable
                }
            });
        } catch (Exception e) {
            com.katalan.core.compat.FailureHandling mode = toFailureHandling(failureHandling);
            if (mode == com.katalan.core.compat.FailureHandling.STOP_ON_FAILURE) {
                if (e instanceof RuntimeException) throw (RuntimeException) e;
                throw new RuntimeException("waitForElementNotClickable failed", e);
            }
            // CONTINUE_ON_FAILURE or OPTIONAL - log and return
            if (mode == com.katalan.core.compat.FailureHandling.CONTINUE_ON_FAILURE) {
                logger.warn("waitForElementNotClickable failed: {}", e.getMessage());
            } else {
                logger.debug("waitForElementNotClickable failed (optional): {}", e.getMessage());
            }
        }
    }
    
    /**
     * Wait for element to NOT have attribute
     */
    public static void waitForElementNotHasAttribute(TestObject testObject, String attributeName, int timeout) {
        waitForElementNotHasAttribute(testObject, attributeName, timeout, null);
    }
    
    /**
     * Wait for element to NOT have attribute with failure handling
     */
    public static void waitForElementNotHasAttribute(TestObject testObject, String attributeName, 
                                                     int timeout, Object failureHandling) {
        logger.info("Waiting for element to NOT have attribute '{}': {}", attributeName, describe(testObject));
        try {
            WebDriver driver = getDriver();
            By by = testObject.toSeleniumBy();
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeout), Duration.ofMillis(100));
            
            wait.until(webDriver -> {
                try {
                    WebElement element = webDriver.findElement(by);
                    return element.getAttribute(attributeName) == null;
                } catch (NoSuchElementException | StaleElementReferenceException e) {
                    return true; // Element not present = doesn't have attribute
                }
            });
        } catch (Exception e) {
            com.katalan.core.compat.FailureHandling mode = toFailureHandling(failureHandling);
            if (mode == com.katalan.core.compat.FailureHandling.STOP_ON_FAILURE) {
                if (e instanceof RuntimeException) throw (RuntimeException) e;
                throw new RuntimeException("waitForElementNotHasAttribute failed", e);
            }
            // CONTINUE_ON_FAILURE or OPTIONAL - log and return
            if (mode == com.katalan.core.compat.FailureHandling.CONTINUE_ON_FAILURE) {
                logger.warn("waitForElementNotHasAttribute failed: {}", e.getMessage());
            } else {
                logger.debug("waitForElementNotHasAttribute failed (optional): {}", e.getMessage());
            }
        }
    }
    
    /**
     * Wait for element to be NOT visible
     */
    public static void waitForElementNotVisible(TestObject testObject, int timeout) {
        waitForElementNotVisible(testObject, timeout, null);
    }
    
    /**
     * Wait for element to be NOT visible with failure handling
     */
    public static void waitForElementNotVisible(TestObject testObject, int timeout, Object failureHandling) {
        logger.info("Waiting for element to be NOT visible: {}", describe(testObject));
        try {
            WebDriver driver = getDriver();
            By by = testObject.toSeleniumBy();
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeout), Duration.ofMillis(100));
            
            wait.until(ExpectedConditions.invisibilityOfElementLocated(by));
        } catch (Exception e) {
            com.katalan.core.compat.FailureHandling mode = toFailureHandling(failureHandling);
            if (mode == com.katalan.core.compat.FailureHandling.STOP_ON_FAILURE) {
                if (e instanceof RuntimeException) throw (RuntimeException) e;
                throw new RuntimeException("waitForElementNotVisible failed", e);
            }
            // CONTINUE_ON_FAILURE or OPTIONAL - log and return
            if (mode == com.katalan.core.compat.FailureHandling.CONTINUE_ON_FAILURE) {
                logger.warn("waitForElementNotVisible failed: {}", e.getMessage());
            } else {
                logger.debug("waitForElementNotVisible failed (optional): {}", e.getMessage());
            }
        }
    }
}
