package com.katalan.core.driver;

import com.katalan.core.context.ExecutionContext;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.support.events.EventFiringDecorator;
import org.openqa.selenium.support.events.WebDriverListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.WeakHashMap;

/**
 * Katalon Studio's "Smart Wait": before every element lookup, wait until the page's
 * AJAX (XHR/fetch) requests have finished and the DOM has stopped changing.
 *
 * Katalon decorates the WebDriver with a listener that runs this wait before each
 * findElement/findElements - including raw {@code DriverFactory.getWebDriver().findElements(..)}
 * calls in scripts - so a script that clicks "Search" and immediately counts result rows
 * sees the new results, not the old table. Same semantics here:
 * - AJAX wait: every tracked XHR/fetch is finished (polled every 100 ms),
 * - DOM wait: no node was added in the last 400 ms (polled every 100 ms),
 * - each phase gives up after the timeout and lets the lookup go ahead.
 *
 * Active when the project setting {@code execution.default.smartWaitMode} is true (Katalon's
 * default) and the script hasn't called {@code WebUI.disableSmartWait()}.
 * {@code -Dkatalan.smartWait=false} turns it off for a whole run.
 */
public final class SmartWait {

    private static final Logger logger = LoggerFactory.getLogger(SmartWait.class);

    /** Kept under the driver's 30 s script timeout so executeAsyncScript itself never times out. */
    private static final long DEFAULT_TIMEOUT_MS = 25_000;

    /** Tracks pending XHR/fetch and the last time a node was added; top window only, like Katalon. */
    private static final String HOOK_SCRIPT =
            "(function () {\n" +
            "  if (window !== window.top || window.__katalanWaiter) return;\n" +
            "  var w = window.__katalanWaiter = { domModifiedTime: 0, pending: 0 };\n" +
            "  function touch() { w.domModifiedTime = Date.now(); }\n" +
            "  function observe() {\n" +
            "    try {\n" +
            "      new MutationObserver(function (list) {\n" +
            "        for (var i = 0; i < list.length; i++) {\n" +
            "          if (list[i].type === 'childList' && list[i].addedNodes.length) { touch(); return; }\n" +
            "        }\n" +
            "      }).observe(document, { childList: true, subtree: true });\n" +
            "    } catch (e) {}\n" +
            "  }\n" +
            "  observe();\n" +
            "  document.addEventListener('DOMContentLoaded', touch, false);\n" +
            "  if (window.XMLHttpRequest) {\n" +
            "    var send = XMLHttpRequest.prototype.send;\n" +
            "    XMLHttpRequest.prototype.send = function () {\n" +
            "      var xhr = this, done = false;\n" +
            "      function finish() {\n" +
            "        if (done) return;\n" +
            "        done = true; w.pending = Math.max(0, w.pending - 1);\n" +
            "      }\n" +
            "      w.pending++;\n" +
            "      xhr.addEventListener('loadend', finish);\n" +
            "      // Server-sent event streams never end; don't let them block the wait.\n" +
            "      xhr.addEventListener('readystatechange', function () {\n" +
            "        if (xhr.readyState >= 2) {\n" +
            "          var type = xhr.getResponseHeader('Content-Type');\n" +
            "          if (type && type.indexOf('text/event-stream') >= 0) finish();\n" +
            "        }\n" +
            "      });\n" +
            "      try { return send.apply(xhr, arguments); } catch (e) { finish(); throw e; }\n" +
            "    };\n" +
            "  }\n" +
            "  if (window.fetch) {\n" +
            "    var nativeFetch = window.fetch;\n" +
            "    window.fetch = function () {\n" +
            "      w.pending++;\n" +
            "      var settled = false;\n" +
            "      function finish() { if (!settled) { settled = true; w.pending = Math.max(0, w.pending - 1); } }\n" +
            "      try {\n" +
            "        var p = nativeFetch.apply(this, arguments);\n" +
            "        p.then(finish, finish);\n" +
            "        return p;\n" +
            "      } catch (e) { finish(); throw e; }\n" +
            "    };\n" +
            "  }\n" +
            "})();";

    /** Katalon starts each phase after a 100 ms tick, then polls every 100 ms. */
    private static final String WAIT_SCRIPT =
            "var timeout = arguments[0];\n" +
            "var done = arguments[arguments.length - 1];\n" +
            "var w = window.__katalanWaiter;\n" +
            "if (!w) { done(false); return; }\n" +
            "var ajaxStart = Date.now();\n" +
            "function domWait() {\n" +
            "  var domStart = Date.now();\n" +
            "  (function tick() {\n" +
            "    setTimeout(function () {\n" +
            "      if (w.domModifiedTime && Date.now() - w.domModifiedTime < 400\n" +
            "          && Date.now() - domStart < timeout) { tick(); } else { done(true); }\n" +
            "    }, 100);\n" +
            "  })();\n" +
            "}\n" +
            "(function tick() {\n" +
            "  setTimeout(function () {\n" +
            "    if (w.pending > 0 && Date.now() - ajaxStart < timeout) { tick(); } else { domWait(); }\n" +
            "  }, 100);\n" +
            "})();";

    private static final Map<WebDriver, WebDriver> DECORATED = Collections.synchronizedMap(new WeakHashMap<>());

    private static volatile boolean localEnabled = true;
    private static volatile Boolean globalEnabled;

    private SmartWait() {}

    /** WebUI.enableSmartWait() / disableSmartWait(). */
    public static void setLocalEnabled(boolean enabled) {
        localEnabled = enabled;
    }

    public static boolean isEnabled() {
        return localEnabled && isGloballyEnabled();
    }

    public static boolean isGloballyEnabled() {
        Boolean global = globalEnabled;
        if (global == null) {
            global = readGlobalSetting();
            globalEnabled = global;
        }
        return global;
    }

    private static boolean readGlobalSetting() {
        String override = System.getProperty("katalan.smartWait");
        if (override != null) {
            return Boolean.parseBoolean(override.trim());
        }
        try {
            ExecutionContext ctx = ExecutionContext.getCurrent();
            Path project = ctx != null ? ctx.getProjectPath() : null;
            if (project != null) {
                for (String file : new String[] {
                        "com.kms.katalon.execution.properties", "com.kms.katalon.execution.webui.properties" }) {
                    Path settings = project.resolve("settings").resolve("internal").resolve(file);
                    if (!Files.isRegularFile(settings)) {
                        continue;
                    }
                    Properties props = new Properties();
                    try (InputStream in = Files.newInputStream(settings)) {
                        props.load(in);
                    }
                    String value = props.getProperty("execution.default.smartWaitMode");
                    if (value != null) {
                        return Boolean.parseBoolean(value.replace("\"", "").trim());
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not read smart wait project setting: {}", e.getMessage());
        }
        return true;
    }

    /**
     * Register the page hook so it runs at the start of every document this tab loads.
     * Chromium only (CDP); other browsers get it injected on first wait instead.
     */
    public static void install(WebDriver driver) {
        if (driver instanceof ChromiumDriver) {
            try {
                ((ChromiumDriver) driver).executeCdpCommand("Page.addScriptToEvaluateOnNewDocument",
                        Collections.singletonMap("source", HOOK_SCRIPT));
            } catch (Exception e) {
                logger.debug("Smart wait hook not registered via CDP: {}", e.getMessage());
            }
        }
    }

    /** The driver as scripts and keywords should see it: lookups go through smart wait. */
    public static WebDriver decorate(WebDriver driver) {
        if (driver == null) {
            return null;
        }
        if (unwrap(driver) != driver) {
            return driver; // already decorated
        }
        return DECORATED.computeIfAbsent(driver,
                raw -> new EventFiringDecorator<WebDriver>(new Listener(raw)).decorate(raw));
    }

    /** The undecorated driver behind {@code driver}, or {@code driver} itself. */
    public static WebDriver unwrap(WebDriver driver) {
        if (driver == null) {
            return null;
        }
        synchronized (DECORATED) {
            for (Map.Entry<WebDriver, WebDriver> e : DECORATED.entrySet()) {
                if (e.getValue() == driver) {
                    return e.getKey();
                }
            }
        }
        return driver;
    }

    public static void doSmartWait(WebDriver driver) {
        doSmartWait(driver, DEFAULT_TIMEOUT_MS);
    }

    public static void doSmartWait(WebDriver driver, long timeoutMs) {
        if (!isEnabled() || !(driver instanceof JavascriptExecutor)) {
            return;
        }
        JavascriptExecutor js = (JavascriptExecutor) driver;
        try {
            Object ready = js.executeAsyncScript(WAIT_SCRIPT, timeoutMs);
            if (Boolean.FALSE.equals(ready)) {
                // Page loaded before the hook was registered (non-Chromium, new window, ...):
                // install it now so later lookups on this page can wait.
                js.executeScript(HOOK_SCRIPT);
                js.executeAsyncScript(WAIT_SCRIPT, timeoutMs);
            }
        } catch (Exception e) {
            // Same as Katalon: smart wait is best effort and never fails the lookup.
            logger.trace("Smart wait skipped: {}", e.getMessage());
        }
    }

    public static final class Listener implements WebDriverListener {
        private final WebDriver raw;

        Listener(WebDriver raw) {
            this.raw = raw;
        }

        @Override
        public void beforeFindElement(WebDriver driver, By locator) {
            doSmartWait(raw);
        }

        @Override
        public void beforeFindElements(WebDriver driver, By locator) {
            doSmartWait(raw);
        }

        @Override
        public void beforeFindElement(WebElement element, By locator) {
            doSmartWait(raw);
        }

        @Override
        public void beforeFindElements(WebElement element, By locator) {
            doSmartWait(raw);
        }
    }
}
