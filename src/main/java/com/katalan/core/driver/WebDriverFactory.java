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
        
        
        // Add common Chrome arguments
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
            "--disable-features=PasswordCheck,PasswordLeakDetection,InsecureFormWarnings",
            "--password-store=basic"
        );

        if (config.isHeadless()) {
            options.addArguments(
                "--headless=new",
                "--window-size=1920,1080",
                "--disable-gpu",
                "--hide-scrollbars",
                "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
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
        
        // Disable all password/autofill features
        java.util.Map<String, Object> prefs = new java.util.HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("profile.password_manager_leak_detection", false);
        prefs.put("autofill.profile_enabled", false);
        prefs.put("autofill.credit_card_enabled", false);
        options.setExperimentalOption("prefs", prefs);
        
        // Accept insecure certs
        options.setAcceptInsecureCerts(true);
        
        // Add custom arguments
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
     */
    private static void configureDriver(WebDriver driver, RunConfiguration config) {
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(config.getImplicitWait()));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(config.getPageLoadTimeout()));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(config.getScriptTimeout()));
        
        // Maximize window by default
        driver.manage().window().maximize();
        
        logger.info("WebDriver configured with timeouts - implicit: {}s, pageLoad: {}s, script: {}s",
                config.getImplicitWait(), config.getPageLoadTimeout(), config.getScriptTimeout());
    }
}
