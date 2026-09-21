// Capture the web app (feature.web) signed in as the demo reader, driving
// headless Chrome over the DevTools protocol. Run through web-screenshots.sh.
//   node web-screenshots.mjs <out dir> <server origin> <session json path> <user id path>
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
const [OUT, base, sessionPath, userIdPath] = process.argv.slice(2);
const CHROME = process.env.CHROME || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const tokens = fs.readFileSync(sessionPath, 'utf8');
const userId = fs.readFileSync(userIdPath, 'utf8').trim();
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'poster-web-shots-'));
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const port = 9334;
const chrome = spawn(CHROME, ['--headless=new', '--no-first-run', `--user-data-dir=${profile}`, `--remote-debugging-port=${port}`, '--window-size=480,900', 'about:blank'], { stdio: 'ignore' });
let targets;
for (let i = 0; i < 40 && !targets; i++) { await sleep(500); try { targets = await (await fetch(`http://localhost:${port}/json`)).json(); } catch { /* not up yet */ } }
if (!targets) { console.error('chrome did not open the debug port'); chrome.kill(); process.exit(1); }
const ws = new WebSocket(targets.find(t => t.type === 'page').webSocketDebuggerUrl);
await new Promise(r => ws.onopen = r);
let id = 0; const pending = {}; const errors = [];
const send = (method, params = {}) => new Promise(res => { const i = ++id; pending[i] = res; ws.send(JSON.stringify({ id: i, method, params })); });
ws.onmessage = (e) => { const m = JSON.parse(e.data); if (m.id && pending[m.id]) { pending[m.id](m.result); delete pending[m.id]; }
  else if (m.method === 'Runtime.exceptionThrown') errors.push(m.params.exceptionDetails.exception?.description || m.params.exceptionDetails.text); };
await send('Runtime.enable'); await send('Page.enable');
const size = (w, h) => send('Emulation.setDeviceMetricsOverride', { width: w, height: h, deviceScaleFactor: 2, mobile: false });
const theme = (v) => send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-color-scheme', value: v }] });
const click = async (x, y) => { for (const type of ['mousePressed', 'mouseReleased']) await send('Input.dispatchMouseEvent', { type, x, y, button: 'left', clickCount: 1 }); };
const shot = async (name) => { const r = await send('Page.captureScreenshot', { format: 'png' }); fs.writeFileSync(`${OUT}/${name}.png`, Buffer.from(r.data, 'base64')); console.log('  ' + path.join(OUT, name + '.png')); };
// Same origin as the app, so the planted session is the one the app reads.
await send('Page.navigate', { url: base + '/health' }); await sleep(1500);
await send('Runtime.evaluate', { expression: `localStorage.setItem('poster.session', ${JSON.stringify(tokens)}); localStorage.setItem('poster.user_id', ${JSON.stringify(userId)}); 'ok'` });
await size(480, 900); await theme('light');
await send('Page.navigate', { url: base + '/app/' }); await sleep(16000);
await shot('light-01-feed');
await theme('dark'); await sleep(3000); await shot('dark-01-feed'); await theme('light'); await sleep(2000);
// The bottom tabs, left to right, in a 480px window.
await click(180, 868); await sleep(4000); await shot('light-02-my-posts');
await click(300, 868); await sleep(4000); await shot('light-03-liked');
await click(420, 868); await sleep(4000); await shot('light-04-settings');
await click(60, 868); await sleep(3000);
await size(1280, 800); await sleep(4000); await shot('light-05-wide-feed');
await theme('dark'); await sleep(3000); await shot('dark-05-wide-feed'); await theme('light'); await sleep(2000);
await click(300, 300); await sleep(4000); await shot('light-06-wide-details');
// The liked-by roster sits above the comments; wheel down to them.
await send('Input.dispatchMouseEvent', { type: 'mouseWheel', x: 800, y: 400, deltaX: 0, deltaY: 1200 }); await sleep(2000); await shot('light-06b-wide-comments');
await send('Input.dispatchMouseEvent', { type: 'mouseWheel', x: 800, y: 400, deltaX: 0, deltaY: -1200 }); await sleep(1500);
// The rail's toggle sits top-left; expanded it shows the tab names.
await click(40, 36); await sleep(3000); await shot('light-07-wide-rail-expanded');
if (errors.length) { console.error('page exceptions:\n' + errors.join('\n')); }
chrome.kill();
// Chrome may still be writing its profile for a moment; the directory is in the temp folder anyway.
await sleep(1000); try { fs.rmSync(profile, { recursive: true, force: true }); } catch { /* left for the OS */ }
process.exit(errors.length ? 1 : 0);
