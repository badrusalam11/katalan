package com.katalan.core.engine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Captures System.out and System.err during test execution
 */
public class ConsoleOutputCapturer {
    
    private final PrintStream originalOut;
    private final PrintStream originalErr;
    private ByteArrayOutputStream capturedOut;
    private ByteArrayOutputStream capturedErr;
    private PrintStream capturingOut;
    private PrintStream capturingErr;
    private boolean capturing = false;
    
    public ConsoleOutputCapturer() {
        this.originalOut = System.out;
        this.originalErr = System.err;
    }
    
    /**
     * Start capturing console output
     */
    public void startCapture() {
        if (capturing) {
            return;
        }
        
        capturedOut = new ByteArrayOutputStream();
        capturedErr = new ByteArrayOutputStream();
        
        try {
            capturingOut = new PrintStream(capturedOut, true, StandardCharsets.UTF_8.name());
            capturingErr = new PrintStream(capturedErr, true, StandardCharsets.UTF_8.name());
            
            // Create tee streams that write to both original and captured streams
            System.setOut(new TeeOutputStream(originalOut, capturingOut));
            System.setErr(new TeeOutputStream(originalErr, capturingErr));
            
            capturing = true;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 encoding not supported", e);
        }
    }
    
    /**
     * Stop capturing and return the captured output
     */
    public String stopCapture() {
        if (!capturing) {
            return "";
        }
        
        // Restore original streams
        System.setOut(originalOut);
        System.setErr(originalErr);
        
        capturing = false;
        
        // Get captured content
        String outContent = capturedOut.toString(StandardCharsets.UTF_8);
        String errContent = capturedErr.toString(StandardCharsets.UTF_8);
        
        // Combine stdout and stderr
        StringBuilder combined = new StringBuilder();
        if (!outContent.isEmpty()) {
            combined.append(outContent);
        }
        if (!errContent.isEmpty()) {
            if (combined.length() > 0) {
                combined.append("\n");
            }
            combined.append(errContent);
        }
        
        return combined.toString();
    }
    
    /**
     * Get currently captured output without stopping capture
     */
    public String getCapturedOutput() {
        if (!capturing) {
            return "";
        }
        
        String outContent = capturedOut.toString(StandardCharsets.UTF_8);
        String errContent = capturedErr.toString(StandardCharsets.UTF_8);
        
        StringBuilder combined = new StringBuilder();
        if (!outContent.isEmpty()) {
            combined.append(outContent);
        }
        if (!errContent.isEmpty()) {
            if (combined.length() > 0) {
                combined.append("\n");
            }
            combined.append(errContent);
        }
        
        return combined.toString();
    }
    
    /**
     * PrintStream that writes to two output streams (tee)
     */
    private static class TeeOutputStream extends PrintStream {
        private final PrintStream second;
        
        public TeeOutputStream(PrintStream first, PrintStream second) {
            super(first);
            this.second = second;
        }
        
        @Override
        public void write(int b) {
            super.write(b);
            second.write(b);
        }
        
        @Override
        public void write(byte[] buf, int off, int len) {
            super.write(buf, off, len);
            second.write(buf, off, len);
        }
        
        @Override
        public void flush() {
            super.flush();
            second.flush();
        }
        
        @Override
        public void close() {
            super.close();
            second.close();
        }
    }
}
