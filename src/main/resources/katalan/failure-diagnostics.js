// Failure diagnostics probe (docs/FAILURE_DIAGNOSTICS.md).
// READ-ONLY: never clicks, scrolls, types or mutates the DOM. Runs once, only when a test case fails.
// arguments: [locatorBy, locatorValue, literal, literalKind, maxTextChars, windowMs, stepFromEpochMs]
var by = arguments[0], val = arguments[1], lit = arguments[2], litKind = arguments[3];
var maxT = arguments[4] || 300, winMs = arguments[5] || 30000;
// Epoch ms of the last action before the failing step (0 = unknown). Requests started after it are
// "inStep": caused by what the test just did, not background traffic of the page.
var stepFrom = arguments[6] || 0;
// Locator of the last action before the failure (e.g. the click that should have opened a popup)
var actBy = arguments[7], actVal = arguments[8];
var origin0 = performance.timeOrigin || (Date.now() - performance.now());
function inStep(e) { return stepFrom > 0 && origin0 + e.startTime >= stepFrom; }
var out = { errors: [] };

function clean(s) { return String(s == null ? '' : s).replace(/\s+/g, ' ').trim(); }
function cut(s) { s = clean(s); return s.length > maxT ? s.slice(0, maxT) + '…' : s; }
function isVisible(el) {
  try {
    if (!el || el.nodeType !== 1 || !el.getClientRects().length) return false;
    var cs = getComputedStyle(el);
    return cs.visibility !== 'hidden' && cs.display !== 'none' && parseFloat(cs.opacity || '1') > 0;
  } catch (e) { return false; }
}
function describe(el) {
  if (!el || el.nodeType !== 1) return null;
  return { tag: el.tagName.toLowerCase(), id: el.id || null,
           classes: typeof el.className === 'string' ? cut(el.className) : null,
           text: cut(el.innerText || el.value || '').slice(0, 80) };
}

// 4a. page
try {
  out.page = {
    url: location.protocol + '//' + location.host + location.pathname,
    title: cut(document.title), readyState: document.readyState,
    errorPage: location.protocol === 'chrome-error:' || !!document.getElementById('main-frame-error'),
    w: window.innerWidth, h: window.innerHeight, ua: navigator.userAgent
  };
} catch (e) { out.errors.push('page: ' + e.message); }

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
    if (st !== null && (st >= 500 || (st === 0 && same))) failed.push(item);
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

// 4c. locator
var nodes = [];
if (by && val) {
  try {
    if (by === 'xpath') {
      var snap = document.evaluate(val, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
      out.matches = snap.snapshotLength;
      for (var j = 0; j < Math.min(snap.snapshotLength, 50); j++) nodes.push(snap.snapshotItem(j));
    } else if (by === 'css') {
      var list = document.querySelectorAll(val);
      out.matches = list.length;
      for (var k = 0; k < Math.min(list.length, 50); k++) nodes.push(list[k]);
    }
    if (out.matches != null) {
      var visibleNodes = nodes.filter(isVisible);
      out.visibleMatches = visibleNodes.length;
      var el = visibleNodes[0] || nodes[0];
      if (el && el.nodeType === 1) {
        var r = el.getBoundingClientRect();
        var inVp = r.bottom > 0 && r.right > 0 && r.top < window.innerHeight && r.left < window.innerWidth;
        out.firstMatch = describe(el);
        out.firstMatch.visible = isVisible(el);
        out.firstMatch.inViewport = inVp;
        out.firstMatch.disabled = !!(el.disabled || el.getAttribute('aria-disabled') === 'true');
        if (out.firstMatch.visible && inVp && r.width > 0 && r.height > 0) {
          var hit = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
          if (hit && hit !== el && !el.contains(hit) && !hit.contains(el)) out.obscuredBy = describe(hit);
        }
      }
    }
  } catch (e) { out.errors.push('locator: ' + e.message); }
}

// 4c2. the last action's target: was it there, and could it react? A click on a disabled button (or on
// a span inside one) is accepted by the browser and silently does nothing.
if (actBy && actVal) {
  try {
    var an = null, acount = 0;
    if (actBy === 'xpath') {
      var asnap = document.evaluate(actVal, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
      acount = asnap.snapshotLength;
      for (var ai = 0; ai < asnap.snapshotLength && !an; ai++) { if (isVisible(asnap.snapshotItem(ai))) an = asnap.snapshotItem(ai); }
      if (!an && acount) an = asnap.snapshotItem(0);
    } else if (actBy === 'css') {
      var alist = document.querySelectorAll(actVal);
      acount = alist.length;
      for (var aj = 0; aj < alist.length && !an; aj++) { if (isVisible(alist[aj])) an = alist[aj]; }
      if (!an && acount) an = alist[0];
    }
    var la = { matches: acount };
    if (an && an.nodeType === 1) {
      var selfDisabled = !!(an.disabled || an.getAttribute('aria-disabled') === 'true');
      var holder = an.parentElement ? an.parentElement.closest('button,[role=button],fieldset,a') : null;
      var parentDisabled = !!(holder && (holder.disabled || holder.getAttribute('aria-disabled') === 'true'));
      var ctl = selfDisabled ? an : (parentDisabled ? holder : an);
      la.visible = isVisible(an);
      la.disabled = selfDisabled || parentDisabled;
      la.tag = ctl.tagName.toLowerCase();
      la.text = cut(ctl.innerText || ctl.value || '').slice(0, 60);
    }
    out.lastAction = la;
  } catch (e) { out.errors.push('lastAction: ' + e.message); }
}

// 4d. candidates: values of the same shape (data) or near-identical labels (label)
try {
  if (lit) {
    var shapes = {
      digits: /\d[\d .-]{4,}\d/g,
      amount: /(?:Rp\.?\s*)?\d{1,3}(?:[.,]\d{3})+(?:[.,]\d{1,2})?/g,
      date: /\b\d{1,4}[\/.\- ](?:\d{1,2}|[A-Za-z]{3,9})[\/.\- ]\d{2,4}\b/g,
      email: /[\w.+-]+@[\w-]+\.[\w.-]+/g
    };
    var seen = {}, cands = [];
    var litNorm = clean(lit).toLowerCase(), litDigits = lit.replace(/\D/g, '');
    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
    var n, scanned = 0;
    while ((n = walker.nextNode()) && cands.length < 5 && scanned < 20000) {
      scanned++;
      var txt = clean(n.nodeValue);
      if (!txt || txt.length > 500 || !isVisible(n.parentElement)) continue;
      if (litKind === 'label') {
        // Exact text would have matched the locator; anything else that is "almost" the label
        // (case, extra spaces, partial) is a candidate. Keep raw spacing so the difference is visible.
        var raw = String(n.nodeValue).replace(/[\r\n\t]+/g, ' ');
        var low = txt.toLowerCase();
        if (raw !== lit && low.length <= 60 &&
            (low.replace(/\s/g, '') === litNorm.replace(/\s/g, '') || low.indexOf(litNorm) >= 0 || (low.length >= 3 && litNorm.indexOf(low) >= 0))) {
          var shown = raw.trim() === lit ? raw.slice(0, 60) : txt;
          if (!seen[shown]) { seen[shown] = 1; cands.push(shown); }
        }
      } else if (shapes[litKind]) {
        var m = txt.match(shapes[litKind]) || [];
        for (var q = 0; q < m.length && cands.length < 5; q++) {
          var v = clean(m[q]);
          if (litKind === 'digits') {
            var d = v.replace(/\D/g, '');
            if (Math.abs(d.length - litDigits.length) > 3 || d === litDigits) continue;
            v = d;
          } else if (v.toLowerCase() === litNorm) continue;
          if (!seen[v]) { seen[v] = 1; cands.push(v); }
        }
      }
    }
    out.candidates = cands;
    // Labels with no near match: list the buttons that ARE on screen, as evidence
    if (litKind === 'label' && !cands.length) {
      var btns = document.querySelectorAll('button, a, [role=button], input[type=submit], input[type=button]');
      var bl = [];
      for (var b = 0; b < btns.length && bl.length < 5; b++) {
        var bt = clean(btns[b].innerText || btns[b].value || '');
        if (bt && bt.length <= 40 && isVisible(btns[b]) && bl.indexOf(bt) < 0) bl.push(bt);
      }
      out.visibleButtons = bl;
    }
  }
} catch (e) { out.errors.push('candidates: ' + e.message); }

// 4e. visible messages (narrowed selectors; no [class*=error]/[class*=danger])
try {
  var msgSel = [['role=alert', '[role=alert]'], ['aria-live', '[aria-live=assertive], [aria-live=polite]'],
                ['toast', '.toast, .Toastify__toast, .ant-message-notice, .ant-notification-notice'],
                ['alert', '.alert'], ['invalid-feedback', '.invalid-feedback, .ant-form-item-explain-error'],
                ['swal2', '.swal2-popup']];
  var msgs = [], mseen = {};
  for (var s = 0; s < msgSel.length && msgs.length < 5; s++) {
    var els = document.querySelectorAll(msgSel[s][1]);
    for (var t = 0; t < els.length && msgs.length < 5; t++) {
      var me = els[t];
      if (!isVisible(me)) continue;
      // Route announcers (Next.js/Gatsby/a11y libs) are aria-live regions that read out the page title;
      // visually-hidden regions (1px clip) are not something the tester could have seen either.
      if (/announcer|a11y/i.test(me.id || '') || /announcer|sr-only|visually-hidden|a11y/i.test(String(me.className || ''))) continue;
      var mr = me.getBoundingClientRect();
      if (mr.width <= 2 || mr.height <= 2) continue;
      var mt = cut(me.innerText);
      if (mt.length < 2 || mseen[mt] || mt === clean(document.title)) continue;
      // Screen-reader hints of select widgets ("Use Up and Down to choose options, press Enter...")
      if (/use up and down to choose|press enter to select|options? available|results? available|option .* focused/i.test(mt)) continue;
      mseen[mt] = 1;
      msgs.push({ source: msgSel[s][0], text: mt });
    }
  }
  // Error banners are often custom components (no role/alert class): recognise them by their text -
  // an error code like "[099]", "General error", a process/trace id, "something went wrong"...
  var errText = /(\[\d{2,4}\]\s*\S)|general error|internal (server )?error|something went wrong|terjadi kesalahan|kesalahan sistem|process id|trace id|request id|correlation id|error code|service unavailable|bad gateway|gateway time-?out/i;
  var tw = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null), tn, tscan = 0;
  while ((tn = tw.nextNode()) && msgs.length < 5 && tscan < 20000) {
    tscan++;
    var tt = clean(tn.nodeValue);
    if (tt.length < 4 || tt.length > 200 || !errText.test(tt)) continue;
    var box = tn.parentElement;
    if (!isVisible(box)) continue;
    // Use the small block around the text (code + message + process id), not just the text node
    for (var up = 0; up < 3 && box.parentElement && box.parentElement !== document.body
         && clean(box.parentElement.innerText).length <= 120; up++) box = box.parentElement;
    var bt = cut(box.innerText);
    if (bt.length < 4 || mseen[bt]) continue;
    var dup = false;
    for (var q = 0; q < msgs.length; q++) { if (msgs[q].text.indexOf(bt) >= 0 || bt.indexOf(msgs[q].text) >= 0) dup = true; }
    if (dup) continue;
    mseen[bt] = 1;
    msgs.push({ source: 'error-text', text: bt });
  }
  out.visibleMessages = msgs;
} catch (e) { out.errors.push('messages: ' + e.message); }

// 4g. untranslated i18n keys on screen ("form.email.label", "button.save"): the page rendered
// without its texts/config, which usually means an API/config call failed - not a locator change.
try {
  var keyRe = /^[a-z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$/;
  var notKey = /\.(com|co|id|net|org|io|html?|js|css|png|jpe?g|pdf|xlsx?|csv|json)$/i;
  var kw = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
  var kn, keys = [], kseen = {}, kscan = 0;
  while ((kn = kw.nextNode()) && kscan < 20000 && keys.length < 50) {
    kscan++;
    var kt = clean(kn.nodeValue).replace(/\s*\*$/, '');
    if (kt.length < 5 || kt.length > 80 || !keyRe.test(kt) || notKey.test(kt) || kseen[kt]) continue;
    if (!isVisible(kn.parentElement)) continue;
    kseen[kt] = 1;
    keys.push(kt);
  }
  out.untranslatedKeys = keys.slice(0, 8);
  out.untranslatedCount = keys.length;
} catch (e) { out.errors.push('i18n: ' + e.message); }

// 4f. open dialogs
try {
  var dlg = document.querySelectorAll('[role=dialog], .modal.show, dialog[open], .swal2-container, .ant-modal-wrap');
  var dl = [];
  for (var z = 0; z < dlg.length && dl.length < 3; z++) {
    if (!isVisible(dlg[z])) continue;
    var tEl = dlg[z].querySelector('.modal-title, .swal2-title, .ant-modal-title, h1, h2, h3, h4, h5');
    var title = cut(tEl ? tEl.innerText : (dlg[z].getAttribute('aria-label') || '')).slice(0, 100);
    dl.push({ tag: dlg[z].tagName.toLowerCase(),
              classes: typeof dlg[z].className === 'string' ? cut(dlg[z].className).slice(0, 80) : null,
              title: title });
  }
  out.openDialogs = dl;
} catch (e) { out.errors.push('dialogs: ' + e.message); }

return JSON.stringify(out);
