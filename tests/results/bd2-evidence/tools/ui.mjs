// UI scenarios in real headless Chrome via the DevTools protocol (no npm packages).
// Usage: node ui.mjs <label> <appUrl> <cdpPort> <screenshotDir> <resultJson>
import { spawn } from 'node:child_process';
import { mkdtempSync, writeFileSync, mkdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const [label, APP, CDP_PORT, SHOTS, OUT] = process.argv.slice(2);
mkdirSync(SHOTS, { recursive: true });
const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const profile = mkdtempSync(join(tmpdir(), 'bd2-chrome-'));
const chrome = spawn(CHROME, ['--headless=new', `--remote-debugging-port=${CDP_PORT}`, `--user-data-dir=${profile}`,
  '--window-size=1400,1000', '--no-first-run', '--no-default-browser-check', '--hide-scrollbars', 'about:blank'],
  { stdio: 'ignore' });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

let target;
for (let i = 0; i < 100 && !target; i++) {
  try { target = await (await fetch(`http://127.0.0.1:${CDP_PORT}/json/new?about:blank`, { method: 'PUT' })).json(); }
  catch { await sleep(100); }
}
const ws = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((r) => ws.addEventListener('open', r, { once: true }));
let id = 0; const pending = new Map(); const listeners = [];
ws.addEventListener('message', (ev) => {
  const m = JSON.parse(ev.data);
  if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); }
  else for (const l of listeners) l(m);
});
const send = (method, params = {}) => new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })); });

// evidence collectors
const consoleErrors = []; const failedRequests = []; const faviconRequests = [];
listeners.push((m) => {
  if (m.method === 'Runtime.exceptionThrown') consoleErrors.push('uncaught: ' + (m.params.exceptionDetails.exception?.description || m.params.exceptionDetails.text).split('\n')[0]);
  if (m.method === 'Runtime.consoleAPICalled' && m.params.type === 'error') consoleErrors.push('console.error: ' + m.params.args.map((a) => a.value ?? a.description ?? '').join(' ').split('\n')[0]);
  if (m.method === 'Log.entryAdded' && m.params.entry.level === 'error') consoleErrors.push('log: ' + m.params.entry.text + ' ' + (m.params.entry.url || ''));
  if (m.method === 'Network.responseReceived') {
    const r = m.params.response;
    if (r.url.includes('favicon')) faviconRequests.push(r.status + ' ' + r.url);
    if (r.status >= 400) failedRequests.push(r.status + ' ' + r.url);
  }
});
await send('Page.enable'); await send('Runtime.enable'); await send('Log.enable'); await send('Network.enable');
await send('Emulation.setDeviceMetricsOverride', { width: 1400, height: 1000, deviceScaleFactor: 1, mobile: false });

async function load() {
  const loaded = new Promise((r) => { const l = (m) => { if (m.method === 'Page.loadEventFired') { listeners.splice(listeners.indexOf(l), 1); r(); } }; listeners.push(l); });
  await send('Page.navigate', { url: APP });
  await loaded; await sleep(400);
}
async function js(body) {
  const r = await send('Runtime.evaluate', { expression: `(async () => { const $ = (i) => document.getElementById(i); const sleep = (ms) => new Promise((r) => setTimeout(r, ms)); const setMode = (v) => { const r = document.querySelector('input[name=mode][value=' + v + ']'); r.checked = true; r.dispatchEvent(new Event('change')); }; ${body} })()`, awaitPromise: true, returnByValue: true });
  if (r.result?.exceptionDetails) return { scriptError: r.result.exceptionDetails.exception?.description };
  return r.result?.result?.value;
}
async function shot(name) {
  const r = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
  const file = join(SHOTS, `${label}-${name}.png`);
  writeFileSync(file, Buffer.from(r.result.data, 'base64'));
  return file;
}
const state = `({status: $('status').textContent, error: $('error').hidden ? '' : $('error').textContent, pos: ($('posLabel') ? $('posLabel').textContent + ' ' : 'Байрлал ') + $('posOut').textContent, cards: [...document.querySelectorAll('#threads .thread')].map((c) => c.querySelector('.name').textContent.split(',')[0] + ': ' + c.querySelector('.state').textContent + ((c.querySelector('.java') || {}).textContent ? ' | ' + c.querySelector('.java').textContent : ''))})`;
const R = { label, app: APP };

// U1 page load
await load();
R.U1_load = { consoleErrors: [...consoleErrors], failedRequests: [...failedRequests], faviconRequests: [...faviconRequests] };
await shot('01-load');

// U2 Replay book example + navigation
R.U2_replay = await js(`$('btnRun').click(); await sleep(700);
  const ready = $('status').textContent;
  for (let i = 0; i < 12; i++) $('btnStep').click(); const s12 = $('posOut').textContent;
  $('btnBack').click(); const back = $('posOut').textContent;
  document.dispatchEvent(new KeyboardEvent('keydown', {key: 'ArrowRight'})); document.dispatchEvent(new KeyboardEvent('keydown', {key: 'ArrowRight'})); const arrows = $('posOut').textContent;
  const sc = $('scrub'); sc.value = '33'; sc.dispatchEvent(new Event('input')); const scrub = $('posOut').textContent;
  $('btnReset').click(); const reset = $('posOut').textContent;
  for (let i = 0; i < 12; i++) $('btnStep').click();
  return {ready, s12, back, arrows, scrub, reset};`);
await shot('02-replay-step12');
R.U2_play = await js(`$('speed').value = '60'; $('speed').dispatchEvent(new Event('input'));
  $('btnPlay').click(); const t0 = performance.now();
  while ($('btnPlay').textContent !== 'Дууссан' && performance.now() - t0 < 8000) await sleep(50);
  const dd = [...document.querySelectorAll('#summary dt')].map((d) => d.textContent + ': ' + d.nextElementSibling.textContent);
  return {pos: $('posOut').textContent, button: $('btnPlay').textContent, ms: Math.round(performance.now() - t0), result: dd.find((x) => x.startsWith('Үр дүн')), check: dd.find((x) => x.startsWith('Шалгалт'))};`);
await shot('03-replay-done');

// U3 Lockstep basics (F-04, F-08)
await load();
R.U3_lockstep = await js(`setMode('live'); await sleep(150); $('btnRun').click(); await sleep(800);
  const start = ${state};
  const steps = []; for (let i = 0; i < 5; i++) { $('btnStep').click(); await sleep(250); steps.push($('posOut').textContent); }
  return {start, steps};`);
await shot('04-lockstep');
R.U3_lockstep_end = await js(`for (let i = 0; i < 80 && $('btnPlay').textContent !== 'Дууссан'; i++) { $('btnStep').click(); await sleep(80); }
  return ${state};`);
await shot('05-lockstep-done');

// U4 F-01 race, 20 independent attempts (fresh page each time)
const f01 = [];
for (let i = 0; i < 20; i++) {
  await load();
  const before = consoleErrors.length;
  const r = await js(`$('btnRun').click(); await sleep(500);
    $('btnRun').click(); setMode('live');            // the reviewer's race: same JS task
    await sleep(500); const afterSwitch = ${state};
    $('btnRun').click(); await sleep(800); const afterRerun = ${state};
    return {afterSwitch, afterRerun};`);
  r.lockstepStarted = r.afterRerun.status.startsWith('Lockstep');
  r.newConsoleErrors = consoleErrors.slice(before);
  f01.push(r);
  if (i === 0) { await shot('06-f01-after-rerun'); }
}
R.U4_f01 = { attempts: f01.length, lockstepStarted: f01.filter((r) => r.lockstepStarted).length,
  attemptsWithTypeError: f01.filter((r) => r.newConsoleErrors.some((e) => /TypeError/.test(e)) || /Cannot read/.test(r.afterSwitch.error + r.afterRerun.error)).length,
  first: f01[0] };

// U5 F-03 in the UI
await load();
R.U5_f03 = await js(`setMode('live'); await sleep(150); $('btnRun').click(); await sleep(800);
  for (let i = 0; i < 4; i++) { await fetch('/api/live/start', {method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body: 'source=input&algoLeft=merge&algoRight=merge&input=3,2,1'}); await sleep(30); }
  $('btnStep').click(); await sleep(600); return ${state};`);
await shot('07-f03-closed-session');

// U6 F-02 server-side error and simulated internal page error
await load();
R.U6_f02_server = await js(`$('input').value = '5,3,abc'; $('btnRun').click(); await sleep(600); return ${state};`);
await shot('08-f02-server-error');
R.U6_f02_internal = await js(`const orig = window.freshView; window.freshView = () => { throw new TypeError('simulated internal error'); };
  $('input').value = '7, 12, 19, 3, 18, 4, 2, 6, 15, 8'; $('btnRun').click(); await sleep(600);
  const s = ${state}; window.freshView = orig;
  $('btnRun').click(); await sleep(700);
  return {duringError: s, afterRetryWithoutReload: $('status').textContent};`);
await shot('09-f02-after-retry');

// U7 F-06 in the UI
await load();
R.U7_f06 = await js(`$('input').value = '1,,2'; $('btnRun').click(); await sleep(700);
  return {status: $('status').textContent, notes: [...document.querySelectorAll('#summary dt')].filter((d) => d.textContent === 'Тэмдэглэл').map((d) => d.nextElementSibling.textContent)};`);
await shot('10-f06-empty-value-note');

R.allConsoleErrors = consoleErrors;
R.allFailedRequests = failedRequests;
writeFileSync(OUT, JSON.stringify(R, null, 2));
ws.close(); chrome.kill();
console.log('done ' + label);
