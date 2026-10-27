package com.kms.katalon.core.mobile.constants;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Properties;

/**
 * Katalon compatibility: Katalon's mobile keyword message constants (an Eclipse NLS bundle in
 * Katalon Studio). Field names mirror Katalon's class; values are read from the bundled
 * coreMobileMessages.properties, as NLS does.
 */
public class CoreMobileMessageConstants {

    public static String KW_LOG_INFO_STARTING_APP_AT;
    public static String KW_LOG_INFO_OVERRIDING_APP;
    public static String KW_LOG_INFO_OVERRIDE_APPLICATION;
    public static String KW_MSG_UNABLE_TO_START_APP_AT;
    public static String KW_LOG_INFO_STARTING_APP_WITH_ID;
    public static String KW_MSG_UNABLE_TO_START_APP_WITH_ID;
    public static String KW_MSG_APP_FILE_MISSING;
    public static String KW_MSG_DEVICE_MISSING;
    public static String KW_LOG_PASSED_START_APP_AT;
    public static String KW_MSG_UNABLE_TO_CLOSE_APPLICATION;
    public static String KW_LOG_PASSED_CLOSE_APP;
    public static String KW_MSG_UNSUPPORT_ACT_FOR_THIS_DEVICE;
    public static String KW_MSG_CANNOT_PRESS_BACK_BTN;
    public static String KW_LOG_PASSED_PRESS_BACK_BTN;
    public static String KW_LOG_PASSED_SWIPED_FROM_STARTXY_TO_ENDXY;
    public static String KW_MSG_CANNOT_SWIPE_ON_DEVICE;
    public static String KW_MSG_UNABLE_TO_TAKE_SCREENSHOT;
    public static String KW_LOG_PASSED_SCREENSHOT_IS_TAKEN;
    public static String KW_MSG_CANNOT_OPEN_NOTIFICATIONS;
    public static String KW_MSG_PASSED_OPEN_NOTIFICATIONS;
    public static String KW_MSG_TOUCH_ID_NOT_ENROLLED;
    public static String KW_MSG_TOUCH_ID_NOT_AVAILABLE_ON_PHYSICAL_MOBILE_DEVICES;
    public static String KW_MSG_TOUCH_ID_SUPPORTS_IOS_APP_ONLY;
    public static String KW_MSG_PASSED_PERFORM_TOUCH_ID;
    public static String KW_MSG_FAILED_PERFORM_TOUCH_ID;
    public static String KW_LOG_PASSED_HOME_BTN_PRESSED;
    public static String KW_MSG_CANNOT_PRESS_HOME_BTN;
    public static String KW_MANUFACTURER_APPLE;
    public static String KW_MSG_DEVICE_MANUFACTURER_IS;
    public static String KW_MSG_CANNOT_GET_MANUFACTURER;
    public static String KW_OS_IOS;
    public static String KW_LOG_PASSED_DEVICE_OS_NAME;
    public static String KW_MSG_CANNOT_GET_OS_NAME;
    public static String KW_LOG_PASSED_DEVICE_OS_VER_IS;
    public static String KW_MSG_CANNOT_GET_OS_VER;
    public static String KW_MSG_CANNOT_GET_DEVICE_MODEL;
    public static String KW_LOG_PASSED_NOTIFICATION_CLOSED;
    public static String KW_MSG_CANNOT_CLOSE_NOTIFICATIONS;
    public static String KW_LOG_PASSED_TOGGLE_AIRPLANE_MODE;
    public static String KW_MSG_CANNOT_TOGGLE_AIRPLANE_MODE;
    public static String KW_LOG_PASSED_TOGGLE_WIFI;
    public static String KW_MSG_CANNOT_TOGGLE_WIFI;
    public static String KW_MSG_TOGGLE_WIFI_NOT_AVAILABLE;
    public static String KW_MSG_COULD_NOT_FIND_TOGGLE_WIFI;
    public static String KW_LOG_PASSED_RUN_IOS_APP_PASSED;
    public static String KW_MSG_CANNOT_RUN_IOS_APP_IN_BACKGROUND;
    public static String KW_MSG_OBJ_NOT_FOUND;
    public static String KW_LOG_PASSED_ELEMENT_TEXT_IS;
    public static String KW_MSG_FAILED_TO_GET_ELEMENT_X_TEXT;
    public static String KW_MSG_FAILED_TO_GET_ELEMENT_TEXT;
    public static String KW_LOG_PASSED_TEXT_HAS_BEEN_SET_TO_ELEMENT;
    public static String KW_LOG_PASSED_ENCRYPTED_TEXT_HAS_BEEN_SET_TO_ELEMENT;
    public static String KW_MSG_FAILED_TO_SET_ELEMENT_X_TEXT;
    public static String KW_MSG_FAILED_TO_SET_ELEMENT_TEXT;
    public static String KW_MSG_FAILED_SET_ELEMENT_ENCRYPTED_TEXT;
    public static String KW_MSG_PASSED_USE_FINGERPRINT;
    public static String KW_MSG_FAILED_USE_FINGERPRINT;
    public static String KW_MSG_INVALID_FINGERPRINT_VALUE;
    public static String KW_MSG_FINGERPRINT_SUPPORTS_ANDROID_APP_ONLY;
    public static String KW_MSG_FINGERPRINT_NOT_AVAILABLE_ON_PHYSICAL_MOBILE_DEVICES;
    public static String KW_LOG_PASSED_TAPPED_ON_ELEMENT;
    public static String KW_MSG_FAILED_TO_TAP_ON_ELEMENT_X;
    public static String KW_MSG_FAILED_TO_TAP_ON_ELEMENT;
    public static String KW_LOG_PASSED_DOUBLE_TAPPED_ON_ELEMENT;
    public static String KW_MSG_FAILED_TO_DOUBLE_TAP_ON_ELEMENT_X;
    public static String KW_MSG_FAILED_TO_DOUBLE_TAP_ON_ELEMENT;
    public static String KW_LOG_PASSED_TAP_AND_HOLD_ON_ELEMENT_X_WITH_DURATION_Y;
    public static String KW_MSG_FAILED_TO_TAP_AND_HOLD_ON_ELEMENT_X_WITH_DURATION_Y;
    public static String KW_MSG_FAILED_TO_TAP_AND_HOLD_ON_ELEMENT;
    public static String KW_LOG_PASSED_TAP_AND_HOLD_AT_X_Y_WITH_DURATION_Z;
    public static String KW_MSG_FAILED_TO_TAP_AND_HOLD_AT_X_Y_WITH_DURATION_Z;
    public static String KW_MSG_FAILED_TO_TAP_AND_HOLD_AT_POSITION;
    public static String KW_LOG_PASSED_LONG_PRESSED_ON_ELEMENT;
    public static String KW_MSG_FAILED_TO_LONG_PRESS_ON_ELEMENT_X;
    public static String KW_MSG_FAILED_TO_LONG_PRESS_ON_ELEMENT;
    public static String KW_LOG_PASSED_ELEMENT_HAS_ATTR;
    public static String KW_MSG_FAILED_TO_GET_ELEMENT_X_ATTR_Y;
    public static String KW_MSG_FAILED_TO_GET_ELEMENT_ATTR;
    public static String KW_LOG_PASSED_ELEMENT_PRESENTED;
    public static String KW_LOG_PASSED_ELEMENT_NOT_PRESENTED;
    public static String KW_MSG_FAILED_TO_WAIT_FOR_ELEMENT_X_PRESENT;
    public static String KW_MSG_FAILED_TO_WAIT_FOR_ELEMENT_X_NOT_PRESENT;
    public static String KW_MSG_FAILED_TO_WAIT_FOR_ELEMENT_PRESENT;
    public static String KW_MSG_FAILED_TO_WAIT_FOR_ELEMENT_NOT_PRESENT;
    public static String KW_LOG_PASSED_ELEMENT_X_EXISTED;
    public static String KW_LOG_FAILED_ELEMENT_X_EXISTED;
    public static String KW_MSG_FAILED_TO_CHECK_FOR_ELEMENT_EXIST;
    public static String KW_MSG_FAILED_TO_CHECK_FOR_ELEMENT_X_EXIST;
    public static String KW_MSG_FAILED_TO_ASSERT_FOR_ELEMENT_EXIST;
    public static String KW_LOG_PASSED_ELEMENT_TEXT_IS_CLEARED;
    public static String KW_MSG_FAILED_TO_CLEAR_TEXT_OF_ELEMENT_X;
    public static String KW_MSG_FAILED_TO_CLEAR_TEXT_OF_ELEMENT;
    public static String KW_LOG_PASSED_VERIFY_LANDSCAPE;
    public static String KW_LOG_FAILED_VERIFY_LANDSCAPE;
    public static String KW_MSG_UNABLE_VERIFY_LANDSCAPE;
    public static String KW_LOG_PASSED_VERIFY_PORTRAIT;
    public static String KW_LOG_FAILED_VERIFY_PORTRAIT;
    public static String KW_MSG_UNABLE_VERIFY_PORTRAIT;
    public static String KW_LOG_PASSED_SWITCH_LANDSCAPE;
    public static String KW_MSG_UNABLE_SWITCH_LANDSCAPE;
    public static String KW_LOG_PASSED_SWITCH_PORTRAIT;
    public static String KW_MSG_UNABLE_SWITCH_PORTRAIT;
    public static String KW_LOG_PASSED_SWITCH_WEB_VIEW;
    public static String KW_LOG_FAILED_SWITCH_WEB_VIEW;
    public static String KW_MSG_UNABLE_SWITCH_WEB_VIEW;
    public static String KW_LOG_PASSED_SWITCH_NATIVE;
    public static String KW_LOG_FAILED_SWITCH_NATIVE;
    public static String KW_MSG_UNABLE_SWITCH_NATIVE;
    public static String KW_LOG_PASSED_SCROLL_TO_TEXT_X;
    public static String KW_MSG_UNABLE_SCROLL_TO_TEXT_X;
    public static String KW_MSG_UNABLE_FIND_DRIVER;
    public static String COMM_LOG_INFO_CHECKING_TEXT;
    public static String COMM_EXC_TEXT_IS_NULL;
    public static String KW_LOG_PASSED_ELEMENT_X_VISIBLE;
    public static String KW_LOG_FAILED_ELEMENT_X_VISIBLE;
    public static String KW_MSG_FAILED_TO_CHECK_FOR_ELEMENT_VISIBLE;
    public static String KW_MSG_FAILED_TO_CHECK_FOR_ELEMENT_X_VISIBLE;
    public static String KW_MSG_FAILED_TO_CHECK_FOR_ELEMENT_NOT_VISIBLE;
    public static String KW_MSG_FAILED_TO_CHECK_FOR_ELEMENT_X_NOT_VISIBLE;
    public static String KW_MSG_FAILED_TO_CHECK_FOR_ELEMENT_NOT_EXIST;
    public static String KW_MSG_FAILED_TO_CHECK_FOR_ELEMENT_X_NOT_EXIST;
    public static String KW_LOG_PASSED_GET_ORIENTATION_X;
    public static String KW_MSG_UNABLE_GET_ORIENTATION;
    public static String KW_LOG_PASSED_GET_DEVICE_WIDTH_X;
    public static String KW_MSG_UNABLE_GET_DEVICE_WIDTH;
    public static String KW_LOG_PASSED_GET_DEVICE_HEIGHT_X;
    public static String KW_MSG_UNABLE_GET_DEVICE_HEIGHT;
    public static String KW_LOG_PASSED_OBJ_X_HAS_ATTRIBUTE_Y;
    public static String KW_LOG_FAILED_OBJ_X_HAS_ATTRIBUTE_Y;
    public static String KW_MSG_CANNOT_VERIFY_OBJ_X_HAS_ATTRIBUTE_Y;
    public static String KW_MSG_CANNOT_VERIFY_OBJ_HAS_ATTRIBUTE;
    public static String COMM_LOG_INFO_CHECKING_ATTRIBUTE_NAME;
    public static String COMM_EXC_ATTRIBUTE_NAME_IS_NULL;
    public static String KW_MSG_CANNOT_VERIFY_OBJ_X_NOT_HAS_ATTRIBUTE_Y;
    public static String KW_MSG_CANNOT_VERIFY_OBJ_NOT_HAS_ATTRIBUTE;
    public static String KW_LOG_PASSED_OBJ_X_ATTRIBUTE_Y_VALUE_Z;
    public static String KW_LOG_FAILED_OBJ_X_ATTRIBUTE_Y_ACTUAL_VALUE_Z_EXPECTED_VALUE_W;
    public static String KW_MSG_CANNOT_VERIFY_OBJ_X_ATTRIBUTE_Y_VALUE_Z;
    public static String KW_MSG_CANNOT_VERIFY_OBJ_ATTRIBUTE_VALUE;
    public static String KW_MSG_CANNOT_WAIT_OBJ_X_HAS_ATTRIBUTE_Y;
    public static String KW_MSG_CANNOT_WAIT_OBJ_HAS_ATTRIBUTE;
    public static String KW_MSG_CANNOT_WAIT_OBJ_X_NOT_HAS_ATTRIBUTE_Y;
    public static String KW_MSG_CANNOT_WAIT_OBJ_NOT_HAS_ATTRIBUTE;
    public static String KW_LOG_FAILED_WAIT_FOR_OBJ_X_HAS_ATTRIBUTE_Y_VALUE_Z;
    public static String KW_MSG_CANNOT_WAIT_OBJ_X_ATTRIBUTE_Y_VALUE_Z;
    public static String KW_MSG_CANNOT_WAIT_OBJ_ATTRIBUTE_VALUE;
    public static String KW_LOG_PASSED_ELEMENT_X_CHECKED;
    public static String KW_LOG_FAILED_ELEMENT_X_CHECKED;
    public static String KW_MSG_FAILED_TO_CHECK_FOR_ELEMENT_CHECKED;
    public static String KW_LOG_PASSED_DRAG_AND_DROP_ELEMENT_X_TO_ELEMENT_Y;
    public static String KW_MSG_FAILED_TO_DRAG_AND_DROP_ELEMENT_X_TO_ELEMENT_Y;
    public static String KW_MSG_FAILED_TO_DRAG_AND_DROP_ELEMENT;
    public static String KW_MSG_FAILED_TO_CHECK_FOR_ELEMENT_X_CHECKED;
    public static String KW_LOG_PASSED_ELEMENT_X_UNCHECKED;
    public static String KW_LOG_FAILED_ELEMENT_X_UNCHECKED;
    public static String KW_MSG_FAILED_TO_CHECK_FOR_ELEMENT_UNCHECKED;
    public static String KW_MSG_FAILED_TO_CHECK_FOR_ELEMENT_X_UNCHECKED;
    public static String KW_LOG_PASSED_HIDE_KEYBOARD;
    public static String KW_MSG_CANNOT_HIDE_KEYBOARD;
    public static String KW_LOG_PASSED_CHECK_ELEMENT;
    public static String KW_MSG_FAILED_TO_CHECK_ELEMENT_X;
    public static String KW_MSG_FAILED_TO_CHECK_ELEMENT;
    public static String KW_LOG_PASSED_UNCHECK_ELEMENT;
    public static String KW_MSG_FAILED_TO_UNCHECK_ELEMENT_X;
    public static String KW_MSG_FAILED_TO_UNCHECK_ELEMENT;
    public static String KW_LOG_PASSED_SET_SLIDER_X_TO_Y;
    public static String KW_MSG_FAILED_SET_SLIDER_X_TO_Y;
    public static String KW_MSG_FAILED_SET_SLIDER_INVALID_PERCENTAGE_X;
    public static String KW_MSG_FAILED_SET_SLIDER;
    public static String KW_LOG_PASSED_TAPPED_AT_X_Y;
    public static String KW_LOG_FAILED_TAPPED_AT_X_Y;
    public static String KW_LOG_FAILED_TAPPED_AT_POSITION;
    public static String KW_LOG_PASSED_ZOOM_AT_X_Y_WITH_OFFSET_Z;
    public static String KW_LOG_FAILED_ZOOM_AT_X_Y_WITH_OFFSET_Z;
    public static String KW_LOG_FAILED_ZOOM_AT_POSITION;
    public static String KW_LOG_PASSED_PINCH_AT_X_Y_WITH_OFFSET_Z;
    public static String KW_LOG_FAILED_PINCH_AT_X_Y_WITH_OFFSET_Z;
    public static String KW_LOG_FAILED_PINCH_AT_POSITION;
    public static String KW_MSG_FAILED_PARAM_X_CANNOT_BE_NULL;
    public static String APPIUM_DRIVER_ERROR_JS_FAILED;
    public static String MOBILE_PROPERTY_FILE_NAME;
    public static String KW_LOG_MOBILE_PROPERTY_SETTING;
    public static String APPIUM_START_EXCEPTION_APPIUM_DIRECTORY_NOT_SET;
    public static String APPIUM_START_EXCEPTION_APPIUM_DIRECTORY_INVALID_CANNOT_FIND_APPIUM_JS;
    public static String COMM_LOG_INFO_CHECKING_DURATION;
    public static String COMM_LOG_WARNING_INVALID_DURATION;
    public static String COMM_LOG_INFO_CHECKING_PERCENTAGE;
    public static String KW_LOG_PASSED_OBJ_HAS_TOP_POSITION;
    public static String KW_MSG_FAILED_TO_GET_TOP_POSITION_OF_ELEMENT;
    public static String KW_MSG_FAILED_TO_GET_TOP_POSITION;
    public static String KW_LOG_PASSED_OBJ_HAS_LEFT_POSITION;
    public static String KW_MSG_FAILED_TO_GET_LEFT_POSITION_OF_ELEMENT;
    public static String KW_MSG_FAILED_TO_GET_LEFT_POSITION;
    public static String KW_MSG_FAILED_TO_UNLOCK_SCREEN;
    public static String KW_MSG_PASSED_TO_UNLOCK_SCREEN;
    public static String KW_LOG_PASSED_LIST_LABELED_ITEM_CLICKED;
    public static String KW_LOG_FAILED_LABELED_ITEM_NOT_FOUND;
    public static String KW_MSG_FAILED_TO_SELECT_ELEMENT_BY_LABEL_OF_OBJ;
    public static String KW_MSG_FAILED_TO_SELECT_ELEMENT_BY_LABEL;
    public static String COMM_LOG_INFO_CHECKING_X;
    public static String COMM_LOG_INFO_CHECKING_Y;
    public static String KW_LOG_PASSED_LIST_ITEM_CLICKED;
    public static String KW_LOG_FAILED_LIST_ITEM_INDEX_EXCEED;
    public static String KW_LOG_FAILED_FEATURE_NOT_AVAILABLE_ON_ANDROID;
    public static String KW_MSG_FAILED_TO_SELECT_ELEMENT_BY_INDEX_OF_OBJ;
    public static String KW_MSG_FAILED_TO_SELECT_ELEMENT_BY_INDEX;
    public static String COMM_LOG_INFO_CHECKING_OFFSET;
    public static String KW_LOG_PASSED_OBJ_HAS_WIDTH;
    public static String KW_MSG_FAILED_TO_GET_WIDTH_OF_ELEMENT_X;
    public static String KW_MSG_FAILED_TO_GET_WIDTH_OF_ELEMENT;
    public static String KW_LOG_PASSED_OBJ_HAS_HEIGHT;
    public static String KW_MSG_FAILED_TO_GET_HEIGHT_OF_ELEMENT_X;
    public static String KW_MSG_FAILED_TO_GET_HEIGHT_OF_ELEMENT;
    public static String KW_MSG_ACTUAL_ELEMENT_TXT_NOT_MATCHED_EXPECTED_TXT;
    public static String KW_LOG_ACTUAL_ELEMENT_TXT_MATCHED_EXPECTED_TXT;
    public static String KW_MSG_VERIFY_ELEMENT_TEXT_FAILED;
    public static String KW_MSG_CANNOT_SEND_KEYS_X_ON_ELEMENT_Y;
    public static String KW_LOG_SEND_KEYS_X_ON_ELEMENT_Y_SUCCESSFULLY;
    public static String KW_MSG_CANNOT_PRESS_KEY_CODE;
    public static String KW_LOG_PRESS_KEY_CODE_SUCCESSFULLY;
    public static String KW_MSG_TEXT_NOT_FOUND;
    public static String KW_LOG_TEXT_FOUND_IN_ELEMENTS;
    public static String KW_LOG_PASSED_EXECUTE_MOBILE_COMMAND;
    public static String KW_LOG_FAILED_TO_EXECUTE_MOBILE_COMMAND;
    public static String KW_MSG_SCREENSHOT_EXCEPTION_FILENAME_NULL_EMPTY;
    public static String KW_MSG_SCREENSHOT_EXCEPTION_ELEMENT_NULL;
    public static String KW_MSG_SCREENSHOT_EXCEPTION_AREA_NULL;
    public static String KW_LOG_SCREENSHOT_FAILED_HIDE_OBJECT;
    public static String KW_LOG_SCREENSHOT_HIDDEN_OBJECTS_COUNT;
    public static String KW_LOG_SCREENSHOT_STATUSBAR_INFO;
    public static String KW_LOG_SCREENSHOT_STATUSBAR_EXIST;
    public static String KW_MSG_SCREENSHOT_STATUSBAR_INFO_FAIL;
    public static String KW_LOG_FAILED_GET_OS_STATUSBAR;
    public static String KW_LOG_FAILED_GET_STATUSBAR;
    public static String KW_LOG_FAILED_GET_DEVICE_SCREEN_INFO;
    public static String KW_LOG_FAILED_GET_SCALE_FACTOR;
    public static String KW_LOG_FAILED_GET_DEVICE_VIEWPORT_RECT;
    public static String KW_LOG_INFO_DEFAULT_LOCATOR_FAILED_TRY_SELF_HEALING;
    public static String KW_LOG_INFO_PROPOSE_ALTERNATE_LOCATOR;
    public static String KW_LOG_INFO_FOUND_MULTI_ELEMENTS;
    public static String KW_LOG_INFO_COULD_NOT_SAVE_SCREENSHOT;
    public static String KW_LOG_INFO_FINDING_ELEMENT_WITH_LOCATOR;
    public static String KW_LOG_INFO_EMPTY_LOCATOR;
    public static String KW_LOG_INFO_FOUND_ELEMENT;

    static {
        Properties messages = new Properties();
        try (InputStream in = CoreMobileMessageConstants.class.getResourceAsStream("coreMobileMessages.properties")) {
            if (in != null) {
                messages.load(in);
            }
        } catch (Exception ignored) {
            // fields fall back to their own names below
        }
        for (Field field : CoreMobileMessageConstants.class.getDeclaredFields()) {
            int mod = field.getModifiers();
            if (Modifier.isStatic(mod) && Modifier.isPublic(mod) && field.getType() == String.class) {
                try {
                    field.set(null, messages.getProperty(field.getName(), field.getName()));
                } catch (IllegalAccessException ignored) {
                    // public static field: not reachable
                }
            }
        }
    }

    private CoreMobileMessageConstants() {
    }
}
