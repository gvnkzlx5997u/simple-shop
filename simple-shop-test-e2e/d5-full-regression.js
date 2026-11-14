/**
 * 阶段D 买家端全量回归（D5-1）
 * 严格按《阶段D-买家端开发计划》§9.1 手工回归清单逐项走查。
 *
 * 前置：
 *   1. 后端跑在 8080、前端跑在 5174
 *   2. 数据库里有测试商品：
 *      GD-D1-ON  (199.00，库存 5，在售)
 *      GD-D1-ON3 (15.50，库存 3，在售)
 *      GD-D1-ON2 (39.90，库存 0，在售但缺货)
 *      GD-D1-OFF (299.00，已下架)
 *      GD-FAV-01..15（11.00~25.00，库存 5，在售）
 *   3. 环境变量 E2E_DB_PASSWORD 必填（脚本不落明文口令）
 *
 * 跑法：node d5-full-regression.js
 */
const { chromium } = require('playwright');
const { execSync } = require('child_process');

const BASE = process.env.E2E_BASE_URL || 'http://localhost:5174';
const DB = process.env.E2E_DB_NAME || 'simple_shop_d1';
const PASS = process.env.E2E_DB_PASSWORD || process.env.DB_PASSWORD;
if (!PASS) {
  console.error('缺少环境变量 E2E_DB_PASSWORD');
  process.exit(2);
}

/** 所有 SQL 必须单行：cmd.exe 会把换行当命令分隔符。 */
const SQL = (q) => {
  try {
    return execSync(
      `mysql -uroot -p${PASS} -N -B --default-character-set=utf8mb4 ${DB} -e "${q}" 2>nul`,
      { shell: 'cmd.exe' },
    ).toString().trim();
  } catch (e) {
    return 'SQLERR';
  }
};

const results = [];
const t = async (name, fn) => {
  try {
    results.push([name, String(await fn())]);
  } catch (e) {
    results.push([name, 'EXCEPTION ' + String(e.message).split('\n')[0].slice(0, 80)]);
  }
};
const ok = (r) => String(r).startsWith('PASS');

function resetFixtures() {
  SQL(`UPDATE simpleshop_goods SET status='ON_SALE', off_shelf_reason=NULL, off_shelf_at=NULL WHERE id LIKE 'GD-%'`);
  SQL(`UPDATE simpleshop_goods_stock SET available=5 WHERE fk_goods_id='GD-D1-ON'`);
  SQL(`UPDATE simpleshop_goods_stock SET available=3 WHERE fk_goods_id='GD-D1-ON3'`);
  SQL(`UPDATE simpleshop_goods_stock SET available=0 WHERE fk_goods_id='GD-D1-ON2'`);
  SQL(`UPDATE simpleshop_goods_stock SET available=5 WHERE fk_goods_id LIKE 'GD-FAV-%'`);
  SQL(`UPDATE simpleshop_goods SET status='OFF_SHELF', off_shelf_reason='MANUAL', off_shelf_at=UTC_TIMESTAMP() WHERE id='GD-D1-OFF'`);
}

function cleanupUser(username) {
  const id = SQL(`SELECT id FROM simpleshop_buyer_account WHERE username='${username}'`);
  if (!id || id === 'SQLERR' || !id.trim()) return;
  SQL(`DELETE i FROM simpleshop_order_item i JOIN simpleshop_order o ON o.id=i.fk_order_id WHERE o.fk_buyer_id='${id}'`);
  SQL(`DELETE FROM simpleshop_order WHERE fk_buyer_id='${id}'`);
  SQL(`DELETE FROM simpleshop_favorite WHERE fk_buyer_id='${id}'`);
  SQL(`DELETE FROM simpleshop_cart_item WHERE fk_buyer_id='${id}'`);
  SQL(`DELETE FROM simpleshop_buyer_account WHERE id='${id}'`);
}

(async () => {
  resetFixtures();
  const browser = await chromium.launch({ channel: 'msedge' });
  const user = 'd5' + Date.now().toString().slice(-7);
  const pageErrors = [];

  // ==================== A 组：未登录 ====================
  const anonCtx = await browser.newContext();
  const anon = await anonCtx.newPage();
  anon.setDefaultTimeout(15000);
  anon.on('pageerror', (e) => pageErrors.push('[匿名] ' + e.message.slice(0, 90)));
  anon.on('console', (m) => {
    if (m.type() === 'error' && !m.text().includes('404')) pageErrors.push('[匿名] ' + m.text().slice(0, 90));
  });
  const ab = () => anon.innerText('body');

  await t('[A1] 未登录·商品列表可浏览', async () => {
    await anon.goto(BASE + '/goods', { waitUntil: 'networkidle' });
    const b = await ab();
    const cards = await anon.locator('.goods-card').count();
    // 测试库里有 18 件在售，列表第一页固定 12 件；不假设某一件商品一定落在第一页。
    return cards >= 10 && b.includes('¥')
      ? 'PASS 渲染 ' + cards + ' 张卡片'
      : 'FAIL cards=' + cards + ' ' + b.slice(0, 60).replace(/\n/g, '|');
  });

  await t('[A2] 未登录·商品详情可浏览', async () => {
    await anon.goto(BASE + '/goods/GD-D1-ON', { waitUntil: 'networkidle' });
    const b = await ab();
    return b.includes('测试耳机在售') && b.includes('199') ? 'PASS' : 'FAIL';
  });

  await t('[A3] 未登录·搜索有结果', async () => {
    await anon.goto(BASE + '/goods', { waitUntil: 'networkidle' });
    await anon.fill('input[placeholder="搜索商品名称或描述"]', '耳机');
    await anon.click('button.search-btn');
    await anon.waitForTimeout(700);
    return (await ab()).includes('测试耳机在售') ? 'PASS' : 'FAIL';
  });

  await t('[A4] 未登录·搜索无结果给空态', async () => {
    await anon.fill('input[placeholder="搜索商品名称或描述"]', '不存在的zzz');
    await anon.click('button.search-btn');
    await anon.waitForTimeout(700);
    const b = await ab();
    return !b.includes('测试耳机在售') ? 'PASS' : 'FAIL 未过滤';
  });

  await t('[A5] 未登录·注册', async () => {
    await anon.goto(BASE + '/register', { waitUntil: 'networkidle' });
    const ins = anon.locator('input');
    await ins.nth(0).fill(user);
    await ins.nth(1).fill('Abcd1234');
    await ins.nth(2).fill('13300001111');
    await ins.nth(3).fill('回归测试点');
    await anon.click('button.primary');
    await anon.waitForTimeout(1800);
    const b = await ab();
    const db = SQL(`SELECT COUNT(*) FROM simpleshop_buyer_account WHERE username='${user}'`);
    return b.includes(user) && db === '1' ? 'PASS 注册并入库' : `FAIL db=${db}`;
  });

  await t('[A6] 未登录·退出后重新登录', async () => {
    await anon.click('button:has-text("退出")');
    await anon.waitForTimeout(1000);
    await anon.goto(BASE + '/login', { waitUntil: 'networkidle' });
    const ins = anon.locator('input');
    await ins.nth(0).fill(user);
    await ins.nth(1).fill('Abcd1234');
    await anon.click('button.primary');
    await anon.waitForTimeout(1700);
    return (await ab()).includes(user) ? 'PASS' : 'FAIL';
  });

  for (const [label, path] of [['A7', '/cart'], ['A8', '/favorites'], ['A9', '/orders']]) {
    await t(`[${label}] 未登录·${path} 被拦到登录`, async () => {
      const c = await browser.newContext();
      const p = await c.newPage();
      await p.goto(BASE + path, { waitUntil: 'networkidle' });
      const u = p.url();
      await c.close();
      return u.includes('/login') ? 'PASS 带 redirect' : 'FAIL ' + u;
    });
  }

  await t('[A10] 未登录·点加购跳登录并带回跳地址', async () => {
    const c = await browser.newContext();
    const p = await c.newPage();
    p.setDefaultTimeout(15000);
    await p.goto(BASE + '/goods/GD-D1-ON', { waitUntil: 'networkidle' });
    await p.click('button:has-text("加入购物车")');
    await p.waitForTimeout(1200);
    const u = p.url();
    await c.close();
    return u.includes('/login') && decodeURIComponent(u).includes('/goods/GD-D1-ON') ? 'PASS' : 'FAIL ' + u;
  });
  await anonCtx.close();

  // ==================== B 组：购物车 ====================
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  page.setDefaultTimeout(18000);
  page.on('pageerror', (e) => pageErrors.push('[主] ' + e.message.slice(0, 90)));
  page.on('console', (m) => {
    if (m.type() === 'error' && !m.text().includes('404')) pageErrors.push('[主] ' + m.text().slice(0, 90));
  });
  const body = () => page.innerText('body');
  const total = async () => (await page.innerText('.total strong')).replace(/\s/g, '');
  const addToCart = async (id) => {
    await page.goto(BASE + '/goods/' + id, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(420);
    await page.click('button:has-text("加入购物车")');
    await page.waitForTimeout(780);
  };
  const cartRows = () =>
    SQL(`SELECT COUNT(*) FROM simpleshop_cart_item c JOIN simpleshop_buyer_account b ON b.id=c.fk_buyer_id WHERE b.username='${user}'`);

  // 先登录（A 组已注册并登录，但换了 context，这里重新登录）
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' });
  {
    const ins = page.locator('input');
    await ins.nth(0).fill(user);
    await ins.nth(1).fill('Abcd1234');
    await page.click('button.primary');
    await page.waitForTimeout(1700);
  }

  await t('[B1] 空购物车有引导', async () => {
    await page.goto(BASE + '/cart', { waitUntil: 'networkidle' });
    await page.waitForTimeout(700);
    const b = await body();
    return b.includes('购物车是空的') && b.includes('去逛逛') ? 'PASS' : 'FAIL ' + b.slice(0, 70).replace(/\n/g, '|');
  });

  await t('[B2] 加购单件', async () => {
    await addToCart('GD-D1-ON');
    return (await body()).includes('已加入购物车') ? 'PASS' : 'FAIL';
  });

  await t('[B3] 重复加购提示已在车里', async () => {
    await page.click('button:has-text("加入购物车")');
    await page.waitForTimeout(900);
    return (await body()).includes('已经在购物车里了') && cartRows() === '1' ? 'PASS 数据库仍 1 行' : 'FAIL ' + cartRows();
  });

  await t('[B4] 加购第二件', async () => {
    await addToCart('GD-D1-ON3');
    await page.goto(BASE + '/cart', { waitUntil: 'networkidle' });
    await page.waitForTimeout(700);
    const n = await page.locator('.row-item').count();
    return n === 2 ? 'PASS 2 行' : 'FAIL ' + n;
  });

  await t('[B5] 改数量', async () => {
    const row = page.locator('.row-item', { hasText: '测试数据线在售' });
    await row.locator('.qty-input').fill('2');
    await row.locator('.qty-input').blur();
    await page.waitForTimeout(400);
    const q = await row.locator('.qty-input').inputValue();
    const tl = await total();
    return q === '2' && tl === '¥230.00' ? 'PASS 数量2 合计¥230.00' : `FAIL qty=${q} total=${tl}`;
  });

  await t('[B6] 数量超库存被夹住', async () => {
    const row = page.locator('.row-item', { hasText: '测试数据线在售' });
    await row.locator('.qty-input').fill('99');
    await row.locator('.qty-input').blur();
    await page.waitForTimeout(400);
    const q = await row.locator('.qty-input').inputValue();
    return q === '3' ? 'PASS 夹到库存3' : 'FAIL ' + q;
  });

  await t('[B7] 取消勾选影响合计', async () => {
    await page.locator('.row-item', { hasText: '测试耳机在售' }).locator('.row-check').uncheck();
    await page.waitForTimeout(400);
    const tl = await total();
    return tl === '¥46.50' ? 'PASS ¥46.50' : 'FAIL ' + tl;
  });

  await t('[B8] 全选勾上可买的', async () => {
    await page.locator('.bar .check-all input').check();
    await page.waitForTimeout(400);
    const tl = await total();
    const b = await body();
    return tl === '¥245.50' && b.includes('已选 2 件') ? 'PASS ¥245.50/2件' : `FAIL total=${tl}`;
  });

  await t('[B9] 刷新后数量与勾选保持', async () => {
    await page.reload({ waitUntil: 'networkidle' });
    await page.waitForTimeout(900);
    const ear = await page.locator('.row-item', { hasText: '测试耳机在售' }).locator('.row-check').isChecked();
    const q = await page.locator('.row-item', { hasText: '测试数据线在售' }).locator('.qty-input').inputValue();
    return ear && q === '3' ? 'PASS' : `FAIL ear=${ear} qty=${q}`;
  });

  await t('[B10] 失效行灰掉锁住并提示', async () => {
    SQL(`UPDATE simpleshop_goods SET status='OFF_SHELF', off_shelf_reason='MANUAL', off_shelf_at=UTC_TIMESTAMP() WHERE id='GD-D1-ON3'`);
    await page.reload({ waitUntil: 'networkidle' });
    await page.waitForTimeout(1000);
    const row = page.locator('.row-item', { hasText: '测试数据线' }).first();
    const txt = await row.innerText();
    const dis = await row.locator('.row-check').isDisabled();
    const qdis = await row.locator('.qty-input').isDisabled();
    const b = await body();
    return txt.includes('已下架') && dis && qdis && b.includes('有 1 件商品已下架')
      ? 'PASS 标注+锁住+提示条' : `FAIL dis=${dis} qty=${qdis}`;
  });

  await t('[B11] 失效行不计入合计', async () => {
    const tl = await total();
    return tl === '¥199.00' ? 'PASS ¥199.00' : 'FAIL ' + tl;
  });
  SQL(`UPDATE simpleshop_goods SET status='ON_SALE', off_shelf_reason=NULL, off_shelf_at=NULL WHERE id='GD-D1-ON3'`);

  await t('[B12] 删除单行', async () => {
    await page.reload({ waitUntil: 'networkidle' });
    await page.waitForTimeout(900);
    const before = await page.locator('.row-item').count();
    await page.locator('.row-item', { hasText: '测试数据线在售' }).locator('button.remove').click();
    await page.waitForTimeout(1400);
    const after = await page.locator('.row-item').count();
    return before === 2 && after === 1 && cartRows() === '1' ? 'PASS 2→1，库内1行' : `FAIL ${before}→${after} db=${cartRows()}`;
  });

  // ==================== C 组：下单 ====================
  const orderCount = () =>
    SQL(`SELECT COUNT(*) FROM simpleshop_order o JOIN simpleshop_buyer_account b ON b.id=o.fk_buyer_id WHERE b.username='${user}'`);

  await t('[C1] 单件下单并跳详情', async () => {
    // 购物车此时只剩耳机 1 件
    await page.goto(BASE + '/cart', { waitUntil: 'networkidle' });
    await page.waitForTimeout(800);
    await page.locator('.bar .check-all input').check();
    await page.waitForTimeout(400);
    await page.click('button.checkout');
    await page.waitForTimeout(2800);
    const u = page.url();
    const b = await body();
    return /\/orders\/[^/?#]+/.test(u) && b.includes('待确认') && b.includes('¥199.00')
      ? 'PASS 跳到详情、待确认、¥199.00' : `FAIL url=${u} ` + b.slice(0, 80).replace(/\n/g, '|');
  });

  await t('[C2] 多件下单金额正确', async () => {
    await addToCart('GD-D1-ON');
    await addToCart('GD-D1-ON3');
    await page.goto(BASE + '/cart', { waitUntil: 'networkidle' });
    await page.waitForTimeout(800);
    await page.locator('.row-item', { hasText: '测试数据线在售' }).locator('.qty-input').fill('2');
    await page.locator('.row-item', { hasText: '测试数据线在售' }).locator('.qty-input').blur();
    await page.waitForTimeout(400);
    await page.locator('.bar .check-all input').check();
    await page.waitForTimeout(400);
    await page.click('button.checkout');
    await page.waitForTimeout(2800);
    const b = await body();
    const rows = await page.locator('.items tbody tr').count();
    return b.includes('¥230.00') && rows === 2 ? 'PASS ¥230.00、明细2行' : `FAIL rows=${rows} ` + b.slice(0, 80).replace(/\n/g, '|');
  });

  await t('[C3] 含失效行下单被拦', async () => {
    await addToCart('GD-D1-ON');
    await addToCart('GD-D1-ON3');
    await page.goto(BASE + '/cart', { waitUntil: 'networkidle' });
    await page.waitForTimeout(800);
    // 勾好后下架其中一件
    SQL(`UPDATE simpleshop_goods SET status='OFF_SHELF', off_shelf_reason='MANUAL', off_shelf_at=UTC_TIMESTAMP() WHERE id='GD-D1-ON3'`);
    await page.locator('.bar .check-all input').check().catch(() => {});
    await page.waitForTimeout(400);
    await page.click('button.checkout');
    await page.waitForTimeout(2500);
    const b = await body();
    return b.includes('有商品已下架') ? 'PASS 提示「有商品已下架」' : 'FAIL ' + b.slice(0, 120).replace(/\n/g, '|');
  });

  await t('[C4] 下单失败后购物车自动刷新（失效行显形）', async () => {
    const row = page.locator('.row-item', { hasText: '测试数据线' }).first();
    const txt = await row.innerText();
    const dis = await row.locator('.row-check').isDisabled();
    return txt.includes('已下架') && dis ? 'PASS 该行标已下架并锁住' : `FAIL dis=${dis} txt=${txt.replace(/\n/g, '|').slice(0, 40)}`;
  });
  SQL(`UPDATE simpleshop_goods SET status='ON_SALE', off_shelf_reason=NULL, off_shelf_at=NULL WHERE id='GD-D1-ON3'`);

  await t('[C5] 库存不足被拒并提示', async () => {
    await page.goto(BASE + '/cart', { waitUntil: 'networkidle' });
    await page.waitForTimeout(900);
    // 把数据线数量拉到 3，同时把库存改成 1
    const row = page.locator('.row-item', { hasText: '测试数据线在售' });
    await row.locator('.qty-input').fill('3');
    await row.locator('.qty-input').blur();
    await page.waitForTimeout(400);
    SQL(`UPDATE simpleshop_goods_stock SET available=1 WHERE fk_goods_id='GD-D1-ON3'`);
    await page.locator('.bar .check-all input').check();
    await page.waitForTimeout(400);
    await page.click('button.checkout');
    await page.waitForTimeout(2500);
    const b = await body();
    return b.includes('库存不足') ? 'PASS 提示「库存不足」' : 'FAIL ' + b.slice(0, 130).replace(/\n/g, '|');
  });
  SQL(`UPDATE simpleshop_goods_stock SET available=3 WHERE fk_goods_id='GD-D1-ON3'`);

  // ==================== D 组：订单 ====================
  await t('[D1] 订单列表可见已下的单', async () => {
    await page.goto(BASE + '/orders', { waitUntil: 'networkidle' });
    await page.waitForTimeout(1000);
    const n = await page.locator('.order-row').count();
    const db = orderCount();
    return n >= 2 && n === Number(db) ? `PASS 列表 ${n} 笔 = 数据库 ${db} 笔` : `FAIL 列表=${n} 库=${db}`;
  });

  await t('[D2] 订单列表分页（补到 12 笔）', async () => {
    const bid = SQL(`SELECT id FROM simpleshop_buyer_account WHERE username='${user}'`);
    SQL(`INSERT INTO simpleshop_order (id, order_no, fk_buyer_id, total_amount, status, create_at, update_at) SELECT CONCAT('D5', LPAD(n,2,'0'), '${user.slice(-4)}'), CONCAT('SOFILL', LPAD(n,4,'0')), '${bid}', 100+n, CASE WHEN n<=4 THEN 'CONFIRMED' WHEN n<=6 THEN 'SHIPPED' WHEN n<=8 THEN 'COMPLETED' ELSE 'CANCELED' END, DATE_ADD('2026-11-11 07:00:00', INTERVAL n MINUTE), DATE_ADD('2026-11-11 07:00:00', INTERVAL n MINUTE) FROM (SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) t`);
    await page.goto(BASE + '/orders', { waitUntil: 'networkidle' });
    await page.waitForTimeout(1100);
    const n = await page.locator('.order-row').count();
    const b = await body();
    return n === 10 && b.includes('第 1 / 2 页') && b.includes('共 12 笔') ? 'PASS 第1页10笔/共12笔' : `FAIL rows=${n} ` + b.slice(0, 90).replace(/\n/g, '|');
  });

  await t('[D3] 翻第 2 页 2 笔', async () => {
    await page.click('button:has-text("下一页")');
    await page.waitForTimeout(1100);
    const n = await page.locator('.order-row').count();
    return n === 2 && (await body()).includes('第 2 / 2 页') ? 'PASS' : 'FAIL rows=' + n;
  });

  await t('[D4] 列表按下单时间倒序', async () => {
    await page.goto(BASE + '/orders', { waitUntil: 'networkidle' });
    await page.waitForTimeout(1100);
    const first = await page.locator('.order-row').first().innerText();
    // 造的数据是 07:00 起，真实下单是刚才（更晚），所以首条应是真实订单而非 SOFILL
    return !first.includes('SOFILL') ? 'PASS 首条是最近真实下单' : 'FAIL ' + first.replace(/\n/g, '|').slice(0, 60);
  });

  await t('[D5] 状态筛选「已发货」2 笔', async () => {
    await page.selectOption('.filter select', 'SHIPPED');
    await page.waitForTimeout(1200);
    const n = await page.locator('.order-row').count();
    return n === 2 ? 'PASS 2 笔' : 'FAIL ' + n;
  });

  await t('[D6] 切筛选回第 1 页', async () => {
    await page.selectOption('.filter select', 'CONFIRMED');
    await page.waitForTimeout(1200);
    return (await body()).includes('第 1 / 1 页') ? 'PASS' : 'FAIL ' + (await body()).slice(0, 70).replace(/\n/g, '|');
  });

  await t('[D7] 详情页两块数据独立加载（明细二次请求）', async () => {
    await page.goto(BASE + '/orders', { waitUntil: 'networkidle' });
    await page.waitForTimeout(900);
    await page.selectOption('.filter select', '');
    await page.waitForTimeout(1100);
    let itemsReq = 0;
    page.on('request', (r) => {
      if (/\/api\/buyer\/orders\/[^/]+\/items/.test(r.url())) itemsReq += 1;
    });
    await page.locator('.order-row').first().click();
    await page.waitForTimeout(1800);
    const b = await body();
    if (!b.includes('明细') && !b.includes('商品')) return 'FAIL 详情未渲染';
    return itemsReq >= 1 ? 'PASS 订单头 + 明细=' + itemsReq + ' 次独立请求' : 'FAIL 未见明细请求';
  });

  await t('[D8] 取消订单二次确认 → 已取消', async () => {
    await page.goto(BASE + '/orders', { waitUntil: 'networkidle' });
    await page.waitForTimeout(900);
    await page.selectOption('.filter select', 'PENDING_CONFIRM');
    await page.waitForTimeout(1200);
    const n = await page.locator('.order-row').count();
    if (n === 0) return 'SKIP 无待确认订单';
    await page.locator('.order-row').first().click();
    await page.waitForTimeout(1600);
    const id = page.url().split('/orders/')[1];
    await page.click('button:has-text("取消订单")');
    await page.waitForTimeout(500);
    const confirmShown = (await body()).includes('确认取消');
    await page.click('button:has-text("确认取消")');
    await page.waitForTimeout(1800);
    const st = SQL(`SELECT status FROM simpleshop_order WHERE id='${id}'`);
    return confirmShown && st === 'CANCELED' && (await body()).includes('已取消')
      ? 'PASS 二次确认 + 数据库 CANCELED' : `FAIL confirm=${confirmShown} st=${st}`;
  });

  await t('[D9] 已发货订单无取消入口', async () => {
    await page.goto(BASE + '/orders', { waitUntil: 'networkidle' });
    await page.waitForTimeout(900);
    await page.selectOption('.filter select', 'SHIPPED');
    await page.waitForTimeout(1200);
    await page.locator('.order-row').first().click();
    await page.waitForTimeout(1600);
    const btn = await page.locator('button:has-text("取消订单")').count();
    return btn === 0 ? 'PASS 无取消按钮' : 'FAIL 仍有按钮';
  });

  await t('[D10] 打开后被发货→点取消提示已发货', async () => {
    await page.goto(BASE + '/orders', { waitUntil: 'networkidle' });
    await page.waitForTimeout(900);
    const bid = SQL(`SELECT id FROM simpleshop_buyer_account WHERE username='${user}'`);
    SQL(`UPDATE simpleshop_order SET status='PENDING_CONFIRM' WHERE order_no='SOFILL0001'`);
    await page.goto(BASE + '/orders', { waitUntil: 'networkidle' });
    await page.waitForTimeout(900);
    await page.selectOption('.filter select', 'PENDING_CONFIRM');
    await page.waitForTimeout(1200);
    await page.locator('.order-row').first().click();
    await page.waitForTimeout(1600);
    const id = page.url().split('/orders/')[1];
    SQL(`UPDATE simpleshop_order SET status='SHIPPED' WHERE id='${id}'`);
    await page.click('button:has-text("取消订单")');
    await page.waitForTimeout(400);
    await page.click('button:has-text("确认取消")');
    await page.waitForTimeout(1800);
    const b = await body();
    return b.includes('已发货，不能取消') ? 'PASS 提示可读' : 'FAIL ' + b.slice(0, 120).replace(/\n/g, '|');
  });

  // ==================== E 组：收藏 ====================
  await t('[E1] 详情页收藏', async () => {
    await page.goto(BASE + '/goods/GD-FAV-01', { waitUntil: 'networkidle' });
    await page.waitForTimeout(600);
    const before = (await page.innerText('button.fav')).trim();
    await page.click('button.fav');
    await page.waitForTimeout(1000);
    const after = (await page.innerText('button.fav')).trim();
    return before === '收藏' && after === '已收藏' ? 'PASS 收藏→已收藏' : `FAIL ${before}→${after}`;
  });

  await t('[E2] 刷新后收藏状态回显', async () => {
    await page.reload({ waitUntil: 'networkidle' });
    await page.waitForTimeout(1000);
    return (await page.innerText('button.fav')).trim() === '已收藏' ? 'PASS' : 'FAIL';
  });

  await t('[E3] 收藏列表显示', async () => {
    await page.goto(BASE + '/favorites', { waitUntil: 'networkidle' });
    await page.waitForTimeout(900);
    const n = await page.locator('.fav-card').count();
    return n === 1 && (await body()).includes('收藏测试商品1') ? 'PASS 1 件' : 'FAIL ' + n;
  });

  await t('[E4] 失效商品保留并标注', async () => {
    SQL(`UPDATE simpleshop_goods SET status='OFF_SHELF', off_shelf_reason='MANUAL', off_shelf_at=UTC_TIMESTAMP() WHERE id='GD-FAV-01'`);
    await page.reload({ waitUntil: 'networkidle' });
    await page.waitForTimeout(1000);
    const card = page.locator('.fav-card').first();
    const txt = await card.innerText();
    const dis = await card.evaluate((el) => el.classList.contains('disabled'));
    return txt.includes('已下架') && dis ? 'PASS 保留+标注+灰掉' : `FAIL txt=${txt.replace(/\n/g, '|').slice(0, 50)}`;
  });

  await t('[E5] 取消收藏', async () => {
    await page.locator('.fav-card').first().locator('button.remove').click();
    await page.waitForTimeout(1400);
    const n = SQL(`SELECT COUNT(*) FROM simpleshop_favorite f JOIN simpleshop_buyer_account b ON b.id=f.fk_buyer_id WHERE b.username='${user}'`);
    return n === '0' ? 'PASS 数据库 0 条' : 'FAIL ' + n;
  });
  SQL(`UPDATE simpleshop_goods SET status='ON_SALE', off_shelf_reason=NULL, off_shelf_at=NULL WHERE id='GD-FAV-01'`);

  await t('[E6] 购物车转收藏', async () => {
    await addToCart('GD-D1-ON');
    await addToCart('GD-FAV-02');
    await page.goto(BASE + '/cart', { waitUntil: 'networkidle' });
    await page.waitForTimeout(900);
    await page.locator('.bar .check-all input').check();
    await page.waitForTimeout(400);
    const cartBefore = cartRows();
    await page.click('button.to-fav');
    await page.waitForTimeout(2200);
    const cartAfter = cartRows();
    const favs = SQL(`SELECT COUNT(*) FROM simpleshop_favorite f JOIN simpleshop_buyer_account b ON b.id=f.fk_buyer_id WHERE b.username='${user}'`);
    const b = await body();
    return Number(cartBefore) >= 2 && cartAfter === '0' && Number(favs) >= 2 && b.includes('移到收藏')
      ? `PASS 购物车 ${cartBefore}→0，收藏 ${favs} 条` : `FAIL cart ${cartBefore}→${cartAfter} fav=${favs}`;
  });

  await t('[E7] 转收藏后收藏页可见', async () => {
    await page.goto(BASE + '/favorites', { waitUntil: 'networkidle' });
    await page.waitForTimeout(1000);
    const b = await body();
    return b.includes('测试耳机在售') && b.includes('收藏测试商品2') ? 'PASS 两件都在' : 'FAIL ' + b.slice(0, 90).replace(/\n/g, '|');
  });

  // ==================== 汇总 ====================
  const groups = [
    ['A 组 · 未登录', 0, 10],
    ['B 组 · 购物车', 10, 22],
    ['C 组 · 下单', 22, 27],
    ['D 组 · 订单', 27, 37],
    ['E 组 · 收藏', 37, 44],
  ];
  console.log('\n================ 阶段D 全量回归（§9.1 清单）================');
  for (const [title, from, to] of groups) {
    const slice = results.slice(from, to);
    if (!slice.length) continue;
    const p = slice.filter(([, r]) => ok(r)).length;
    console.log('\n--- ' + title + ' ' + p + '/' + slice.length + ' ---');
    slice.forEach(([n, r]) => console.log('  ' + (ok(r) ? '✔' : '✘') + ' ' + n.padEnd(38) + ' ' + r));
  }

  const bad = results.filter(([, r]) => !ok(r));
  console.log('\n============================================================');
  console.log('总计 ' + (results.length - bad.length) + '/' + results.length + ' 通过');
  console.log('页面级报错 ' + pageErrors.length + ' 条');
  pageErrors.slice(0, 8).forEach((e) => console.log('  ' + e));

  await browser.close();
  cleanupUser(user);
  resetFixtures();
  console.log('测试账号 ' + user + ' 及其订单/购物车/收藏已清理');

  console.log('\nRESULT: ' + (bad.length === 0 ? 'ALL CHECKS PASSED' : 'FAILED'));
  process.exitCode = bad.length === 0 ? 0 : 1;
})().catch((e) => {
  console.error('SCRIPT FAIL:', e.message);
  process.exit(1);
});
