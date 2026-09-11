package com.kms.katalon.core.mobile.keyword.builtin;

import com.katalan.core.model.TestObject;
import com.kms.katalon.core.mobile.keyword.internal.MobileAbstractKeyword;

/**
 * Katalon compatibility stub for the internal builtin keyword class. Some custom keyword
 * libraries (e.g. brimerchant's CSMobile) import this directly even though they never
 * instantiate it - Groovy still requires the class to resolve at compile time. Delegates
 * to the real engine in case anything does call it directly.
 */
public class WaitForElementPresentKeyword extends MobileAbstractKeyword {

    public boolean execute(TestObject to, int timeout) {
        return com.katalan.keywords.Mobile.waitForElementPresent(to, timeout);
    }
}
