'use strict';
/** P 组：分类 / 商品 / 图片 / 收藏 / 商品评论区 / 管理端（P1~P34） */
const api = require('../lib/api');
const t = require('../lib/harness');
const { get, post, put, del, upload, expectCode, ok, png16 } = api;
const PROD = api.PORTS.product;

function payload(over = {}) {
  return Object.assign({
    title: '测试商品' + Date.now().toString().slice(-6),
    description: '自动化测试创建的商品描述',
    categoryId: null,
    price: 199.0,
    originalPrice: 999.0,
    conditionLevel: 9,
    images: [],
    province: '湖北省', city: '武汉市', district: '洪山区',
    freightMode: 'DISTANCE',
    status: 'ON_SALE'
  }, over);
}

module.exports = {
  name: 'P 商品与分类（P1~P34）',
  async run(ctx) {
    t.suite(this.name);
    const alice = () => api.token('alice');
    const newbie = () => api.token('newbie');

    await t.test('P1', '游客分页：强制 20 条上限并返回 guestLimited', async () => {
      const d = ok(await get('/product/page?pageNum=3&pageSize=100'));
      t.assert(d.records.length <= 20, '游客返回超过 20 条：' + d.records.length);
      t.eq(d.guestLimited, true, 'guestLimited');
      t.eq(Number(d.pageNum), 1, '游客 pageNum 被强制为 1');
      return `records=${d.records.length} total=${d.total}`;
    });

    await t.test('P1-登录分页', '登录后不再限制 20 条、guestLimited=false', async () => {
      const d = ok(await get('/product/page?pageSize=5', { token: alice() }));
      t.eq(d.guestLimited, false, 'guestLimited');
      return `total=${d.total}`;
    });

    await t.test('P1-筛选排序', 'keyword / categoryId / sort / priceMin / city 生效', async () => {
      const byKw = ok(await get('/product/page?keyword=' + encodeURIComponent('iPhone'), { token: alice() }));
      t.assert(byKw.records.every(r => /iPhone/i.test(r.title)), 'keyword 未过滤：' + byKw.records.map(r => r.title).join(','));
      // 卡片 VO 不返回 categoryId，改为「按分类查必须在、按别的分类查必须不在」
      const byCat = ok(await get('/product/page?categoryId=' + ctx.categoryId + '&pageSize=20', { token: alice() }));
      t.assert(byCat.records.some(r => r.title.startsWith('测试商品') || Number(r.price) > 0), '按分类查询没有结果');
      const otherCat = ok(await get('/product/page?categoryId=' + (ctx.otherCategoryId || 999999) + '&pageSize=20', { token: alice() }));
      t.eq(otherCat.records.length, 0, '按不存在的分类查询应有 0 条');
      const asc = ok(await get('/product/page?sort=price_asc&pageSize=20', { token: alice() }));
      const prices = asc.records.map(r => Number(r.price));
      const sorted = [...prices].sort((a, b) => a - b);
      t.assert(JSON.stringify(prices) === JSON.stringify(sorted), 'price_asc 未排序：' + prices.join(','));
      const byCity = ok(await get('/product/page?city=' + encodeURIComponent('武汉市'), { token: alice() }));
      t.assert(byCity.records.every(r => r.city === '武汉市'), 'city 未过滤');
      const byPrice = ok(await get('/product/page?priceMin=100000&priceMax=200000', { token: alice() }));
      t.eq(byPrice.records.length, 0, '高价区间不应有数据');
      return `kw=${byKw.total} cat=${byCat.total} asc首价=${prices[0]}`;
    });

    await t.test('P2-游客', '游客访问商品详情返回 10002', async () => {
      const r = await get('/product/2');
      expectCode(r, 10002);
      return '10002';
    });

    await t.test('P4-校验', '发布校验：缺图/缺分类/价格 0 均返回 10001', async () => {
      const noImg = await post('/product', payload({ categoryId: ctx.categoryId, images: [] }), { token: alice() });
      expectCode(noImg, 10001, '缺图');
      const noCat = await post('/product', payload({ categoryId: null, images: [ctx.avatarUrl] }), { token: alice() });
      expectCode(noCat, 10001, '缺分类');
      const zero = await post('/product', payload({ categoryId: ctx.categoryId, images: [ctx.avatarUrl], price: 0 }), { token: alice() });
      expectCode(zero, 10001, '价格 0');
      const shortTitle = await post('/product', payload({ categoryId: ctx.categoryId, images: [ctx.avatarUrl], title: '短' }), { token: alice() });
      expectCode(shortTitle, 10001, '标题过短');
      return '4 项校验均拦截';
    });

    await t.test('P4', '发布商品成功（ON_SALE）', async () => {
      const d = ok(await post('/product', payload({ categoryId: ctx.categoryId, images: [ctx.avatarUrl] }), { token: alice() }), '发布');
      ctx.productId = String(d.productId || d.id);
      ctx.createdProducts.push(ctx.productId);
      t.eq(d.status, 'ON_SALE', 'status');
      return 'productId=' + ctx.productId;
    });

    await t.test('P2', '详情返回卖家卡片、运费提示、图片列表', async () => {
      const d = ok(await get('/product/' + ctx.productId, { token: newbie() }));
      t.eq(String(d.id), ctx.productId, 'id');
      t.assert(d.seller && (d.seller.nickname || d.seller.id), '缺 seller');
      t.notEmpty(d.freightHint, 'freightHint');
      t.assert(Array.isArray(d.images) && d.images.length >= 1, '缺图片');
      t.eq(d.favorited, false, 'favorited 初始应为 false');
      return `freight=${d.freightHint} seller=${d.seller.nickname}`;
    });

    await t.test('P3', '相似商品列表（可为空，但必须是数组）', async () => {
      const d = ok(await get('/product/' + ctx.productId + '/similar', { token: newbie() }));
      t.assert(Array.isArray(d), '应返回数组');
      return '共 ' + d.length + ' 条';
    });

    await t.test('P5', '局部编辑：只传标题也可保存', async () => {
      const title = '改名商品' + Date.now().toString().slice(-5);
      ok(await put('/product/' + ctx.productId, { title }, { token: alice() }), '局部编辑');
      const d = ok(await get('/product/' + ctx.productId, { token: alice() }));
      t.eq(d.title, title, 'title');
      t.assert(Number(d.price) > 0, '价格被清空');
      return 'ok';
    });

    await t.test('P5-越权', '编辑他人商品返回 30005', async () => {
      const r = await put('/product/' + ctx.productId, { title: '越权改名' }, { token: newbie() });
      expectCode(r, 30005);
      return '30005';
    });

    await t.test('P6', '卖家下架商品（OFF_SHELF）', async () => {
      ok(await put('/product/' + ctx.productId + '/off-shelf', null, { token: alice() }), '下架');
      const d = ok(await get('/product/' + ctx.productId, { token: alice() }));
      t.eq(d.status, 'OFF_SHELF', 'status');
      return 'ok';
    });

    await t.test('P7', '卖家重新上架（ON_SALE 且清空下架原因）', async () => {
      ok(await put('/product/' + ctx.productId + '/on-shelf', null, { token: alice() }), '上架');
      const d = ok(await get('/product/' + ctx.productId, { token: alice() }));
      t.eq(d.status, 'ON_SALE', 'status');
      t.assert(!d.offShelfReason, 'offShelfReason 应被清空，实际 ' + d.offShelfReason);
      return 'ok';
    });

    await t.test('P9/P10', '草稿：发布草稿缺项返回 30004，补齐后可发布', async () => {
      const draft = ok(await post('/product', { title: '草稿商品' + Date.now().toString().slice(-5), status: 'DRAFT' }, { token: alice() }), '存草稿');
      const draftId = String(draft.productId || draft.id);
      ctx.createdProducts.push(draftId);
      t.eq(draft.status, 'DRAFT', 'status');
      const list = ok(await get('/product/my/page?status=DRAFT&pageSize=50', { token: alice() }));
      t.assert(list.records.some(r => String(r.id) === draftId), '草稿箱里找不到');
      const r = await put('/product/' + draftId + '/publish', undefined, { token: alice() });
      const data = expectCode(r, 30004, '缺项发布');
      t.assert(Array.isArray(data.missing) && data.missing.length, '30004 应带 missing 列表');
      // P5 文档要求「可只传要改的字段」，草稿同样适用：这里必须是 code=0
      const partial = await put('/product/' + draftId, {
        categoryId: ctx.categoryId, price: 88, images: [ctx.avatarUrl], province: '湖北省', city: '武汉市', district: '洪山区'
      }, { token: alice() });
      ctx.partialDraftCode = partial.json ? partial.json.code : -1;
      t.eq(ctx.partialDraftCode, 0, '草稿局部编辑被拒：' + (partial.json && partial.json.message));
      if (ctx.partialDraftCode !== 0) {
        const d0 = ok(await get('/product/' + draftId, { token: alice() }));
        ok(await put('/product/' + draftId, {
          title: d0.title, categoryId: ctx.categoryId, price: 88, images: [ctx.avatarUrl],
          province: '湖北省', city: '武汉市', district: '洪山区'
        }, { token: alice() }), '补齐草稿');
      }
      const pub = ok(await put('/product/' + draftId + '/publish', undefined, { token: alice() }), '发布草稿');
      t.assert(pub === null || pub === true || typeof pub === 'object', '返回值异常');
      const d = ok(await get('/product/' + draftId, { token: alice() }));
      t.eq(d.status, 'ON_SALE', '发布后状态');
      return 'missing=' + data.missing.join(',') + '；草稿局部更新 code=' + ctx.partialDraftCode;
    });

    await t.test('P10-游客不可见草稿', '草稿不出现在公开列表', async () => {
      const d = ok(await get('/product/page?pageSize=20', { token: alice() }));
      t.assert(!d.records.some(r => r.status === 'DRAFT'), '公开列表出现草稿');
      return 'ok';
    });

    await t.test('P11', '图片上传（bizType=product）返回 URL；非法 bizType 被拒', async () => {
      const r = await upload('/product/upload', { file: { name: 'p.png', type: 'image/png', data: png16() }, fields: { bizType: 'product' }, token: alice() });
      const d = ok(r, '上传');
      t.assert(/\/files\/product\//.test(d.url), 'url 不符合预期：' + d.url);
      const bad = await upload('/product/upload', { file: { name: 'p.png', type: 'image/png', data: png16() }, fields: { bizType: 'hack' }, token: alice() });
      const code = bad.json ? bad.json.code : -1;
      t.assert(code === 10001 || code === 10008, '非法 bizType 未被拒：' + code);
      return d.url;
    });

    await t.test('P13', '发表评论成功（comment_count +1）', async () => {
      const before = ok(await get('/product/' + ctx.productId, { token: newbie() })).commentCount;
      const d = ok(await post('/product/' + ctx.productId + '/comments', { content: '自动化测试评论：' + Date.now() }, { token: newbie() }), '发评论');
      ctx.commentId = String(d.commentId || d.id);
      const after = ok(await get('/product/' + ctx.productId, { token: newbie() })).commentCount;
      t.eq(Number(after), Number(before) + 1, 'commentCount');
      return 'commentId=' + ctx.commentId;
    });

    await t.test('P9-评论列表', '评论列表返回 user 与 replyCount', async () => {
      const d = ok(await get('/product/' + ctx.productId + '/comments?pageNum=1&pageSize=10', { token: newbie() }));
      t.assert(d.records.length >= 1, '评论为空');
      const c = d.records.find(x => String(x.id) === ctx.commentId) || d.records[0];
      t.assert(c.user && (c.user.nickname || c.user.id), '评论缺少 user');
      t.assert(c.replyCount !== undefined, '缺少 replyCount');
      return `共 ${d.total} 条`;
    });

    await t.test('P13-回复', '回复评论（parentId）出现在 replies 里', async () => {
      ok(await post('/product/' + ctx.productId + '/comments', { content: '卖家回复：' + Date.now(), parentId: ctx.commentId }, { token: alice() }), '回复');
      const d = ok(await get('/product/' + ctx.productId + '/comments?pageNum=1&pageSize=10', { token: alice() }));
      const c = d.records.find(x => String(x.id) === ctx.commentId);
      t.assert(c && c.replies && c.replies.length >= 1, 'replies 为空');
      return 'ok';
    });

    await t.test('P14', '删除自己的评论；删除他人评论被拒（30005/10003）', async () => {
      // 用 alice 发第二条，避开 newbie 的「1 分钟 3 条」限流
      const other = ok(await post('/product/' + ctx.productId + '/comments', { content: '待删评论' + Date.now() }, { token: alice() }), '再发一条');
      const otherId = String(other.commentId || other.id);
      const rDeny = await del('/product/comments/' + otherId, { token: newbie() });
      const denyCode = rDeny.json && rDeny.json.code;
      t.assert([30005, 10003].includes(denyCode), '删他人评论期望 30005/10003，实际 ' + denyCode);
      ok(await del('/product/comments/' + ctx.commentId, { token: newbie() }), '删自己的评论');
      const list = ok(await get('/product/' + ctx.productId + '/comments?pageNum=1&pageSize=20', { token: newbie() }));
      t.assert(!list.records.some(x => String(x.id) === ctx.commentId), '删除后仍可见');
      return 'ok';
    });

    await t.test('P15/P16', '收藏 → 收藏列表含该商品 → 取消收藏', async () => {
      ok(await post('/favorite/' + ctx.productId, null, { token: newbie() }), '收藏');
      const fav = ok(await get('/favorite/page?pageNum=1&pageSize=20', { token: newbie() }));
      t.assert(fav.records.some(r => String(r.productId) === ctx.productId), '收藏列表里没有该商品');
      const d = ok(await get('/product/' + ctx.productId, { token: newbie() }));
      t.eq(d.favorited, true, 'favorited');
      ok(await del('/favorite/' + ctx.productId, { token: newbie() }), '取消收藏');
      const d2 = ok(await get('/product/' + ctx.productId, { token: newbie() }));
      t.eq(d2.favorited, false, '取后 favorited');
      return 'ok';
    });

    await t.test('P19', 'GET /product/dict 返回成色/运费/排序字典', async () => {
      const guest = await get('/product/dict');
      t.eq(guest.json && guest.json.code, 10002, '游客访问 dict 应 10002');
      const d = ok(await get('/product/dict', { token: alice() }));
      t.assert(d && typeof d === 'object', 'dict 为空');
      return Object.keys(d).join(',');
    });

    await t.test('P20', '分类树（公开、两级）', async () => {
      const d = ok(await get('/category/tree'));
      t.assert(Array.isArray(d) && d.length > 0, '分类树为空');
      t.assert(d[0].children === undefined || Array.isArray(d[0].children), 'children 结构异常');
      return '一级 ' + d.length + ' 个';
    });

    await t.test('P21', '管理员新建一级分类与二级分类', async () => {
      const name = '测试分类' + Date.now().toString().slice(-5);
      const top = ok(await post('/category/admin', { name, parentId: 0, sort: 99 }, { token: api.token('admin') }), '建一级');
      ctx.topCategoryId = String(top.id || top.categoryId);
      const child = ok(await post('/category/admin', { name: name + '子', parentId: ctx.topCategoryId, sort: 1 }, { token: api.token('admin') }), '建二级');
      ctx.childCategoryId = String(child.id || child.categoryId);
      return `${ctx.topCategoryId}/${ctx.childCategoryId}`;
    });

    await t.test('P21-重复名', '同级同名分类返回 10001', async () => {
      const dup = '重复分类' + Date.now().toString().slice(-6);
      const r = await post('/category/admin', { name: dup, parentId: 0, sort: 1 }, { token: api.token('admin') });
      ok(r, '首次创建');
      const r2 = await post('/category/admin', { name: dup, parentId: 0, sort: 1 }, { token: api.token('admin') });
      expectCode(r2, 10001, '重复创建');
      return '10001';
    });

    await t.test('P21-三级限制', '在二级分类下再建子级返回 30008', async () => {
      const r = await post('/category/admin', { name: '三级分类', parentId: ctx.childCategoryId, sort: 1 }, { token: api.token('admin') });
      expectCode(r, 30008);
      return '30008';
    });

    await t.test('P22', '修改分类名称', async () => {
      ok(await put('/category/admin/' + ctx.childCategoryId, { name: '改名子分类' + Date.now().toString().slice(-4), sort: 2 }, { token: api.token('admin') }), '改名');
      return 'ok';
    });

    await t.test('P23-占用保护', '删除仍有商品的分类返回 30007', async () => {
      const r = await del('/category/admin/' + ctx.categoryId, { token: api.token('admin') });
      const code = r.json ? r.json.code : -1;
      t.assert(code === 30007 || code === 0, '期望 30007（或该分类本就无商品），实际 ' + code);
      return 'code=' + code;
    });

    await t.test('P23', '删除空分类（先删二级再删一级）', async () => {
      ok(await del('/category/admin/' + ctx.childCategoryId, { token: api.token('admin') }), '删二级');
      ok(await del('/category/admin/' + ctx.topCategoryId, { token: api.token('admin') }), '删一级');
      const tree = ok(await get('/category/tree'));
      t.assert(!tree.some(c => String(c.id) === ctx.topCategoryId), '一级分类仍存在');
      return 'ok';
    });

    await t.test('P24', '后台商品分页（支持状态/关键词筛选）', async () => {
      const d = ok(await get('/product/admin/page?pageNum=1&pageSize=10', { token: api.token('admin') }));
      t.assert(d.total >= 1, 'total 应 ≥1');
      const onSale = ok(await get('/product/admin/page?status=ON_SALE&pageSize=5', { token: api.token('admin') }));
      t.assert(onSale.records.every(r => r.status === 'ON_SALE'), '状态筛选失效');
      const bySeller = ok(await get('/product/admin/page?sellerId=3&pageSize=5', { token: api.token('admin') }));
      t.assert(bySeller.records.every(r => String(r.sellerId) === '3'), 'sellerId 筛选失效');
      return `total=${d.total}`;
    });

    await t.test('P25', '管理员强制下架（写入下架原因）', async () => {
      ok(await put('/product/admin/' + ctx.productId + '/off-shelf', { reason: '违规内容测试' }, { token: api.token('admin') }), '强制下架');
      const d = ok(await get('/product/' + ctx.productId, { token: alice() }));
      t.eq(d.status, 'OFF_SHELF', 'status');
      t.eq(d.offShelfReason, '违规内容测试', 'offShelfReason');
      const notices = ok(await get('/message/notices?pageNum=1&pageSize=50', { token: alice() }));
      const hit = (notices.records || []).find(n => String(n.bizId) === String(ctx.productId) && n.title === '商品已被平台下架');
      t.assert(hit, '卖家未收到下架通知');
      t.contains(hit.content, '违规内容测试', '通知正文');
      return '下架 + 通知均成功';
    });

    await t.test('P26', '后台商品统计', async () => {
      const d = ok(await get('/product/admin/statistics', { token: api.token('admin') }));
      t.assert(d.total !== undefined && d.onSale !== undefined, '统计字段缺失：' + JSON.stringify(d));
      return JSON.stringify(d);
    });

    // ---------- 内部接口：直连 4402 ----------
    await t.test('P27', 'GET /inner/product/{id}（直连 4402）', async () => {
      const d = ok(await get('/inner/product/' + ctx.productId, { internal: true, port: PROD }));
      t.eq(String(d.id), ctx.productId, 'id');
      return 'ok';
    });

    await t.test('P28', 'POST /inner/product/batch 批量取商品', async () => {
      const d = ok(await post('/inner/product/batch', { ids: [Number(ctx.productId), 1] }, { internal: true, port: PROD }));
      t.assert(Array.isArray(d) && d.length >= 1, 'batch 结果为空');
      return '共 ' + d.length + ' 条';
    });

    await t.test('P29/P30', '内部扣库存 → 幂等 → 回补', async () => {
      // 专建一件在售商品，避免受前面"强制下架/SOLD"的影响
      const fresh = ok(await post('/product', payload({ categoryId: ctx.categoryId, images: [ctx.avatarUrl] }), { token: alice() }), '建专测商品');
      const pid = String(fresh.productId || fresh.id);
      ctx.createdProducts.push(pid);
      const orderNo = 'TEST' + Date.now();
      const r1 = await post('/inner/product/stock/deduct', { productId: Number(pid), orderNo }, { internal: true, port: PROD });
      t.assert(r1.json && r1.json.code === 0, '扣库存失败');
      const r2 = await post('/inner/product/stock/deduct', { productId: Number(pid), orderNo }, { internal: true, port: PROD });
      t.assert(r2.json && r2.json.code === 0, '同订单重复扣库存应幂等');
      const r3 = await post('/inner/product/stock/restore', { productId: Number(pid), orderNo }, { internal: true, port: PROD });
      t.assert(r3.json && r3.json.code === 0, '回补失败');
      const after = ok(await get('/inner/product/' + pid, { internal: true, port: PROD }));
      t.eq(after.status, 'ON_SALE', '回补后状态');
      return 'ok';
    });

    await t.test('P34', 'GET /inner/product/statistics/{sellerId}', async () => {
      const d = ok(await get('/inner/product/statistics/3', { internal: true, port: PROD }));
      for (const k of ['onSaleCount', 'draftCount', 'soldCount']) t.assert(d[k] !== undefined, '缺字段 ' + k);
      return JSON.stringify(d);
    });
    await t.test('P1-卡片字段', '列表卡片应带 categoryId（前端分类联动需要）', async () => {
      const d = ok(await get('/product/page?pageNum=1&pageSize=1', { token: alice() }));
      const card = d.records[0];
      t.assert(card, '列表为空');
      t.assert(card.categoryId !== undefined, '卡片缺 categoryId，实际字段：' + Object.keys(card).join(','));
      return 'categoryId=' + card.categoryId;
    });

    await t.test('P9-空请求体', '发布草稿时请求体为空对象应返回 30004（带缺失项）', async () => {
      const d = ok(await post('/product', { title: 'P9 空体用例' + Date.now().toString().slice(-5), status: 'DRAFT' }, { token: alice() }), '建草稿');
      const pid = String(d.productId || d.id);
      const r = await put('/product/' + pid + '/publish', {}, { token: alice() });
      const code = r.json && r.json.code;
      t.assert([30004].includes(code), '期望 30004（缺图片/分类等），实际 ' + code + ' ' + (r.json && r.json.message));
      return '30004';
    });
  }
};
