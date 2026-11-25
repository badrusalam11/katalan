// Failure diagnostics - network part (docs/FAILURE_DIAGNOSTICS.md). READ-ONLY, runs once when a test fails.
// Kept apart from failure-diagnostics.js and run FIRST: it only reads performance entries and the recorder
// buffer (no DOM scan), so it still finishes on a heavy/busy page where the DOM probe times out.
// arguments: [windowMs, stepFromEpochMs]
var winMs = arguments[0] || 90000;
var stepFrom = arguments[1] || 0;
var origin0 = performance.timeOrigin || (Date.now() - performance.now());
function inStep(e) { return stepFrom > 0 && origin0 + e.startTime >= stepFrom; }
var out = { errors: [] };

// 4b. network: entries that finished within the last winMs (90 s) (no listener needed, nothing runs before failure)
try {
  var now = performance.now();
  var res = performance.getEntriesByType('resource');
  var ents = performance.getEntriesByType('navigation').concat(res);
  var failed = [], slow = [], statusKnown = false, inWindow = 0;
  for (var i = 0; i < ents.length; i++) {
    var e = ents[i];
    if (now - (e.startTime + e.duration) > winMs) continue;
    var type = e.entryType === 'navigation' ? 'document' : e.initiatorType;
    if (['document', 'xmlhttprequest', 'fetch', 'beacon', 'other'].indexOf(type) < 0) continue;
    inWindow++;
    var st = typeof e.responseStatus === 'number' ? e.responseStatus : null;
    if (st !== null) statusKnown = true;
    var same = true, path = String(e.name).split('?')[0];
    try { var u = new URL(e.name); same = u.origin === location.origin; path = (same ? '' : u.host) + u.pathname; } catch (x) {}
    var item = { path: path.slice(0, 200), type: type, status: st, ms: Math.round(e.duration), inStep: inStep(e) };
    // 4xx counts only after the last action (e.g. search/validation rejected); on page load it is mostly noise
    if (st !== null && (st >= 500 || (st === 0 && same) || (st >= 400 && item.inStep))) failed.push(item);
    else if (e.duration >= 10000) slow.push(item);
  }
  // Same endpoint failing again and again (e.g. once per loop iteration, also in the iterations that
  // passed) is background noise: group it into one row with a count.
  var byPath = {}, grouped = [];
  for (var g = 0; g < failed.length; g++) {
    var fi = failed[g], gk = fi.path + (fi.inStep ? '#step' : '');
    if (byPath[gk]) { byPath[gk].count++; byPath[gk].status = fi.status; byPath[gk].ms = fi.ms; }
    else { fi.count = 1; byPath[gk] = fi; grouped.push(fi); }
  }
  // Last API calls before the failure, whatever their status: shows whether the last action reached
  // the server at all (agoSec = seconds before the failure).
  // One row per endpoint (latest call), so a polling/beacon endpoint cannot push the useful calls out
  var recent = [], recentSeen = {};
  for (var rq = ents.length - 1; rq >= 0 && recent.length < 8; rq--) {
    var re = ents[rq];
    if (['xmlhttprequest', 'fetch'].indexOf(re.initiatorType) < 0) continue;
    if (now - re.startTime > winMs) continue;
    var rp = String(re.name).split('?')[0];
    try { var ru = new URL(re.name); rp = (ru.origin === location.origin ? '' : ru.host) + ru.pathname; } catch (x) {}
    if (recentSeen[rp]) { recentSeen[rp].calls++; continue; }
    recent.push({ path: rp.slice(0, 200), status: typeof re.responseStatus === 'number' ? re.responseStatus : null,
                  ms: Math.round(re.duration), agoSec: Math.round((now - re.startTime) / 1000), calls: 1, inStep: inStep(re) });
    recentSeen[rp] = recent[recent.length - 1];
  }
  // API calls made since the last action (polling endpoints - called 5+ times in the window - left out):
  // none at all means the action never reached the server.
  var stepCalls = null;
  if (stepFrom > 0) {
    stepCalls = [];
    for (var sc = 0; sc < recent.length; sc++) {
      if (recent[sc].inStep && recent[sc].calls < 5) stepCalls.push(recent[sc]);
    }
  }
  out.network = { failed: grouped.slice(-5), stepCalls: stepCalls, slow: slow.slice(-5), recent: recent, statusKnown: inWindow ? statusKnown : null,
                  bufferFull: res.length >= 250 };
} catch (e) { out.errors.push('network: ' + e.message); }

// 4c. payload/response of the API calls before the failure, from network-recorder.js (Chromium only).
// Failed (4xx/5xx/terputus) and slow calls always come first; then the calls since the last action. The same
// endpoint called again and again (monitoring beacon, polling, translations) is one row with a count, and
// endpoints called 5+ times are left out of the "since the last action" part - they are background noise.
try {
  var kn = window.__katalanNet;
  if (kn && kn.calls) {
    var nowEpoch = Date.now(), seen = {}, all = [];
    var src = (kn.bad || []).concat(kn.calls);
    for (var ci = 0; ci < src.length; ci++) {
      var c = src[ci];
      if (seen[c.id] || nowEpoch - (c.start + (c.ms || 0)) > winMs) continue;
      seen[c.id] = true;
      all.push(c);
    }
    all.sort(function (a, b) { return a.start - b.start; });
    var keyOf = function (c) { return c.method + ' ' + String(c.url).split('?')[0]; };
    var counts = {};
    for (var k = 0; k < all.length; k++) counts[keyOf(all[k])] = (counts[keyOf(all[k])] || 0) + 1;
    var isBad = function (c) { return c.status === 0 || (typeof c.status === 'number' && c.status >= 400) || (c.ms || 0) >= 10000; };
    var pick = function (c) {
      return { id: c.id, method: c.method, url: c.url, status: c.status, ms: c.ms, inStep: stepFrom > 0 && c.start >= stepFrom,
               agoSec: Math.round((nowEpoch - c.start) / 1000), count: counts[keyOf(c)], reqHeaders: c.reqHeaders, req: c.req,
               resType: c.resType, resHeaders: c.resHeaders, res: c.res, err: c.err };
    };
    // newest call per endpoint+status for the failed ones, newest per endpoint for the rest
    var bad = [], badSeen = {}, ok = [], okSeen = {};
    for (var j = all.length - 1; j >= 0; j--) {
      var cj = all[j], key = keyOf(cj);
      if (isBad(cj)) {
        if (!badSeen[key + '#' + cj.status]) { badSeen[key + '#' + cj.status] = true; bad.push(pick(cj)); }
      } else if (stepFrom > 0 && cj.start >= stepFrom && counts[key] < 5 && !okSeen[key]) {
        okSeen[key] = true; ok.push(pick(cj));
      }
    }
    bad = bad.slice(0, 8);
    out.networkCalls = bad.concat(ok.slice(0, Math.max(0, 12 - bad.length)));
  } else {
    out.errors.push('network: perekam payload tidak aktif di halaman ini (tab baru / bukan Chrome)');
  }
} catch (e) { out.errors.push('networkCalls: ' + e.message); }

return JSON.stringify(out);
