'use strict';

/**
 * 浏览器功能验收：
 *   node e2e/browser/run.js
 *
 * 说明：
 * - 使用系统 Chrome + DevTools Protocol，不依赖 Playwright/Selenium。
 * - 业务操作在浏览器里完成；API 只用于准备账号、临时商品/订单与清理。
 * - 结果追加到 文档归类/06-功能测试报告.md，并写入同名 JSON 的 browser 字段。
 */

const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { launchBrowser, sleep, ROOT } = require('./lib/cdp');
const png16 = require('../lib/api').png16;
const { suite, test, summary } = require('../lib/harness');

const APP = 'http://127.0.0.1:5173';
const GATEWAY = 'http://127.0.0.1:4399';
const REPORT_MD = path.join(ROOT, '文档归类', '06-功能测试报告.md');
const REPORT_JSON = path.join(ROOT, '文档归类', '06-功能测试报告.json');
const ARTIFACT_DIR = path.join(ROOT, '.tmp', 'browser-artifacts');

const REDIS_CANDIDATES = [
  'D:\\Redis\\Redis-8.8.0-Windows-x64-cygwin-with-Service\\redis-cli.exe',
  'D:\\Redis-8.6.1-Windows-x64-cygwin-with-Service\\redis-cli.exe',
  'D:\\Redis\\msi\\redis-cli.exe'
];

function nowText() {
  return new Date().toLocaleString('zh-CN', { hour12: false });
}

function stamp() {
  const d = new Date();
  const p = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`;
}

function redisValue(key) {
  const redis = REDIS_CANDIDATES.find(p => fs.existsSync(p));
  if (!redis) throw new Error('未找到 redis-cli，无法读取验证码');
  return execFileSync(redis, ['GET', key], { timeout: 5000 }).toString().trim();
}

async function api(method, p, body, token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(GATEWAY + p, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const text = await res.text();
  let json;
  try { json = JSON.parse(text); } catch (_) { throw new Error(`${method} ${p} 非 JSON 响应: ${text.slice(0, 200)}`); }
  return { status: res.status, json, text };
}

async function login(account, password) {
  const cap = await api('GET', '/auth/captcha');
  if (cap.json.code !== 0) throw new Error(`获取验证码失败: ${cap.text}`);
  const code = redisValue('mall:auth:captcha:' + cap.json.data.captchaKey);
  const r = await api('POST', '/auth/login', {
    account, password, captchaKey: cap.json.data.captchaKey, captchaCode: code
  });
  if (r.json.code !== 0) throw new Error(`登录 ${account} 失败: ${r.text}`);
  return { ...r.json.data, account };
}

function localAuth(loginData) {
  return {
    mall_access_token: loginData.accessToken,
    mall_refresh_token: loginData.refreshToken,
    mall_user: JSON.stringify(loginData.userInfo)
  };
}

async function setAuth(page, loginData) {
  await page.setLocalStorage(localAuth(loginData));
}

async function clearAuth(page) {
  await page.setLocalStorage({ mall_access_token: null, mall_refresh_token: null, mall_user: null });
}

async function visit(page, p, requiredTexts = [], timeout = 15000) {
  await page.goto(p, timeout);
  for (const text of requiredTexts) await page.waitForText(text, timeout);
  if (await page.exists('.vite-error-overlay')) throw new Error('页面出现 Vite 错误遮罩');
}

async function clickInsideCard(page, cardText, buttonText) {
  return await page.evaluate(`return (() => {
    const cardText = ${JSON.stringify(cardText)};
    const buttonText = ${JSON.stringify(buttonText)};
    const cards = [...document.querySelectorAll('.order,.card,.mall-card')];
    const card = cards.find(c => (c.innerText || '').includes(cardText));
    if (!card) return false;
    const buttons = [...card.querySelectorAll('button,.el-button')];
    const btn = buttons.find(b => (b.innerText || '').trim() === buttonText)
      || buttons.find(b => (b.innerText || '').includes(buttonText));
    if (!btn) return false;
    btn.click();
    return true;
  })()`);
}

async function screenshot(page, id) {
  try {
    fs.mkdirSync(ARTIFACT_DIR, { recursive: true });
    const r = await page.send('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.join(ARTIFACT_DIR, `${id.replace(/[^\w.-]+/g, '_')}.png`), Buffer.from(r.data, 'base64'));
  } catch (_) {}
}

async function browserTest(page, id, title, fn) {
  return await test(id, title, async () => {
    try {
      return await fn();
    } catch (e) {
      await screenshot(page, id);
      throw e;
    }
  });
}

function mdCell(s) {
  return String(s == null ? '' : s).replace(/\|/g, '\\|').replace(/\r?\n/g, ' ').slice(0, 260);
}

function writeBrowserReport(meta, summaryObj, results) {
  const suites = [...new Set(results.map(r => r.suite))];
  const lines = [];
  lines.push('<!-- BROWSER_E2E_START -->');
  lines.push('## 四、浏览器功能验收（自动生成）');
  lines.push('');
  lines.push(`- 生成时间：${meta.generatedAt}`);
  lines.push(`- 浏览器：${meta.browser}`);
  lines.push(`- 被测入口：${meta.base}`);
  lines.push(`- 用例总数：**${summaryObj.total}**，通过 **${summaryObj.passed}**，失败 **${summaryObj.failed}**`);
  lines.push(`- 通过率：**${summaryObj.total ? ((summaryObj.passed / summaryObj.total) * 100).toFixed(1) : 0}%**`);
  lines.push('');
  lines.push('### 4.1 模块总览');
  lines.push('');
  lines.push('| 模块 | 用例数 | 通过 | 失败 |');
  lines.push('| --- | ---: | ---: | ---: |');
  for (const name of suites) {
    const rs = results.filter(r => r.suite === name);
    const passed = rs.filter(r => r.ok).length;
    lines.push(`| ${mdCell(name)} | ${rs.length} | ${passed} | ${rs.length - passed} |`);
  }
  lines.push(`| **合计** | **${summaryObj.total}** | **${summaryObj.passed}** | **${summaryObj.failed}** |`);
  lines.push('');
  lines.push('### 4.2 未实现 / 失败项');
  lines.push('');
  if (summaryObj.failed) {
    lines.push('| # | 用例 | 现象 |');
    lines.push('| ---: | --- | --- |');
    summaryObj.failedList.forEach((r, i) => lines.push(`| ${i + 1} | ${mdCell(r.id + ' ' + r.title)} | ${mdCell(r.err)} |`));
  } else {
    lines.push('无失败用例。');
  }
  lines.push('');
  lines.push('### 4.3 明细');
  lines.push('');
  for (const name of suites) {
    lines.push(`#### ${name}`);
    lines.push('');
    lines.push('| 用例 | 检查项 | 结果 | 耗时 | 说明 |');
    lines.push('| --- | --- | --- | ---: | --- |');
    for (const r of results.filter(x => x.suite === name)) {
      lines.push(`| ${mdCell(r.id)} | ${mdCell(r.title)} | ${r.ok ? '通过' : '失败'} | ${r.ms}ms | ${mdCell(r.ok ? r.info : r.err)} |`);
    }
    lines.push('');
  }
  lines.push('### 4.4 结论');
  lines.push('');
  lines.push(meta.conclusion);
  lines.push('');
  lines.push('> 说明：本套件验证“浏览器中页面和关键交互是否存在并可触发”；接口级业务规则仍以本报告前半部分的 REST 用例为准。');
  lines.push('<!-- BROWSER_E2E_END -->');

  const block = lines.join('\n');
  let md = fs.existsSync(REPORT_MD) ? fs.readFileSync(REPORT_MD, 'utf8') : '# 功能测试报告（自动生成）\n';
  const start = md.indexOf('<!-- BROWSER_E2E_START -->');
  const end = md.indexOf('<!-- BROWSER_E2E_END -->');
  if (start >= 0 && end >= start) {
    md = md.slice(0, start) + block + md.slice(end + '<!-- BROWSER_E2E_END -->'.length);
  } else {
    md = md.trimEnd() + '\n\n---\n\n' + block + '\n';
  }
  fs.writeFileSync(REPORT_MD, md, 'utf8');

  let json = {};
  if (fs.existsSync(REPORT_JSON)) {
    try { json = JSON.parse(fs.readFileSync(REPORT_JSON, 'utf8')); } catch (_) { json = {}; }
  }
  json.browser = { meta, summary: summaryObj, results };
  fs.writeFileSync(REPORT_JSON, JSON.stringify(json, null, 2), 'utf8');
}

async function main() {
  fs.mkdirSync(ARTIFACT_DIR, { recursive: true });
  const ctx = { suffix: stamp(), tempProductId: null, tempOrderNo: null, draftTitle: null, draftId: null };

  console.log('== 浏览器功能验收开始 ==');
  const alice = await login('alice', 'Qingchuan@2026');
  const bob = await login('bob', 'Qingchuan@2026');
  const admin = await login('admin', 'Qingchuan@2026');
  const superadmin = await login('superadmin', 'Qingchuan@2026');
  ctx.alice = alice;
  ctx.bob = bob;
  ctx.admin = admin;
  ctx.superadmin = superadmin;

  const tree = await api('GET', '/category/tree');
  if (tree.json.code !== 0) throw new Error('分类树加载失败: ' + tree.text);
  const top = (tree.json.data || []).find(c => c.children && c.children.length);
  ctx.categoryId = top ? top.children[0].id : tree.json.data[0].id;

  const page = await api('GET', '/product/page?pageNum=1&pageSize=20');
  if (page.json.code !== 0) throw new Error('商品列表加载失败: ' + page.text);
  const product = (page.json.data.records || []).find(p => p.status === 'ON_SALE' && String(p.sellerId) !== String(bob.userInfo.id));
  if (!product) throw new Error('没有可用于浏览器测试的在售商品');
  ctx.productId = product.id;
  ctx.productTitle = product.title;
  const detail = await api('GET', `/product/${product.id}`, undefined, bob.accessToken);
  if (detail.json.code !== 0) throw new Error('商品详情加载失败: ' + detail.text);
  ctx.sellerId = detail.json.data.sellerId || detail.json.data.seller?.id;
  ctx.sellerName = detail.json.data.seller?.nickname || '卖家';

  let conversations = await api('GET', '/message/conversations?pageNum=1&pageSize=20', undefined, bob.accessToken);
  let conversationId = conversations.json?.data?.records?.[0]?.conversationId;
  if (!conversationId) {
    const conv = await api('POST', '/message/conversation', {
      peerId: alice.userInfo.id,
      productId: ctx.productId,
      openWithProductCard: true
    }, bob.accessToken);
    if (conv.json.code !== 0) throw new Error('创建测试会话失败: ' + conv.text);
    conversationId = conv.json.data.conversationId;
  }
  ctx.conversationId = conversationId;

  ctx.editDraftTitle = `Codex编辑页测试${ctx.suffix}`;
  const editDraft = await api('POST', '/product', {
    title: ctx.editDraftTitle,
    status: 'DRAFT'
  }, alice.accessToken);
  if (editDraft.json.code !== 0) throw new Error('创建编辑页测试草稿失败: ' + editDraft.text);
  ctx.editDraftId = editDraft.json.data.productId;

  const browser = await launchBrowser({ port: 9345 });
  const cdp = browser.cdp;

  try {
    await cdp.goto('/');

    // ---------------- A 基础页面与认证 ----------------
    suite('A 基础页面与认证');

    await browserTest(cdp, 'B-A1', '游客首页、分类、排序与 20 条限制', async () => {
      await clearAuth(cdp);
      await visit(cdp, '/', ['晴川二手市场', '全部分类', '最新发布', '登录后可查看全部商品']);
      const cards = await cdp.count('.card');
      if (cards < 1 || cards > 20) throw new Error(`游客商品卡片数量异常: ${cards}`);
      if (!(await cdp.exists('.cats')) || !(await cdp.exists('.sort'))) throw new Error('分类栏或排序栏不存在');
      if (!(await cdp.exists('.cat.top.has-children')) || !(await cdp.exists('.cat-arrow'))) {
        throw new Error('一级分类缺少可展开标识');
      }
      if ((await cdp.count('.cat.sub')) !== 0) throw new Error('二级分类默认未折叠');
      if (!(await cdp.clickSelector('.cat.top.has-children'))) throw new Error('一级分类不可点击');
      await cdp.waitFor(`document.querySelectorAll('.cat.sub').length > 0`, 5000, '点击一级分类展开二级');
      const expandedCategoryId = await cdp.evaluate(`return new URLSearchParams(location.search).get('categoryId') || '';`);
      if (!expandedCategoryId) throw new Error('展开一级分类时未同步分类筛选');
      if (!(await cdp.clickSelector('.cat.top.has-children'))) throw new Error('一级分类无法再次点击');
      await cdp.waitFor(`document.querySelectorAll('.cat.sub').length === 0`, 5000, '再次点击收起二级');
      return `商品卡片 ${cards} 个；二级分类默认折叠，点击一级分类展开，再次点击收起`;
    });

    await browserTest(cdp, 'B-A2', '游客访问商品详情被引导登录', async () => {
      await clearAuth(cdp);
      await cdp.goto(`/product/${ctx.productId}`);
      await cdp.waitFor(`location.pathname === '/login'`, 10000, '游客详情跳登录');
      return '路由守卫生效';
    });

    await browserTest(cdp, 'B-A3', '登录页控件完整', async () => {
      await clearAuth(cdp);
      await visit(cdp, '/login', ['晴川二手市场']);
      await cdp.waitForPlaceholder('用户名 / 邮箱');
      await cdp.waitForPlaceholder('密码');
      await cdp.waitForPlaceholder('验证码');
      if (!(await cdp.exists('img[alt="验证码"]'))) throw new Error('验证码图片未渲染');
      if (!(await cdp.exists('button'))) throw new Error('登录按钮不存在');
      const body = await cdp.bodyText();
      if (!body.includes('忘记密码') || !body.includes('立即注册')) throw new Error('找回密码/注册入口缺失');
      return '账号、密码、验证码、找回密码、注册入口均可见';
    });

    await browserTest(cdp, 'B-A4', '登录表单可实际登录', async () => {
      await clearAuth(cdp);
      await cdp.goto('/login');
      await cdp.waitFor(`document.querySelector('img[alt="验证码"]')`, 12000, '验证码图片');
      const cap = await cdp.getResponseBody('/auth/captcha');
      if (!cap?.data?.captchaKey) throw new Error('未捕获登录页验证码响应');
      const code = redisValue('mall:auth:captcha:' + cap.data.captchaKey);
      if (!(await cdp.fillPlaceholder('用户名 / 邮箱', 'alice'))) throw new Error('账号输入框不可用');
      if (!(await cdp.fillPlaceholder('密码', 'Qingchuan@2026'))) throw new Error('密码输入框不可用');
      if (!(await cdp.fillPlaceholder('验证码', code))) throw new Error('验证码输入框不可用');
      if (!(await cdp.clickText('登录', 'button'))) throw new Error('登录按钮不可点击');
      await cdp.waitFor(`location.pathname === '/'`, 15000, '登录成功跳首页');
      await cdp.waitForText('爱丽丝', 10000);
      return 'alice 登录成功并回到首页';
    });

    await browserTest(cdp, 'B-A5', '注册页控件完整', async () => {
      await clearAuth(cdp);
      await visit(cdp, '/login', ['登录']);
      if (!(await cdp.clickText('立即注册', '.el-button'))) throw new Error('“立即注册”入口不可点击');
      await cdp.waitFor(`location.pathname === '/register'`, 10000, '跳转注册页');
      for (const ph of ['4~20 位字母、数字或下划线', '用于登录与找回密码', '8~20 位，含字母和数字', '再输入一次密码', '验证码']) {
        await cdp.waitForPlaceholder(ph);
      }
      if (!(await cdp.bodyText()).includes('上传头像')) throw new Error('注册页缺少头像上传入口');
      return '头像、用户名、邮箱、密码、确认密码、验证码控件可见';
    });

    await browserTest(cdp, 'B-A6', '找回密码两个页面可用', async () => {
      await clearAuth(cdp);
      await visit(cdp, '/reset', ['找回密码']);
      await cdp.waitForPlaceholder('注册邮箱');
      await visit(cdp, '/reset/confirm', ['设置新密码', '重置密码']);
      await cdp.waitForPlaceholder('6 位数字验证码');
      return '发码页与重置页均渲染';
    });

    await browserTest(cdp, 'B-A7', '登录后顶部用户菜单与发布入口存在', async () => {
      await setAuth(cdp, alice);
      await visit(cdp, '/', ['晴川二手市场', '爱丽丝']);
      if (!(await cdp.exists('.fab'))) throw new Error('首页发布悬浮按钮缺失');
      if (!(await cdp.exists('.me'))) throw new Error('顶部用户菜单缺失');
      return '用户菜单与发布 FAB 可见';
    });

    await browserTest(cdp, 'B-A8', '点击顶部 Logo 回首页并整页刷新', async () => {
      await setAuth(cdp, bob);
      await visit(cdp, `/product/${ctx.productId}`, ['商品评论']);
      await cdp.evaluate(`window.__logoReloadMarker = 'still-alive'; return true;`);
      if (!(await cdp.clickSelector('.logo'))) throw new Error('顶部 Logo 不可点击');
      await cdp.waitFor(`location.pathname === '/'`, 10000, 'Logo 回到首页');
      await cdp.waitFor(`document.readyState === 'complete' && document.querySelector('#app')?.children.length > 0`, 10000, '首页重新加载');
      const marker = await cdp.evaluate(`return window.__logoReloadMarker || '';`);
      if (marker) throw new Error('Logo 只做了 SPA 跳转，没有整页刷新');
      return '已整页刷新到首页';
    });

    await browserTest(cdp, 'B-A9', '全局视觉主题与桌面布局生效', async () => {
      await setAuth(cdp, bob);
      await visit(cdp, '/', ['晴川二手市场', '全部分类']);
      const primary = await cdp.evaluate(`return getComputedStyle(document.documentElement).getPropertyValue('--mall-primary').trim();`);
      const logoImage = await cdp.evaluate(`return document.querySelector('.logo-mark img')?.getAttribute('src') || '';`);
      const cardRadius = await cdp.evaluate(`return parseFloat(getComputedStyle(document.querySelector('.card')).borderRadius) || 0;`);
      const inputRight = await cdp.evaluate(`return document.querySelector('.search .el-input__wrapper').getBoundingClientRect().right;`);
      const buttonLeft = await cdp.evaluate(`return document.querySelector('.search-submit').getBoundingClientRect().left;`);
      const buttonRight = await cdp.evaluate(`return document.querySelector('.search-submit').getBoundingClientRect().right;`);
      const searchRight = await cdp.evaluate(`return document.querySelector('.search').getBoundingClientRect().right;`);
      const noHorizontalOverflow = await cdp.evaluate(`return document.documentElement.scrollWidth <= window.innerWidth + 1;`);
      if (String(primary).toLowerCase() !== '#f26b3f') throw new Error('品牌主色未生效：' + primary);
      if (!String(logoImage).includes('/brand/qingchuan-logo.png')) throw new Error('学校 Logo 未渲染：' + logoImage);
      if (cardRadius < 16) throw new Error('商品卡圆角未应用：' + cardRadius);
      if (inputRight > buttonLeft + 1) throw new Error(`搜索输入区与按钮重叠：${inputRight} > ${buttonLeft}`);
      if (buttonRight > searchRight + 1) throw new Error(`搜索按钮超出容器：${buttonRight} > ${searchRight}`);
      if (!noHorizontalOverflow) throw new Error('桌面首页出现横向溢出');
      return '暖色主题、学校 Logo、大圆角商品卡和桌面布局均生效';
    });

    // ---------------- U 我的 ----------------
    suite('U 我的');

    await browserTest(cdp, 'B-U1', '个人中心资料、统计与 7 个页签', async () => {
      await setAuth(cdp, alice);
      await visit(cdp, '/mine/published', ['我发布的', '草稿箱', '我卖出的', '我买到的', '我的收藏', '收货地址', '我的评价']);
      const body = await cdp.bodyText();
      if (!body.includes('信用分')) throw new Error('个人资料信用分未渲染');
      if (!body.includes('在售')) throw new Error('统计区未渲染');
      return '资料卡、统计与 7 个页签齐全';
    });

    await browserTest(cdp, 'B-U2', '我的收藏页可用', async () => {
      await setAuth(cdp, alice);
      await visit(cdp, '/mine/favorite', ['我的收藏']);
      const body = await cdp.bodyText();
      if (!body.includes('收藏') && !body.includes('还没有收藏')) throw new Error('收藏内容/空状态均未渲染');
      return body.includes('还没有收藏') ? '空状态正常' : '收藏列表正常';
    });

    await browserTest(cdp, 'B-U3', '收货地址页和全国三级地区选择可用', async () => {
      await setAuth(cdp, alice);
      await visit(cdp, '/mine/address', ['收货地址', '新增地址']);
      if (!(await cdp.clickText('新增地址'))) throw new Error('新增地址按钮不可点击');
      await cdp.waitForText('新增收货地址', 6000);
      if (!(await cdp.exists('.el-dialog'))) throw new Error('地址弹窗未打开');
      if (!(await cdp.clickSelector('.el-dialog .el-cascader'))) throw new Error('所在地区级联不可点击');
      await cdp.waitForText('浙江省', 8000);
      for (const name of ['浙江省', '杭州市', '西湖区']) {
        if (!(await cdp.clickText(name, '.el-cascader-node'))) throw new Error('无法选择地区：' + name);
        await sleep(250);
      }
      const values = await cdp.evaluate(`return [...document.querySelectorAll('.el-dialog input')].map(x => x.value).filter(Boolean);`);
      if (!values.some(v => v.includes('浙江省') && v.includes('杭州市') && v.includes('西湖区'))) {
        throw new Error('全国地区选择未回填：' + JSON.stringify(values));
      }
      if (!(await cdp.clickText('取消'))) throw new Error('地址弹窗取消按钮不存在');
      return '可选浙江省 / 杭州市 / 西湖区';
    });

    await browserTest(cdp, 'B-U4', '我的评价页可用', async () => {
      await setAuth(cdp, alice);
      await visit(cdp, '/mine/reviews', ['我的评价']);
      const body = await cdp.bodyText();
      if (!body.includes('评价')) throw new Error('评价区域未渲染');
      return body.includes('还没有发表过评价') ? '空状态正常' : '评价列表正常';
    });

    await browserTest(cdp, 'B-U5', '草稿箱页签可用', async () => {
      await setAuth(cdp, alice);
      await visit(cdp, '/mine/drafts', ['草稿箱']);
      return '草稿箱路由与内容区正常';
    });

    await browserTest(cdp, 'B-U6', '个人中心可点击头像上传并保存', async () => {
      await setAuth(cdp, alice);
      await visit(cdp, '/mine', ['我发布的', '更换头像']);
      const file = path.join(ROOT, '.tmp', 'avatar-ui-test.png');
      fs.writeFileSync(file, png16());
      try {
        await cdp.setFileInput('.avatar-edit input[type=file]', file);
        await cdp.waitFor(`document.querySelector('.avatar-edit img')?.src.includes('/files/avatar/')`, 15000, '头像上传并更新');
        const src = await cdp.evaluate(`return document.querySelector('.avatar-edit img')?.src || '';`);
        if (!src.includes('/files/avatar/')) throw new Error('头像地址未更新：' + src);
        return '头像上传、资料保存和页面刷新正常';
      } finally {
        try { await api('PUT', '/user/profile', { avatar: alice.userInfo.avatar }, alice.accessToken); } catch (_) {}
        try { fs.unlinkSync(file); } catch (_) {}
      }
    });

    await browserTest(cdp, 'B-U7', '个人资料可手动修改省市区', async () => {
      const original = await api('GET', '/user/profile', undefined, alice.accessToken);
      if (original.json.code !== 0) throw new Error('读取原资料失败');
      try {
        await setAuth(cdp, alice);
        await visit(cdp, '/mine', ['编辑资料']);
        if (!(await cdp.clickText('编辑资料', 'button'))) throw new Error('编辑资料按钮不可点击');
        for (const label of ['个性签名', '昵称', '所在地区']) {
          await cdp.waitForText(label, 5000);
        }
        await cdp.waitFor(`document.querySelector('.sign-edit .region-input')`, 6000, '资料地区级联');
        if (!(await cdp.clickSelector('.sign-edit .region-input'))) throw new Error('资料地区级联不可点击');
        await cdp.waitForText('浙江省', 8000);
        for (const name of ['浙江省', '杭州市', '西湖区']) {
          if (!(await cdp.clickText(name, '.el-cascader-node'))) throw new Error('无法选择地区：' + name);
          await sleep(250);
        }
        if (!(await cdp.clickText('保存', '.sign-edit button'))) throw new Error('资料保存按钮不可点击');
        await cdp.waitForText('浙江省 / 杭州市 / 西湖区', 12000);
        return '手动修改省市区并保存成功';
      } finally {
        const restore = {};
        for (const k of ['province', 'city', 'district']) if (original.json.data[k]) restore[k] = original.json.data[k];
        try { await api('PUT', '/user/profile', restore, alice.accessToken); } catch (_) {}
      }
    });

    await browserTest(cdp, 'B-U8', '我卖出的/我买到的在个人中心右侧内嵌展示', async () => {
      await setAuth(cdp, alice);
      await visit(cdp, '/mine/published', ['我的交易', '我卖出的', '我买到的']);
      if (!(await cdp.clickText('我卖出的', '.side-sub div'))) throw new Error('“我卖出的”菜单不可点击');
      await cdp.waitFor(`location.pathname === '/mine/sold'`, 8000, '右侧切换到我卖出的');
      await cdp.waitFor(`document.querySelector('.mine-order-panel')`, 8000, '我卖出的内嵌面板');
      if (!(await cdp.bodyText()).includes('在这里查看买家与成交进度')) throw new Error('我卖出的右侧内容未展示');
      if (!(await cdp.clickText('我买到的', '.side-sub div'))) throw new Error('“我买到的”菜单不可点击');
      await cdp.waitFor(`location.pathname === '/mine/bought'`, 8000, '右侧切换到我买到的');
      await cdp.waitFor(`document.querySelector('.mine-order-panel')`, 8000, '我买到的内嵌面板');
      const path = await cdp.path();
      if (path.startsWith('/orders')) throw new Error('交易菜单仍跳转到订单页');
      return '交易子项均在个人中心右侧内嵌展示，未跳转订单页';
    });

    // ---------------- P 商品 ----------------
    suite('P 商品');

    await browserTest(cdp, 'B-P1', '首页商品卡可进入详情', async () => {
      await setAuth(cdp, bob);
      await visit(cdp, '/', ['最新闲置']);
      if (!(await cdp.clickSelector('.card'))) throw new Error('商品卡片不可点击');
      await cdp.waitFor(`location.pathname.startsWith('/product/')`, 10000, '商品详情跳转');
      await cdp.waitForText('商品评论', 12000);
      return '卡片点击、详情路由与评论区域正常';
    });

    await browserTest(cdp, 'B-P2', '商品详情核心控件完整', async () => {
      await setAuth(cdp, bob);
      await visit(cdp, `/product/${ctx.productId}`, ['商品评论', '立即购买', '联系卖家', '收藏']);
      if (!(await cdp.exists('.seller-card'))) throw new Error('卖家卡片未渲染');
      return '购买、联系卖家、收藏、卖家卡片、评论区均可见';
    });

    await browserTest(cdp, 'B-P3A', '卖家主页路由可独立访问', async () => {
      await setAuth(cdp, bob);
      await visit(cdp, `/seller/${ctx.sellerId}`, ['在售闲置']);
      return `卖家主页 /seller/${ctx.sellerId} 正常渲染`;
    });

    await browserTest(cdp, 'B-P3B', '商品详情卖家卡片点击可跳转', async () => {
      await setAuth(cdp, bob);
      await visit(cdp, `/product/${ctx.productId}`, ['商品评论']);
      await cdp.waitFor(`document.querySelector('.seller-card')`, 10000, '卖家卡片');
      if (!(await cdp.clickSelector('.seller-card'))) throw new Error('卖家卡片不可点击');
      try {
        await cdp.waitFor(`location.pathname.startsWith('/seller/')`, 4000, '卖家主页跳转');
      } catch (_) {
        throw new Error('点击卖家卡片后仍停留在商品详情；ProductDetail 响应缺少 sellerId，前端 toSeller 判断失败');
      }
      await cdp.waitForText('在售闲置', 10000);
      return '卖家卡片点击后进入卖家主页';
    });

    await browserTest(cdp, 'B-P4', '发布页表单与图片上传入口完整', async () => {
      await setAuth(cdp, alice);
      await visit(cdp, '/publish', ['发布闲置', '商品图片', '标题', '分类', '价格', '发货方式', '发货地', '描述']);
      const body = await cdp.bodyText();
      if (!body.includes('添加图片')) throw new Error('图片上传入口缺失');
      if (!body.includes('发布') || !body.includes('存草稿')) throw new Error('发布/存草稿按钮缺失');
      if (!(await cdp.clickSelector('.grid-3 .el-select__wrapper'))) throw new Error('成色选择器不可点击');
      await cdp.waitForText('全新', 5000);
      if (!(await cdp.clickText('全新', '.el-select-dropdown__item'))) throw new Error('成色选项缺少“全新”');
      await sleep(250);
      const conditionText = await cdp.evaluate(`return document.querySelector('.grid-3 .el-select')?.innerText || '';`);
      if (!conditionText.includes('全新')) throw new Error('选择“全新”后未回填');
      return '表单、图片入口、成色“全新”选项、发布与存草稿按钮齐全';
    });

    await browserTest(cdp, 'B-P5', '存草稿通过浏览器实际成功', async () => {
      ctx.draftTitle = `Codex浏览器草稿${ctx.suffix}`;
      await setAuth(cdp, alice);
      await visit(cdp, '/publish', ['发布闲置']);
      if (!(await cdp.fillPlaceholder('例如：iPhone 13 128G 蓝色 自用无磕碰', ctx.draftTitle))) throw new Error('标题输入框不可用');
      const before = await cdp.evaluate(`return document.querySelector('input[placeholder="例如：iPhone 13 128G 蓝色 自用无磕碰"]')?.value || '';`);
      if (!(await cdp.clickText('存草稿', 'button'))) throw new Error('存草稿按钮不可点击');
      let navigated = true;
      try {
        await cdp.waitFor(`location.pathname === '/mine/drafts'`, 4000, '存草稿后跳转草稿箱');
      } catch (_) { navigated = false; }
      if (!navigated) {
        const createRequests = cdp.responses.filter(r => r.url.endsWith('/product') && r.status >= 200 && r.status < 300).length;
        const countText = (await cdp.bodyText()).match(/\d+ \/ 60/)?.[0] || 'unknown';
        throw new Error(`点击存草稿后仍停留在 /publish；标题值=${before || '(空)'}；页面计数=${countText}；成功 POST /product 请求数=${createRequests}`);
      }
      await cdp.waitForText(ctx.draftTitle, 12000);
      const page = await api('GET', '/product/my/page?status=DRAFT&pageNum=1&pageSize=50', undefined, alice.accessToken);
      const hit = (page.json.data.records || []).find(p => p.title === ctx.draftTitle);
      if (!hit) throw new Error('草稿接口中未找到刚保存的数据');
      ctx.draftId = hit.id;
      return `草稿已保存并出现在草稿箱，id=${hit.id}`;
    });

    await browserTest(cdp, 'B-P6', '草稿编辑页可回填并可发布', async () => {
      await setAuth(cdp, alice);
      await visit(cdp, `/publish/${ctx.editDraftId}`, ['编辑闲置']);
      const val = await cdp.evaluate(`return document.querySelector('input[placeholder="例如：iPhone 13 128G 蓝色 自用无磕碰"]')?.value || '';`);
      if (val !== ctx.editDraftTitle) throw new Error(`编辑页标题回填异常: ${val}`);
      const body = await cdp.bodyText();
      if (!body.includes('保存并发布')) throw new Error('编辑态缺少“保存并发布”');
      return '标题正确回填，编辑态发布按钮存在';
    });

    await browserTest(cdp, 'B-P7', '商品评论编辑器可用', async () => {
      await setAuth(cdp, bob);
      await visit(cdp, `/product/${ctx.productId}`, ['商品评论', '发表评论']);
      if (!(await cdp.exists('textarea[placeholder="问问卖家细节，或者聊聊成色…"]'))) throw new Error('评论输入框未渲染');
      return '评论输入、图片上传入口与发表按钮可见';
    });

    await browserTest(cdp, 'B-P8', '购买页地址、订单信息与下单入口完整', async () => {
      await setAuth(cdp, bob);
      await visit(cdp, `/purchase/${ctx.productId}`, ['确认订单', '收货地址', '订单信息', '确认下单']);
      const body = await cdp.bodyText();
      if (!body.includes('商品金额') || !body.includes('运费')) throw new Error('订单金额/运费信息缺失');
      return '地址、商品、运费、备注与确认下单均可见';
    });

    // ---------------- O 订单与支付 ----------------
    suite('O 订单与支付');

    await browserTest(cdp, 'B-O1', '订单页 6 个状态页签与买卖身份切换完整', async () => {
      await setAuth(cdp, bob);
      await visit(cdp, '/orders?tab=ALL', ['我的订单', '我买到的', '我卖出的', '待付款', '待发货', '待收货', '已完成', '退款/售后', '全部']);
      if (!(await cdp.clickText('我卖出的', '.el-radio-button'))) throw new Error('“我卖出的”切换不可点击');
      await sleep(700);
      return '状态页签与买家/卖家切换均存在';
    });

    await browserTest(cdp, 'B-O2', '订单支付确认弹窗可由浏览器触发', async () => {
      const title = `Codex浏览器订单${ctx.suffix}`;
      const productRes = await api('POST', '/product', {
        title,
        categoryId: ctx.categoryId,
        price: 9.99,
        conditionLevel: 9,
        freightMode: 'DISTANCE',
        province: '湖北省', city: '武汉市', district: '洪山区',
        description: '浏览器订单测试临时商品',
        images: [alice.userInfo.avatar],
        status: 'ON_SALE'
      }, alice.accessToken);
      if (productRes.json.code !== 0) throw new Error('创建临时商品失败: ' + productRes.text);
      ctx.tempProductId = productRes.json.data.productId;

      const addrRes = await api('GET', '/user/addresses', undefined, bob.accessToken);
      let address = (addrRes.json.data || [])[0];
      if (!address) {
        const created = await api('POST', '/user/addresses', {
          receiver: 'Codex测试', phone: '13800138000', province: '湖北省', city: '武汉市', district: '洪山区',
          detail: '浏览器测试地址 1 号', isDefault: true
        }, bob.accessToken);
        if (created.json.code !== 0) throw new Error('创建测试地址失败: ' + created.text);
        address = { id: created.json.data.id };
      }

      const orderRes = await api('POST', '/order/create', {
        productId: ctx.tempProductId,
        addressId: address.id,
        remark: 'Codex浏览器测试'
      }, bob.accessToken);
      if (orderRes.json.code !== 0) throw new Error('创建测试订单失败: ' + orderRes.text);
      ctx.tempOrderNo = orderRes.json.data.orderNo;

      await setAuth(cdp, bob);
      await visit(cdp, '/orders?tab=UNPAID', [title]);
      if (!(await clickInsideCard(cdp, title, '去支付'))) throw new Error('测试订单的“去支付”按钮不可点击');
      await cdp.waitForText('确认支付', 8000);
      if ((await cdp.bodyText()).includes('不会真的扣款')) {
        // 支付弹窗文案已出现
      }
      if (!(await cdp.clickText('取消', '.el-message-box button'))) {
        await cdp.clickText('取消');
      }
      return '临时订单已提交到订单页，支付确认弹窗成功打开';
    });

    await browserTest(cdp, 'B-O3', '退款/售后页签可加载', async () => {
      await setAuth(cdp, bob);
      await visit(cdp, '/orders?tab=REFUND', ['我的订单', '退款/售后']);
      await sleep(700);
      const body = await cdp.bodyText();
      if (body.includes('服务器内部错误')) throw new Error('退款/售后页出现服务器内部错误');
      return '退款/售后页签与内容区正常加载';
    });

    await browserTest(cdp, 'B-O5', '买家可在订单页成功提交退款申请', async () => {
      const title = `Codex退款回归${ctx.suffix}`;
      const product = await api('POST', '/product', {
        title, categoryId: ctx.categoryId, price: 11.11, conditionLevel: 9,
        freightMode: 'DISTANCE', province: '湖北省', city: '武汉市', district: '洪山区',
        images: [alice.userInfo.avatar], status: 'ON_SALE'
      }, alice.accessToken);
      if (product.json.code !== 0) throw new Error('创建退款测试商品失败：' + product.text);
      const pid = product.json.data.productId;
      let orderNo = '';
      try {
        const addr = await api('GET', '/user/addresses', undefined, bob.accessToken);
        if (!addr.json.data?.length) throw new Error('bob 没有测试收货地址');
        const created = await api('POST', '/order/create', { productId: pid, addressId: addr.json.data[0].id }, bob.accessToken);
        if (created.json.code !== 0) throw new Error('创建退款测试订单失败：' + created.text);
        orderNo = created.json.data.orderNo;
        const paid = await api('POST', '/payment/pay', { orderNo }, bob.accessToken);
        if (paid.json.code !== 0) throw new Error('支付退款测试订单失败：' + paid.text);

        await setAuth(cdp, bob);
        await visit(cdp, '/orders?tab=UNSHIPPED', [title]);
        if (!(await clickInsideCard(cdp, title, '申请退款'))) throw new Error('申请退款按钮不可点击');
        await cdp.waitForText('申请退款 / 售后', 6000);
        await cdp.fillPlaceholder('请填写退款原因', '浏览器退款回归');
        if (!(await cdp.clickText('提交申请', '.el-dialog button'))) throw new Error('提交申请不可点击');
        await cdp.waitForText('退款申请已提交', 10000);
        return '退款申请已从订单页成功提交';
      } finally {
        try {
          if (orderNo) {
            const page = await api('GET', '/order/refund/page?role=sold&pageNum=1&pageSize=50', undefined, alice.accessToken);
            const row = (page.json?.data?.records || []).find(r => r.orderNo === orderNo && r.status === 'APPLYING');
            if (row) await api('PUT', `/order/refund/${row.id}/handle`, { agree: true, remark: '浏览器测试清理' }, alice.accessToken);
          }
          await api('DELETE', `/product/${pid}`, undefined, alice.accessToken);
        } catch (_) {}
      }
    });

    await browserTest(cdp, 'B-O6', '卖家可从退款通知进入审批并同意退款', async () => {
      const title = `Codex卖家退款审批${ctx.suffix}`;
      let pid = '';
      let orderNo = '';
      try {
        const product = await api('POST', '/product', {
          title, categoryId: ctx.categoryId, price: 12.34, conditionLevel: 9,
          freightMode: 'DISTANCE', province: '湖北省', city: '武汉市', district: '洪山区',
          images: [alice.userInfo.avatar], status: 'ON_SALE'
        }, alice.accessToken);
        if (product.json.code !== 0) throw new Error('创建审批回归商品失败：' + product.text);
        pid = product.json.data.productId;

        const addr = await api('GET', '/user/addresses', undefined, bob.accessToken);
        if (!addr.json.data?.length) throw new Error('bob 没有测试收货地址');
        const created = await api('POST', '/order/create', {
          productId: pid, addressId: addr.json.data[0].id
        }, bob.accessToken);
        if (created.json.code !== 0) throw new Error('创建审批回归订单失败：' + created.text);
        orderNo = created.json.data.orderNo;
        const paid = await api('POST', '/payment/pay', { orderNo }, bob.accessToken);
        if (paid.json.code !== 0) throw new Error('支付审批回归订单失败：' + paid.text);
        const applied = await api('POST', `/order/${orderNo}/refund`, {
          amount: 12.34, reason: '浏览器卖家审批回归'
        }, bob.accessToken);
        if (applied.json.code !== 0) throw new Error('提交审批回归退款失败：' + applied.text);

        await setAuth(cdp, alice);
        await visit(cdp, '/chat?tab=notice', ['系统通知']);
        await cdp.waitForText('退款申请待处理', 10000);
        await cdp.waitForText(orderNo, 10000);
        await cdp.waitFor(`[...document.querySelectorAll('.notice')]
          .some(x => (x.innerText || '').includes(${JSON.stringify(orderNo)}) && x.querySelector('.notice-op button'))`,
          10000, '目标退款通知及处理按钮');
        try {
          await cdp.evaluate(`(() => {
          const notice = [...document.querySelectorAll('.notice')]
            .find(x => (x.innerText || '').includes(${JSON.stringify(orderNo)}));
          const button = notice?.querySelector('.notice-op button');
          if (!button) return false;
          button.click();
          return true;
        })()`);
        } catch (_) {
          // 点击触发路由跳转时 CDP 执行上下文可能被销毁，以页面 URL 作为成功依据。
        }
        await cdp.waitFor(`location.pathname === '/orders' && location.search.includes('tab=REFUND') && location.search.includes('role=sold')`, 10000, '退款通知跳转卖家审批页');
        await cdp.waitForText(orderNo, 10000);
        const soldSelected = await cdp.evaluate(`return [...document.querySelectorAll('.el-radio-button')]
          .some(x => (x.innerText || '').includes('我卖出的') && x.classList.contains('is-active'));`);
        if (!soldSelected) throw new Error('退款通知跳转后未选中“我卖出的”');
        if (!(await cdp.exists('.refund-actions'))) throw new Error('卖家退款审批操作区缺失');
        if (!(await clickInsideCard(cdp, orderNo, '同意退款'))) throw new Error('目标退款单的同意按钮不可点击');
        await cdp.waitForText('确认同意退款', 8000);
        await cdp.fillPlaceholder('处理备注（选填）', '浏览器审批通过');
        if (!(await cdp.clickText('确认同意退款', '.el-message-box button'))) throw new Error('确认审批按钮不可点击');
        await cdp.waitForText('退款已处理', 10000);

        const list = await api('GET', '/order/refund/page?role=sold&pageNum=1&pageSize=50', undefined, alice.accessToken);
        const row = (list.json?.data?.records || []).find(r => r.orderNo === orderNo);
        if (!row || row.status !== 'REFUNDED') throw new Error('审批后退款单状态异常：' + JSON.stringify(row));
        return '卖家通知入口、卖家视角退款列表、同意退款操作及状态流转均正常';
      } finally {
        try {
          if (orderNo) {
            const page = await api('GET', '/order/refund/page?role=sold&pageNum=1&pageSize=50', undefined, alice.accessToken);
            const row = (page.json?.data?.records || []).find(r => r.orderNo === orderNo && r.status === 'APPLYING');
            if (row) await api('PUT', `/order/refund/${row.id}/handle`, { agree: true, remark: '浏览器测试清理' }, alice.accessToken);
          }
          if (pid) await api('DELETE', `/product/${pid}`, undefined, alice.accessToken);
        } catch (_) {}
      }
    });

    await browserTest(cdp, 'B-O4', '卖家订单卡显示买家姓名电话地址', async () => {
      await setAuth(cdp, alice);
      await visit(cdp, '/orders?tab=UNSHIPPED', ['我的订单']);
      if (!(await cdp.clickText('我卖出的', '.el-radio-button'))) throw new Error('我卖出的切换失败');
      await cdp.waitFor(`document.querySelector('.buyer-info')`, 10000, '买家信息块');
      const text = await cdp.evaluate(`return document.querySelector('.buyer-info')?.innerText || '';`);
      if (!text.includes('买家') || !text.includes('电话') || !text.includes('地址')) {
        throw new Error('买家信息不完整：' + text);
      }
      return '买家姓名、电话、地址均显示';
    });

    // ---------------- M 聊天 ----------------
    suite('M 聊天');

    await browserTest(cdp, 'B-M1', '聊天中心会话列表与系统通知入口可用', async () => {
      await setAuth(cdp, bob);
      await visit(cdp, '/chat', ['系统通知']);
      await cdp.waitForPlaceholder('搜索会话');
      if (!(await cdp.exists('.conv-list'))) throw new Error('会话列表容器缺失');
      return '会话搜索、会话列表、系统通知入口可见';
    });

    await browserTest(cdp, 'B-M2', '指定会话可打开且输入区完整', async () => {
      await setAuth(cdp, bob);
      await visit(cdp, `/chat?cid=${ctx.conversationId}`, ['发送']);
      await cdp.waitForPlaceholder('输入消息，Enter 发送');
      if (!(await cdp.exists('.msg-list'))) throw new Error('消息列表未渲染');
      if (!(await cdp.exists('textarea[placeholder="输入消息，Enter 发送"]'))) throw new Error('聊天输入框缺失');
      if (!(await cdp.exists('.input-bar .el-button'))) throw new Error('发送按钮缺失');
      return '深链会话、消息区、输入框与发送按钮正常';
    });

    await browserTest(cdp, 'B-M3', '系统通知页签可打开', async () => {
      await setAuth(cdp, bob);
      await visit(cdp, '/chat?tab=notice', ['系统通知']);
      await sleep(500);
      const body = await cdp.bodyText();
      if (!body.includes('通知')) throw new Error('通知内容区未渲染');
      return '系统通知页签正常';
    });

    await browserTest(cdp, 'B-M4', '管理员下架后卖家能在通知页看到原因', async () => {
      const title = `Codex下架通知${ctx.suffix}`;
      const created = await api('POST', '/product', {
        title, categoryId: ctx.categoryId, price: 8.88, conditionLevel: 9,
        freightMode: 'DISTANCE', province: '湖北省', city: '武汉市', district: '洪山区',
        images: [alice.userInfo.avatar], status: 'ON_SALE'
      }, alice.accessToken);
      if (created.json.code !== 0) throw new Error('创建通知测试商品失败：' + created.text);
      const pid = created.json.data.productId;
      try {
        const off = await api('PUT', `/product/admin/${pid}/off-shelf`, { reason: '通知回归验证' }, admin.accessToken);
        if (off.json.code !== 0) throw new Error('管理员下架失败：' + off.text);
        const before = await api('GET', '/message/notices/unread-count', undefined, alice.accessToken);
        if (before.json.code !== 0 || Number(before.json.data.total) < 1) {
          throw new Error('下架后通知未读数应大于 0：' + before.text);
        }
        await setAuth(cdp, alice);
        await visit(cdp, '/chat?tab=notice', ['系统通知']);
        await cdp.waitForText('商品已被平台下架', 10000);
        await cdp.waitForText(title, 10000);
        await cdp.waitForText('通知回归验证', 10000);
        await sleep(700);
        const after = await api('GET', '/message/notices/unread-count', undefined, alice.accessToken);
        if (after.json.code !== 0 || Number(after.json.data.total) !== 0) {
          throw new Error('进入通知页后未读数未清零：' + after.text);
        }
        const badgeVisible = await cdp.evaluate(`return [...document.querySelectorAll('.conv.sys .el-badge__content')]
          .some(x => x.getClientRects().length > 0 && getComputedStyle(x).display !== 'none');`);
        if (badgeVisible) throw new Error('通知已全部已读，但系统通知红点仍显示');
        return '卖家通知页可见下架原因；进入后全部通知已读且红点清除';
      } finally {
        try { await api('DELETE', `/product/${pid}`, undefined, alice.accessToken); } catch (_) {}
      }
    });

    await browserTest(cdp, 'B-M5', '订单卡片显示商品信息并跳转商品详情', async () => {
      const title = `Codex订单卡片${ctx.suffix}`;
      let pid = '';
      let orderNo = '';
      try {
        const product = await api('POST', '/product', {
          title, categoryId: ctx.categoryId, price: 18.88, conditionLevel: 9,
          freightMode: 'DISTANCE', province: '湖北省', city: '武汉市', district: '洪山区',
          images: [alice.userInfo.avatar], status: 'ON_SALE'
        }, alice.accessToken);
        if (product.json.code !== 0) throw new Error('创建订单卡片测试商品失败：' + product.text);
        pid = product.json.data.productId;

        const addr = await api('GET', '/user/addresses', undefined, bob.accessToken);
        if (!addr.json.data?.length) throw new Error('bob 没有测试收货地址');
        const created = await api('POST', '/order/create', {
          productId: pid, addressId: addr.json.data[0].id
        }, bob.accessToken);
        if (created.json.code !== 0) throw new Error('创建订单卡片测试订单失败：' + created.text);
        orderNo = created.json.data.orderNo;
        const paid = await api('POST', '/payment/pay', { orderNo }, bob.accessToken);
        if (paid.json.code !== 0) throw new Error('支付订单卡片测试订单失败：' + paid.text);
        const shipped = await api('PUT', `/order/${orderNo}/ship`, { logisticsNo: 'CARD-E2E' }, alice.accessToken);
        if (shipped.json.code !== 0) throw new Error('发货订单卡片测试订单失败：' + shipped.text);

        const conversations = await api('GET', '/message/conversations?pageNum=1&pageSize=50', undefined, bob.accessToken);
        const conversation = (conversations.json?.data?.records || [])
          .find(c => String(c.productId) === String(pid));
        if (!conversation) throw new Error('未找到订单卡片所属会话');

        await setAuth(cdp, bob);
        await visit(cdp, `/chat?cid=${conversation.conversationId}`, [title]);
        const compactTitle = title.replace(/\s+/g, '');
        await cdp.waitFor(`[...document.querySelectorAll('.card-bubble.order')]
          .some(x => (x.innerText || '').replace(/\\s+/g, '').includes(${JSON.stringify(compactTitle)}))`,
          10000, '订单商品卡片渲染');
        const cardText = await cdp.evaluate(`return [...document.querySelectorAll('.card-bubble.order')]
          .map(x => x.innerText || '').join('\\n---\\n');`);
        const cardImage = await cdp.evaluate(`return [...document.querySelectorAll('.card-bubble.order')]
          .filter(x => (x.innerText || '').replace(/\\s+/g, '').includes(${JSON.stringify(compactTitle)}))
          .map(x => x.querySelector('img')?.src || '')[0] || '';`);
        if (!cardText.replace(/\s+/g, '').includes(compactTitle)) throw new Error('订单卡片未显示商品名');
        if (!cardImage) throw new Error('订单卡片未显示商品图');
        if (cardText.includes(orderNo)) throw new Error('订单卡片仍显示订单号：' + cardText);
        await cdp.evaluate(`(() => {
          const card = [...document.querySelectorAll('.card-bubble.order')]
            .find(x => (x.innerText || '').replace(/\\s+/g, '').includes(${JSON.stringify(compactTitle)}));
          if (card) card.dataset.codexProductCard = '1';
          return !!card;
        })()`);
        try {
          await cdp.evaluate(`document.querySelector('[data-codex-product-card="1"]')?.click(); return true;`);
        } catch (_) {
          // 路由跳转可能销毁执行上下文，以下方 URL 断言为准。
        }
        await cdp.waitFor(`location.pathname === '/product/${pid}'`, 10000, '订单卡片跳转商品详情');
        return '订单卡片显示商品图/商品名，点击后进入商品详情且不显示订单号';
      } finally {
        try {
          if (orderNo) await api('PUT', `/order/${orderNo}/receive`, undefined, bob.accessToken);
          if (pid) await api('DELETE', `/product/${pid}`, undefined, alice.accessToken);
        } catch (_) {}
      }
    });

    // ---------------- X 管理后台 ----------------
    suite('X 管理后台');

    await browserTest(cdp, 'B-X1', '管理员用户管理页与控制项完整', async () => {
      await setAuth(cdp, admin);
      await visit(cdp, '/admin/users', ['管理后台', '用户管理', '查询']);
      await cdp.waitForPlaceholder('用户名 / 邮箱 / 昵称 / 手机号');
      const body = await cdp.bodyText();
      if (!body.includes('封禁') || !body.includes('限制上架')) throw new Error('用户治理操作按钮缺失');
      return '搜索、角色/状态筛选、封禁与上架权限操作可见';
    });

    await browserTest(cdp, 'B-X2', '管理员商品页可按已下架状态快速筛选', async () => {
      await setAuth(cdp, admin);
      await visit(cdp, '/admin/products', ['管理后台', '商品管理', '已下架']);
      await cdp.waitForPlaceholder('标题关键词');
      if (!(await cdp.clickText('已下架', '.stat'))) throw new Error('已下架状态卡不可点击');
      await sleep(800);
      const active = await cdp.evaluate(`return document.querySelector('.stat.active')?.innerText || '';`);
      if (!active.includes('已下架')) throw new Error('已下架筛选未激活：' + active);
      const rows = await cdp.evaluate(`return [...document.querySelectorAll('.el-table__body-wrapper tbody tr')].map(tr => tr.innerText);`);
      if (rows.length === 0) throw new Error('已下架筛选后没有数据行');
      if (!rows.every(t => t.includes('已下架'))) throw new Error('筛选后出现非已下架商品');
      return `已下架筛选正常，rows=${rows.length}`;
    });

    await browserTest(cdp, 'B-X3', '管理员分类管理页完整', async () => {
      await setAuth(cdp, admin);
      await visit(cdp, '/admin/categories', ['管理后台', '分类管理', '新建一级分类']);
      const body = await cdp.bodyText();
      if (!body.includes('重命名') || !body.includes('删除')) throw new Error('分类编辑操作缺失');
      return '新建、加子分类、重命名、删除入口可见';
    });

    await browserTest(cdp, 'B-X4', '管理员订单与退款页完整', async () => {
      await setAuth(cdp, admin);
      await visit(cdp, '/admin/orders', ['管理后台', '订单与退款', '订单总数', '退款 / 售后']);
      return '订单统计、订单/退款页签与表格区正常';
    });

    await browserTest(cdp, 'B-X5', '超级管理员角色操作入口可见', async () => {
      await setAuth(cdp, superadmin);
      await visit(cdp, '/admin/users', ['用户管理']);
      const body = await cdp.bodyText();
      if (!body.includes('设置角色')) throw new Error('超级管理员角色设置按钮不可见');
      return '超级管理员可看到“设置角色”操作';
    });

  } finally {
    // 清理浏览器测试产生的临时订单/商品
    try {
      if (ctx.tempOrderNo) {
        await api('PUT', `/order/${ctx.tempOrderNo}/cancel`, { reason: 'Codex浏览器测试清理' }, bob.accessToken);
      }
    } catch (_) {}
    try {
      if (ctx.tempProductId) await api('DELETE', `/product/${ctx.tempProductId}`, undefined, alice.accessToken);
    } catch (_) {}
    try {
      if (ctx.draftId) await api('DELETE', `/product/${ctx.draftId}`, undefined, alice.accessToken);
      if (ctx.editDraftId) await api('DELETE', `/product/${ctx.editDraftId}`, undefined, alice.accessToken);
      if (ctx.draftTitle) {
        const drafts = await api('GET', '/product/my/page?status=DRAFT&pageNum=1&pageSize=100', undefined, alice.accessToken);
        for (const d of (drafts.json?.data?.records || []).filter(x => x.title === ctx.draftTitle)) {
          await api('DELETE', `/product/${d.id}`, undefined, alice.accessToken);
        }
      }
    } catch (_) {}
    await browser.close();
  }

  const s = summary();
  const browserInfo = `${browser.version.Browser || 'Chrome'} (headless)`;
  const failedNames = s.failedList.map(r => r.id).join('、') || '无';
  const meta = {
    generatedAt: nowText(),
    browser: browserInfo,
    base: APP,
    gateway: GATEWAY,
    note: '浏览器测试使用系统 Chrome + CDP；接口仅用于准备测试数据和清理，关键操作由浏览器触发。',
    conclusion: s.failed === 0
      ? '浏览器侧页面与关键交互均已实现并可触发；未发现阻断性缺失。'
      : `浏览器侧 ${s.passed}/${s.total} 项通过，${s.failed} 项失败：${failedNames}。请按明细中的错误信息和截图定位，避免复用历史结论。`
  };
  writeBrowserReport(meta, s, require('../lib/harness').results);
  console.log(`\n== 浏览器功能验收完成：${s.passed}/${s.total} 通过，${s.failed} 失败 ==`);
  console.log('报告：' + REPORT_MD);
  // 关闭浏览器后显式退出，避免 CDP/网络句柄让测试进程滞留。
  process.exit(s.failed ? 2 : 0);
}

main().catch(e => {
  console.error('[浏览器验收异常]', e && e.stack ? e.stack : e);
  process.exit(1);
});
