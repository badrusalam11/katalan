package com.katalan.core.driver;

import com.katalan.core.config.RunConfiguration;
import io.appium.java_client.service.local.AppiumDriverLocalService;
import io.appium.java_client.service.local.AppiumServiceBuilder;
import org.openqa.selenium.remote.service.DriverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Field;

/**
 * Manages the lifecycle of a local Appium server process, the same way
 * WebDriverManager auto-manages chromedriver/geckodriver: callers don't need a
 * server already running, katalan spawns one and tears it down at run-end.
 *
 * If {@code RunConfiguration.getAppiumServerUrl()} is set this class is bypassed
 * entirely and the caller talks directly to that externally-managed server instead.
 */
public class AppiumServerManager {

    private static final Logger logger = LoggerFactory.getLogger(AppiumServerManager.class);

    private static volatile AppiumDriverLocalService service;
    private static volatile boolean shutdownHookRegistered = false;

    private AppiumServerManager() {
    }

    /**
     * Ensure an Appium server is reachable for this run and return its base URL.
     * Auto-starts a local server on first call unless an explicit appiumServerUrl
     * was configured, in which case that URL is returned untouched.
     */
    public static synchronized String ensureRunning(RunConfiguration config) {
        if (config.getAppiumServerUrl() != null && !config.getAppiumServerUrl().trim().isEmpty()) {
            logger.info("📡 Using externally-managed Appium server: {}", config.getAppiumServerUrl());
            return config.getAppiumServerUrl();
        }

        if (service != null && service.isRunning()) {
            return service.getUrl().toString();
        }

        logger.info("🚀 Starting local Appium server (auto-managed)...");
        AppiumServiceBuilder builder = new AppiumServiceBuilder();

        // AppiumServiceBuilder.usingDriverExecutable() expects the Node.js binary, NOT the
        // appium wrapper script.  For nvm/npm installs, node and appium live in the same
        // bin/ directory (e.g. ~/.nvm/versions/node/vX/bin/{node,appium}), and the main.js
        // entry point is at ../lib/node_modules/appium/build/lib/main.js relative to that.
        File appiumBinary = findAppiumExecutable();
        if (appiumBinary != null) {
            File binDir = appiumBinary.getParentFile(); // e.g. ~/.nvm/.../bin

            // node binary sits alongside appium in the same bin/ directory
            File nodeBinary = new File(binDir, "node");
            if (!nodeBinary.exists() || !nodeBinary.canExecute()) {
                nodeBinary = findExecutableOnPath("node");
            }
            if (nodeBinary != null && nodeBinary.exists()) {
                logger.debug("Using Node.js binary: {}", nodeBinary.getAbsolutePath());
                builder.usingDriverExecutable(nodeBinary);
            }

            // main.js is at <prefix>/lib/node_modules/appium/build/lib/main.js
            File mainJs = new File(binDir.getParentFile(),
                    "lib/node_modules/appium/build/lib/main.js");
            if (mainJs.exists()) {
                logger.debug("Using Appium main.js: {}", mainJs.getAbsolutePath());
                builder.withAppiumJS(mainJs);
            } else {
                logger.warn("Could not locate Appium main.js at expected path: {}", mainJs);
            }
        } else {
            logger.warn("Could not locate 'appium' executable on PATH - relying on " +
                    "AppiumServiceBuilder's own lookup. If this fails, install Appium " +
                    "(npm i -g appium) or pass --appium-url to use an external server.");
        }

        if (config.getAppiumServerPort() > 0) {
            builder.usingPort(config.getAppiumServerPort());
        } else {
            builder.usingAnyFreePort();
        }
        builder.withIPAddress("127.0.0.1");

        registerShutdownHook();

        try {
            service = AppiumDriverLocalService.buildService(builder);
            service.start();
        } catch (Exception e) {
            service = null;
            throw new IllegalStateException(
                    "Failed to start local Appium server. Is Appium installed and on PATH? " +
                    "(npm i -g appium) Or pass --appium-url to use a server you start yourself. " +
                    "Cause: " + e.getMessage(), e);
        }

        long pid = extractPid(service);
        if (pid > 0) {
            DriverCleanupManager.trackAppiumServerPid(pid);
        }

        logger.info("✅ Local Appium server started at {}", service.getUrl());
        return service.getUrl().toString();
    }

    /**
     * Stop the locally-managed Appium server, if one was started. No-op when
     * the run used an externally-managed server (appiumServerUrl was set).
     */
    public static synchronized void stopIfRunning() {
        if (service == null) {
            return;
        }
        try {
            logger.info("🔒 Stopping local Appium server...");
            long pid = extractPid(service);
            service.stop();
            if (pid > 0) {
                DriverCleanupManager.untrackAppiumServerPid(pid);
            }
            logger.debug("✅ Local Appium server stopped");
        } catch (Exception e) {
            logger.warn("Failed to stop Appium server cleanly: {}", e.getMessage());
        } finally {
            service = null;
        }
    }

    private static synchronized void registerShutdownHook() {
        if (shutdownHookRegistered) {
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(
                AppiumServerManager::stopIfRunning, "Appium-Server-Cleanup-Thread"));
        shutdownHookRegistered = true;
    }

    /**
     * Best-effort PID extraction via reflection (DriverService.process → ExternalProcess.process
     * → Process.pid()). Graceful service.stop() is the primary shutdown path; this is a
     * fallback for DriverCleanupManager.forceCleanup().
     */
    private static long extractPid(AppiumDriverLocalService svc) {
        try {
            Field processField = DriverService.class.getDeclaredField("process");
            processField.setAccessible(true);
            Object externalProcess = processField.get(svc);
            if (externalProcess == null) return -1;
            Field procField = externalProcess.getClass().getDeclaredField("process");
            procField.setAccessible(true);
            Process p = (Process) procField.get(externalProcess);
            return p != null ? p.pid() : -1;
        } catch (Exception e) {
            logger.debug("Could not extract Appium server PID: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * Locate the 'appium' wrapper script on PATH (handles nvm-managed Node installs
     * that may not appear on the PATH inherited by the JVM when launched from an IDE).
     */
    private static File findAppiumExecutable() {
        return findExecutableOnPath("appium");
    }

    private static File findExecutableOnPath(String name) {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        try {
            ProcessBuilder pb = windows
                    ? new ProcessBuilder("where", name)
                    : new ProcessBuilder("which", name);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output;
            try (java.io.BufferedReader reader =
                         new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                output = reader.readLine();
            }
            p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (output != null && !output.trim().isEmpty()) {
                File f = new File(output.trim());
                if (f.exists()) {
                    return f;
                }
            }
        } catch (Exception e) {
            logger.debug("which/where {} lookup failed: {}", name, e.getMessage());
        }
        return null;
    }
}
