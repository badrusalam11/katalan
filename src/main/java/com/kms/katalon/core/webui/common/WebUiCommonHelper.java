package com.kms.katalon.core.webui.common;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Katalon compatibility stub for WebUiCommonHelper.
 * Very minimal - methods just log warnings.
 */
public class WebUiCommonHelper {
    private static final Logger logger = LoggerFactory.getLogger(WebUiCommonHelper.class);

    public static WebElement findWebElement(Object testObject) {
        logger.warn("WebUiCommonHelper.findWebElement - not fully implemented");
        return null;
    }

    public static WebElement findWebElement(Object testObject, int timeout) {
        return findWebElement(testObject);
    }

    public static boolean isElementPresent(WebDriver driver, Object testObject, int timeout) {
        logger.warn("WebUiCommonHelper.isElementPresent - not fully implemented");
        return false;
    }

    public static String getTestObjectXpath(Object testObject) {
        logger.warn("WebUiCommonHelper.getTestObjectXpath - not fully implemented");
        return null;
    }
}
