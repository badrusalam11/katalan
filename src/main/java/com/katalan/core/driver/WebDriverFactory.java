package com.katalan.core.driver;

import com.katalan.core.config.RunConfiguration;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.safari.SafariDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.Duration;
import java.util.Map;

/**
 * WebDriver Factory - Creates and configures WebDriver instances
 */
public class WebDriverFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(WebDriverFactory.class);
    
    /**
     * Create a WebDriver based on configuration
     */
    public static WebDriver createDriver(RunConfiguration config) {
        logger.info("Creating WebDriver for browser: {}", config.getBrowserType());
        
        WebDriver driver;
        
        if (config.isUseRemoteWebDriver()) {
            driver = createRemoteDriver(config);
        } else {
            driver = createLocalDriver(config);
        }
        
        configureDriver(driver, config);
        return driver;
    }
    
    /**
     * Create a local WebDriver
     */
    private static WebDriver createLocalDriver(RunConfiguration config) {
        switch (config.getBrowserType()) {
            case CHROME:
                return createChromeDriver(config);
            case FIREFOX:
                return createFirefoxDriver(config);
            case EDGE:
                return createEdgeDriver(config);
            case SAFARI:
                return createSafariDriver(config);
            default:
                throw new IllegalArgumentException("Unsupported browser: " + config.getBrowserType());
        }
    }
    
    /**
     * Create Chrome WebDriver
     */
    private static WebDriver createChromeDriver(RunConfiguration config) {
        if (config.getDriverPath() == null || config.getDriverPath().isEmpty()) {
            WebDriverManager.chromedriver().setup();
        } else {
            System.setProperty("webdriver.chrome.driver", config.getDriverPath());
        }
        
        ChromeOptions options = new ChromeOptions();
        
        // ============================================================
        // 1. Read custom args from RunConfiguration (from automation script)
        // ============================================================
        Object argsFromScript = com.kms.katalon.core.configuration.RunConfiguration.getWebDriverPreferencesProperty("args");
        if (argsFromScript instanceof java.util.List) {
            java.util.List<?> argsList = (java.util.List<?>) argsFromScript;
            for (Object arg : argsList) {
                if (arg != null) {
                    options.addArguments(arg.toString());
                    logger.debug("Added Chrome arg from script: {}", arg);
                }
            }
        }
        
        // ============================================================
        // 2. Read custom prefs from RunConfiguration (from automation script)
        // ============================================================
        Object prefsFromScript = com.kms.katalon.core.configuration.RunConfiguration.getWebDriverPreferencesProperty("prefs");
        java.util.Map<String, Object> prefs = new java.util.HashMap<>();
        if (prefsFromScript instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> prefsMap = (java.util.Map<String, Object>) prefsFromScript;
            prefs.putAll(prefsMap);
            logger.debug("Added Chrome prefs from script: {}", prefsMap.keySet());
        }
        
        // ============================================================
        // 3. Read localState from RunConfiguration (for download bubble)
        // ============================================================
        Object localStateFromScript = com.kms.katalon.core.configuration.RunConfiguration.getWebDriverPreferencesProperty("localState");
        if (localStateFromScript instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> localStateMap = (java.util.Map<String, Object>) localStateFromScript;
            options.setExperimentalOption("localState", localStateMap);
            logger.debug("Added Chrome localState from script: {}", localStateMap.keySet());
        }
        
        // Add common Chrome arguments (defaults - will be overridden by script if duplicates)
        options.addArguments(
            "--disable-blink-features=AutomationControlled",
            "--disable-extensions",
            "--disable-gpu",
            "--no-sandbox",
            "--disable-dev-shm-usage",
            "--remote-allow-origins=*",
            "--disable-popup-blocking",
            "--disable-notifications",
            "--disable-infobars",
            "--ignore-certificate-errors",
            "--ignore-ssl-errors",
            "--allow-running-insecure-content",
            "--safebrowsing-disable-download-protection",
            "--safebrowsing-disable-extension-blacklist",
            "--disable-features=InsecureDownloadWarnings,PasswordCheck,PasswordLeakDetection,InsecureFormWarnings",
            "--password-store=basic"
        );

        if (config.isHeadless()) {
            // ============================================================
            // CRITICAL: FORCE VIEWPORT SIZE VIA mobileEmulation
            // This is the ONLY reliable way to set window size in headless!
            // --window-size flag is IGNORED in headless mode!
            // ============================================================
            java.util.Map<String, Object> deviceMetrics = new java.util.HashMap<>();
            deviceMetrics.put("width", 1920);
            deviceMetrics.put("height", 1080);
            deviceMetrics.put("pixelRatio", 1.0);
            
            java.util.Map<String, Object> mobileEmulation = new java.util.HashMap<>();
            mobileEmulation.put("deviceMetrics", deviceMetrics);
            mobileEmulation.put("userAgent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36");
            
            options.setExperimentalOption("mobileEmulation", mobileEmulation);
            
            options.addArguments(
                "--headless=new",
                "--disable-gpu",
                "--hide-scrollbars",
                "--disable-dev-shm-usage",
                "--disable-software-rasterizer",
                "--enable-features=NetworkService,NetworkServiceInProcess",
                "--force-device-scale-factor=1",
                "--disable-setuid-sandbox",
                "--no-first-run",
                "--no-default-browser-check"
            );
            
            options.setExperimentalOption(
                "excludeSwitches",
                java.util.Arrays.asList("enable-automation")
            );
            options.setExperimentalOption(
                "useAutomationExtension",
                false
            );
        }
        
        
        // Use fresh temporary profile - no saved passwords, no Google account
        // Use File.separator for cross-platform compatibility (Mac/Linux use '/', Windows '\')
        String tempProfile = System.getProperty("java.io.tmpdir") + java.io.File.separator
                + "katalan-chrome-" + System.currentTimeMillis();
        options.addArguments("--user-data-dir=" + tempProfile);
        
        // Disable all password/autofill features (defaults - can be overridden by script prefs)
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("profile.password_manager_leak_detection", false);
        prefs.put("autofill.profile_enabled", false);
        prefs.put("autofill.credit_card_enabled", false);
        
        // Apply all prefs (script prefs + defaults)
        if (!prefs.isEmpty()) {
            options.setExperimentalOption("prefs", prefs);
        }
        
        // Accept insecure certs
        options.setAcceptInsecureCerts(true);
        
        // Add custom arguments from config (legacy support)
        for (Map.Entry<String, String> arg : config.getBrowserArguments().entrySet()) {
            options.addArguments(arg.getKey() + "=" + arg.getValue());
        }
        
        // Add custom capabilities
        for (Map.Entry<String, Object> cap : config.getBrowserCapabilities().entrySet()) {
            options.setCapability(cap.getKey(), cap.getValue());
        }
        
        if (config.getBrowserBinaryPath() != null && !config.getBrowserBinaryPath().isEmpty()) {
            options.setBinary(config.getBrowserBinaryPath());
        }
        
        return new ChromeDriver(options);
    }
    
    /**
     * Create Firefox WebDriver
     */
    private static WebDriver createFirefoxDriver(RunConfiguration config) {
        if (config.getDriverPath() == null || config.getDriverPath().isEmpty()) {
            WebDriverManager.firefoxdriver().setup();
        } else {
            System.setProperty("webdriver.gecko.driver", config.getDriverPath());
        }
        
        FirefoxOptions options = new FirefoxOptions();
        
        if (config.isHeadless()) {
            options.addArguments("--headless");
        }
        
        // Add custom capabilities
        for (Map.Entry<String, Object> cap : config.getBrowserCapabilities().entrySet()) {
            options.setCapability(cap.getKey(), cap.getValue());
        }
        
        if (config.getBrowserBinaryPath() != null && !config.getBrowserBinaryPath().isEmpty()) {
            options.setBinary(config.getBrowserBinaryPath());
        }
        
        return new FirefoxDriver(options);
    }
    
    /**
     * Create Edge WebDriver
     */
    private static WebDriver createEdgeDriver(RunConfiguration config) {
        if (config.getDriverPath() == null || config.getDriverPath().isEmpty()) {
            WebDriverManager.edgedriver().setup();
        } else {
            System.setProperty("webdriver.edge.driver", config.getDriverPath());
        }
        
        EdgeOptions options = new EdgeOptions();
        
        if (config.isHeadless()) {
            options.addArguments("--headless=new");
        }
        
        // Add custom capabilities
        for (Map.Entry<String, Object> cap : config.getBrowserCapabilities().entrySet()) {
            options.setCapability(cap.getKey(), cap.getValue());
        }
        
        return new EdgeDriver(options);
    }
    
    /**
     * Create Safari WebDriver
     */
    private static WebDriver createSafariDriver(RunConfiguration config) {
        // Safari doesn't need driver setup
        return new SafariDriver();
    }
    
    /**
     * Create Remote WebDriver
     */
    private static WebDriver createRemoteDriver(RunConfiguration config) {
        try {
            URL remoteUrl = new URL(config.getRemoteWebDriverUrl());
            
            switch (config.getBrowserType()) {
                case CHROME:
                    ChromeOptions chromeOptions = new ChromeOptions();
                    if (config.isHeadless()) {
                        chromeOptions.addArguments("--headless=new");
                    }
                    for (Map.Entry<String, Object> cap : config.getBrowserCapabilities().entrySet()) {
                        chromeOptions.setCapability(cap.getKey(), cap.getValue());
                    }
                    return new RemoteWebDriver(remoteUrl, chromeOptions);
                    
                case FIREFOX:
                    FirefoxOptions firefoxOptions = new FirefoxOptions();
                    if (config.isHeadless()) {
                        firefoxOptions.addArguments("--headless");
                    }
                    for (Map.Entry<String, Object> cap : config.getBrowserCapabilities().entrySet()) {
                        firefoxOptions.setCapability(cap.getKey(), cap.getValue());
                    }
                    return new RemoteWebDriver(remoteUrl, firefoxOptions);
                    
                case EDGE:
                    EdgeOptions edgeOptions = new EdgeOptions();
                    if (config.isHeadless()) {
                        edgeOptions.addArguments("--headless=new");
                    }
                    for (Map.Entry<String, Object> cap : config.getBrowserCapabilities().entrySet()) {
                        edgeOptions.setCapability(cap.getKey(), cap.getValue());
                    }
                    return new RemoteWebDriver(remoteUrl, edgeOptions);
                    
                default:
                    throw new IllegalArgumentException("Unsupported browser for remote execution: " + config.getBrowserType());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create remote WebDriver", e);
        }
    }
    
    /**
     * Configure WebDriver timeouts
     * CRITICAL: implicitWait MUST be 0 to avoid double-waiting with explicit waits
     * Katalon Studio uses implicitWait=0 for best performance
     */
    private static void configureDriver(WebDriver driver, RunConfiguration config) {
        // ALWAYS use 0 for implicit wait to prevent double-waiting issues
        // When implicit wait > 0, every findElement call waits the full timeout
        // This causes 2x slowdown: implicit wait + explicit wait
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
        
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(config.getPageLoadTimeout()));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(config.getScriptTimeout()));
        
        // For non-headless: maximize window
        // For headless: window size already forced via mobileEmulation in ChromeOptions
        if (!config.isHeadless()) {
            driver.manage().window().maximize();
        } else {
            // Verify headless viewport size (for logging only)
            try {
                org.openqa.selenium.Dimension actualSize = driver.manage().window().getSize();
                logger.info("Headless mode: Viewport forced to 1920x1080 via mobileEmulation (actual: {}x{})", 
                           actualSize.getWidth(), actualSize.getHeight());
            } catch (Exception e) {
                logger.warn("Could not verify headless viewport size: {}", e.getMessage());
            }
        }
        
        logger.info("WebDriver configured with timeouts - implicit: 0s (FAST), pageLoad: {}s, script: {}s",
                config.getPageLoadTimeout(), config.getScriptTimeout());
    }
}
