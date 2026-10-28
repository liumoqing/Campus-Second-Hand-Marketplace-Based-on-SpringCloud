'use strict';
/**
 * 极简 WebSocket 客户端（RFC 6455，仅文本帧），用于验证 /ws/chat。
 * 不引第三方依赖，避免离线环境装包失败。
 */
const net = require('net');
const crypto = require('crypto');

class WsClient {
  constructor(name) {
    this.name = name || 'ws';
    this.socket = null;
    this.buffer = Buffer.alloc(0);
    this.frames = [];
    this.waiters = [];
    this.closed = false;
    this.closeInfo = null;
  }

  connect(port, pathWithQuery, timeout = 5000) {
    return new Promise((resolve, reject) => {
      const key = crypto.randomBytes(16).toString('base64');
      const socket = net.connect({ host: '127.0.0.1', port }, () => {
        socket.write(
          `GET ${pathWithQuery} HTTP/1.1\r\n` +
          `Host: 127.0.0.1:${port}\r\n` +
          `Upgrade: websocket\r\n` +
          `Connection: Upgrade\r\n` +
          `Sec-WebSocket-Key: ${key}\r\n` +
          `Sec-WebSocket-Version: 13\r\n\r\n`
        );
      });
      this.socket = socket;
      const timer = setTimeout(() => reject(new Error('WS 握手超时')), timeout);
      let handshook = false;

      socket.on('data', buf => {
        if (!handshook) {
          this.buffer = Buffer.concat([this.buffer, buf]);
          const idx = this.buffer.indexOf('\r\n\r\n');
          if (idx < 0) return;
          const head = this.buffer.slice(0, idx).toString('latin1');
          if (!/^HTTP\/1\.1 101/.test(head)) {
            clearTimeout(timer);
            const err = new Error('WS 握手被拒：' + head.split('\r\n')[0]);
            err.statusLine = head.split('\r\n')[0];
            return reject(err);
          }
          handshook = true;
          this.buffer = this.buffer.slice(idx + 4);
          clearTimeout(timer);
          resolve(this);
          this._drain();
          return;
        }
        this.buffer = Buffer.concat([this.buffer, buf]);
        this._drain();
      });
      socket.on('close', () => {
        this.closed = true;
        this.waiters.splice(0).forEach(w => w.reject(new Error('WS 已关闭')));
      });
      socket.on('error', e => {
        if (!handshook) { clearTimeout(timer); reject(e); }
      });
    });
  }

  _drain() {
    for (;;) {
      if (this.buffer.length < 2) return;
      const b0 = this.buffer[0];
      const b1 = this.buffer[1];
      const fin = (b0 & 0x80) !== 0;
      const opcode = b0 & 0x0f;
      const masked = (b1 & 0x80) !== 0;
      let len = b1 & 0x7f;
      let offset = 2;
      if (len === 126) {
        if (this.buffer.length < 4) return;
        len = this.buffer.readUInt16BE(2);
        offset = 4;
      } else if (len === 127) {
        if (this.buffer.length < 10) return;
        len = Number(this.buffer.readBigUInt64BE(2));
        offset = 10;
      }
      let maskKey = null;
      if (masked) {
        if (this.buffer.length < offset + 4) return;
        maskKey = this.buffer.slice(offset, offset + 4);
        offset += 4;
      }
      if (this.buffer.length < offset + len) return;
      let payload = this.buffer.slice(offset, offset + len);
      this.buffer = this.buffer.slice(offset + len);

      if (masked && maskKey) {
        const out = Buffer.alloc(payload.length);
        for (let i = 0; i < payload.length; i++) out[i] = payload[i] ^ maskKey[i % 4];
        payload = out;
      }

      if (opcode === 0x8) {
        this.closeInfo = payload.length >= 2 ? payload.readUInt16BE(0) : 1005;
        this.closed = true;
        try { this.socket.end(); } catch (_) { /* ignore */ }
        this.waiters.splice(0).forEach(w => w.reject(new Error('WS 对端关闭 code=' + this.closeInfo)));
        return;
      }
      if (opcode === 0x9) { this._send(0xa, payload); continue; }
      if (opcode === 0x1 || opcode === 0x2) {
        const text = payload.toString('utf8');
        let obj = null;
        try { obj = JSON.parse(text); } catch (_) { obj = { raw: text }; }
        obj.__fin = fin;
        this.frames.push(obj);
        const w = this.waiters.shift();
        if (w) w.resolve(obj);
      }
    }
  }

  _send(opcode, payload) {
    const mask = crypto.randomBytes(4);
    const len = payload.length;
    let header;
    if (len < 126) {
      header = Buffer.alloc(2);
      header[1] = 0x80 | len;
    } else if (len < 65536) {
      header = Buffer.alloc(4);
      header[1] = 0x80 | 126;
      header.writeUInt16BE(len, 2);
    } else {
      header = Buffer.alloc(10);
      header[1] = 0x80 | 127;
      header.writeBigUInt64BE(BigInt(len), 2);
    }
    header[0] = 0x80 | opcode;
    const masked = Buffer.alloc(len);
    for (let i = 0; i < len; i++) masked[i] = payload[i] ^ mask[i % 4];
    this.socket.write(Buffer.concat([header, mask, masked]));
  }

  sendJson(obj) {
    this._send(0x1, Buffer.from(JSON.stringify(obj), 'utf8'));
  }

  /** 取下一帧；超时抛错。skip 可忽略若干帧（例如先到的 ONLINE/OFFLINE 通知） */
  next(timeout = 4000) {
    if (this.frames.length) return Promise.resolve(this.frames.shift());
    return new Promise((resolve, reject) => {
      const w = { resolve, reject };
      this.waiters.push(w);
      const timer = setTimeout(() => {
        const i = this.waiters.indexOf(w);
        if (i >= 0) this.waiters.splice(i, 1);
        reject(new Error('等待 WS 帧超时'));
      }, timeout);
      const wrap = r => { clearTimeout(timer); resolve(r); };
      const wrapErr = e => { clearTimeout(timer); reject(e); };
      w.resolve = wrap;
      w.reject = wrapErr;
    });
  }

  /** 读到满足条件的帧（忽略其它类型），超时抛错 */
  async waitFor(type, timeout = 5000, predicate) {
    const deadline = Date.now() + timeout;
    for (;;) {
      const left = deadline - Date.now();
      if (left <= 0) throw new Error(`等待 WS 帧 type=${type} 超时`);
      const f = await this.next(left);
      if (f.type === type && (!predicate || predicate(f))) return f;
    }
  }

  close() {
    try { this._send(0x8, Buffer.alloc(0)); } catch (_) { /* ignore */ }
    try { this.socket.end(); } catch (_) { /* ignore */ }
  }
}

/** 便捷函数：建连并返回已握手的客户端 */
async function connect(port, pathWithQuery, timeout = 5000) {
  const c = new WsClient('e2e');
  await c.connect(port, pathWithQuery, timeout);
  return c;
}

module.exports = { WsClient, connect };
