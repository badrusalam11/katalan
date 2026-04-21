package com.kms.katalon.core.webservice.keyword;

import com.kms.katalon.core.model.FailureHandling;
import com.kms.katalon.core.testobject.RequestObject;
import com.kms.katalon.core.testobject.ResponseObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Katalon compatibility stub for WSBuiltInKeywords
 */
public class WSBuiltInKeywords {
    
    private static final Logger logger = LoggerFactory.getLogger(WSBuiltInKeywords.class);
    
    private static void logNotSupported(String method) {
        logger.warn("WS.{} is not fully implemented in Katalan compatibility layer", method);
    }
    
    public static ResponseObject sendRequest(RequestObject request) {
        logNotSupported("sendRequest");
        return new ResponseObject();
    }
    
    public static ResponseObject sendRequest(RequestObject request, FailureHandling fh) {
        return sendRequest(request);
    }
    
    public static ResponseObject sendRequestAndVerify(RequestObject request) {
        return sendRequest(request);
    }
    
    public static ResponseObject sendRequestAndVerify(RequestObject request, FailureHandling fh) {
        return sendRequest(request);
    }
    
    public static boolean verifyResponseStatusCode(ResponseObject response, int statusCode) {
        return response != null && response.getStatusCode() == statusCode;
    }
    
    public static boolean verifyResponseStatusCode(ResponseObject response, int statusCode, FailureHandling fh) {
        return verifyResponseStatusCode(response, statusCode);
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
    }

    public static void comment(Object message) {
        logger.info("[COMMENT] {}", message);
    }
}
