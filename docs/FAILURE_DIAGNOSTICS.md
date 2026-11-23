# Failure diagnostics

When a test case fails for good, katalan inspects the still-open browser and tells the tester *why* it
most likely failed, in one human-readable sentence plus the evidence behind it:

| Category | Typical cause |
|---|---|
| `TEST_DATA` | The value the script looks for is not on the page, or the app shows a validation/business message |
| `LOCATOR` | The element/label the script looks for is gone or its text changed |
| `NETWORK` | An API call failed or was slow, the browser shows an error page, or the page rendered without its texts |
| `RESOURCE` (shown as "Timing / machine load") | Element appeared too late, was covered by an overlay, the action was not processed, the page froze, the browser crashed or the machine was overloaded |
| `OTHER` | Assertion mismatch, or nothing conclusive |

The diagnosis is **a guess with evidence, not a verdict**. It never changes the test result.

## When it runs

- Only for the final error that fails a test case, on the last retry attempt.
- Not for steps run with `FailureHandling.OPTIONAL` (they are allowed to miss).
- Not for passing test cases.

## Guarantees

- **No cost for passing test cases.** The only always-on work is recording each keyword's start time
  (two clock reads per keyword) and sampling system CPU every 5 s on a background thread.
- **Bounded cost for failing test cases**: one `executeScript` round trip, capped by
  `katalan.failureDiagnostics.timeoutMs` (default 1500 ms). Typical: 40–250 ms.
- **Read-only**: never clicks, scrolls, types or changes the DOM; no extra screenshot.
- Never throws, never changes the result.

## Configuration (`katalan.properties`, project or next to the jar; `-D` overrides)

```properties
katalan.failureDiagnostics.enabled=true      # default true
katalan.failureDiagnostics.timeoutMs=1500    # hard cap for the browser probe
katalan.failureDiagnostics.maxTextChars=300  # max length of any text taken from the page
```

## Output

1. **Report PDF** — the "Failure Summary" shows the category, the sentence and the evidence before the
   technical details. Console lines of steps run with `FailureHandling.OPTIONAL` are hidden from the
   snippet and replaced by a count.
2. **One stdout line** that a launching process can consume:

```
KATALAN_FAILURE {"v":1, ...}
```

Single line, at most 8 KB (least useful fields are dropped first). Schema v1:

```jsonc
{
  "v": 1,
  "ts": "2026-01-01T10:00:00.000+00:00",
  "testCaseId": "Test Cases/Checkout/TC01 - Pay with card",   // same text as the progress line
  "attempt": 1, "maxAttempts": 1,
  "kind": "FAILED",                         // FAILED | ERROR
  "category": "NETWORK",                    // TEST_DATA | LOCATOR | NETWORK | RESOURCE | OTHER
  "code": "REQUEST_FAILED",                 // rule that won, see below
  "confidence": "high",                     // high | medium | low
  "summary": "After the last action (click), an API call failed: /api/payments → 500 (12 s) ...",
  "evidence": [ { "code": "DIALOG_OPEN", "detail": "Dialog open: \"Payment\"" } ],
  "stepWindow": { "keyword": "verifyElementPresent", "action": "click", "windowSec": 31 },
  "error": {
    "type": "WebElementNotFoundException",
    "message": "Unable to verify object '//*[text()='Payment received']' is present ...",  // ≤ 500 chars
    "keyword": "verify",
    "locator": { "by": "xpath", "value": "//*[text()='Payment received']" },
    "source": "PaymentKeywords.groovy:120 · pay"   // first custom keyword frame, if any
  },
  "browser": { "alive": true, "driverKind": "chrome", "headless": true,
               "url": "https://app.example/checkout",   // no query string
               "title": "...", "readyState": "complete", "errorPage": false,
               "viewport": { "w": 1920, "h": 1080 }, "userAgent": "..." },
  "network": {
    "failed": [ { "path": "/api/payments", "type": "fetch", "status": 500, "ms": 12000, "count": 1, "inStep": true } ],
    "slow":   [],
    "stepCalls": [ { "path": "/api/payments", "status": 500, "ms": 12000, "agoSec": 30, "calls": 1, "inStep": true } ],
    "recent":    [ /* last API calls, one row per endpoint */ ],
    "statusKnown": true, "bufferFull": false
  },
  "locatorCheck": { "literal": "Payment received", "literalKind": "message",  // digits|amount|date|email|label|message
                    "matches": 0, "visibleMatches": 0, "firstMatch": null, "obscuredBy": null,
                    "candidates": [], "visibleButtons": null },
  "pageRender": { "untranslatedKeys": [], "untranslatedCount": 0 },
  "visibleMessages": [ { "source": "role=alert", "text": "..." } ],
  "openDialogs": [ { "tag": "div", "classes": "modal show", "title": "Payment" } ],
  "machine": { "cpuPct": 41, "ramPct": 63 },
  "screenshotPath": "/abs/path/to/failure.png",
  "diagnosticsMs": 120,
  "diagnosticsErrors": []
}
```

Page texts are truncated but **not masked** by katalan; a process that forwards this line elsewhere is
responsible for masking credentials.

## Rules

Every matching rule becomes a candidate; the highest confidence wins, ties go to the lower order number.
The other candidates are kept as `evidence`.

| Order | Code | Category | Confidence | Rule |
|---|---|---|---|---|
| 10 | `BROWSER_CRASHED` | RESOURCE | high | session gone / tab crashed / chrome not reachable / OOM |
| 20 | `PAGE_UNRESPONSIVE` | RESOURCE | medium | the read-only probe did not answer within the time cap |
| 30 | `NETWORK_ERROR_PAGE` | NETWORK | high | browser error page (`chrome-error://`) |
| 35 | `PAGE_NOT_RENDERED` | NETWORK | high | ≥ 3 untranslated i18n keys visible (`form.email.label`) |
| 40 | `REQUEST_FAILED` | NETWORK | high | an API call **started after the last action** failed (5xx / 0) |
| 50 | `REQUEST_SLOW` | NETWORK | medium | an API call after the last action took ≥ 10 s, or the page is still loading |
| 60 | `PAGE_SHOWS_SERVER_ERROR` | NETWORK | medium | visible message mentions timeout / 5xx / server / connection |
| 70 | `PAGE_SHOWS_ERROR` | TEST_DATA | medium | other visible error/alert message |
| 75 | `MACHINE_OVERLOADED` | RESOURCE | medium | CPU ≥ 90 % or RAM ≥ 95 % |
| 80 | `DATA_VALUE_NOT_FOUND` | TEST_DATA | high/medium | the locator contains a value (digits, amount, date, email) that is not on the page; same-shaped candidates listed |
| 85 | `REQUEST_FAILED` | NETWORK | medium/low | failed calls when the step time is unknown (fallback) |
| 90 | `LOCATOR_TEXT_CHANGED` | LOCATOR | high | label not found but a near-identical text is (case, spacing, partial) |
| 95 | `EXPECTED_MESSAGE_MISSING` | RESOURCE | medium | a confirmation sentence the script waits for never showed up |
| 100 | `ELEMENT_NOT_FOUND` | LOCATOR | medium | element absent on a normally loaded page, no failed call |
| 110 | `ELEMENT_HIDDEN` / `ELEMENT_OBSCURED` / `ELEMENT_DISABLED` / `ELEMENT_APPEARED_LATE` | LOCATOR / RESOURCE / TEST_DATA / RESOURCE | low | element present but hidden / covered / disabled / visible only now |
| 120 | `ASSERTION` | OTHER | medium | verify/assert mismatch |
| — | `UNKNOWN` | OTHER | low | nothing matched |

### Step timing

Keyword calls go through one dispatcher, where katalan records when each keyword starts. The window
that matters starts at the **last action** (click, setText, select, navigate…) before the failing keyword
— a wait fails because of what the preceding action triggered. API calls started inside that window are
`inStep` and can be the cause; older ones are page background and only reported as evidence.
Extra evidence: `NO_REQUEST_IN_STEP` (the action never reached the server) and `SERVER_OK_IN_STEP`
(the server answered 2xx but the element never appeared).

### Limits

- Network data comes from the browser's Resource Timing API: only finished requests, status codes from
  Chrome 109+ and same-origin (or CORS) responses, buffer of 250 entries. Requests still pending at the
  timeout are not visible.
- iframes: the probe runs in the frame the driver is currently switched to.
- Mobile (Appium) drivers: DOM checks are skipped.

## Related message changes

Objects built from a locator in the script have no object id. Katalon prints `id: 'null'` / `object 'null'`
for them, which reads as if the script were broken; katalan prints the locator instead:

```
Unable to click on object '//button[text()='Save']' (Root cause: ... Web element located by '//button[text()='Save']' not found
```
