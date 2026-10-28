'use strict';
/** 跨切面：网关鉴权、内部接口隔离、序列化、错误封装、游客、限流 */
const api = require('../lib/api');
const t = require('../lib/harness');
const { get, post, request, expectCode, ok } = api;

module.exports = {
  name: 'X 跨切面（网关/鉴权/序列化/错误码）',
  async run(ctx) {
    t.suite(this.name);
    const alice = () => api.token('alice');
    const bob = () => api.token('bob');

    await t.test('X-网关', '/inner/** 经网关一律 404（不对外暴露）', async () => {
      const r = await get('/inner/order/statistics/4', { internal: true });
      t.eq(r.status, 404, 'HTTP 状态');
      return '404';
    });

    await t.test('X-网关', '客户端伪造 X-User-Id 被剥离', async () => {
      const r = await request('GET', '/user/profile', { headers: { 'X-User-Id': '1', 'X-User-Role': 'SUPER_ADMIN' } });
      const code = r.json && r.json.code;
      t.assert([10002, 401].includes(code) || r.status === 401, '伪造头生效了：' + r.raw.slice(0, 120));
      return 'code=' + code;
    });

    await t.test('X-鉴权', '无 token 访问受保护接口 10002', async () => {
      const r = await get('/order/page');
      expectCode(r, 10002);
      return '10002';
    });

    await t.test('X-鉴权', '坏 token 返回 10002', async () => {
      const r = await get('/order/page', { token: 'abc.def.ghi' });
      const code = r.json && r.json.code;
      t.assert([10002, 401].includes(code), '实际 ' + code);
      return 'code=' + code;
    });

    await t.test('X-鉴权', '普通用户访问后台接口 10003', async () => {
      const r = await get('/user/admin/page', { token: alice() });
      expectCode(r, 10003);
      return '10003';
    });

    await t.test('X-游客', '游客可访问商品列表与分类树、不能访问详情', async () => {
      const page = await get('/product/page?pageNum=1&pageSize=100');
      t.assert(page.json && page.json.code === 0, '列表应公开');
      const d = ok(await get('/product/page?pageNum=1&pageSize=100'));
      t.eq(d.records.length <= 20, true, '游客应被强制 20 条，实际 ' + d.records.length);
      t.eq(d.guestLimited, true, 'guestLimited');
      const tree = await get('/category/tree');
      t.assert(tree.json && tree.json.code === 0, '分类树应公开');
      const detail = await get('/product/' + (d.records[0] && d.records[0].id ? d.records[0].id : 1));
      expectCode(detail, 10002, '游客详情');
      return 'ok';
    });

    await t.test('X-序列化', 'Long 主键与 BigDecimal 金额都以字符串返回', async () => {
      const d = ok(await get('/user/profile', { token: alice() }));
      t.eq(typeof d.id, 'string', 'id 应为字符串');
      const page = ok(await get('/product/page?pageNum=1&pageSize=5', { token: alice() }));
      const p = page.records[0];
      t.eq(typeof p.price, 'string', 'price 应为字符串');
      return `id=${d.id} price=${p.price}`;
    });

    await t.test('X-错误封装', '统一响应体 {code,message,data,timestamp}', async () => {
      const r = await get('/order/not-exist-order-no', { token: alice() });
      const j = r.json || {};
      for (const k of ['code', 'message', 'timestamp']) t.assert(j[k] !== undefined, '缺字段 ' + k);
      t.assert(typeof j.code === 'number', 'code 应为数字');
      return 'code=' + j.code;
    });

    await t.test('X-参数校验', '路径参数类型错误返回 10001', async () => {
      const r = await get('/order/refund/not-a-number/handle', { token: alice() });
      const code = r.json && r.json.code;
      t.assert([10001, 40001].includes(code), '实际 ' + code);
      return 'code=' + code;
    });

    await t.test('X-CORS', '预检请求允许跨域', async () => {
      const r = await request('OPTIONS', '/product/page', {
        headers: {
          Origin: 'http://127.0.0.1:5173',
          'Access-Control-Request-Method': 'GET',
          'Access-Control-Request-Headers': 'authorization,content-type'
        }
      });
      const allow = r.headers['access-control-allow-origin'];
      t.assert(r.status === 200 || r.status === 204, '预检状态码 ' + r.status);
      t.assert(!!allow, '缺少 access-control-allow-origin');
      return 'status=' + r.status + ' allow=' + allow;
    });

    await t.test('X-限流', '评论 1 分钟 3 条限制', async () => {
      const pid = ctx.createdProducts[ctx.createdProducts.length - 1];
      let hit = false;
      for (let i = 0; i < 5; i++) {
        const r = await post('/product/' + pid + '/comments', { content: '限流测试 ' + i }, { token: bob() });
        if (r.json && r.json.code !== 0) { hit = true; break; }
      }
      t.assert(hit, '连续 5 条都没有触发限流');
      return 'limited';
    });
  }
};
