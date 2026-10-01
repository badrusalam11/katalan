package com.kms.katalon.core.testobject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Katalon compatibility interface for the body of a web service request/response.
 * Mirrors com.kms.katalon.core.testobject.HttpBodyContent from Katalon Studio.
 */
public interface HttpBodyContent {

    /** Content type of the body, e.g. "application/json". */
    String getContentType();

    /** Length of the body in bytes, or -1 if unknown. */
    long getContentLength();

    /** Charset the body text is encoded with, e.g. "UTF-8". */
    String getContentEncoding();

    InputStream getInputStream() throws IOException, UnsupportedOperationException;

    void writeTo(OutputStream outstream) throws IOException;
}
