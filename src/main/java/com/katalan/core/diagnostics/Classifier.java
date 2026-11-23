package com.katalan.core.diagnostics;

import com.katalan.core.diagnostics.FailureDiagnostics.ErrorInfo;
import com.katalan.core.diagnostics.FailureDiagnostics.Verdict;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.katalan.core.diagnostics.FailureDiagnostics.*;

/**
 * Rules of docs/FAILURE_DIAGNOSTICS.md. Every matching rule becomes a candidate; the one with
 * the highest confidence wins (ties: the lower order number) and the others are kept as evidence.
 * Machine load (75) ranks below what the page itself says (messages, failed requests) because those
 * are more specific, and above element-level guesses because load is what makes elements late.
 * Pure function of its inputs so it can be tested without a browser.
 */
final class Classifier {

    private static final Pattern SERVER_MSG = Pattern.compile(
            "(?i).*(timeout|time out|timed out|\\b5\\d\\d\\b|server|koneksi|connection|network|jaringan|gangguan|unavailable|try again later).*");

    private Classifier() {}

    private static final class Candidate {
        final Verdict verdict;
        final int order;
        Candidate(Verdict v, int order) { this.verdict = v; this.order = order; }
        int rank() {
            switch (verdict.confidence) {
                case "high": return 3;
                case "medium": return 2;
                default: return 1;
            }
        }
    }

    static Verdict classify(ErrorInfo err, Map<String, Object> browser, Map<String, Object> probe,
                            Map<String, Object> machine) {
        return classify(err, browser, probe, machine, false);
    }

    static Verdict classify(ErrorInfo err, Map<String, Object> browser, Map<String, Object> probe,
                            Map<String, Object> machine, boolean probeTimedOut) {
        List<Candidate> c = new ArrayList<>();
        Map<String, Object> page = probe == null ? null : asMap(probe.get("page"));
        Map<String, Object> net = probe == null ? null : asMap(probe.get("network"));
        List<Object> failed = net == null ? List.of() : asList(net.get("failed"));
        List<Object> slow = net == null ? List.of() : asList(net.get("slow"));
        List<Object> messages = probe == null ? List.of() : asList(probe.get("visibleMessages"));
        List<Object> dialogs = probe == null ? List.of() : asList(probe.get("openDialogs"));
        List<Object> candidates = probe == null ? List.of() : asList(probe.get("candidates"));
        List<Object> buttons = probe == null ? List.of() : asList(probe.get("visibleButtons"));
        Integer matches = probe == null ? null : toInt(probe.get("matches"));
        Integer visibleMatches = probe == null ? null : toInt(probe.get("visibleMatches"));
        Map<String, Object> first = probe == null ? null : asMap(probe.get("firstMatch"));
        Map<String, Object> obscured = probe == null ? null : asMap(probe.get("obscuredBy"));
        Integer cpu = toInt(machine.get("cpuPct"));
        Integer ram = toInt(machine.get("ramPct"));
        // When the engine knows when the last action before the failing step started (StepClock),
        // only requests started since then can be the cause; everything older is page background.
        List<Object> stepCalls = net == null || net.get("stepCalls") == null ? null : asList(net.get("stepCalls"));
        boolean stepKnown = stepCalls != null;
        List<Object> freshFailed = new ArrayList<>();
        for (Object o : failed) {
            Map<String, Object> r = asMap(o);
            if (r == null) continue;
            boolean noise;
            if (stepKnown) {
                noise = !Boolean.TRUE.equals(r.get("inStep"));
            } else {
                // Fallback without step timing: failed in an earlier failed TC, or again and again on this page
                Integer count = toInt(r.get("count"));
                noise = Boolean.TRUE.equals(r.get("seenBefore")) || (count != null && count >= 3);
            }
            if (!noise) freshFailed.add(o);
        }
        if (stepKnown) {
            List<Object> stepSlow = new ArrayList<>();
            for (Object o : slow) {
                Map<String, Object> r = asMap(o);
                if (r != null && Boolean.TRUE.equals(r.get("inStep"))) stepSlow.add(o);
            }
            slow = stepSlow;
        }
        boolean networkClean = freshFailed.isEmpty();
        List<Object> keys = probe == null ? List.of() : asList(probe.get("untranslatedKeys"));
        Integer keyCount = probe == null ? null : toInt(probe.get("untranslatedCount"));
        String target = targetText(err);

        // 1. browser crashed
        if (err.crashed || Boolean.FALSE.equals(browser.get("alive"))) {
            add(c, 10, CAT_RESOURCE, "BROWSER_CRASHED", "high",
                    "Browser tertutup/crash saat test berjalan — sering terjadi saat RAM/CPU mesin penuh.", err);
        }
        // page did not answer the read-only probe within the budget: frozen JS or a very busy browser
        if (probeTimedOut && !Boolean.FALSE.equals(browser.get("alive"))) {
            add(c, 20, CAT_RESOURCE, "PAGE_UNRESPONSIVE", "medium",
                    "Halaman tidak merespons saat dicek (JavaScript halaman macet atau browser sangat sibuk).", err);
        }
        // 2. machine overloaded
        boolean overloaded = (cpu != null && cpu >= CPU_OVERLOADED) || (ram != null && ram >= RAM_OVERLOADED);
        if (overloaded) {
            add(c, 75, CAT_RESOURCE, "MACHINE_OVERLOADED", "medium",
                    "Mesin sedang penuh saat test gagal (" + machineText(cpu, ram)
                            + ") — halaman/elemen kemungkinan telat karena beban.", err);
        }
        // 3. browser error page
        if (page != null && Boolean.TRUE.equals(page.get("errorPage"))) {
            add(c, 30, CAT_NETWORK, "NETWORK_ERROR_PAGE", "high",
                    "Halaman tidak bisa dibuka (halaman error browser) — koneksi ke aplikasi terputus.", err);
        }
        // 3b. page rendered without its texts: raw i18n keys on screen
        if (keyCount != null && keyCount >= 3) {
            String s = "Halaman tidak termuat sempurna \u2014 teks masih berupa kunci terjemahan ("
                    + join(keys, 3) + ")" + (target != null ? "; " + lowerFirst(target) + " belum muncul" : "") + ".";
            if (!freshFailed.isEmpty()) {
                s += " Kemungkinan API/server bermasalah: " + requestsText(freshFailed) + ".";
            } else {
                s += " Kemungkinan API/konfigurasi aplikasi gagal dimuat.";
            }
            add(c, 35, CAT_NETWORK, "PAGE_NOT_RENDERED", "high", s, err);
        }
        // 4. failed requests: evidence more than verdict - background endpoints often return 500 on dev envs
        if (!freshFailed.isEmpty() && stepKnown) {
            String s = "Setelah aksi terakhir (" + actionName(net) + "), request API gagal: " + requestsText(freshFailed)
                    + (target != null && err.notFound ? "; " + lowerFirst(target) + " tidak muncul" : "")
                    + ". Server/API bermasalah.";
            add(c, 40, CAT_NETWORK, "REQUEST_FAILED", "high", s, err);
        } else if (!freshFailed.isEmpty()) {
            String s = (target != null && err.notFound ? target + " tidak muncul; s" : "S")
                    + "ebelumnya " + freshFailed.size() + " request API gagal: " + requestsText(freshFailed)
                    + ". Kemungkinan server/API bermasalah.";
            add(c, 85, CAT_NETWORK, "REQUEST_FAILED", "medium", s, err);
        } else if (!failed.isEmpty()) {
            add(c, 85, CAT_NETWORK, "REQUEST_FAILED", "low",
                    failed.size() + " request API gagal (" + requestsText(failed) + "), tapi "
                            + (stepKnown ? "terjadi sebelum aksi terakhir" : "endpoint yang sama gagal berulang / juga di TC sebelumnya")
                            + " \u2014 kemungkinan error latar yang tidak terkait.", err);
        }
        // 5. slow requests / page still loading
        if (!slow.isEmpty()) {
            add(c, stepKnown ? 50 : 87, CAT_NETWORK, "REQUEST_SLOW", stepKnown ? "medium" : "low",
                    "Server lambat" + (stepKnown ? " setelah aksi terakhir" : "") + ": " + requestsText(slow) + ".", err);
        } else if (page != null && page.get("readyState") != null && !"complete".equals(page.get("readyState"))) {
            add(c, 50, CAT_NETWORK, "REQUEST_SLOW", "medium",
                    "Halaman belum selesai dimuat (readyState=" + page.get("readyState") + ").", err);
        }
        // 6/7. messages on screen
        String serverMsg = null, otherMsg = null;
        for (Object o : messages) {
            Map<String, Object> m = asMap(o);
            String t = m == null ? null : (String) m.get("text");
            if (t == null) continue;
            if (SERVER_MSG.matcher(t).matches()) { if (serverMsg == null) serverMsg = t; }
            else if (otherMsg == null) otherMsg = t;
        }
        if (serverMsg != null) {
            add(c, 60, CAT_NETWORK, "PAGE_SHOWS_SERVER_ERROR", "medium",
                    "Aplikasi menampilkan pesan gangguan: \"" + shorten(serverMsg, 160) + "\".", err);
        }
        if (otherMsg != null) {
            add(c, 70, CAT_TEST_DATA, "PAGE_SHOWS_ERROR", "medium",
                    "Aplikasi menampilkan pesan: \"" + shorten(otherMsg, 160) + "\" — kemungkinan test data tidak sesuai.", err);
        }

        boolean absentNow = matches == null || matches == 0;
        if (err.notFound && absentNow) {
            if (err.isData()) {
                // 8. data value not found
                String what = dataNoun(err.literalKind) + " \"" + err.literal + "\"";
                if (!candidates.isEmpty()) {
                    add(c, 80, CAT_TEST_DATA, "DATA_VALUE_NOT_FOUND", "high",
                            what + " tidak ada di halaman. Yang tersedia: " + join(candidates, 3)
                                    + ". Cek test data.", err);
                } else {
                    add(c, 80, CAT_TEST_DATA, "DATA_VALUE_NOT_FOUND", "medium",
                            what + " tidak ditemukan di halaman. Mungkin test data tidak tersedia di environment ini, "
                                    + "atau daftar pilihan belum terbuka.", err);
                }
            } else if (err.isMessage()) {
                // 9b. a confirmation/notification the script waits for after an action never showed up
                String s = "Pesan \"" + err.literal + "\" tidak muncul \u2014 aksi sebelumnya kemungkinan tidak diproses aplikasi"
                        + " (klik terjadi saat form belum siap, validasi, atau request ke server belum selesai/gagal).";
                if (!dialogs.isEmpty()) {
                    Map<String, Object> d0 = asMap(dialogs.get(0));
                    Object title = d0 == null ? null : d0.get("title");
                    if (title != null && !title.toString().isEmpty()) s += " Dialog \"" + title + "\" masih terbuka.";
                }
                add(c, 95, CAT_RESOURCE, "EXPECTED_MESSAGE_MISSING", "medium", s, err);
            } else if (err.literal != null && !candidates.isEmpty()) {
                // 9. label changed
                add(c, 90, CAT_LOCATOR, "LOCATOR_TEXT_CHANGED", "high",
                        "Elemen \"" + err.literal + "\" tidak ditemukan, tapi ada \"" + candidates.get(0)
                                + "\" — kemungkinan teks/locator berubah.", err);
            } else if (probe != null && networkClean) {
                // 10. not found on a normally loaded page
                String s = err.literal != null
                        ? "Elemen \"" + err.literal + "\" tidak ditemukan di halaman"
                        : "Elemen tidak ditemukan di halaman";
                s += pageText(page) + ". Kemungkinan locator berubah atau alur berpindah ke halaman lain.";
                if (!buttons.isEmpty()) s += " Tombol yang terlihat: " + join(buttons, 4) + ".";
                add(c, 100, CAT_LOCATOR, "ELEMENT_NOT_FOUND", "medium", s, err);
            }
        }
        if (err.notFound && matches != null && matches > 0 && visibleMatches != null && visibleMatches > 0 && obscured == null) {
            add(c, 110, CAT_RESOURCE, "ELEMENT_APPEARED_LATE", "low",
                    "Elemen sekarang sudah ada dan terlihat — kemungkinan muncul terlambat (aplikasi lambat atau mesin sibuk).", err);
        }
        if (obscured != null) {
            add(c, 110, CAT_RESOURCE, "ELEMENT_OBSCURED", "low",
                    "Elemen tertutup oleh " + elementText(obscured) + " — biasanya loading/popup belum hilang.", err);
        }
        if (matches != null && matches > 0 && visibleMatches != null && visibleMatches == 0) {
            add(c, 110, CAT_LOCATOR, "ELEMENT_HIDDEN", "low", "Elemen ada di halaman tapi tersembunyi.", err);
        }
        if (first != null && Boolean.TRUE.equals(first.get("disabled"))) {
            add(c, 110, CAT_TEST_DATA, "ELEMENT_DISABLED", "low",
                    "Elemen ada tapi nonaktif — biasanya ada isian yang belum valid.", err);
        }
        if (err.assertion) {
            add(c, 120, CAT_OTHER, "ASSERTION", "medium",
                    "Hasil tidak sesuai harapan: " + shorten(err.message, 200), err);
        }
        // Element-not-found with nothing else to go on (e.g. probe timed out) still points at locator/data
        if (c.isEmpty() && err.notFound) {
            if (err.isData()) {
                add(c, 80, CAT_TEST_DATA, "DATA_VALUE_NOT_FOUND", "low",
                        dataNoun(err.literalKind) + " \"" + err.literal + "\" tidak ditemukan di halaman. Cek test data.", err);
            } else {
                add(c, 100, CAT_LOCATOR, "ELEMENT_NOT_FOUND", "low",
                        "Elemen tidak ditemukan di halaman. Kemungkinan locator berubah.", err);
            }
        }

        Candidate best = null;
        for (Candidate x : c) {
            if (best == null || x.rank() > best.rank() || (x.rank() == best.rank() && x.order < best.order)) best = x;
        }
        Verdict v = best == null ? Verdict.unknown(err) : best.verdict;
        for (Candidate x : c) {
            if (x == best) continue;
            // The page-not-rendered sentence already lists the failed requests
            if ("PAGE_NOT_RENDERED".equals(v.code) && "REQUEST_FAILED".equals(x.verdict.code)) continue;
            v.evidence.add(evidence(x.verdict.code, stripSource(x.verdict.summary, err)));
        }
        if (stepKnown && err.notFound && freshFailed.isEmpty()) {
            if (stepCalls.isEmpty()) {
                v.evidence.add(evidence("NO_REQUEST_IN_STEP", "Tidak ada request API sejak aksi terakhir (" + actionName(net)
                        + ") \u2014 aksi kemungkinan tidak sampai ke server (klik/isian tidak diproses aplikasi)."));
            } else {
                boolean allOk = true;
                for (Object o : stepCalls) {
                    Map<String, Object> r = asMap(o);
                    Integer st = r == null ? null : toInt(r.get("status"));
                    if (st == null || st < 200 || st >= 300) { allOk = false; break; }
                }
                if (allOk) {
                    v.evidence.add(evidence("SERVER_OK_IN_STEP", "Server menjawab OK setelah aksi terakhir (" + requestsText(stepCalls)
                            + "), tapi elemen tidak muncul \u2014 cek isi respons/data atau tampilan aplikasi."));
                }
            }
        }
        for (Object o : dialogs) {
            Map<String, Object> d = asMap(o);
            if (d == null) continue;
            String title = (String) d.get("title");
            v.evidence.add(evidence("DIALOG_OPEN", "Dialog terbuka" + (title == null || title.isEmpty() ? "" : ": \"" + title + "\"")));
        }
        if (!overloaded && (cpu != null || ram != null) && !"MACHINE_OVERLOADED".equals(v.code)) {
            v.evidence.add(evidence("MACHINE", "Kondisi mesin: " + machineText(cpu, ram)));
        }
        return v;
    }

    // ------------------------------------------------------------------ text helpers

    private static void add(List<Candidate> c, int order, String cat, String code, String conf, String text, ErrorInfo err) {
        c.add(new Candidate(Verdict.of(cat, code, conf, Verdict.withSource(text, err)), order));
    }

    private static Map<String, Object> evidence(String code, String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("detail", detail);
        return m;
    }

    private static String stripSource(String s, ErrorInfo err) {
        if (err.source != null && s.endsWith(" (" + err.source + ")")) {
            return s.substring(0, s.length() - err.source.length() - 3);
        }
        return s;
    }

    private static String dataNoun(String kind) {
        switch (kind == null ? "" : kind) {
            case "digits": return "Nomor";
            case "amount": return "Nominal";
            case "date": return "Tanggal";
            case "email": return "Email";
            default: return "Data";
        }
    }

    private static String machineText(Integer cpu, Integer ram) {
        List<String> p = new ArrayList<>();
        if (cpu != null) p.add("CPU " + cpu + "%");
        if (ram != null) p.add("RAM " + ram + "%");
        return String.join(", ", p);
    }

    private static String requestsText(List<Object> reqs) {
        List<String> p = new ArrayList<>();
        for (int i = 0; i < Math.min(2, reqs.size()); i++) {
            Map<String, Object> r = asMap(reqs.get(reqs.size() - 1 - i));
            if (r == null) continue;
            Integer st = toInt(r.get("status"));
            Integer ms = toInt(r.get("ms"));
            String status = st == null ? "" : (st == 0 ? " → gagal/terputus" : " → " + st);
            String dur = ms != null && ms >= 1000 ? " (" + Math.round(ms / 1000.0) + " dtk)" : "";
            Integer n = toInt(r.get("count"));
            p.add(r.get("path") + status + dur + (n != null && n > 1 ? " \u00d7" + n : ""));
        }
        String joined = String.join(", ", p);
        return reqs.size() > 2 ? joined + " (dan " + (reqs.size() - 2) + " lainnya)" : joined;
    }

    /** "Elemen //*[@id='submit']" / "Elemen \"Retry\"" for summaries, or null without a locator. */
    private static String targetText(ErrorInfo err) {
        if (err.literal != null) return "Elemen \"" + err.literal + "\"";
        if (err.locatorValue != null) return "Elemen " + shorten(err.locatorValue, 80);
        return null;
    }

    /** Name of the last action before the failing step, set by FailureDiagnostics from StepClock. */
    private static String actionName(Map<String, Object> net) {
        Object a = net == null ? null : net.get("stepAction");
        return a == null ? "aksi" : a.toString();
    }

    private static String lowerFirst(String s) {
        return s == null || s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static String pageText(Map<String, Object> page) {
        if (page == null) return "";
        Object title = page.get("title");
        Object url = page.get("url");
        String path = url == null ? null : url.toString().replaceFirst("^https?://[^/]+", "");
        if (title != null && !title.toString().isEmpty()) {
            return " (halaman: " + title + (path != null && !path.isEmpty() && !"/".equals(path) ? " · " + path : "") + ")";
        }
        return path == null || path.isEmpty() ? "" : " (halaman: " + path + ")";
    }

    private static String elementText(Map<String, Object> el) {
        StringBuilder sb = new StringBuilder(String.valueOf(el.get("tag")));
        Object id = el.get("id");
        if (id != null && !id.toString().isEmpty()) sb.append('#').append(id);
        Object cls = el.get("classes");
        if (cls != null && !cls.toString().isEmpty()) {
            String[] parts = cls.toString().trim().split("\\s+");
            for (int i = 0; i < Math.min(2, parts.length); i++) sb.append('.').append(parts[i]);
        }
        return sb.toString();
    }

    private static String join(List<Object> items, int max) {
        List<String> p = new ArrayList<>();
        for (int i = 0; i < Math.min(max, items.size()); i++) p.add(String.valueOf(items.get(i)));
        if (items.size() > max) p.add("…");
        return String.join(", ", p);
    }

    private static Integer toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String) {
            try { return Integer.parseInt(((String) o).trim()); } catch (NumberFormatException ignored) { }
        }
        return null;
    }
}
