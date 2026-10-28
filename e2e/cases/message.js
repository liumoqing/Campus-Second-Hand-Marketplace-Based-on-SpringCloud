'use strict';
/** M 组：聊天与通知（M1 WS / M2~M13） */
const api = require('../lib/api');
const ws = require('../lib/ws');
const t = require('../lib/harness');
const { get, post, put, expectCode, ok } = api;
const PORT = api.PORTS.message;

module.exports = {
  name: 'M 聊天与消息（M1~M13）',
  async run(ctx) {
    t.suite(this.name);
    const alice = () => api.token('alice');
    const bob = () => api.token('bob');

    // ---------- M1 WebSocket ----------
    await t.test('M1-握手', 'WS /ws/chat?token= 可连接并回 PONG', async () => {
      const c = await ws.connect(api.PORTS.message, '/ws/chat?token=' + encodeURIComponent(alice()));
      ctx.wsAlice = c;
      c.sendJson({ type: 'PING', clientTime: Date.now() });
      const f = await c.waitFor('PONG', 8000);
      t.eq(f.type, 'PONG', 'type');
      return 'connected';
    });

    await t.test('M1-无令牌', '无 token 握手被拒', async () => {
      let failed = false;
      try {
        const c = await ws.connect(api.PORTS.message, '/ws/chat');
        c.close();
      } catch (e) { failed = true; }
      t.assert(failed, '无 token 居然连上了');
      return 'rejected';
    });

    // ---------- M12 建会话 ----------
    await t.test('M12', '联系卖家建会话（幂等 + 商品卡片开场）', async () => {
      const d = ok(await post('/message/conversation', { peerId: '3', productId: String(ctx.seedProductId || 1), openWithProductCard: true }, { token: bob() }), '建会话');
      ctx.conversationId = String(d.conversationId);
      const again = ok(await post('/message/conversation', { peerId: '3', productId: String(ctx.seedProductId || 1) }, { token: bob() }), '重复建会话');
      t.eq(String(again.conversationId), String(ctx.conversationId), '应返回同一会话');
      return 'cid=' + ctx.conversationId;
    });

    await t.test('M12-自己', '给自己建会话返回 60002', async () => {
      const r = await post('/message/conversation', { peerId: '4', productId: String(ctx.seedProductId || 1) }, { token: bob() });
      expectCode(r, 60002);
      return '60002';
    });

    // ---------- M1 收发 ----------
    let clientMsgId = 'e2e-' + Date.now();
    await t.test('M1-CHAT', 'WS 发文本消息，收到 ACK（含 msgId/createTime）', async () => {
      const c = ctx.wsAlice;
      c.sendJson({ type: 'CHAT', clientMsgId, conversationId: ctx.conversationId, toId: '4', contentType: 'TEXT', content: '你好，这条是自动化测试' });
      const frame = await c.waitFor('ACK', 8000, f => (f.data || f).clientMsgId === clientMsgId);
      const ack = frame.data || frame;
      t.notEmpty(ack.msgId, 'msgId');
      t.notEmpty(ack.createTime, 'createTime');
      ctx.lastMsgId = String(ack.msgId);
      return 'msgId=' + ack.msgId;
    });

    await t.test('M1-CHAT 幂等', '同一 clientMsgId 重发不产生新消息', async () => {
      const c = ctx.wsAlice;
      c.sendJson({ type: 'CHAT', clientMsgId, conversationId: ctx.conversationId, toId: '4', contentType: 'TEXT', content: '你好，这条是自动化测试' });
      const frame2 = await c.waitFor('ACK', 8000, f => (f.data || f).clientMsgId === clientMsgId);
      const ack2 = frame2.data || frame2;
      t.eq(String(ack2.msgId), String(ctx.lastMsgId), 'msgId 应相同');
      return 'idempotent';
    });

    await t.test('M1-CHAT 非法类型', 'contentType=ORDER 被拒（ERROR/60004 或 HTTP 60004）', async () => {
      const r = await post('/message/send', { conversationId: ctx.conversationId, toId: '4', contentType: 'ORDER', content: 'x', clientMsgId: 'bad-' + Date.now() }, { token: alice() });
      const code = r.json && r.json.code;
      t.assert([60004, 10001].includes(code), '期望 60004，实际 ' + code);
      return 'code=' + code;
    });

    await t.test('M4', 'HTTP 兜底发送成功', async () => {
      const d = ok(await post('/message/send', { conversationId: ctx.conversationId, toId: '4', contentType: 'TEXT', content: 'HTTP 兜底消息', clientMsgId: 'http-' + Date.now() }, { token: alice() }), 'HTTP 发送');
      t.notEmpty(d.msgId, 'msgId');
      return 'msgId=' + d.msgId;
    });

    await t.test('M3', '历史消息（倒序 + hasMore）', async () => {
      const d = ok(await get('/message/history?conversationId=' + ctx.conversationId + '&size=20', { token: bob() }));
      t.assert(Array.isArray(d.records) && d.records.length >= 2, '历史不足：' + d.records.length);
      t.assert(d.hasMore !== undefined, 'hasMore');
      const first = d.records[0];
      t.notEmpty(first.contentType, 'contentType');
      return `records=${d.records.length} hasMore=${d.hasMore}`;
    });

    await t.test('M2', '会话列表（含未读与最后一条摘要）', async () => {
      const d = ok(await get('/message/conversations?pageNum=1&pageSize=10', { token: bob() }));
      t.assert(d.total >= 1, '会话为空');
      const one = d.records.find(r => String(r.conversationId || r.id) === ctx.conversationId);
      t.assert(one, '找不到测试会话');
      t.assert(one.unreadCount !== undefined, 'unreadCount 缺失');
      return `total=${d.total} unread=${one.unreadCount} lastMsgType=${one.lastMsgType}`;
    });

    await t.test('M5', '已读（聊天语义）返回 unreadTotal', async () => {
      const d = ok(await put('/message/read', { conversationId: ctx.conversationId, lastMsgId: String(ctx.lastMsgId) }, { token: bob() }), '标记已读');
      t.assert(d.unreadTotal !== undefined, 'unreadTotal');
      return 'unreadTotal=' + d.unreadTotal;
    });

    await t.test('M5-互斥', '两字段同时传返回 10001', async () => {
      const r = await put('/message/read', { conversationId: ctx.conversationId, lastMsgId: '1', noticeIds: ['1'] }, { token: bob() });
      expectCode(r, 10001);
      return '10001';
    });

    // ---------- 订单卡片 ----------
    await t.test('M13', '内部写订单卡片（幂等，不重复）', async () => {
      const body = {
        orderNo: 'E2E' + Date.now(), cardType: 'ORDER_SHIPPED',
        buyerId: '4', sellerId: '3', productId: String(ctx.seedProductId || 1),
        productTitle: 'E2E 卡片商品', statusText: '卖家已发货', actionText: '查看订单'
      };
      const r1 = await post('/inner/message/card', body, { internal: true, port: PORT });
      t.assert(r1.json && r1.json.code === 0, '首次写卡片失败：' + r1.raw);
      const r2 = await post('/inner/message/card', body, { internal: true, port: PORT });
      t.assert(r2.json && r2.json.code === 0, '重复写卡片失败：' + r2.raw);
      return 'ok';
    });

    // ---------- 通知 ----------
    await t.test('M11/M6/M10', '内部发通知 → 通知列表 → 未读数 → 已读清零', async () => {
      const n = { userId: '4', type: 'SYSTEM', title: '测试通知', content: '自动化测试通知正文', bizType: 'NONE' };
      const r = await post('/inner/message/notice', n, { internal: true, port: PORT });
      t.assert(r.json && r.json.code === 0, '发通知失败：' + r.raw);
      const list = ok(await get('/message/notices?pageNum=1&pageSize=10', { token: bob() }));
      t.assert(list.total >= 1, '通知列表为空');
      const unread = ok(await get('/message/notices/unread-count', { token: bob() }));
      t.assert(unread.total !== undefined, '未读数字段');
      const mark = ok(await put('/message/read', { noticeIds: list.records.map(x => String(x.noticeId || x.id)) }, { token: bob() }), '通知已读');
      t.assert(mark.unreadTotal !== undefined, 'unreadTotal');
      return `notices=${list.total} unread=${unread.total}`;
    });

    await t.test('M7', 'GET /inner/message/unread/{userId}', async () => {
      const d = ok(await get('/inner/message/unread/4', { internal: true, port: PORT }));
      for (const k of ['chatUnread', 'noticeUnread']) t.assert(d[k] !== undefined, '缺字段 ' + k);
      return JSON.stringify(d);
    });

    // ---------- 权限 ----------
    await t.test('M2-越权', '未登录访问会话列表返回 10002', async () => {
      const r = await get('/message/conversations');
      expectCode(r, 10002);
      return '10002';
    });

    // 收尾：关闭 WS
    if (ctx.wsAlice) { try { ctx.wsAlice.close(); } catch (e) { } }
  }
};
