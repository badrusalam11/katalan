package com.kms.katalon.core.testobject.impl;

import com.kms.katalon.core.testobject.HttpBodyContent;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * Katalon compatibility class for a plain-text request body (JSON, XML, form text...).
 * Mirrors com.kms.katalon.core.testobject.impl.HttpTextBodyContent from Katalon Studio:
 * defaults to UTF-8 and "text/plain" when charset/content type aren't given.
 */
public class HttpTextBodyContent implements HttpBodyContent {

    private static final String DEFAULT_CHARSET = "UTF-8";
    private static final String DEFAULT_CONTENT_TYPE = "text/plain";

    private String text;
    private String charset;
    private String contentType;

    public HttpTextBodyContent(String text, String charset, String contentType) {
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        this.text = text;
        this.charset = charset != null ? charset : DEFAULT_CHARSET;
        this.contentType = contentType != null ? contentType : DEFAULT_CONTENT_TYPE;
    }

    public HttpTextBodyContent(String text) {
        this(text, DEFAULT_CHARSET, DEFAULT_CONTENT_TYPE);
    }

    public String getText() {
        return text;
    }

    public String getCharset() {
        return charset;
    }

    private byte[] getBytes() {
        return text.getBytes(Charset.forName(charset));
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public long getContentLength() {
        return getBytes().length;
    }

    @Override
    public String getContentEncoding() {
        return charset;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(getBytes());
    }

    @Override
    public void writeTo(OutputStream outstream) throws IOException {
        outstream.write(getBytes());
        outstream.flush();
    }

    @Override
    public String toString() {
        return text;
    }
}
