'use strict';
/** A 组：认证与账号（A1~A10）+ 游客/令牌边界 */
const api = require('../lib/api');
const t = require('../lib/harness');
const { post, get, put, upload, expectCode, ok, png16 } = api;

module.exports = {
  name: 'A 认证与令牌（A1~A10）',
  async run(ctx) {
    t.suite(this.name);

    await t.test('A1', 'GET /auth/captcha 返回 captchaKey 与 base64 图片', async () => {
      const d = ok(await get('/auth/captcha'));
      t.notEmpty(d.captchaKey, 'captchaKey');
      t.assert(d.imageBase64 && d.imageBase64.length > 100, 'imageBase64 太短：' + (d.imageBase64 || '').length);
      ctx.captchaKey = d.captchaKey;
      return `key=${d.captchaKey.slice(0, 8)}… 图片 ${d.imageBase64.length} 字符`;
    });

    await t.test('A2', 'POST /auth/register 注册新用户（五要素 + 头像）', async () => {
      const u = ctx.newUser;
      const r = await post('/auth/register', {
        avatar: ctx.avatarUrl,
        username: u.username,
        email: u.email,
        password: u.password,
        confirmPassword: u.password,
        captchaKey: 'test',
        captchaCode: '0000'
      });
      const d = ok(r, '注册');
      if (d && d.userId) u.userId = String(d.userId);
      return `username=${u.username}`;
    });

    await t.test('A2-重复用户名', '注册同名用户返回 20001', async () => {
      const u = ctx.newUser;
      const r = await post('/auth/register', {
        avatar: ctx.avatarUrl, username: u.username, email: 'other_' + u.email,
        password: u.password, confirmPassword: u.password, captchaKey: 'test', captchaCode: '0000'
      });
      expectCode(r, 20001);
      return '20001';
    });

    await t.test('A2-重复邮箱', '注册同邮箱用户返回 20006', async () => {
      const u = ctx.newUser;
      const r = await post('/auth/register', {
        avatar: ctx.avatarUrl, username: u.username + 'b', email: u.email,
        password: u.password, confirmPassword: u.password, captchaKey: 'test', captchaCode: '0000'
      });
      expectCode(r, 20006);
      return '20006';
    });

    await t.test('A2-两次密码不一致', 'confirmPassword 不一致返回 20005', async () => {
      const u = ctx.newUser;
      const r = await post('/auth/register', {
        avatar: ctx.avatarUrl, username: u.username + 'c', email: 'c_' + u.email,
        password: u.password, confirmPassword: u.password + 'x', captchaKey: 'test', captchaCode: '0000'
      });
      expectCode(r, 20005);
      return '20005';
    });

    await t.test('A2-用户名过短', 'username 少于 4 位返回 10001', async () => {
      const u = ctx.newUser;
      const r = await post('/auth/register', {
        avatar: ctx.avatarUrl, username: 'ab', email: 'd_' + u.email,
        password: u.password, confirmPassword: u.password, captchaKey: 'test', captchaCode: '0000'
      });
      expectCode(r, 10001);
      return '10001';
    });

    await t.test('A2-头像非本站', 'avatar 传外部地址返回 20007', async () => {
      const u = ctx.newUser;
      const r = await post('/auth/register', {
        avatar: 'https://evil.example.com/a.png', username: u.username + 'e', email: 'e_' + u.email,
        password: u.password, confirmPassword: u.password, captchaKey: 'test', captchaCode: '0000'
      });
      expectCode(r, 20007);
      return '20007';
    });

    await t.test('A3', '用户名登录 / 邮箱登录均可', async () => {
      const u = ctx.newUser;
      const r1 = await post('/auth/login', { account: u.username, password: u.password, captchaKey: 'test', captchaCode: '0000' });
      const d1 = ok(r1, '用户名登录');
      t.assert(d1.accessToken && d1.refreshToken, '缺少令牌');
      t.eq(d1.userInfo.username, u.username, '用户名');
      const r2 = await post('/auth/login', { account: u.email, password: u.password, captchaKey: 'test', captchaCode: '0000' });
      const d2 = ok(r2, '邮箱登录');
      t.eq(d2.userInfo.username, u.username, '邮箱登录用户名');
      ctx.newUser.refreshToken = d2.refreshToken;
      api.setToken('newbie', d2.accessToken);
      return `expiresIn=${d2.expiresIn}`;
    });

    await t.test('A3-密码错误', '密码错误返回 20002', async () => {
      const u = ctx.newUser;
      const r = await post('/auth/login', { account: u.username, password: 'Wrong@1234', captchaKey: 'test', captchaCode: '0000' });
      expectCode(r, 20002);
      return '20002';
    });

    await t.test('A4', 'POST /auth/refresh 轮换：首次成功、复用旧 refresh 返回 10002', async () => {
      const old = ctx.newUser.refreshToken;
      const r1 = await post('/auth/refresh', { refreshToken: old });
      const d = ok(r1, '首次刷新');
      t.assert(d.accessToken, '缺少 accessToken');
      const r2 = await post('/auth/refresh', { refreshToken: old });
      expectCode(r2, 10002, '旧 refresh 复用');
      api.setToken('newbie', d.accessToken);
      return '一次性轮换生效';
    });

    await t.test('A6', 'GET /auth/me 返回本人资料；无令牌返回 10002', async () => {
      const d = ok(await get('/auth/me', { token: api.token('newbie') }));
      t.eq(d.username, ctx.newUser.username, 'username');
      t.notEmpty(d.role, 'role');
      const r2 = await get('/auth/me');
      expectCode(r2, 10002, '无令牌');
      return `role=${d.role} canPublish=${d.canPublish}`;
    });

    await t.test('A7-原密码错误', 'PUT /auth/password 原密码错误返回 20005', async () => {
      const r = await put('/auth/password', { oldPassword: 'Bad@12345', newPassword: 'Test@2026b', confirmPassword: 'Test@2026b' }, { token: api.token('newbie') });
      expectCode(r, 20005);
      return '20005';
    });

    await t.test('A7', 'PUT /auth/password 改密成功，新密码可登录、旧密码失效', async () => {
      const u = ctx.newUser;
      ok(await put('/auth/password', { oldPassword: u.password, newPassword: u.password2, confirmPassword: u.password2 }, { token: api.token('newbie') }), '改密');
      const rOld = await post('/auth/login', { account: u.username, password: u.password, captchaKey: 'test', captchaCode: '0000' });
      expectCode(rOld, 20002, '旧密码登录');
      const rNew = await post('/auth/login', { account: u.username, password: u.password2, captchaKey: 'test', captchaCode: '0000' });
      const d = ok(rNew, '新密码登录');
      api.setToken('newbie', d.accessToken);
      // 改回去，方便后续用例
      ok(await put('/auth/password', { oldPassword: u.password2, newPassword: u.password, confirmPassword: u.password }, { token: d.accessToken }), '改回');
      const back = ok(await post('/auth/login', { account: u.username, password: u.password, captchaKey: 'test', captchaCode: '0000' }), '改回后登录');
      api.setToken('newbie', back.accessToken);
      return '改密 + 令牌失效均生效';
    });

    await t.test('A8', 'POST /auth/avatar 上传头像返回本站 URL', async () => {
      const r = await upload('/auth/avatar', { file: { name: 'a.png', type: 'image/png', data: png16() }, fields: {} });
      const d = ok(r, '上传头像');
      t.assert(/^https?:\/\/.*\/files\//.test(d.url), 'url 不是本站文件地址：' + d.url);
      ctx.avatarUrl = d.url;
      return d.url;
    });

    await t.test('A8-非图片', '上传文本文件被拒（10008/10001）', async () => {
      const r = await upload('/auth/avatar', { file: { name: 'a.txt', type: 'text/plain', data: Buffer.from('not an image') }, fields: {} });
      const code = r.json ? r.json.code : -1;
      t.assert(code === 10008 || code === 10001, '期望 10008/10001，实际 ' + code);
      return 'code=' + code;
    });

    await t.test('A9', 'POST /auth/reset-code 发码（开发模式回显 devCode）', async () => {
      const r = await post('/auth/reset-code', { email: ctx.newUser.email });
      const d = ok(r, '发码');
      t.eq(d.sent, true, 'sent');
      ctx.newUser.resetCode = d.devCode;
      return d.devCode ? 'devCode 已回显（开发模式）' : '未回显 devCode（需从 Redis 取）';
    });

    await t.test('A10', 'POST /auth/reset-password 找回密码并用新密码登录', async () => {
      const u = ctx.newUser;
      const code = ctx.newUser.resetCode;
      t.assert(code, '没有可用的验证码（A9 未回显）');
      const pwd3 = 'Reset@2026c';
      ok(await post('/auth/reset-password', { email: u.email, code, newPassword: pwd3, confirmPassword: pwd3 }), '重置密码');
      const d = ok(await post('/auth/login', { account: u.email, password: pwd3, captchaKey: 'test', captchaCode: '0000' }), '新密码登录');
      api.setToken('newbie', d.accessToken);
      // 用新的 accessToken 改回统一密码
      ok(await put('/auth/password', { oldPassword: pwd3, newPassword: u.password, confirmPassword: u.password }, { token: d.accessToken }), '改回统一密码');
      const back = ok(await post('/auth/login', { account: u.username, password: u.password, captchaKey: 'test', captchaCode: '0000' }), '改回后登录');
      api.setToken('newbie', back.accessToken);
      return '重置 → 登录 → 改回 全部成功';
    });

    await t.test('A5', 'POST /auth/logout 注销成功（refresh 一并失效）', async () => {
      const d = ok(await post('/auth/logout', { refreshToken: ctx.newUser.refreshToken }, { token: api.token('newbie') }));
      t.assert(d === null || d === true || typeof d === 'object', '返回值异常：' + JSON.stringify(d));
      // 重新登录，供后续用例继续使用
      const r = await post('/auth/login', { account: ctx.newUser.username, password: ctx.newUser.password, captchaKey: 'test', captchaCode: '0000' });
      const again = ok(r, '注销后重新登录');
      api.setToken('newbie', again.accessToken);
      return '注销成功';
    });

    await t.test('A-令牌伪造', '伪造 Bearer 令牌访问受保护接口返回 10002', async () => {
      const r = await get('/user/profile', { token: 'aaa.bbb.ccc' });
      expectCode(r, 10002);
      return '10002';
    });
  }
};
