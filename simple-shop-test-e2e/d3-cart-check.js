/**
 * 购物车（D3）端到端验证：不只读页面，每一步都回数据库对账。
 *
 * 为什么要对账：只看页面文字，无法区分「页面自己算对了」和「后端真的收到了」。
 * 这里把页面显示的合计、数据库里的订单总额、订单明细之和三方放在一起比。
 *
 * 前提：后端跑在 8080、前端跑在 5174、用的是 simple_shop_d1 测试库。
 * 用法：node d3-cart-check.js
 */
const { chromium } = require('playwright')
const db = require('./scripts/db')

// 地址与 Playwright 共用 .env 里的 BUYER_URL；旧名 BUYER_BASE_URL 仍兼容。
const BASE = process.env.BUYER_URL || process.env.BUYER_BASE_URL || 'http://localhost:5174'
/**
 * 夹具库（.env 的 E2E_FIXTURE_DB_NAME），不是 E2E 主库 simple_shop_e2e：
 * 本脚本要用的 GD-D1-* 商品在夹具库里，两套数据不能混。
 */
const DB_NAME = process.env.E2E_FIXTURE_DB_NAME || process.env.TEST_DB || 'simple_shop_d1'

/**
 * 单值查询：走 node 直连（scripts/db.js），不再拼 `mysql -e` 过 cmd.exe。
 * 返回字符串（NULL → 空串），与原来 `mysql -N -B` 的输出口径一致，
 * 所以下面所有 `=== '1'` / `=== '230.00'` 的断言不需要改。
 * 库名固定为 TEST_DB，避免与 E2E 主库 simple_shop_e2e 混淆。
 */
const sql = (q) => db.scalar(q, [], { database: DB_NAME })

const log = []
const step = (s) => log.push(s)

async function main() {
  // 先探活：连不上库时立刻停下，别等到登录完才发现对账查不了。
  const health = await db.probe({ database: DB_NAME })
  if (!health.ok) {
    console.error(`[E2E] 连不上数据库 ${health.target}：${health.reason}`)
    process.exit(2)
  }

  const browser = await chromium.launch({ channel: 'msedge' })
  const ctx = await browser.newContext()
  const page = await ctx.newPage()
  page.setDefaultTimeout(15000)

  const errs = []
  page.on('pageerror', (e) => errs.push('pageerror: ' + e.message))
  page.on('console', (m) => {
    if (m.type() === 'error') errs.push(m.text().slice(0, 120))
  })

  const body = () => page.innerText('body')
  const total = async () => (await page.innerText('.total strong')).replace(/\s/g, '')
  const user = 'verify' + Date.now().toString().slice(-6)
  const byUser = `JOIN simpleshop_buyer_account b ON b.id=c.fk_buyer_id WHERE b.username='${user}'`

  // 1. 注册
  await page.goto(BASE + '/register', { waitUntil: 'networkidle' })
  const ins = page.locator('input')
  await ins.nth(0).fill(user)
  await ins.nth(1).fill('Abcd1234')
  await ins.nth(2).fill('13900001111')
  await ins.nth(3).fill('验证地点')
  await page.click('button.primary')
  await page.waitForTimeout(1800)
  const dbUser = await sql(`SELECT COUNT(*) FROM simpleshop_buyer_account WHERE username='${user}'`)
  step(`[1] 注册 ${user} → 数据库查到该账号 ${dbUser} 条`)

  // 2. 加购两件
  for (const id of ['GD-D1-ON', 'GD-D1-ON3']) {
    await page.goto(BASE + '/goods/' + id, { waitUntil: 'networkidle' })
    await page.click('button:has-text("加入购物车")')
    await page.waitForTimeout(900)
  }
  const cartCount = await sql(`SELECT COUNT(*) FROM simpleshop_cart_item c ${byUser}`)
  const cartGoods = await sql(
    `SELECT GROUP_CONCAT(c.fk_goods_id SEPARATOR ',') FROM simpleshop_cart_item c ${byUser}`,
  )
  step(`[2] 加购 2 件 → 数据库购物车 ${cartCount} 行：[${cartGoods}]`)

  await page.goto(BASE + '/cart', { waitUntil: 'networkidle' })
  await page.waitForTimeout(800)
  const uiRows = await page.locator('.row-item').count()
  const uiTotal0 = await total()
  step(`[3] 页面渲染 ${uiRows} 行，合计 ${uiTotal0}（应 = 199.00 + 15.50 = 214.50）`)

  // 3. 改数量 + 取消勾选
  const wire = page.locator('.row-item', { hasText: '测试数据线在售' })
  await wire.locator('.qty-input').fill('2')
  await wire.locator('.qty-input').blur()
  await page.waitForTimeout(400)
  await page.locator('.row-item', { hasText: '测试耳机在售' }).locator('.row-check').uncheck()
  await page.waitForTimeout(400)
  const uiTotal1 = await total()
  const cartCount2 = await sql(`SELECT COUNT(*) FROM simpleshop_cart_item c ${byUser}`)
  const qtyCols = await sql(
    `SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${DB_NAME}' AND table_name='simpleshop_cart_item' AND column_name IN ('quantity','checked')`,
  )
  step(`[4] 改数量=2、取消勾选耳机 → 页面合计 ${uiTotal1}（应 = 15.50×2 = 31.00）`)
  step(`    同时数据库购物车仍 ${cartCount2} 行；购物车表数量/勾选列数 = ${qtyCols}（0 = 后端确实不存）`)

  // 4. 勾回来
  await page.locator('.row-item', { hasText: '测试耳机在售' }).locator('.row-check').check()
  await page.waitForTimeout(400)
  const uiTotal2 = await total()
  step(`[5] 勾回耳机 → 页面合计 ${uiTotal2}（应 = 199×1 + 15.50×2 = 230.00）`)

  // 5. 结算
  const orderBefore = await sql(
    `SELECT COUNT(*) FROM simpleshop_order o JOIN simpleshop_buyer_account b ON b.id=o.fk_buyer_id WHERE b.username='${user}'`,
  )
  await page.click('button.checkout')
  await page.waitForTimeout(2500)
  const uiAfter = await body()
  const orderAfter = await sql(
    `SELECT COUNT(*) FROM simpleshop_order o JOIN simpleshop_buyer_account b ON b.id=o.fk_buyer_id WHERE b.username='${user}'`,
  )
  const orderNo = (uiAfter.match(/订单号\s*(SO\d+)/) || [])[1] || '(未捕获)'
  const dbTotal = await sql(`SELECT total_amount FROM simpleshop_order WHERE order_no='${orderNo}'`)
  const dbItems = await sql(
    `SELECT GROUP_CONCAT(CONCAT(i.goods_name,' ',i.unit_price,'x',i.quantity) SEPARATOR ' + ') FROM simpleshop_order_item i JOIN simpleshop_order o ON o.id=i.fk_order_id WHERE o.order_no='${orderNo}'`,
  )
  const dbSum = await sql(
    `SELECT SUM(i.unit_price*i.quantity) FROM simpleshop_order_item i JOIN simpleshop_order o ON o.id=i.fk_order_id WHERE o.order_no='${orderNo}'`,
  )
  step(`[6] 点结算 → 页面出现订单号 ${orderNo}`)
  step(`    数据库订单数 ${orderBefore} → ${orderAfter}；该订单 total_amount = ${dbTotal}`)
  step(`    数据库明细：${dbItems}；明细金额之和 = ${dbSum}`)

  // 6. 收尾
  const cartAfter = await sql(`SELECT COUNT(*) FROM simpleshop_cart_item c ${byUser}`)
  const uiEmpty = (await body()).includes('购物车是空的')
  step(`[7] 下单后 → 页面空态=${uiEmpty}；数据库购物车剩 ${cartAfter} 行`)

  const checks = [
    ['注册后数据库有该账号', dbUser === '1'],
    ['数据库购物车 2 行', cartCount === '2'],
    ['页面渲染 2 行', uiRows === 2],
    ['页面合计 214.50', uiTotal0 === '¥214.50'],
    ['改数量后 31.00', uiTotal1 === '¥31.00'],
    ['购物车表无数量/勾选列', qtyCols === '0'],
    ['勾回后 230.00', uiTotal2 === '¥230.00'],
    ['订单新增 1 笔', Number(orderAfter) - Number(orderBefore) === 1],
    ['订单总额 230.00', dbTotal === '230.00'],
    ['明细之和 230.00', dbSum === '230.00'],
    ['页面金额 == 后端金额', uiTotal2 === '¥' + dbTotal],
    ['下单后购物车清空', cartAfter === '0' && uiEmpty === true],
  ]

  console.log('=== 逐步证据 ===')
  log.forEach((l) => console.log('  ' + l))
  console.log('\n=== 判定 ===')
  let pass = 0
  for (const [n, ok] of checks) {
    if (ok) pass++
    console.log('  ' + (ok ? '[OK]  ' : '[FAIL]') + ' ' + n)
  }
  console.log(`\n${pass}/${checks.length} 通过；页面级报错 ${errs.length} 条`)
  errs.slice(0, 5).forEach((e) => console.log('  ' + e))

  await browser.close()
  await db.close()
  process.exit(pass === checks.length ? 0 : 1)
}

main().catch(async (e) => {
  console.error('脚本异常：', e.message)
  await db.close()
  process.exit(2)
})
