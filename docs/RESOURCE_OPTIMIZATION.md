# 🚀 Resource Optimization Strategy untuk Katalan Runner

## 📊 Analisis Current State

### Resource Usage Issues yang Ditemukan:

1. **WebDriver Management**
   - ❌ Setiap `openBrowser()` creates NEW browser instance
   - ❌ Old browsers stay alive until JVM shutdown
   - ❌ Multiple test cases = multiple Chrome instances (memory leak!)
   - ❌ Cleanup hanya via shutdown hook (delayed)

2. **Memory Management**
   - ❌ XmlKeywordLogger buffers ALL records in memory
   - ❌ Screenshot files loaded to memory before async write
   - ❌ Step definitions cache unlimited size (ConcurrentHashMap)
   - ❌ No memory limit on GroovyShell instances

3. **Thread Management**
   - ✅ Good: Single thread executor for async screenshot (daemon)
   - ❌ No thread pool size configuration
   - ❌ No timeout for long-running operations
   - ❌ No circuit breaker for failing operations

4. **I/O Operations**
   - ❌ Synchronous report generation (blocking)
   - ❌ Multiple file reads for same ObjectRepository
   - ❌ No compression for large log files
   - ❌ Screenshot files not optimized (full quality PNG)

---

## 🎯 Optimization Strategies

### 1. **WebDriver Pooling & Reuse** (High Impact)

**Current:**
```java
// Every openBrowser() creates new Chrome
WebUI.openBrowser(url) → new ChromeDriver() → stays alive!
```

**Optimized:**
```java
// Browser pool with configurable limits
WebDriverPool {
    - maxConcurrent: 3 (default)
    - idleTimeout: 60s
    - enableReuse: true
    - cleanupStrategy: IMMEDIATE | DELAYED | LAZY
}
```

**Benefits:**
- ✅ Reduce memory: 1 Chrome = ~200-300MB RAM
- ✅ Faster test execution (no browser startup overhead)
- ✅ Configurable limits prevent resource exhaustion

---

### 2. **Streaming Report Generation** (High Impact)

**Current:**
```java
// Buffer ALL logs in memory
XmlKeywordLogger.getRecords() → List<KeywordRecord> (unbounded)
```

**Optimized:**
```java
// Stream directly to file
StreamingXmlLogger {
    - writeToFile(record) → append immediately
    - bufferSize: 1000 records
    - flushInterval: 5 seconds
    - compression: gzip (optional)
}
```

**Benefits:**
- ✅ Constant memory usage (no matter how many steps)
- ✅ Faster report access (no wait for flush)
- ✅ File compression saves disk space

---

### 3. **Lazy Loading & Smart Caching** (Medium Impact)

**Current:**
```java
// Load ALL objects at startup
ObjectRepository.loadAll() → Map<String, TestObject> (full workspace)

// Load ALL step definitions at startup
StepDefinitions.loadAll() → List<StepDefinition> (all .groovy files)
```

**Optimized:**
```java
// Lazy load on-demand
LazyObjectRepository {
    - loadOnDemand(objectId) → only when needed
    - cache: LRU(maxSize: 500)
    - preloadHints: [frequently used objects]
}

StepDefinitionCache {
    - loadOnDemand(featureFile) → only needed steps
    - evictionPolicy: LRU
    - maxCacheSize: 1000 patterns
    - ttl: 1 hour
}
```

**Benefits:**
- ✅ Faster startup time
- ✅ Lower memory footprint
- ✅ Better scalability for large projects

---

### 4. **Screenshot Optimization** (Medium Impact)

**Current:**
```java
// Full quality PNG, load to memory
screenshot = driver.getScreenshotAs(OutputType.FILE)
asyncWriter.copy(source, dest) → still loads to RAM
```

**Optimized:**
```java
ScreenshotOptimizer {
    - format: JPEG (smaller than PNG)
    - quality: 0.85 (configurable)
    - maxWidth: 1920px (downscale if larger)
    - skipDuplicates: true (compare hash)
    - streamingCopy: true (no memory load)
}
```

**Benefits:**
- ✅ 70-80% smaller file size
- ✅ Lower memory usage
- ✅ Faster I/O operations

---

### 5. **Resource Limits & Circuit Breakers** (High Impact)

**New Configuration:**
```java
ResourceLimits {
    // Memory limits
    maxHeapUsage: 80%
    warningThreshold: 70%
    
    // Browser limits
    maxBrowsers: 3
    browserIdleTimeout: 60s
    
    // Thread limits
    maxThreads: 10
    queueSize: 100
    
    // I/O limits
    maxScreenshotSize: 5MB
    maxLogFileSize: 100MB
    
    // Circuit breaker
    failureThreshold: 5
    timeoutThreshold: 30s
    halfOpenDelay: 10s
}
```

**Benefits:**
- ✅ Prevent OOM errors
- ✅ Graceful degradation
- ✅ Better error recovery

---

## 📈 Implementation Priority

### Phase 1: Quick Wins (1-2 days)
1. ✅ WebDriver immediate cleanup after test
2. ✅ Screenshot JPEG compression
3. ✅ Memory threshold warnings
4. ✅ Browser reuse flag (opt-in)

### Phase 2: Core Improvements (3-5 days)
1. ✅ Streaming report logger
2. ✅ WebDriver pool implementation
3. ✅ Lazy loading for ObjectRepository
4. ✅ LRU cache for step definitions

### Phase 3: Advanced Features (5-7 days)
1. ✅ Circuit breaker pattern
2. ✅ Resource monitoring dashboard
3. ✅ Auto-scaling browser pool
4. ✅ Distributed execution support

---

## 💻 Configuration Example

**katalan.properties:**
```properties
# Resource optimization settings
katalan.optimization.enabled=true

# Browser pooling
katalan.browser.pool.enabled=true
katalan.browser.pool.maxSize=3
katalan.browser.pool.reuseWindows=true
katalan.browser.pool.idleTimeout=60s
katalan.browser.cleanup.strategy=IMMEDIATE

# Memory management
katalan.memory.maxHeapUsage=80%
katalan.memory.warningThreshold=70%
katalan.memory.gcAfterTest=true

# Screenshot optimization
katalan.screenshot.format=JPEG
katalan.screenshot.quality=85
katalan.screenshot.maxWidth=1920
katalan.screenshot.compression=true

# Logging optimization
katalan.logging.streaming=true
katalan.logging.bufferSize=1000
katalan.logging.flushInterval=5s
katalan.logging.compression=true

# Cache settings
katalan.cache.objectRepository.maxSize=500
katalan.cache.stepDefinitions.maxSize=1000
katalan.cache.stepMatch.maxSize=1024
katalan.cache.ttl=3600s

# Thread pool
katalan.threads.maxPoolSize=10
katalan.threads.corePoolSize=3
katalan.threads.queueCapacity=100
katalan.threads.keepAlive=60s

# Circuit breaker
katalan.circuitBreaker.enabled=true
katalan.circuitBreaker.failureThreshold=5
katalan.circuitBreaker.timeout=30s
katalan.circuitBreaker.halfOpenDelay=10s
```

---

## 📊 Expected Improvements

### Memory Usage:
- **Before:** 500MB - 2GB (depending on test count)
- **After:** 200MB - 800MB (60% reduction)

### Execution Speed:
- **Before:** 10 tests = 5 minutes
- **After:** 10 tests = 2-3 minutes (40-50% faster)

### Disk Space:
- **Before:** 100 screenshots = 50MB
- **After:** 100 screenshots = 10-15MB (70-80% reduction)

### Startup Time:
- **Before:** 10-15 seconds
- **After:** 2-5 seconds (70% faster)

---

## 🔧 Backward Compatibility

All optimizations will be **opt-in by default** to maintain backward compatibility:

```java
// Legacy mode (current behavior)
katalan.optimization.enabled=false

// Optimized mode (new behavior)
katalan.optimization.enabled=true
```

Users can enable specific optimizations individually:
```properties
katalan.browser.pool.enabled=true  # Only enable browser pooling
katalan.screenshot.format=JPEG     # Only enable screenshot optimization
```

---

## 🚦 Monitoring & Metrics

New metrics exposed via JMX/Log:

```
Resource Metrics:
- heap.used / heap.max
- browsers.active / browsers.max
- threads.active / threads.max
- cache.hits / cache.misses
- screenshots.saved.bytes
- log.records.buffered

Performance Metrics:
- test.execution.time.ms
- browser.startup.time.ms
- screenshot.write.time.ms
- report.generation.time.ms

Reliability Metrics:
- circuit.breaker.state
- failures.last.minute
- memory.gc.frequency
```

---

## 🎓 Migration Guide

### For Users:
1. Update `katalan-runner` to v1.2.0+
2. Add optimization config to project
3. Run with `--optimize` flag
4. Monitor logs for warnings
5. Adjust limits based on needs

### For Developers:
1. Use `WebDriverPool.acquire()` instead of `WebDriverFactory.create()`
2. Call `WebDriverPool.release()` after test
3. Use `StreamingLogger` instead of buffering
4. Implement `ResourceAware` interface for custom keywords

---

## 📚 References

- [WebDriver Best Practices](https://www.selenium.dev/documentation/webdriver/drivers/)
- [JVM Memory Management](https://docs.oracle.com/javase/8/docs/technotes/guides/vm/gctuning/)
- [Circuit Breaker Pattern](https://martinfowler.com/bliki/CircuitBreaker.html)
- [LRU Cache Implementation](https://www.baeldung.com/java-lru-cache)

---

**Last Updated:** May 4, 2026  
**Version:** 1.0 (Draft)  
**Status:** Proposal for Review
