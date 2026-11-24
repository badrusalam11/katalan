package com.katalan.core.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.katalan.core.model.TestCase;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prints the list of test cases a suite is about to run, in execution order, as ONE stdout line:
 * <pre>KATALAN_PLAN {"v":1,"suite":"My Suite","total":3,"testCases":["Test Cases/A","Test Cases/B","Test Cases/A"]}</pre>
 * A launching process (CI, dashboard, bot) can then show test cases that have not started yet, and
 * tell "3 of 7 done, now running X" without waiting for each test case to print its own RUNNING line.
 * Ids are exactly the ones printed later in the "Test Cases/...........RUNNING" lines. A test case that
 * appears twice in the suite appears twice here. Never throws.
 */
public final class ExecutionPlan {

    public static final String STDOUT_PREFIX = "KATALAN_PLAN ";
    static final int MAX_LINE_BYTES = 60_000;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExecutionPlan() {}

    public static void emit(String suiteName, List<TestCase> testCases) {
        try {
            System.out.println(STDOUT_PREFIX + toJson(suiteName, ids(testCases)));
            System.out.flush();
        } catch (Throwable ignored) {
            // a missing plan only means the dashboard shows test cases as they start
        }
    }

    static List<String> ids(List<TestCase> testCases) {
        List<String> ids = new ArrayList<>();
        if (testCases != null) {
            for (TestCase tc : testCases) ids.add(tc.getId());
        }
        return ids;
    }

    /** One-line JSON; when it would be too long only the count is sent (truncated=true). */
    static String toJson(String suiteName, List<String> ids) throws Exception {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("v", 1);
        plan.put("suite", suiteName);
        plan.put("total", ids.size());
        plan.put("testCases", ids);
        String json = MAPPER.writeValueAsString(plan);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_LINE_BYTES) {
            plan.put("testCases", new ArrayList<String>());
            plan.put("truncated", true);
            json = MAPPER.writeValueAsString(plan);
        }
        return json;
    }
}
