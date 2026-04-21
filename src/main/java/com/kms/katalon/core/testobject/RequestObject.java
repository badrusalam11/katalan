package com.kms.katalon.core.testobject;

import java.util.HashMap;
import java.util.Map;

/**
 * Katalon compatibility class for RequestObject (web service request)
 */
public class RequestObject {
    private String objectId;
    private String restUrl;
    private String restRequestMethod = "GET";
    private String httpBody;
    private Map<String, String> httpHeaderProperties = new HashMap<>();
    
    public RequestObject() {}
    
    public RequestObject(String objectId) {
        this.objectId = objectId;
    }
    
    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId; }
    
    public String getRestUrl() { return restUrl; }
    public void setRestUrl(String restUrl) { this.restUrl = restUrl; }
    
    public String getRestRequestMethod() { return restRequestMethod; }
    public void setRestRequestMethod(String method) { this.restRequestMethod = method; }
    
    public String getHttpBody() { return httpBody; }
    public void setHttpBody(String httpBody) { this.httpBody = httpBody; }
    
    public Map<String, String> getHttpHeaderProperties() { return httpHeaderProperties; }
    public void setHttpHeaderProperties(Map<String, String> props) { this.httpHeaderProperties = props; }
}
