package com.kms.katalon.core.windows.keyword;

import com.kms.katalon.core.model.FailureHandling;
import com.kms.katalon.core.testobject.TestObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Katalon compatibility stub for WindowsBuiltinKeywords
 * Windows testing is not supported in Katalan - these are stubs.
 */
public class WindowsBuiltinKeywords {
    
    private static final Logger logger = LoggerFactory.getLogger(WindowsBuiltinKeywords.class);
    
    private static void logNotSupported(String method) {
        logger.warn("Windows.{} is not supported in Katalan", method);
    }
    
    public static void startApplication(String appFile) { logNotSupported("startApplication"); }
    public static void startApplication(String appFile, FailureHandling fh) { logNotSupported("startApplication"); }
    public static void closeApplication() { logNotSupported("closeApplication"); }
    public static void click(TestObject to) { logNotSupported("click"); }
    public static void click(TestObject to, FailureHandling fh) { logNotSupported("click"); }
    public static void setText(TestObject to, String text) { logNotSupported("setText"); }
    public static void setText(TestObject to, String text, FailureHandling fh) { logNotSupported("setText"); }
    public static String getText(TestObject to) { logNotSupported("getText"); return ""; }
    public static String getText(TestObject to, FailureHandling fh) { logNotSupported("getText"); return ""; }
}
