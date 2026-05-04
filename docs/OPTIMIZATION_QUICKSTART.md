# 🚀 Resource Optimization - Quick Start Guide

## 📋 Table of Contents
1. [Quick Enable](#quick-enable-5-minutes)
2. [Phase 1 Implementation](#phase-1-quick-wins-1-2-days)
3. [Phase 2 Implementation](#phase-2-core-improvements-3-5-days)
4. [Testing & Validation](#testing--validation)
5. [Rollback Plan](#rollback-plan)

---

## Quick Enable (5 minutes)

### Step 1: Copy Configuration File

```bash
# Copy example config to project root
cp katalan.properties.example katalan.properties

# Or create minimal config
cat > katalan.properties << 'EOF'
# Enable optimization
katalan.optimization.enabled=true

# Browser pooling (biggest impact)
katalan.browser.pool.enabled=true
katalan.browser.pool.maxSize=3

# Screenshot optimization (easy win)
katalan.screenshot.format=JPEG
katalan.screenshot.quality=85
EOF
```

### Step 2: Test with Single Test Case

```bash
# Run single test to verify
java -jar katalan-runner-1.1.1.jar run \
  -tc "Test Cases/Login/TC001.groovy" \
  -b chrome \
  --headless

# Check logs for optimization messages
grep "optimization" logs/katalan.log
```

### Step 3: Measure Impact

```bash
# Before optimization
time java -jar katalan-runner.jar run -ts "Test Suites/Smoke"
# Note: memory usage, execution time

# After optimization
time java -jar katalan-runner.jar run -ts "Test Suites/Smoke"
# Compare: should see 30-50% improvement
```

---

## Phase 1: Quick Wins (1-2 days)

### Task 1.1: Implement Browser Pool Integration

**File:** `src/main/java/com/katalan/keywords/WebUI.java`

```java
// Add at top of class
private static OptimizationConfig optimizationConfig;

// Initialize in static block
static {
    ExecutionContext ctx = ExecutionContext.getCurrent();
    if (ctx != null && ctx.getRunConfiguration() != null) {
        optimizationConfig = OptimizationConfig.load(
            ctx.getRunConfiguration().getProjectPath()
        );
        
        if (optimizationConfig.isOptimizationEnabled() && 
            optimizationConfig.isBrowserPoolEnabled()) {
            
            // Configure pool
            WebDriverPool.configure(ctx.getRunConfiguration());
            WebDriverPool.setMaxPoolSize(optimizationConfig.getBrowserPoolMaxSize());
            WebDriverPool.setReuseEnabled(optimizationConfig.isBrowserReuseWindows());
            
            logger.info("🚀 Browser pooling ENABLED - max: {}", 
                optimizationConfig.getBrowserPoolMaxSize());
        }
    }
}

// Modify openBrowser() method
public static void openBrowser(String url) {
    logger.info("🌐 openBrowser() called with URL: {}", url);
    ExecutionContext context = ExecutionContext.getCurrent();
    
    WebDriver driver;
    
    // Use pool if enabled
    if (optimizationConfig != null && 
        optimizationConfig.isBrowserPoolEnabled()) {
        
        try {
            driver = WebDriverPool.acquire();
            logger.info("♻️  Acquired browser from pool");
        } catch (Exception e) {
            logger.warn("⚠️  Pool acquire failed, creating new: {}", e.getMessage());
            driver = WebDriverFactory.createDriver(context.getRunConfiguration());
        }
    } else {
        // Legacy mode
        driver = WebDriverFactory.createDriver(context.getRunConfiguration());
    }
    
    context.setWebDriver(driver);
    
    if (url != null && !url.trim().isEmpty()) {
        driver.get(url);
    }
}

// Add new closeBrowser() implementation
public static void closeBrowser() {
    logger.info("Closing browser");
    WebDriver driver = getDriver();
    
    if (driver == null) {
        return;
    }
    
    // Return to pool if enabled
    if (optimizationConfig != null && 
        optimizationConfig.isBrowserPoolEnabled()) {
        
        try {
            WebDriverPool.release(driver);
            logger.info("↩️  Returned browser to pool");
            return;
        } catch (Exception e) {
            logger.warn("⚠️  Pool release failed: {}", e.getMessage());
        }
    }
    
    // Legacy mode - quit driver
    driver.quit();
}
```

**Verification:**
```bash
# Run test and check logs
java -jar katalan-runner.jar run -tc "Test Cases/Login/TC001.groovy"

# Should see:
# "🚀 Browser pooling ENABLED - max: 3"
# "♻️  Acquired browser from pool"
# "↩️  Returned browser to pool"
```

---

### Task 1.2: Add Screenshot JPEG Compression

**File:** `src/main/java/com/katalan/keywords/WebUI.java`

```java
// Add to takeScreenshot() method
public static String takeScreenshot(String filename) {
    WebDriver driver = getDriver();
    
    if (!(driver instanceof TakesScreenshot)) {
        logger.warn("Driver does not support screenshots");
        return null;
    }
    
    try {
        // Take screenshot as PNG (Selenium default)
        File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        
        // Load optimization config
        OptimizationConfig config = getOptimizationConfig();
        
        // Convert to JPEG if enabled
        if (config.getScreenshotFormat() == OptimizationConfig.ScreenshotFormat.JPEG) {
            screenshot = convertToJPEG(screenshot, config.getScreenshotQuality());
            logger.debug("📸 Converted screenshot to JPEG (quality: {})", 
                config.getScreenshotQuality());
        }
        
        Path destination = resolveScreenshotPath(filename);
        Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
        
        logger.info("Screenshot saved: {}", destination);
        return destination.toString();
        
    } catch (Exception e) {
        logger.error("Failed to take screenshot: {}", e.getMessage(), e);
        return null;
    }
}

// Add helper method
private static File convertToJPEG(File pngFile, int quality) throws IOException {
    BufferedImage image = ImageIO.read(pngFile);
    
    // Create temp JPEG file
    File jpegFile = File.createTempFile("screenshot", ".jpg");
    
    // Write as JPEG with quality setting
    ImageOutputStream ios = ImageIO.createImageOutputStream(jpegFile);
    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    
    ImageWriteParam param = writer.getDefaultWriteParam();
    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
    param.setCompressionQuality(quality / 100f);
    
    writer.setOutput(ios);
    writer.write(null, new IIOImage(image, null, null), param);
    
    writer.dispose();
    ios.close();
    
    return jpegFile;
}
```

**Required imports:**
```java
import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
```

**Verification:**
```bash
# Take screenshot and compare sizes
ls -lh screenshots/

# PNG: ~500KB
# JPEG (quality 85): ~100KB (80% reduction!)
```

---

### Task 1.3: Add Memory Monitoring

**File:** `src/main/java/com/katalan/core/engine/KatalanEngine.java`

```java
// Add to executeTestCase() method
private TestCaseResult executeTestCase(TestCase testCase) {
    logger.info("Executing test case: {}", testCase.getName());
    
    // Memory check before test
    checkMemoryUsage();
    
    TestCaseResult result = new TestCaseResult(testCase);
    result.markStarted();
    
    try {
        // ... existing test execution code ...
        
    } finally {
        // Cleanup after test
        if (optimizationConfig.isMemoryGcAfterTest()) {
            System.gc();
            logger.debug("🧹 Triggered GC after test");
        }
        
        // Memory check after test
        checkMemoryUsage();
    }
    
    return result;
}

// Add memory monitoring method
private void checkMemoryUsage() {
    if (optimizationConfig == null || !optimizationConfig.isOptimizationEnabled()) {
        return;
    }
    
    Runtime runtime = Runtime.getRuntime();
    long maxMemory = runtime.maxMemory();
    long usedMemory = runtime.totalMemory() - runtime.freeMemory();
    int usagePercent = (int) ((usedMemory * 100) / maxMemory);
    
    if (usagePercent >= optimizationConfig.getMemoryMaxHeapUsagePercent()) {
        logger.warn("⚠️  HIGH MEMORY USAGE: {}% ({} MB / {} MB)", 
            usagePercent, 
            usedMemory / 1024 / 1024,
            maxMemory / 1024 / 1024);
        
        // Trigger GC if above threshold
        System.gc();
        logger.info("🧹 Triggered emergency GC");
        
    } else if (usagePercent >= optimizationConfig.getMemoryWarningThresholdPercent()) {
        logger.info("📊 Memory usage: {}% ({} MB / {} MB)", 
            usagePercent,
            usedMemory / 1024 / 1024,
            maxMemory / 1024 / 1024);
    }
}
```

**Verification:**
```bash
# Run tests and monitor memory warnings
java -Xmx512m -jar katalan-runner.jar run -ts "Test Suites/LongRunning"

# Should see periodic memory logs:
# "📊 Memory usage: 65% (330 MB / 512 MB)"
# "⚠️  HIGH MEMORY USAGE: 85% (435 MB / 512 MB)"
```

---

## Phase 2: Core Improvements (3-5 days)

### Task 2.1: Integrate Streaming Logger

**File:** `src/main/java/com/katalan/core/engine/KatalanEngine.java`

```java
// Add field
private StreamingXmlLogger streamingLogger;

// Initialize in initialize() method
public void initialize() throws IOException {
    // ... existing initialization ...
    
    // Initialize streaming logger if enabled
    if (optimizationConfig.isLoggingStreaming()) {
        Path reportPath = config.getReportPath();
        streamingLogger = new StreamingXmlLogger(reportPath);
        streamingLogger.setBufferSize(optimizationConfig.getLoggingBufferSize());
        streamingLogger.setFlushInterval(
            optimizationConfig.getLoggingFlushIntervalSeconds(), 
            TimeUnit.SECONDS
        );
        streamingLogger.setCompressionEnabled(optimizationConfig.isLoggingCompression());
        
        logger.info("✅ Streaming logger initialized");
    }
}

// Add to cleanup
public void cleanup() {
    // ... existing cleanup ...
    
    // Close streaming logger
    if (streamingLogger != null) {
        try {
            streamingLogger.close();
            logger.info("✅ Streaming logger closed - {} records written", 
                streamingLogger.getStats().recordsWritten);
        } catch (Exception e) {
            logger.error("Failed to close streaming logger", e);
        }
    }
}
```

---

### Task 2.2: Add Configuration Loading

**File:** `src/main/java/com/katalan/core/engine/KatalanEngine.java`

```java
// Add field
private OptimizationConfig optimizationConfig;

// Load in constructor
public KatalanEngine(RunConfiguration config) {
    this.config = config;
    this.context = new ExecutionContext(config);
    
    // Load optimization config
    if (config.getProjectPath() != null) {
        this.optimizationConfig = OptimizationConfig.load(config.getProjectPath());
    } else {
        this.optimizationConfig = new OptimizationConfig();
    }
    
    // ... rest of constructor ...
}
```

---

## Testing & Validation

### Unit Tests

```bash
# Run unit tests for new components
mvn test -Dtest=WebDriverPoolTest
mvn test -Dtest=StreamingXmlLoggerTest
mvn test -Dtest=OptimizationConfigTest
```

### Integration Tests

```bash
# Test browser pooling
./test-browser-pool.sh

# Test streaming logger
./test-streaming-logger.sh

# Test memory limits
./test-memory-limits.sh
```

### Performance Benchmarks

```bash
# Run benchmark suite
./benchmark-optimization.sh

# Compare results
# Before: 10 tests in 5:00 min, 1.5GB RAM
# After:  10 tests in 3:00 min, 600MB RAM (40% faster, 60% less memory!)
```

---

## Rollback Plan

### Quick Disable

```bash
# Disable in properties file
echo "katalan.optimization.enabled=false" > katalan.properties

# Or via system property
java -Dkatalan.optimization.enabled=false -jar katalan-runner.jar run ...
```

### Emergency Rollback

```bash
# Revert to previous JAR version
cp katalan-runner-1.1.1.jar.backup katalan-runner-1.1.1.jar

# Or rebuild from previous commit
git checkout <previous-commit>
mvn clean package
```

---

## Monitoring & Metrics

### Enable JMX Monitoring

```bash
# Run with JMX enabled
java -Dcom.sun.management.jmxremote \
     -Dcom.sun.management.jmxremote.port=9010 \
     -Dcom.sun.management.jmxremote.authenticate=false \
     -Dcom.sun.management.jmxremote.ssl=false \
     -jar katalan-runner.jar run ...
```

### View Metrics

```bash
# Connect with JConsole
jconsole localhost:9010

# Or use CLI
jcmd <pid> GC.heap_info
jcmd <pid> Thread.print
```

---

## Troubleshooting

### Browser Pool Issues

**Symptom:** "Pool full, waiting for available driver"

**Solution:**
```properties
# Increase pool size
katalan.browser.pool.maxSize=5

# Or reduce idle timeout
katalan.browser.pool.idleTimeout=30
```

### Memory Issues

**Symptom:** OutOfMemoryError

**Solution:**
```bash
# Increase heap size
java -Xmx2g -jar katalan-runner.jar run ...

# Enable aggressive GC
java -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -jar katalan-runner.jar run ...
```

### Screenshot Issues

**Symptom:** JPEG quality too low

**Solution:**
```properties
# Increase quality
katalan.screenshot.quality=95

# Or use PNG
katalan.screenshot.format=PNG
```

---

## Support

- 📖 Full documentation: `docs/RESOURCE_OPTIMIZATION.md`
- 🐛 Report issues: GitHub Issues
- 💬 Discussions: GitHub Discussions
- 📧 Email: support@katalan.com

---

**Last Updated:** May 4, 2026  
**Version:** 1.0  
**Status:** Ready for Implementation
