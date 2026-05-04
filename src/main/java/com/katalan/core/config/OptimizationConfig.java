package com.katalan.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Resource Optimization Configuration
 * 
 * Manages all resource optimization settings for Katalan runner:
 * - WebDriver pooling
 * - Memory management
 * - Screenshot optimization
 * - Logging optimization
 * - Cache settings
 * - Thread pool configuration
 * - Circuit breaker settings
 * 
 * Configuration sources (in priority order):
 * 1. System properties (-Dkatalan.optimization.enabled=true)
 * 2. katalan.properties file in project root
 * 3. Default values
 * 
 * Usage:
 * <pre>
 * // Load configuration
 * OptimizationConfig config = OptimizationConfig.load(projectPath);
 * 
 * // Access settings
 * if (config.isOptimizationEnabled()) {
 *     WebDriverPool.setMaxPoolSize(config.getBrowserPoolMaxSize());
 *     WebDriverPool.setReuseEnabled(config.isBrowserReuseEnabled());
 * }
 * </pre>
 */
public class OptimizationConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(OptimizationConfig.class);
    
    // Global optimization toggle
    private boolean optimizationEnabled = false;
    
    // Browser pooling settings
    private boolean browserPoolEnabled = false;
    private int browserPoolMaxSize = 3;
    private boolean browserReuseWindows = true;
    private long browserIdleTimeoutSeconds = 60;
    private CleanupStrategy browserCleanupStrategy = CleanupStrategy.IMMEDIATE;
    
    // Memory management settings
    private int memoryMaxHeapUsagePercent = 80;
    private int memoryWarningThresholdPercent = 70;
    private boolean memoryGcAfterTest = false;
    
    // Screenshot optimization settings
    private ScreenshotFormat screenshotFormat = ScreenshotFormat.PNG;
    private int screenshotQuality = 85;
    private int screenshotMaxWidth = 1920;
    private boolean screenshotCompression = false;
    private boolean screenshotSkipDuplicates = false;
    
    // Logging optimization settings
    private boolean loggingStreaming = false;
    private int loggingBufferSize = 1000;
    private long loggingFlushIntervalSeconds = 5;
    private boolean loggingCompression = false;
    
    // Cache settings
    private int cacheObjectRepositoryMaxSize = 500;
    private int cacheStepDefinitionsMaxSize = 1000;
    private int cacheStepMatchMaxSize = 1024;
    private long cacheTtlSeconds = 3600;
    
    // Thread pool settings
    private int threadsMaxPoolSize = 10;
    private int threadsCorePoolSize = 3;
    private int threadsQueueCapacity = 100;
    private long threadsKeepAliveSeconds = 60;
    
    // Circuit breaker settings
    private boolean circuitBreakerEnabled = false;
    private int circuitBreakerFailureThreshold = 5;
    private long circuitBreakerTimeoutSeconds = 30;
    private long circuitBreakerHalfOpenDelaySeconds = 10;
    
    /**
     * Load configuration from project path
     * 
     * HYBRID APPROACH (priority order):
     * 1. Global: {jar-dir}/katalan.properties (default for all projects)
     * 2. Project: {project}/katalan.properties (overrides global)
     * 3. System properties: -Dkatalan.xxx=yyy (overrides all)
     */
    public static OptimizationConfig load(Path projectPath) {
        OptimizationConfig config = new OptimizationConfig();
        
        // 1. Load GLOBAL default config from jar location
        Path jarLocation = getJarLocation();
        if (jarLocation != null) {
            Path globalProps = jarLocation.resolve("katalan.properties");
            if (Files.exists(globalProps)) {
                try (InputStream in = Files.newInputStream(globalProps)) {
                    Properties props = new Properties();
                    props.load(in);
                    config.loadFromProperties(props);
                    logger.info("📦 Loaded GLOBAL config: {}", globalProps);
                } catch (IOException e) {
                    logger.debug("Could not load global config: {}", e.getMessage());
                }
            }
        }
        
        // 2. Load PROJECT-SPECIFIC config (overrides global)
        Path propsFile = projectPath.resolve("katalan.properties");
        if (Files.exists(propsFile)) {
            try (InputStream in = Files.newInputStream(propsFile)) {
                Properties props = new Properties();
                props.load(in);
                config.loadFromProperties(props);
                logger.info("🎯 Loaded PROJECT config: {}", propsFile);
            } catch (IOException e) {
                logger.warn("⚠️  Failed to load project config: {}", e.getMessage());
            }
        } else {
            logger.info("No project katalan.properties found (using defaults)");
        }
        
        // 3. Override with system properties (highest priority)
        config.loadFromSystemProperties();
        
        // Log configuration
        config.logConfiguration();
        
        return config;
    }
    
    /**
     * Get jar location directory
     */
    private static Path getJarLocation() {
        try {
            String location = OptimizationConfig.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()
                .getPath();
            
            Path path = Path.of(location);
            
            // If JAR file, return parent dir
            if (location.endsWith(".jar")) {
                return path.getParent();
            }
            
            // If IDE (target/classes), return project root
            if (location.contains("target/classes")) {
                return path.getParent().getParent();
            }
            
            return path.getParent();
        } catch (Exception e) {
            logger.debug("Could not determine jar location: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Load configuration from Properties object
     */
    private void loadFromProperties(Properties props) {
        // Global optimization
        optimizationEnabled = getBooleanProperty(props, "katalan.optimization.enabled", false);
        
        // Browser pooling
        browserPoolEnabled = getBooleanProperty(props, "katalan.browser.pool.enabled", false);
        browserPoolMaxSize = getIntProperty(props, "katalan.browser.pool.maxSize", 3);
        browserReuseWindows = getBooleanProperty(props, "katalan.browser.pool.reuseWindows", true);
        browserIdleTimeoutSeconds = getLongProperty(props, "katalan.browser.pool.idleTimeout", 60);
        browserCleanupStrategy = getEnumProperty(props, "katalan.browser.cleanup.strategy", 
            CleanupStrategy.class, CleanupStrategy.IMMEDIATE);
        
        // Memory management
        memoryMaxHeapUsagePercent = getIntProperty(props, "katalan.memory.maxHeapUsage", 80);
        memoryWarningThresholdPercent = getIntProperty(props, "katalan.memory.warningThreshold", 70);
        memoryGcAfterTest = getBooleanProperty(props, "katalan.memory.gcAfterTest", false);
        
        // Screenshot optimization
        screenshotFormat = getEnumProperty(props, "katalan.screenshot.format", 
            ScreenshotFormat.class, ScreenshotFormat.PNG);
        screenshotQuality = getIntProperty(props, "katalan.screenshot.quality", 85);
        screenshotMaxWidth = getIntProperty(props, "katalan.screenshot.maxWidth", 1920);
        screenshotCompression = getBooleanProperty(props, "katalan.screenshot.compression", false);
        screenshotSkipDuplicates = getBooleanProperty(props, "katalan.screenshot.skipDuplicates", false);
        
        // Logging optimization
        loggingStreaming = getBooleanProperty(props, "katalan.logging.streaming", false);
        loggingBufferSize = getIntProperty(props, "katalan.logging.bufferSize", 1000);
        loggingFlushIntervalSeconds = getLongProperty(props, "katalan.logging.flushInterval", 5);
        loggingCompression = getBooleanProperty(props, "katalan.logging.compression", false);
        
        // Cache settings
        cacheObjectRepositoryMaxSize = getIntProperty(props, "katalan.cache.objectRepository.maxSize", 500);
        cacheStepDefinitionsMaxSize = getIntProperty(props, "katalan.cache.stepDefinitions.maxSize", 1000);
        cacheStepMatchMaxSize = getIntProperty(props, "katalan.cache.stepMatch.maxSize", 1024);
        cacheTtlSeconds = getLongProperty(props, "katalan.cache.ttl", 3600);
        
        // Thread pool
        threadsMaxPoolSize = getIntProperty(props, "katalan.threads.maxPoolSize", 10);
        threadsCorePoolSize = getIntProperty(props, "katalan.threads.corePoolSize", 3);
        threadsQueueCapacity = getIntProperty(props, "katalan.threads.queueCapacity", 100);
        threadsKeepAliveSeconds = getLongProperty(props, "katalan.threads.keepAlive", 60);
        
        // Circuit breaker
        circuitBreakerEnabled = getBooleanProperty(props, "katalan.circuitBreaker.enabled", false);
        circuitBreakerFailureThreshold = getIntProperty(props, "katalan.circuitBreaker.failureThreshold", 5);
        circuitBreakerTimeoutSeconds = getLongProperty(props, "katalan.circuitBreaker.timeout", 30);
        circuitBreakerHalfOpenDelaySeconds = getLongProperty(props, "katalan.circuitBreaker.halfOpenDelay", 10);
    }
    
    /**
     * Load configuration from system properties
     */
    private void loadFromSystemProperties() {
        // Check each property and override if set
        String prop;
        
        if ((prop = System.getProperty("katalan.optimization.enabled")) != null) {
            optimizationEnabled = Boolean.parseBoolean(prop);
        }
        
        if ((prop = System.getProperty("katalan.browser.pool.enabled")) != null) {
            browserPoolEnabled = Boolean.parseBoolean(prop);
        }
        
        if ((prop = System.getProperty("katalan.browser.pool.maxSize")) != null) {
            browserPoolMaxSize = Integer.parseInt(prop);
        }
        
        // Add more system property overrides as needed...
    }
    
    /**
     * Log configuration summary
     */
    private void logConfiguration() {
        if (!optimizationEnabled) {
            logger.info("⚠️  Resource optimization: DISABLED (legacy mode)");
            return;
        }
        
        logger.info("🚀 Resource optimization: ENABLED");
        logger.info("   Browser Pool: {} (max: {}, reuse: {}, idle: {}s)", 
            browserPoolEnabled ? "ENABLED" : "DISABLED",
            browserPoolMaxSize, browserReuseWindows, browserIdleTimeoutSeconds);
        logger.info("   Memory: max {}%, warning {}%, gc after test: {}", 
            memoryMaxHeapUsagePercent, memoryWarningThresholdPercent, memoryGcAfterTest);
        logger.info("   Screenshot: format={}, quality={}, maxWidth={}", 
            screenshotFormat, screenshotQuality, screenshotMaxWidth);
        logger.info("   Logging: streaming={}, buffer={}, flush={}s, compression={}", 
            loggingStreaming, loggingBufferSize, loggingFlushIntervalSeconds, loggingCompression);
        logger.info("   Cache: objects={}, steps={}, matches={}", 
            cacheObjectRepositoryMaxSize, cacheStepDefinitionsMaxSize, cacheStepMatchMaxSize);
        logger.info("   Threads: max={}, core={}, queue={}", 
            threadsMaxPoolSize, threadsCorePoolSize, threadsQueueCapacity);
        logger.info("   Circuit Breaker: {}", 
            circuitBreakerEnabled ? "ENABLED" : "DISABLED");
    }
    
    // Helper methods for property parsing
    
    private boolean getBooleanProperty(Properties props, String key, boolean defaultValue) {
        String value = props.getProperty(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }
    
    private int getIntProperty(Properties props, String key, int defaultValue) {
        String value = props.getProperty(key);
        try {
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for {}: {}", key, value);
            return defaultValue;
        }
    }
    
    private long getLongProperty(Properties props, String key, long defaultValue) {
        String value = props.getProperty(key);
        try {
            return value != null ? Long.parseLong(value) : defaultValue;
        } catch (NumberFormatException e) {
            logger.warn("Invalid long value for {}: {}", key, value);
            return defaultValue;
        }
    }
    
    private <T extends Enum<T>> T getEnumProperty(Properties props, String key, 
                                                   Class<T> enumClass, T defaultValue) {
        String value = props.getProperty(key);
        try {
            return value != null ? Enum.valueOf(enumClass, value.toUpperCase()) : defaultValue;
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid enum value for {}: {}", key, value);
            return defaultValue;
        }
    }
    
    // Getters
    
    public boolean isOptimizationEnabled() {
        return optimizationEnabled;
    }
    
    public boolean isBrowserPoolEnabled() {
        return browserPoolEnabled;
    }
    
    public int getBrowserPoolMaxSize() {
        return browserPoolMaxSize;
    }
    
    public boolean isBrowserReuseWindows() {
        return browserReuseWindows;
    }
    
    public long getBrowserIdleTimeoutSeconds() {
        return browserIdleTimeoutSeconds;
    }
    
    public CleanupStrategy getBrowserCleanupStrategy() {
        return browserCleanupStrategy;
    }
    
    public int getMemoryMaxHeapUsagePercent() {
        return memoryMaxHeapUsagePercent;
    }
    
    public int getMemoryWarningThresholdPercent() {
        return memoryWarningThresholdPercent;
    }
    
    public boolean isMemoryGcAfterTest() {
        return memoryGcAfterTest;
    }
    
    public ScreenshotFormat getScreenshotFormat() {
        return screenshotFormat;
    }
    
    public int getScreenshotQuality() {
        return screenshotQuality;
    }
    
    public int getScreenshotMaxWidth() {
        return screenshotMaxWidth;
    }
    
    public boolean isScreenshotCompression() {
        return screenshotCompression;
    }
    
    public boolean isScreenshotSkipDuplicates() {
        return screenshotSkipDuplicates;
    }
    
    public boolean isLoggingStreaming() {
        return loggingStreaming;
    }
    
    public int getLoggingBufferSize() {
        return loggingBufferSize;
    }
    
    public long getLoggingFlushIntervalSeconds() {
        return loggingFlushIntervalSeconds;
    }
    
    public boolean isLoggingCompression() {
        return loggingCompression;
    }
    
    public int getCacheObjectRepositoryMaxSize() {
        return cacheObjectRepositoryMaxSize;
    }
    
    public int getCacheStepDefinitionsMaxSize() {
        return cacheStepDefinitionsMaxSize;
    }
    
    public int getCacheStepMatchMaxSize() {
        return cacheStepMatchMaxSize;
    }
    
    public long getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }
    
    public int getThreadsMaxPoolSize() {
        return threadsMaxPoolSize;
    }
    
    public int getThreadsCorePoolSize() {
        return threadsCorePoolSize;
    }
    
    public int getThreadsQueueCapacity() {
        return threadsQueueCapacity;
    }
    
    public long getThreadsKeepAliveSeconds() {
        return threadsKeepAliveSeconds;
    }
    
    public boolean isCircuitBreakerEnabled() {
        return circuitBreakerEnabled;
    }
    
    public int getCircuitBreakerFailureThreshold() {
        return circuitBreakerFailureThreshold;
    }
    
    public long getCircuitBreakerTimeoutSeconds() {
        return circuitBreakerTimeoutSeconds;
    }
    
    public long getCircuitBreakerHalfOpenDelaySeconds() {
        return circuitBreakerHalfOpenDelaySeconds;
    }
    
    // Enums
    
    public enum CleanupStrategy {
        IMMEDIATE,  // Close driver immediately after test
        DELAYED,    // Close driver after idle timeout
        LAZY        // Only close on pool full or shutdown
    }
    
    public enum ScreenshotFormat {
        PNG,
        JPEG,
        WEBP
    }
}
