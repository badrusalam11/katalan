package com.kms.katalon.core.mobile.keyword;

import com.katalan.keywords.Mobile;
import com.katalan.core.model.TestObject;
import com.kms.katalon.core.model.FailureHandling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Katalon compatibility layer for MobileBuiltInKeywords.
 *
 * Every method here delegates the actual work to {@link com.katalan.keywords.Mobile} (the
 * real Appium-backed engine) and is solely responsible for FailureHandling semantics -
 * exactly the same split used by {@code WebUiBuiltInKeywords}/{@code com.katalan.keywords.WebUI}.
 *
 * Default FailureHandling when omitted follows Katalon convention: verify/action keywords
 * default to STOP_ON_FAILURE, wait* keywords default to CONTINUE_ON_FAILURE (they're commonly
 * used as conditionals, e.g. {@code if (Mobile.waitForElementPresent(to, 5)) ...}).
 */
public class MobileBuiltInKeywords {

    private static final Logger logger = LoggerFactory.getLogger(MobileBuiltInKeywords.class);
    private static final int DEFAULT_TIMEOUT = 30;

    // ==================== FailureHandling helpers ====================

    @FunctionalInterface private interface VoidOp { void run() throws Throwable; }
    @FunctionalInterface private interface BoolOp { boolean run() throws Throwable; }
    @FunctionalInterface private interface ObjOp<T> { T run() throws Throwable; }

    private static boolean isLenient(FailureHandling fh) {
        return fh == FailureHandling.OPTIONAL || fh == FailureHandling.CONTINUE_ON_FAILURE;
    }

    private static void runVoid(VoidOp op, FailureHandling fh) {
        runVoid(op, fh, FailureHandling.STOP_ON_FAILURE);
    }

    private static void runVoid(VoidOp op, FailureHandling fh, FailureHandling defaultMode) {
        FailureHandling mode = fh != null ? fh : defaultMode;
        try {
            op.run();
        } catch (Throwable t) {
            if (isLenient(mode)) {
                logger.warn("Mobile keyword failed (flowControl={}): {}", mode, t.getMessage());
                return;
            }
            if (t instanceof RuntimeException) throw (RuntimeException) t;
            if (t instanceof Error) throw (Error) t;
            throw new RuntimeException(t);
        }
    }

    private static boolean runBool(BoolOp op, FailureHandling fh) {
        return runBool(op, fh, FailureHandling.STOP_ON_FAILURE);
    }

    private static boolean runBool(BoolOp op, FailureHandling fh, FailureHandling defaultMode) {
        FailureHandling mode = fh != null ? fh : defaultMode;
        try {
            return op.run();
        } catch (Throwable t) {
            if (isLenient(mode)) {
                logger.warn("Mobile keyword failed (flowControl={}): {}", mode, t.getMessage());
                return false;
            }
            if (t instanceof RuntimeException) throw (RuntimeException) t;
            if (t instanceof Error) throw (Error) t;
            throw new RuntimeException(t);
        }
    }

    private static <T> T runObj(ObjOp<T> op, FailureHandling fh, T fallback) {
        FailureHandling mode = fh != null ? fh : FailureHandling.STOP_ON_FAILURE;
        try {
            return op.run();
        } catch (Throwable t) {
            if (isLenient(mode)) {
                logger.warn("Mobile keyword failed (flowControl={}): {}", mode, t.getMessage());
                return fallback;
            }
            if (t instanceof RuntimeException) throw (RuntimeException) t;
            if (t instanceof Error) throw (Error) t;
            throw new RuntimeException(t);
        }
    }

    // ==================== Application Management ====================

    public static void startApplication(String appFile, boolean isRestartApp) {
        Mobile.startApplication(appFile, isRestartApp);
    }

    public static void startApplication(String appFile, boolean isRestartApp, FailureHandling fh) {
        runVoid(() -> startApplication(appFile, isRestartApp), fh);
    }

    public static void startExistingApplication(String appId) {
        Mobile.startExistingApplication(appId);
    }

    public static void startExistingApplication(String appId, FailureHandling fh) {
        runVoid(() -> startExistingApplication(appId), fh);
    }

    public static void closeApplication() {
        Mobile.closeApplication();
    }

    public static void closeApplication(FailureHandling fh) {
        runVoid(Mobile::closeApplication, fh);
    }

    public static void installApp(String appFile) {
        Mobile.installApp(appFile);
    }

    public static void installApp(String appFile, FailureHandling fh) {
        runVoid(() -> installApp(appFile), fh);
    }

    public static void removeApp(String appId) {
        Mobile.removeApp(appId);
    }

    public static void removeApp(String appId, FailureHandling fh) {
        runVoid(() -> removeApp(appId), fh);
    }

    public static boolean isAppInstalled(String appId) {
        return Mobile.isAppInstalled(appId);
    }

    public static boolean isAppInstalled(String appId, FailureHandling fh) {
        return runBool(() -> isAppInstalled(appId), fh);
    }

    public static void resetApp() {
        Mobile.resetApp();
    }

    public static void resetApp(FailureHandling fh) {
        runVoid(Mobile::resetApp, fh);
    }

    public static void backgroundApp(int seconds) {
        Mobile.backgroundApp(seconds);
    }

    public static void backgroundApp(int seconds, FailureHandling fh) {
        runVoid(() -> backgroundApp(seconds), fh);
    }

    public static String getCurrentActivity() {
        return Mobile.getCurrentActivity();
    }

    public static String getCurrentActivity(FailureHandling fh) {
        return runObj(Mobile::getCurrentActivity, fh, "");
    }

    public static String getCurrentPackage() {
        return Mobile.getCurrentPackage();
    }

    public static String getCurrentPackage(FailureHandling fh) {
        return runObj(Mobile::getCurrentPackage, fh, "");
    }

    // ==================== Device Info ====================

    public static String getDeviceId() {
        return Mobile.getDeviceId();
    }

    public static String getDeviceId(FailureHandling fh) {
        return runObj(Mobile::getDeviceId, fh, "");
    }

    public static String getDeviceName() {
        return Mobile.getDeviceName();
    }

    public static String getDeviceName(FailureHandling fh) {
        return runObj(Mobile::getDeviceName, fh, "");
    }

    public static String getDeviceManufacturer() {
        return Mobile.getDeviceManufacturer();
    }

    public static String getDeviceManufacturer(FailureHandling fh) {
        return runObj(Mobile::getDeviceManufacturer, fh, "");
    }

    public static String getDeviceModel() {
        return Mobile.getDeviceModel();
    }

    public static String getDeviceModel(FailureHandling fh) {
        return runObj(Mobile::getDeviceModel, fh, "");
    }

    public static String getDeviceOS() {
        return Mobile.getDeviceOS();
    }

    public static String getDeviceOS(FailureHandling fh) {
        return runObj(Mobile::getDeviceOS, fh, "");
    }

    public static String getDeviceOSVersion() {
        return Mobile.getDeviceOSVersion();
    }

    public static String getDeviceOSVersion(FailureHandling fh) {
        return runObj(Mobile::getDeviceOSVersion, fh, "");
    }

    public static String getDeviceOrientation() {
        return Mobile.getDeviceOrientation();
    }

    public static String getDeviceOrientation(FailureHandling fh) {
        return runObj(Mobile::getDeviceOrientation, fh, "");
    }

    public static void setDeviceOrientation(String orientation) {
        Mobile.setDeviceOrientation(orientation);
    }

    public static void setDeviceOrientation(String orientation, FailureHandling fh) {
        runVoid(() -> setDeviceOrientation(orientation), fh);
    }

    // ==================== Element Interaction ====================

    public static void tap(TestObject to) {
        tap(to, DEFAULT_TIMEOUT);
    }

    public static void tap(TestObject to, FailureHandling fh) {
        runVoid(() -> tap(to, DEFAULT_TIMEOUT), fh);
    }

    public static void tap(TestObject to, int timeout) {
        Mobile.tap(to, timeout);
    }

    public static void tap(TestObject to, int timeout, FailureHandling fh) {
        runVoid(() -> tap(to, timeout), fh);
    }

    public static void doubleTap(TestObject to) {
        doubleTap(to, DEFAULT_TIMEOUT);
    }

    public static void doubleTap(TestObject to, FailureHandling fh) {
        runVoid(() -> doubleTap(to, DEFAULT_TIMEOUT), fh);
    }

    public static void doubleTap(TestObject to, int timeout) {
        Mobile.doubleTap(to, timeout);
    }

    public static void doubleTap(TestObject to, int timeout, FailureHandling fh) {
        runVoid(() -> doubleTap(to, timeout), fh);
    }

    public static void longPress(TestObject to, int durationSeconds) {
        longPress(to, durationSeconds, DEFAULT_TIMEOUT);
    }

    public static void longPress(TestObject to, int durationSeconds, FailureHandling fh) {
        runVoid(() -> longPress(to, durationSeconds, DEFAULT_TIMEOUT), fh);
    }

    public static void longPress(TestObject to, int durationSeconds, int timeout) {
        Mobile.longPress(to, durationSeconds, timeout);
    }

    public static void longPress(TestObject to, int durationSeconds, int timeout, FailureHandling fh) {
        runVoid(() -> longPress(to, durationSeconds, timeout), fh);
    }

    public static void tapAtPosition(int x, int y) {
        Mobile.tapAtPosition(x, y);
    }

    public static void tapAtPosition(int x, int y, FailureHandling fh) {
        runVoid(() -> tapAtPosition(x, y), fh);
    }

    public static void setText(TestObject to, String text) {
        setText(to, text, DEFAULT_TIMEOUT);
    }

    public static void setText(TestObject to, String text, FailureHandling fh) {
        runVoid(() -> setText(to, text, DEFAULT_TIMEOUT), fh);
    }

    public static void setText(TestObject to, String text, int timeout) {
        Mobile.setText(to, text, timeout);
    }

    public static void setText(TestObject to, String text, int timeout, FailureHandling fh) {
        runVoid(() -> setText(to, text, timeout), fh);
    }

    public static void setEncryptedText(TestObject to, String encryptedText) {
        setEncryptedText(to, encryptedText, DEFAULT_TIMEOUT);
    }

    public static void setEncryptedText(TestObject to, String encryptedText, FailureHandling fh) {
        runVoid(() -> setEncryptedText(to, encryptedText, DEFAULT_TIMEOUT), fh);
    }

    public static void setEncryptedText(TestObject to, String encryptedText, int timeout) {
        Mobile.setEncryptedText(to, encryptedText, timeout);
    }

    public static void setEncryptedText(TestObject to, String encryptedText, int timeout, FailureHandling fh) {
        runVoid(() -> setEncryptedText(to, encryptedText, timeout), fh);
    }

    public static void clearText(TestObject to) {
        clearText(to, DEFAULT_TIMEOUT);
    }

    public static void clearText(TestObject to, FailureHandling fh) {
        runVoid(() -> clearText(to, DEFAULT_TIMEOUT), fh);
    }

    public static void clearText(TestObject to, int timeout) {
        Mobile.clearText(to, timeout);
    }

    public static void clearText(TestObject to, int timeout, FailureHandling fh) {
        runVoid(() -> clearText(to, timeout), fh);
    }

    public static String getText(TestObject to) {
        return getText(to, DEFAULT_TIMEOUT);
    }

    public static String getText(TestObject to, FailureHandling fh) {
        return runObj(() -> getText(to, DEFAULT_TIMEOUT), fh, null);
    }

    public static String getText(TestObject to, int timeout) {
        return Mobile.getText(to, timeout);
    }

    public static String getText(TestObject to, int timeout, FailureHandling fh) {
        return runObj(() -> getText(to, timeout), fh, null);
    }

    public static String getAttribute(TestObject to, String attribute) {
        return getAttribute(to, attribute, DEFAULT_TIMEOUT);
    }

    public static String getAttribute(TestObject to, String attribute, FailureHandling fh) {
        return runObj(() -> getAttribute(to, attribute, DEFAULT_TIMEOUT), fh, null);
    }

    public static String getAttribute(TestObject to, String attribute, int timeout) {
        return Mobile.getAttribute(to, attribute, timeout);
    }

    public static String getAttribute(TestObject to, String attribute, int timeout, FailureHandling fh) {
        return runObj(() -> getAttribute(to, attribute, timeout), fh, null);
    }

    public static int getElementWidth(TestObject to) {
        return getElementWidth(to, DEFAULT_TIMEOUT);
    }

    public static int getElementWidth(TestObject to, FailureHandling fh) {
        return runObj(() -> getElementWidth(to, DEFAULT_TIMEOUT), fh, 0);
    }

    public static int getElementWidth(TestObject to, int timeout) {
        return Mobile.getElementWidth(to, timeout);
    }

    public static int getElementWidth(TestObject to, int timeout, FailureHandling fh) {
        return runObj(() -> getElementWidth(to, timeout), fh, 0);
    }

    public static int getElementHeight(TestObject to) {
        return getElementHeight(to, DEFAULT_TIMEOUT);
    }

    public static int getElementHeight(TestObject to, FailureHandling fh) {
        return runObj(() -> getElementHeight(to, DEFAULT_TIMEOUT), fh, 0);
    }

    public static int getElementHeight(TestObject to, int timeout) {
        return Mobile.getElementHeight(to, timeout);
    }

    public static int getElementHeight(TestObject to, int timeout, FailureHandling fh) {
        return runObj(() -> getElementHeight(to, timeout), fh, 0);
    }

    public static int getElementTopPosition(TestObject to) {
        return getElementTopPosition(to, DEFAULT_TIMEOUT);
    }

    public static int getElementTopPosition(TestObject to, FailureHandling fh) {
        return runObj(() -> getElementTopPosition(to, DEFAULT_TIMEOUT), fh, 0);
    }

    public static int getElementTopPosition(TestObject to, int timeout) {
        return Mobile.getElementTopPosition(to, timeout);
    }

    public static int getElementTopPosition(TestObject to, int timeout, FailureHandling fh) {
        return runObj(() -> getElementTopPosition(to, timeout), fh, 0);
    }

    public static int getElementLeftPosition(TestObject to) {
        return getElementLeftPosition(to, DEFAULT_TIMEOUT);
    }

    public static int getElementLeftPosition(TestObject to, FailureHandling fh) {
        return runObj(() -> getElementLeftPosition(to, DEFAULT_TIMEOUT), fh, 0);
    }

    public static int getElementLeftPosition(TestObject to, int timeout) {
        return Mobile.getElementLeftPosition(to, timeout);
    }

    public static int getElementLeftPosition(TestObject to, int timeout, FailureHandling fh) {
        return runObj(() -> getElementLeftPosition(to, timeout), fh, 0);
    }

    public static void swipe(int startX, int startY, int endX, int endY) {
        Mobile.swipe(startX, startY, endX, endY);
    }

    public static void swipe(int startX, int startY, int endX, int endY, FailureHandling fh) {
        runVoid(() -> swipe(startX, startY, endX, endY), fh);
    }

    public static void swipe(int startX, int startY, int endX, int endY, int durationMs) {
        Mobile.swipe(startX, startY, endX, endY, durationMs);
    }

    public static void swipe(int startX, int startY, int endX, int endY, int durationMs, FailureHandling fh) {
        runVoid(() -> swipe(startX, startY, endX, endY, durationMs), fh);
    }

    public static void scrollToText(String text) {
        Mobile.scrollToText(text);
    }

    public static void scrollToText(String text, FailureHandling fh) {
        runVoid(() -> scrollToText(text), fh);
    }

    public static void scrollToElement(TestObject to, int timeout) {
        Mobile.scrollToElement(to, timeout);
    }

    public static void scrollToElement(TestObject to, int timeout, FailureHandling fh) {
        runVoid(() -> scrollToElement(to, timeout), fh);
    }

    // ==================== Verification ====================

    public static boolean verifyElementExist(TestObject to, int timeout) {
        return Mobile.verifyElementExist(to, timeout);
    }

    public static boolean verifyElementExist(TestObject to, int timeout, FailureHandling fh) {
        return runBool(() -> verifyElementExist(to, timeout), fh);
    }

    public static boolean verifyElementNotExist(TestObject to, int timeout) {
        return Mobile.verifyElementNotExist(to, timeout);
    }

    public static boolean verifyElementNotExist(TestObject to, int timeout, FailureHandling fh) {
        return runBool(() -> verifyElementNotExist(to, timeout), fh);
    }

    public static boolean verifyElementVisible(TestObject to) {
        return verifyElementVisible(to, DEFAULT_TIMEOUT);
    }

    public static boolean verifyElementVisible(TestObject to, FailureHandling fh) {
        return runBool(() -> verifyElementVisible(to, DEFAULT_TIMEOUT), fh);
    }

    public static boolean verifyElementVisible(TestObject to, int timeout) {
        return Mobile.verifyElementVisible(to, timeout);
    }

    public static boolean verifyElementVisible(TestObject to, int timeout, FailureHandling fh) {
        return runBool(() -> verifyElementVisible(to, timeout), fh);
    }

    public static boolean verifyElementNotVisible(TestObject to) {
        return verifyElementNotVisible(to, DEFAULT_TIMEOUT);
    }

    public static boolean verifyElementNotVisible(TestObject to, FailureHandling fh) {
        return runBool(() -> verifyElementNotVisible(to, DEFAULT_TIMEOUT), fh);
    }

    public static boolean verifyElementNotVisible(TestObject to, int timeout) {
        return Mobile.verifyElementNotVisible(to, timeout);
    }

    public static boolean verifyElementNotVisible(TestObject to, int timeout, FailureHandling fh) {
        return runBool(() -> verifyElementNotVisible(to, timeout), fh);
    }

    public static boolean verifyElementText(TestObject to, String expectedText) {
        return verifyElementText(to, expectedText, DEFAULT_TIMEOUT);
    }

    public static boolean verifyElementText(TestObject to, String expectedText, FailureHandling fh) {
        return runBool(() -> verifyElementText(to, expectedText, DEFAULT_TIMEOUT), fh);
    }

    public static boolean verifyElementText(TestObject to, String expectedText, int timeout) {
        return Mobile.verifyElementText(to, expectedText, timeout);
    }

    public static boolean verifyElementText(TestObject to, String expectedText, int timeout, FailureHandling fh) {
        return runBool(() -> verifyElementText(to, expectedText, timeout), fh);
    }

    public static boolean verifyElementContainsText(TestObject to, String expectedText) {
        return verifyElementContainsText(to, expectedText, DEFAULT_TIMEOUT);
    }

    public static boolean verifyElementContainsText(TestObject to, String expectedText, FailureHandling fh) {
        return runBool(() -> verifyElementContainsText(to, expectedText, DEFAULT_TIMEOUT), fh);
    }

    public static boolean verifyElementContainsText(TestObject to, String expectedText, int timeout) {
        return Mobile.verifyElementContainsText(to, expectedText, timeout);
    }

    public static boolean verifyElementContainsText(TestObject to, String expectedText, int timeout, FailureHandling fh) {
        return runBool(() -> verifyElementContainsText(to, expectedText, timeout), fh);
    }

    public static boolean verifyElementAttributeValue(TestObject to, String attribute, String value, int timeout) {
        return Mobile.verifyElementAttributeValue(to, attribute, value, timeout);
    }

    public static boolean verifyElementAttributeValue(TestObject to, String attribute, String value, int timeout, FailureHandling fh) {
        return runBool(() -> verifyElementAttributeValue(to, attribute, value, timeout), fh);
    }

    public static boolean verifyElementChecked(TestObject to, int timeout) {
        return Mobile.verifyElementChecked(to, timeout);
    }

    public static boolean verifyElementChecked(TestObject to, int timeout, FailureHandling fh) {
        return runBool(() -> verifyElementChecked(to, timeout), fh);
    }

    public static boolean verifyElementNotChecked(TestObject to, int timeout) {
        return Mobile.verifyElementNotChecked(to, timeout);
    }

    public static boolean verifyElementNotChecked(TestObject to, int timeout, FailureHandling fh) {
        return runBool(() -> verifyElementNotChecked(to, timeout), fh);
    }

    public static boolean verifyEqual(Object actual, Object expected) {
        return Mobile.verifyEqual(actual, expected);
    }

    public static boolean verifyEqual(Object actual, Object expected, FailureHandling fh) {
        return runBool(() -> verifyEqual(actual, expected), fh);
    }

    public static boolean verifyMatch(String actualText, String expectedText, boolean isRegex) {
        return Mobile.verifyMatch(actualText, expectedText, isRegex);
    }

    public static boolean verifyMatch(String actualText, String expectedText, boolean isRegex, FailureHandling fh) {
        return runBool(() -> verifyMatch(actualText, expectedText, isRegex), fh);
    }

    // ==================== Wait Keywords ====================
    // Default to CONTINUE_ON_FAILURE when no FailureHandling is given - Katalon's wait*
    // keywords are commonly used as conditionals (if (Mobile.waitForElementPresent(...)))
    // rather than hard assertions.

    public static boolean waitForElementPresent(TestObject to, int timeout) {
        return runBool(() -> Mobile.waitForElementPresent(to, timeout), null, FailureHandling.CONTINUE_ON_FAILURE);
    }

    public static boolean waitForElementPresent(TestObject to, int timeout, FailureHandling fh) {
        return runBool(() -> Mobile.waitForElementPresent(to, timeout), fh, FailureHandling.CONTINUE_ON_FAILURE);
    }

    public static boolean waitForElementNotPresent(TestObject to, int timeout) {
        return runBool(() -> Mobile.waitForElementNotPresent(to, timeout), null, FailureHandling.CONTINUE_ON_FAILURE);
    }

    public static boolean waitForElementNotPresent(TestObject to, int timeout, FailureHandling fh) {
        return runBool(() -> Mobile.waitForElementNotPresent(to, timeout), fh, FailureHandling.CONTINUE_ON_FAILURE);
    }

    public static boolean waitForElementVisible(TestObject to, int timeout) {
        return runBool(() -> Mobile.waitForElementVisible(to, timeout), null, FailureHandling.CONTINUE_ON_FAILURE);
    }

    public static boolean waitForElementVisible(TestObject to, int timeout, FailureHandling fh) {
        return runBool(() -> Mobile.waitForElementVisible(to, timeout), fh, FailureHandling.CONTINUE_ON_FAILURE);
    }

    public static boolean waitForElementNotVisible(TestObject to, int timeout) {
        return runBool(() -> Mobile.waitForElementNotVisible(to, timeout), null, FailureHandling.CONTINUE_ON_FAILURE);
    }

    public static boolean waitForElementNotVisible(TestObject to, int timeout, FailureHandling fh) {
        return runBool(() -> Mobile.waitForElementNotVisible(to, timeout), fh, FailureHandling.CONTINUE_ON_FAILURE);
    }

    public static void delay(double seconds) {
        Mobile.delay(seconds);
    }

    public static void delay(double seconds, FailureHandling fh) {
        runVoid(() -> delay(seconds), fh);
    }

    // ==================== Navigation / Hardware Keys ====================

    public static void pressBack() {
        Mobile.pressBack();
    }

    public static void pressBack(FailureHandling fh) {
        runVoid(Mobile::pressBack, fh);
    }

    public static void pressHome() {
        Mobile.pressHome();
    }

    public static void pressHome(FailureHandling fh) {
        runVoid(Mobile::pressHome, fh);
    }

    public static void hideKeyboard() {
        Mobile.hideKeyboard();
    }

    public static void hideKeyboard(FailureHandling fh) {
        runVoid(Mobile::hideKeyboard, fh);
    }

    public static void sendKeyEvent(int keyCode) {
        Mobile.sendKeyEvent(keyCode);
    }

    public static void sendKeyEvent(int keyCode, FailureHandling fh) {
        runVoid(() -> sendKeyEvent(keyCode), fh);
    }

    // ==================== Notifications / Network ====================

    public static void openNotifications() {
        Mobile.openNotifications();
    }

    public static void openNotifications(FailureHandling fh) {
        runVoid(Mobile::openNotifications, fh);
    }

    public static void closeNotifications() {
        Mobile.closeNotifications();
    }

    public static void closeNotifications(FailureHandling fh) {
        runVoid(Mobile::closeNotifications, fh);
    }

    public static void toggleWifi(String state) {
        Mobile.toggleWifi(state);
    }

    public static void toggleWifi(String state, FailureHandling fh) {
        runVoid(() -> toggleWifi(state), fh);
    }

    public static void toggleWifi(boolean enable) {
        Mobile.toggleWifi(enable);
    }

    public static void toggleWifi(boolean enable, FailureHandling fh) {
        runVoid(() -> toggleWifi(enable), fh);
    }

    public static void toggleData(String state) {
        Mobile.toggleData(state);
    }

    public static void toggleData(String state, FailureHandling fh) {
        runVoid(() -> toggleData(state), fh);
    }

    public static void toggleData(boolean enable) {
        Mobile.toggleData(enable);
    }

    public static void toggleData(boolean enable, FailureHandling fh) {
        runVoid(() -> toggleData(enable), fh);
    }

    // ==================== Lock / Misc Device ====================

    public static void lockDevice() {
        Mobile.lockDevice();
    }

    public static void lockDevice(FailureHandling fh) {
        runVoid(Mobile::lockDevice, fh);
    }

    public static void unlockDevice() {
        Mobile.unlockDevice();
    }

    public static void unlockDevice(FailureHandling fh) {
        runVoid(Mobile::unlockDevice, fh);
    }

    public static boolean isDeviceLocked() {
        return Mobile.isDeviceLocked();
    }

    public static boolean isDeviceLocked(FailureHandling fh) {
        return runBool(Mobile::isDeviceLocked, fh);
    }

    public static Object executeMobileCommand(String command, Map<String, Object> args) {
        return Mobile.executeMobileCommand(command, args);
    }

    public static Object executeMobileCommand(String command, Map<String, Object> args, FailureHandling fh) {
        return runObj(() -> executeMobileCommand(command, args), fh, null);
    }

    // ==================== Screenshot ====================

    public static String takeScreenshot() {
        return Mobile.takeScreenshot();
    }

    public static String takeScreenshot(FailureHandling fh) {
        return runObj(Mobile::takeScreenshot, fh, null);
    }

    public static String takeScreenshot(String fileName) {
        return Mobile.takeScreenshot(fileName);
    }

    public static String takeScreenshot(String fileName, FailureHandling fh) {
        return runObj(() -> takeScreenshot(fileName), fh, null);
    }

    // ==================== Utility Keywords ====================

    public static void comment(String message) {
        Mobile.comment(message);
    }

    public static Object callTestCase(Object testCase, Map<String, Object> binding) {
        Mobile.callTestCase(testCase, binding);
        return null;
    }

    public static Object callTestCase(Object testCase, Map<String, Object> binding, FailureHandling fh) {
        return runObj(() -> callTestCase(testCase, binding), fh, null);
    }
}
