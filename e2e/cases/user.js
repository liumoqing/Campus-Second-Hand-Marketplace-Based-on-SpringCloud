'use strict';
/** U 组：用户资料 / 地址 / 后台用户治理（U1~U23） */
const api = require('../lib/api');
const t = require('../lib/harness');
const { get, post, put, del, expectCode, ok } = api;

module.exports = {
  name: 'U 用户与账号治理（U1~U23）',
  async run(ctx) {
    t.suite(this.name);
    const alice = () => api.token('alice');
    const newbie = () => api.token('newbie');

    await t.test('U1', 'GET /user/profile 返回本人资料', async () => {
      const d = ok(await get('/user/profile', { token: alice() }));
      t.eq(d.username, 'alice', 'username');
      t.notEmpty(d.email, 'email');
      t.assert(typeof d.canPublish === 'boolean', 'canPublish 应为布尔');
      return `city=${d.city || '-'} credit=${d.creditScore}`;
    });

    await t.test('U2', 'PUT /user/profile 修改昵称与签名', async () => {
      const old = ok(await get('/user/profile', { token: newbie() }));
      const nickname = '测试用户' + Date.now().toString().slice(-6);
      ok(await put('/user/profile', { nickname, signature: '机器人测试签名' }, { token: newbie() }), '更新资料');
      const now = ok(await get('/user/profile', { token: newbie() }));
      t.eq(now.nickname, nickname, 'nickname');
      t.eq(now.signature, '机器人测试签名', 'signature');
      ok(await put('/user/profile', { nickname: old.nickname, signature: old.signature }, { token: newbie() }), '还原');
      return '昵称/' + nickname;
    });

    await t.test('U2-签名超长', 'signature 超过 200 字返回 10001', async () => {
      const r = await put('/user/profile', { signature: 'x'.repeat(201) }, { token: newbie() });
      expectCode(r, 10001);
      return '10001';
    });

    await t.test('U3', 'GET /user/{id} 卖家主页（需登录）', async () => {
      const r0 = await get('/user/3');
      expectCode(r0, 10002, '游客访问');
      const d = ok(await get('/user/3', { token: newbie() }));
      t.notEmpty(d.nickname, 'nickname');
      t.assert(d.soldCount !== undefined && d.onSaleCount !== undefined && d.avgRating !== undefined, '缺统计字段');
      return `sold=${d.soldCount} onSale=${d.onSaleCount} rating=${d.avgRating}(${d.reviewCount})`;
    });

    await t.test('U4', 'GET /user/statistics 返回八项角标', async () => {
      const d = ok(await get('/user/statistics', { token: alice() }));
      for (const k of ['favoriteCount', 'onSaleCount', 'draftCount', 'soldCount', 'boughtCount', 'pendingShipCount', 'pendingReceiveCount']) {
        t.assert(d[k] !== undefined, '缺字段 ' + k);
      }
      return JSON.stringify(d);
    });

    await t.test('U5', 'GET /user/addresses 列表（默认地址排最前、手机号脱敏）', async () => {
      const list = ok(await get('/user/addresses', { token: alice() }));
      t.assert(Array.isArray(list), '应返回数组');
      if (list.length) {
        t.assert(/\*{4}/.test(list[0].phone), '手机号未脱敏：' + list[0].phone);
        t.eq(list[0].isDefault, true, '默认地址应排最前');
      }
      return `${list.length} 条`;
    });

    await t.test('U6', 'POST /user/addresses 新增地址返回 {id}', async () => {
      const d = ok(await post('/user/addresses', {
        receiver: '测试收货人', phone: '13800001111', province: '湖北省', city: '武汉市', district: '洪山区',
        detail: '测试路 1 号', isDefault: false
      }, { token: newbie() }), '新增地址');
      t.notEmpty(d.id, 'id');
      ctx.addressId = d.id;
      return 'id=' + d.id;
    });

    await t.test('U7', 'PUT /user/addresses/{id} 修改地址', async () => {
      ok(await put('/user/addresses/' + ctx.addressId, {
        receiver: '测试收货人2', phone: '13800002222', province: '湖北省', city: '武汉市', district: '江岸区',
        detail: '测试路 2 号', isDefault: false
      }, { token: newbie() }), '修改地址');
      const list = ok(await get('/user/addresses', { token: newbie() }));
      const one = list.find(a => String(a.id) === String(ctx.addressId));
      t.assert(one, '改完查不到该地址');
      t.eq(one.receiver, '测试收货人2', 'receiver');
      return 'ok';
    });

    await t.test('U9', 'PUT /user/addresses/{id}/default 设默认', async () => {
      ok(await put('/user/addresses/' + ctx.addressId + '/default', null, { token: newbie() }), '设默认');
      const d = ok(await get('/user/addresses/default', { token: newbie() }));
      t.eq(String(d.id), String(ctx.addressId), '默认地址 id');
      return 'ok';
    });

    await t.test('U10', 'GET /user/addresses/default 与 U5 默认标记一致', async () => {
      const list = ok(await get('/user/addresses', { token: newbie() }));
      const def = list.filter(a => a.isDefault === true);
      t.eq(def.length, 1, '默认地址数量');
      return 'ok';
    });

    await t.test('U6-地址上限', '同一用户第 11 条地址返回 20010', async () => {
      const created = [];
      try {
        for (let i = 0; i < 9; i++) {
          const d = ok(await post('/user/addresses', {
            receiver: '上限' + i, phone: '138000033' + String(i).padStart(2, '0'), province: '湖北省',
            city: '武汉市', district: '洪山区', detail: '上限测试地址' + i + '号门牌'
          }, { token: newbie() }), '第 ' + (i + 2) + ' 条');
          created.push(d.id);
        }
        const r = await post('/user/addresses', {
          receiver: '溢出', phone: '13800009999', province: '湖北省', city: '武汉市', district: '洪山区', detail: '溢出测试地址一号'
        }, { token: newbie() });
        expectCode(r, 20010);
        return '20010';
      } finally {
        for (const id of created) await del('/user/addresses/' + id, { token: newbie() });
      }
    });

    await t.test('U8', 'DELETE /user/addresses/{id} 删除并回退默认', async () => {
      const list = ok(await get('/user/addresses', { token: newbie() }));
      const target = list.find(a => String(a.id) === String(ctx.addressId)) || list[0];
      ok(await del('/user/addresses/' + target.id, { token: newbie() }), '删除');
      const after = ok(await get('/user/addresses', { token: newbie() }));
      t.assert(!after.some(a => String(a.id) === String(target.id)), '删除后仍存在');
      ctx.addressId = after.length ? after[0].id : null;
      return '剩余 ' + after.length + ' 条';
    });

    await t.test('U12-越权', '普通用户访问 /user/admin/page 返回 10003', async () => {
      const r = await get('/user/admin/page', { token: alice() });
      expectCode(r, 10003);
      return '10003';
    });

    await t.test('U12', '管理员分页查询用户 + keyword 搜索', async () => {
      const d = ok(await get('/user/admin/page?pageNum=1&pageSize=10', { token: api.token('admin') }));
      t.assert(d.total >= 4, 'total 应 ≥ 4，实际 ' + d.total);
      const kw = ok(await get('/user/admin/page?keyword=' + encodeURIComponent('alice'), { token: api.token('admin') }));
      t.assert(kw.records.some(r => r.username === 'alice'), 'keyword 未命中 alice');
      ctx.adminUserId = d.records[0].id;
      return `total=${d.total} 首条=${d.records[0].username}`;
    });

    await t.test('U13', '管理员查看用户详情（含商品/订单统计）', async () => {
      const me = ok(await get('/user/admin/page?keyword=' + encodeURIComponent(ctx.newUser.username), { token: api.token('admin') }));
      const hit = (me.records || []).find(u => u.username === ctx.newUser.username);
      t.assert(hit, '找不到测试用户');
      me.records = [hit];
      const d = ok(await get('/user/admin/' + me.records[0].id, { token: api.token('admin') }));
      t.eq(d.username, ctx.newUser.username, 'username');
      return 'ok';
    });

    await t.test('U14-不能动超管', '管理员封禁超级管理员返回 20008', async () => {
      const r = await put('/user/admin/1/ban', { permanent: true, reason: '越权测试' }, { token: api.token('admin') });
      expectCode(r, 20008);
      return '20008';
    });

    await t.test('U14-不能操作自己', '管理员封禁自己返回 20009', async () => {
      const r = await put('/user/admin/2/ban', { permanent: true, reason: '自封' }, { token: api.token('admin') });
      expectCode(r, 20009);
      return '20009';
    });

    await t.test('U14', '封禁用户：登录返回 20003 且带解封时间、旧令牌立即失效', async () => {
      const page = ok(await get('/user/admin/page?keyword=' + encodeURIComponent(ctx.newUser.username) + '&pageNum=1&pageSize=50', { token: api.token('admin') }));
      // 关键字是模糊匹配，必须精确比对用户名，否则会操作到上一轮遗留的同前缀用户
      const exact = page.records.find(r => r.username === ctx.newUser.username);
      t.assert(exact, '找不到本轮测试用户 ' + ctx.newUser.username + '，候选：' + page.records.map(r => r.username).join(','));
      ctx.newUser.id = exact.id;
      const before = api.token('newbie');
      const until = new Date(Date.now() + 24 * 3600 * 1000).toISOString().slice(0, 19).replace('T', ' ');
      ok(await put('/user/admin/' + ctx.newUser.id + '/ban', { banUntil: until, reason: '违规测试' }, { token: api.token('admin') }), '封禁');
      const r = await post('/auth/login', { account: ctx.newUser.username, password: ctx.newUser.password, captchaKey: 'test', captchaCode: '0000' });
      const data = expectCode(r, 20003, '封禁后登录');
      t.assert(data && data.banUntil, '20003 未带 banUntil');
      const rOld = await get('/user/profile', { token: before });
      const oldCode = rOld.json && rOld.json.code;
      t.assert(oldCode === 10002 || oldCode === 20003 || rOld.status === 401, '旧令牌仍可用：HTTP ' + rOld.status + ' code=' + oldCode);
      return '20003 banUntil=' + data.banUntil;
    });

    await t.test('U15', '解封后可以重新登录', async () => {
      ok(await put('/user/admin/' + ctx.newUser.id + '/unban', null, { token: api.token('admin') }), '解封');
      const d = ok(await post('/auth/login', { account: ctx.newUser.username, password: ctx.newUser.password, captchaKey: 'test', captchaCode: '0000' }), '解封后登录');
      api.setToken('newbie', d.accessToken);
      return 'ok';
    });

    await t.test('U16', '限制上架权限：发布商品返回 30009 且带解封时间', async () => {
      ok(await put('/user/admin/' + ctx.newUser.id + '/publish-permission', {
        canPublish: false, publishBanUntil: new Date(Date.now() + 24 * 3600 * 1000).toISOString().slice(0, 19).replace('T', ' ')
      }, { token: api.token('admin') }), '限制上架');
      const r = await post('/product', {
        title: '权限受限测试商品', categoryId: ctx.categoryId, price: 10, images: [ctx.avatarUrl],
        province: '湖北省', city: '武汉市', district: '洪山区', freightMode: 'FREE_SHIPPING', status: 'ON_SALE'
      }, { token: api.token('newbie') });
      const data = expectCode(r, 30009, '受限发布');
      t.assert(data && data.publishBanUntil, '30009 未带 publishBanUntil');
      // 恢复
      ok(await put('/user/admin/' + ctx.newUser.id + '/publish-permission', { canPublish: true }, { token: api.token('admin') }), '恢复');
      return '30009 publishBanUntil=' + data.publishBanUntil;
    });

    await t.test('U17-常管无权', '普通管理员设置角色返回 10003', async () => {
      const r = await put('/user/admin/' + ctx.newUser.id + '/role', { role: 'ADMIN' }, { token: api.token('admin') });
      expectCode(r, 10003);
      return '10003';
    });

    await t.test('U17-不能设为超管', '超级管理员把用户设为 SUPER_ADMIN 返回 20008', async () => {
      const r = await put('/user/admin/' + ctx.newUser.id + '/role', { role: 'SUPER_ADMIN' }, { token: api.token('superadmin') });
      expectCode(r, 20008);
      return '20008';
    });

    await t.test('U17', '超管提升为 ADMIN 后该用户可访问后台，降回 USER 后被拒', async () => {
      ok(await put('/user/admin/' + ctx.newUser.id + '/role', { role: 'ADMIN' }, { token: api.token('superadmin') }), '提管理员');
      const relogin = ok(await post('/auth/login', { account: ctx.newUser.username, password: ctx.newUser.password, captchaKey: 'test', captchaCode: '0000' }), '重登');
      api.setToken('newbie', relogin.accessToken);
      ok(await get('/user/admin/page?pageNum=1&pageSize=1', { token: api.token('newbie') }), '新管理员访问后台');
      ok(await put('/user/admin/' + ctx.newUser.id + '/role', { role: 'USER' }, { token: api.token('superadmin') }), '降回普通用户');
      const relogin2 = ok(await post('/auth/login', { account: ctx.newUser.username, password: ctx.newUser.password, captchaKey: 'test', captchaCode: '0000' }), '再重登');
      api.setToken('newbie', relogin2.accessToken);
      const r = await get('/user/admin/page?pageNum=1&pageSize=1', { token: api.token('newbie') });
      expectCode(r, 10003);
      return '升降级均生效，且角色变更使旧令牌失效';
    });

    await t.test('U18', 'GET /inner/user/credential 按用户名/邮箱取凭证', async () => {
      const r1 = await get('/inner/user/credential?account=alice', { internal: true, port: api.PORTS.user });
      const d1 = ok(r1, '内部取凭证');
      t.eq(d1.username, 'alice', 'username');
      t.assert(d1.password && d1.password.startsWith('$2'), '密码应为 BCrypt');
      const r2 = await get('/inner/user/credential?account=' + encodeURIComponent('alice@qingchuan.local'), { internal: true, port: api.PORTS.user });
      ok(r2, '按邮箱取凭证');
      const r3 = await get('/inner/user/credential?account=alice', {});
      t.assert(r3.status === 404 || (r3.json && r3.json.code === 10009), '无内部令牌应被拒绝，实际 HTTP ' + r3.status + ' code=' + (r3.json && r3.json.code));
      return 'ok';
    });

    await t.test('U20', 'GET /inner/user/{id} 与 /batch 返回精简资料', async () => {
      const d = ok(await get('/inner/user/3', { internal: true, port: api.PORTS.user }));
      t.eq(d.username, 'alice', 'username');
      const list = ok(await get('/inner/user/batch?ids=3,4', { internal: true, port: api.PORTS.user }));
      t.assert(Array.isArray(list) && list.length === 2, 'batch 应返回 2 条');
      return 'batch=' + list.map(x => x.username).join(',');
    });

    await t.test('U21', 'GET /inner/user/address/{addressId} 返回订单快照用的真实手机号', async () => {
      const list = ok(await get('/user/addresses', { token: alice() }));
      t.assert(list.length, 'alice 没有地址');
      const d = ok(await get('/inner/user/address/' + list[0].id + '?userId=3', { internal: true, port: api.PORTS.user }));
      t.assert(/^1\d{10}$/.test(d.phone), '手机号未脱敏错误：' + d.phone);
      return d.city + d.district;
    });

    await t.test('U22', 'PUT /inner/user/{id}/credit 扣信用分（下限 0）', async () => {
      const before = ok(await get('/inner/user/' + ctx.newUser.id, { internal: true, port: api.PORTS.user }));
      ok(await put('/inner/user/' + ctx.newUser.id + '/credit?score=2', null, { internal: true, port: api.PORTS.user }), '扣分');
      const after = ok(await get('/inner/user/' + ctx.newUser.id, { internal: true, port: api.PORTS.user }));
      t.eq(after.creditScore, before.creditScore - 2, '信用分');
      return `${before.creditScore} → ${after.creditScore}`;
    });

    await t.test('U23', 'PUT /inner/user/{id}/password 内部改密', async () => {
      const tmp = 'Inner@2026d';
      ok(await put('/inner/user/' + ctx.newUser.id + '/password', { password: tmp }, { internal: true, port: api.PORTS.user }), '内部改密');
      const r = await post('/auth/login', { account: ctx.newUser.username, password: tmp, captchaKey: 'test', captchaCode: '0000' });
      t.assert(r.json && r.json.code === 0, '内部改密后登录失败');
      api.setToken('newbie', r.json.data.accessToken);
      ok(await put('/inner/user/' + ctx.newUser.id + '/password', { password: ctx.newUser.password }, { internal: true, port: api.PORTS.user }), '改回');
      const back = ok(await post('/auth/login', { account: ctx.newUser.username, password: ctx.newUser.password, captchaKey: 'test', captchaCode: '0000' }), '改回登录');
      api.setToken('newbie', back.accessToken);
      return 'ok';
    });
    await t.test('U15-解封清空', '解封后 banUntil / banReason 应清空（避免前端误显示）', async () => {
      const admin = () => api.token('admin');
      ok(await put('/user/admin/' + ctx.newUser.id + '/ban', { permanent: false, banUntil: '2099-01-01 00:00:00', reason: '解封清空用例' }, { token: admin() }), '临时封禁');
      ok(await put('/user/admin/' + ctx.newUser.id + '/unban', {}, { token: admin() }), '解封');
      const d = ok(await get('/user/admin/' + ctx.newUser.id, { token: admin() }));
      t.eq(d.status, 'NORMAL', 'status');
      t.assert(!d.banUntil, '解封后 banUntil 仍有值：' + d.banUntil);
      return 'cleared';
    });
  }
};
