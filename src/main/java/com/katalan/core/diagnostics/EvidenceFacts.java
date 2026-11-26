package com.katalan.core.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.katalan.core.diagnostics.FailureDiagnostics.asList;
import static com.katalan.core.diagnostics.FailureDiagnostics.asMap;
import static com.katalan.core.diagnostics.FailureDiagnostics.shorten;

/**
 * Turns a diagnosis into plain facts observed when the test failed ("API x → 500", "banner shown",
 * "button disabled"). v1 shows only these facts - no category, no confidence, no "probably": a confident
 * wrong verdict misleads more than no verdict. The rule-based guess stays in the JSON (category/code)
 * so it can be measured later. See docs/FAILURE_DIAGNOSTICS.md.
 */
public final class EvidenceFacts {

    private EvidenceFacts() {}

    /**
     * One line saying where it failed ("click //button[...] — elemen tidak ditemukan"), shown as the card
     * headline / next to the Error block. Null when unknown.
     */
    public static String failedAt(Map<String, Object> d) {
        try {
            Map<String, Object> err = asMap(d.get("error"));
            if (err == null) return null;
            String kw = err.get("keyword") != null ? String.valueOf(err.get("keyword")) : null;
            Map<String, Object> loc = asMap(err.get("locator"));
            String what = loc != null ? String.valueOf(loc.get("value")) : null;
            String type = String.valueOf(err.get("type"));
            String result;
            if (type.contains("NotFound") || type.contains("NoSuchElement")) result = "elemen tidak ditemukan";
            else if (type.contains("Assertion")) {
                // "assert response.responseCode == '00'" says which value; the power-assert diagram stays in the message
                String first = firstLine(err.get("message"));
                result = "nilai tidak sesuai harapan" + (first.isEmpty() ? "" : ": " + shorten(first, 200));
            }
            else if (type.contains("NotInteractable")) result = "elemen ada tapi tidak bisa diinteraksi";
            else if (type.contains("ClickIntercepted")) result = "klik tertutup elemen lain";
            else if (type.contains("StaleElement")) result = "elemen berubah saat sedang diproses";
            else if (type.contains("Timeout")) result = "timeout";
            else result = shorten(String.valueOf(err.get("message")), 160);
            String at = ((kw != null ? kw + " " : "") + (what != null ? what : "")).trim();
            // BDD: the step people wrote in the feature file says more than the generic keyword
            if (at.isEmpty() && d.get("bddStep") != null) at = String.valueOf(d.get("bddStep"));
            return at.isEmpty() ? result : at + " \u2014 " + result;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String firstLine(Object message) {
        if (message == null) return "";
        String m = String.valueOf(message).trim();
        int nl = m.indexOf('\n');
        if (nl >= 0) m = m.substring(0, nl);
        // Groovy power-assert diagram ("| | '68' false") once the message was flattened to one line
        int diagram = m.indexOf(" | ");
        if (diagram < 0 && m.endsWith(" |")) diagram = m.length() - 2;
        return (diagram > 0 && m.startsWith("assert ") ? m.substring(0, diagram) : m).trim();
    }

    /** Facts observed in the browser, most useful first (without the failedAt line). Never throws. */
    public static List<String> build(Map<String, Object> d) {
        List<String> f = new ArrayList<>();
        try {
            Map<String, Object> browser = asMap(d.get("browser"));
            Map<String, Object> net = asMap(d.get("network"));
            Map<String, Object> lc = asMap(d.get("locatorCheck"));
            Map<String, Object> step = asMap(d.get("stepWindow"));
            Map<String, Object> last = asMap(d.get("lastAction"));
            Map<String, Object> render = asMap(d.get("pageRender"));
            Map<String, Object> machine = asMap(d.get("machine"));
            boolean stepKnown = step != null;

            // 1. API test (WS.sendRequest): the last call and what it answered - usually why an assert failed
            Map<String, Object> ws = asMap(d.get("wsCall"));
            if (ws != null) {
                String path = String.valueOf(ws.get("url")).replaceFirst("^https?://[^/]+", "");
                if (ws.get("err") != null) f.add("API " + ws.get("method") + " " + path + " \u2192 gagal: " + ws.get("err"));
                else f.add("API " + ws.get("method") + " " + path + " \u2192 HTTP " + ws.get("status") + durationText(ws.get("ms"), 0));
                if (ws.get("res") != null && !String.valueOf(ws.get("res")).isEmpty()) f.add("Response: " + ws.get("res"));
            }
            List<String> pageLoadApis = new ArrayList<>();

            // 2. what the page said
            for (Object o : asList(d.get("visibleMessages"))) {
                Map<String, Object> m = asMap(o);
                if (m != null && m.get("text") != null) f.add("Pesan di layar: \"" + shorten(String.valueOf(m.get("text")), 200) + "\"");
            }

            // 3. what the server did. Requests after the last action come first: they show whether the action
            // reached the server and what it answered. Failures on page load are often the same noisy endpoint
            // on every page, so they go last (section 8).
            List<Object> stepCalls = net == null || net.get("stepCalls") == null ? null : asList(net.get("stepCalls"));
            java.util.Set<String> stepPaths = new java.util.HashSet<>();
            if (stepCalls != null && !stepCalls.isEmpty()) {
                List<String> calls = new ArrayList<>();
                for (Object o : stepCalls) {
                    Map<String, Object> r = asMap(o);
                    if (r == null) continue;
                    stepPaths.add(String.valueOf(r.get("path")));
                    calls.add(r.get("path") + " → " + statusText(r.get("status")) + durationText(r.get("ms"), 0));
                }
                if (!calls.isEmpty()) f.add("API setelah aksi terakhir (" + actionName(step, net) + "): " + String.join("; ", calls));
            }
            List<String> apis = new ArrayList<>();
            for (Object o : net == null ? List.of() : asList(net.get("failed"))) {
                Map<String, Object> r = asMap(o);
                if (r == null) continue;
                boolean inStep = Boolean.TRUE.equals(r.get("inStep"));
                String path = String.valueOf(r.get("path"));
                if (inStep && stepPaths.contains(path)) continue; // already in the line above
                StringBuilder s = new StringBuilder(path).append(" → ").append(statusText(r.get("status")));
                Object n = r.get("count");
                if (n instanceof Number && ((Number) n).intValue() > 1) s.append(" ×").append(n);
                s.append(durationText(r.get("ms"), 1000));
                List<String> notes = new ArrayList<>();
                if (Boolean.TRUE.equals(r.get("background"))) notes.add("juga gagal di banyak halaman lain");
                else if (stepKnown && inStep) notes.add("setelah aksi terakhir");
                if (Boolean.TRUE.equals(r.get("seenBefore"))) notes.add("juga gagal di TC sebelumnya");
                if (!notes.isEmpty()) s.append(" (").append(String.join(", ", notes)).append(")");
                if (stepKnown && !inStep) pageLoadApis.add(s.toString());
                else apis.add(s.toString());
            }
            if (!apis.isEmpty()) f.add("API gagal: " + String.join("; ", apis));
            for (Object o : net == null ? List.of() : asList(net.get("slow"))) {
                Map<String, Object> r = asMap(o);
                if (r == null) continue;
                Object ms = r.get("ms");
                f.add("API lambat: " + r.get("path") + (ms instanceof Number ? " (" + Math.round(((Number) ms).longValue() / 1000.0) + " dtk)" : ""));
            }
            if (stepKnown && stepCalls != null && stepCalls.isEmpty()) {
                f.add("Tidak ada request API sejak aksi terakhir (" + actionName(step, net) + ")");
            }

            // 4. the action before the failure and the state around it
            List<String> around = new ArrayList<>();
            if (last != null && last.get("text") != null) {
                String a = (step != null && step.get("action") != null ? step.get("action") : "aksi") + " \"" + last.get("text") + "\"";
                if (Boolean.TRUE.equals(last.get("disabled"))) a += " — tombol disabled saat diklik";
                around.add("Aksi sebelumnya: " + a);
            }
            List<String> dialogs = new ArrayList<>();
            for (Object o : asList(d.get("openDialogs"))) {
                Map<String, Object> m = asMap(o);
                if (m != null && m.get("title") != null && !String.valueOf(m.get("title")).isEmpty()) dialogs.add("\"" + m.get("title") + "\"");
            }
            if (!dialogs.isEmpty()) around.add("Dialog terbuka: " + String.join(", ", dialogs));
            if (!around.isEmpty()) f.add(String.join(" · ", around));

            // 5. the element itself
            if (lc != null) {
                Integer matches = toInt(lc.get("matches"));
                Integer visible = toInt(lc.get("visibleMatches"));
                if (matches != null && matches > 0 && visible != null && visible == 0) f.add("Elemen ada di halaman tapi tersembunyi");
                Map<String, Object> ob = asMap(lc.get("obscuredBy"));
                if (ob != null) f.add("Elemen tertutup oleh " + ob.get("tag") + (ob.get("classes") != null ? "." + String.valueOf(ob.get("classes")).trim().split("\\s+")[0] : ""));
                Map<String, Object> first = asMap(lc.get("firstMatch"));
                if (first != null && Boolean.TRUE.equals(first.get("disabled"))) f.add("Elemen ada tapi disabled");
                List<Object> cands = asList(lc.get("candidates"));
                if (!cands.isEmpty()) f.add("Teks serupa di halaman: " + join(cands, 4));
                List<Object> buttons = asList(lc.get("visibleButtons"));
                if (cands.isEmpty() && !buttons.isEmpty()) f.add("Tombol yang terlihat: " + join(buttons, 5));
            }
            Integer keys = render == null ? null : toInt(render.get("untranslatedCount"));
            if (keys != null && keys >= 3) f.add("Teks halaman masih berupa kunci terjemahan: " + join(asList(render.get("untranslatedKeys")), 3));

            // 6. browser / machine
            if (browser != null && Boolean.FALSE.equals(browser.get("alive"))) f.add("Browser sudah tertutup/crash saat diperiksa");
            if (browser != null && Boolean.TRUE.equals(browser.get("errorPage"))) f.add("Browser menampilkan halaman error (koneksi gagal)");
            if (browser != null && browser.get("readyState") != null && !"complete".equals(browser.get("readyState"))) {
                f.add("Halaman belum selesai dimuat (" + browser.get("readyState") + ")");
            }
            if (String.valueOf(d.get("diagnosticsErrors")).contains("browser: timeout")) f.add("Halaman tidak merespons saat diperiksa");
            if (machine != null) {
                Integer cpu = toInt(machine.get("cpuPct"));
                Integer ram = toInt(machine.get("ramPct"));
                if ((cpu != null && cpu >= FailureDiagnostics.CPU_OVERLOADED) || (ram != null && ram >= FailureDiagnostics.RAM_OVERLOADED)) {
                    f.add("Mesin sedang penuh: CPU " + cpu + "%, RAM " + ram + "%");
                }
            }

            // 7. where
            if (browser != null && browser.get("url") != null) {
                String path = String.valueOf(browser.get("url")).replaceFirst("^https?://[^/]+", "");
                f.add("Halaman: " + (path.isEmpty() ? "/" : path) + (browser.get("title") != null ? " · " + browser.get("title") : ""));
            }

            // 8. failures from before the last action (page load): rarely the cause, kept for completeness
            if (!pageLoadApis.isEmpty()) f.add("API gagal saat halaman dimuat (sebelum aksi terakhir): " + String.join("; ", pageLoadApis));
        } catch (Throwable ignored) {
            // facts are best effort
        }
        return f;
    }

    private static String statusText(Object st) {
        if (st == null) return "?";
        return "0".equals(String.valueOf(st)) ? "gagal/terputus" : String.valueOf(st);
    }

    /** " (1.2 dtk)" when the duration is known and at least minMs, else "". */
    private static String durationText(Object ms, long minMs) {
        if (!(ms instanceof Number)) return "";
        long v = ((Number) ms).longValue();
        if (v < minMs) return "";
        return v < 1000 ? " (" + v + " ms)" : String.format(Locale.ROOT, " (%.1f dtk)", v / 1000.0);
    }

    private static String actionName(Map<String, Object> step, Map<String, Object> net) {
        Object a = step != null && step.get("action") != null ? step.get("action") : net != null ? net.get("stepAction") : null;
        return a != null ? String.valueOf(a) : "aksi";
    }

    private static String join(List<Object> items, int max) {
        List<String> p = new ArrayList<>();
        for (int i = 0; i < Math.min(max, items.size()); i++) p.add(String.valueOf(items.get(i)));
        if (items.size() > max) p.add("…");
        return String.join(", ", p);
    }

    private static Integer toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        try { return o == null ? null : Integer.parseInt(String.valueOf(o).trim().toLowerCase(Locale.ROOT)); } catch (NumberFormatException e) { return null; }
    }
}
