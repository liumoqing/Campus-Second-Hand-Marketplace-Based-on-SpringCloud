'use strict';

const fs = require('fs');
const path = require('path');
const { spawn, spawnSync } = require('child_process');

const ROOT = path.resolve(__dirname, '..', '..', '..');
const CHROME_CANDIDATES = [
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
];

function findChrome() {
  const hit = CHROME_CANDIDATES.find(p => fs.existsSync(p));
  if (!hit) throw new Error('未找到 Chrome/Edge 可执行文件');
  return hit;
}

function sleep(ms) {
  return new Promise(r => setTimeout(r, ms));
}

class CDP {
  constructor(wsUrl) {
    this.wsUrl = wsUrl;
    this.nextId = 1;
    this.pending = new Map();
    this.listeners = new Map();
    this.responses = [];
    this.consoleErrors = [];
    this.ws = null;
  }

  open() {
    return new Promise((resolve, reject) => {
      const ws = new WebSocket(this.wsUrl);
      this.ws = ws;
      const timer = setTimeout(() => reject(new Error('CDP WebSocket 连接超时')), 10000);
      ws.addEventListener('open', () => { clearTimeout(timer); resolve(); });
      ws.addEventListener('error', e => { clearTimeout(timer); reject(new Error('CDP WebSocket 错误: ' + (e.message || e.type))); });
      ws.addEventListener('message', ev => {
        let msg;
        try { msg = JSON.parse(typeof ev.data === 'string' ? ev.data : String(ev.data)); } catch (_) { return; }
        if (msg.id) {
          const p = this.pending.get(msg.id);
          if (!p) return;
          this.pending.delete(msg.id);
          clearTimeout(p.timer);
          if (msg.error) p.reject(new Error(`${p.method}: ${msg.error.message}`));
          else p.resolve(msg.result);
          return;
        }
        if (msg.method === 'Network.responseReceived') {
          this.responses.push({ requestId: msg.params.requestId, url: msg.params.response.url, status: msg.params.response.status, at: Date.now() });
          if (this.responses.length > 500) this.responses.splice(0, this.responses.length - 500);
        }
        if (msg.method === 'Runtime.exceptionThrown') {
          const d = msg.params.exceptionDetails || {};
          this.consoleErrors.push(d.exception?.description || d.text || '页面脚本异常');
        }
        if (msg.method === 'Log.entryAdded' && msg.params.entry.level === 'error') {
          this.consoleErrors.push(msg.params.entry.text);
        }
        const list = this.listeners.get(msg.method);
        if (list) list.forEach(fn => { try { fn(msg.params); } catch (_) {} });
      });
    });
  }

  send(method, params = {}, timeoutMs = 20000) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return Promise.reject(new Error('CDP 未连接'));
    const id = this.nextId++;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(`${method} 超时`));
      }, timeoutMs);
      this.pending.set(id, { resolve, reject, timer, method });
      this.ws.send(JSON.stringify({ id, method, params }));
    });
  }

  on(method, fn) {
    const list = this.listeners.get(method) || [];
    list.push(fn);
    this.listeners.set(method, list);
  }

  async evaluate(code) {
    const expression = `(async () => { ${code} })()`;
    const r = await this.send('Runtime.evaluate', {
      expression,
      awaitPromise: true,
      returnByValue: true,
      userGesture: true
    });
    if (r.exceptionDetails) {
      throw new Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text || '页面执行异常');
    }
    return r.result?.value;
  }

  async waitFor(expr, timeoutMs = 15000, label = expr) {
    const deadline = Date.now() + timeoutMs;
    let lastErr = null;
    while (Date.now() < deadline) {
      try {
        if (await this.evaluate(`return Boolean(${expr});`)) return true;
      } catch (e) { lastErr = e; }
      await sleep(150);
    }
    let text = '';
    try { text = (await this.bodyText()).slice(0, 500); } catch (_) {}
    throw new Error(`等待超时: ${label}${lastErr ? ` (${lastErr.message})` : ''}${text ? ` | 页面: ${text}` : ''}`);
  }

  async goto(pathname, waitMs = 15000) {
    const url = pathname.startsWith('http') ? pathname : `http://127.0.0.1:5173${pathname}`;
    this.consoleErrors = [];
    this.responses = [];
    await this.send('Page.navigate', { url });
    await this.waitFor(`document.readyState === 'complete'`, waitMs, `页面加载 ${url}`);
    await this.waitFor(`document.querySelector('#app') && document.querySelector('#app').children.length > 0`, waitMs, `Vue 挂载 ${url}`);
    await sleep(250);
  }

  async bodyText() {
    return await this.evaluate(`return document.body ? document.body.innerText : '';`);
  }

  async waitForText(text, timeoutMs = 12000) {
    const wanted = JSON.stringify(text);
    return await this.waitFor(`document.body && document.body.innerText.includes(${wanted})`, timeoutMs, `页面文本: ${text}`);
  }

  async path() {
    return await this.evaluate(`return location.pathname + location.search;`);
  }

  async exists(selector) {
    return await this.evaluate(`return !!document.querySelector(${JSON.stringify(selector)});`);
  }

  async count(selector) {
    return await this.evaluate(`return document.querySelectorAll(${JSON.stringify(selector)}).length;`);
  }

  async clickSelector(selector) {
    return await this.evaluate(`return (() => {
      const el = document.querySelector(${JSON.stringify(selector)});
      if (!el) return false;
      el.scrollIntoView({ block: 'center' });
      el.click();
      return true;
    })()`);
  }

  async clickText(text, selector = 'button,a,.el-button,.el-dropdown-item,.side-item,.side-sub div,.sort-item,.cat,.conv,.o-title,.card-bubble') {
    return await this.evaluate(`return (() => {
      const wanted = ${JSON.stringify(text)};
      const nodes = [...document.querySelectorAll(${JSON.stringify(selector)})]
        .filter(el => el && el.getClientRects().length > 0);
      const norm = s => (s || '').replace(/\\s+/g, ' ').trim();
      let el = nodes.find(x => norm(x.innerText) === wanted) || nodes.find(x => norm(x.innerText).includes(wanted));
      if (!el) return false;
      el.scrollIntoView({ block: 'center' });
      el.click();
      return true;
    })()`);
  }

  async waitForPlaceholder(placeholder, timeoutMs = 12000) {
    const wanted = JSON.stringify(placeholder);
    return await this.waitFor(`([...document.querySelectorAll('input,textarea')].some(x => x.placeholder === ${wanted}))`, timeoutMs, `输入框: ${placeholder}`);
  }

  async fillPlaceholder(placeholder, value) {
    await this.waitForPlaceholder(placeholder);
    const focused = await this.evaluate(`return (() => {
      const el = [...document.querySelectorAll('input,textarea')]
        .find(x => x.placeholder === ${JSON.stringify(placeholder)});
      if (!el) return false;
      el.focus();
      el.select();
      return true;
    })()`);
    if (!focused) return false;
    await this.send('Input.insertText', { text: String(value) });
    await sleep(80);
    return true;
  }

  async setFileInput(selector, filePath) {
    const r = await this.send('Runtime.evaluate', {
      expression: `document.querySelector(${JSON.stringify(selector)})`,
      returnByValue: false
    });
    if (!r.result?.objectId) throw new Error('文件输入框不存在: ' + selector);
    await this.send('DOM.setFileInputFiles', { files: [filePath], objectId: r.result.objectId });
    await sleep(150);
    return true;
  }

  async setLocalStorage(values) {
    return await this.evaluate(`
      const values = ${JSON.stringify(values)};
      for (const [k, v] of Object.entries(values)) {
        if (v == null) localStorage.removeItem(k);
        else localStorage.setItem(k, String(v));
      }
      return true;
    `);
  }

  async getResponseBody(urlPart) {
    const hit = [...this.responses].reverse().find(r => r.url.includes(urlPart) && r.status >= 200 && r.status < 300);
    if (!hit) return null;
    const body = await this.send('Network.getResponseBody', { requestId: hit.requestId });
    const raw = body.base64Encoded ? Buffer.from(body.body, 'base64').toString('utf8') : body.body;
    try { return JSON.parse(raw); } catch (_) { return raw; }
  }
}

async function launchBrowser(options = {}) {
  const port = options.port || 9345;
  const chrome = findChrome();
  const profile = path.join(ROOT, '.tmp', `browser-e2e-${Date.now()}`);
  fs.mkdirSync(profile, { recursive: true });
  const args = [
    '--headless=new',
    '--disable-gpu',
    '--no-sandbox',
    '--disable-dev-shm-usage',
    '--no-first-run',
    '--no-default-browser-check',
    '--remote-allow-origins=*',
    `--remote-debugging-port=${port}`,
    `--user-data-dir=${profile}`,
    '--window-size=1440,900',
    'about:blank'
  ];
  const child = spawn(chrome, args, { windowsHide: true, stdio: ['ignore', 'ignore', 'pipe'] });
  let stderr = '';
  child.stderr.on('data', d => { stderr += d.toString(); });

  const deadline = Date.now() + 15000;
  let version = null;
  while (Date.now() < deadline) {
    try {
      const r = await fetch(`http://127.0.0.1:${port}/json/version`);
      if (r.ok) { version = await r.json(); break; }
    } catch (_) {}
    await sleep(200);
  }
  if (!version) {
    spawnSync('taskkill.exe', ['/PID', String(child.pid), '/T', '/F'], { windowsHide: true });
    throw new Error('Chrome CDP 启动失败: ' + stderr.slice(-800));
  }

  const targets = await fetch(`http://127.0.0.1:${port}/json/list`).then(r => r.json());
  const target = targets.find(t => t.type === 'page');
  if (!target) throw new Error('Chrome 未创建页面 target');

  const cdp = new CDP(target.webSocketDebuggerUrl);
  await cdp.open();
  await cdp.send('Page.enable');
  await cdp.send('Runtime.enable');
  await cdp.send('Network.enable');
  await cdp.send('DOM.enable');
  await cdp.send('Log.enable');
  await cdp.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false });

  async function close() {
    try { await cdp.send('Browser.close', {}, 5000); } catch (_) {}
    await sleep(300);
    try { child.kill(); } catch (_) {}
    spawnSync('taskkill.exe', ['/PID', String(child.pid), '/T', '/F'], { windowsHide: true });
  }

  return { cdp, close, profile, version };
}

module.exports = { CDP, launchBrowser, sleep, ROOT };