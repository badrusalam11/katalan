package com.kms.katalon.core.webservice.keyword;

import com.katalan.keywords.WebService;
import com.kms.katalon.core.exception.StepFailedException;
import com.kms.katalon.core.model.FailureHandling;
import com.kms.katalon.core.testobject.RequestObject;
import com.kms.katalon.core.testobject.ResponseObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collections;

/**
 * Katalon compatibility layer for WSBuiltInKeywords (imported as WS in scripts).
 * The HTTP work is done by {@link WebService}; this class owns the FailureHandling
 * semantics: STOP_ON_FAILURE (default) throws StepFailedException, CONTINUE_ON_FAILURE
 * marks the test case failed and keeps going, OPTIONAL only logs a warning.
 */
public class WSBuiltInKeywords {

    private static final Logger logger = LoggerFactory.getLogger(WSBuiltInKeywords.class);

    private static void logNotSupported(String method) {
        logger.warn("WS.{} is not fully implemented in Katalan compatibility layer", method);
    }

    private static void fail(String message, Throwable cause, FailureHandling fh) {
        FailureHandling mode = fh != null ? fh : FailureHandling.STOP_ON_FAILURE;
        switch (mode) {
            case OPTIONAL:
                logger.warn("[WARNING] {}", message);
                return;
            case CONTINUE_ON_FAILURE:
                logger.error("[FAILED] {}", message);
                com.katalan.core.context.ExecutionContext.getCurrent().recordContinuedFailure(message);
                return;
            default:
                throw cause != null ? new StepFailedException(message, cause) : new StepFailedException(message);
        }
    }

    public static ResponseObject sendRequest(RequestObject request) {
        return sendRequest(request, FailureHandling.STOP_ON_FAILURE);
    }

    public static ResponseObject sendRequest(RequestObject request, FailureHandling fh) {
        try {
            return WebService.sendRequest(request);
        } catch (Exception e) {
            String url = request != null ? request.getRestUrl() : null;
            fail("Unable to send request" + (url != null ? " to '" + url + "'" : "") + " (Root cause: "
                    + e.getClass().getName() + ": " + e.getMessage() + ")", e, fh);
            return null;
        }
    }

    public static ResponseObject sendRequestAndVerify(RequestObject request) {
        return sendRequest(request);
    }

    public static ResponseObject sendRequestAndVerify(RequestObject request, FailureHandling fh) {
        return sendRequest(request, fh);
    }

    public static boolean verifyResponseStatusCode(ResponseObject response, int statusCode) {
        return verifyResponseStatusCode(response, statusCode, FailureHandling.STOP_ON_FAILURE);
    }

    public static boolean verifyResponseStatusCode(ResponseObject response, int statusCode, FailureHandling fh) {
        if (response == null) {
            fail("Response object is null", null, fh);
            return false;
        }
        if (response.getStatusCode() != statusCode) {
            fail("Expected status code is '" + statusCode + "' but actual status code is '"
                    + response.getStatusCode() + "'", null, fh);
            return false;
        }
        logger.info("Response status code is '{}' as expected", statusCode);
        return true;
    }

    public static boolean verifyResponseStatusCodeInRange(ResponseObject response, int fromStatusCode, int toStatusCode) {
        return verifyResponseStatusCodeInRange(response, fromStatusCode, toStatusCode, FailureHandling.STOP_ON_FAILURE);
    }

    public static boolean verifyResponseStatusCodeInRange(ResponseObject response, int fromStatusCode,
            int toStatusCode, FailureHandling fh) {
        if (response == null) {
            fail("Response object is null", null, fh);
            return false;
        }
        int code = response.getStatusCode();
        if (code < fromStatusCode || code > toStatusCode) {
            fail("Status code '" + code + "' is not in range [" + fromStatusCode + ", " + toStatusCode + "]",
                    null, fh);
            return false;
        }
        logger.info("Status code '{}' is in range [{}, {}]", code, fromStatusCode, toStatusCode);
        return true;
    }

    public static boolean containsString(ResponseObject response, String string, boolean useRegex) {
        return containsString(response, string, useRegex, FailureHandling.STOP_ON_FAILURE);
    }

    public static boolean containsString(ResponseObject response, String string, boolean useRegex, FailureHandling fh) {
        String body = response != null ? response.getResponseBodyContent() : null;
        boolean found = body != null && string != null
                && (useRegex ? java.util.regex.Pattern.compile(string).matcher(body).find() : body.contains(string));
        if (!found) {
            fail("Response does not contain '" + string + "'", null, fh);
        }
        return found;
    }

    public static String getResponseBodyContent(ResponseObject response) {
        return response != null ? response.getResponseBodyContent() : null;
    }

    public static Object getElementPropertyValue(Object element, String propertyPath) {
        logNotSupported("getElementPropertyValue");
        return null;
    }

    /** Katalon-style comment logging */
    public static void comment(String message) {
        logger.info("[COMMENT] {}", message);
        com.katalan.core.logging.XmlKeywordLogger.getInstance()
                .logMessage("INFO", message, Collections.emptyMap());
    }

    public static void comment(Object message) {
        String msg = message != null ? message.toString() : "null";
        logger.info("[COMMENT] {}", msg);
        com.katalan.core.logging.XmlKeywordLogger.getInstance()
                .logMessage("INFO", msg, Collections.emptyMap());
    }
}
