package com.kms.katalon.core.mobile.keyword;

import com.kms.katalon.core.model.FailureHandling;
import com.kms.katalon.core.testobject.TestObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Katalon compatibility stub for MobileBuiltInKeywords
 * Mobile testing is not supported in Katalan - these are stubs.
 */
public class MobileBuiltInKeywords {
    
    private static final Logger logger = LoggerFactory.getLogger(MobileBuiltInKeywords.class);
    
    private static void logNotSupported(String method) {
        logger.warn("Mobile.{} is not supported in Katalan (mobile testing not implemented)", method);
    }
    
    public static void startApplication(String appFile, boolean reset) { logNotSupported("startApplication"); }
    public static void startApplication(String appFile, boolean reset, FailureHandling fh) { logNotSupported("startApplication"); }
    public static void closeApplication() { logNotSupported("closeApplication"); }
    public static void closeApplication(FailureHandling fh) { logNotSupported("closeApplication"); }
    public static void tap(TestObject to) { logNotSupported("tap"); }
    public static void tap(TestObject to, int timeout) { logNotSupported("tap"); }
    public static void tap(TestObject to, int timeout, FailureHandling fh) { logNotSupported("tap"); }
    public static void setText(TestObject to, String text) { logNotSupported("setText"); }
    public static void setText(TestObject to, String text, int timeout) { logNotSupported("setText"); }
    public static void setText(TestObject to, String text, int timeout, FailureHandling fh) { logNotSupported("setText"); }
    public static String getText(TestObject to) { logNotSupported("getText"); return ""; }
    public static String getText(TestObject to, int timeout) { logNotSupported("getText"); return ""; }
    public static String getText(TestObject to, int timeout, FailureHandling fh) { logNotSupported("getText"); return ""; }
    public static boolean verifyElementExist(TestObject to, int timeout) { logNotSupported("verifyElementExist"); return false; }
    public static boolean verifyElementExist(TestObject to, int timeout, FailureHandling fh) { logNotSupported("verifyElementExist"); return false; }
    public static void delay(int seconds) {
        try { Thread.sleep(seconds * 1000L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
