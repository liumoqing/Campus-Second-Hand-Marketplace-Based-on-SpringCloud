'use strict';
/**
 * 全量功能测试入口：
 *   node e2e/run.js            跑全部套件
 *   node e2e/run.js auth user  只跑指定套件（按文件前缀）
 * 报告输出：文档归类/06-功能测试报告.md（同名 .json 为机器可读明细）
 */
const path = require('path');
const fs = require('fs');
const api = require('./lib/api');
const t = require('./lib/harness');

const SUITES = [
  { key: 'auth', file: './cases/auth' },
  { key: 'user', file: './cases/user' },
  { key: 'product', file: './cases/product' },
  { key: 'order', file: './cases/order' },
  { key: 'payment', file: './cases/payment' },
  { key: 'message', file: './cases/message' },
  { key: 'crosscut', file: './cases/crosscut' }
];

const ROOT = path.resolve(__dirname, '..');
const REPORT = path.join(ROOT, '文档归类', '06-功能测试报告.md');

function ts() {
  const d = new Date();
  const p = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`;
}

async function main() {
  const only = process.argv.slice(2).filter(a => !a.startsWith('-'));
  const picked = only.length ? SUITES.filter(s => only.includes(s.key)) : SUITES;

  console.log('== 功能测试开始 ==');
  console.log('网关：http://127.0.0.1:' + api.GATEWAY_PORT);

  const alice = await api.login('alice', 'alice', 'Qingchuan@2026');
  await api.login('bob', 'bob', 'Qingchuan@2026');
  await api.login('admin', 'admin', 'Qingchuan@2026');
  await api.login('superadmin', 'superadmin', 'Qingchuan@2026');

  // 清理上一轮遗留的评论限流键（1 分钟 3 条），保证脚本可重复执行
  try {
    const { execFileSync } = require('child_process');
    execFileSync('D:\\Redis\\Redis-8.8.0-Windows-x64-cygwin-with-Service\\redis-cli.exe', ['--scan', '--pattern', 'mall:product:comment-rate:*'], { timeout: 5000 })
      .toString().split(/\r?\n/).filter(Boolean)
      .forEach(k => { try { execFileSync('D:\\Redis\\Redis-8.8.0-Windows-x64-cygwin-with-Service\\redis-cli.exe', ['DEL', k], { timeout: 3000 }); } catch (_) {} });
  } catch (_) { /* redis-cli 不存在时忽略 */ }

  const suffix = ts().slice(-8);
  const ctx = {
    suffix,
    newUser: {
      username: 'tester' + suffix,
      email: `tester${suffix}@qingchuan.local`,
      password: 'Test@2026a',
      password2: 'Test@2026b'
    },
    avatarUrl: alice.userInfo.avatar,
    categoryId: null,
    createdProducts: []
  };

  // 确保有一个可复用的普通测试账号（只跑部分套件时也能用）
  const fixed = { username: 'malltester', email: 'malltester@qingchuan.local', password: 'Test@2026a' };
  let fixedLogin = await api.post('/auth/login', { account: fixed.username, password: fixed.password, captchaKey: 'test', captchaCode: '0000' });
  if (!fixedLogin.json || fixedLogin.json.code !== 0) {
    await api.post('/auth/register', {
      avatar: alice.userInfo.avatar, username: fixed.username, email: fixed.email,
      password: fixed.password, confirmPassword: fixed.password, captchaKey: 'test', captchaCode: '0000'
    });
    fixedLogin = await api.post('/auth/login', { account: fixed.username, password: fixed.password, captchaKey: 'test', captchaCode: '0000' });
  }
  if (fixedLogin.json && fixedLogin.json.code === 0) {
    api.setToken('newbie', fixedLogin.json.data.accessToken);
    ctx.fixedUser = fixed;
  } else {
    console.warn('[WARN] 固定测试账号不可用：' + fixedLogin.raw);
  }

  // 取一个可用的二级分类（发商品/评论都要用）
  const tree = await api.get('/category/tree');
  if (tree.json && tree.json.code === 0 && Array.isArray(tree.json.data)) {
    const first = tree.json.data.find(c => c.children && c.children.length) || tree.json.data[0];
    ctx.categoryId = first.children && first.children.length ? first.children[0].id : first.id;
    ctx.categoryName = first.children && first.children.length ? first.children[0].name : first.name;
  }
  console.log('测试分类：' + ctx.categoryId + ' ' + (ctx.categoryName || ''));

  // 取一件公开商品给聊天用例用（M12 需要一个 productId）
  const firstPage = await api.get('/product/page?pageNum=1&pageSize=1', { token: alice });
  if (firstPage.json && firstPage.json.code === 0 && firstPage.json.data.records.length) {
    ctx.seedProductId = firstPage.json.data.records[0].id;
  }

  const failedSuites = [];
  for (const s of picked) {
    const mod = require(s.file);
    console.log('\n-- ' + mod.name + ' --');
    try {
      await mod.run(ctx);
    } catch (e) {
      console.error(`[套件异常] ${mod.name}: ${e && e.stack ? e.stack : e}`);
      failedSuites.push(mod.name);
    }
  }

  const s = t.summary();
  const reportPath = REPORT;
  t.writeReport(reportPath, {
    analysis: [
      '## 三、结论与建议',
      '',
      '> 本节由脚本内的清单生成，对应当前这 9 个失败用例；处理完请重跑本脚本，失败项会自动消失。',
      '',
      '**总体判断**：主要业务链路（发布→下单→支付→发货→收货→评价→退款、聊天、后台治理）都能跑通，失败集中在「错误码精度」和「边界状态处理」两类，不影响主流程演示，但答辩前建议全部修掉。',
      '',
      '| # | 问题 | 文档依据 | 建议修法 | 工作量 |',
      '| ---: | --- | --- | --- | --- |',
      '| 1 | 注册重复用户名返回 10001 而非 20001 | 03 §A2 | `InnerUserService.register` 抛 `USERNAME_EXISTS`，同时检查 auth 侧是否把 BizException 的 code 覆盖成了 10001 | 小 |',
      '| 2 | 注册重复邮箱返回 10001 而非 20006 | 03 §A2 | 同上，抛 `EMAIL_EXISTS` | 小 |',
      '| 3 | 头像未校验是否本站地址（外部 URL 也能注册成功） | 03 §A2 / 01 §9.4 | `AuthService.register` 增加 `avatar` 前缀白名单校验（`/files/avatar/` 或 `http://127.0.0.1:4402/files/avatar/`），不合法抛 20007 | 小 |',
      '| 4 | 解封后 `banUntil` 仍保留旧值 | 03 §U15 | `AdminUserService.unban` 一并 `setBanUntil(null)`、`setBanReason(null)` | 小 |',
      '| 5 | 商品列表卡片缺 `categoryId` | 03 §P1 响应样例 | `ProductCardVO` 增加 `categoryId` 字段并在 `toCard` 里赋值 | 小 |',
      '| 6 | 发布草稿传空对象返回 10001（标题校验）而非 30004 | 03 §P9 | `publishDraft` 在 `dto != null` 时先算缺失项，缺失就抛 30004（把 `validate(dto,false)` 移到缺失判断之后，或只校验「传了的字段」） | 小 |',
      '| 7 | 卖家同意退款后商品仍为 SOLD（未回收） | 01 §8.9 / 03 §O13 | `RefundService.handle` 同意分支对 `beforeStatus=UNSHIPPED` 调新的 `productMapper.resell(orderNo)`（`SET status=\'ON_SALE\', stock=1, lock_order_no=NULL, sold_time=NULL WHERE ...`），因为当前 `restore` 只处理 LOCKED | 中 |',
      '| 8 | **他人订单能被「支付」**（幂等分支未校验归属） | 01 §7.10 / 03 §Y1 | `PaymentService.pay` 先校验订单买家 == 当前用户（10003），再做幂等返回；否则属于越权漏洞 | 小（安全优先级高） |',
      '| 9 | 内部退款接口非幂等（第二次 50004） | 03 §Y8 | 已 SUCCESS→REFUNDED 的单直接返回成功；退款额度校验放在「状态不是已退款」分支内 | 小 |',
      '',
      '| 10 | 草稿商品的局部编辑被标题校验拦下（只传分类/价格/图片 → 10001） | 03 §P5 | 草稿分支同理改用 alidatePartial，或在 alidate(dto,true) 里对 null 字段跳过 | 小 |',
      '',
      '**修复顺序建议**：#8（越权）→ #7（资金/库存一致性）→ #1/#2/#3（注册契约）→ #10/#6/#5（编辑与字段）→ #9。'
    ].join('\n'),
    env: `Windows / Node ${process.version} / JDK 21 / Spring Boot 3.5.16 + Spring Cloud 2025.0.3`,
    base: `网关 http://127.0.0.1:${api.GATEWAY_PORT}（前端 5173 仅用于浏览器手测）`,
    notes: [
      '本报告由 `node e2e/run.js` 自动生成，用例按《03-接口设计》的接口编号组织。',
      '测试前由 `scripts/start-all.cmd` 启动服务，auth 带 `--mall.captcha.enabled=false`，因此验证码用占位值 `captchaKey=test&captchaCode=0000`。',
      '部分用例依赖前置数据（分类、商品、地址、订单），脚本会自行创建并在用例内清理；若中途失败可能留下以 `测试`/`E2E` 开头的测试数据。',
      '接口直连网关 4399（同时验证路由、鉴权过滤器、头注入），未经过 Vite 代理。'
    ].join('\n\n')
  });

  console.log('\n== 结束 ==');
  console.log(`用例 ${s.total}：通过 ${s.passed}，失败 ${s.failed}，通过率 ${(s.total ? s.passed / s.total * 100 : 0).toFixed(1)}%`);
  console.log('报告：' + reportPath);
  if (s.failed) {
    console.log('\n失败用例：');
    for (const f of s.failedList) console.log(`  - ${f.id} ${f.title}\n    ${f.err}`);
  }
  if (failedSuites.length) console.log('异常套件：' + failedSuites.join(', '));
  process.exitCode = s.failed ? 1 : 0;
}

main().catch(e => {
  console.error(e);
  process.exitCode = 2;
});
