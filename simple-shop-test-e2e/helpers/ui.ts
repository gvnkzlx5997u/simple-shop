/**
 * 页面操作辅助（走真实 UI，不塞令牌走后门）。
 *
 * 选择器原则（交接文档 §3.5）：
 *   1. 优先 aria-label / id（前端已有：`#goods-name`、`aria-label="状态筛选"`）；
 *   2. 其次 role + 可访问名称（按钮文案）；
 *   3. 最后才用 CSS 类名 —— 类名随样式变动，最脆。
 */
import { expect, type Page } from '@playwright/test';

import { BUYER_URL, SELLER_PASSWORD, SELLER_URL, SELLER_ACCOUNT } from './config';

// ---------------- 卖家端 ----------------

/** 卖家登录（真实填表 + 点按钮），成功后停在商品列表。 */
export async function sellerLoginUi(page: Page, account = SELLER_ACCOUNT, password = SELLER_PASSWORD) {
  await page.goto(`${SELLER_URL}/login`);
  await page.getByLabel('账号').fill(account);
  await page.getByLabel('密码').fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 20_000 });
  await expect(page.getByRole('heading', { name: '商品列表' })).toBeVisible();
}

/** 卖家侧通用确认弹窗：点「确认」（exact 防止命中「确认发布」之类子串）。 */
export async function confirmDialog(page: Page) {
  const dialog = page.getByRole('dialog');
  await dialog.waitFor({ state: 'visible' });
  await dialog.getByRole('button', { name: '确认', exact: true }).click();
  await dialog.waitFor({ state: 'hidden' }).catch(() => undefined);
}

/** 卖家商品列表里按名称定位商品卡片。 */
export function goodsCard(page: Page, name: string) {
  return page.locator('.goods-card').filter({ hasText: name });
}

/** 从列表点进某个商品的详情。 */
export async function openGoodsDetail(page: Page, name: string) {
  await goodsCard(page, name).first().click();
  await expect(page.getByRole('heading', { name: '商品详情' })).toBeVisible();
}

// ---------------- 买家端 ----------------

/** 买家登录（真实填表）。 */
export async function buyerLoginUi(page: Page, username: string, password: string) {
  await page.goto(`${BUYER_URL}/login`);
  await page.getByLabel('用户名').fill(username);
  await page.getByLabel('密码').fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.waitForURL((url) => url.pathname.startsWith('/goods'), { timeout: 20_000 });
}

/** 买家注册（真实填表，注册成功后会自动登录并回到商品列表）。 */
export async function buyerRegisterUi(
  page: Page,
  input: { username: string; password: string; phone: string; location?: string },
) {
  await page.goto(`${BUYER_URL}/register`);
  await page.getByLabel('用户名').fill(input.username);
  await page.getByLabel('密码').fill(input.password);
  await page.getByLabel('电话').fill(input.phone);
  if (input.location) await page.getByLabel('默认交易地点').fill(input.location);
  await page.getByRole('button', { name: '注册并登录' }).click();
  await page.waitForURL((url) => url.pathname.startsWith('/goods'), { timeout: 20_000 });
}

/** 买家列表里按名称定位商品卡片。 */
export function buyerGoodsCard(page: Page, name: string) {
  return page.locator('.goods-card').filter({ hasText: name });
}

/** 买家打开某个商品详情。 */
export async function buyerOpenGoods(page: Page, name: string) {
  await buyerGoodsCard(page, name).first().click();
  await expect(page.locator('.summary .title')).toBeVisible();
}

/** 读取卖家详情页某个字段行的值（dl 结构：dt/dd 成对）。 */
export async function detailField(page: Page, label: string): Promise<string> {
  const row = page.locator('.goods__meta div, dl div').filter({ hasText: label }).first();
  return (await row.innerText()).replace(label, '').trim();
}
