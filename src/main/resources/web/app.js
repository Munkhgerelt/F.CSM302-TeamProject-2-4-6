/*
 * II-4-6 Multithreaded Sorting - browser side.
 * The page never sorts anything itself: it asks the Java server to run the
 * real 2 sorting threads + 1 merging thread with tracing on, and then shows
 * the recorded events (in their global seq order) step by step.
 *  - Replay mode:   the run is finished; its recorded events are replayed (forward/back).
 *  - Lockstep mode: the threads are still alive and blocked on a semaphore; every
 *                   "Step" grants one operation on the server and shows its events.
 */
'use strict';

const $ = (id) => document.getElementById(id);
// Canvas colours come from the CSS custom properties in style.css.
const COLORS = {};
const COLOR_VARS = {
  left: '--c-left', right: '--c-right', merge: '--c-merge', parent: '--c-parent', cmp: '--c-cmp',
  mov: '--c-mov', used: '--c-used', ink: '--ink', muted: '--muted', line: '--line', grid: '--c-grid'
};
function readColors() {
  const cs = getComputedStyle(document.documentElement);
  for (const [k, v] of Object.entries(COLOR_VARS)) COLORS[k] = cs.getPropertyValue(v).trim() || '#888';
}
readColors();

const BOOK_EXAMPLE = '7, 12, 19, 3, 18, 4, 2, 6, 15, 8';
const THREADS = ['Parent', 'Sorter-L', 'Sorter-R', 'Merger'];
const THREAD_CLASS = { 'Parent': 'p', 'Sorter-L': 'l', 'Sorter-R': 'r', 'Merger': 'm' };
const JAVA_STATE_TEXT = {
  NEW: 'NEW, start() хийгээгүй', RUNNABLE: 'RUNNABLE, ажиллаж байна', BLOCKED: 'BLOCKED',
  WAITING: 'WAITING', TIMED_WAITING: 'TIMED_WAITING', TERMINATED: 'TERMINATED, дууссан'
};
const STATE_TEXT = {
  new: 'Үүсээгүй', created: 'start() дуудагдсан', running: 'Ажиллаж байна',
  waiting: 'join() дээр хүлээж байна', done: 'Дууссан'
};

// ----------------------------------------------------------------- state
const app = {
  meta: null,
  trace: null,        // server response .trace
  response: null,
  pos: 0,             // number of events applied
  playing: false,
  lastFrame: 0,
  carry: 0,
  view: null,         // derived replay state
  mode: 'replay',     // 'replay' | 'live'
  live: null          // {id, next, finished, consumed, waiting, javaStates:{name:{state,tid}}, busy}
};

function freshView(trace) {
  const v = {
    list: trace.input.slice(),
    result: new Array(trace.n).fill(null),
    resultSrc: new Array(trace.n).fill(null),
    hlList: new Map(),       // index -> 'cmp' | 'mov'
    hlResult: -1,
    mergePtr: null,          // {i, j}
    threads: {},
    joined: new Set(),
    lastEvent: null
  };
  for (const name of THREADS) v.threads[name] = { state: 'new', cmp: 0, swp: 0, wr: 0 };
  return v;
}

// Applies one recorded event to the view. Only real trace events change the picture.
function apply(v, e, mid) {
  const th = v.threads[e.th];
  v.hlList.clear();
  v.hlResult = -1;
  v.lastEvent = e;
  switch (e.type) {
    case 'RUN_START': th.state = 'running'; break;
    case 'START': v.threads[e.note].state = 'created'; break;
    case 'JOIN':
      v.joined.add(e.note);
      v.threads.Parent.state = 'running';
      break;
    case 'T_START': case 'M_START': th.state = 'running'; break;
    case 'T_DONE': case 'M_DONE': th.state = 'done'; break;
    case 'RUN_END': th.state = 'done'; break;
    case 'CMP': th.cmp++; v.hlList.set(e.a, 'cmp'); v.hlList.set(e.b, 'cmp'); break;
    case 'CMPV': th.cmp++; v.hlList.set(e.a, 'cmp'); break;
    case 'CMPA': th.cmp++; break;   // positions refer to merge sort's private buffer
    case 'SWP': {
      th.swp++;
      const t = v.list[e.a]; v.list[e.a] = v.list[e.b]; v.list[e.b] = t;
      v.hlList.set(e.a, 'mov'); v.hlList.set(e.b, 'mov');
      break;
    }
    case 'SET': th.wr++; v.list[e.a] = e.b; v.hlList.set(e.a, 'mov'); break;
    case 'MCMP':
      th.cmp++;
      v.mergePtr = { i: e.a, j: e.b };
      v.hlList.set(e.a, 'cmp'); v.hlList.set(e.b, 'cmp');
      break;
    case 'MW':
      th.wr++;
      v.result[e.a] = e.c;
      v.resultSrc[e.a] = e.b < mid ? 'left' : 'right';
      v.hlResult = e.a;
      v.hlList.set(e.b, 'mov');
      advanceMergePtr(v, e.b, mid);
      break;
  }
  // Parent state is derived from its own recorded calls: while some started
  // thread has not been joined yet, the parent is blocked inside join().
  const p = v.threads.Parent;
  if (e.type === 'RUN_END') p.state = 'done';
  else if (p.state !== 'new') {
    const open = ['Sorter-L', 'Sorter-R', 'Merger']
      .some((n) => v.threads[n].state !== 'new' && !v.joined.has(n));
    const lastWasStart = e.th === 'Parent' && e.type === 'START';
    p.state = open && !lastWasStart ? 'waiting' : 'running';
  }
}

// After result[k] = list[src] the merger's pointer on that side moves on.
function advanceMergePtr(v, src, mid) {
  if (!v.mergePtr) v.mergePtr = { i: 0, j: mid };
  if (src < mid) v.mergePtr.i = src + 1; else v.mergePtr.j = src + 1;
}

function seek(target) {
  const t = app.trace;
  if (!t) return;
  target = Math.max(0, Math.min(target, t.events.length));
  if (target < app.pos) { app.view = freshView(t); app.pos = 0; }
  while (app.pos < target) apply(app.view, t.events[app.pos++], t.mid);
  render();
}

// ----------------------------------------------------------------- drawing
function setupCanvas(cv) {
  const dpr = window.devicePixelRatio || 1;
  // The CSS height comes from the HTML height attribute, read once: cv.height below
  // overwrites that attribute with the pixel size, which on HiDPI screens (dpr = 2)
  // would otherwise double the canvas on every render.
  if (cv._baseH === undefined) cv._baseH = Number(cv.getAttribute('height'));
  const w = cv.clientWidth, h = cv._baseH;
  if (cv.width !== Math.round(w * dpr) || cv._h !== h) {
    cv.width = Math.round(w * dpr); cv.height = Math.round(h * dpr); cv._h = h;
    cv.style.height = h + 'px';
  }
  const ctx = cv.getContext('2d');
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, w, h);
  return { ctx, w, h };
}

function valueRange(values) {
  let lo = Math.min(0, ...values), hi = Math.max(0, ...values);
  if (lo === hi) hi = lo + 1;
  return { lo, hi };
}

function drawBars(cv, values, colorOf, opts) {
  const { ctx, w, h } = setupCanvas(cv);
  const n = values.length;
  const padB = n <= 40 ? 18 : 6, padT = 8;
  const plotH = h - padB - padT;
  const range = opts.range;
  const yZero = padT + plotH * (range.hi / (range.hi - range.lo));
  const slot = w / Math.max(n, 1);
  const gap = slot > 6 ? Math.min(3, slot * 0.18) : 0;
  ctx.fillStyle = COLORS.grid;
  ctx.fillRect(0, yZero - 0.5, w, 1);
  for (let i = 0; i < n; i++) {
    const x = i * slot + gap / 2, bw = Math.max(slot - gap, 1);
    const val = values[i];
    if (val === null || val === undefined) {
      ctx.strokeStyle = COLORS.line; ctx.setLineDash([3, 3]);
      ctx.strokeRect(x + 0.5, padT + 0.5, bw - 1, plotH - 1);
      ctx.setLineDash([]);
      continue;
    }
    const y = padT + plotH * ((range.hi - val) / (range.hi - range.lo));
    ctx.fillStyle = colorOf(i);
    const top = Math.min(y, yZero), bh = Math.max(Math.abs(yZero - y), 1.5);
    if (bw >= 6 && ctx.roundRect) {
      // rounded on the end away from zero, square on the baseline
      const r = Math.min(4, bw / 3, bh);
      ctx.beginPath();
      ctx.roundRect(x, top, bw, bh, val >= 0 ? [r, r, 0, 0] : [0, 0, r, r]);
      ctx.fill();
    } else {
      ctx.fillRect(x, top, bw, bh);
    }
    if (n <= 40) {
      ctx.fillStyle = COLORS.muted;
      ctx.font = '11px system-ui, sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText(String(val), x + bw / 2, h - 5);
    }
  }
  if (opts.mid !== undefined && opts.mid > 0 && opts.mid < n) {
    ctx.fillStyle = COLORS.ink;
    ctx.fillRect(opts.mid * slot - 1, padT - 4, 2, plotH + 8);
  }
  if (opts.pointers) {
    ctx.fillStyle = COLORS.merge;
    ctx.font = '600 11px system-ui, sans-serif';
    ctx.textAlign = 'center';
    for (const [label, idx] of opts.pointers) {
      if (idx < 0 || idx >= n) continue;
      ctx.fillText(label, idx * slot + slot / 2, padT + 2);
    }
  }
}

function render() {
  const t = app.trace;
  if (!t) return;
  const v = app.view, mid = t.mid, n = t.n;
  const range = valueRange(t.input);
  const ptr = v.mergePtr;
  const mergeStarted = v.threads.Merger.state !== 'new' && v.threads.Merger.state !== 'created';

  drawBars($('cvList'), v.list, (i) => {
    const hl = v.hlList.get(i);
    if (hl === 'cmp') return COLORS.cmp;
    if (hl === 'mov') return COLORS.mov;
    if (mergeStarted && ptr && ((i < mid && i < ptr.i) || (i >= mid && i < ptr.j))) return COLORS.used;
    return i < mid ? COLORS.left : COLORS.right;
  }, { range, mid, pointers: mergeStarted && ptr && v.threads.Merger.state !== 'done' ? [['i', ptr.i], ['j', ptr.j]] : null });

  drawBars($('cvResult'), v.result, (i) => {
    if (i === v.hlResult) return COLORS.mov;
    return COLORS.merge;
  }, { range });

  drawTimeline();
  renderThreads();
  renderLog();
  renderSummary();

  const total = t.events.length;
  $('scrub').max = String(total);
  $('scrub').value = String(app.pos);
  $('posOut').textContent = app.pos + ' / ' + total;
  if (app.mode === 'live') {
    const fin = app.live.finished;
    $('scrub').disabled = true;
    $('btnBack').disabled = true;
    $('btnStep').disabled = fin || app.live.busy;
    $('btnPlay').disabled = fin;
    $('btnPlay').textContent = app.playing ? 'Түр зогсоох' : (fin ? 'Дууссан' : 'Тоглуулах');
  } else {
    $('scrub').disabled = false;
    $('btnBack').disabled = app.pos === 0;
    $('btnStep').disabled = app.pos >= total;
    $('btnPlay').disabled = app.pos >= total && !app.playing;
    $('btnPlay').textContent = app.playing ? 'Түр зогсоох' : (app.pos >= total ? 'Дууссан' : 'Тоглуулах');
  }
  $('listMeta').textContent = 'n = ' + n + ', зүүн [0,' + mid + ') ба баруун [' + mid + ',' + n + ')';
  const filled = v.result.filter((x) => x !== null).length;
  $('resultMeta').textContent = filled + ' / ' + n + ' байрлал бөглөгдсөн';
}

// Swimlane of the real threads on the nanosecond time axis, with a playhead.
function drawTimeline() {
  const t = app.trace;
  const { ctx, w, h } = setupCanvas($('cvTimeline'));
  const lanes = ['Parent', 'Sorter-L', 'Sorter-R', 'Merger'];
  const laneColor = { 'Parent': COLORS.parent, 'Sorter-L': COLORS.left, 'Sorter-R': COLORS.right, 'Merger': COLORS.merge };
  const labelW = 74, padR = 10, laneH = (h - 22) / lanes.length;
  const ev = t.events;
  const tMax = Math.max(ev.length ? ev[ev.length - 1].t : 1, 1);
  const X = (ns) => labelW + (w - labelW - padR) * (ns / tMax);

  ctx.font = '12px system-ui, sans-serif';
  ctx.textBaseline = 'middle';
  lanes.forEach((name, k) => {
    const y = k * laneH;
    ctx.fillStyle = COLORS.muted; ctx.textAlign = 'left';
    ctx.fillText(name, 0, y + laneH / 2);
    ctx.fillStyle = COLORS.grid;
    ctx.fillRect(labelW, y + laneH / 2 - 0.5, w - labelW - padR, 1);
  });
  // active span of each worker thread from its own T_START/M_START .. T_DONE/M_DONE events,
  // drawn only up to the playhead
  const cur = app.pos > 0 ? ev[app.pos - 1].t : 0;
  const span = {};
  for (let i = 0; i < app.pos; i++) {
    const e = ev[i];
    if (e.type === 'T_START' || e.type === 'M_START') span[e.th] = { from: e.t, to: null };
    if ((e.type === 'T_DONE' || e.type === 'M_DONE') && span[e.th]) span[e.th].to = e.t;
  }
  lanes.forEach((name, k) => {
    const s = span[name];
    if (!s) return;
    const end = s.to === null ? cur : s.to;
    ctx.fillStyle = laneColor[name];
    ctx.fillRect(X(s.from), k * laneH + laneH * 0.3, Math.max(X(end) - X(s.from), 2), laneH * 0.4);
  });
  // the parent's own calls (start / join) as small markers
  for (let i = 0; i < app.pos; i++) {
    const e = ev[i];
    if (e.th !== 'Parent') continue;
    ctx.fillStyle = COLORS.parent;
    ctx.fillRect(Math.round(X(e.t)) - 1, laneH * 0.25, 2, laneH * 0.5);
  }
  // playhead
  ctx.fillStyle = COLORS.ink;
  ctx.fillRect(X(cur) - 1, 0, 2, h - 20);
  ctx.fillStyle = COLORS.muted; ctx.textAlign = 'left';
  ctx.fillText('0 ms', labelW, h - 8);
  const curX = Math.min(Math.max(X(cur), labelW + 30), w - padR - 30);
  ctx.textAlign = 'right';
  if (curX < w - padR - 120) ctx.fillText((tMax / 1e6).toFixed(3) + ' ms', w - padR, h - 8);
  ctx.textAlign = 'center';
  ctx.fillStyle = COLORS.ink;
  ctx.fillText((cur / 1e6).toFixed(3) + ' ms', curX, h - 8);
}

function renderThreads() {
  const t = app.trace, v = app.view;
  const info = {};
  for (const th of t.threads) info[th.name] = th;
  const html = THREADS.map((name) => {
    const s = v.threads[name], i = info[name];
    let detail = '';
    if (name === 'Parent') detail = 'main, join() хийж үр дүнг хэвлэнэ';
    else if (name === 'Merger') detail = 'list[0,' + i.mid + ') + list[' + i.mid + ',' + i.end + ') → result[]';
    else detail = i.algorithm + ', муж [' + i.start + ',' + i.end + ')';
    const js = app.mode === 'live' ? app.live.javaStates[name] : null;
    const tidVal = js ? js.tid : (i && i.tid);
    const tid = tidVal ? ', tid ' + tidVal : '';
    const ops = name === 'Parent' ? '' :
      '<div class="kv">cmp ' + s.cmp + (name === 'Merger' ? '' : ', swap ' + s.swp) + ', write ' + s.wr + '</div>';
    let java = '';
    if (app.mode === 'live') {
      let text = js ? JAVA_STATE_TEXT[js.state] || js.state : 'үүсээгүй (new Thread() хийгээгүй)';
      if (js && js.state === 'WAITING') text = name === 'Parent' ? 'WAITING, join() дээр' : 'WAITING, Semaphore permit хүлээж байна';
      java = '<div class="kv java">Java: ' + text + '</div>';
    }
    return '<div class="thread ' + THREAD_CLASS[name] + '"><div class="name">' + name + tid + '</div>' +
      '<div class="state" data-s="' + s.state + '">' + STATE_TEXT[s.state] + '</div>' +
      '<div class="kv">' + detail + '</div>' + ops + java + '</div>';
  }).join('');
  $('threads').innerHTML = html;
}

function describe(e) {
  const f = (x) => (x === undefined ? '' : x);
  switch (e.type) {
    case 'RUN_START': return 'эхэллээ: n=' + e.a + ', mid=' + e.b;
    case 'START': return e.note + '.start()';
    case 'JOIN': return e.note + '.join() буцлаа';
    case 'T_START': return 'list[' + e.a + ',' + e.b + ') эрэмбэлж эхэлнэ';
    case 'T_DONE': return 'list[' + e.a + ',' + e.b + ') эрэмбэлэгдлээ';
    case 'CMP': return 'list[' + e.a + '] ? list[' + e.b + ']  (' + (e.c < 0 ? '<' : e.c > 0 ? '>' : '=') + ')';
    case 'CMPV': return 'list[' + e.a + '] ? pivot ' + e.b;
    case 'CMPA': return 'buffer-ийн ' + e.a + ' ба ' + e.b + ' дахь утгыг харьцуулав';
    case 'SWP': return 'swap list[' + e.a + '] ↔ list[' + e.b + ']';
    case 'SET': return 'list[' + e.a + '] = ' + e.b;
    case 'M_START': return 'нэгтгэл эхэллээ: [0,' + e.b + ') + [' + e.b + ',' + e.c + ')';
    case 'MCMP': return 'list[' + e.a + '] ? list[' + e.b + ']';
    case 'MW': return 'result[' + e.a + '] = list[' + e.b + '] = ' + e.c;
    case 'M_DONE': return 'result[] бүрэн боллоо';
    case 'RUN_END': return e.a === 1 ? 'дууслаа, result эрэмбэлэгдсэн' : 'дууслаа, АЛДАА: эрэмбэлэгдээгүй';
    default: return e.type + ' ' + f(e.a) + ' ' + f(e.b) + ' ' + f(e.c);
  }
}

function renderLog() {
  const ev = app.trace.events;
  const from = Math.max(0, app.pos - 12);
  const rows = [];
  for (let i = from; i < app.pos; i++) {
    const e = ev[i];
    rows.push('<li' + (i === app.pos - 1 ? ' class="cur"' : '') + '>#' + e.seq + '  ' +
      (e.t / 1e6).toFixed(3).padStart(8, ' ') + ' ms  <span class="th-' + THREAD_CLASS[e.th] + '">' +
      e.th.padEnd(8, ' ') + '</span>  ' + escapeHtml(describe(e)) + '</li>');
  }
  $('log').innerHTML = rows.join('') || '<li>Эхлээд “Ажиллуулах”, дараа нь “Тоглуулах” эсвэл “Алхам ▸”.</li>';
  $('evMeta').textContent = app.pos + ' / ' + ev.length;
}

function renderSummary() {
  if (app.mode === 'live') return renderLiveSummary();
  const t = app.trace, r = app.response;
  const done = app.pos >= t.events.length;
  const preview = (a) => (a.length <= 30 ? a.join(', ') : a.slice(0, 15).join(', ') + ', …, ' + a.slice(-5).join(', '));
  const rows = [
    ['Өгөгдөл', escapeHtml(r.source)],
    ['Оролт', preview(t.input)],
    ['Үр дүн', done ? preview(t.result) : 'тоглуулж дуусахад харагдана'],
    ['Шалгалт', done ? (t.sorted ? '<span class="good">Эрэмбэлэгдсэн, зөв</span>' : '<span class="bad">Буруу</span>') : '…'],
    ['Хугацаа (trace-гүй)', (t.timing.untracedTotalNs / 1e6).toFixed(3) + ' ms (дулаацуулсны дараах ' + t.timing.untracedRuns + ' ажиллалтын медиан)'],
    ['Хугацаа (trace-тэй)', (t.timing.tracedTotalNs / 1e6).toFixed(3) + ' ms'],
    ['Event-ийн тоо', String(t.eventCount)]
  ];
  for (const note of r.notes) rows.push(['Тэмдэглэл', escapeHtml(note)]);
  $('summary').innerHTML = rows.map(([k, val]) => '<dt>' + k + '</dt><dd>' + val + '</dd>').join('');
}

function renderLiveSummary() {
  const t = app.trace, r = app.response, L = app.live;
  const preview = (a) => (a.length <= 30 ? a.join(', ') : a.slice(0, 15).join(', ') + ', …, ' + a.slice(-5).join(', '));
  const rows = [
    ['Горим', 'Lockstep: thread-үүд үйлдэл бүрийн өмнө зогсож, зөвшөөрөл хүлээнэ'],
    ['Өгөгдөл', escapeHtml(r.source)],
    ['Оролт', preview(t.input)],
    ['Олгосон алхам', String(L.consumed) + ' (compare/swap/write)'],
    ['Permit хүлээж буй', L.finished ? '0' : String(L.waiting) + ' thread'],
    ['Үр дүн', L.finished && L.result ? preview(L.result) : 'дуусаагүй'],
    ['Шалгалт', L.finished ? (L.sorted ? '<span class="good">Эрэмбэлэгдсэн, зөв</span>' : '<span class="bad">' + escapeHtml(L.error || 'Буруу') + '</span>') : '…']
  ];
  for (const note of r.notes) rows.push(['Тэмдэглэл', escapeHtml(note)]);
  $('summary').innerHTML = rows.map(([k, val]) => '<dt>' + k + '</dt><dd>' + val + '</dd>').join('');
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
}

// ----------------------------------------------------------------- playback
function speed() {
  // slider 0..100 mapped logarithmically to 1..5000 events per second
  return Math.round(Math.pow(10, (Number($('speed').value) / 100) * Math.log10(5000)));
}

function frame(ts) {
  if (!app.playing) return;
  const dt = app.lastFrame ? (ts - app.lastFrame) / 1000 : 0;
  app.lastFrame = ts;
  app.carry += dt * speed();
  const k = Math.floor(app.carry);
  if (k > 0) {
    app.carry -= k;
    seek(app.pos + k);
  }
  if (app.pos >= app.trace.events.length) {
    app.playing = false;
    setStatus('Тоглуулж дууслаа. Үр дүнг доорх “Үр дүн” хэсгээс харна уу. “Ажиллуулах” дахин дарвал шинэ ажиллалт эхэлнэ.', 'ok');
    render();
    return;
  }
  requestAnimationFrame(frame);
}

function play() {
  if (app.mode === 'live') return livePlay();
  if (!app.trace || app.pos >= app.trace.events.length) return;
  app.playing = true;
  app.lastFrame = 0;
  app.carry = 0;
  requestAnimationFrame(frame);
  render();
}

function pause() {
  app.playing = false;
  render();
}

function stepForward() {
  pause();
  if (app.mode === 'live') liveStep(1); else seek(app.pos + 1);
}

function stepBack() {
  if (app.mode === 'live') return;   // real threads cannot run backwards
  pause();
  seek(app.pos - 1);
}

// ----------------------------------------------------------------- lockstep (live) mode
async function postForm(path, params) {
  const res = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
    body: params.toString()
  });
  const data = await res.json();
  if (!data.ok) throw new Error(data.error || 'Тодорхойгүй алдаа');
  return data;
}

// Takes a state reply from the server, appends its new events and applies them.
function absorbLive(st) {
  const L = app.live;
  if (!L || st.id !== L.id) return;
  for (const e of st.events) app.trace.events.push(e);
  L.next = st.next;
  L.finished = st.finished;
  L.consumed = st.consumed;
  L.waiting = st.waitingForPermit;
  L.error = st.error;
  if (st.result) { L.result = st.result; L.sorted = st.sorted; }
  L.javaStates = {};
  for (const th of st.threads) L.javaStates[th.name] = { state: th.javaState, tid: th.tid };
  seek(app.trace.events.length);
  if (L.finished) {
    app.playing = false;
    setStatus(L.error ? 'Session зогслоо: ' + L.error : 'Бүх thread TERMINATED. Үр дүн ба шалгалтыг доорх “Үр дүн” хэсгээс харна уу.', L.error ? '' : 'ok');
    render();
  }
}

async function liveStep(count) {
  const L = app.live;
  if (!L || L.finished || L.busy) return;
  L.busy = true;
  try {
    const p = new URLSearchParams({ id: L.id, count: String(count), from: String(L.next) });
    absorbLive(await postForm('/api/live/step', p));
  } catch (err) {
    app.playing = false;
    showError(err.message);
  } finally {
    L.busy = false;
    render();
  }
}

async function livePlay() {
  const L = app.live;
  if (!L || L.finished) return;
  app.playing = true;
  render();
  while (app.playing && app.mode === 'live' && app.live === L && !L.finished) {
    // about 10 requests per second; each grants speed/10 operations
    await liveStep(Math.max(1, Math.round(speed() / 10)));
    await new Promise((r) => setTimeout(r, 100));
  }
}

async function liveStop() {
  const L = app.live;
  app.live = null;
  if (!L || L.finished) return;
  try { await postForm('/api/live/stop', new URLSearchParams({ id: L.id })); } catch (e) { /* session already gone */ }
}

async function startLive() {
  await liveStop();
  const data = await postForm('/api/live/start', readForm());
  app.response = data;
  app.trace = { input: data.input, n: data.n, mid: data.mid, events: [], threads: [
    { name: 'Sorter-L', algorithm: data.algorithms.left, start: 0, end: data.mid },
    { name: 'Sorter-R', algorithm: data.algorithms.right, start: data.mid, end: data.n },
    { name: 'Merger', mid: data.mid, end: data.n }
  ] };
  app.view = freshView(app.trace);
  app.pos = 0;
  app.live = { id: data.id, next: 0, finished: false, consumed: 0, waiting: 0, javaStates: {}, busy: false };
  const st = await (await fetch('/api/live/state?id=' + encodeURIComponent(data.id) + '&from=0')).json();
  if (!st.ok) throw new Error(st.error);
  absorbLive(st);
  $('btnReset').disabled = false;
  setStatus('Lockstep: thread-үүд эхэлж, анхны үйлдлийнхээ өмнө зогсож байна. “Алхам ▸” дарах бүрд нэг thread нэг үйлдэл хийнэ.', 'ok');
}

// ----------------------------------------------------------------- server calls
async function loadMeta() {
  const res = await fetch('/api/meta');
  app.meta = await res.json();
  for (const id of ['algoLeft', 'algoRight']) {
    const sel = $(id);
    sel.innerHTML = app.meta.algorithms.map((a) =>
      '<option value="' + a.key + '">' + a.key + ', ' + a.complexity + '</option>').join('');
  }
  $('algoLeft').value = 'insertion';
  $('algoRight').value = 'merge';
  applyModeLimits();
}

function applyModeLimits() {
  if (!app.meta) return;
  const live = currentMode() === 'live';
  const max = live ? app.meta.limits.liveMaxN : app.meta.limits.replayMaxN;
  $('n').max = String(max);
  if (Number($('n').value) > max) $('n').value = String(max);
  $('modeHint').textContent = live
    ? 'Lockstep: Java thread-үүд дуусалгүй, үйлдэл бүрийн өмнө зогсож хүлээнэ. “Алхам ▸” бүрд нэг thread нэг үйлдэл хийнэ. Хойш алхахгүй (бодит thread-ийг ухрааж болохгүй). n ≤ ' + max + '.'
    : 'Replay: Java дээр 3 бодит thread эхлээд бүрэн ажиллаж дуусна. Дараа нь тэдний хийсэн алхмуудыг бичлэг шиг урагш, хойш тоглуулж харна. n ≤ ' + max + '.';
}

// Clears the picture of an earlier run (used when the mode is switched).
function resetStage(message) {
  app.playing = false;
  app.trace = null;
  app.view = null;
  app.response = null;
  app.pos = 0;
  for (const id of ['cvList', 'cvResult', 'cvTimeline']) setupCanvas($(id));
  $('threads').innerHTML = '';
  $('log').innerHTML = '';
  $('evMeta').textContent = '';
  $('listMeta').textContent = 'sorting thread-үүд өөрийн хэсгийг энд эрэмбэлнэ';
  $('resultMeta').textContent = 'merging thread энд нэгтгэнэ';
  $('summary').innerHTML = '<dt>Төлөв</dt><dd>Ажиллуулаагүй</dd>';
  ['btnBack', 'btnPlay', 'btnStep', 'btnReset', 'scrub'].forEach((id) => { $(id).disabled = true; });
  $('btnPlay').textContent = 'Тоглуулах';
  $('scrub').max = '0';
  $('scrub').value = '0';
  $('posOut').textContent = '0 / 0';
  showError(null);
  setStatus(message, '');
}

async function onModeChange() {
  applyModeLimits();
  if (!app.trace) return;
  // a lockstep session keeps real threads waiting on the server: stop it
  app.playing = false;
  await liveStop();
  app.mode = currentMode();
  resetStage('Горим солигдлоо. “Ажиллуулах” дарна уу.');
}

function currentMode() {
  return document.querySelector('input[name=mode]:checked').value;
}

function readForm() {
  const p = new URLSearchParams();
  const source = document.querySelector('input[name=source]:checked').value;
  p.set('source', source);
  p.set('algoLeft', $('algoLeft').value);
  p.set('algoRight', $('algoRight').value);
  if (source === 'random') {
    for (const id of ['n', 'seed', 'min', 'max', 'pattern']) p.set(id, $(id).value);
  } else {
    p.set('input', $('input').value);
  }
  return p;
}

async function runOnServer() {
  pause();
  showError(null);
  setStatus('Java thread-үүд ажиллаж байна…', 'busy');
  $('btnRun').disabled = true;
  app.mode = currentMode();
  if (app.mode === 'live') {
    try {
      await startLive();
    } catch (err) {
      showError(err.message.includes('Failed to fetch')
        ? 'Сервертэй холбогдож чадсангүй. Terminal дээр ./run.sh --web ажиллаж байгаа эсэхийг шалгана уу.'
        : err.message);
      setStatus('Алдаа гарлаа. Өгөгдлөө засаад дахин оролдоно уу.', '');
    } finally {
      $('btnRun').disabled = false;
      render();
    }
    return;
  }
  await liveStop();
  try {
    const res = await fetch('/api/run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
      body: readForm().toString()
    });
    const data = await res.json();
    if (!data.ok) throw new Error(data.error || 'Тодорхойгүй алдаа');
    app.response = data;
    app.trace = data.trace;
    app.view = freshView(app.trace);
    app.pos = 0;
    ['btnReset', 'scrub'].forEach((id) => { $(id).disabled = false; });
    setStatus('Бэлэн: ' + data.trace.eventCount + ' бодит event бүртгэгдлээ. “Тоглуулах” эсвэл “Алхам ▸” дарна уу.', 'ok');
    render();
  } catch (err) {
    showError(err.message.includes('Failed to fetch')
      ? 'Сервертэй холбогдож чадсангүй. Terminal дээр ./run.sh --web ажиллаж байгаа эсэхийг шалгана уу.'
      : err.message);
    setStatus('Алдаа гарлаа. Өгөгдлөө засаад дахин оролдоно уу.', '');
  } finally {
    $('btnRun').disabled = false;
  }
}

function showError(msg) {
  const box = $('error');
  box.hidden = !msg;
  box.textContent = msg || '';
}

function setStatus(msg, cls) {
  const s = $('status');
  s.textContent = msg;
  s.className = 'status ' + (cls || '');
}

// ----------------------------------------------------------------- wiring
function wire() {
  document.querySelectorAll('input[name=source]').forEach((r) => r.addEventListener('change', () => {
    const random = document.querySelector('input[name=source]:checked').value === 'random';
    $('srcInput').hidden = random;
    $('srcRandom').hidden = !random;
  }));
  $('btnBook').addEventListener('click', () => { $('input').value = BOOK_EXAMPLE; });
  $('btnRun').addEventListener('click', runOnServer);
  $('btnPlay').addEventListener('click', () => (app.playing ? pause() : play()));
  $('btnStep').addEventListener('click', stepForward);
  $('btnBack').addEventListener('click', stepBack);
  $('btnReset').addEventListener('click', () => {
    pause();
    if (app.mode === 'live') { runOnServer(); return; }   // real threads: start a fresh session
    seek(0);
    setStatus('Эхэнд нь буцлаа.', '');
  });
  document.querySelectorAll('input[name=mode]').forEach((r) => r.addEventListener('change', onModeChange));
  window.addEventListener('beforeunload', () => {
    if (app.live && !app.live.finished) navigator.sendBeacon('/api/live/stop', new URLSearchParams({ id: app.live.id }));
  });
  $('scrub').addEventListener('input', (e) => {
    const target = Number(e.target.value);   // read first: pause() re-renders and resets the slider
    pause();
    seek(target);
  });
  $('speed').addEventListener('input', () => { $('speedOut').textContent = String(speed()); });
  $('speedOut').textContent = String(speed());
  window.addEventListener('resize', () => render());
  document.addEventListener('keydown', (e) => {
    const tag = document.activeElement.tagName;
    if (!app.trace || /INPUT|TEXTAREA|SELECT/.test(tag)) return;
    // on a focused button Space already "clicks" it; do not toggle a second time
    if (e.key === ' ' && tag !== 'BUTTON') { e.preventDefault(); app.playing ? pause() : play(); }
    if (e.key === 'ArrowRight') stepForward();
    if (e.key === 'ArrowLeft') stepBack();
  });
}

wire();
loadMeta().catch(() => showError('Сервертэй холбогдож чадсангүй. Terminal дээр ./run.sh --web ажиллаж байгаа эсэхийг шалгана уу.'));
