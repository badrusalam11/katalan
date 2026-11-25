// Network recorder for failure diagnostics (docs/FAILURE_DIAGNOSTICS.md).
// Registered with Page.addScriptToEvaluateOnNewDocument, so it runs before the page's own scripts.
// Keeps the last MAX fetch/XHR calls (method, URL, status, duration, request body, response body) in
// window.__katalanNet so the failure probe can show what the API received and answered.
// Rules: never changes what the app sees (same promise, same response, same exceptions), masks secrets
// by key before anything is stored, truncates bodies, never throws.
(function () {
  if (window !== window.top || window.__katalanNet) return;
  var MAX = 40, MAX_REQ = 4000, MAX_RES = 8000, MAX_READ = 2 * 1024 * 1024;
  // calls: last MAX calls of any status. bad: failed (4xx/5xx/terputus) or slow calls, kept apart so a chatty
  // endpoint (monitoring beacon, polling) cannot push the one failed call out before the diagnosis reads it.
  var MAX_BAD = 20;
  var net = window.__katalanNet = { v: 2, calls: [], bad: [], seq: 0 };
  var SECRET_KEY = /^(pin|otp|cvv|cvc|mpin|tpin)$|pass|pwd|token|secret|authori|cookie|session|credential|signature|captcha|private.?key|api.?key/i;

  function maskObj(o, depth) {
    if (depth > 20 || o === null || typeof o !== 'object') return o;
    if (Array.isArray(o)) { for (var i = 0; i < o.length; i++) o[i] = maskObj(o[i], depth + 1); return o; }
    for (var k in o) {
      if (!Object.prototype.hasOwnProperty.call(o, k)) continue;
      if (SECRET_KEY.test(k) && o[k] !== null && typeof o[k] !== 'object') o[k] = '***';
      else o[k] = maskObj(o[k], depth + 1);
    }
    return o;
  }
  function maskForm(s) {
    return s.replace(/(^|&)([^=&]+)=([^&]*)/g, function (m, a, k, v) {
      var key = k; try { key = decodeURIComponent(k); } catch (e) {}
      return a + k + '=' + (SECRET_KEY.test(key) ? '***' : v);
    });
  }
  function mask(s) {
    if (typeof s !== 'string' || !s) return s;
    var t = s.trim();
    if (t.charAt(0) === '{' || t.charAt(0) === '[') {
      try { return JSON.stringify(maskObj(JSON.parse(t), 0)); } catch (e) {}
    }
    if (/^[^\s=&]+=[^\s]*(&[^\s=&]+=[^\s]*)*$/.test(t)) return maskForm(t);
    return s.replace(/(bearer)\s+[A-Za-z0-9._\-]+/ig, '$1 ***');
  }
  function cut(s, max) {
    if (typeof s !== 'string') return s;
    return s.length > max ? s.slice(0, max) + '…[dipotong, total ' + s.length + ' karakter]' : s;
  }
  function urlOf(u) {
    try {
      var x = new URL(String(u), location.href);
      var q = x.search ? '?' + maskForm(x.search.slice(1)) : '';
      return (x.origin === location.origin ? '' : x.host) + x.pathname + q;
    } catch (e) { return String(u).slice(0, 300); }
  }
  function bodyOf(b) {
    try {
      if (b === undefined || b === null) return null;
      if (typeof b === 'string') return cut(mask(b), MAX_REQ);
      if (typeof URLSearchParams !== 'undefined' && b instanceof URLSearchParams) return cut(maskForm(b.toString()), MAX_REQ);
      if (typeof FormData !== 'undefined' && b instanceof FormData) {
        var parts = [];
        b.forEach(function (v, k) {
          parts.push(k + '=' + (SECRET_KEY.test(k) ? '***' : (typeof v === 'string' ? v : '[file ' + (v && v.name) + ']')));
        });
        return cut('[FormData] ' + parts.join('&'), MAX_REQ);
      }
      if (typeof Blob !== 'undefined' && b instanceof Blob) return '[binary ' + b.size + ' bytes]';
      if (b.byteLength !== undefined) return '[binary ' + b.byteLength + ' bytes]';
      return cut(mask(String(b)), MAX_REQ);
    } catch (e) { return '[tidak terbaca]'; }
  }
  // Headers: only what the page's JS can see. Request: the ones the app sets (not Cookie/User-Agent, which the
  // browser adds). Response: what the server exposes (never Set-Cookie). Secret ones masked by name.
  var MAX_HEADERS = 40, MAX_HEADER_VALUE = 500;
  var SECRET_HEADER = /authori|cookie|token|secret|api.?key|session|signature|password|pin$|otp|csrf|xsrf/i;
  function addHeader(h, k, v) {
    try {
      if (!k || Object.keys(h).length >= MAX_HEADERS) return;
      var key = String(k).toLowerCase(), val = String(v == null ? '' : v);
      if (SECRET_HEADER.test(key)) val = /^bearer\s/i.test(val) ? 'Bearer ***' : '***';
      h[key] = h[key] ? h[key] + ', ' + val.slice(0, MAX_HEADER_VALUE) : val.slice(0, MAX_HEADER_VALUE);
    } catch (e) {}
  }
  function headersOf(src) {
    var h = {};
    try {
      if (!src) return h;
      if (typeof Headers !== 'undefined' && src instanceof Headers) { src.forEach(function (v, k) { addHeader(h, k, v); }); }
      else if (Array.isArray(src)) { for (var i = 0; i < src.length; i++) addHeader(h, src[i][0], src[i][1]); }
      else if (typeof src === 'object') { for (var k in src) if (Object.prototype.hasOwnProperty.call(src, k)) addHeader(h, k, src[k]); }
    } catch (e) {}
    return h;
  }
  function parseRawHeaders(raw) {
    var h = {};
    String(raw || '').trim().split(/[\r\n]+/).forEach(function (line) {
      var i = line.indexOf(':');
      if (i > 0) addHeader(h, line.slice(0, i).trim(), line.slice(i + 1).trim());
    });
    return h;
  }

  function textual(type) {
    return !type || /json|text|xml|javascript|x-www-form-urlencoded/i.test(type);
  }
  function push(c) {
    try {
      net.calls.push(c);
      if (net.calls.length > MAX) net.calls.splice(0, net.calls.length - MAX);
      if (c.status === 0 || c.status >= 400 || (c.ms || 0) >= 10000) {
        net.bad.push(c);
        if (net.bad.length > MAX_BAD) net.bad.splice(0, net.bad.length - MAX_BAD);
      }
    } catch (e) {}
  }

  if (window.XMLHttpRequest) {
    var XP = XMLHttpRequest.prototype, open = XP.open, send = XP.send, setHeader = XP.setRequestHeader;
    XP.open = function (method, url) {
      try { this.__kn = { method: String(method || 'GET').toUpperCase(), url: urlOf(url), reqHeaders: {} }; } catch (e) {}
      return open.apply(this, arguments);
    };
    XP.setRequestHeader = function (k, v) {
      try { if (this.__kn) addHeader(this.__kn.reqHeaders, k, v); } catch (e) {}
      return setHeader.apply(this, arguments);
    };
    XP.send = function (body) {
      var xhr = this, info = xhr.__kn;
      try {
        if (info) {
          var c = { id: ++net.seq, kind: 'xhr', method: info.method, url: info.url, start: Date.now(),
                    reqHeaders: info.reqHeaders, req: bodyOf(body), status: null, ms: null, resType: null,
                    resHeaders: null, res: null };
          xhr.addEventListener('loadend', function () {
            try {
              c.ms = Date.now() - c.start;
              c.status = xhr.status;
              c.resType = xhr.getResponseHeader('Content-Type');
              c.resHeaders = parseRawHeaders(xhr.getAllResponseHeaders());
              var rt = xhr.responseType;
              if (rt === '' || rt === 'text') {
                c.res = textual(c.resType) ? cut(mask(xhr.responseText || ''), MAX_RES) : '[' + (c.resType || 'binary') + ']';
              } else if (rt === 'json') {
                c.res = cut(xhr.response == null ? '' : JSON.stringify(maskObj(xhr.response, 0)), MAX_RES);
              } else {
                c.res = '[responseType ' + rt + ']';
              }
              if (xhr.status === 0) c.err = 'gagal/terputus';
            } catch (e) {}
            push(c);
          });
        }
      } catch (e) {}
      return send.apply(this, arguments);
    };
  }

  if (window.fetch) {
    var nativeFetch = window.fetch;
    window.fetch = function (input, init) {
      var p = nativeFetch.apply(this, arguments);
      try {
        var isReq = typeof Request !== 'undefined' && input instanceof Request;
        var c = { id: ++net.seq, kind: 'fetch',
                  method: String((init && init.method) || (isReq && input.method) || 'GET').toUpperCase(),
                  url: urlOf(isReq ? input.url : input), start: Date.now(),
                  reqHeaders: headersOf((init && init.headers) || (isReq && input.headers)),
                  req: bodyOf(init && init.body), status: null, ms: null, resType: null, resHeaders: null, res: null };
        if (c.req === null && isReq && c.method !== 'GET' && c.method !== 'HEAD') {
          try { input.clone().text().then(function (t) { c.req = cut(mask(t), MAX_REQ); }, function () {}); } catch (e) {}
        }
        p.then(function (r) {
          try {
            c.ms = Date.now() - c.start;
            c.status = r.status;
            c.resType = r.headers.get('Content-Type');
            c.resHeaders = headersOf(r.headers);
            var len = Number(r.headers.get('Content-Length') || 0);
            if (!textual(c.resType) || /event-stream/i.test(c.resType || '')) { c.res = '[' + (c.resType || 'binary') + ']'; push(c); return; }
            if (len > MAX_READ) { c.res = '[' + len + ' bytes, tidak disimpan]'; push(c); return; }
            r.clone().text().then(function (t) { c.res = cut(mask(t), MAX_RES); push(c); },
                                  function () { c.res = '[tidak terbaca]'; push(c); });
          } catch (e) { push(c); }
        }, function (e) {
          c.ms = Date.now() - c.start; c.status = 0; c.err = String(e && e.message || e).slice(0, 200); push(c);
        });
      } catch (e) {}
      return p;
    };
  }
})();
