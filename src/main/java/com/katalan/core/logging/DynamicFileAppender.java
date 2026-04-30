package com.katalan.core.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.util.FileUtil;

import java.io.IOException;

/**
 * Custom FileAppender that allows setting file path programmatically at runtime.
 * Used for writing console0.log to dynamic report directories.
 */
public class DynamicFileAppender extends FileAppender<ILoggingEvent> {
    
    private static DynamicFileAppender instance;
    private boolean dynamicFileSet = false;
    
    public DynamicFileAppender() {
        instance = this;
    }
    
    public static DynamicFileAppender getInstance() {
        return instance;
    }
    
    /**
     * Dynamically set the output file path at runtime.
     * This closes the current file (if any) and opens a new one.
     */
    public synchronized void setDynamicFile(String filePath) {
        if (dynamicFileSet && filePath.equals(getFile())) {
            // Same file, no need to reopen
            return;
        }
        
        // Close current file
        if (dynamicFileSet) {
            stop();
        }
        
        // Set new file
        setFile(filePath);
        setAppend(false); // Overwrite each test run
        
        // Create parent directories if needed
        FileUtil.createMissingParentDirectories(new java.io.File(filePath));
        
        // Start writing to new file
        start();
        dynamicFileSet = true;
        
        addInfo("DynamicFileAppender: Writing console logs to: " + filePath);
    }
    
    /**
     * Override to prevent writing before dynamic file is set
     */
    @Override
    protected void append(ILoggingEvent eventObject) {
        if (dynamicFileSet) {
            super.append(eventObject);
        }
        // If not set, logs just go to CONSOLE appender (no error)
    }
}
