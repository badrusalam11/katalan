package com.katalan.core.engine;

import com.katalan.core.context.ExecutionContext;
import com.kms.katalon.core.exception.StepFailedException;
import com.kms.katalon.core.webui.exception.WebElementNotFoundException;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * Reproduces the failure text Katalon Studio produces, so listeners that forward
 * {@code testCaseContext.getMessage()} (e.g. the Actmo reporter) get the same message under katalan.
 *
 * <p>A failed built-in keyword surfaces in Katalon as
 * {@code StepFailedException: Unable to click on object 'X' (Root cause: <cause stack trace>)} with the
 * cause attached (KeywordMain.stepFailed), and a failed test case as
 * {@code <testCaseId> FAILED.\nReason:\n<stack trace>} (TestCaseExecutor.onExecutionError).
 */
public final class KatalonStepFailure {

    /** Katalon's own wording for keywords that have no specific message (KeywordMain.runKeyword). */
    private static final String GENERIC_MESSAGE = "Keyword {0} was failed";

    /** Root cause Katalon reports when a test object's element cannot be found (EXC_WEB_ELEMENT_NOT_FOUND). */
    private static final String ELEMENT_NOT_FOUND = "Web element with id: ''{0}'' located by ''{1}'' not found";

    /**
     * Katalon's per-keyword failure messages (coreWebuiMessages.properties), formatted with the
     * keyword's arguments; a test object argument is replaced by its object id.
     */
    private static final Map<String, String> MESSAGES = new HashMap<>();
    static {
        MESSAGES.put("click", "Unable to click on object ''{0}''");
        MESSAGES.put("doubleClick", "Unable to double click on object ''{0}''");
        MESSAGES.put("rightClick", "Unable to right click on object ''{0}''");
        MESSAGES.put("focus", "Unable to focus on object ''{0}''");
        MESSAGES.put("check", "Unable to check object ''{0}''");
        MESSAGES.put("uncheck", "Unable to un-check object ''{0}''");
        MESSAGES.put("submit", "Unable to submit the form containing object ''{0}''");
        MESSAGES.put("scrollToElement", "Unable to scroll to object ''{0}''");
        MESSAGES.put("setText", "Unable to set text ''{1}'' of object ''{0}''");
        MESSAGES.put("setEncryptedText", "Unable to set encrypted text for object ''{0}''");
        MESSAGES.put("getText", "Unable to get text of object ''{0}''");
        MESSAGES.put("getAttribute", "Unable to get attribute ''{1}'' of object ''{0}''");
        MESSAGES.put("uploadFile", "Unable to upload file ''{1}'' to object ''{0}''");
        MESSAGES.put("selectOptionByIndex", "Unable to select option by index ''{1}'' of object ''{0}''");
        MESSAGES.put("selectOptionByValue", "Unable to select option by value {1}");
        MESSAGES.put("selectOptionByLabel", "Unable to select option by label {1}");
        MESSAGES.put("selectAllOption", "Unable to select all options on object ''{0}''");
        MESSAGES.put("deselectOptionByValue", "Unable to de-select option by value {1}");
        MESSAGES.put("deselectOptionByLabel", "Unable to de-select option by label {1}");
        MESSAGES.put("verifyElementPresent", "Unable to verify object ''{0}'' is present");
        MESSAGES.put("verifyElementNotPresent", "Unable to verify object ''{0}'' is not present");
        MESSAGES.put("verifyElementVisible", "Unable to verify object ''{0}'' is visible");
        MESSAGES.put("verifyElementNotVisible", "Unable to verify object ''{0}'' is NOT visible");
        MESSAGES.put("verifyElementClickable", "Unable to verify object ''{0}'' to be clickable");
        MESSAGES.put("verifyElementNotClickable", "Unable to verify object ''{0}'' to be NOT clickable");
        MESSAGES.put("verifyElementChecked", "Unable to verify object ''{0}'' is checked");
        MESSAGES.put("verifyElementNotChecked", "Unable to verify object ''{0}'' is not checked");
        MESSAGES.put("verifyElementHasAttribute", "Unable to verify if object ''{0}'' has attribute ''{1}''");
        MESSAGES.put("waitForElementPresent", "Unable to wait for object ''{0}'' to be present");
        MESSAGES.put("waitForElementNotPresent", "Unable to wait for object ''{0}'' to be not present");
        MESSAGES.put("waitForElementVisible", "Unable to wait for object ''{0}'' to be visible");
        MESSAGES.put("waitForElementNotVisible", "Unable to wait for object ''{0}'' to be NOT visible");
        MESSAGES.put("waitForElementClickable", "Unable to wait for object ''{0}'' to be clickable");
        MESSAGES.put("waitForElementNotClickable", "Unable to wait for object ''{0}'' to be not clickable");
        MESSAGES.put("navigateToUrl", "Unable to navigate to ''{0}''");
        MESSAGES.put("switchToWindowTitle", "Unable to switch to window with title: ''{0}''");
        MESSAGES.put("switchToWindowUrl", "Unable to switch to window with url: ''{0}''");
        MESSAGES.put("switchToWindowIndex", "Unable to switch to window with index: ''{0}''");
        MESSAGES.put("closeWindowTitle", "Unable to close window with title: ''{0}''");
        MESSAGES.put("closeWindowUrl", "Unable to close window with url: ''{0}''");
        MESSAGES.put("closeWindowIndex", "Unable to close window with index {0}");
    }

    private KatalonStepFailure() {}

    /**
     * Convert a built-in keyword's failure into the {@link StepFailedException} Katalon would throw.
     * Failures that are already Katalon's (KeywordUtil.markFailed, a nested call) pass through.
     */
    public static RuntimeException forKeyword(String keyword, Object[] args, Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof StepFailedException) {
            return (StepFailedException) cause;
        }
        // A called test case already reported its own failing step; don't bury it under another layer.
        if ("callTestCase".equals(keyword) && cause instanceof RuntimeException) {
            return (RuntimeException) cause;
        }
        if (cause instanceof groovy.lang.MissingMethodException && keyword.equals(((groovy.lang.MissingMethodException) cause).getMethod())) {
            return (RuntimeException) cause;
        }

        Object[] values = args == null ? new Object[0] : args.clone();
        Object testObject = null;
        for (int i = 0; i < values.length; i++) {
            if (isTestObject(values[i])) {
                if (testObject == null) testObject = values[i];
                values[i] = objectIdOf(values[i]);
            }
        }

        Throwable rootCause = katalonRootCause(testObject, cause);
        String template = MESSAGES.get(keyword);
        if (template == null && rootCause instanceof StepFailedException) {
            // A check the keyword failed on purpose (verifyMatch, verifyElementText, ...): Katalon
            // reports that message itself, not "Keyword X was failed".
            return (StepFailedException) rootCause;
        }
        String message = template != null ? MessageFormat.format(template, values)
                : MessageFormat.format(GENERIC_MESSAGE, keyword);
        return new StepFailedException(message + " (Root cause: " + stackTraceOf(rootCause) + ")", rootCause);
    }

    /** The text Katalon stores as {@code testCaseContext.getMessage()} for a failed test case. */
    public static String testCaseFailedMessage(String testCaseId, String stackTrace) {
        return testCaseId + " FAILED.\nReason:\n" + stackTrace;
    }

    /** Strip the reflection / script-runner wrappers katalan adds around the script's own exception. */
    public static Throwable unwrap(Throwable t) {
        while (t != null && t.getCause() != null && t.getCause() != t) {
            boolean wrapper = t instanceof InvocationTargetException
                    || t instanceof UndeclaredThrowableException
                    || t instanceof org.codehaus.groovy.runtime.InvokerInvocationException
                    || (t.getClass() == RuntimeException.class && (t.getMessage() == null
                        || t.getMessage().startsWith("Script execution failed: ")
                        || t.getMessage().equals(t.getCause().toString())));
            if (!wrapper) break;
            t = t.getCause();
        }
        return t;
    }

    public static String stackTraceOf(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /**
     * Katalon looks the element up itself and reports a missing one as WebElementNotFoundException;
     * katalan's keywords surface that as a Selenium timeout or a katalan StepFailedException instead.
     */
    private static Throwable katalonRootCause(Object testObject, Throwable cause) {
        if (testObject != null && isLookupFailure(cause)) {
            By by = locatorOf(testObject);
            if (by != null && !elementExists(by)) {
                WebElementNotFoundException notFound = new WebElementNotFoundException(MessageFormat.format(
                        ELEMENT_NOT_FOUND, objectIdOf(testObject), locatorText(by)));
                notFound.setStackTrace(cause.getStackTrace());
                return notFound;
            }
        }
        if (cause instanceof com.katalan.core.exception.StepFailedException) {
            StepFailedException katalon = new StepFailedException(cause.getMessage(), cause.getCause());
            katalon.setStackTrace(cause.getStackTrace());
            return katalon;
        }
        return cause;
    }

    private static boolean isLookupFailure(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (c instanceof org.openqa.selenium.NoSuchElementException
                    || c instanceof org.openqa.selenium.TimeoutException
                    || c instanceof org.openqa.selenium.StaleElementReferenceException
                    || c instanceof com.katalan.core.exception.StepFailedException) {
                return true;
            }
        }
        return false;
    }

    private static boolean elementExists(By by) {
        ExecutionContext ctx = ExecutionContext.getCurrent();
        WebDriver driver = ctx == null ? null : ctx.getWebDriver();
        if (driver == null) return true; // no browser: not a lookup failure, keep the original cause
        try {
            return !driver.findElements(by).isEmpty();
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean isTestObject(Object o) {
        return o instanceof com.kms.katalon.core.testobject.TestObject
                || o instanceof com.katalan.core.model.TestObject;
    }

    private static String objectIdOf(Object o) {
        if (o instanceof com.kms.katalon.core.testobject.TestObject) {
            return ((com.kms.katalon.core.testobject.TestObject) o).getObjectId();
        }
        return ((com.katalan.core.model.TestObject) o).getObjectId();
    }

    private static By locatorOf(Object o) {
        try {
            if (o instanceof com.kms.katalon.core.testobject.TestObject) {
                return ((com.kms.katalon.core.testobject.TestObject) o).toSeleniumBy();
            }
            return ((com.katalan.core.model.TestObject) o).toSeleniumBy();
        } catch (Exception e) {
            return null;
        }
    }

    /** {@code By.xpath: //a} -> {@code //a}, the bare selector Katalon prints. */
    private static String locatorText(By by) {
        String s = by.toString();
        int i = s.indexOf(": ");
        return i >= 0 ? s.substring(i + 2) : s;
    }
}
