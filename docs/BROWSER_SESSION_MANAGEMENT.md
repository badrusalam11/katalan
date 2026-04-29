# Browser Session Management - Katalon-Compatible Behavior

## Philosophy: Multiple Browsers Per Suite (Katalon-Compatible)

### ✅ Current Approach: One Browser Per Test Case (Katalon Behavior)

**Concept**: Setiap test case mendapat browser instance baru. Browser tetap hidup sepanjang test execution. Cleanup semua browser hanya di akhir saat shutdown.

**This mimics Katalon Studio behavior EXACTLY**:
- ✅ Each test case gets its own fresh browser
- ✅ No browser reuse between test cases
- ✅ All browsers stay alive until suite ends
- ✅ All browsers killed at shutdown (prevents zombie processes)

**Benefits**:
- 🔒 **Test Isolation**: Setiap test dapat fresh browser state
- 🚀 **No Cleanup Overhead**: Tidak ada delay buka-tutup browser per test
- 🧹 **Fixed Katalon Bug**: Cleanup di akhir BENERAN jalan (kill all tracked PIDs)
- 💰 **Resource Efficient**: Parallel execution friendly (each test = 1 browser)

**How It Works**:
```
TC01: context.setWebDriver(null) → openBrowser() → Create Chrome #1 → Track PID
TC01: Test execution → Chrome #1 stays alive ✅

TC02: context.setWebDriver(null) → openBrowser() → Create Chrome #2 → Track PID  
TC02: Test execution → Chrome #2 stays alive ✅

TC03: context.setWebDriver(null) → openBrowser() → Create Chrome #3 → Track PID
TC03: Test execution → Chrome #3 stays alive ✅

Shutdown: DriverCleanupManager kills ALL tracked PIDs (Chrome #1, #2, #3) 🔥
```

**Key Implementation Details**:
1. **Before each test**: `context.setWebDriver(null)` forces fresh browser creation
2. **During test**: `WebUI.openBrowser()` creates NEW browser (driver == null)
3. **After test**: Browser stays alive (NO cleanup between tests)
4. **At shutdown**: `DriverCleanupManager` kills all tracked Chrome + ChromeDriver PIDs

---

## Why This Approach?

### Problem with Katalon Studio:
❌ **Browser cleanup doesn't work** → Chrome zombie processes accumulate → **RAM BLOAT**

### Katalan Solution:
✅ **Same behavior as Katalon** (multiple browsers per suite)  
✅ **Fixed cleanup mechanism** (DriverCleanupManager kills ALL tracked PIDs)  
✅ **No zombie processes** (proper PID tracking + force kill at shutdown)

### Comparison with Katalon:

| Aspect | Katalon Studio | Katalan Framework |
|--------|---------------|-------------------|
| **Browser per test** | ✅ New browser | ✅ New browser |
| **Browser stays alive** | ✅ Until suite ends | ✅ Until suite ends |
| **Cleanup mechanism** | ❌ **BROKEN** (zombies) | ✅ **FIXED** (kills all) |
| **RAM bloat** | ❌ Yes (zombies accumulate) | ✅ No (proper cleanup) |
| **Test isolation** | ✅ Each test = fresh state | ✅ Each test = fresh state |

---

## Implementation Details

### 1. KatalanEngine - Clear Driver Before Each Test

```java
// BEFORE test case execution
context.setWebDriver(null); // Force fresh browser on next openBrowser()

// Execute test case
executeTestCase(testCase);

// NO cleanup here - browser stays alive!
```

**Why?**
- Setting `context.setWebDriver(null)` tells `WebUI.openBrowser()` to create NEW browser
- Browser stays alive after test finishes (no cleanup between tests)
- Mimics Katalon behavior exactly

### 2. WebUI.openBrowser() - Smart Browser Creation

```java
public static void openBrowser(String url) {
    WebDriver driver = context.getWebDriver();
    
    // Check if driver exists and is still alive
    if (driver == null || isDriverClosed(driver)) {
        // Create new browser instance
        driver = WebDriverFactory.createDriver(...);
        context.setWebDriver(driver);
        // Note: WebDriverFactory automatically tracks PIDs
    }
    
    // Navigate to URL
    if (url != null && !url.trim().isEmpty()) {
        driver.get(url);
    }
}
```

**Behavior**:
- **If `driver == null`**: Create NEW Chrome → Track PID ✅
- **If `driver != null` but closed**: Create NEW Chrome → Track PID ✅
- **If `driver != null` and alive**: Reuse existing (shouldn't happen with our setup)

### 3. WebDriverFactory - PID Tracking

```java
public static WebDriver createDriver(RunConfiguration config) {
    // Create ChromeDriver
    ChromeDriver driver = new ChromeDriver(options);
    
    // Track ChromeDriver PID
    long driverPid = getDriverPid(driver);
    DriverCleanupManager.trackDriverPid(driverPid);
    
    // Track Chrome browser PID
    long chromePid = getChromePid();
    DriverCleanupManager.trackChromePid(chromePid);
    
    return driver;
}
```

**Why PID Tracking?**
- Each browser creation adds PIDs to tracked set
- Cleanup manager can kill ALL browsers at once
- No orphan scanning needed (fast cleanup)

### 4. DriverCleanupManager - Guaranteed Cleanup

```java
// Shutdown hook registered at initialization
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    cleanupTrackedProcesses(); // Force kill all tracked PIDs
}));

private static void cleanupTrackedProcesses() {
    // Kill all tracked ChromeDriver PIDs
    for (Long pid : trackedDriverPids) {
        killProcess(pid, true); // forceful = true
    }
    
    // Kill all tracked Chrome browser PIDs
    for (Long pid : trackedChromePids) {
        killProcess(pid, true); // forceful = true
    }
}
```

**Cleanup Triggers**:
1. **Normal exit**: `engine.shutdown()` calls `context.cleanup()` (graceful)
2. **JVM shutdown**: Shutdown hook force kills all tracked PIDs (guaranteed)
3. **Manual**: `DriverCleanupManager.forceCleanup()` (testing/debugging)

---

## Test Case Best Practices

### ✅ DO: Each Test Calls openBrowser()

```groovy
// Test Case 1
WebUI.openBrowser("http://localhost/login") // Creates Chrome #1
// ... test steps ...
// Browser stays alive ✅

// Test Case 2
WebUI.openBrowser("http://localhost/dashboard") // Creates Chrome #2 (NEW!)
// ... test steps ...
// Browser stays alive ✅

// Test Case 3
WebUI.openBrowser("http://localhost/settings") // Creates Chrome #3 (NEW!)
// ... test steps ...
// Browser stays alive ✅
```

**Result**: 3 test cases = 3 Chrome instances tracked = All killed at shutdown

### ✅ DO: Tests Can Be Independent

```groovy
// TC01: Login test
WebUI.openBrowser("http://localhost/login")
WebUI.setText(findTestObject("Login/txtUsername"), "user1")
WebUI.click(findTestObject("Login/btnSubmit"))
// No need to logout - this browser will be killed at shutdown

// TC02: Fresh login test
WebUI.openBrowser("http://localhost/login") // NEW browser, fresh state!
WebUI.setText(findTestObject("Login/txtUsername"), "user2")
WebUI.click(findTestObject("Login/btnSubmit"))
```

**Why This Works**:
- Each test gets completely fresh browser
- No session bleeding between tests
- No need for explicit logout/cleanup

### ❌ DON'T: Assume Browser Will Be Reused

```groovy
// BAD: Expecting browser reuse
// TC01
WebUI.openBrowser("http://localhost/login")
GlobalVariable.browserHandle = DriverFactory.getWebDriver()

// TC02
driver = GlobalVariable.browserHandle // Will be NULL or closed!
```

**Why?**: `context.setWebDriver(null)` before TC02 means TC01's driver is no longer in context.

### ❌ DON'T: Manually Close Browser

```groovy
// BAD: Manual cleanup
WebUI.openBrowser("http://localhost/app")
// ... test ...
WebUI.closeBrowser() // Don't do this!
```

**Why?**: 
- Defeats the purpose of tracked cleanup
- May leave PIDs untracked
- Framework handles cleanup automatically

---

## Common Issues & Solutions

### Problem 1: "Element Not Found" in Test

**Symptom**:
```
TC02: FAILED - TimeoutException: Element 'Login Button' not found
```

**Root Cause**: TC02 has NEW browser (fresh state), might be at wrong URL.

**Solution**:
```groovy
// TC02 - Verify you're navigating to correct page
WebUI.openBrowser("http://localhost/login") // Explicit URL
WebUI.verifyElementPresent(findTestObject("Login/btnSubmit"))
```

### Problem 2: Zombie Chrome Processes After Crash

**Symptom**: Test crashes, Chrome processes left running.

**Root Cause**: JVM terminated before shutdown hook could run.

**Solution**:
- Manual: Run `DriverCleanupManager.forceCleanup()` in recovery script
- Prevention: Let tests complete normally (don't force-kill JVM)

### Problem 3: Too Many Chrome Instances

**Symptom**: 10 test cases = 10 Chrome browsers = High RAM usage.

**Expected Behavior**: This is **CORRECT**! Each test gets own browser (Katalon behavior).

**If RAM is issue**:
- Run fewer tests in parallel
- Use headless mode: `--headless=new` in ChromeOptions
- Increase system RAM

---

## Performance Comparison

| Metric | Katalon Studio | Katalan Framework |
|--------|---------------|-------------------|
| **Browsers per 10 tests** | 10 (intended) | 10 (working!) |
| **Zombie processes** | ❌ Yes (cleanup broken) | ✅ No (cleanup works) |
| **RAM usage during run** | Same | Same |
| **RAM after completion** | ❌ High (zombies) | ✅ Normal (all killed) |
| **Test isolation** | ✅ Perfect | ✅ Perfect |
| **Execution speed** | Normal | Same |

---

## FAQ

### Q: Kenapa setiap test dapat browser baru?

**A**: Ini behavior Katalon Studio yang correct! Setiap test case harus mendapat fresh browser untuk test isolation. Yang salah di Katalon adalah cleanup-nya broken, bukan konsepnya.

### Q: Apakah bisa reuse browser antar test untuk speed?

**A**: **JANGAN!** Ini akan menghancurkan test isolation. Behavior sekarang sudah correct (sama seperti Katalon):
- Each test = fresh browser = proper isolation
- No reuse = no session bleeding
- Cleanup di akhir = no zombies

### Q: Bagaimana kalau test crash sebelum browser dibuat?

**A**: No problem! `DriverCleanupManager` hanya track PIDs yang beneran dibuat. Test yang crash before `openBrowser()` tidak menambahkan PID ke tracking.

### Q: Apakah bisa manual kill browser di tengah test?

**A**: Technically yes, tapi **TIDAK RECOMMENDED**:
```groovy
WebDriver driver = DriverFactory.getWebDriver()
driver.quit()
context.setWebDriver(null)
```
Lebih baik let framework handle lifecycle.

### Q: Kenapa harus `context.setWebDriver(null)` sebelum test?

**A**: Supaya `WebUI.openBrowser()` detect "driver not exist" dan create browser baru. Tanpa ini, test kedua akan coba reuse browser test pertama (yang mungkin sudah di state aneh).

### Q: Bagaimana kalau parallel execution?

**A**: Each thread gets own `ExecutionContext` → own WebDriver → isolated browsers. Tracking PIDs works across threads (ConcurrentHashMap).

---

## Migration from Katalon

**Good News**: Test scripts **TIDAK PERLU DIUBAH**!

Katalon test script:
```groovy
// Test Case
WebUI.openBrowser("http://localhost/login")
WebUI.setText(findTestObject("Login/txtUsername"), "user")
WebUI.click(findTestObject("Login/btnSubmit"))
// No cleanup
```

Katalan test script:
```groovy
// EXACTLY THE SAME!
WebUI.openBrowser("http://localhost/login")
WebUI.setText(findTestObject("Login/txtUsername"), "user")
WebUI.click(findTestObject("Login/btnSubmit"))
// No cleanup needed
```

**Behavior Changes**:
- ✅ **Browser lifecycle**: Same (one per test)
- ✅ **Test isolation**: Same (fresh browser per test)
- ✅ **Cleanup**: **FIXED!** (actually kills all browsers now)
- ✅ **No zombies**: **NEW!** (PIDs properly tracked and killed)

---

## Troubleshooting

### Debug: Check Tracked PIDs

```java
// In your test or listener
Set<Long> driverPids = DriverCleanupManager.getTrackedDriverPids();
Set<Long> chromePids = DriverCleanupManager.getTrackedChromePids();
System.out.println("Tracked ChromeDriver PIDs: " + driverPids);
System.out.println("Tracked Chrome PIDs: " + chromePids);
```

### Debug: Manual Cleanup

```java
// Force cleanup (for testing)
DriverCleanupManager.forceCleanup();
```

### Debug: Verify Browser Created

```groovy
WebUI.openBrowser("http://localhost/app")
WebDriver driver = DriverFactory.getWebDriver()
if (driver == null) {
    println "ERROR: Browser not created!"
} else {
    println "SUCCESS: Browser created with session: " + ((RemoteWebDriver)driver).getSessionId()
}
```

---

## Summary: The Fix

### Katalon Studio Problem:
```
TC01: openBrowser() → Chrome #1 created
TC02: openBrowser() → Chrome #2 created
TC03: openBrowser() → Chrome #3 created
End: Cleanup tries to run → FAILS → 3 zombie Chrome processes ❌
```

### Katalan Framework Solution:
```
TC01: context.setWebDriver(null) → openBrowser() → Chrome #1 → Track PID #1
TC02: context.setWebDriver(null) → openBrowser() → Chrome #2 → Track PID #2
TC03: context.setWebDriver(null) → openBrowser() → Chrome #3 → Track PID #3
End: DriverCleanupManager → Force kill PID #1, #2, #3 → All dead ✅
```

**Key Differences**:
1. ✅ `context.setWebDriver(null)` before each test ensures fresh browser
2. ✅ PID tracking ensures we know exactly which processes to kill
3. ✅ Force kill at shutdown **ACTUALLY WORKS** (no zombies)

---

**Last Updated**: April 29, 2026  
**Version**: 3.0 - Katalon-Compatible with Fixed Cleanup  
**Author**: Katalan Framework Team
