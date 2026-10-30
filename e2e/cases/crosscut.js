'use strict';
/** 跨切面：网关鉴权、内部接口隔离、序列化、错误封装、游客、限流 */
const api = require('../lib/api');
const t = require('../lib/harness');
const { get, post, request, expectCode, ok } = api;
const NACOS_CONFIG_URL = 'http://127.0.0.1:8848/nacos/v1/cs/configs';

async function readNacosConfig(dataId) {
  const url = NACOS_CONFIG_URL + '?dataId=' + encodeURIComponent(dataId) + '&group=DEFAULT_GROUP&tenant=';
  const res = await fetch(url);
  if (!res.ok) throw new Error('读取 Nacos 配置失败: HTTP ' + res.status + ' ' + await res.text());
  return res.text();
}

async function publishNacosConfig(dataId, content) {
  const body = new URLSearchParams({ dataId, group: 'DEFAULT_GROUP', type: 'yaml', content });
  const res = await fetch(NACOS_CONFIG_URL, { method: 'POST', body });
  const text = await res.text();
  if (!res.ok || text.trim() !== 'true') throw new Error('发布 Nacos 配置失败: HTTP ' + res.status + ' ' + text);
}

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

    await t.test('X-Observability', 'Prometheus 指标与 Zipkin 跨服务调用链可用', async () => {
      const metricsRes = await fetch('http://127.0.0.1:4399/actuator/prometheus');
      const metrics = await metricsRes.text();
      t.eq(metricsRes.status, 200, 'Prometheus 端点状态');
      t.assert(metrics.includes('jvm_memory_used_bytes'), '缺少 JVM 指标');
      t.assert(metrics.includes('http_server_requests_seconds_bucket'), '缺少 HTTP 直方图指标');

      const health = await fetch('http://127.0.0.1:9411/health').then(r => r.json());
      t.eq(health.status, 'UP', 'Zipkin 健康状态');

      const grafana = await fetch('http://127.0.0.1:3000/api/health').then(r => r.json());
      t.eq(grafana.database, 'ok', 'Grafana 数据库状态');
      t.assert(grafana.version, '缺少 Grafana 版本');

      await fetch('http://127.0.0.1:4399/config/runtime');
      await new Promise(resolve => setTimeout(resolve, 2200));
      const traces = await fetch('http://127.0.0.1:9411/api/v2/traces?serviceName=mall-gateway&limit=20&lookback=3600000').then(r => r.json());
      const crossTrace = traces.find(trace => {
        const services = new Set(trace.map(span => span.localEndpoint && span.localEndpoint.serviceName));
        return services.has('mall-gateway') && services.has('mall-user');
      });
      t.assert(crossTrace, '未找到网关到用户服务的调用链');
      return `metrics=ok, trace=${crossTrace[0].traceId}, spans=${crossTrace.length}`;
    });

    await t.test('X-Nacos Config', '公共配置集中加载且 @RefreshScope 动态刷新', async () => {
      const initial = ok(await get('/config/runtime'), '读取运行配置');
      t.eq(initial.centralMarker, 'mall-user', '服务专属配置');
      const original = await readNacosConfig('mall-common.yaml');
      const testVersion = 10000 + (Date.now() % 50000);
      const changed = original
        .replace(/refresh-version:\s*\d+/m, 'refresh-version: ' + testVersion)
        .replace(/announcement:\s*.*/m, 'announcement: Nacos动态刷新测试');
      try {
        await publishNacosConfig('mall-common.yaml', changed);
        const deadline = Date.now() + 15000;
        let current = initial;
        while (Date.now() < deadline && Number(current.refreshVersion) !== testVersion) {
          await new Promise(resolve => setTimeout(resolve, 700));
          current = ok(await get('/config/runtime'), '轮询动态配置');
        }
        t.eq(Number(current.refreshVersion), testVersion, '动态刷新版本');
        t.eq(current.announcement, 'Nacos动态刷新测试', '动态刷新公告');
        return `version=${testVersion}, marker=mall-user`;
      } finally {
        await publishNacosConfig('mall-common.yaml', original);
      }
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

    await t.test('X-OAuth2', '标准 OAuth2 discovery/JWKS/token 与兼容登录 JWT 可用', async () => {
      const discovery = await fetch('http://127.0.0.1:4400/.well-known/oauth-authorization-server').then(r => r.json());
      t.eq(discovery.issuer, 'http://127.0.0.1:4400', 'issuer');
      t.assert(discovery.token_endpoint.endsWith('/oauth2/token'), '缺少标准 token endpoint');
      t.assert(discovery.jwks_uri.endsWith('/oauth2/jwks'), '缺少 JWKS endpoint');

      const jwks = await fetch('http://127.0.0.1:4400/oauth2/jwks').then(r => r.json());
      t.assert(Array.isArray(jwks.keys) && jwks.keys.length > 0, 'JWKS 为空');

      const loginToken = api.token('alice');
      const header = JSON.parse(Buffer.from(loginToken.split('.')[0], 'base64url').toString('utf8'));
      const payload = JSON.parse(Buffer.from(loginToken.split('.')[1], 'base64url').toString('utf8'));
      t.eq(header.alg, 'RS256', '兼容登录 JWT 算法');
      t.eq(payload.role, 'USER', '兼容登录 JWT 角色');
      t.assert(payload.uid != null, '兼容登录 JWT 缺少 uid');

      const basic = Buffer.from('mall-client:mall-client-secret').toString('base64');
      const form = new URLSearchParams({ grant_type: 'client_credentials', scope: 'mall.read' });
      const tokenRes = await fetch('http://127.0.0.1:4400/oauth2/token', {
        method: 'POST',
        headers: { Authorization: 'Basic ' + basic, 'Content-Type': 'application/x-www-form-urlencoded' },
        body: form
      });
      const tokenBody = await tokenRes.json();
      t.eq(tokenRes.status, 200, 'client_credentials token HTTP');
      t.assert(tokenBody.access_token, '缺少 access_token');
      t.eq(tokenBody.token_type, 'Bearer', 'token_type');
      return 'issuer=ok, jwks=1, legacy=RS256, client_credentials=ok';
    });
    await t.test('X-Sentinel 网关', 'Sentinel 网关流控返回 HTTP 429 与统一错误体', async () => {
      await new Promise(resolve => setTimeout(resolve, 1200));
      const responses = await Promise.all(Array.from({ length: 50 }, () =>
        fetch('http://127.0.0.1:4399/product/page?pageNum=1&pageSize=1')));
      const blocked = responses.filter(r => r.status === 429);
      t.assert(blocked.length > 0, '突发 50 请求未触发网关限流');
      const body = await blocked[0].json();
      t.eq(body.code, 10007, '限流错误码');
      t.eq(body.data.resource, '/product/page', '限流资源');
      t.eq(body.data.reason, 'FLOW_LIMIT', '限流原因');
      const unrelated = await fetch('http://127.0.0.1:4399/auth/captcha');
      t.eq(unrelated.status, 200, '限流不应误伤认证接口');
      return `allowed=${responses.length - blocked.length}, blocked=${blocked.length}`;
    });
  }
};
