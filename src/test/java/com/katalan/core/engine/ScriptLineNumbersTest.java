package com.katalan.core.engine;

import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Stack-trace line numbers must match the test case file (order #3065 reported line 64 for line 54). */
class ScriptLineNumbersTest {

    private static final String SCRIPT = String.join("\n",
            "import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject",
            "import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI",
            "import internal.GlobalVariable as GlobalVariable",
            "",
            "def a = 1",
            "throw new IllegalStateException('line 6')");

    @Test
    void userLinesKeepTheirNumbers() {
        String processed = GroovyScriptExecutor.preprocessKatalonScript(SCRIPT);
        String[] in = SCRIPT.split("\n", -1);
        String[] out = processed.split("\n", -1);
        assertEquals(in[4], out[4]);
        assertEquals(in[5], out[5]);
    }

    @Test
    void runtimeErrorReportsTheOriginalLine() {
        String processed = GroovyScriptExecutor.preprocessKatalonScript(SCRIPT);
        Script s = new GroovyShell().parse(processed, "60 - View Detail");
        try {
            s.run();
        } catch (IllegalStateException e) {
            StackTraceElement frame = java.util.Arrays.stream(e.getStackTrace())
                    .filter(f -> "60 - View Detail".equals(f.getFileName())).findFirst().orElseThrow();
            assertEquals(6, frame.getLineNumber());
            return;
        }
        assertTrue(false, "script should throw");
    }
}
