package com.katalan.core.diagnostics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceFramesTest {

    @Test
    void testCaseScriptFrameIsKept() {
        // order #3065: MissingMethodException thrown directly in the test case script
        String st = "groovy.lang.MissingMethodException: No signature of method ...\n"
                + "at org.codehaus.groovy.runtime.ScriptBytecodeAdapter.unwrap(ScriptBytecodeAdapter.java:72)\n"
                + "at org.codehaus.groovy.vmplugin.v8.IndyInterface.fromCache(IndyInterface.java:321)\n"
                + "at 60_-_View_Detail_Task_Transaksi_KLIRING.run(60 - View Detail Task Transaksi KLIRING:64)\n"
                + "at com.katalan.core.engine.GroovyScriptExecutor.executeScript(GroovyScriptExecutor.java:407)\n"
                + "at picocli.CommandLine.execute(CommandLine.java:2170)\n";
        assertEquals(List.of("Test case \"60 - View Detail Task Transaksi KLIRING\" baris 64 (tab Script)"),
                FailureDiagnostics.sourceFrames(st, 4));
    }

    @Test
    void keywordFrameThenTestCaseFrame() {
        String st = "at fund_transfer.RTGS.MakerViewDetail(RTGS.groovy:120)\n"
                + "at 01_-_Maker.run(01 - Maker:33)\n"
                + "at java.base/java.lang.Thread.run(Thread.java:829)\n"
                + "at sun.reflect.Foo.invoke(Unknown Source)\n";
        assertEquals(List.of("RTGS.groovy:120 · fund_transfer.RTGS.MakerViewDetail()", "Test case \"01 - Maker\" baris 33 (tab Script)"),
                FailureDiagnostics.sourceFrames(st, 4));
    }

    @Test
    void javaAndLibraryFramesAreDropped() {
        String st = "at com.example.Util.x(Util.java:5)\nat org.openqa.selenium.Foo.y(Foo.groovy:1)\n";
        assertEquals(List.of(), FailureDiagnostics.sourceFrames(st, 4));
    }
}
