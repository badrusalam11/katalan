package com.kms.katalon.core.testobject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Katalon compatibility class for ResponseObject (web service response).
 * Header fields are Map&lt;String, List&lt;String&gt;&gt; keyed by the header name exactly as the
 * server sent it, as in Katalon - scripts do getHeaderFields().get("correlationId").get(0).
 */
public class ResponseObject {
    private int statusCode;
    private String responseText;
    private String contentType;
    private String contentCharset;
    private Map<String, List<String>> headerFields = new LinkedHashMap<>();
    private long responseHeaderSize;
    private long responseBodySize;
    private long elapsedTime;
    private long waitingTime;
    private long contentDownloadTime;

    public ResponseObject() {}

    public ResponseObject(String responseText) {
        this.responseText = responseText;
    }

    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }

    public String getResponseText() { return responseText; }
    public void setResponseText(String responseText) { this.responseText = responseText; }

    /** The response body as text (same value as getResponseText()). */
    public String getResponseBodyContent() { return responseText; }
    public void setResponseBodyContent(String body) { this.responseText = body; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getContentCharset() { return contentCharset; }
    public void setContentCharset(String contentCharset) { this.contentCharset = contentCharset; }

    public Map<String, List<String>> getHeaderFields() { return headerFields; }
    public void setHeaderFields(Map<String, List<String>> fields) {
        this.headerFields = fields != null ? fields : new LinkedHashMap<>();
    }

    public long getResponseHeaderSize() { return responseHeaderSize; }
    public void setResponseHeaderSize(long size) { this.responseHeaderSize = size; }

    public long getResponseBodySize() { return responseBodySize; }
    public void setResponseBodySize(long size) { this.responseBodySize = size; }

    public long getResponseSize() { return responseHeaderSize + responseBodySize; }

    public long getElapsedTime() { return elapsedTime; }
    public void setElapsedTime(long elapsedTime) { this.elapsedTime = elapsedTime; }

    public long getWaitingTime() { return waitingTime; }
    public void setWaitingTime(long waitingTime) { this.waitingTime = waitingTime; }

    public long getContentDownloadTime() { return contentDownloadTime; }
    public void setContentDownloadTime(long time) { this.contentDownloadTime = time; }

    public boolean isJsonContentType() {
        return contentType != null && contentType.toLowerCase().contains("json");
    }

    public boolean isXmlContentType() {
        return contentType != null && contentType.toLowerCase().contains("xml");
    }

    public boolean isHtmlContentType() {
        return contentType != null && contentType.toLowerCase().contains("html");
    }

    @Override
    public String toString() {
        return responseText != null ? responseText : "";
    }
}
