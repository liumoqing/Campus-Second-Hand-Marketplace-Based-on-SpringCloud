'use strict';
/** O 组：订单 / 运费 / 状态机 / 双向评价 / 退款售后（O1~O22） */
const api = require('../lib/api');
const t = require('../lib/harness');
const { get, post, put, expectCode, ok } = api;
const PORT = api.PORTS.order;

module.exports = {
  name: 'O 订单与交易（O1~O22）',
  async run(ctx) {
    t.suite(this.name);
    const alice = () => api.token('alice');
    const bob = () => api.token('bob');
    const admin = () => api.token('admin');

    const mkProduct = async (over = {}) => {
      const d = ok(await post('/product', Object.assign({
        title: '交易测试商品' + Date.now().toString().slice(-6),
        description: '订单用例专用',
        categoryId: ctx.categoryId,
        price: 128.0,
        conditionLevel: 9,
        images: [ctx.avatarUrl],
        province: '湖北省', city: '武汉市', district: '洪山区',
        freightMode: 'DISTANCE',
        status: 'ON_SALE'
      }, over), { token: alice() }), '建商品');
      const id = String(d.productId || d.id);
      ctx.createdProducts.push(id);
      return id;
    };
    const mkOrder = async (productId, buyer = 'bob') => {
      const addr = await api.ensureAddress(buyer);
      const d = ok(await post('/order/create', { productId: String(productId), addressId: String(addr.id), remark: '自动化用例' }, { token: api.token(buyer) }), '下单');
      ctx.orders.push(d.orderNo);
      return d;
    };
    ctx.orders = ctx.orders || [];

    // ---------- 下单与运费 ----------
    await t.test('O1-自己的商品', '买自己的商品返回 40003', async () => {
      const pid = await mkProduct();
      const addr = await api.ensureAddress('alice');
      const r = await post('/order/create', { productId: String(pid), addressId: String(addr.id) }, { token: alice() });
      expectCode(r, 40003);
      return '40003';
    });

    await t.test('O1-跨城自提', '自提 + 跨城返回 40004', async () => {
      const pid = await mkProduct({ freightMode: 'SELF_PICKUP', city: '北京市', province: '北京市', district: '海淀区' });
      const addr = await api.ensureAddress('bob');
      const r = await post('/order/create', { productId: String(pid), addressId: String(addr.id) }, { token: bob() });
      expectCode(r, 40004);
      return '40004';
    });

    await t.test('O1-运费规则', '包邮 0 / 同城 5 / 跨城 10', async () => {
      const free = await mkOrder(await mkProduct({ freightMode: 'FREE_SHIPPING', price: 100 }));
      const same = await mkOrder(await mkProduct({ freightMode: 'DISTANCE', price: 100 }));
      const cross = await mkOrder(await mkProduct({ freightMode: 'DISTANCE', price: 100, city: '北京市', province: '北京市', district: '海淀区' }));
      t.eq(Number(free.freight), 0, '包邮');
      t.eq(Number(same.freight), 5, '同城');
      t.eq(Number(cross.freight), 10, '跨城');
      t.eq(Number(same.payAmount), 105, '应付=100+5');
      ctx.orderSame = same.orderNo;
      ctx.orderCross = cross.orderNo;
      return `free=${free.freight} same=${same.freight} cross=${cross.freight}`;
    });

    await t.test('O1-商品锁定', '下单后商品变 LOCKED，第二人下单返回 30002/30003', async () => {
      const pid = await mkProduct();
      await mkOrder(pid);
      const addr = await api.ensureAddress('newbie');
      const r = await post('/order/create', { productId: String(pid), addressId: String(addr.id) }, { token: api.token('newbie') });
      const code = r.json && r.json.code;
      t.assert([30002, 30003].includes(code), '期望 30002/30003，实际 ' + code);
      const d = ok(await get('/product/' + pid, { token: alice() }));
      t.eq(d.status, 'LOCKED', '商品状态');
      return 'code=' + code;
    });

    await t.test('O1-地址不属于自己', '使用他人地址下单返回 10004', async () => {
      const pid = await mkProduct();
      const aliceAddr = await api.ensureAddress('alice');
      const r = await post('/order/create', { productId: String(pid), addressId: String(aliceAddr.id) }, { token: bob() });
      const code = r.json && r.json.code;
      t.assert([10004, 10003].includes(code), '期望 10004/10003，实际 ' + code);
      return 'code=' + code;
    });

    // ---------- 查询 ----------
    await t.test('O2', '我买到的 / 我卖出的 列表互不串味', async () => {
      const bought = ok(await get('/order/page?role=bought&pageNum=1&pageSize=10', { token: bob() }));
      t.assert(bought.total >= 3, '买家订单数应 ≥3，实际 ' + bought.total);
      const sold = ok(await get('/order/page?role=sold&pageNum=1&pageSize=10', { token: alice() }));
      t.assert(sold.total >= 3, '卖家订单数应 ≥3，实际 ' + sold.total);
      t.assert(bought.records.every(r => String(r.buyerId) === '4'), '买到的列表混入他人订单');
      const withBuyer = sold.records.find(r => r.buyerName && r.buyerPhone && r.buyerAddress);
      t.assert(withBuyer, '卖家订单卡缺少买家姓名/电话/地址');
      t.assert(/^1\d{10}$/.test(String(withBuyer.buyerPhone || '')), '买家电话不完整：' + withBuyer.buyerPhone);
      return `bought=${bought.total} sold=${sold.total} buyer=${withBuyer.buyerName}`;
    });

    await t.test('O3', '订单详情：归属校验 + 待付款返回 expireTime', async () => {
      const d = ok(await get('/order/' + ctx.orderSame, { token: bob() }));
      t.eq(d.status, 'UNPAID', 'status');
      t.notEmpty(d.expireTime, 'expireTime');
      t.notEmpty(d.address && d.address.city, 'address');
      const deny = await get('/order/' + ctx.orderSame, { token: api.token('newbie') });
      expectCode(deny, 10003, '他人订单');
      return `expireTime=${d.expireTime}`;
    });

    // ---------- 状态机 ----------
    await t.test('O4', '取消未付款订单：商品回到在售、重复取消 40002', async () => {
      const pid = await mkProduct();
      const o = await mkOrder(pid);
      ok(await put('/order/' + o.orderNo + '/cancel', { reason: '不想要了' }, { token: bob() }), '取消');
      const d = ok(await get('/order/' + o.orderNo, { token: bob() }));
      t.eq(d.status, 'CANCELLED', 'status');
      const p = ok(await get('/product/' + pid, { token: alice() }));
      t.eq(p.status, 'ON_SALE', '商品应回到在售');
      const again = await put('/order/' + o.orderNo + '/cancel', {}, { token: bob() });
      expectCode(again, 40002);
      return 'ok';
    });

    await t.test('O5/O6', '发货与收货：卖家发货 → 买家收货，越权都被拦', async () => {
      const pid = await mkProduct();
      const o = await mkOrder(pid);
      const pay = ok(await post('/payment/pay', { orderNo: o.orderNo }, { token: bob() }), '支付');
      t.eq(pay.status, 'SUCCESS', 'pay status');
      const byBuyer = await put('/order/' + o.orderNo + '/ship', { logisticsNo: 'SF0001' }, { token: bob() });
      const shipCode = byBuyer.json && byBuyer.json.code;
      t.assert([10003, 40002].includes(shipCode), '买家发货应被拒，实际 ' + shipCode);
      ok(await put('/order/' + o.orderNo + '/ship', { logisticsNo: 'SF0001' }, { token: alice() }), '卖家发货');
      const bySeller = await put('/order/' + o.orderNo + '/receive', null, { token: alice() });
      const recvCode = bySeller.json && bySeller.json.code;
      t.assert([10003, 40002].includes(recvCode), '卖家收货应被拒，实际 ' + recvCode);
      ok(await put('/order/' + o.orderNo + '/receive', null, { token: bob() }), '买家收货');
      const d = ok(await get('/order/' + o.orderNo, { token: bob() }));
      t.eq(d.status, 'COMPLETED', 'status');
      t.notEmpty(d.finishTime, 'finishTime');
      ctx.completedOrder = o.orderNo;
      return 'ok';
    });

    // ---------- 双向评价 ----------
    await t.test('O7-未完成不可评', '未完成订单评价返回 40002', async () => {
      const pid = await mkProduct();
      const o = await mkOrder(pid);
      const r = await post('/order/' + o.orderNo + '/review', { rating: 5 }, { token: bob() });
      expectCode(r, 40002);
      return '40002';
    });

    await t.test('O7', '买家评价 → 重复评价 40005 → 卖家评价（方向自动判定）', async () => {
      const first = ok(await post('/order/' + ctx.completedOrder + '/review', { rating: 5, content: '买家好评' }, { token: bob() }), '买家评价');
      t.eq(first.direction, 'BUYER_TO_SELLER', 'direction');
      const dup = await post('/order/' + ctx.completedOrder + '/review', { rating: 4 }, { token: bob() });
      expectCode(dup, 40005);
      const second = ok(await post('/order/' + ctx.completedOrder + '/review', { rating: 4, content: '卖家回评' }, { token: alice() }), '卖家评价');
      t.eq(second.direction, 'SELLER_TO_BUYER', 'direction');
      ctx.reviewId = String(first.reviewId || first.id);
      return '双向评价完成';
    });

    await t.test('O8', '回复评价：被评人可回、其他人 10003', async () => {
      const deny = await put('/order/review/' + ctx.reviewId + '/reply', { replyContent: '越权回复' }, { token: bob() });
      const denyCode = deny.json && deny.json.code;
      t.assert([10003, 40002].includes(denyCode), '非被评人回复应被拒，实际 ' + denyCode);
      ok(await put('/order/review/' + ctx.reviewId + '/reply', { replyContent: '感谢支持' }, { token: alice() }), '卖家回复');
      return 'ok';
    });

    await t.test('O9/O10/O11', '我的评价 / 收到的评价 / 用户评价列表', async () => {
      const mine = ok(await get('/order/review/mine?pageNum=1&pageSize=10', { token: bob() }));
      t.assert(mine.records.length >= 1, '我的评价为空');
      const received = ok(await get('/order/review/received?pageNum=1&pageSize=10', { token: alice() }));
      t.assert(received.records.length >= 1, '收到的评价为空');
      const ofUser = ok(await get('/order/review/user/3?pageNum=1&pageSize=10', { token: bob() }));
      t.assert(Array.isArray(ofUser.records), 'ofUser 结构异常');
      t.assert(ofUser.records.every(r => r.direction === 'BUYER_TO_SELLER'), '卖家主页应只展示买家评价');
      return `mine=${mine.total} received=${received.total} ofUser=${ofUser.total}`;
    });

    // ---------- 退款 / 售后 ----------
    await t.test('O12-未付款不可退', '未付款订单申请退款返回 40002', async () => {
      const pid = await mkProduct();
      const o = await mkOrder(pid);
      const r = await post('/order/' + o.orderNo + '/refund', { amount: 100, reason: '不想要' }, { token: bob() });
      expectCode(r, 40002);
      return '40002';
    });

    await t.test('O12', '已付款未发货可退款，重复申请 40007', async () => {
      const pid = await mkProduct();
      const o = await mkOrder(pid);
      ok(await post('/payment/pay', { orderNo: o.orderNo }, { token: bob() }), '支付');
      const r = ok(await post('/order/' + o.orderNo + '/refund', { amount: 100, reason: '七天无理由' }, { token: bob() }), '申请退款');
      ctx.refundId = String(r.refundId || r.id);
      const dup = await post('/order/' + o.orderNo + '/refund', { amount: 100, reason: '再申请' }, { token: bob() });
      expectCode(dup, 40007);
      ctx.refundOrderNo = o.orderNo;
      const d = ok(await get('/order/' + o.orderNo, { token: bob() }));
      t.eq(d.status, 'REFUNDING', 'status');
      const notices = ok(await get('/message/notices?type=REFUND&pageNum=1&pageSize=50', { token: alice() }), '卖家退款通知');
      const sellerNotice = notices.records.find(n => n.bizId === o.orderNo && n.title === '退款申请待处理');
      t.assert(sellerNotice, '卖家未收到退款申请通知');
      t.eq(sellerNotice.type, 'REFUND', '通知类型');
      const soldRefunds = ok(await get('/order/refund/page?role=sold&pageNum=1&pageSize=50', { token: alice() }), '卖家退款列表');
      const sellerRefund = soldRefunds.records.find(r => String(r.refundId || r.id) === ctx.refundId);
      t.assert(sellerRefund, '卖家的退款/售后列表未出现该申请');
      return `refundId=${ctx.refundId}, notice=${sellerNotice.noticeId}`;
    });

    await t.test('O13-拒绝', '卖家拒绝退款：订单按 before_status 回到 UNSHIPPED；一单一次故再次申请 40007', async () => {
      const forbidden = await put('/order/refund/' + ctx.refundId + '/handle', { agree: false, remark: '越权处理' }, { token: bob() });
      expectCode(forbidden, 10003, '买家不能审批退款');
      ok(await put('/order/refund/' + ctx.refundId + '/handle', { agree: false, remark: '商品无问题' }, { token: alice() }), '拒绝退款');
      const d = ok(await get('/order/' + ctx.refundOrderNo, { token: bob() }));
      t.eq(d.status, 'UNSHIPPED', '拒绝后状态');
      const again = await post('/order/' + ctx.refundOrderNo + '/refund', { amount: 100, reason: '再申请一次' }, { token: bob() });
      expectCode(again, 40007, '一单一次（文档 §O12：uk_order 兜底）');
      const notices = ok(await get('/message/notices?type=REFUND&pageNum=1&pageSize=50', { token: bob() }), '买家退款结果通知');
      const rejected = notices.records.find(n => n.bizId === ctx.refundOrderNo && n.title === '退款申请已拒绝');
      t.assert(rejected, '买家未收到退款拒绝通知');
      return 'ok';
    });

    await t.test('O13-同意', '卖家同意退款：订单 REFUNDED、支付单 REFUNDED、商品回收为 ON_SALE', async () => {
      const pid = await mkProduct();
      const o = await mkOrder(pid);
      ok(await post('/payment/pay', { orderNo: o.orderNo }, { token: bob() }), '支付');
      const r = ok(await post('/order/' + o.orderNo + '/refund', { amount: 100, reason: '同意路径' }, { token: bob() }), '申请退款');
      const rid = String(r.refundId || r.id);
      ok(await put('/order/refund/' + rid + '/handle', { agree: true, remark: '同意' }, { token: alice() }), '同意退款');
      const d = ok(await get('/order/' + o.orderNo, { token: bob() }));
      t.eq(d.status, 'REFUNDED', 'status');
      const pay = ok(await get('/inner/payment/order/' + o.orderNo, { internal: true, port: api.PORTS.payment }));
      t.eq(pay.status, 'REFUNDED', '支付单状态');
      const p = ok(await get('/product/' + pid, { token: alice() }));
      t.eq(p.status, 'ON_SALE', '商品应回收为在售');
      const notices = ok(await get('/message/notices?type=REFUND&pageNum=1&pageSize=50', { token: bob() }), '买家退款结果通知');
      const approved = notices.records.find(n => n.bizId === o.orderNo && n.title === '退款已同意');
      t.assert(approved, '买家未收到退款同意通知');
      return 'ok';
    });

    await t.test('O14', '退款列表（买家视角）', async () => {
      const d = ok(await get('/order/refund/page?pageNum=1&pageSize=10', { token: bob() }));
      t.assert(d.total >= 1, '退款列表为空');
      const one = d.records.find(r => String(r.id) === ctx.refundId2) || d.records[0];
      t.notEmpty(one.status, 'status');
      return `total=${d.total}`;
    });

    await t.test('O15', '订单字典（状态/角色/退款状态）', async () => {
      const d = ok(await get('/order/dict', { token: bob() }));
      t.assert(d && typeof d === 'object', 'dict 为空');
      return Object.keys(d).join(',');
    });

    // ---------- 管理端 ----------
    await t.test('O16', '后台订单分页与统计', async () => {
      const page = ok(await get('/order/admin/page?pageNum=1&pageSize=10', { token: admin() }));
      t.assert(page.total >= 1, '后台订单为空');
      const stat = ok(await get('/order/admin/statistics', { token: admin() }));
      t.assert(stat.total !== undefined, '统计缺 total：' + JSON.stringify(stat));
      return `orders=${page.total} ${JSON.stringify(stat)}`;
    });

    await t.test('O16-越权', '普通用户访问后台订单返回 10003', async () => {
      const r = await get('/order/admin/page', { token: bob() });
      expectCode(r, 10003);
      return '10003';
    });

    // ---------- 内部接口（直连 4403） ----------
    await t.test('O19', 'GET /inner/order/{orderNo}', async () => {
      const d = ok(await get('/inner/order/' + ctx.orderSame, { internal: true, port: PORT }));
      t.eq(d.orderNo, ctx.orderSame, 'orderNo');
      return 'status=' + d.status;
    });

    await t.test('O20', 'POST /inner/order/{orderNo}/notify-paid 幂等', async () => {
      const pid = await mkProduct();
      const o = await mkOrder(pid);
      const r1 = await post('/inner/order/' + o.orderNo + '/notify-paid', { payType: 'MOCK' }, { internal: true, port: PORT });
      t.assert(r1.json && r1.json.code === 0, '首次通知失败：' + r1.raw);
      const r2 = await post('/inner/order/' + o.orderNo + '/notify-paid', { payType: 'MOCK' }, { internal: true, port: PORT });
      t.assert(r2.json && r2.json.code === 0, '重复通知应幂等：' + r2.raw);
      const d = ok(await get('/order/' + o.orderNo, { token: bob() }));
      t.eq(d.status, 'UNSHIPPED', 'status');
      return 'ok';
    });

    await t.test('O22', 'GET /inner/order/statistics/{userId}', async () => {
      const d = ok(await get('/inner/order/statistics/4', { internal: true, port: PORT }));
      for (const k of ['soldCount', 'boughtCount', 'avgRating', 'reviewCount']) t.assert(d[k] !== undefined, '缺字段 ' + k);
      return JSON.stringify(d);
    });
  }
};
