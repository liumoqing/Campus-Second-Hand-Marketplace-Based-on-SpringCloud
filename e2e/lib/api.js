'use strict';
/**
 * 直连网关 4399 的 HTTP 客户端 + 账号令牌管理。
 * 走网关（而不是直连各服务）才能同时验证：路由、鉴权过滤器、头注入、限流、错误码。
 */
const http = require('http');

const GATEWAY_HOST = '127.0.0.1';
const GATEWAY_PORT = Number(process.env.GATEWAY_PORT || 4399);
const INTERNAL_TOKEN = process.env.INTERNAL_TOKEN || 'mall-internal-2026';

const tokens = {};

const REDIS_CLI = process.env.REDIS_CLI || 'D:\\Redis\\Redis-8.8.0-Windows-x64-cygwin-with-Service\\redis-cli.exe';

/** 从 Redis 读验证码答案（服务端把答案存在 mall:auth:captcha:{key}） */
function redisGet(key) {
  try {
    const { execFileSync } = require('child_process');
    return execFileSync(REDIS_CLI, ['GET', key], { timeout: 4000 }).toString().trim();
  } catch (e) { return null; }
}

/**
 * 把占位验证码换成真实的一对。
 * auth 以 `--mall.captcha.enabled=false` 启动时，服务端会忽略验证码，这里不必调用；
 * 以 IDEA（验证码开启）启动时，用例里的 `captchaKey:'test'` 会被自动替换成真实验证码。
 */
async function fillCaptcha(body) {
  if (!body || body.captchaKey !== 'test') return body;
  const cap = await get('/auth/captcha');
  if (!cap.json || cap.json.code !== 0) return body;
  const key = cap.json.data.captchaKey;
  const code = redisGet('mall:auth:captcha:' + key);
  if (!code) return body;
  const next = Object.assign({}, body, { captchaKey: key, captchaCode: code });
  return next;
}


function request(method, path, opts = {}) {
  const port = opts.port || GATEWAY_PORT;
  return new Promise((resolve, reject) => {
    const payload = opts.body === undefined ? null : Buffer.from(JSON.stringify(opts.body), 'utf8');
    const headers = Object.assign({}, opts.headers || {});
    if (payload) {
      headers['Content-Type'] = 'application/json; charset=utf-8';
      headers['Content-Length'] = payload.length;
    }
    if (opts.token) headers['Authorization'] = 'Bearer ' + opts.token;
    if (opts.internal) headers['X-Internal-Token'] = INTERNAL_TOKEN;
    if (opts.userId != null) headers['X-User-Id'] = String(opts.userId);
    if (opts.role) headers['X-User-Role'] = opts.role;

    const req = http.request({ host: GATEWAY_HOST, port, method, path, headers, timeout: opts.timeout || 20000 }, res => {
      const chunks = [];
      res.on('data', c => chunks.push(c));
      res.on('end', () => {
        const raw = Buffer.concat(chunks).toString('utf8');
        let json = null;
        try { json = raw ? JSON.parse(raw) : null; } catch (_) { json = null; }
        resolve({ status: res.statusCode, headers: res.headers, raw, json });
      });
    });
    req.on('timeout', () => req.destroy(new Error(`请求超时 ${method} ${path}`)));
    req.on('error', reject);
    if (payload) req.write(payload);
    req.end();
  });
}

/** 上传文件（multipart/form-data，单文件字段 file + 普通字段） */
function upload(path, opts = {}) {
  return new Promise((resolve, reject) => {
    const boundary = '----malltest' + Date.now().toString(16);
    const parts = [];
    for (const [k, v] of Object.entries(opts.fields || {})) {
      parts.push(Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="${k}"\r\n\r\n${v}\r\n`, 'utf8'));
    }
    if (opts.file) {
      parts.push(Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="${opts.fileName || 'file'}"; filename="${opts.file.name}"\r\nContent-Type: ${opts.file.type}\r\n\r\n`, 'utf8'));
      parts.push(opts.file.data);
      parts.push(Buffer.from('\r\n', 'utf8'));
    }
    parts.push(Buffer.from(`--${boundary}--\r\n`, 'utf8'));
    const payload = Buffer.concat(parts);
    const headers = {
      'Content-Type': 'multipart/form-data; boundary=' + boundary,
      'Content-Length': payload.length
    };
    if (opts.token) headers['Authorization'] = 'Bearer ' + opts.token;
    const req = http.request({ host: GATEWAY_HOST, port: GATEWAY_PORT, method: 'POST', path, headers, timeout: opts.timeout || 30000 }, res => {
      const chunks = [];
      res.on('data', c => chunks.push(c));
      res.on('end', () => {
        const raw = Buffer.concat(chunks).toString('utf8');
        let json = null;
        try { json = raw ? JSON.parse(raw) : null; } catch (_) { json = null; }
        resolve({ status: res.statusCode, raw, json });
      });
    });
    req.on('timeout', () => req.destroy(new Error(`上传超时 POST ${path}`)));
    req.on('error', reject);
    req.write(payload);
    req.end();
  });
}

const get = (path, o = {}) => request('GET', path, o);
// POST/PUT 遇到占位验证码（captchaKey==='test'）时自动换成真实验证码，兼容验证码开启的启动方式
const post = async (path, body, o = {}) => request('POST', path, Object.assign({ body: await fillCaptcha(body) }, o));
const put = async (path, body, o = {}) => request('PUT', path, Object.assign({ body: await fillCaptcha(body) }, o));
const del = (path, o = {}) => request('DELETE', path, o);

/** 登录并缓存 token；captcha 关闭时占位值即可通过 */
async function login(key, account, password) {
  const r = await post('/auth/login', { account, password, captchaKey: 'test', captchaCode: '0000' });
  if (!r.json || r.json.code !== 0) {
    throw new Error(`登录失败 ${account}: HTTP ${r.status} ${r.raw.slice(0, 200)}`);
  }
  tokens[key] = r.json.data.accessToken;
  return r.json.data;
}

function token(key) {
  const t = tokens[key];
  if (!t) throw new Error(`账号 ${key} 未登录`);
  return t;
}

function setToken(key, t) {
  tokens[key] = t;
}

async function refresh(key, refreshToken) {
  const r = await post('/auth/refresh', { refreshToken });
  if (r.json && r.json.code === 0) tokens[key] = r.json.data.accessToken;
  return r;
}

/** 期望业务码；不匹配时把 HTTP 状态与原始响应带进错误信息，便于写报告 */
function expectCode(r, code, label) {
  const actual = r.json ? r.json.code : `HTTP ${r.status}`;
  if (actual !== code) {
    const body = r.raw && r.raw.length > 300 ? r.raw.slice(0, 300) + '…' : r.raw;
    throw new Error(`${label ? label + '：' : ''}期望 code=${code}，实际 code=${actual}；HTTP ${r.status}；body=${body}`);
  }
  return r.json.data;
}

function ok(r, label) {
  return expectCode(r, 0, label);
}

/** 生成一个 4 字节 PNG（ImageIO 可识别，用于上传用例） */
function tinyPng() {
  return Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
    'base64'
  );
}

/** 生成可被 ImageIO 识别、稍大一点的 PNG（16x16 纯色） */
function png16() {
  const size = 16;
  const raw = [];
  for (let y = 0; y < size; y++) {
    raw.push(0); // filter none
    for (let x = 0; x < size; x++) raw.push(255, 107, 53, 255);
  }
  const crcTable = (() => {
    const t = [];
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      t[n] = c >>> 0;
    }
    return t;
  })();
  const crc32 = buf => {
    let c = 0xffffffff;
    for (const b of buf) c = crcTable[(c ^ b) & 0xff] ^ (c >>> 8);
    return (c ^ 0xffffffff) >>> 0;
  };
  const chunk = (type, data) => {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length);
    const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(td));
    return Buffer.concat([len, td, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  const idat = require('zlib').deflateSync(Buffer.from(raw));
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', idat),
    chunk('IEND', Buffer.alloc(0))
  ]);
}


/** 确保账号有默认收货地址（订单/退款用例依赖），返回地址对象 */
async function ensureAddress(key) {
  const tk = token(key);
  try {
    const r = await get('/user/addresses/default', { token: tk });
    if (r.json && r.json.code === 0 && r.json.data && r.json.data.id) return r.json.data;
  } catch (e) { /* 没有就建 */ }
  const body = {
    receiver: key === 'bob' ? '鲍勃' : (key === 'alice' ? '爱丽丝' : '测试收件人'),
    phone: key === 'bob' ? '13900002222' : '13800001111',
    province: '湖北省', city: '武汉市', district: '洪山区',
    detail: '测试路 ' + Math.floor(Math.random() * 900 + 100) + ' 号',
    isDefault: 1
  };
  const r = await post('/user/addresses', body, { token: tk });
  if (!r.json || r.json.code !== 0) throw new Error('创建地址失败：' + r.raw);
  const id = (r.json.data && (r.json.data.id || r.json.data)) || null;
  const again = await get('/user/addresses/default', { token: tk });
  if (again.json && again.json.data) return again.json.data;
  return { id: id, city: body.city };
}

module.exports = {
  GATEWAY_PORT, INTERNAL_TOKEN, ensureAddress,
  request, get, post, put, del, upload,
  login, token, setToken, refresh, tokens,
  expectCode, ok, tinyPng, png16,
  PORTS: { auth: 4400, user: 4401, product: 4402, order: 4403, payment: 4404, message: 4405 }
};
