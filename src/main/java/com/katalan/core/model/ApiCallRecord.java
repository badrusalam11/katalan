package com.katalan.core.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One HTTP call made through {@code WS.sendRequest(...)} while a test case ran.
 * Captured by the web service engine so reports can show the request (URL, method,
 * headers, body) next to the response the test case received.
 */
public class ApiCallRecord {

    private Instant timestamp;
    private String method;
    private String url;
    /** Request headers as "name", "value" pairs, in the order they were sent. */
    private List<String[]> requestHeaders = new ArrayList<>();
    private String requestBody;
    private int statusCode;
    private Map<String, List<String>> responseHeaders;
    private String responseBody;
    private long elapsedMs;
    /** Set when the request could not be sent or no response was received. */
    private String error;

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public List<String[]> getRequestHeaders() { return requestHeaders; }
    public void addRequestHeader(String name, String value) { requestHeaders.add(new String[]{name, value}); }

    public String getRequestBody() { return requestBody; }
    public void setRequestBody(String requestBody) { this.requestBody = requestBody; }

    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }

    public Map<String, List<String>> getResponseHeaders() { return responseHeaders; }
    public void setResponseHeaders(Map<String, List<String>> responseHeaders) { this.responseHeaders = responseHeaders; }

    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }

    public long getElapsedMs() { return elapsedMs; }
    public void setElapsedMs(long elapsedMs) { this.elapsedMs = elapsedMs; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
