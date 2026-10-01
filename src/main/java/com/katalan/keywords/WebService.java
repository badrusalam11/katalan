package com.katalan.keywords;

import com.kms.katalon.core.testobject.HttpBodyContent;
import com.kms.katalon.core.testobject.RequestObject;
import com.kms.katalon.core.testobject.ResponseObject;
import com.kms.katalon.core.testobject.TestObjectProperty;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Real web service engine behind the Katalon {@code WS.sendRequest(...)} keyword.
 *
 * Mirrors Katalon Studio's defaults for a RESTful RequestObject:
 * - SSL certificates are not verified (Katalon's default "bypass" certificate option),
 * - redirects are followed only when the request asks for it,
 * - no cookies are carried between requests (Katalon builds a fresh client per request),
 * - no connect/socket timeout unless configured.
 *
 * Failures throw; FailureHandling is applied by the compat WSBuiltInKeywords layer.
 */
public class WebService {

    private static final Logger logger = LoggerFactory.getLogger(WebService.class);

    private static volatile CloseableHttpClient client;

    private WebService() {}

    private static CloseableHttpClient getClient() throws Exception {
        if (client == null) {
            synchronized (WebService.class) {
                if (client == null) {
                    client = HttpClients.custom()
                            .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                                    .setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
                                            .setSslContext(SSLContextBuilder.create()
                                                    .loadTrustMaterial(TrustAllStrategy.INSTANCE)
                                                    .build())
                                            .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                                            .build())
                                    .setMaxConnTotal(50)
                                    .setMaxConnPerRoute(20)
                                    .build())
                            .disableCookieManagement()
                            .useSystemProperties()
                            .build();
                }
            }
        }
        return client;
    }

    public static ResponseObject sendRequest(RequestObject request) throws Exception {
        if (request == null) {
            throw new IllegalArgumentException("Request object cannot be null");
        }
        if (RequestObject.SERVICE_TYPE_SOAP.equalsIgnoreCase(request.getServiceType())) {
            throw new UnsupportedOperationException("SOAP requests are not supported by katalan yet");
        }
        String url = buildUrl(request);
        String method = request.getRestRequestMethod() != null
                ? request.getRestRequestMethod().trim().toUpperCase() : "GET";

        HttpUriRequestBase httpRequest = new HttpUriRequestBase(method, URI.create(url));
        httpRequest.setConfig(RequestConfig.custom()
                .setRedirectsEnabled(request.isFollowRedirects())
                .build());

        boolean hasContentType = false;
        for (TestObjectProperty header : request.getHttpHeaderProperties()) {
            if (header == null || !header.isActive() || header.getName() == null
                    || header.getName().trim().isEmpty()) {
                continue;
            }
            String name = header.getName().trim();
            // HttpClient computes these from the entity itself; setting them twice is a protocol error.
            if (name.equalsIgnoreCase("Content-Length") || name.equalsIgnoreCase("Transfer-Encoding")) {
                continue;
            }
            if (name.equalsIgnoreCase("Content-Type")) {
                hasContentType = true;
            }
            httpRequest.addHeader(name, header.getValue() != null ? header.getValue() : "");
        }

        HttpBodyContent body = request.getBodyContent();
        if (body != null) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            body.writeTo(out);
            ContentType entityType = null;
            if (!hasContentType && body.getContentType() != null) {
                entityType = ContentType.parseLenient(withCharset(body.getContentType(), body.getContentEncoding()));
            }
            httpRequest.setEntity(new ByteArrayEntity(out.toByteArray(), entityType));
        }

        logger.info("WS {} {}", method, url);
        long start = System.currentTimeMillis();
        return getClient().execute(httpRequest, httpResponse -> {
            long headersAt = System.currentTimeMillis();
            ResponseObject response = new ResponseObject();
            response.setStatusCode(httpResponse.getCode());

            Map<String, List<String>> headerFields = new LinkedHashMap<>();
            long headerSize = 0;
            for (Header h : httpResponse.getHeaders()) {
                headerFields.computeIfAbsent(h.getName(), k -> new ArrayList<>()).add(h.getValue());
                headerSize += h.getName().length() + h.getValue().length() + 4;
            }
            response.setHeaderFields(headerFields);
            response.setResponseHeaderSize(headerSize);

            HttpEntity entity = httpResponse.getEntity();
            String text = "";
            if (entity != null) {
                ContentType contentType = ContentType.parseLenient(entity.getContentType());
                Charset charset = contentType != null && contentType.getCharset() != null
                        ? contentType.getCharset() : StandardCharsets.UTF_8;
                byte[] bytes = EntityUtils.toByteArray(entity);
                text = new String(bytes, charset);
                response.setResponseBodySize(bytes.length);
                response.setContentType(entity.getContentType());
                response.setContentCharset(charset.name());
            }
            response.setResponseText(text);

            long end = System.currentTimeMillis();
            response.setWaitingTime(headersAt - start);
            response.setContentDownloadTime(end - headersAt);
            response.setElapsedTime(end - start);
            logger.info("WS {} {} -> {} ({} ms)", method, url, response.getStatusCode(), response.getElapsedTime());
            return response;
        });
    }

    private static String buildUrl(RequestObject request) {
        String url = request.getRestUrl();
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("Request URL cannot be empty");
        }
        url = url.trim();
        StringBuilder query = new StringBuilder();
        for (TestObjectProperty param : request.getRestParameters()) {
            if (param == null || !param.isActive() || param.getName() == null || param.getName().isEmpty()) {
                continue;
            }
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(URLEncoder.encode(param.getName(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(param.getValue() != null ? param.getValue() : "", StandardCharsets.UTF_8));
        }
        if (query.length() > 0) {
            url += (url.contains("?") ? "&" : "?") + query;
        }
        return url;
    }

    private static String withCharset(String contentType, String charset) {
        if (charset == null || contentType.toLowerCase().contains("charset=")) {
            return contentType;
        }
        return contentType + "; charset=" + charset;
    }
}
