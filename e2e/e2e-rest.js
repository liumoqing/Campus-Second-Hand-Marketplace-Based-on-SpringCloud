const http = require('http');

const HOST = '127.0.0.1';
const PORT = 4399; // 直连网关（不再经 5173 代理：代理对 chunked 响应偶发 Parse Error）

function call(method, path, body, token, basePort) {
  const port = basePort || PORT;
  // 用例里的路径都写成 /api/xxx（早期经 Vite 代理时用的前缀）；直连网关时要去掉这层前缀
  path = path.replace(/^\/api(?=\/)/, '');
  return new Promise((resolve) => {
    const data = body === undefined || body === null ? null : Buffer.from(JSON.stringify(body));
    const headers = {};
    if (data) {
      headers['Content-Type'] = 'application/json';
      headers['Content-Length'] = data.length;
    }
    if (token) headers['Authorization'] = 'Bearer ' + token;
    const req = http.request({ host: HOST, port, path, method, headers }, (res) => {
      let raw = '';
      res.setEncoding('utf8');
      res.on('data', (c) => (raw += c));
      res.on('end', () => {
        let json = null;
        try { json = JSON.parse(raw); } catch (e) { /* ignore */ }
        resolve({ status: res.statusCode, code: json ? json.code : -1, data: json ? json.data : null, raw: raw.slice(0, 160) });
      });
    });
    req.on('error', (e) => resolve({ status: 0, code: -1, err: e.message }));
    if (data) req.write(data);
    req.end();
  });
}

const results = [];
function check(name, r, okCode, predicate) {
  const expected = okCode === undefined ? 0 : okCode;
  const pass = r.code === expected && (!predicate || !!predicate(r));
  results.push({ name, pass, code: r.code, extra: pass ? '' : (r.err || r.raw) });
  console.log((pass ? 'PASS' : 'FAIL') + '  ' + name + '  code=' + r.code + (pass ? '' : '  ' + (r.err || r.raw)));
}

async function login(account) {
  const r = await call('POST', '/api/auth/login', {
    account, password: 'Qingchuan@2026', captchaKey: 'dev', captchaCode: '0000'
  });
  if (r.code !== 0) throw new Error('login failed for ' + account + ': ' + r.raw);
  return r.data.accessToken;
}

(async () => {
  const alice = await login('alice');
  const bob = await login('bob');
  const admin = await login('admin');
  const root = await login('superadmin');

  // ---------- 商品：发布 -> 编辑 -> 下架 -> 上架 ----------
  const created = await call('POST', '/api/product', {
    title: 'E2E 测试用二手自行车',
    description: '联调自动生成，结束后可删',
    categoryId: '10',
    price: '320.00',
    originalPrice: '800.00',
    conditionLevel: 8,
    images: ['http://127.0.0.1:4402/static/avatar/alice.png'],
    province: '湖北省', city: '武汉市', district: '洪山区',
    freightMode: 'DISTANCE',
    status: 'ON_SALE'
  }, alice);
  check('P4 发布商品', created);
  const pid = created.data && created.data.productId;

  if (pid) {
    const detail = await call('GET', '/api/product/' + pid, null, alice);
    check('P2 商品详情', detail);

    const upd = await call('PUT', '/api/product/' + pid, {
      title: 'E2E 测试用二手自行车（已改价）',
      description: '改价测试',
      categoryId: '10',
      price: '300.00',
      conditionLevel: 8,
      images: ['http://127.0.0.1:4402/static/avatar/alice.png'],
      province: '湖北省', city: '武汉市', district: '洪山区',
      freightMode: 'DISTANCE',
      status: 'ON_SALE'
    }, alice);
    check('P5 编辑商品', upd);

    check('P15 收藏', await call('POST', '/api/favorite/' + pid, null, bob));
    check('P16 取消收藏', await call('DELETE', '/api/favorite/' + pid, null, bob));
    check('P6 下架', await call('PUT', '/api/product/' + pid + '/off-shelf', null, alice));
    check('P7 重新上架', await call('PUT', '/api/product/' + pid + '/on-shelf', null, alice));

    const cm = await call('POST', '/api/product/' + pid + '/comments', { content: '这个还能砍价吗？' }, bob);
    check('P12 发评论', cm);
    if (cm.data && cm.data.commentId) {
      const rep = await call('POST', '/api/product/' + pid + '/comments', { content: '可以小刀。', parentId: cm.data.commentId }, alice);
      check('P12 回复评论', rep);
    }
    check('P13 评论列表', await call('GET', '/api/product/' + pid + '/comments?pageNum=1&pageSize=10', null, bob));
  }

  // ---------- 草稿：存草稿 -> 草稿箱 -> 发布 ----------
  const draft = await call('POST', '/api/product', { title: 'E2E 草稿商品', status: 'DRAFT' }, alice);
  check('P4 存草稿（只校验标题）', draft);
  const did = draft.data && draft.data.productId;
  if (did) {
    check('P10 草稿箱', await call('GET', '/api/product/my/page?status=DRAFT&pageSize=10', null, alice));
    const pub = await call('PUT', '/api/product/' + did + '/publish', {
      title: 'E2E 草稿转正式',
      description: '草稿发布',
      categoryId: '10',
      price: '88.00',
      conditionLevel: 9,
      images: ['http://127.0.0.1:4402/static/avatar/alice.png'],
      province: '湖北省', city: '武汉市', district: '洪山区',
      freightMode: 'FREE_SHIPPING'
    }, alice);
    check('P9 草稿发布', pub);
  }

  // ---------- 地址：新增 -> 设默认 -> 编辑 -> 删除 ----------
  // ---------- 地址：先清空 bob 的旧地址（多次跑会累积到 10 条上限） ----------
  const preList = await call('GET', '/api/user/addresses', null, bob);
  if (preList && Array.isArray(preList.data) && preList.data.length >= 9) {
    for (const a of preList.data) await call('DELETE', '/api/user/addresses/' + a.id, null, bob);
  }

  const addr = await call('POST', '/api/user/addresses', {
    receiver: '鲍勃', phone: '13900001111', province: '湖北省', city: '武汉市', district: '武昌区',
    detail: '中南路 8 号 302', isDefault: false
  }, bob);
  check('U6 新增地址', addr);
  const aid = addr.data && addr.data.id;
  if (aid) {
    check('U9 设默认', await call('PUT', '/api/user/addresses/' + aid + '/default', null, bob));
    check('U7 编辑地址', await call('PUT', '/api/user/addresses/' + aid, {
      receiver: '鲍勃', phone: '13900001111', province: '湖北省', city: '武汉市', district: '武昌区',
      detail: '中南路 8 号 305', isDefault: true
    }, bob));
  }

  // ---------- 订单主流程：下单 -> 支付 -> 发货 -> 收货 -> 双向评价 ----------
  let orderNo = null;
  if (pid && aid) {
    const oc = await call('POST', '/api/order/create', { productId: pid, addressId: aid, remark: 'E2E 下单' }, bob);
    check('O1 下单', oc);
    orderNo = oc.data && oc.data.orderNo;

    if (orderNo) {
      check('O3 订单详情', await call('GET', '/api/order/' + orderNo, null, bob));
      const pay = await call('POST', '/api/payment/pay', { orderNo }, bob);
      check('Y1 模拟支付', pay);
      check('Y2 支付详情', pay.data && pay.data.payNo ? await call('GET', '/api/payment/' + pay.data.payNo, null, bob) : { code: -1, raw: 'no payNo' });
      check('O5 卖家发货', await call('PUT', '/api/order/' + orderNo + '/ship', { logisticsNo: 'SF-E2E-001' }, alice));
      check('O6 买家收货', await call('PUT', '/api/order/' + orderNo + '/receive', null, bob));
      const rv = await call('POST', '/api/order/' + orderNo + '/review', { rating: 5, content: '卖家很好，车很好骑。' }, bob);
      check('O7 买家评价', rv);
      check('O7 卖家回评', await call('POST', '/api/order/' + orderNo + '/review', { rating: 5, content: '爽快买家。' }, alice));
      check('O7 重复评价应拦截', await call('POST', '/api/order/' + orderNo + '/review', { rating: 4 }, bob), 40005);
      if (rv.data) check('O8 回复评价', await call('PUT', '/api/order/review/' + rv.data.reviewId + '/reply', { replyContent: '谢谢！' }, alice));
      check('U3 卖家主页', await call('GET', '/api/user/' + '3', null, bob));
      check('O11 某用户收到的评价', await call('GET', '/api/order/review/user/3?pageSize=5', null, bob));
    }
  }

  // ---------- 退款：下单 -> 支付 -> 发货 -> 申请退款 -> 管理员同意 ----------
  const p2 = await call('POST', '/api/product', {
    title: 'E2E 退款用商品', description: '退款流程测试', categoryId: '10',
    price: '100.00', conditionLevel: 9,
    images: ['http://127.0.0.1:4402/static/avatar/alice.png'],
    province: '湖北省', city: '武汉市', district: '洪山区',
    freightMode: 'FREE_SHIPPING', status: 'ON_SALE'
  }, alice);
  const pid2 = p2.data && p2.data.productId;
  if (pid2 && aid) {
    const oc2 = await call('POST', '/api/order/create', { productId: pid2, addressId: aid }, bob);
    check('O1 下单（退款单）', oc2);
    const no2 = oc2.data && oc2.data.orderNo;
    if (no2) {
      const pay2 = await call('POST', '/api/payment/pay', { orderNo: no2 }, bob);
      check('Y1 支付（退款单）', pay2);
      check('O5 发货（退款单）', await call('PUT', '/api/order/' + no2 + '/ship', { logisticsNo: 'SF-E2E-002' }, alice));
      const ap = await call('POST', '/api/order/' + no2 + '/refund', { amount: '100.00', reason: '不想要了' }, bob);
      check('O12 申请退款', ap);
      check('O12 重复申请应拦截', await call('POST', '/api/order/' + no2 + '/refund', { amount: '100.00', reason: '再申请一次' }, bob), 40007);
      check('O14 退款列表', await call('GET', '/api/order/refund/page?pageSize=5', null, bob));
      if (ap.data && ap.data.refundId) {
        check('O13 管理员同意退款', await call('PUT', '/api/order/refund/' + ap.data.refundId + '/handle', { agree: true, remark: 'E2E 同意' }, admin));
        // 支付单状态走网关的公开路径（/inner/** 不过网关）
        const payDetail = await call('GET', '/api/payment/' + (pay2.data && pay2.data.payNo), null, bob);
        check('Y4 退款后支付单状态', payDetail, 0, (r) => r.data && r.data.status === 'REFUNDED');
      }
    }
  }

  // ---------- 管理端：分类、强制下架、用户治理、角色 ----------
  // 分类名带时间戳，保证脚本可重复执行（唯一键是 parent_id + name + deleted）
  const catTag = 'E2E 分类 ' + Date.now();
  const cat = await call('POST', '/api/category/admin', { name: catTag, sort: 99 }, admin);
  check('P20 新建分类', cat);
  const cid = cat.data && cat.data.categoryId;
  if (cid) {
    check('P21 重命名分类', await call('PUT', '/api/category/admin/' + cid, { name: catTag + ' 改名', sort: 98 }, admin));
    check('P23 删除分类', await call('DELETE', '/api/category/admin/' + cid, null, admin));
  }
  check('P24 后台商品列表', await call('GET', '/api/product/admin/page?pageSize=5', null, admin));
  check('P26 后台商品统计', await call('GET', '/api/product/admin/statistics', null, admin));
  // 强制下架用一件专门的商品，避免复用已被订单买走的商品（SOLD 不能下架）
  const pAdmin = await call('POST', '/api/product', {
    title: 'E2E 强制下架专用商品', description: '管理端下架测试', categoryId: '10',
    price: '66.00', conditionLevel: 9,
    images: ['http://127.0.0.1:4402/static/avatar/alice.png'],
    province: '湖北省', city: '武汉市', district: '洪山区',
    freightMode: 'FREE_SHIPPING', status: 'ON_SALE'
  }, alice);
  const pidAdmin = pAdmin.data && pAdmin.data.productId;
  if (pidAdmin) {
    check('P25 强制下架', await call('PUT', '/api/product/admin/' + pidAdmin + '/off-shelf', { reason: 'E2E 违规测试' }, admin));
    check('P7 卖家重新上架', await call('PUT', '/api/product/' + pidAdmin + '/on-shelf', null, alice));
  }
  check('U12 后台用户列表', await call('GET', '/api/user/admin/page?pageSize=5', null, admin));
  check('U13 后台用户详情', await call('GET', '/api/user/admin/4', null, admin));

  const banUntil = new Date(Date.now() + 86400000).toISOString().slice(0, 19).replace('T', ' ');
  const ban = await call('PUT', '/api/user/admin/4/ban', { banUntil, reason: 'E2E 封禁测试' }, admin);
  check('U14 封禁', ban);
  const bannedLogin = await call('POST', '/api/auth/login', { account: 'bob', password: 'Qingchuan@2026', captchaKey: 'dev', captchaCode: '0000' });
  check('封禁后登录应 20003', bannedLogin, 20003);
  check('U15 解封', await call('PUT', '/api/user/admin/4/unban', null, admin));
  const unbannedLogin = await call('POST', '/api/auth/login', { account: 'bob', password: 'Qingchuan@2026', captchaKey: 'dev', captchaCode: '0000' });
  check('解封后可登录', unbannedLogin);

  const pbUntil = new Date(Date.now() + 86400000).toISOString().slice(0, 19).replace('T', ' ');
  check('U16 限制上架权限', await call('PUT', '/api/user/admin/3/publish-permission', { canPublish: false, publishBanUntil: pbUntil }, admin));
  const blocked = await call('POST', '/api/product', { title: 'E2E 受限发布测试', status: 'ON_SALE' }, alice);
  check('受限发布应 30009', blocked, 30009);
  check('U16 恢复上架权限', await call('PUT', '/api/user/admin/3/publish-permission', { canPublish: true }, admin));

  check('U17 普通管理员设角色应 10003', await call('PUT', '/api/user/admin/4/role', { role: 'ADMIN' }, admin), 10003);
  check('U17 超管设角色', await call('PUT', '/api/user/admin/4/role', { role: 'ADMIN' }, root));
  check('U17 超管改回普通用户', await call('PUT', '/api/user/admin/4/role', { role: 'USER' }, root));
  check('U17 不能动超管应 20008', await call('PUT', '/api/user/admin/1/role', { role: 'USER' }, root), 20008);
  check('O16 后台订单列表', await call('GET', '/api/order/admin/page?pageSize=5', null, admin));
  check('O18 后台订单统计', await call('GET', '/api/order/admin/statistics', null, admin));

  // ---------- 聊天 ----------
  const conv = await call('POST', '/api/message/conversation', { peerId: 3, productId: pid || 1 }, bob);
  check('M12 建会话', conv);
  const convId = conv.data && conv.data.conversationId;
  if (convId) {
    check('M4 发消息', await call('POST', '/api/message/send', {
      conversationId: convId, toId: 3, contentType: 'TEXT', content: 'E2E 你好', clientMsgId: 'e2e-' + Date.now()
    }, bob));
    check('M3 历史消息', await call('GET', '/api/message/history?conversationId=' + convId + '&size=10', null, bob));
    check('M5 标记已读', await call('PUT', '/api/message/read', { conversationId: convId }, bob));
    check('M2 会话列表', await call('GET', '/api/message/conversations?pageSize=10', null, bob));
    check('M6 系统通知', await call('GET', '/api/message/notices?pageSize=5', null, bob));
    check('M10 未读数', await call('GET', '/api/message/notices/unread-count', null, bob));
  }

  // ---------- 找回密码 ----------
  const rc = await call('POST', '/api/auth/reset-code', { email: 'bob@qingchuan.local' });
  check('A9 找回密码验证码', rc);
  if (rc.data && rc.data.devCode) {
    check('A10 重置密码', await call('POST', '/api/auth/reset-password', {
      email: 'bob@qingchuan.local', code: rc.data.devCode, newPassword: 'Qingchuan@2026', confirmPassword: 'Qingchuan@2026'
    }));
  }

  // ---------- 收尾清理：删掉本次创建的地址，避免多跑几次撞「最多 10 条」的上限 ----------
  try {
    const list = await call('GET', '/api/user/addresses', null, bob);
    const rows = (list && Array.isArray(list.data)) ? list.data : []
    for (const a of rows) {
      if (String(a.id) !== String(aid)) await call('DELETE', '/api/user/addresses/' + a.id, null, bob);
    }
    if (aid) await call('DELETE', '/api/user/addresses/' + aid, null, bob);
  } catch (e) {
    console.log('cleanup addresses skipped: ' + e.message);
  }

  const failed = results.filter((r) => !r.pass);
  console.log('\n==== ' + (results.length - failed.length) + '/' + results.length + ' passed ====');
  if (failed.length) {
    console.log('FAILED:');
    failed.forEach((f) => console.log(' - ' + f.name + ' code=' + f.code + ' ' + f.extra));
  }
  process.exit(failed.length ? 1 : 0);
})().catch((e) => {
  console.log('FATAL ' + e.message);
  process.exit(2);
});
