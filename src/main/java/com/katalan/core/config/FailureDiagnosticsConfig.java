package com.katalan.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Settings for failure diagnostics (see docs/FAILURE_DIAGNOSTICS.md).
 *
 * <p>Resolution order, highest priority first: system property
 * ({@code -Dkatalan.failureDiagnostics.enabled=false}), {@code <project>/katalan.properties},
 * {@code <jar-dir>/katalan.properties}, built-in default.</p>
 */
public final class FailureDiagnosticsConfig {

    private static final Logger logger = LoggerFactory.getLogger(FailureDiagnosticsConfig.class);

    public static final String KEY_ENABLED = "katalan.failureDiagnostics.enabled";
    public static final String KEY_TIMEOUT = "katalan.failureDiagnostics.timeoutMs";
    public static final String KEY_MAX_TEXT = "katalan.failureDiagnostics.maxTextChars";

    private static volatile boolean enabled = true;
    private static volatile long timeoutMs = 4000; // network part runs first; the DOM scan gets the rest
    private static volatile int maxTextChars = 300;

    private FailureDiagnosticsConfig() {}

    public static boolean isEnabled() { return enabled; }
    public static long getTimeoutMs() { return timeoutMs; }
    public static int getMaxTextChars() { return maxTextChars; }

    public static void loadFrom(Path projectPath) {
        Properties merged = new Properties();
        read(jarDirectory() == null ? null : jarDirectory().resolve("katalan.properties"), merged);
        if (projectPath != null) {
            read(projectPath.resolve("katalan.properties"), merged);
        }
        for (String key : new String[]{KEY_ENABLED, KEY_TIMEOUT, KEY_MAX_TEXT}) {
            String sys = System.getProperty(key);
            if (sys != null) merged.setProperty(key, sys);
        }
        String e = merged.getProperty(KEY_ENABLED);
        if (e != null) enabled = Boolean.parseBoolean(e.trim());
        timeoutMs = parseLong(merged.getProperty(KEY_TIMEOUT), timeoutMs, 200, 10_000);
        maxTextChars = (int) parseLong(merged.getProperty(KEY_MAX_TEXT), maxTextChars, 50, 2000);
        logger.info("Failure diagnostics {} (timeoutMs={}, maxTextChars={})",
                enabled ? "enabled" : "DISABLED", timeoutMs, maxTextChars);
    }

    private static long parseLong(String raw, long def, long min, long max) {
        if (raw == null) return def;
        try {
            // properties files in this repo often carry trailing "# comment" on the same line
            String v = raw.split("#")[0].trim();
            return Math.max(min, Math.min(max, Long.parseLong(v)));
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private static void read(Path file, Properties into) {
        if (file == null || !Files.isRegularFile(file)) return;
        try (InputStream in = Files.newInputStream(file)) {
            Properties p = new Properties();
            p.load(in);
            for (String k : p.stringPropertyNames()) {
                if (k.startsWith("katalan.failureDiagnostics.")) {
                    into.setProperty(k, p.getProperty(k).split("#")[0].trim());
                }
            }
        } catch (Exception ex) {
            logger.debug("Could not read failure diagnostics settings from {}: {}", file, ex.getMessage());
        }
    }

    private static Path jarDirectory() {
        try {
            Path jar = Path.of(FailureDiagnosticsConfig.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return Files.isDirectory(jar) ? jar : jar.getParent();
        } catch (Exception ex) {
            return null;
        }
    }
}
