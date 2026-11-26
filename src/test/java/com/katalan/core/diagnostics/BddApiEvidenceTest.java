package com.katalan.core.diagnostics;

import com.katalan.core.model.ApiCallRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BddApiEvidenceTest {

    /** Order #3197: the failure line said only "Cucumber execution finished with 1 failed scenario(s)". */
    @Test
    void bddAssertOnApiResponseShowsStepAssertAndResponse() {
        AssertionError stepError = new AssertionError("assert response.response.responseCode == '00'\n"
                + "|        |        |            |\n"
                + "|        |        '68'         false");
        String stepStack = "org.codehaus.groovy.runtime.powerassert.PowerAssertionError: assert ...\n"
                + "\tat org.codehaus.groovy.runtime.InvokerHelper.createAssertError(InvokerHelper.java:414)\n"
                + "\tat brimsInquiryAccount.InquiryAccountAdditional.verifyResponseInquiryAccountAdditional(InquiryAccountAdditional.groovy:205)\n"
                + "java.lang.RuntimeException: Cucumber execution finished with 1 failed scenario(s)\n"
                + "\tat com.katalan.keywords.CucumberKW.runFeatureFileWithTags(CucumberKW.java:179)\n"
                + "\tat SQAESB-3644_-_Inquiry_Cashcard_PLN.run(SQAESB-3644 - Inquiry Cashcard PLN:20)\n";
        Map<String, Object> d = FailureDiagnostics.diagnose(null, false, "Test Cases/X/SQAESB-3644 - Inquiry Cashcard PLN",
                1, 1, "ERROR", stepError, stepError.getMessage(), stepStack, null);

        ApiCallRecord call = new ApiCallRecord();
        call.setTimestamp(Instant.now());
        call.setMethod("POST");
        call.setUrl("https://apiesbqa.dev.bri.co.id:5556/gateway/inquiryaccount/1.0/inquiryaccountadditional?x=1");
        call.addRequestHeader("Content-Type", "application/json");
        call.setRequestBody("{\n  \"request\": {\"accountNo\": \"444445616161300\"}\n}");
        call.setStatusCode(200);
        call.setElapsedMs(5483);
        call.setResponseBody("{\"response\":{\"responseCode\":\"68\",\"errorCode\":\"VA-68\",\"responseMessage\":\"Timeout\"}}");

        FailureDiagnostics.addBddAndApiEvidence(d,
                "Then User verify response ESB MSR BRIMSInquiryAccount Inquiry Account Additional Success", List.of(call));

        assertEquals("Then User verify response ESB MSR BRIMSInquiryAccount Inquiry Account Additional Success"
                + " — nilai tidak sesuai harapan: assert response.response.responseCode == '00'", d.get("failedAt"));
        assertEquals(List.of("InquiryAccountAdditional.groovy:205 · brimsInquiryAccount.InquiryAccountAdditional.verifyResponseInquiryAccountAdditional()",
                "Test case \"SQAESB-3644 - Inquiry Cashcard PLN\" baris 20 (tab Script)"), d.get("sourceFrames"));
        @SuppressWarnings("unchecked")
        List<String> facts = (List<String>) d.get("facts");
        assertEquals("API POST /gateway/inquiryaccount/1.0/inquiryaccountadditional → HTTP 200 (5.5 dtk)", facts.get(0));
        assertEquals("Response: {\"response\":{\"responseCode\":\"68\",\"errorCode\":\"VA-68\",\"responseMessage\":\"Timeout\"}}", facts.get(1));
        List<?> net = (List<?>) d.get(FailureDiagnostics.NETWORK_CALLS);
        assertEquals(1, net.size());
        assertTrue(((Map<?, ?>) net.get(0)).get("res").toString().contains("VA-68"));
    }

    @Test
    void noStepNoCallsLeavesDiagnosisAlone() {
        Map<String, Object> d = FailureDiagnostics.diagnose(null, false, "Test Cases/Y", 1, 1, "FAILED",
                new RuntimeException("boom"), "boom", "", null);
        Object before = d.get("failedAt");
        FailureDiagnostics.addBddAndApiEvidence(d, null, List.of());
        assertEquals(before, d.get("failedAt"));
        assertFalse(d.containsKey("wsCall"));
    }
}
