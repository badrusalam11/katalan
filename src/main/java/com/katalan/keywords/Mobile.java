package com.katalan.keywords;

import com.katalan.core.context.ExecutionContext;
import com.katalan.core.driver.MobileDriverFactory;
import com.katalan.core.model.TestObject;
import com.kms.katalon.core.testobject.ConditionType;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.HidesKeyboard;
import io.appium.java_client.InteractsWithApps;
import io.appium.java_client.LocksDevice;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.connection.ConnectionState;
import io.appium.java_client.android.nativekey.AndroidKey;
import io.appium.java_client.android.nativekey.KeyEvent;
import io.appium.java_client.ios.IOSDriver;

import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Pause;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Mobile Keywords - real Appium-backed engine, compatible with Katalon's Mobile built-in
 * keywords.
 *
 * Mirrors {@link WebUI}'s role for the Web keyword set: every method here performs the
 * actual Appium interaction and throws on failure. FailureHandling (STOP_ON_FAILURE /
 * CONTINUE_ON_FAILURE / OPTIONAL) is applied one layer up, by the Katalon-compatibility
 * shim {@code com.kms.katalon.core.mobile.keyword.MobileBuiltInKeywords}.
 *
 * Usage in Groovy scripts (via the compat layer):
 *   Mobile.startApplication('path/to/app.apk', false)
 *   Mobile.tap(findTestObject('Page/button'), 5)
 *   Mobile.setText(findTestObject('Page/input'), 'text', 5)
 */
public class Mobile {

    private static final Logger logger = LoggerFactory.getLogger(Mobile.class);

    private Mobile() {
    }

    // ==================== Application Management ====================

    public static void startApplication(String appFile, boolean isRestartApp) {
        ExecutionContext context = ExecutionContext.getCurrent();
        AppiumDriver existing = context.getMobileDriver();
        if (existing != null && !isRestartApp) {
            logger.info("📱 Mobile session already active - reusing it (isRestartApp=false)");
            return;
        }
        if (existing != null) {
            logger.info("🔒 Closing existing mobile session before starting a fresh one (isRestartApp=true)");
            try {
                existing.quit();
            } catch (Exception e) {
                logger.warn("Failed to close previous mobile session: {}", e.getMessage());
            }
            context.setMobileDriver(null);
        }

        context.getRunConfiguration().setMobileAppFile(appFile);
        AppiumDriver driver = MobileDriverFactory.createDriver(context.getRunConfiguration());
        context.setMobileDriver(driver);
        logger.info("✅ Application started: {}", appFile);
    }

    /**
     * Start (or bring to foreground) an already-installed application by its package /
     * bundle id. If no session exists yet, a new one is created targeting that app;
     * if one is already running, the app is simply activated.
     */
    public static void startExistingApplication(String appId) {
        ExecutionContext context = ExecutionContext.getCurrent();
        AppiumDriver driver = context.getMobileDriver();
        if (driver == null) {
            context.getRunConfiguration().setMobileAppPackage(appId);
            context.getRunConfiguration().setMobileAppFile(null);
            driver = MobileDriverFactory.createDriver(context.getRunConfiguration());
            context.setMobileDriver(driver);
            logger.info("✅ Mobile session started for existing application: {}", appId);
            return;
        }
        if (driver instanceof InteractsWithApps) {
            ((InteractsWithApps) driver).activateApp(appId);
            logger.info("✅ Application activated: {}", appId);
        }
    }

    public static void closeApplication() {
        ExecutionContext context = ExecutionContext.getCurrent();
        AppiumDriver driver = context.getMobileDriver();
        if (driver != null) {
            try {
                driver.quit();
            } finally {
                context.setMobileDriver(null);
            }
        }
        logger.info("🔒 Mobile application/session closed");
    }

    public static void installApp(String appFile) {
        AppiumDriver driver = getDriver();
        if (driver instanceof InteractsWithApps) {
            ((InteractsWithApps) driver).installApp(resolveAppPath(appFile));
            logger.info("✅ App installed: {}", appFile);
        }
    }

    public static void removeApp(String appId) {
        AppiumDriver driver = getDriver();
        if (driver instanceof InteractsWithApps) {
            ((InteractsWithApps) driver).removeApp(appId);
            logger.info("✅ App removed: {}", appId);
        }
    }

    public static boolean isAppInstalled(String appId) {
        AppiumDriver driver = getDriver();
        if (driver instanceof InteractsWithApps) {
            return ((InteractsWithApps) driver).isAppInstalled(appId);
        }
        return false;
    }

    public static void resetApp() {
        ExecutionContext context = ExecutionContext.getCurrent();
        String appId = context.getRunConfiguration().getMobileAppPackage();
        AppiumDriver driver = getDriver();
        if (appId != null && driver instanceof InteractsWithApps) {
            ((InteractsWithApps) driver).terminateApp(appId);
            ((InteractsWithApps) driver).activateApp(appId);
        }
        logger.info("🔄 Application reset");
    }

    public static void backgroundApp(int seconds) {
        AppiumDriver driver = getDriver();
        if (driver instanceof InteractsWithApps) {
            ((InteractsWithApps) driver).runAppInBackground(Duration.ofSeconds(seconds));
            logger.info("⏸ App moved to background for {}s", seconds);
        }
    }

    public static String getCurrentActivity() {
        AppiumDriver driver = getDriver();
        if (driver instanceof AndroidDriver) {
            return ((AndroidDriver) driver).currentActivity();
        }
        return "";
    }

    public static String getCurrentPackage() {
        AppiumDriver driver = getDriver();
        if (driver instanceof AndroidDriver) {
            return ((AndroidDriver) driver).getCurrentPackage();
        }
        return "";
    }

    // ==================== Device Info ====================

    public static String getDeviceId() {
        AppiumDriver driver = getDriver();
        Object udid = driver.getCapabilities().getCapability("udid");
        return udid != null ? udid.toString() : "";
    }

    public static String getDeviceName() {
        AppiumDriver driver = getDriver();
        Object name = driver.getCapabilities().getCapability("deviceName");
        return name != null ? name.toString() : "";
    }

    public static String getDeviceManufacturer() {
        String value = adbGetProp("ro.product.manufacturer");
        return value != null ? value : "";
    }

    public static String getDeviceModel() {
        String value = adbGetProp("ro.product.model");
        return value != null ? value : "";
    }

    public static String getDeviceOS() {
        return isAndroid() ? "Android" : "iOS";
    }

    public static String getDeviceOSVersion() {
        AppiumDriver driver = getDriver();
        Object version = driver.getCapabilities().getCapability("platformVersion");
        if (version != null) {
            return version.toString();
        }
        String adbVersion = adbGetProp("ro.build.version.release");
        return adbVersion != null ? adbVersion : "";
    }

    public static String getDeviceOrientation() {
        AppiumDriver driver = getDriver();
        if (driver instanceof io.appium.java_client.remote.SupportsRotation) {
            return ((io.appium.java_client.remote.SupportsRotation) driver).getOrientation().toString();
        }
        return "";
    }

    public static void setDeviceOrientation(String orientation) {
        AppiumDriver driver = getDriver();
        if (driver instanceof io.appium.java_client.remote.SupportsRotation) {
            ((io.appium.java_client.remote.SupportsRotation) driver)
                    .rotate(org.openqa.selenium.ScreenOrientation.valueOf(orientation.toUpperCase()));
        } else {
            logger.warn("setDeviceOrientation is not supported on this driver - ignoring");
        }
    }

    private static String adbGetProp(String prop) {
        ExecutionContext context = ExecutionContext.getCurrent();
        AppiumDriver driver = context.getMobileDriver();
        Object udid = driver != null ? driver.getCapabilities().getCapability("udid") : null;
        try {
            List<String> command = new ArrayList<>();
            command.add("adb");
            if (udid != null) {
                command.add("-s");
                command.add(udid.toString());
            }
            command.add("shell");
            command.add("getprop");
            command.add(prop);
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try (java.io.BufferedReader reader =
                         new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                return line != null ? line.trim() : null;
            }
        } catch (Exception e) {
            logger.debug("adb getprop {} failed: {}", prop, e.getMessage());
            return null;
        }
    }

    // ==================== Element Interaction ====================

    public static void tap(TestObject testObject, int timeout) {
        logger.info("Tapping on: {}", describe(testObject));
        WebElement element = waitForElement(testObject, timeout);
        element.click();
    }

    public static void doubleTap(TestObject testObject, int timeout) {
        WebElement element = waitForElement(testObject, timeout);
        org.openqa.selenium.interactions.Actions actions = new org.openqa.selenium.interactions.Actions(getDriver());
        actions.doubleClick(element).perform();
    }

    public static void longPress(TestObject testObject, int durationSeconds, int timeout) {
        WebElement element = waitForElement(testObject, timeout);
        AppiumDriver driver = getDriver();
        Rectangle rect = element.getRect();
        int centerX = rect.getX() + rect.getWidth() / 2;
        int centerY = rect.getY() + rect.getHeight() / 2;
        performLongPress(driver, centerX, centerY, durationSeconds);
    }

    private static void performLongPress(AppiumDriver driver, int x, int y, int durationSeconds) {
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence sequence = new Sequence(finger, 0);
        sequence.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), x, y));
        sequence.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
        sequence.addAction(new Pause(finger, Duration.ofSeconds(durationSeconds)));
        sequence.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        driver.perform(Collections.singletonList(sequence));
    }

    public static void tapAtPosition(int x, int y) {
        AppiumDriver driver = getDriver();
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("x", x);
        params.put("y", y);
        try {
            driver.executeScript("mobile: clickGesture", params);
        } catch (Exception e) {
            PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
            Sequence sequence = new Sequence(finger, 0);
            sequence.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), x, y));
            sequence.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
            sequence.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
            driver.perform(Collections.singletonList(sequence));
        }
    }

    public static void setText(TestObject testObject, String text, int timeout) {
        logger.info("Setting text on: {} = '{}'", describe(testObject), text);
        WebElement element = waitForElement(testObject, timeout);
        try {
            element.clear();
        } catch (Exception ignored) {
            // Some elements (e.g. read-only fields) don't support clear() - keep going.
        }
        element.sendKeys(text);
    }

    public static void setEncryptedText(TestObject testObject, String encryptedText, int timeout) {
        // No real encryption support in katalan - treat the same as plain setText.
        setText(testObject, encryptedText, timeout);
    }

    public static void clearText(TestObject testObject, int timeout) {
        WebElement element = waitForElement(testObject, timeout);
        element.clear();
    }

    public static String getText(TestObject testObject, int timeout) {
        WebElement element = waitForElement(testObject, timeout);
        return element.getText();
    }

    public static String getAttribute(TestObject testObject, String attribute, int timeout) {
        WebElement element = waitForElement(testObject, timeout);
        return element.getAttribute(attribute);
    }

    public static int getElementWidth(TestObject testObject, int timeout) {
        return waitForElement(testObject, timeout).getRect().getWidth();
    }

    public static int getElementHeight(TestObject testObject, int timeout) {
        return waitForElement(testObject, timeout).getRect().getHeight();
    }

    public static int getElementTopPosition(TestObject testObject, int timeout) {
        return waitForElement(testObject, timeout).getRect().getY();
    }

    public static int getElementLeftPosition(TestObject testObject, int timeout) {
        return waitForElement(testObject, timeout).getRect().getX();
    }

    public static void swipe(int startX, int startY, int endX, int endY) {
        swipe(startX, startY, endX, endY, 800);
    }

    public static void swipe(int startX, int startY, int endX, int endY, int durationMs) {
        AppiumDriver driver = getDriver();
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence sequence = new Sequence(finger, 0);
        sequence.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), startX, startY));
        sequence.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
        sequence.addAction(finger.createPointerMove(Duration.ofMillis(durationMs), PointerInput.Origin.viewport(), endX, endY));
        sequence.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        driver.perform(Collections.singletonList(sequence));
    }

    /**
     * Scroll a scrollable container until an element with the given text is visible
     * (Android UiScrollable, matching Katalon's Mobile.scrollToText behavior).
     */
    public static void scrollToText(String text) {
        AppiumDriver driver = getDriver();
        if (driver instanceof AndroidDriver) {
            String uiSelector = "new UiScrollable(new UiSelector().scrollable(true))"
                    + ".scrollIntoView(new UiSelector().textContains(\"" + escapeForUiSelector(text) + "\"));";
            driver.findElement(AppiumBy.androidUIAutomator(uiSelector));
        } else {
            scrollToElement(buildTextObject(text), 10);
        }
    }

    public static void scrollToElement(TestObject testObject, int timeout) {
        long deadline = System.currentTimeMillis() + timeout * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (isElementVisibleNow(testObject)) {
                return;
            }
            swipe(540, 1400, 540, 600, 400);
        }
        throw new RuntimeException("Could not scroll element into view: " + describe(testObject));
    }

    private static TestObject buildTextObject(String text) {
        TestObject to = new TestObject();
        to.addProperty("text", ConditionType.EQUALS, text);
        return to;
    }

    private static String escapeForUiSelector(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ==================== Verification ====================

    public static boolean verifyElementExist(TestObject testObject, int timeout) {
        if (waitForElementSafely(testObject, timeout) == null) {
            throw new RuntimeException("verifyElementExist failed: element not found: " + describe(testObject));
        }
        return true;
    }

    public static boolean verifyElementNotExist(TestObject testObject, int timeout) {
        long deadline = System.currentTimeMillis() + timeout * 1000L;
        do {
            if (findElementsSafely(testObject).isEmpty()) {
                return true;
            }
            sleep(100);
        } while (System.currentTimeMillis() < deadline);
        if (!findElementsSafely(testObject).isEmpty()) {
            throw new RuntimeException("verifyElementNotExist failed: element still exists: " + describe(testObject));
        }
        return true;
    }

    public static boolean verifyElementVisible(TestObject testObject, int timeout) {
        WebElement element = waitForElementSafely(testObject, timeout);
        if (element == null || !element.isDisplayed()) {
            throw new RuntimeException("verifyElementVisible failed: element not visible: " + describe(testObject));
        }
        return true;
    }

    public static boolean verifyElementNotVisible(TestObject testObject, int timeout) {
        long deadline = System.currentTimeMillis() + timeout * 1000L;
        do {
            List<WebElement> elements = findElementsSafely(testObject);
            if (elements.isEmpty() || !elements.get(0).isDisplayed()) {
                return true;
            }
            sleep(100);
        } while (System.currentTimeMillis() < deadline);
        List<WebElement> elements = findElementsSafely(testObject);
        if (!elements.isEmpty() && elements.get(0).isDisplayed()) {
            throw new RuntimeException("verifyElementNotVisible failed: element still visible: " + describe(testObject));
        }
        return true;
    }

    public static boolean verifyElementText(TestObject testObject, String expectedText, int timeout) {
        String actual = getText(testObject, timeout);
        boolean matches = expectedText != null && expectedText.equals(actual);
        if (!matches) {
            throw new RuntimeException("verifyElementText failed: expected '" + expectedText + "' but got '" + actual + "'");
        }
        return true;
    }

    public static boolean verifyElementContainsText(TestObject testObject, String expectedText, int timeout) {
        String actual = getText(testObject, timeout);
        boolean matches = actual != null && expectedText != null && actual.contains(expectedText);
        if (!matches) {
            throw new RuntimeException("verifyElementContainsText failed: '" + actual + "' does not contain '" + expectedText + "'");
        }
        return true;
    }

    public static boolean verifyElementAttributeValue(TestObject testObject, String attribute, String expectedValue, int timeout) {
        String actual = getAttribute(testObject, attribute, timeout);
        boolean matches = expectedValue != null && expectedValue.equalsIgnoreCase(actual);
        if (!matches) {
            throw new RuntimeException("verifyElementAttributeValue failed: attribute '" + attribute
                    + "' expected '" + expectedValue + "' but got '" + actual + "'");
        }
        return true;
    }

    public static boolean verifyElementChecked(TestObject testObject, int timeout) {
        WebElement element = waitForElement(testObject, timeout);
        String checked = element.getAttribute("checked");
        boolean isChecked = "true".equalsIgnoreCase(checked) || element.isSelected();
        if (!isChecked) {
            throw new RuntimeException("verifyElementChecked failed: " + describe(testObject) + " is not checked");
        }
        return true;
    }

    public static boolean verifyElementNotChecked(TestObject testObject, int timeout) {
        WebElement element = waitForElement(testObject, timeout);
        String checked = element.getAttribute("checked");
        boolean isChecked = "true".equalsIgnoreCase(checked) || element.isSelected();
        if (isChecked) {
            throw new RuntimeException("verifyElementNotChecked failed: " + describe(testObject) + " is checked");
        }
        return true;
    }

    public static boolean verifyEqual(Object actual, Object expected) {
        boolean equal = actual == null ? expected == null : actual.equals(expected);
        if (!equal) {
            throw new RuntimeException("verifyEqual failed: '" + actual + "' != '" + expected + "'");
        }
        return true;
    }

    public static boolean verifyMatch(String actualText, String expectedText, boolean isRegex) {
        boolean matches = isRegex
                ? actualText != null && Pattern.matches(expectedText, actualText)
                : java.util.Objects.equals(actualText, expectedText);
        if (!matches) {
            throw new RuntimeException("verifyMatch failed: '" + actualText + "' does not match '" + expectedText + "'");
        }
        return true;
    }

    // ==================== Wait Keywords ====================

    public static boolean waitForElementPresent(TestObject testObject, int timeout) {
        return waitForElementSafely(testObject, timeout) != null;
    }

    public static boolean waitForElementNotPresent(TestObject testObject, int timeout) {
        return verifyElementNotExist(testObject, timeout);
    }

    public static boolean waitForElementVisible(TestObject testObject, int timeout) {
        return verifyElementVisible(testObject, timeout);
    }

    public static boolean waitForElementNotVisible(TestObject testObject, int timeout) {
        return verifyElementNotVisible(testObject, timeout);
    }

    public static void delay(double seconds) {
        try {
            Thread.sleep((long) (seconds * 1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== Navigation / Hardware Keys ====================

    public static void pressBack() {
        AppiumDriver driver = getDriver();
        if (driver instanceof AndroidDriver) {
            ((AndroidDriver) driver).pressKey(new KeyEvent(AndroidKey.BACK));
        }
    }

    public static void pressHome() {
        AppiumDriver driver = getDriver();
        if (driver instanceof AndroidDriver) {
            ((AndroidDriver) driver).pressKey(new KeyEvent(AndroidKey.HOME));
        }
    }

    public static void hideKeyboard() {
        AppiumDriver driver = getDriver();
        if (driver instanceof HidesKeyboard) {
            ((HidesKeyboard) driver).hideKeyboard();
        }
    }

    public static void sendKeyEvent(int keyCode) {
        AppiumDriver driver = getDriver();
        if (driver instanceof AndroidDriver) {
            for (AndroidKey key : AndroidKey.values()) {
                if (key.getCode() == keyCode) {
                    ((AndroidDriver) driver).pressKey(new KeyEvent(key));
                    return;
                }
            }
            logger.warn("Unknown Android key code: {}", keyCode);
        }
    }

    // ==================== Notifications / Network ====================

    public static void openNotifications() {
        AppiumDriver driver = getDriver();
        if (driver instanceof AndroidDriver) {
            ((AndroidDriver) driver).openNotifications();
        }
    }

    public static void closeNotifications() {
        pressBack();
    }

    /**
     * Set WiFi on/off absolutely (Katalon's documented Mobile.toggleWifi(String) keyword).
     * Uses HasNetworkConnection's bitmask rather than the gesture-based no-arg toggleWifi()
     * native method, so it sets state directly instead of guessing current state first.
     */
    public static void toggleWifi(String state) {
        toggleWifi(parseOnOff(state));
    }

    public static void toggleWifi(boolean enable) {
        AppiumDriver driver = getDriver();
        if (!(driver instanceof AndroidDriver)) {
            logger.warn("toggleWifi is only supported on Android - ignoring");
            return;
        }
        AndroidDriver androidDriver = (AndroidDriver) driver;
        long mask = androidDriver.getConnection().getBitMask();
        mask = enable ? (mask | ConnectionState.WIFI_MASK) : (mask & ~ConnectionState.WIFI_MASK);
        androidDriver.setConnection(new ConnectionState(mask));
    }

    public static void toggleData(String state) {
        toggleData(parseOnOff(state));
    }

    public static void toggleData(boolean enable) {
        AppiumDriver driver = getDriver();
        if (!(driver instanceof AndroidDriver)) {
            logger.warn("toggleData is only supported on Android - ignoring");
            return;
        }
        AndroidDriver androidDriver = (AndroidDriver) driver;
        long mask = androidDriver.getConnection().getBitMask();
        mask = enable ? (mask | ConnectionState.DATA_MASK) : (mask & ~ConnectionState.DATA_MASK);
        androidDriver.setConnection(new ConnectionState(mask));
    }

    private static boolean parseOnOff(String state) {
        if (state == null) {
            return false;
        }
        String normalized = state.trim().toLowerCase();
        return normalized.equals("on") || normalized.equals("true") || normalized.equals("enable") || normalized.equals("enabled");
    }

    // ==================== Lock / Misc Device ====================

    public static void lockDevice() {
        AppiumDriver driver = getDriver();
        if (driver instanceof LocksDevice) {
            ((LocksDevice) driver).lockDevice();
        }
    }

    public static void unlockDevice() {
        AppiumDriver driver = getDriver();
        if (driver instanceof LocksDevice) {
            ((LocksDevice) driver).unlockDevice();
        }
    }

    public static boolean isDeviceLocked() {
        AppiumDriver driver = getDriver();
        return driver instanceof LocksDevice && ((LocksDevice) driver).isDeviceLocked();
    }

    public static Object executeMobileCommand(String command, Map<String, Object> args) {
        AppiumDriver driver = getDriver();
        return driver.executeScript("mobile: " + command, args);
    }

    // ==================== Screenshot ====================

    public static String takeScreenshot() {
        return takeScreenshotInternal(String.valueOf(System.currentTimeMillis()));
    }

    public static String takeScreenshot(String filename) {
        String name = (filename == null || filename.isEmpty())
                ? String.valueOf(System.currentTimeMillis())
                : filename;
        return takeScreenshotInternal(name);
    }

    private static String takeScreenshotInternal(String filename) {
        logger.info("Taking mobile screenshot: {}", filename);
        try {
            TakesScreenshot ts = (TakesScreenshot) getDriver();
            File source = ts.getScreenshotAs(OutputType.FILE);
            Path destination = Path.of(getScreenshotPath(), filename + ".png");
            Files.createDirectories(destination.getParent());
            AsyncScreenshotWriter.getInstance().submitCopy(source.toPath(), destination);

            com.katalan.core.logging.XmlKeywordLogger kwLogger = com.katalan.core.logging.XmlKeywordLogger.getInstance();
            Map<String, String> props = new java.util.LinkedHashMap<>();
            props.put("attachment", filename + ".png");
            props.put("testops-method-name", "com.kms.katalon.core.mobile.keyword.builtin.TakeScreenshotKeyword.takeScreenshot");
            props.put("testops-execution-stacktrace", "");
            kwLogger.logMessage("PASSED", "Taking screenshot successfully", props);

            return destination.toString();
        } catch (IOException e) {
            logger.error("Failed to take mobile screenshot", e);
            throw new RuntimeException("Failed to take screenshot", e);
        }
    }

    private static String getScreenshotPath() {
        ExecutionContext context = ExecutionContext.getCurrent();
        if (context.getRunConfiguration() != null && context.getRunConfiguration().getScreenshotPath() != null) {
            return context.getRunConfiguration().getScreenshotPath().toString();
        }
        return "screenshots";
    }

    // ==================== Utility Keywords ====================

    public static void comment(String message) {
        logger.info("[COMMENT] {}", message);
    }

    public static void callTestCase(Object testCaseObj, Map<String, Object> variables) {
        WebUI.callTestCase(testCaseObj, variables);
    }

    public static void callTestCase(Object testCaseObj, Map<String, Object> variables, Object failureHandling) {
        WebUI.callTestCase(testCaseObj, variables, failureHandling);
    }

    // ==================== Driver / Locator Resolution ====================

    public static AppiumDriver getCurrentDriver() {
        return ExecutionContext.getCurrent().getMobileDriver();
    }

    /** Find a single element, waiting up to {@code timeout} seconds. Throws if not found. */
    public static WebElement findElement(TestObject testObject, int timeout) {
        return waitForElement(testObject, timeout);
    }

    /** Find all matching elements, waiting up to {@code timeout} seconds for at least one to appear. */
    public static List<WebElement> findElements(TestObject testObject, int timeout) {
        long deadline = System.currentTimeMillis() + Math.max(timeout, 0) * 1000L;
        List<WebElement> found = findElementsSafely(testObject);
        while (found.isEmpty() && System.currentTimeMillis() < deadline) {
            sleep(100);
            found = findElementsSafely(testObject);
        }
        return found;
    }

    private static AppiumDriver getDriver() {
        AppiumDriver driver = ExecutionContext.getCurrent().getMobileDriver();
        if (driver == null) {
            throw new IllegalStateException(
                    "Mobile driver is not initialized. Call Mobile.startApplication() or Mobile.startExistingApplication() first.");
        }
        return driver;
    }

    private static boolean isAndroid() {
        return getDriver() instanceof AndroidDriver;
    }

    private static String describe(TestObject testObject) {
        if (testObject == null) {
            return "<null>";
        }
        if (testObject.getObjectId() != null && !testObject.getObjectId().isEmpty()) {
            return testObject.getObjectId();
        }
        java.util.Map<String, String> props = testObject.getProperties();
        if (!props.isEmpty()) {
            java.util.Map.Entry<String, String> first = props.entrySet().iterator().next();
            return first.getKey() + "=" + first.getValue();
        }
        return "<unnamed test object>";
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String resolveAppPath(String appFile) {
        File f = new File(appFile);
        if (f.isAbsolute()) {
            return f.getAbsolutePath();
        }
        File resolved = new File(System.getProperty("user.dir"), appFile);
        return resolved.exists() ? resolved.getAbsolutePath() : f.getAbsolutePath();
    }

    /**
     * Wait (polling) for an element matching the TestObject to be present, returning it.
     * Throws if not found within timeout - mirrors WebUI.waitForElement()'s contract.
     */
    private static WebElement waitForElement(TestObject testObject, int timeout) {
        WebElement element = waitForElementSafely(testObject, timeout);
        if (element == null) {
            throw new RuntimeException("Element not found: " + describe(testObject) + " (timeout " + timeout + "s)");
        }
        return element;
    }

    private static final int DEFAULT_ELEMENT_TIMEOUT = 30;
    private static final int POLL_IMPLICIT_MS = 1000;

    /**
     * Mirrors Katalon's two-phase element search:
     * 1. Set Appium implicit timeout to `effectiveTimeout` seconds → findElements (server waits).
     * 2. Set implicit to 1s → verify still present.
     * timeout ≤ 0 uses DEFAULT_ELEMENT_TIMEOUT (matches Katalon's KeywordHelper correction).
     */
    private static WebElement waitForElementSafely(TestObject testObject, int timeout) {
        AppiumDriver driver = getDriver();
        By by = resolveLocator(testObject, driver instanceof IOSDriver);

        int effectiveTimeout = timeout <= 0 ? DEFAULT_ELEMENT_TIMEOUT : timeout;
        try {
            driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(effectiveTimeout));
            List<WebElement> found = driver.findElements(by);
            if (!found.isEmpty()) {
                driver.manage().timeouts().implicitlyWait(java.time.Duration.ofMillis(POLL_IMPLICIT_MS));
                found = driver.findElements(by);
                return found.isEmpty() ? null : found.get(0);
            }
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            try {
                driver.manage().timeouts().implicitlyWait(java.time.Duration.ofMillis(POLL_IMPLICIT_MS));
            } catch (Exception ignored) { /* best-effort restore */ }
        }
    }

    private static boolean isElementVisibleNow(TestObject testObject) {
        List<WebElement> elements = findElementsSafely(testObject);
        return !elements.isEmpty() && elements.get(0).isDisplayed();
    }

    private static List<WebElement> findElementsSafely(TestObject testObject) {
        try {
            AppiumDriver driver = getDriver();
            By by = resolveLocator(testObject, driver instanceof IOSDriver);
            return driver.findElements(by);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Resolve a Katalon Mobile TestObject's properties into an Appium {@link By} locator.
     *
     * Property names follow the same convention Katalon's Mobile Object Spy uses (and what
     * the brimerchant CSMobile.cari() helper builds at runtime): the literal UiAutomator /
     * XCUITest attribute names - "xpath", "resource-id" (Android), "content-desc" (Android),
     * "class", "text", "name"/"label" (iOS), or any other custom attribute, falling back to a
     * generic XPath attribute match. ConditionType.CONTAINS produces an xpath contains()
     * match instead of an exact one. When more than one active property is present (as in
     * real exported Object Repository .rs files), all are AND-combined into one XPath.
     */
    /**
     * Resolve a katalan TestObject (Map-based) to an Appium By locator.
     * Property names follow Katalon Mobile Object Spy conventions ("xpath", "resource-id",
     * "content-desc", "text", "class", "android uiautomator", etc.).
     * All properties in the map are considered active.
     */
    static By resolveLocator(TestObject testObject, boolean isIos) {
        if (testObject == null) {
            throw new IllegalArgumentException("TestObject cannot be null");
        }

        // Primary selector (set by ObjectRepository loader)
        TestObject.SelectorMethod sel = testObject.getSelectorMethod();
        String selVal = testObject.getSelectorValue();
        if (sel != null && selVal != null && !selVal.isEmpty()) {
            switch (sel) {
                case XPATH: return By.xpath(selVal);
                case CSS:   return By.cssSelector(selVal);
                case ID:    return isIos ? AppiumBy.accessibilityId(selVal) : By.id(selVal);
                default:    break;
            }
        }

        java.util.Map<String, String> props = testObject.getProperties();
        if (props.isEmpty()) {
            throw new IllegalStateException("TestObject has no properties to build a locator from: "
                    + describe(testObject));
        }

        // xpath wins outright — it's already a complete locator (may be a compound "A | B" expr).
        String xpath = props.get("xpath");
        if (xpath == null) xpath = props.get("XPATH");
        if (xpath != null && !xpath.isEmpty()) return By.xpath(xpath);

        // Directly-mappable strategies
        String id = firstNonNull(props, "resource-id", "id");
        if (id != null) return isIos ? AppiumBy.accessibilityId(id) : AppiumBy.id(id);

        String desc = firstNonNull(props, "content-desc", "accessibility id", "name");
        if (desc != null) return AppiumBy.accessibilityId(desc);

        String cls = firstNonNull(props, "class", "classname");
        if (cls != null) return AppiumBy.className(cls);

        String ua = firstNonNull(props, "android uiautomator", "-android uiautomator");
        if (ua != null) return AppiumBy.androidUIAutomator(ua);

        String iosPred = firstNonNull(props, "-ios predicate string", "ios predicate string");
        if (iosPred != null) return AppiumBy.iOSNsPredicateString(iosPred);

        String iosChain = firstNonNull(props, "-ios class chain", "ios class chain");
        if (iosChain != null) return AppiumBy.iOSClassChain(iosChain);

        // text → xpath @text attribute match
        String text = props.get("text");
        if (text != null && !text.isEmpty()) {
            String attr = isIos ? "label" : "text";
            return By.xpath("//*[@" + attr + "=" + xpathEscape(text) + "]");
        }

        // Last resort: build an AND-joined xpath from all remaining properties
        StringBuilder xb = new StringBuilder("//*[");
        boolean first = true;
        for (java.util.Map.Entry<String, String> e : props.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            if (!first) xb.append(" and ");
            String attrName = isIos ? mapAttributeName(e.getKey().toLowerCase()) : e.getKey().toLowerCase();
            xb.append("@").append(attrName).append("=").append(xpathEscape(e.getValue()));
            first = false;
        }
        if (first) {
            throw new IllegalStateException("TestObject has no usable properties: " + describe(testObject));
        }
        xb.append("]");
        return By.xpath(xb.toString());
    }

    private static String firstNonNull(java.util.Map<String, String> props, String... keys) {
        for (String k : keys) {
            String v = props.get(k);
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }

    private static String mapAttributeName(String propertyName) {
        switch (propertyName) {
            case "content-desc": return "name";
            case "resource-id":  return "name";
            default:             return propertyName;
        }
    }

    /** Wrap a value in XPath string-literal syntax, handling embedded quotes via concat(). */
    private static String xpathEscape(String value) {
        if (value == null) {
            return "''";
        }
        if (!value.contains("'")) {
            return "'" + value + "'";
        }
        if (!value.contains("\"")) {
            return "\"" + value + "\"";
        }
        StringBuilder sb = new StringBuilder("concat(");
        String[] parts = value.split("'");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(",\"'\",");
            }
            sb.append("'").append(parts[i]).append("'");
        }
        sb.append(")");
        return sb.toString();
    }
}
