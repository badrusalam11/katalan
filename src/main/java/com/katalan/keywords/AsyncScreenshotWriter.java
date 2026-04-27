package com.katalan.keywords;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Small helper to perform screenshot file writes asynchronously to avoid blocking
 * keyword execution thread on disk IO when many screenshots are taken.
 */
class AsyncScreenshotWriter {
    private static final Logger logger = LoggerFactory.getLogger(AsyncScreenshotWriter.class);
    private static final AsyncScreenshotWriter INSTANCE = new AsyncScreenshotWriter();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "async-screenshot-writer");
        t.setDaemon(true);
        return t;
    });

    private AsyncScreenshotWriter() {
        // Register shutdown hook to flush pending writes
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                executor.shutdown();
                executor.awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
        }));
    }

    static AsyncScreenshotWriter getInstance() {
        return INSTANCE;
    }

    void submitCopy(Path source, Path destination) {
        executor.submit(() -> {
            try {
                // Perform the actual copy
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                logger.warn("Async screenshot write failed: {} -> {} : {}", source, destination, e.getMessage());
            }
        });
    }
}
