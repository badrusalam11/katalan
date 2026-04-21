package com.kms.katalon.core.webui.keyword;

import com.katalan.keywords.WebUI;
import com.kms.katalon.core.model.FailureHandling;
import com.katalan.core.context.ExecutionContext;
import com.kms.katalon.core.testobject.TestObject;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * Katalon compatibility layer for WebUiBuiltInKeywords
 * This class delegates calls to Katalan's WebUI implementation,
 * allowing JAR libraries compiled against Katalon to work with Katalan.
 * 
 * Methods not available in Katalan's WebUI are stubbed with warnings.
 */
public class WebUiBuiltInKeywords {
    
    private static final Logger logger = LoggerFactory.getLogger(WebUiBuiltInKeywords.class);
    
    private static void logNotSupported(String methodName) {
        logger.warn("Method {} is not fully supported in Katalan compatibility layer", methodName);
    }
    
    private static WebDriver getCurrentDriver() {
        return ExecutionContext.getCurrent().getWebDriver();
    }
    
    /**
     * Convert Katalon TestObject to Katalan TestObject
     */
    private static com.katalan.core.model.TestObject toKatalan(TestObject to) {
        if (to == null) return null;
        return to.toKatalanTestObject();
    }
    
    // ==================== Browser Keywords ====================
    
    public static void openBrowser(String url) {
        WebUI.openBrowser(url);
    }
    
    public static void openBrowser(String url, FailureHandling flowControl) {
        WebUI.openBrowser(url);
    }

    // ==================== Smart Wait (Katalon-specific, no-op in Katalan) ====================

    public static void enableSmartWait() {
        logger.debug("enableSmartWait() called - no-op in Katalan compatibility layer");
    }

    public static void enableSmartWait(FailureHandling flowControl) {
        logger.debug("enableSmartWait(flowControl) called - no-op in Katalan compatibility layer");
    }

    public static void disableSmartWait() {
        logger.debug("disableSmartWait() called - no-op in Katalan compatibility layer");
    }

    public static void disableSmartWait(FailureHandling flowControl) {
        logger.debug("disableSmartWait(flowControl) called - no-op in Katalan compatibility layer");
    }
    
    public static void closeBrowser() {
        WebUI.closeBrowser();
    }
    
    public static void closeBrowser(FailureHandling flowControl) {
        WebUI.closeBrowser();
    }
    
    public static void navigateToUrl(String url) {
        WebUI.navigateToUrl(url);
    }
    
    public static void navigateToUrl(String url, FailureHandling flowControl) {
        WebUI.navigateToUrl(url);
    }
    
    public static void refresh() {
        WebUI.refresh();
    }
    
    public static void refresh(FailureHandling flowControl) {
        WebUI.refresh();
    }
    
    public static void back() {
        WebUI.back();
    }
    
    public static void back(FailureHandling flowControl) {
        WebUI.back();
    }
    
    public static void forward() {
        WebUI.forward();
    }
    
    public static void forward(FailureHandling flowControl) {
        WebUI.forward();
    }
    
    public static String getUrl() {
        return WebUI.getUrl();
    }
    
    public static String getUrl(FailureHandling flowControl) {
        return WebUI.getUrl();
    }
    
    public static void maximizeWindow() {
        WebUI.maximizeWindow();
    }
    
    public static void maximizeWindow(FailureHandling flowControl) {
        WebUI.maximizeWindow();
    }
    
    public static void setViewPortSize(int width, int height) {
        WebDriver driver = getCurrentDriver();
        if (driver != null) {
            driver.manage().window().setSize(new org.openqa.selenium.Dimension(width, height));
        }
    }
    
    public static void setViewPortSize(int width, int height, FailureHandling flowControl) {
        setViewPortSize(width, height);
    }
    
    public static WebDriver getWebDriver() {
        return getCurrentDriver();
    }
    
    // ==================== Click Keywords ====================
    
    public static void click(TestObject to) {
        WebUI.click(toKatalan(to));
    }
    
    public static void click(TestObject to, FailureHandling flowControl) {
        WebUI.click(toKatalan(to));
    }
    
    public static void doubleClick(TestObject to) {
        WebUI.doubleClick(toKatalan(to));
    }
    
    public static void doubleClick(TestObject to, FailureHandling flowControl) {
        WebUI.doubleClick(toKatalan(to));
    }
    
    public static void rightClick(TestObject to) {
        WebUI.rightClick(toKatalan(to));
    }
    
    public static void rightClick(TestObject to, FailureHandling flowControl) {
        WebUI.rightClick(toKatalan(to));
    }
    
    public static void clickOffset(TestObject to, int offsetX, int offsetY) {
        logNotSupported("clickOffset");
        WebUI.click(toKatalan(to));
    }
    
    public static void clickOffset(TestObject to, int offsetX, int offsetY, FailureHandling flowControl) {
        clickOffset(to, offsetX, offsetY);
    }
    
    // ==================== Text Input Keywords ====================
    
    public static void setText(TestObject to, String text) {
        WebUI.setText(toKatalan(to), text);
    }
    
    public static void setText(TestObject to, String text, FailureHandling flowControl) {
        WebUI.setText(toKatalan(to), text);
    }
    
    public static void sendKeys(TestObject to, String keys) {
        WebUI.sendKeys(toKatalan(to), 30, keys);
    }
    
    public static void sendKeys(TestObject to, String keys, FailureHandling flowControl) {
        WebUI.sendKeys(toKatalan(to), 30, keys);
    }
    
    public static void clearText(TestObject to) {
        WebUI.clearText(toKatalan(to));
    }
    
    public static void clearText(TestObject to, FailureHandling flowControl) {
        WebUI.clearText(toKatalan(to));
    }
    
    public static void setEncryptedText(TestObject to, String encryptedText) {
        logNotSupported("setEncryptedText");
        WebUI.setText(toKatalan(to), encryptedText);
    }
    
    public static void setEncryptedText(TestObject to, String encryptedText, FailureHandling flowControl) {
        setEncryptedText(to, encryptedText);
    }
    
    // ==================== Wait Keywords ====================
    
    public static void delay(int seconds) {
        WebUI.delay(seconds);
    }
    
    public static void delay(int seconds, FailureHandling flowControl) {
        WebUI.delay(seconds);
    }
    
    public static boolean waitForElementVisible(TestObject to, int timeout) {
        WebUI.waitForElementVisible(toKatalan(to), timeout);
        return true;
    }
    
    public static boolean waitForElementVisible(TestObject to, int timeout, FailureHandling flowControl) {
        return waitForElementVisible(to, timeout);
    }
    
    public static boolean waitForElementNotVisible(TestObject to, int timeout) {
        logNotSupported("waitForElementNotVisible");
        WebUI.delay(1);
        return true;
    }
    
    public static boolean waitForElementNotVisible(TestObject to, int timeout, FailureHandling flowControl) {
        return waitForElementNotVisible(to, timeout);
    }
    
    public static boolean waitForElementPresent(TestObject to, int timeout) {
        WebUI.waitForElementPresent(toKatalan(to), timeout);
        return true;
    }
    
    public static boolean waitForElementPresent(TestObject to, int timeout, FailureHandling flowControl) {
        return waitForElementPresent(to, timeout);
    }
    
    public static boolean waitForElementNotPresent(TestObject to, int timeout) {
        WebUI.waitForElementNotPresent(toKatalan(to), timeout);
        return true;
    }
    
    public static boolean waitForElementNotPresent(TestObject to, int timeout, FailureHandling flowControl) {
        return waitForElementNotPresent(to, timeout);
    }
    
    public static boolean waitForElementClickable(TestObject to, int timeout) {
        WebUI.waitForElementClickable(toKatalan(to), timeout);
        return true;
    }
    
    public static boolean waitForElementClickable(TestObject to, int timeout, FailureHandling flowControl) {
        return waitForElementClickable(to, timeout);
    }
    
    public static boolean waitForPageLoad(int timeout) {
        WebUI.waitForPageLoad(timeout);
        return true;
    }
    
    public static boolean waitForPageLoad(int timeout, FailureHandling flowControl) {
        return waitForPageLoad(timeout);
    }
    
    public static boolean waitForAlert(int timeout) {
        WebUI.waitForAlert(timeout);
        return true;
    }
    
    public static boolean waitForAlert(int timeout, FailureHandling flowControl) {
        return waitForAlert(timeout);
    }
    
    // ==================== Verification Keywords ====================
    
    public static boolean verifyElementPresent(TestObject to, int timeout) {
        return WebUI.verifyElementPresent(toKatalan(to), timeout);
    }
    
    public static boolean verifyElementPresent(TestObject to, int timeout, FailureHandling flowControl) {
        return verifyElementPresent(to, timeout);
    }
    
    public static boolean verifyElementNotPresent(TestObject to, int timeout) {
        return WebUI.verifyElementNotPresent(toKatalan(to), timeout);
    }
    
    public static boolean verifyElementNotPresent(TestObject to, int timeout, FailureHandling flowControl) {
        return verifyElementNotPresent(to, timeout);
    }
    
    public static boolean verifyElementVisible(TestObject to) {
        return WebUI.verifyElementVisible(toKatalan(to), 30);
    }
    
    public static boolean verifyElementVisible(TestObject to, FailureHandling flowControl) {
        return WebUI.verifyElementVisible(toKatalan(to), 30);
    }
    
    public static boolean verifyElementVisible(TestObject to, int timeout) {
        return WebUI.verifyElementVisible(toKatalan(to), timeout);
    }
    
    public static boolean verifyElementVisible(TestObject to, int timeout, FailureHandling flowControl) {
        return WebUI.verifyElementVisible(toKatalan(to), timeout);
    }
    
    public static boolean verifyElementNotVisible(TestObject to, FailureHandling flowControl) {
        logNotSupported("verifyElementNotVisible");
        return true;
    }
    
    public static boolean verifyElementClickable(TestObject to, FailureHandling flowControl) {
        logNotSupported("verifyElementClickable");
        return true;
    }
    
    public static boolean verifyElementText(TestObject to, String text) {
        return WebUI.verifyElementText(toKatalan(to), text);
    }
    
    public static boolean verifyElementText(TestObject to, String text, FailureHandling flowControl) {
        return verifyElementText(to, text);
    }
    
    public static boolean verifyElementAttributeValue(TestObject to, String attribute, String value, int timeout) {
        return WebUI.verifyElementAttributeValue(toKatalan(to), attribute, value, timeout);
    }
    
    public static boolean verifyElementAttributeValue(TestObject to, String attribute, String value, int timeout, FailureHandling flowControl) {
        return verifyElementAttributeValue(to, attribute, value, timeout);
    }
    
    public static boolean verifyTextPresent(String text, boolean isRegex) {
        return WebUI.verifyTextPresent(text, isRegex);
    }
    
    public static boolean verifyTextPresent(String text, boolean isRegex, FailureHandling flowControl) {
        return verifyTextPresent(text, isRegex);
    }
    
    public static boolean verifyTextNotPresent(String text, boolean isRegex) {
        logNotSupported("verifyTextNotPresent");
        return true;
    }
    
    public static boolean verifyTextNotPresent(String text, boolean isRegex, FailureHandling flowControl) {
        return verifyTextNotPresent(text, isRegex);
    }
    
    // ==================== Get/Attribute Keywords ====================
    
    public static String getText(TestObject to) {
        return WebUI.getText(toKatalan(to));
    }
    
    public static String getText(TestObject to, FailureHandling flowControl) {
        return getText(to);
    }
    
    public static String getAttribute(TestObject to, String attribute) {
        return WebUI.getAttribute(toKatalan(to), attribute);
    }
    
    public static String getAttribute(TestObject to, String attribute, FailureHandling flowControl) {
        return getAttribute(to, attribute);
    }
    
    public static int getElementHeight(TestObject to) {
        logNotSupported("getElementHeight");
        return 0;
    }
    
    public static int getElementWidth(TestObject to) {
        logNotSupported("getElementWidth");
        return 0;
    }
    
    public static int getElementLeftPosition(TestObject to) {
        logNotSupported("getElementLeftPosition");
        return 0;
    }
    
    public static int getNumberOfTotalOption(TestObject to) {
        return WebUI.getNumberOfTotalOption(toKatalan(to));
    }
    
    public static int getNumberOfSelectedOption(TestObject to) {
        return WebUI.getNumberOfSelectedOption(toKatalan(to));
    }
    
    // ==================== Select/Dropdown Keywords ====================
    
    public static void selectOptionByValue(TestObject to, String value) {
        WebUI.selectOptionByValue(toKatalan(to), value);
    }
    
    public static void selectOptionByValue(TestObject to, String value, boolean isRegex) {
        WebUI.selectOptionByValue(toKatalan(to), value, isRegex, 30);
    }
    
    public static void selectOptionByValue(TestObject to, String value, boolean isRegex, FailureHandling flowControl) {
        selectOptionByValue(to, value, isRegex);
    }
    
    public static void selectOptionByLabel(TestObject to, String label) {
        WebUI.selectOptionByLabel(toKatalan(to), label);
    }
    
    public static void selectOptionByLabel(TestObject to, String label, boolean isRegex) {
        WebUI.selectOptionByLabel(toKatalan(to), label, isRegex, 30);
    }
    
    public static void selectOptionByLabel(TestObject to, String label, boolean isRegex, FailureHandling flowControl) {
        selectOptionByLabel(to, label, isRegex);
    }
    
    public static void selectOptionByIndex(TestObject to, int index) {
        WebUI.selectOptionByIndex(toKatalan(to), index);
    }
    
    public static void selectOptionByIndex(TestObject to, int index, FailureHandling flowControl) {
        selectOptionByIndex(to, index);
    }
    
    public static void deselectAllOption(TestObject to) {
        WebUI.deselectAllOption(toKatalan(to));
    }
    
    public static void deselectAllOption(TestObject to, FailureHandling flowControl) {
        deselectAllOption(to);
    }
    
    public static void deselectOptionByValue(TestObject to, String value) {
        logNotSupported("deselectOptionByValue");
    }
    
    public static void deselectOptionByValue(TestObject to, String value, boolean isRegex) {
        deselectOptionByValue(to, value);
    }
    
    public static void deselectOptionByLabel(TestObject to, String label) {
        logNotSupported("deselectOptionByLabel");
    }
    
    public static void deselectOptionByLabel(TestObject to, String label, boolean isRegex) {
        deselectOptionByLabel(to, label);
    }
    
    public static void deselectOptionByIndex(TestObject to, int index) {
        logNotSupported("deselectOptionByIndex");
    }
    
    // ==================== Checkbox/Radio Keywords ====================
    
    public static void check(TestObject to) {
        WebUI.check(toKatalan(to));
    }
    
    public static void check(TestObject to, FailureHandling flowControl) {
        check(to);
    }
    
    public static void uncheck(TestObject to) {
        WebUI.uncheck(toKatalan(to));
    }
    
    public static void uncheck(TestObject to, FailureHandling flowControl) {
        uncheck(to);
    }
    
    public static boolean verifyElementChecked(TestObject to, int timeout) {
        return WebUI.verifyElementChecked(toKatalan(to), timeout);
    }
    
    public static boolean verifyElementChecked(TestObject to, int timeout, FailureHandling flowControl) {
        return verifyElementChecked(to, timeout);
    }
    
    public static boolean verifyElementNotChecked(TestObject to, int timeout) {
        logNotSupported("verifyElementNotChecked");
        return true;
    }
    
    public static boolean verifyElementNotChecked(TestObject to, int timeout, FailureHandling flowControl) {
        return verifyElementNotChecked(to, timeout);
    }
    
    // ==================== Alert Keywords ====================
    
    public static void acceptAlert() {
        WebUI.acceptAlert();
    }
    
    public static void acceptAlert(FailureHandling flowControl) {
        acceptAlert();
    }
    
    public static void dismissAlert() {
        WebUI.dismissAlert();
    }
    
    public static void dismissAlert(FailureHandling flowControl) {
        dismissAlert();
    }
    
    public static String getAlertText() {
        return WebUI.getAlertText();
    }
    
    public static String getAlertText(FailureHandling flowControl) {
        return getAlertText();
    }
    
    public static void setAlertText(String text) {
        WebUI.setAlertText(text);
    }
    
    public static void setAlertText(String text, FailureHandling flowControl) {
        setAlertText(text);
    }
    
    // ==================== Frame/Window Keywords ====================
    
    public static void switchToFrame(TestObject to) {
        WebUI.switchToFrame(toKatalan(to));
    }
    
    public static void switchToFrame(TestObject to, int timeout) {
        WebUI.switchToFrame(toKatalan(to), timeout);
    }
    
    public static void switchToFrame(TestObject to, int timeout, FailureHandling flowControl) {
        switchToFrame(to, timeout);
    }
    
    public static void switchToDefaultContent() {
        WebUI.switchToDefaultContent();
    }
    
    public static void switchToDefaultContent(FailureHandling flowControl) {
        switchToDefaultContent();
    }
    
    public static void switchToWindowTitle(String title) {
        WebUI.switchToWindowTitle(title);
    }
    
    public static void switchToWindowTitle(String title, FailureHandling flowControl) {
        switchToWindowTitle(title);
    }
    
    public static void switchToWindowIndex(int index) {
        WebUI.switchToWindowIndex(index);
    }
    
    public static void switchToWindowIndex(int index, FailureHandling flowControl) {
        switchToWindowIndex(index);
    }
    
    public static void switchToWindowUrl(String url) {
        WebUI.switchToWindowUrl(url);
    }
    
    public static void switchToWindowUrl(String url, FailureHandling flowControl) {
        switchToWindowUrl(url);
    }
    
    public static void closeWindowTitle(String title) {
        logNotSupported("closeWindowTitle");
    }
    
    public static void closeWindowTitle(String title, FailureHandling flowControl) {
        closeWindowTitle(title);
    }
    
    public static void closeWindowIndex(int index) {
        WebUI.closeWindowIndex(index);
    }
    
    public static void closeWindowIndex(int index, FailureHandling flowControl) {
        closeWindowIndex(index);
    }
    
    public static void closeWindowUrl(String url) {
        logNotSupported("closeWindowUrl");
    }
    
    public static void closeWindowUrl(String url, FailureHandling flowControl) {
        closeWindowUrl(url);
    }
    
    public static int getWindowIndex() {
        logNotSupported("getWindowIndex");
        return 0;
    }
    
    public static String getWindowTitle() {
        return WebUI.getWindowTitle();
    }
    
    // ==================== Scroll Keywords ====================
    
    public static void scrollToElement(TestObject to, int timeout) {
        WebUI.scrollToElement(toKatalan(to), timeout);
    }
    
    public static void scrollToElement(TestObject to, int timeout, FailureHandling flowControl) {
        scrollToElement(to, timeout);
    }
    
    public static void scrollToPosition(int x, int y) {
        WebUI.scrollToPosition(x, y);
    }
    
    public static void scrollToPosition(int x, int y, FailureHandling flowControl) {
        scrollToPosition(x, y);
    }
    
    // ==================== Screenshot Keywords ====================
    
    public static String takeScreenshot() {
        return WebUI.takeScreenshot();
    }
    
    public static String takeScreenshot(FailureHandling flowControl) {
        return takeScreenshot();
    }
    
    public static String takeScreenshot(String fileName) {
        return WebUI.takeScreenshot(fileName);
    }
    
    public static String takeScreenshot(String fileName, FailureHandling flowControl) {
        return takeScreenshot(fileName);
    }
    
    public static String takeElementScreenshot(TestObject to) {
        return WebUI.takeElementScreenshot(toKatalan(to), "element_screenshot.png");
    }
    
    public static String takeElementScreenshot(TestObject to, String fileName) {
        return WebUI.takeElementScreenshot(toKatalan(to), fileName);
    }
    
    public static String takeElementScreenshot(TestObject to, String fileName, FailureHandling flowControl) {
        return takeElementScreenshot(to, fileName);
    }
    
    public static String takeFullPageScreenshot() {
        return takeScreenshot();
    }
    
    public static String takeFullPageScreenshot(String fileName) {
        return takeScreenshot(fileName);
    }
    
    public static String takeFullPageScreenshot(String fileName, FailureHandling flowControl) {
        return takeFullPageScreenshot(fileName);
    }
    
    // ==================== Drag/Drop Keywords ====================
    
    public static void dragAndDropToObject(TestObject source, TestObject target) {
        logNotSupported("dragAndDropToObject");
    }
    
    public static void dragAndDropToObject(TestObject source, TestObject target, FailureHandling flowControl) {
        dragAndDropToObject(source, target);
    }
    
    public static void dragAndDropByOffset(TestObject source, int offsetX, int offsetY) {
        logNotSupported("dragAndDropByOffset");
    }
    
    public static void dragAndDropByOffset(TestObject source, int offsetX, int offsetY, FailureHandling flowControl) {
        dragAndDropByOffset(source, offsetX, offsetY);
    }
    
    // ==================== Mouse Keywords ====================
    
    public static void mouseOver(TestObject to) {
        WebUI.mouseOver(toKatalan(to));
    }
    
    public static void mouseOver(TestObject to, FailureHandling flowControl) {
        mouseOver(to);
    }
    
    public static void mouseOverOffset(TestObject to, int offsetX, int offsetY) {
        logNotSupported("mouseOverOffset");
        mouseOver(to);
    }
    
    public static void mouseOverOffset(TestObject to, int offsetX, int offsetY, FailureHandling flowControl) {
        mouseOverOffset(to, offsetX, offsetY);
    }
    
    // ==================== JavaScript Keywords ====================
    
    public static Object executeJavaScript(String script, Object... args) {
        return WebUI.executeJavaScript(script, args);
    }
    
    public static Object executeJavaScript(String script, FailureHandling flowControl, Object... args) {
        return executeJavaScript(script, args);
    }
    
    // ==================== Focus Keywords ====================
    
    public static void focus(TestObject to) {
        WebUI.focus(toKatalan(to));
    }
    
    public static void focus(TestObject to, FailureHandling flowControl) {
        focus(to);
    }
    
    // ==================== Upload Keywords ====================
    
    public static void uploadFile(TestObject to, String filePath) {
        WebUI.uploadFile(toKatalan(to), filePath);
    }
    
    public static void uploadFile(TestObject to, String filePath, FailureHandling flowControl) {
        uploadFile(to, filePath);
    }
    
    public static void uploadFileWithDragAndDrop(TestObject to, String filePath) {
        logNotSupported("uploadFileWithDragAndDrop");
        uploadFile(to, filePath);
    }
    
    public static void uploadFileWithDragAndDrop(TestObject to, String filePath, FailureHandling flowControl) {
        uploadFileWithDragAndDrop(to, filePath);
    }
    
    // ==================== Comment/Log Keywords ====================
    
    public static void comment(String message) {
        WebUI.comment(message);
    }
    
    // ==================== Submit Keywords ====================
    
    public static void submit(TestObject to) {
        logNotSupported("submit");
        WebUI.click(toKatalan(to));
    }
    
    public static void submit(TestObject to, FailureHandling flowControl) {
        submit(to);
    }
    
    // ==================== Call Test Case ====================
    
    public static Object callTestCase(com.katalan.core.compat.TestCase testCase, java.util.Map<String, Object> binding) {
        WebUI.callTestCase(testCase, binding);
        return null;
    }
    
    public static Object callTestCase(com.katalan.core.compat.TestCase testCase, java.util.Map<String, Object> binding, FailureHandling flowControl) {
        return callTestCase(testCase, binding);
    }
    
    // ==================== Find Element Methods ====================
    
    public static WebElement findWebElement(TestObject to) {
        logNotSupported("findWebElement");
        return null;
    }
    
    public static WebElement findWebElement(TestObject to, int timeout) {
        return findWebElement(to);
    }
    
    public static List<WebElement> findWebElements(TestObject to, int timeout) {
        logNotSupported("findWebElements");
        return Collections.emptyList();
    }
}
