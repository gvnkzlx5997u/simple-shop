/**
 * 买家端验收用例（验收方式 V-03 页面走查 + V-04 端到端演练）
 *
 * 覆盖的验收标准：
 *   AC-09 口令码当场展示 + 保存提醒          → A2
 *   AC-11 凭码查位次 / 已进入交易            → A4、（交易中态在卖家端用例里）
 *   AC-12 买家端不设商品列表 / 页面恰 4 个    → A3、A10
 *   AC-13 终态后查/改/撤销均被拒 + 不说明原因 → A6、A7
 *   AC-19 口令码三态互斥                     → A4、A7、A8
 *   AC-28 5 条【必须】文案（买家端部分）      → A2、A4、A7
 *   BR-24 / K5-02 成功页无独立地址           → A3
 *   M10-28 格式校验（前端拦，不发查询）      → A9
 */
import { test, expect } from '@playwright/test';
import { TOKEN_RE } from '../helpers/config';
import { newApi, ensureOnSaleProduct } from '../helpers/api';

// 测试库是独立库（simple_shop_test），数据随便造，不影响正式库
test.beforeAll(async () => {
  const api = await newApi();
  const { product } = await ensureOnSaleProduct(api);
  await api.dispose();
  expect(
    product,
    '前置失败：测试库中没有在售商品。请先双击「启动测试环境.bat」再跑，或执行复位。',
  ).toBeTruthy();
  expect(
    product.status,
    `前置失败：当前商品状态是 ${product.status}（需要 on_sale）。请先复位测试库。`,
  ).toBe('on_sale');
});

/** 点击表单元素时，页面上可能出现的二次确认弹层 */
async function confirm(page: import('@playwright/test').Page) {
  const mask = page.locator('#confirmMask');
  await expect(mask).toBeVisible();
  await page.click('#confirmOk');
}

/* ══════════════════════════════════════════════════════════
   一、商品浏览与页面结构
   ══════════════════════════════════════════════════════════ */

test('A1 首页展示当前商品，提交入口可用（FR-12、FR-19）', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/商品详情/);

  // 不是空态
  await expect(page.locator('.empty')).toHaveCount(0);
  await expect(page.locator('.card__title')).toBeVisible();

  // 提交入口存在，且不是「置灰保留」态
  const submit = page.locator('a[href="/intent/new"]');
  await expect(submit).toBeVisible();
  await expect(submit).not.toHaveAttribute('aria-disabled', 'true');

  // 凭口令码查询入口始终存在（FR-20）
  await expect(page.locator('a[href="/query"]')).toBeVisible();
});

test('A10 买家端首页 URL 不含商品 ID，且不设商品列表（AC-12）', async ({ page }) => {
  await page.goto('/');
  expect(new URL(page.url()).pathname, '首页 URL 不应带参数（A-03/B-02）').toBe('/');

  const html = await page.content();
  for (const w of ['我的意向', '我的订单', '商品列表', '历史商品']) {
    expect(html, `买家端不应出现「${w}」入口（S10-02/S10-03/DEC-38）`).not.toContain(w);
  }
});

test('A3 提交成功页没有独立地址，直接访问应 404（BR-24、K5-02）', async ({ page }) => {
  const res = await page.goto('/intent/success');
  expect(res?.status(), '成功页只应由 POST 成功分支渲染，GET 必须 404').toBe(404);
});

/* ══════════════════════════════════════════════════════════
   二、意向全流程（有顺序依赖：提交 → 查 → 改 → 撤销 → 再查）
   ══════════════════════════════════════════════════════════ */

test.describe.serial('买家端 · 意向全流程', () => {
  let token = '';

  test('A2 提交意向 → 成功页当场展示口令码 + 保存提醒（AC-09、M10-04、UX-04）', async ({
    page,
  }) => {
    await page.goto('/intent/new');
    await page.fill('#fName', '自动测试张三');
    await page.fill('#fTel', '13800001111');

    // ⚠️ 提交按钮初始是 disabled，两个字段都合法后才启用（前端体验层）
    await expect(page.locator('#btnSubmit')).toBeEnabled();
    await page.click('#btnSubmit');
    await confirm(page);

    // 跳转到成功页
    await expect(page).toHaveTitle(/提交成功/);

    // ① 口令码原文直接展示
    const codeEl = page.locator('#tokenCode');
    await expect(codeEl).toBeVisible();
    token = ((await codeEl.textContent()) || '').trim();
    expect(token, '口令码应为 12 位大写字母与数字（C-16）').toMatch(TOKEN_RE);

    // ② 保存提醒（M10-04【必须】）
    await expect(page.locator('.notice--warn')).toContainText(/仅显示这一次|无法自助找回/);
  });

  test('A4 凭口令码查询 → 显示排队位次与买家信息（AC-11、M10-07）', async ({ page }) => {
    await page.goto('/query');
    await page.fill('#fToken', token);
    await page.getByRole('button', { name: '查询' }).click();

    const ok = page.locator('.result--ok');
    await expect(ok).toBeVisible();
    await expect(ok).toContainText(/排第\s*\d+\s*位/);
    await expect(ok.locator('.kv__v').first()).toContainText('自动测试张三');

    // 排队中 → 可撤销（撤销按钮存在且可用）
    await expect(page.locator('#btnRevoke')).toBeEnabled();
  });

  test('A5 修改联系信息 → 新值生效（FR-16）', async ({ page }) => {
    await page.goto('/query');
    await page.fill('#fToken', token);
    await page.getByRole('button', { name: '查询' }).click();

    await expect(page.locator('#updateForm')).toBeVisible();
    await page.fill('#fNewName', '自动测试李四');
    await page.getByRole('button', { name: '修改信息' }).click();

    // 就地成功提示 + 结果区回显新值
    await expect(page.locator('.notice--ok')).toBeVisible();
    await expect(page.locator('.result--ok .kv__v').first()).toContainText('自动测试李四');
  });

  test('A6 撤销意向 → 需二次确认，撤销后转失效态（FR-17、IS-03）', async ({ page }) => {
    await page.goto('/query');
    await page.fill('#fToken', token);
    await page.getByRole('button', { name: '查询' }).click();

    await page.click('#btnRevoke');
    await confirm(page);

    // 撤销后意向为终态 → 页面转失效态
    await expect(page.locator('.result--error')).toContainText('失效');
  });

  test('A7 终态口令码：只说失效，不说明原因、不返回买家信息（AC-13、BR-29、M10-06）', async ({
    page,
  }) => {
    await page.goto('/query');
    await page.fill('#fToken', token);
    await page.getByRole('button', { name: '查询' }).click();

    const box = page.locator('.result--error');
    await expect(box).toBeVisible();
    await expect(box).toContainText('失效');

    const text = await box.innerText();
    // 不得说明原因 / 透露交易结果
    for (const w of ['已售出', '交易失败', '已撤销', '交易成功', '排队']) {
      expect(text, `失效提示不得出现「${w}」（BR-29）`).not.toContain(w);
    }
    // 不得返回买家信息
    expect(text).not.toContain('自动测试');
    expect(text).not.toContain('13800001111');

    // 操作区（改信息 / 撤销）整体收起
    await expect(page.locator('#updateForm')).toHaveCount(0);
    await expect(page.locator('#btnRevoke')).toHaveCount(0);
  });
});

/* ══════════════════════════════════════════════════════════
   三、口令码负向用例
   ══════════════════════════════════════════════════════════ */

test('A8 不存在的口令码 → 提示「口令不正确」（AC-19 三态之一、M10-05）', async ({ page }) => {
  await page.goto('/query');
  await page.fill('#fToken', 'ZZZZ00000000');
  await page.getByRole('button', { name: '查询' }).click();

  const box = page.locator('.result--error');
  await expect(box).toBeVisible();
  await expect(box).toContainText(/不正确|不存在/);
  // 与「失效」是两种不同提示
  await expect(box).not.toContainText('已失效');
});

test('A9 口令码格式不符 → 前端就地提示，不发起查询（M10-28、C-16）', async ({ page }) => {
  await page.goto('/query');
  await page.fill('#fToken', 'abc'); // 输入时会自动转大写，但长度仍不足 12
  await page.getByRole('button', { name: '查询' }).click();

  await expect(page.locator('#tokenFormatError')).toBeVisible();
  // 没有服务端结果区（说明请求没发出去）
  await expect(page.locator('.result')).toHaveCount(0);
});
