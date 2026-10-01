package com.kms.katalon.core.testobject;

import com.kms.katalon.core.testobject.impl.HttpTextBodyContent;

import java.util.ArrayList;
import java.util.List;

/**
 * Katalon compatibility class for RequestObject (web service request).
 * Mirrors Katalon Studio's shape: it is a TestObject, headers/parameters are
 * List&lt;TestObjectProperty&gt; (scripts call getHttpHeaderProperties().add(...)),
 * and the body is an HttpBodyContent whose text getHttpBody() still exposes.
 */
public class RequestObject extends TestObject {

    public static final String SERVICE_TYPE_RESTFUL = "RESTful";
    public static final String SERVICE_TYPE_SOAP = "SOAP";

    private String name;
    private String serviceType = SERVICE_TYPE_RESTFUL;
    private String restUrl;
    private String restRequestMethod = "GET";
    private List<TestObjectProperty> httpHeaderProperties = new ArrayList<>();
    private List<TestObjectProperty> restParameters = new ArrayList<>();
    private String httpBody;
    private HttpBodyContent bodyContent;
    private boolean followRedirects;

    public RequestObject() {
        super();
    }

    public RequestObject(String objectId) {
        super(objectId);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }

    public String getRestUrl() { return restUrl; }
    public void setRestUrl(String restUrl) { this.restUrl = restUrl; }

    public String getRestRequestMethod() { return restRequestMethod; }
    public void setRestRequestMethod(String method) { this.restRequestMethod = method; }

    public List<TestObjectProperty> getHttpHeaderProperties() { return httpHeaderProperties; }
    public void setHttpHeaderProperties(List<TestObjectProperty> props) {
        this.httpHeaderProperties = props != null ? props : new ArrayList<>();
    }

    public List<TestObjectProperty> getRestParameters() { return restParameters; }
    public void setRestParameters(List<TestObjectProperty> params) {
        this.restParameters = params != null ? params : new ArrayList<>();
    }

    /** Text of the body - kept in sync with setBodyContent() like Katalon does. */
    public String getHttpBody() { return httpBody; }

    public void setHttpBody(String httpBody) {
        this.httpBody = httpBody;
        this.bodyContent = httpBody != null ? new HttpTextBodyContent(httpBody) : null;
    }

    public HttpBodyContent getBodyContent() { return bodyContent; }

    public void setBodyContent(HttpBodyContent bodyContent) {
        this.bodyContent = bodyContent;
        if (bodyContent instanceof HttpTextBodyContent) {
            this.httpBody = ((HttpTextBodyContent) bodyContent).getText();
        } else {
            this.httpBody = null;
        }
    }

    public boolean isFollowRedirects() { return followRedirects; }
    public void setFollowRedirects(boolean followRedirects) { this.followRedirects = followRedirects; }
}
