package com.katalan.core.logging;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Global keyword execution logger — records every statement/keyword call in
 * Katalon XmlKeywordLogger format for emission into execution0.log.
 * 
 * Thread-safe singleton that buffers log records during test execution.
 */
public class XmlKeywordLogger {
    
    private static final XmlKeywordLogger INSTANCE = new XmlKeywordLogger();
    
    private final Queue<LogRecord> records = new ConcurrentLinkedQueue<>();
    private final AtomicInteger sequenceCounter = new AtomicInteger(0);
    private final ThreadLocal<Integer> nestedLevel = ThreadLocal.withInitial(() -> 0);
    
    private XmlKeywordLogger() {}
    
    public static XmlKeywordLogger getInstance() {
        return INSTANCE;
    }
    
    /**
     * Clear all buffered log records (call at suite start).
     */
    public void reset() {
        records.clear();
        sequenceCounter.set(0);
        nestedLevel.set(0);
    }
    
    /**
     * Get all buffered log records in insertion order.
     */
    public List<LogRecord> getRecords() {
        return new ArrayList<>(records);
    }
    
    /**
     * Log suite start.
     */
    public void startSuite(String suiteName, String suiteId, Map<String, String> properties) {
        logRecord("START", "startSuite", "Start Test Suite : " + suiteId, 0, properties);
    }
    
    /**
     * Log suite end.
     */
    public void endSuite(String suiteName, String suiteId, Map<String, String> properties) {
        logRecord("END", "endSuite", "End Test Suite : " + suiteId, 0, properties);
    }
    
    /**
     * Log RUN_DATA (environment metadata).
     */
    public void logRunData(String key, String value) {
        Map<String, String> props = new LinkedHashMap<>();
        props.put(key, value != null ? value : "");
        logRecord("RUN_DATA", "logMessage", key + " = " + (value != null ? value : ""), 0, props);
    }
    
    /**
     * Log test case start.
     */
    public void startTest(String testCaseName, String testCaseId, Map<String, String> properties) {
        nestedLevel.set(1); // Reset to test-case level
        logRecord("START", "startTest", "Start Test Case : " + testCaseId, 1, properties);
    }
    
    /**
     * Log test case end.
     */
    public void endTest(String testCaseName, String testCaseId, Map<String, String> properties) {
        logRecord("END", "endTest", "End Test Case : " + testCaseId, 0, properties);
        nestedLevel.set(0);
    }
    
    /**
     * Log keyword/action start (e.g. WebUI.click, println, variable assignment).
     * Increments nested level for the duration of this keyword.
     */
    public void startKeyword(String actionText, Integer startLine, Integer stepIndex) {
        int currentNested = nestedLevel.get();
        Map<String, String> props = new LinkedHashMap<>();
        if (startLine != null) props.put("startLine", String.valueOf(startLine));
        if (stepIndex != null) props.put("stepIndex", String.valueOf(stepIndex));
        logRecord("START", "startKeyword", "Start action : " + actionText, currentNested, props);
        nestedLevel.set(currentNested + 1);
    }
    
    /**
     * Log keyword/action end.
     * Decrements nested level.
     */
    public void endKeyword(String actionText) {
        int currentNested = nestedLevel.get() - 1;
        if (currentNested < 0) currentNested = 0;
        nestedLevel.set(currentNested);
        logRecord("END", "endKeyword", "End action : " + actionText, currentNested, Collections.emptyMap());
    }
    
    /**
     * Log a general message (INFO, PASSED, FAILED, WARNING, etc.).
     */
    public void logMessage(String level, String message, Map<String, String> properties) {
        int currentNested = nestedLevel.get();
        logRecord(level, "logMessage", message, currentNested, properties);
    }
    
    /**
     * Log listener action start (beforeSuite, afterTestCase, etc.).
     */
    public void startListener(String listenerName) {
        int currentNested = nestedLevel.get();
        logRecord("START", "startKeyword", "Start listener action : " + listenerName, currentNested, Collections.emptyMap());
        nestedLevel.set(currentNested + 1);
    }
    
    /**
     * Log listener action end.
     */
    public void endListener(String listenerName) {
        int currentNested = nestedLevel.get() - 1;
        if (currentNested < 0) currentNested = 0;
        nestedLevel.set(currentNested);
        logRecord("END", "endKeyword", "End listener action : " + listenerName, currentNested, Collections.emptyMap());
    }
    
    /**
     * Internal: append a log record to the buffer.
     */
    private void logRecord(String level, String method, String message, int nestedLvl, Map<String, String> properties) {
        LogRecord rec = new LogRecord();
        rec.timestamp = Instant.now();
        rec.sequence = sequenceCounter.getAndIncrement();
        rec.level = level;
        rec.method = method;
        rec.thread = 1;
        rec.message = message;
        rec.nestedLevel = nestedLvl;
        rec.properties = properties != null ? new LinkedHashMap<>(properties) : new LinkedHashMap<>();
        records.add(rec);
    }
    
    // ========== BDD-specific logging (Cucumber/Gherkin) ==========
    
    /**
     * Log BDD scenario start (Katalon-style).
     * Properties: BDD_TESTCASE_TYPE, BDD_TESTCASE_NAME, BDD_FEATURE_NAME, etc.
     */
    public void startBddScenario(String scenarioName, String featureName, int line, String uuid) {
        nestedLevel.set(2); // BDD scenarios are nested under test case
        Map<String, String> props = new LinkedHashMap<>();
        props.put("BDD_TESTCASE_TYPE", "scenario");
        props.put("BDD_TESTCASE_DESCRIPTION", "");
        props.put("BDD_TESTCASE_LINE", String.valueOf(line));
        props.put("BDD_TESTRUN_UUID", uuid);
        props.put("BDD_TESTCASE_NAME", scenarioName);
        props.put("BDD_FEATURE_NAME", featureName);
        logRecord("START", "startTest", "Start Test Case : SCENARIO " + scenarioName, 2, props);
    }
    
    /**
     * Log BDD scenario end.
     */
    public void endBddScenario(String scenarioName) {
        logRecord("END", "endTest", "End Test Case : SCENARIO " + scenarioName, 2, Collections.emptyMap());
    }
    
    /**
     * Log BDD step start (Given/When/Then/And).
     * Properties: BDD_STEP_LINE, BDD_STEP_NAME, BDD_STEP_KEYWORD, BDD_STEP_UUID
     */
    public void startBddStep(String keyword, String stepName, int line, String uuid) {
        int currentNested = nestedLevel.get();
        Map<String, String> props = new LinkedHashMap<>();
        props.put("BDD_STEP_LINE", String.valueOf(line));
        props.put("BDD_STEP_NAME", stepName);
        props.put("BDD_STEP_KEYWORD", keyword + " "); // Add trailing space to match Katalon format
        props.put("BDD_STEP_UUID", uuid);
        logRecord("START", "startKeyword", "Start action : " + keyword + " " + stepName, currentNested, props);
        nestedLevel.set(currentNested + 1);
    }
    
    /**
     * Log BDD step end.
     */
    public void endBddStep(String keyword, String stepName) {
        int currentNested = nestedLevel.get() - 1;
        if (currentNested < 0) currentNested = 0;
        nestedLevel.set(currentNested);
        logRecord("END", "endKeyword", "End action : " + keyword + " " + stepName, currentNested, Collections.emptyMap());
    }
    
    /**
     * A single log record entry.
     */
    public static class LogRecord {
        public Instant timestamp;
        public int sequence;
        public String level;
        public String method;
        public int thread;
        public String message;
        public int nestedLevel;
        public Map<String, String> properties;
    }
}
