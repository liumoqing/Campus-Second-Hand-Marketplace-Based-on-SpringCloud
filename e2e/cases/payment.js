'use strict';
/** Y 组：支付（模拟）与退款执行（Y1~Y9） */
const api = require('../lib/api');
const t = require('../lib/harness');
const { get, post, expectCode, ok } = api;
const PORT = api.PORTS.payment;

module.exports = {
  name: 'Y 支付与退款执行（Y1~Y9）',
  async run(ctx) {
    t.suite(this.name);
    const alice = () => api.token('alice');
    const bob = () => api.token('bob');
    const admin = () => api.token('admin');

    const mkOrder = async (over = {}) => {
      const d = ok(await post('/product', Object.assign({
        title: '支付测试商品' + Date.now().toString().slice(-6),
        description: '支付用例专用',
        categoryId: ctx.categoryId,
        price: 66.6,
        conditionLevel: 9,
        images: [ctx.avatarUrl],
        province: '湖北省', city: '武汉市', district: '洪山区',
        freightMode: 'FREE_SHIPPING',
        status: 'ON_SALE'
      }, over), { token: alice() }), '建商品');
      const pid = String(d.productId || d.id);
      const addr = await api.ensureAddress('bob');
      const o = ok(await post('/order/create', { productId: pid, addressId: String(addr.id) }, { token: bob() }), '下单');
      return { pid, orderNo: o.orderNo };
    };

    await t.test('Y1', '模拟支付：SUCCESS + MOCK 流水号 + 订单转待发货', async () => {
      const o = await mkOrder();
      const p = ok(await post('/payment/pay', { orderNo: o.orderNo }, { token: bob() }), '支付');
      t.eq(p.status, 'SUCCESS', 'status');
      t.eq(p.channel, 'MOCK', 'channel');
      t.assert(String(p.tradeNo).startsWith('MOCK-'), 'tradeNo=' + p.tradeNo);
      const od = ok(await get('/order/' + o.orderNo, { token: bob() }));
      t.eq(od.status, 'UNSHIPPED', '订单状态');
      ctx.paidOrder = o.orderNo;
      ctx.paidPayNo = p.payNo;
      return `payNo=${p.payNo}`;
    });

    await t.test('Y1-幂等', '重复支付同一订单返回同一张支付单', async () => {
      const p2 = ok(await post('/payment/pay', { orderNo: ctx.paidOrder }, { token: bob() }), '重复支付');
      t.eq(String(p2.payNo), String(ctx.paidPayNo), 'payNo 应一致');
      t.eq(p2.status, 'SUCCESS', 'status');
      return 'idempotent';
    });

    await t.test('Y1-非待付款', '对非待付款订单支付返回 50002', async () => {
      const r = await post('/payment/pay', { orderNo: ctx.paidOrder }, { token: alice() });
      const code = r.json && r.json.code;
      t.assert([50002, 10003].includes(code), '期望 50002/10003，实际 ' + code);
      return 'code=' + code;
    });

    await t.test('Y2', '支付详情：本人可查、他人 10003', async () => {
      const d = ok(await get('/payment/' + ctx.paidPayNo, { token: bob() }));
      t.eq(d.status, 'SUCCESS', 'status');
      t.assert(d.amount !== undefined, 'amount');
      const deny = await get('/payment/' + ctx.paidPayNo, { token: api.token('newbie') });
      expectCode(deny, 10003, '他人支付单');
      return `amount=${d.amount}`;
    });

    await t.test('Y3', '回调验签失败返回 50001', async () => {
      const r = await post('/payment/callback/mock', { payNo: ctx.paidPayNo, tradeNo: 'X', status: 'SUCCESS', sign: 'bad-sign' });
      expectCode(r, 50001, '错误签名');
      return '50001';
    });

    await t.test('Y5', '支付字典（渠道/状态）', async () => {
      const d = ok(await get('/payment/dict', { token: bob() }));
      t.assert(d && typeof d === 'object', 'dict 为空');
      return Object.keys(d).join(',');
    });

    await t.test('Y6', '后台支付分页：管理员可查、普通用户 10003', async () => {
      const page = ok(await get('/payment/admin/page?pageNum=1&pageSize=10', { token: admin() }));
      t.assert(page.total >= 1, '支付单为空');
      const deny = await get('/payment/admin/page', { token: bob() });
      expectCode(deny, 10003);
      return `total=${page.total}`;
    });

    await t.test('Y7', 'GET /inner/payment/order/{orderNo}', async () => {
      const d = ok(await get('/inner/payment/order/' + ctx.paidOrder, { internal: true, port: PORT }));
      t.eq(d.status, 'SUCCESS', 'status');
      return 'ok';
    });

    await t.test('Y8', 'POST /inner/payment/refund 幂等', async () => {
      const body = { orderNo: ctx.paidOrder, amount: 66.6, reason: '内部退款' };
      const r1 = await post('/inner/payment/refund', body, { internal: true, port: PORT });
      t.assert(r1.json && r1.json.code === 0, '首次退款失败：' + r1.raw);
      const r2 = await post('/inner/payment/refund', body, { internal: true, port: PORT });
      t.assert(r2.json && r2.json.code === 0, '重复退款应幂等：' + r2.raw);
      const d = ok(await get('/inner/payment/order/' + ctx.paidOrder, { internal: true, port: PORT }));
      t.eq(d.status, 'REFUNDED', '支付单状态');
      return 'ok';
    });

    await t.test('内部接口鉴权', '无 internal-token 直连服务被拒 10009', async () => {
      const r = await get('/inner/payment/order/' + ctx.paidOrder, { port: PORT });
      const code = r.json && r.json.code;
      t.assert([10009, 401].includes(code), '期望 10009，实际 ' + code);
      return 'code=' + code;
    });
  }
};
