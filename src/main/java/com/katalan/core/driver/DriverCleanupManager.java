package com.katalan.core.driver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages ChromeDriver process cleanup to prevent zombie processes and memory leaks.
 * 
 * Features:
 * - Tracks active ChromeDriver PIDs created by this JVM
 * - Cleanup on JVM shutdown (graceful exit)
 * - Safe cleanup: only kills processes created by this JVM
 * - Windows Server optimized (taskkill support)
 */
public class DriverCleanupManager {
    
    private static final Logger logger = LoggerFactory.getLogger(DriverCleanupManager.class);
    
    // Track ChromeDriver PIDs created by this JVM session
    private static final Set<Long> trackedDriverPids = ConcurrentHashMap.newKeySet();
    
    // Track Chrome browser PIDs created by tracked drivers
    private static final Set<Long> trackedChromePids = ConcurrentHashMap.newKeySet();
    
    private static volatile boolean shutdownHookRegistered = false;
    
    /**
     * Initialize cleanup manager - registers shutdown hook
     */
    public static synchronized void initialize() {
        if (!shutdownHookRegistered) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("🧹 JVM shutdown detected - cleaning up ChromeDriver processes...");
                cleanupTrackedProcesses();
            }, "ChromeDriver-Cleanup-Thread"));
            
            shutdownHookRegistered = true;
            logger.info("✅ DriverCleanupManager initialized - shutdown hook registered");
        }
    }
    
    /**
     * Track a ChromeDriver process ID
     */
    public static void trackDriverPid(long pid) {
        trackedDriverPids.add(pid);
        logger.debug("📌 Tracking ChromeDriver PID: {}", pid);
    }
    
    /**
     * Track a Chrome browser process ID
     */
    public static void trackChromePid(long pid) {
        trackedChromePids.add(pid);
        logger.debug("📌 Tracking Chrome browser PID: {}", pid);
    }
    
    /**
     * Cleanup all tracked processes - FAST version (no orphan scan)
     */
    private static void cleanupTrackedProcesses() {
        int killedDrivers = 0;
        int killedChrome = 0;
        
        // Kill tracked ChromeDriver processes (force kill for speed)
        for (Long pid : trackedDriverPids) {
            logger.debug("🔫 Killing tracked ChromeDriver PID: {}", pid);
            if (killProcess(pid, true)) { // forceful = true for speed
                killedDrivers++;
            }
        }
        
        // Kill tracked Chrome browser processes (force kill for speed)
        for (Long pid : trackedChromePids) {
            logger.debug("🔫 Killing tracked Chrome browser PID: {}", pid);
            if (killProcess(pid, true)) { // forceful = true for speed
                killedChrome++;
            }
        }
        
        if (killedDrivers > 0 || killedChrome > 0) {
            logger.info("✅ Cleanup complete - Killed {} ChromeDriver(s), {} Chrome browser(s)", 
                killedDrivers, killedChrome);
        }
        
        trackedDriverPids.clear();
        trackedChromePids.clear();
    }
    
    /**
     * Kill a process by PID - FAST version
     */
    private static boolean killProcess(long pid, boolean forceful) {
        try {
            ProcessBuilder pb;
            
            if (isWindows()) {
                // Windows: always use /F for force kill (faster)
                pb = new ProcessBuilder("taskkill", "/F", "/PID", String.valueOf(pid));
            } else {
                // Unix: use -9 for force kill
                pb = new ProcessBuilder("kill", "-9", String.valueOf(pid));
            }
            
            pb.redirectErrorStream(true);
            Process p = pb.start();
            
            boolean finished = p.waitFor(2, TimeUnit.SECONDS); // Reduced timeout
            return finished && p.exitValue() == 0;
            
        } catch (Exception e) {
            logger.debug("Failed to kill process PID {}: {}", pid, e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if running on Windows
     */
    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
    
    /**
     * Manual cleanup call (for testing or manual triggers)
     */
    public static void forceCleanup() {
        logger.debug("🧹 Manual cleanup triggered...");
        cleanupTrackedProcesses();
    }
}
