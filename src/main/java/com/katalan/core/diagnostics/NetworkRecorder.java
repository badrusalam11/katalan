package com.katalan.core.diagnostics;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers {@code network-recorder.js} on every document the tab loads (Chromium, via CDP), so a failure
 * can show the request payload and response body of the API calls before it, not only their status.
 * Secrets are masked by key in the page and bodies are truncated before anything is stored.
 *
 * <p>Off with {@code -Dkatalan.failureDiagnostics.networkBodies=false}. Other browsers: no recorder, the
 * diagnosis falls back to status/duration from Resource Timing.</p>
 */
public final class NetworkRecorder {

    private static final Logger logger = LoggerFactory.getLogger(NetworkRecorder.class);
    private static volatile String script;

    private NetworkRecorder() {}

    public static boolean isEnabled() {
        return com.katalan.core.config.FailureDiagnosticsConfig.isEnabled()
                && !"false".equalsIgnoreCase(System.getProperty("katalan.failureDiagnostics.networkBodies", "true"));
    }

    public static void install(WebDriver driver) {
        if (!isEnabled() || !(driver instanceof ChromiumDriver)) return;
        try {
            ((ChromiumDriver) driver).executeCdpCommand("Page.addScriptToEvaluateOnNewDocument",
                    Collections.singletonMap("source", script()));
        } catch (Exception e) {
            logger.debug("Network recorder not registered via CDP: {}", e.getMessage());
        }
    }

    static String script() throws Exception {
        String s = script;
        if (s == null) {
            try (InputStream in = NetworkRecorder.class.getResourceAsStream("/katalan/network-recorder.js")) {
                if (in == null) throw new IllegalStateException("network-recorder.js not on classpath");
                s = script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return s;
    }
}
