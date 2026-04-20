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
        logger.info("Opening browser with URL: {}", url);
        ExecutionContext context = ExecutionContext.getCurrent();
        WebDriver driver = context.getWebDriver();
        
        // Check if current driver is valid
        if (driver == null || isDriverClosed(driver)) {
            // Create new browser instance
            logger.info("Creating new browser instance");
            com.katalan.core.driver.WebDriverFactory factory = new com.katalan.core.driver.WebDriverFactory();
            driver = com.katalan.core.driver.WebDriverFactory.createDriver(context.getRunConfiguration());
            context.setWebDriver(driver);
        }
        
        // Only navigate if URL is provided (non-empty)
        if (url != null && !url.trim().isEmpty()) {
            driver.get(url);
        }
        // Empty URL means just open the browser without navigating (Katalon behavior)
    }
    
    /**
     * Check if WebDriver session is closed
     */
    private static boolean isDriverClosed(WebDriver driver) {
        try {
            driver.getTitle(); // Try a simple command
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
        getDriver().get(url);
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
        logger.info("Clicking on: {}", testObject.getName());
        WebElement element = waitForElement(testObject, timeout);
        element.click();
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
        logger.info("Double clicking on: {}", testObject.getName());
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
        logger.info("Right clicking on: {}", testObject.getName());
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
        logger.info("JS Click on: {}", testObject.getName());
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
     * Set text to an input field with timeout
     */
    public static void setText(TestObject testObject, String text, int timeout) {
        logger.info("Setting text '{}' to: {}", text, testObject.getName());
        WebElement element = waitForElement(testObject, timeout);
        element.clear();
        element.sendKeys(text);
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
        logger.info("Clearing text from: {}", testObject.getName());
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
        logger.info("Sending text to: {}", testObject.getName());
        WebElement element = waitForElement(testObject, timeout);
        element.sendKeys(text);
    }
    
    /**
     * Send keys to an element with timeout
     */
    public static void sendKeys(TestObject testObject, int timeout, Keys... keys) {
        logger.info("Sending keys to: {}", testObject.getName());
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
        logger.info("Uploading file '{}' to: {}", filePath, testObject.getName());
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
        logger.info("Selecting option '{}' from: {}", label, testObject.getName());
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
        logger.info("Selecting option by value '{}' from: {}", value, testObject.getName());
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
        logger.info("Selecting option by index {} from: {}", index, testObject.getName());
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
        logger.info("Deselecting all options from: {}", testObject.getName());
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
        logger.info("Checking checkbox: {}", testObject.getName());
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
        logger.info("Unchecking checkbox: {}", testObject.getName());
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
        logger.info("Waiting for element present: {}", testObject.getName());
        WebDriverWait wait = new WebDriverWait(getDriver(), Duration.ofSeconds(timeout));
        wait.until(ExpectedConditions.presenceOfElementLocated(testObject.toSeleniumBy()));
    }
    
    /**
     * Wait for element to be visible
     */
    public static void waitForElementVisible(TestObject testObject, int timeout) {
        logger.info("Waiting for element visible: {}", testObject.getName());
        WebDriverWait wait = new WebDriverWait(getDriver(), Duration.ofSeconds(timeout));
        wait.until(ExpectedConditions.visibilityOfElementLocated(testObject.toSeleniumBy()));
    }
    
    /**
     * Wait for element to be clickable
     */
    public static void waitForElementClickable(TestObject testObject, int timeout) {
        logger.info("Waiting for element clickable: {}", testObject.getName());
        WebDriverWait wait = new WebDriverWait(getDriver(), Duration.ofSeconds(timeout));
        wait.until(ExpectedConditions.elementToBeClickable(testObject.toSeleniumBy()));
    }
    
    /**
     * Wait for element to not be present
     */
    public static void waitForElementNotPresent(TestObject testObject, int timeout) {
        logger.info("Waiting for element not present: {}", testObject.getName());
        WebDriverWait wait = new WebDriverWait(getDriver(), Duration.ofSeconds(timeout));
        wait.until(ExpectedConditions.invisibilityOfElementLocated(testObject.toSeleniumBy()));
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
        try {
            waitForElementPresent(testObject, timeout);
            return true;
        } catch (Exception e) {
            logger.warn("Element not present: {}", testObject.getName());
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
        try {
            waitForElementVisible(testObject, timeout);
            return true;
        } catch (Exception e) {
            logger.warn("Element not visible: {}", testObject.getName());
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
        try {
            waitForElementNotPresent(testObject, timeout);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Verify text is present
     */
    public static boolean verifyTextPresent(String text, boolean isRegex) {
        String pageSource = getDriver().getPageSource();
        if (isRegex) {
            return pageSource.matches("(?s).*" + text + ".*");
        }
        return pageSource.contains(text);
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
        return verifyElementText(testObject, expectedText, false, 30);
    }
    
    /**
     * Verify element text with regex support
     */
    public static boolean verifyElementText(TestObject testObject, String expectedText, boolean isRegex, int timeout) {
        String actualText = getText(testObject, timeout);
        if (isRegex) {
            return actualText.matches(expectedText);
        }
        return actualText.equals(expectedText);
    }
    
    /**
     * Verify element attribute value
     */
    public static boolean verifyElementAttributeValue(TestObject testObject, String attributeName, 
                                                       String expectedValue, int timeout) {
        String actualValue = getAttribute(testObject, attributeName, timeout);
        return expectedValue.equals(actualValue);
    }
    
    /**
     * Verify checkbox is checked
     */
    public static boolean verifyElementChecked(TestObject testObject, int timeout) {
        WebElement element = waitForElement(testObject, timeout);
        return element.isSelected();
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
        return takeScreenshot("screenshot_" + System.currentTimeMillis());
    }
    
    /**
     * Take screenshot with filename
     */
    public static String takeScreenshot(String filename) {
        logger.info("Taking screenshot: {}", filename);
        try {
            TakesScreenshot ts = (TakesScreenshot) getDriver();
            File source = ts.getScreenshotAs(OutputType.FILE);
            Path destination = Path.of(getScreenshotPath(), filename + ".png");
            Files.createDirectories(destination.getParent());
            Files.copy(source.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
            return destination.toString();
        } catch (IOException e) {
            logger.error("Failed to take screenshot", e);
            throw new RuntimeException("Failed to take screenshot", e);
        }
    }
    
    /**
     * Take element screenshot
     */
    public static String takeElementScreenshot(TestObject testObject, String filename) {
        logger.info("Taking element screenshot: {}", filename);
        try {
            WebElement element = findElement(testObject);
            File source = element.getScreenshotAs(OutputType.FILE);
            Path destination = Path.of(getScreenshotPath(), filename + ".png");
            Files.createDirectories(destination.getParent());
            Files.copy(source.toPath(), destination);
            return destination.toString();
        } catch (IOException e) {
            logger.error("Failed to take element screenshot", e);
            throw new RuntimeException("Failed to take element screenshot", e);
        }
    }
    
    /**
     * Take screenshot as checkpoint (Katalon Visual Testing compatibility)
     * This is a simplified version that just takes a screenshot
     */
    public static String takeScreenshotAsCheckpoint(String checkpointName) {
        logger.info("Taking screenshot checkpoint: {}", checkpointName);
        return takeScreenshot("checkpoint_" + checkpointName.replaceAll("\\s+", "_"));
    }
    
    /**
     * Take full page screenshot as checkpoint (Katalon Visual Testing compatibility)
     * This is a simplified version that takes a regular screenshot
     */
    public static String takeFullPageScreenshotAsCheckpoint(String checkpointName, List<TestObject> ignoredElements) {
        logger.info("Taking full page screenshot checkpoint: {} (ignoring {} elements)", checkpointName, 
            ignoredElements != null ? ignoredElements.size() : 0);
        return takeScreenshot("checkpoint_fullpage_" + checkpointName.replaceAll("\\s+", "_"));
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
    
    private static WebDriver getDriver() {
        WebDriver driver = ExecutionContext.getCurrent().getWebDriver();
        if (driver == null) {
            throw new IllegalStateException("WebDriver is not initialized. Please call openBrowser first.");
        }
        return driver;
    }
    
    private static WebElement waitForElement(TestObject testObject, int timeout) {
        WebDriverWait wait = new WebDriverWait(getDriver(), Duration.ofSeconds(timeout));
        return wait.until(ExpectedConditions.presenceOfElementLocated(testObject.toSeleniumBy()));
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
}
