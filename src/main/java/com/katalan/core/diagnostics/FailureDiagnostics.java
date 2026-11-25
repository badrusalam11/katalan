package com.katalan.core.diagnostics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.katalan.core.config.FailureDiagnosticsConfig;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collects evidence from the still-open browser when a test case fails for good and classifies the
 * likely cause (Test Data / Locator / Network / Resource), see docs/FAILURE_DIAGNOSTICS.md.
 *
 * <p>Guarantees: never throws, never changes the test result, read-only towards the page, bounded by
 * {@link FailureDiagnosticsConfig#getTimeoutMs()}, and costs nothing for passing test cases.</p>
 */
public final class FailureDiagnostics {

    private static final Logger logger = LoggerFactory.getLogger(FailureDiagnostics.class);

    public static final String STDOUT_PREFIX = "KATALAN_FAILURE ";
    /** Separate stdout line with request/response bodies of the API calls before a failure. */
    public static final String NETWORK_PREFIX = "KATALAN_NET ";
    /** Key in the diagnosis map holding the calls until {@link #emit} splits them off. */
    public static final String NETWORK_CALLS = "networkCalls";
    private static final int MAX_NETWORK_LINE_BYTES = 60000;
    public static final int SCHEMA_VERSION = 1;
    private static final int MAX_LINE_BYTES = 8000;
    // Element waits usually time out after ~30 s, so a request that failed right before the wait
    // started must still be inside the window.
    private static final long NETWORK_WINDOW_MS = 90_000;
    static final double CPU_OVERLOADED = 90.0;
    static final double RAM_OVERLOADED = 95.0;

    public static final String CAT_TEST_DATA = "TEST_DATA";
    public static final String CAT_LOCATOR = "LOCATOR";
    public static final String CAT_NETWORK = "NETWORK";
    public static final String CAT_RESOURCE = "RESOURCE";
    public static final String CAT_OTHER = "OTHER";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final ExecutorService BROWSER_PROBE = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "katalan-failure-diagnostics");
        t.setDaemon(true);
        return t;
    });

    /**
     * Request paths that already failed in an earlier failed test case of this run. Dev environments often
     * have endpoints that return 500 on every page; the second time one shows up it is marked
     * {@code seenBefore} so it is not mistaken for the cause.
     */
    private static final java.util.Set<String> FAILED_PATHS_SEEN = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static volatile ScheduledExecutorService cpuSampler;

    /**
     * Endpoints known to fail on many unrelated pages (a broken global widget, a polling call...), given
     * by the launching process: {@code -Dkatalan.failureDiagnostics.backgroundRequests=/api/a,/api/b}.
     * Their failures are reported as background, never as the cause. Empty by default.
     */
    static java.util.Set<String> backgroundRequests() {
        String raw = System.getProperty("katalan.failureDiagnostics.backgroundRequests", "");
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String p : raw.split(",")) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
    private static volatile double lastCpuPct = -1;

    private FailureDiagnostics() {}

    // ------------------------------------------------------------------ machine

    /**
     * Sample system CPU every 5 s in the background (one cheap OS call). The JDK reports CPU load
     * "since the previous call", so a periodic sampler is what makes the value mean "the last few
     * seconds" at the moment a test fails, instead of "since the run started".
     */
    public static synchronized void startCpuSampler() {
        if (cpuSampler != null) return;
        cpuSampler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "katalan-cpu-sampler");
            t.setDaemon(true);
            return t;
        });
        cpuSampler.scheduleAtFixedRate(() -> {
            double v = readSystemCpuLoad();
            if (v >= 0) lastCpuPct = v * 100.0;
        }, 0, 5, TimeUnit.SECONDS);
    }

    @SuppressWarnings("deprecation")
    private static double readSystemCpuLoad() {
        try {
            java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean) {
                return ((com.sun.management.OperatingSystemMXBean) os).getSystemCpuLoad();
            }
        } catch (Throwable ignored) { }
        return -1;
    }

    @SuppressWarnings("deprecation")
    static Map<String, Object> machineInfo() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cpuPct", lastCpuPct >= 0 ? (int) Math.round(lastCpuPct) : null);
        Integer ram = null;
        try {
            // Linux: "free" excludes page cache and always looks full; MemAvailable is the real headroom.
            Path meminfo = Path.of("/proc/meminfo");
            if (Files.isReadable(meminfo)) {
                long total = -1, avail = -1;
                for (String l : Files.readAllLines(meminfo)) {
                    if (l.startsWith("MemTotal:")) total = Long.parseLong(l.replaceAll("\\D", ""));
                    if (l.startsWith("MemAvailable:")) avail = Long.parseLong(l.replaceAll("\\D", ""));
                }
                if (total > 0 && avail >= 0) ram = (int) Math.round(100.0 * (total - avail) / total);
            }
            if (ram == null && System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
                ram = macRamPct();
            }
            // Windows: the JDK's "free" is GlobalMemoryStatusEx.ullAvailPhys, which already counts
            // standby/cache as available, so it is a fair number there.
            if (ram == null) {
                java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
                if (os instanceof com.sun.management.OperatingSystemMXBean) {
                    com.sun.management.OperatingSystemMXBean sun = (com.sun.management.OperatingSystemMXBean) os;
                    long total = sun.getTotalPhysicalMemorySize();
                    long free = sun.getFreePhysicalMemorySize();
                    if (total > 0) ram = (int) Math.round(100.0 * (total - free) / total);
                }
            }
        } catch (Throwable ignored) { }
        m.put("ramPct", ram);
        return m;
    }

    /**
     * macOS: the JDK's free memory excludes inactive/purgeable pages and always reads ~100%.
     * Same formula as Activity Monitor's "available": free + inactive + speculative + purgeable.
     */
    @SuppressWarnings("deprecation")
    private static Integer macRamPct() {
        try {
            Process p = new ProcessBuilder("vm_stat").redirectErrorStream(true).start();
            if (!p.waitFor(500, TimeUnit.MILLISECONDS)) { p.destroyForcibly(); return null; }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Matcher ps = Pattern.compile("page size of (\\d+) bytes").matcher(out);
            long pageSize = ps.find() ? Long.parseLong(ps.group(1)) : 4096;
            long avail = 0;
            for (String key : new String[]{"Pages free", "Pages inactive", "Pages speculative", "Pages purgeable"}) {
                Matcher m = Pattern.compile(key + ":\\s+(\\d+)").matcher(out);
                if (m.find()) avail += Long.parseLong(m.group(1));
            }
            java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            long total = os instanceof com.sun.management.OperatingSystemMXBean
                    ? ((com.sun.management.OperatingSystemMXBean) os).getTotalPhysicalMemorySize() : -1;
            if (total <= 0 || avail <= 0) return null;
            return (int) Math.round(100.0 * Math.max(0, total - avail * pageSize) / total);
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ entry point

    /**
     * Run the diagnosis. Returns the JSON-ready map (never null) and never throws.
     *
     * @param kind "FAILED" or "ERROR"
     */
    public static Map<String, Object> diagnose(WebDriver driver, boolean headless, String testCaseId,
                                               int attempt, int maxAttempts, String kind,
                                               Throwable error, String errorMessage, String stackTrace,
                                               String screenshotPath) {
        long t0 = System.currentTimeMillis();
        long deadline = t0 + FailureDiagnosticsConfig.getTimeoutMs();
        List<String> diagErrors = new ArrayList<>();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("v", SCHEMA_VERSION);
        out.put("ts", OffsetDateTime.now().toString());
        out.put("testCaseId", testCaseId);
        out.put("attempt", attempt);
        out.put("maxAttempts", maxAttempts);
        out.put("kind", kind);

        ErrorInfo err = ErrorInfo.parse(error, errorMessage, stackTrace);
        Map<String, Object> browser = new LinkedHashMap<>();
        Map<String, Object> probe = null;
        Map<String, Object> machine = machineInfo();

        try {
            String driverKind = driverKind(driver);
            browser.put("driverKind", driverKind);
            browser.put("headless", driver == null ? null : headless);
            if (driver == null) {
                browser.put("alive", null);
            } else {
                long remaining = Math.max(50, deadline - System.currentTimeMillis());
                Future<Map<String, Object>> f = BROWSER_PROBE.submit(
                        () -> probeBrowser(driver, driverKind, err, deadline, diagErrors, browser));
                try {
                    probe = f.get(remaining, TimeUnit.MILLISECONDS);
                } catch (TimeoutException te) {
                    f.cancel(true);
                    diagErrors.add("browser: timeout setelah " + FailureDiagnosticsConfig.getTimeoutMs() + " ms");
                }
            }
        } catch (Throwable t) {
            diagErrors.add("browser: " + t.getClass().getSimpleName() + ": " + shorten(t.getMessage(), 150));
        }

        if (probe != null) {
            Object pe = probe.remove("errors");
            if (pe instanceof List) for (Object o : (List<?>) pe) diagErrors.add(String.valueOf(o));
            Map<String, Object> net = asMap(probe.get("network"));
            if (net != null && StepClock.actionKeyword() != null) net.put("stepAction", StepClock.actionKeyword());
            if (net != null) {
                List<String> paths = new ArrayList<>();
                for (Object o : asList(net.get("failed"))) {
                    Map<String, Object> r = asMap(o);
                    if (r == null) continue;
                    String path = String.valueOf(r.get("path"));
                    if (FAILED_PATHS_SEEN.contains(path)) r.put("seenBefore", true);
                    if (backgroundRequests().contains(path)) r.put("background", true);
                    paths.add(path);
                }
                FAILED_PATHS_SEEN.addAll(paths);
            }
        }

        Verdict v;
        try {
            boolean probeTimedOut = diagErrors.stream().anyMatch(x -> x.startsWith("browser: timeout"));
            v = Classifier.classify(err, browser, probe, machine, probeTimedOut);
        } catch (Throwable t) {
            diagErrors.add("classify: " + t.getClass().getSimpleName());
            v = Verdict.unknown(err);
        }

        if (StepClock.windowStart() > 0) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("keyword", StepClock.failedKeyword());
            step.put("action", StepClock.actionKeyword());
            step.put("windowSec", Math.round((System.currentTimeMillis() - StepClock.windowStart()) / 1000.0));
            out.put("stepWindow", step);
        }
        out.put("category", v.category);
        out.put("code", v.code);
        out.put("confidence", v.confidence);
        out.put("summary", v.summary);
        out.put("evidence", v.evidence);
        out.put("error", err.toMap());
        Map<String, Object> page = probe == null ? null : asMap(probe.get("page"));
        if (page != null) {
            browser.put("url", page.get("url"));
            browser.put("title", page.get("title"));
            browser.put("readyState", page.get("readyState"));
            browser.put("errorPage", page.get("errorPage"));
            Map<String, Object> vp = new LinkedHashMap<>();
            vp.put("w", page.get("w"));
            vp.put("h", page.get("h"));
            browser.put("viewport", vp);
            browser.put("userAgent", page.get("ua"));
        }
        out.put("browser", browser);
        if (probe != null) {
            Map<String, Object> net = asMap(probe.get("network"));
            out.put("network", net);
            if (net != null && Boolean.TRUE.equals(net.get("bufferFull"))) diagErrors.add("network: buffer resource timing penuh (250)");
            if (net != null && Boolean.FALSE.equals(net.get("statusKnown"))) diagErrors.add("network: status code tidak tersedia (Chrome < 109 / lintas origin)");
            // statusKnown == null: no request finished inside the window - nothing to say
            Map<String, Object> lc = new LinkedHashMap<>();
            lc.put("literal", err.literal);
            lc.put("literalKind", err.literalKind);
            lc.put("matches", probe.get("matches"));
            lc.put("visibleMatches", probe.get("visibleMatches"));
            lc.put("firstMatch", probe.get("firstMatch"));
            lc.put("obscuredBy", probe.get("obscuredBy"));
            lc.put("candidates", probe.get("candidates"));
            lc.put("visibleButtons", probe.get("visibleButtons"));
            out.put("locatorCheck", lc);
            Map<String, Object> render = new LinkedHashMap<>();
            render.put("untranslatedKeys", probe.get("untranslatedKeys"));
            render.put("untranslatedCount", probe.get("untranslatedCount"));
            out.put("pageRender", render);
            out.put("lastAction", probe.get("lastAction"));
            out.put("visibleMessages", probe.get("visibleMessages"));
            out.put("openDialogs", probe.get("openDialogs"));
            // Not part of the 8 KB failure line: emitted as its own line by emitNetwork (bodies are large)
            if (probe.get("networkCalls") != null) out.put(NETWORK_CALLS, probe.get("networkCalls"));
        }
        out.put("machine", machine);
        out.put("screenshotPath", screenshotPath);
        out.put("diagnosticsMs", System.currentTimeMillis() - t0);
        out.put("diagnosticsErrors", diagErrors);
        // v1 shows facts only; category/code/confidence/summary stay in the JSON for later measurement
        out.put("failedAt", EvidenceFacts.failedAt(out));
        out.put("sourceFrames", sourceFrames(stackTrace, 4));
        out.put("facts", EvidenceFacts.build(out));
        return out;
    }

    /**
     * A stack frame of project code: a keyword/include file ({@code at pkg.Kw.m(Kw.groovy:12)}) or a test
     * case script, which Groovy compiles under the test case name without extension
     * ({@code at 60_-_View_Detail.run(60 - View Detail:64)}).
     */
    private static final Pattern FRAME = Pattern.compile("at\\s+([\\w.$-]+)\\(([^():\\r\\n]+?):(\\d+)\\)");

    /**
     * Project script frames of the stack trace ("Keyword.groovy:823 · pkg.Keyword.method()", or
     * "Test case \"60 - View Detail\" baris 64 (tab Script)"), the same "Source File(s) Involved" the PDF report shows.
     * Engine/library frames are left out. Never null.
     */
    public static List<String> sourceFrames(String stackTrace, int max) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        if (stackTrace == null) return new ArrayList<>();
        Matcher m = FRAME.matcher(stackTrace);
        while (m.find() && out.size() < max) {
            String fq = m.group(1);
            String file = m.group(2).trim();
            if (isLibraryFrame(fq)) continue;
            if (file.endsWith(".groovy")) out.add(file + ":" + m.group(3) + " \u00b7 " + fq + "()");
            else if (isTestCaseScript(file)) out.add(testCaseFrame(file, m.group(3)));
        }
        return new ArrayList<>(out);
    }

    /** A test case script frame as people find it in Katalon: test case name, line, Script tab. */
    public static String testCaseFrame(String testCaseName, String line) {
        return "Test case \"" + testCaseName + "\" baris " + line + " (tab Script)";
    }

    /** Engine, Groovy runtime and library frames, never the user's own code. */
    public static boolean isLibraryFrame(String fq) {
        String low = fq.toLowerCase(Locale.ROOT);
        return low.startsWith("com.kms.") || low.startsWith("com.katalan.") || low.startsWith("org.")
                || low.startsWith("io.cucumber.") || low.startsWith("java.") || low.startsWith("jdk.")
                || low.startsWith("sun.") || low.startsWith("groovy.") || low.startsWith("picocli.");
    }

    /** A test case script source name: no file extension ("Unknown Source" and "Native Method" excluded). */
    public static boolean isTestCaseScript(String file) {
        return !file.isEmpty() && !file.contains(".") && !"Unknown Source".equals(file) && !"Native Method".equals(file);
    }

    /** Print the single stdout line a launching process can pick up. Never throws. */
    public static void emit(Map<String, Object> diagnosis) {
        try {
            System.out.println(STDOUT_PREFIX + toBoundedJson(diagnosis));
            System.out.flush();
        } catch (Throwable t) {
            logger.debug("Could not emit failure diagnostics: {}", t.getMessage());
        }
        emitNetwork(diagnosis);
    }

    /**
     * {@code KATALAN_NET {testCaseId, ts, attempt, calls}}: payload/response of the API calls before the
     * failure, paired with the failure line by testCaseId + ts. Its own line because bodies do not fit the
     * 8 KB failure line. Never throws.
     */
    @SuppressWarnings("unchecked")
    static void emitNetwork(Map<String, Object> diagnosis) {
        try {
            List<Object> calls = asList(diagnosis.get(NETWORK_CALLS));
            if (calls.isEmpty()) return;
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("testCaseId", diagnosis.get("testCaseId"));
            line.put("ts", diagnosis.get("ts"));
            line.put("attempt", diagnosis.get("attempt"));
            List<Map<String, Object>> copy = new ArrayList<>();
            for (Object o : calls) {
                Map<String, Object> m = asMap(o);
                if (m != null) copy.add(new LinkedHashMap<>(m));
            }
            line.put("calls", copy);
            String json = MAPPER.writeValueAsString(line);
            // too big: shorten bodies, then drop the oldest calls (the ones closest to the failure stay)
            int[][] limits = {{2000, 4000}, {500, 1500}};
            for (int i = 0; json.getBytes(StandardCharsets.UTF_8).length > MAX_NETWORK_LINE_BYTES && i < limits.length; i++) {
                for (Map<String, Object> m : copy) {
                    if (m.get("req") instanceof String) m.put("req", shorten((String) m.get("req"), limits[i][0]));
                    if (m.get("res") instanceof String) m.put("res", shorten((String) m.get("res"), limits[i][1]));
                }
                json = MAPPER.writeValueAsString(line);
            }
            while (json.getBytes(StandardCharsets.UTF_8).length > MAX_NETWORK_LINE_BYTES && copy.size() > 1) {
                copy.remove(0);
                json = MAPPER.writeValueAsString(line);
            }
            System.out.println(NETWORK_PREFIX + json);
            System.out.flush();
        } catch (Throwable t) {
            logger.debug("Could not emit network calls: {}", t.getMessage());
        }
    }

    /** JSON on one line, at most {@link #MAX_LINE_BYTES}; drops the least useful fields first. */
    @SuppressWarnings("unchecked")
    static String toBoundedJson(Map<String, Object> diagnosis) throws Exception {
        Map<String, Object> d = new LinkedHashMap<>(diagnosis);
        d.remove(NETWORK_CALLS);
        String json = MAPPER.writeValueAsString(d);
        String[] dropOrder = {"openDialogs", "visibleMessages", "network", "locatorCheck", "evidence"};
        for (int i = 0; json.getBytes(StandardCharsets.UTF_8).length > MAX_LINE_BYTES && i < dropOrder.length; i++) {
            d.put(dropOrder[i], null);
            List<String> errs = new ArrayList<>((List<String>) d.getOrDefault("diagnosticsErrors", Collections.emptyList()));
            errs.add("dipotong: " + dropOrder[i]);
            d.put("diagnosticsErrors", errs);
            json = MAPPER.writeValueAsString(d);
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_LINE_BYTES) {
            Map<String, Object> e = new LinkedHashMap<>((Map<String, Object>) d.get("error"));
            e.put("message", shorten((String) e.get("message"), 300));
            d.put("error", e);
            d.put("summary", shorten((String) d.get("summary"), 400));
            json = MAPPER.writeValueAsString(d);
        }
        return json;
    }

    // ------------------------------------------------------------------ browser probe

    private static String probeScript;

    private static synchronized String probeScript() throws Exception {
        if (probeScript == null) {
            try (InputStream in = FailureDiagnostics.class.getResourceAsStream("/katalan/failure-diagnostics.js")) {
                if (in == null) throw new IllegalStateException("failure-diagnostics.js not on classpath");
                probeScript = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return probeScript;
    }

    private static Map<String, Object> probeBrowser(WebDriver driver, String driverKind, ErrorInfo err,
                                                    long deadline, List<String> diagErrors,
                                                    Map<String, Object> browser) throws Exception {
        try {
            driver.getWindowHandle();
            browser.put("alive", true);
        } catch (Exception e) {
            browser.put("alive", false);
            browser.put("deadReason", shorten(e.getMessage(), 200));
            return null;
        }
        if ("appium".equals(driverKind) || !(driver instanceof JavascriptExecutor)) {
            diagErrors.add("dom: dilewati (driver bukan web)");
            return null;
        }
        Duration oldTimeout = null;
        try {
            long remaining = Math.max(100, deadline - System.currentTimeMillis());
            try {
                oldTimeout = driver.manage().timeouts().getScriptTimeout();
                driver.manage().timeouts().scriptTimeout(Duration.ofMillis(remaining));
            } catch (Exception ignored) { }
            Object raw = ((JavascriptExecutor) driver).executeScript(probeScript(),
                    err.locatorBy, err.locatorValue, err.literal, err.literalKind,
                    FailureDiagnosticsConfig.getMaxTextChars(), NETWORK_WINDOW_MS, StepClock.windowStart(),
                    actionBy(), actionValue());
            if (raw == null) return null;
            return MAPPER.readValue(raw.toString(), new TypeReference<LinkedHashMap<String, Object>>() {});
        } finally {
            if (oldTimeout != null) {
                try { driver.manage().timeouts().scriptTimeout(oldTimeout); } catch (Exception ignored) { }
            }
        }
    }

    private static String actionBy() {
        String[] l = StepClock.actionTargetLocator();
        return l == null ? null : l[0];
    }

    private static String actionValue() {
        String[] l = StepClock.actionTargetLocator();
        return l == null ? null : l[1];
    }

    private static String driverKind(WebDriver driver) {
        if (driver == null) return null;
        String n = driver.getClass().getName().toLowerCase(Locale.ROOT);
        if (n.contains("appium")) return "appium";
        if (n.contains("chrome")) return "chrome";
        if (n.contains("firefox") || n.contains("gecko")) return "firefox";
        if (n.contains("edge")) return "edge";
        if (n.contains("safari")) return "safari";
        return "unknown";
    }

    // ------------------------------------------------------------------ helpers

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    static List<Object> asList(Object o) {
        return o instanceof List ? (List<Object>) o : Collections.emptyList();
    }

    static String shorten(String s, int max) {
        if (s == null) return null;
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() > max ? s.substring(0, max) + "\u2026" : s;
    }

    // ------------------------------------------------------------------ error parsing

    /** What the failure message tells us: exception type, keyword, locator, literal, script line. */
    static final class ErrorInfo {
        String type;
        String message;
        String keyword;
        String locatorBy;
        String locatorValue;
        String literal;
        String literalKind;
        String source;
        boolean assertion;
        boolean notFound;
        boolean crashed;

        private static final Pattern ROOT_CAUSE = Pattern.compile("Root cause:\\s*(?:[\\w.]+\\.)?(\\w+(?:Exception|Error))");
        private static final Pattern LOCATED_BY = Pattern.compile("located by '(.+?)' (?:not found|not visible|not clickable|is not)", Pattern.DOTALL);
        private static final Pattern NOT_PRESENT = Pattern.compile("Element not present: (\\S.*?)\\s*$", Pattern.MULTILINE);
        // "Unable to click on object '//xpath' (Root cause: ...)" - katalan prints the locator for objects without id
        private static final Pattern ON_OBJECT = Pattern.compile("(?:on|of|verify) object '(.+?)'(?: \\(Root cause| is | to |$)", Pattern.MULTILINE);
        private static final Pattern BY_PREFIX = Pattern.compile("^By\\.(xpath|cssSelector|css selector|id|name|className|linkText|partialLinkText|tagName):\\s*(.*)$", Pattern.DOTALL);
        private static final Pattern KEYWORD = Pattern.compile("Unable to ([a-z][a-zA-Z ]*?) (?:on |of |to |for )?(?:the )?object");
        private static final Pattern USER_FRAME = Pattern.compile("at\\s+([\\w.$]+)\\(([\\w$]+\\.groovy):(\\d+)\\)");
        private static final Pattern[] LITERAL = {
                Pattern.compile("(?:text\\(\\)|\\.|normalize-space\\([^)]*\\))\\s*=\\s*'([^']+)'"),
                Pattern.compile("(?:text\\(\\)|\\.|normalize-space\\([^)]*\\))\\s*=\\s*\"([^\"]+)\""),
                Pattern.compile("contains\\(\\s*(?:text\\(\\)|\\.|normalize-space\\([^)]*\\))\\s*,\\s*'([^']+)'\\s*\\)"),
                Pattern.compile("contains\\(\\s*(?:text\\(\\)|\\.|normalize-space\\([^)]*\\))\\s*,\\s*\"([^\"]+)\"\\s*\\)"),
                Pattern.compile("@(?:value|title|aria-label|placeholder)\\s*=\\s*'([^']+)'"),
                Pattern.compile("@(?:value|title|aria-label|placeholder)\\s*=\\s*\"([^\"]+)\""),
        };
        private static final Pattern AMOUNT = Pattern.compile("^(?:Rp\\.?\\s*)?\\d{1,3}(?:[.,]\\d{3})+(?:[.,]\\d{1,2})?$", Pattern.CASE_INSENSITIVE);
        private static final Pattern DATE = Pattern.compile("^\\d{1,4}[/.\\- ](?:\\d{1,2}|[A-Za-z]{3,9})[/.\\- ]\\d{2,4}$");
        private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[\\w.-]+$");
        private static final Pattern DIGIT_RUN = Pattern.compile("\\d{6,}");
        private static final Pattern MESSAGE_WORDS = Pattern.compile(
                "(?i)\\b(has been|have been|successfully|success|succeeded|added|deleted|saved|submitted|updated|"
                        + "failed|berhasil|sukses|gagal|telah|sudah|tersimpan|terkirim|ditambahkan|dihapus|disimpan)\\b");

        static ErrorInfo parse(Throwable error, String errorMessage, String stackTrace) {
            ErrorInfo e = new ErrorInfo();
            String msg = errorMessage != null ? errorMessage : (error != null ? String.valueOf(error.getMessage()) : "");
            String all = msg + "\n" + (stackTrace == null ? "" : stackTrace);
            e.message = shorten(withoutStackFrames(msg), 500);
            Matcher rc = ROOT_CAUSE.matcher(all);
            e.type = rc.find() ? rc.group(1) : (error != null ? error.getClass().getSimpleName() : null);
            Matcher kw = KEYWORD.matcher(msg);
            if (kw.find()) e.keyword = kw.group(1).trim();

            String loc = null;
            Matcher lb = LOCATED_BY.matcher(all);
            if (lb.find()) loc = lb.group(1);
            if (loc == null) {
                Matcher np = NOT_PRESENT.matcher(all);
                if (np.find()) loc = np.group(1);
            }
            if (loc == null) {
                Matcher oo = ON_OBJECT.matcher(msg);
                if (oo.find() && (oo.group(1).startsWith("/") || oo.group(1).startsWith("(") || oo.group(1).startsWith("By."))) loc = oo.group(1);
            }
            if (loc != null) {
                loc = loc.trim();
                Matcher bp = BY_PREFIX.matcher(loc);
                if (bp.matches()) {
                    String by = bp.group(1);
                    e.locatorValue = bp.group(2).trim();
                    e.locatorBy = by.equals("xpath") ? "xpath"
                            : (by.startsWith("css") ? "css" : by);
                } else {
                    e.locatorValue = loc;
                    e.locatorBy = (loc.startsWith("/") || loc.startsWith("(")) ? "xpath" : "css";
                }
                if ("id".equals(e.locatorBy)) { e.locatorBy = "css"; e.locatorValue = "#" + e.locatorValue; }
                if (!"xpath".equals(e.locatorBy) && !"css".equals(e.locatorBy)) e.locatorBy = null;
                for (Pattern p : LITERAL) {
                    Matcher m = p.matcher(e.locatorValue);
                    if (m.find()) { e.literal = m.group(1).trim(); break; }
                }
                if (e.literal != null && !e.literal.isEmpty()) e.literalKind = literalKind(e.literal);
                else e.literal = null;
            }

            String low = all.toLowerCase(Locale.ROOT);
            e.crashed = low.contains("session deleted") || low.contains("tab crashed")
                    || low.contains("chrome not reachable") || low.contains("not connected to devtools")
                    || low.contains("outofmemory") || low.contains("invalid session id");
            String lowMsg = msg.toLowerCase(Locale.ROOT);
            e.notFound = (e.type != null && (e.type.contains("NotFound") || e.type.contains("NoSuchElement")))
                    || lowMsg.contains("not found") || lowMsg.contains("not present")
                    || lowMsg.contains("unable to locate") || lowMsg.contains("no such element")
                    || lowMsg.contains("waiting for visibility") || lowMsg.contains("waiting for presence");
            e.assertion = !e.notFound && (error instanceof AssertionError
                    || lowMsg.matches("(?s).*(not match|expected|actual|verify|assert).*"));

            if (stackTrace != null) {
                Matcher m = USER_FRAME.matcher(stackTrace);
                while (m.find()) {
                    String fq = m.group(1).toLowerCase(Locale.ROOT);
                    if (fq.startsWith("com.kms.") || fq.startsWith("com.katalan.") || fq.startsWith("org.")
                            || fq.startsWith("groovy.") || fq.startsWith("java.") || fq.startsWith("io.cucumber.")) continue;
                    String method = m.group(1);
                    int dot = method.lastIndexOf('.');
                    String shortMethod = dot >= 0 ? method.substring(dot + 1) : method;
                    // Script classes are named Script<digits>.groovy; the method name is "run" - keep the file
                    e.source = m.group(2) + ":" + m.group(3) + ("run".equals(shortMethod) ? "" : " \u00b7 " + shortMethod);
                    break;
                }
            }
            return e;
        }

        /** Katalon messages often embed the cause's stack trace; keep only the text before the first frame. */
        static String withoutStackFrames(String msg) {
            if (msg == null) return null;
            StringBuilder sb = new StringBuilder();
            for (String line : msg.split("\\r?\\n")) {
                String t = line.trim();
                if (t.startsWith("at ") || t.startsWith("... ")) break;
                if (sb.length() > 0) sb.append(' ');
                sb.append(t);
            }
            return sb.toString();
        }

                static String literalKind(String lit) {
            String s = lit.trim();
            if (EMAIL.matcher(s).matches()) return "email";
            if (AMOUNT.matcher(s).matches() || s.toLowerCase(Locale.ROOT).startsWith("rp")) return "amount";
            if (DATE.matcher(s).matches()) return "date";
            int letters = s.replaceAll("[^A-Za-z]", "").length();
            int words = s.trim().split("\\s+").length;
            // A value: digits with at most a short prefix ("No. 12345678", "ID 123456")
            if (DIGIT_RUN.matcher(s.replaceAll("[ .\\-]", "")).find() && letters <= 4) return "digits";
            // A sentence the app shows after an action ("Order 12345678 has been created", "Data berhasil
            // disimpan") - as opposed to a menu/button label such as "Settings & Privacy"
            if (words >= 3 && letters >= 8 && (DIGIT_RUN.matcher(s).find() || MESSAGE_WORDS.matcher(s).find()
                    || s.matches(".*[.!?]$") || words >= 6)) return "message";
            return "label";
        }

        boolean isData() {
            return literalKind != null && !"label".equals(literalKind) && !"message".equals(literalKind);
        }

        boolean isMessage() {
            return "message".equals(literalKind);
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", type);
            m.put("message", message);
            m.put("keyword", keyword);
            if (locatorValue != null) {
                Map<String, Object> l = new LinkedHashMap<>();
                l.put("by", locatorBy);
                l.put("value", shorten(locatorValue, 300));
                m.put("locator", l);
            } else {
                m.put("locator", null);
            }
            m.put("source", source);
            return m;
        }
    }

    // ------------------------------------------------------------------ verdict

    static final class Verdict {
        String category;
        String code;
        String confidence;
        String summary;
        List<Map<String, Object>> evidence = new ArrayList<>();

        static Verdict of(String category, String code, String confidence, String summary) {
            Verdict v = new Verdict();
            v.category = category;
            v.code = code;
            v.confidence = confidence;
            v.summary = summary;
            return v;
        }

        static Verdict unknown(ErrorInfo err) {
            return of(CAT_OTHER, "UNKNOWN", "low", withSource(
                    "Penyebab tidak bisa disimpulkan otomatis \u2014 lihat screenshot dan detail teknis.", err));
        }

        static String withSource(String s, ErrorInfo err) {
            return err != null && err.source != null ? s + " (" + err.source + ")" : s;
        }
    }
}
