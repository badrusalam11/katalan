package com.kms.katalon.core.mobile.helper;

import com.katalan.core.model.TestObject;

import java.util.List;

/**
 * Katalon compatibility layer for MobileElementCommonHelper.
 * Delegates to katalan's real Appium-backed Mobile engine.
 */
public class MobileElementCommonHelper {

    public static Object findElement(TestObject to, int timeout) {
        return com.katalan.keywords.Mobile.findElement(to, timeout);
    }

    public static List<?> findElements(TestObject to, int timeout) {
        return com.katalan.keywords.Mobile.findElements(to, timeout);
    }
}
