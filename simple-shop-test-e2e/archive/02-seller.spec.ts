/**
 * 卖家端验收用例（验收方式 V-03 页面走查 + V-04 端到端演练）
 *
 * ⚠️ 本组用例是【一条有序剧本】，不能乱序、不能单独跑中间的某一条：
 *    系统只有 1 个商品槽位（INV-01），「标记成功」或「下架」后商品即归档，
 *    后续用例失去前置条件。故整体 serial。
 *
 * 覆盖的验收标准：
 *   FR-01     登录                              → S1、S2
 *   FR-02     修改密码页（P10-10 走查，不真改密）  → S16（2026-10-14 补）
 *   FR-03     发布商品（P10-06 走查 + 实发一件）   → S14（2026-10-14 补）
 *   FR-04     查看当前商品（含冻结来源）         → S3、S5、S8
 *   FR-05/PS-02 手动冻结                        → S5
 *   FR-06/PS-05 手动解冻                        → S7
 *   FR-07/PS-01/IS-02 进入交易                  → S8、S11
 *   FR-08     查看意向名单                      → S4
 *   FR-09/PS-03/IS-04/IS-05 标记交易成功        → S11
 *   FR-10/PS-04/IS-07 标记失败 + 重新排队        → S10
 *   FR-10/PS-04/IS-06 标记失败 + 作废            → S10b（2026-10-14 补）
 *   FR-11     查看历史商品                      → S12、S13
 *   AC-07     商品冻结/解冻前后口令码查询结果完全一致 → S4b + S6b + S7（P0）
 *   AC-08     排队中口令码必然有效                  → S10
 *   AC-10     交易冻结期买家端提示 M10-01        → S6
 *   AC-11     M10-08「您已进入交易」             → S9
 *   AC-14     历史中每次交易结果可查             → S13
 *   AC-15     时间字段展示口径                   → S12
 *   AC-16     重新排队：口令码保持有效            → S10
 *   AC-21     队列非空禁下架 ／ 队列为空可下架     → S4、S15（S15 为 2026-10-14 补，V-04 点名）
 *   FR-23/PS-08 手动下架（队列为空 → 归档）       → S15（2026-10-14 补）
 */
import { test, expect } from '@playwright/test';
import { BUYER_URL, SELLER_URL } from '../helpers/config';
import { newApi, ensureOnSaleProduct, apiSubmitIntention } from '../helpers/api';
import { uiLogin, confirmDialog, buyerQuery } from '../helpers/ui';

// 每次跑都用唯一的买家姓名，避免与历史数据混淆（配合「先复位测试库」双保险）
const STAMP = Date.now().toString(36).slice(-5).toUpperCase();
const NAME_A = `剧本买家甲${STAMP}`;
const NAME_B = `剧本买家乙${STAMP}`;
const NAME_C = `剧本买家丙${STAMP}`; // 丙：承担 IS-06（标记失败 + 作废），这样甲/乙仍能走「成交 / 商品已售出」
const TEL_A = '13900001111';
const TEL_B = '13900002222';
const TEL_C = '13900003333';

test.describe.serial('卖家端 · 完整业务流程', () => {
  let codeA = ''; // 甲的口令码（用来验证「重新排队后仍有效」「冻结前后查询一致」）
  let codeB = '';
  let codeC = ''; // 丙的口令码（IS-06 作废后应失效）
  let productName = ''; // 本次剧本操作的这件商品（历史页用它精确定位）
  let textBeforeFreeze = ''; // AC-07 基线：手动冻结【之前】用甲的口令码查询到的结果区文本
  let publishedName = ''; // S14 通过界面发布的第二件商品（供 S15 手动下架定位）

  test.beforeAll(async () => {
    // ① 确保有在售商品（测试库为空或上次已归档时会自动发布）
    const api = await newApi();
    const { product } = await ensureOnSaleProduct(api);
    await api.dispose();
    expect(product, '前置失败：测试库无法准备在售商品').toBeTruthy();
    expect(product.status, `前置失败：商品状态为 ${product.status}，需 on_sale`).toBe('on_sale');
    productName = product.name;

    // ② 造三条买家意向，作为后续剧本的数据（走接口，快且稳；
    //    「用界面提交意向」已由买家端用例 A2 覆盖，此处只是前置准备）
    codeA = await apiSubmitIntention(NAME_A, TEL_A);
    codeB = await apiSubmitIntention(NAME_B, TEL_B);
    codeC = await apiSubmitIntention(NAME_C, TEL_C);
    expect(codeA).toMatch(/^[A-Z0-9]{12}$/);
    expect(codeB).toMatch(/^[A-Z0-9]{12}$/);
    expect(codeC).toMatch(/^[A-Z0-9]{12}$/);
  });

  test('S1 未登录访问后台 → 自动跳转登录页', async ({ page }) => {
    await page.goto(`${SELLER_URL}/`);
    await page.waitForURL(/\/login/, { timeout: 15_000 });
    await expect(page.locator('#account')).toBeVisible();
    await expect(page.locator('#password')).toBeVisible();
  });

  test('S2 卖家登录 → 进入后台首页（FR-01）', async ({ page }) => {
    await uiLogin(page);
    expect(page.url().replace(/\/$/, ''), '登录后应停在后台首页').toBe(SELLER_URL);
    await expect(page.locator('.page__title')).toContainText('当前商品');
  });

  test('S3 商品卡片展示当前商品与状态（FR-04）', async ({ page }) => {
    await uiLogin(page);
    const card = page.locator('.product-card');
    await expect(card).toBeVisible();
    await expect(card.locator('.product-card__name')).not.toBeEmpty();
    await expect(card).toContainText('在售');
    // 发布时间存在（C-10/C-11：UTC 存储、东八区展示）
    await expect(card).toContainText('发布时间');
  });

  test('S4 意向名单展示排队买家；队列非空时「手动下架」禁用（FR-08、AC-21）', async ({
    page,
  }) => {
    await uiLogin(page);

    // 注意：名单里可能还留着之前跑过的「已撤销」等终态行，故按买家姓名精确定位
    const rowA = page.locator('.intention-table tbody tr').filter({ hasText: NAME_A });
    const rowB = page.locator('.intention-table tbody tr').filter({ hasText: NAME_B });
    await expect(rowA).toHaveCount(1);
    await expect(rowB).toHaveCount(1);

    // 两位买家状态均为「排队中」
    await expect(rowA).toContainText('排队中');
    await expect(rowB).toContainText('排队中');
    // 位次列（第 1 列）：甲为队首 = 1
    await expect(rowA.locator('td').first()).toHaveText('1');

    // 队列非空 ⇒ 下架按钮禁用，并带提示（M10-16，实现方式为 title 属性）
    const offline = page.getByRole('button', { name: '手动下架' });
    await expect(offline).toBeDisabled();
    await expect(offline).toHaveAttribute('title', /队列中仍有买家/);
  });

  /* ── AC-07 参照组：先取「商品状态变化【之前】」的查询结果作为基线 ── */
  test('S4b 冻结前基线：队列中的买家凭口令码查到「排第 N 位」（AC-07 步骤①②）', async ({
    page,
  }) => {
    textBeforeFreeze = await buyerQuery(page, codeA);
    expect(textBeforeFreeze, '冻结前：甲应为排队中，并显示位次').toMatch(/排第\s*\d+\s*位/);
    expect(textBeforeFreeze).not.toContain('失效');
  });

  test('S5 手动冻结 → 商品转为「已冻结/手动冻结」（FR-05、PS-02）', async ({ page }) => {
    await uiLogin(page);
    await page.getByRole('button', { name: '手动冻结' }).click();
    await confirmDialog(page);

    const header = page.locator('.product-card__header');
    await expect(header).toContainText('已冻结');
    await expect(header).toContainText('手动冻结');
  });

  test('S6 冻结期买家端：提示 M10-01，提交入口置灰但保留（AC-10、UX-06）', async ({ page }) => {
    await page.goto(`${BUYER_URL}/`);

    // ① 提示文案可见
    await expect(page.locator('.notice--warn')).toContainText('商品交易中，暂停接收新意向');

    // ② 提交入口【保留】但置灰（10-B：不隐藏，让买家知道商品还在）
    const submit = page.locator('a[href="/intent/new"]');
    await expect(submit, '冻结时提交入口应保留（不隐藏）').toBeVisible();
    await expect(submit).toHaveAttribute('aria-disabled', 'true');

    // ③ 商品详情仍可浏览（BR-14）
    await expect(page.locator('.card__title')).toBeVisible();
  });

  /* ── AC-07（P0）：口令码有效性只与所属意向是否终态相关，与商品状态无关 ──
     验收步骤 ③④：卖家手动冻结商品后，再用【同一个】口令码查询，
     结果必须与冻结【前完全一致】（仍为有效口令 + 同一位次）。          */
  test('S6b 手动冻结期间：口令码仍有效，且查询结果与冻结前完全一致（AC-07、INV-07、BR-26）', async ({
    page,
  }) => {
    expect(textBeforeFreeze, '前置：S4b 应已取得冻结前的基线文本').not.toBe('');

    const afterFreeze = await buyerQuery(page, codeA);

    expect(
      afterFreeze,
      'AC-07 判据：口令码返回结果在商品状态变化前后必须【完全一致】（INV-07）',
    ).toBe(textBeforeFreeze);
    expect(afterFreeze, '冻结不得让队列中已有买家的口令码失效').not.toContain('失效');
  });

  test('S7 手动解冻 → 恢复「在售」（FR-06、PS-05、AC-07 步骤⑤）', async ({ page }) => {
    await uiLogin(page);
    await page.getByRole('button', { name: '手动解冻' }).click();

    const header = page.locator('.product-card__header');
    await expect(header).toContainText('在售');
    // 提交入口恢复可用
    await page.goto(`${BUYER_URL}/`);
    await expect(page.locator('a[href="/intent/new"]')).not.toHaveAttribute('aria-disabled', 'true');

    // ⑤ AC-07 步骤⑤：解冻【之后】再用同一个口令码查询 → 仍与冻结前完全一致。
    //    「商品状态变化前后完全一致」里的「变化」含【解冻】这一步，故不能只验冻结中。
    const afterUnfreeze = await buyerQuery(page, codeA);
    expect(
      afterUnfreeze,
      'AC-07 判据：口令码返回结果在商品状态变化（含解冻）前后必须【完全一致】',
    ).toBe(textBeforeFreeze);
  });

  test('S8 进入交易（仅队首）→ 商品转为交易冻结（FR-07、PS-01、IS-02、AC-22）', async ({
    page,
  }) => {
    await uiLogin(page);

    // 「进入交易」按钮只应出现在队首那一行（其余行显示「—」）
    const enterBtns = page.getByRole('button', { name: '进入交易' });
    await expect(enterBtns).toHaveCount(1);

    await enterBtns.click();
    await confirmDialog(page);

    // 商品：已冻结 + 交易冻结
    const header = page.locator('.product-card__header');
    await expect(header).toContainText('已冻结');
    await expect(header).toContainText('交易冻结');

    // 该意向转为「交易中」
    await expect(page.locator('.intention-table')).toContainText('交易中');

    // 交易冻结时「手动解冻」必须禁用（PS-06、AC-20）
    const unfreeze = page.getByRole('button', { name: '手动解冻' });
    await expect(unfreeze).toBeDisabled();
    await expect(unfreeze).toHaveAttribute('title', /交易中的商品不可解冻/);
  });

  test('S9 交易中的买家凭口令码查到「您已进入交易」（AC-11、M10-08）', async ({ page }) => {
    const text = await buyerQuery(page, codeA);
    expect(text, '交易中应显示 M10-08').toContain('您已进入交易');
  });

  test('S10 标记失败 + 重新排队 → 回到排队中且口令码仍有效（FR-10、IS-07、AC-16、AC-08）', async ({
    page,
  }) => {
    await uiLogin(page);

    // 交易中的行 → 「标记失败」按钮
    await page.getByRole('button', { name: '标记失败' }).click();
    const dlg = page.getByRole('dialog');
    await expect(dlg).toContainText('标记交易失败');

    // 选「重新排队」（不是作废）
    await page.locator('input[type="radio"][value="requeued"]').check();
    await page.fill('#fail-reason', '自动化验收测试：模拟一次失败的交易');
    await dlg.getByRole('button', { name: '确认标记失败' }).click();

    // 商品回到「在售」
    await expect(page.locator('.product-card__header')).toContainText('在售');

    // ⭐ 关键：口令码仍有效，且仍显示排队位次（BR-27、AC-16）
    const text = await buyerQuery(page, codeA);
    expect(text, '重新排队后口令码必须仍然有效').toMatch(/排第\s*\d+\s*位/);
    expect(text).not.toContain('失效');
  });

  /* ── IS-06（补缺）：标记失败 +【作废】→ 交易中 → 交易失败（终态） ──
     S10 走的是「重新排队」分支（IS-07）；作废是同一个弹窗里的另一条分支，必须单独覆盖。
     此时队列 = 乙(1)、丙(2)、甲(3)（甲在 S10 重排后到队尾），故对队首【乙】操作。 */
  test('S10b 标记失败 + 作废 → 意向转终态、口令码失效，商品回到在售（IS-06、PS-04、BR-26）', async ({
    page,
  }) => {
    await uiLogin(page);

    // 对队首（乙）发起交易（IS-02 / PS-01）
    await page.getByRole('button', { name: '进入交易' }).click();
    await confirmDialog(page);
    await expect(page.locator('.product-card__header')).toContainText('交易冻结');

    // 交易中 → 标记失败，处理方式选「作废」（与 S10 的「重新排队」相对）
    await page.getByRole('button', { name: '标记失败' }).click();
    const dlg = page.getByRole('dialog');
    await expect(dlg).toContainText('标记交易失败');
    await page.locator('input[type="radio"][value="voided"]').check();
    await page.fill('#fail-reason', '自动化验收测试：模拟一次卖家作废');
    await dlg.getByRole('button', { name: '确认标记失败' }).click();

    // ① 商品回到「在售」（PS-04：标记失败 ⇒ 已冻结 → 在售）
    await expect(page.locator('.product-card__header')).toContainText('在售');

    // ② 该意向进入终态 ⇒ 乙的口令码必须失效（IS-06 的必然结果，BR-26）
    const text = await buyerQuery(page, codeB);
    expect(text, '作废后乙的口令码必须失效').toContain('失效');
  });

  test('S11 对队首（丙）进入交易并标记成功 → 商品下架归档；甲转「商品已售出」（FR-09、PS-03、IS-04、IS-05）', async ({
    page,
  }) => {
    await uiLogin(page);

    await page.getByRole('button', { name: '进入交易' }).click();
    await confirmDialog(page);

    await page.getByRole('button', { name: '标记成功' }).click();
    await confirmDialog(page);

    // 商品已归档 ⇒ 当前商品页回到空态
    await expect(page.locator('.empty')).toContainText('当前没有商品');
  });

  test('S12 历史商品列表：展示「发布时间 + 交易结束时间」，不展示交易开始时间（FR-11、AC-15）', async ({
    page,
  }) => {
    await uiLogin(page);
    await page.goto(`${SELLER_URL}/history`);
    await expect(page.locator('.page__title')).toContainText('历史商品');

    const table = page.locator('table');
    await expect(table).toBeVisible();
    // 历史里可能累积多次跑的商品，用本次商品名精确定位
    const row = page.locator('table tbody tr').filter({ hasText: productName });
    await expect(row).toHaveCount(1);

    const head = await table.locator('thead').innerText();
    expect(head).toContain('发布时间');
    expect(head, '列表必须展示交易结束时间（澄清 Q22）').toContain('交易结束时间');
    expect(head, '列表不得展示交易开始时间').not.toContain('交易开始时间');
  });

  test('S13 历史详情：买家名单含交易记录，每次失败都可查（FR-08、FR-11、AC-14）', async ({
    page,
  }) => {
    await uiLogin(page);
    await page.goto(`${SELLER_URL}/history`);

    // 点开本次剧本产生的这件商品
    await page
      .locator('table tbody tr')
      .filter({ hasText: productName })
      .getByRole('button')
      .click();
    await page.waitForURL(/\/history\//, { timeout: 15_000 });

    await expect(page.locator('.page__title')).toContainText('历史商品详情');
    const body = page.locator('body');
    // 三位买家都在历史名单里（名单必须包含每一种结果类型）
    await expect(body).toContainText(NAME_A);
    await expect(body).toContainText(NAME_B);
    await expect(body).toContainText(NAME_C);

    // 交易结果覆盖：
    //   丙 = 交易成功（IS-05 本人成交）
    //   甲 = 商品已售出（IS-04 他人成交，系统批量转终态）
    //   乙 = 交易失败 + 卖家作废（IS-06 标记失败 + 作废）
    await expect(body).toContainText('交易成功');
    await expect(body).toContainText('商品已售出');
    await expect(body).toContainText('交易失败');
    await expect(body).toContainText('卖家作废');
  });

  /* ══════════════════════════════════════════════════════════
     四、收尾段：发布商品 → 手动下架 → 修改密码（2026-10-14 补）
     依据：第 13 章 §13.2.2 `V-03` 页面走查（卖家端 6 页）+ `V-04` 端到端点名「手动下架」
     ══════════════════════════════════════════════════════════ */

  test('S14 发布商品页（P10-06）：字段走查 + 二次确认 → 发布成功（FR-03、V-03）', async ({
    page,
  }) => {
    await uiLogin(page);

    // 上一件商品已在 S11 归档 ⇒ 当前无商品，发布入口可用（INV-01 / BR-11 的前置）
    await expect(page.locator('.empty')).toContainText('当前没有商品');

    await page.goto(`${SELLER_URL}/publish`);
    await expect(page.locator('.page__title')).toContainText('发布商品');

    // ① 字段走查（P10-06：名称／描述／图片／价格）
    await expect(page.locator('#goods-name')).toBeVisible();
    await expect(page.locator('#goods-description')).toBeVisible();
    await expect(page.locator('#goods-image')).toBeVisible();
    await expect(page.locator('#goods-price')).toBeVisible();

    // ② 填表发布（图片为可选，此处不选图）
    const newName = `界面发布商品${STAMP}`;
    await page.fill('#goods-name', newName);
    await page.fill('#goods-description', '由自动化验收测试通过界面发布');
    await page.fill('#goods-price', '128.50');
    await page.getByRole('button', { name: '发布', exact: true }).click();

    // ③ 二次确认弹窗：四字段发布后不可修改（对应 BR-15、AC-24③「无编辑入口」的口径）
    const dlg = page.getByRole('dialog');
    await expect(dlg).toContainText('发布后名称、描述、图片、价格不可修改');
    await dlg.getByRole('button', { name: '确认', exact: true }).click();

    // ④ 发布成功 ⇒ 自动回到首页，卡片显示新商品且在售
    await page.waitForURL((url) => !url.pathname.includes('/publish'), { timeout: 15_000 });
    const card = page.locator('.product-card');
    await expect(card).toContainText(newName);
    await expect(card).toContainText('在售');

    publishedName = newName;
  });

  /* `AC-21` 判据第②步：**队列为空时** `I11-09` → 成功，商品进入 `off_sale` 并归档。
     `V-04` 点名演练项之一，此前只覆盖了负向（`S4` 禁用态 + `D3` 绕过前端被拒）。 */
  test('S15 队列为空时「手动下架」→ 成功并归档（AC-21②、PS-08、V-04 点名）', async ({
    page,
  }) => {
    await uiLogin(page);

    // 前置：刚发布的商品名下【没有任何意向】⇒ 下架按钮必须可用
    await expect(page.locator('.intention-table tbody tr')).toHaveCount(0);
    const offline = page.getByRole('button', { name: '手动下架' });
    await expect(offline, '队列为空时「手动下架」必须可用（AC-21②）').toBeEnabled();

    await offline.click();
    await confirmDialog(page);

    // ① 商品归档 ⇒ 当前商品页回到空态
    await expect(page.locator('.empty')).toContainText('当前没有商品');

    // ② 历史列表出现这件商品，结果为「手动下架」（`result = offline`，见 `AC-06` 的 result 口径）
    await page.goto(`${SELLER_URL}/history`);
    const row = page.locator('table tbody tr').filter({ hasText: publishedName });
    await expect(row).toHaveCount(1);
    await expect(row).toContainText('手动下架');
  });

  /* `V-03` 对页面的走查口径是「字段／按钮可用条件／空态／**错误态**」，
     故此处核对三字段 + 前端错误态即可，**不做真实改密**：
     改密会使全部会话失效并改掉测试账号口令，影响「可反复跑」这一前提。
     （`FR-02` 在第 13 章无对应 `AC` 条目，验收方式即 `V-03`。） */
  test('S16 修改密码页（P10-10）：字段走查 + 前端错误态（FR-02、V-03）', async ({ page }) => {
    await uiLogin(page);
    await page.goto(`${SELLER_URL}/password`);
    await expect(page.locator('.page__title')).toContainText('修改密码');

    // ① 字段走查（P10-10：原密码／新密码／确认新密码）
    await expect(page.locator('#old-password')).toBeVisible();
    await expect(page.locator('#new-password')).toBeVisible();
    await expect(page.locator('#confirm-password')).toBeVisible();

    // ② 错误态：新密码不足 8 位 + 两次不一致
    await page.fill('#old-password', 'whatever-not-the-real-one');
    await page.fill('#new-password', 'abc');
    await page.fill('#confirm-password', 'xyz');
    await page.getByRole('button', { name: '提交修改' }).click();

    const form = page.locator('form');
    await expect(form, '新密码长度不足应就地报错').toContainText('新密码至少 8 位');
    await expect(form, '两次新密码不一致应就地报错').toContainText('两次输入的新密码不一致');

    // ③ 前端校验未通过 ⇒ 不得弹出确认弹窗（证明请求没有发出去）
    await expect(page.getByRole('dialog')).toHaveCount(0);
  });
});
