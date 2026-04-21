package com.kms.katalon.core.testobject;

import java.util.HashMap;
import java.util.Map;

/**
 * Katalon compatibility class for ResponseObject (web service response)
 */
public class ResponseObject {
    private int statusCode;
    private String responseText;
    private String responseBodyContent;
    private Map<String, String> headerFields = new HashMap<>();
    
    public ResponseObject() {}
    
    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
    
    public String getResponseText() { return responseText; }
    public void setResponseText(String responseText) { this.responseText = responseText; }
    
    public String getResponseBodyContent() { return responseBodyContent; }
    public void setResponseBodyContent(String body) { this.responseBodyContent = body; }
    
    public Map<String, String> getHeaderFields() { return headerFields; }
    public void setHeaderFields(Map<String, String> fields) { this.headerFields = fields; }
}
