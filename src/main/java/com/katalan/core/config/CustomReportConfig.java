package com.katalan.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Toggle for the third-party "custom report" exporters that Katalon projects
 * wire into their Test Listeners - in practice {@code CSReport.exportKatalonReports()}
 * from the bundled denstoo reporting library.
 *
 * <p>Those exporters build their own PDF on top of the run folder, on top of the
 * report katalan already generates. When the project only wants katalan's own
 * report, turning this on makes katalan drop the export call while leaving the
 * rest of the listener method intact (proxy setup, screenshots, and so on).</p>
 *
 * <p>Resolution order, highest priority first:</p>
 * <ol>
 *   <li>{@code --skip-custom-report} on the command line</li>
 *   <li>{@code skipCustomReport} in {@code <project>/katalan.properties}</li>
 *   <li>{@code skipCustomReport} in {@code <jar-dir>/katalan.properties}</li>
 * </ol>
 * Default is {@code false}, so existing projects keep their custom report.
 */
public final class CustomReportConfig {

    private static final Logger logger = LoggerFactory.getLogger(CustomReportConfig.class);

    /** Property/key name used both in katalan.properties and as a system property suffix. */
    public static final String KEY = "skipCustomReport";

    private static volatile boolean skipCustomReport = false;

    private CustomReportConfig() {}

    public static boolean isSkipCustomReport() {
        return skipCustomReport;
    }

    /** Force the flag on/off, e.g. from a CLI option. Wins over any properties file. */
    public static void setSkipCustomReport(boolean skip) {
        skipCustomReport = skip;
        logger.info("Custom report export {} (set explicitly)", skip ? "DISABLED" : "enabled");
    }

    /**
     * Read the flag from {@code katalan.properties}, project file first then the one
     * next to the jar. Does nothing when neither file declares the key, so a value
     * already set by {@link #setSkipCustomReport(boolean)} survives.
     */
    public static void loadFrom(Path projectPath) {
        Boolean value = null;
        if (projectPath != null) {
            value = read(projectPath.resolve("katalan.properties"));
        }
        if (value == null) {
            value = read(jarDirectory() == null ? null : jarDirectory().resolve("katalan.properties"));
        }
        if (value == null) {
            return;
        }
        skipCustomReport = value;
        logger.info("Custom report export {} (katalan.properties: {}={})",
                value ? "DISABLED" : "enabled", KEY, value);
    }

    /** Value of {@link #KEY} in this properties file, or null when absent/unreadable. */
    private static Boolean read(Path propertiesFile) {
        if (propertiesFile == null || !Files.isRegularFile(propertiesFile)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(propertiesFile)) {
            Properties props = new Properties();
            props.load(in);
            String raw = props.getProperty(KEY);
            return raw == null ? null : Boolean.valueOf(raw.trim());
        } catch (Exception e) {
            logger.debug("Could not read {} from {}: {}", KEY, propertiesFile, e.getMessage());
            return null;
        }
    }

    /** Directory holding the running katalan jar, or null when it cannot be determined. */
    private static Path jarDirectory() {
        try {
            Path jar = Path.of(CustomReportConfig.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return Files.isDirectory(jar) ? jar : jar.getParent();
        } catch (Exception e) {
            return null;
        }
    }
}
