/**
 * 页面操作辅助（走真实 UI，不塞令牌走后门）。
 *
 * 选择器原则：
 *   1. 优先 aria-label / placeholder / data-testid（前端已提供：
 *      `aria-label="按状态与下架原因筛选"`、`data-testid="status-tag"`、`data-testid="toast-item"`）；
 *   2. 其次 role + 可访问名称（按钮文案）；
 *   3. 最后才用 CSS 类名 —— 类名随样式变动，最脆。
 *
 * 阶段U 之后卖家端已换 daisyUI：`.goods-card`、`.tag`、`.field__error`、`section.l1`
 * 这些阶段二的类名都不存在了，不要再照抄归档里的写法。
 */
import { expect, type Page } from '@playwright/test';

import { BUYER_URL, SELLER_PASSWORD, SELLER_URL, SELLER_ACCOUNT } from './config';

// ---------------- 卖家端 ----------------

/**
 * 卖家登录（真实填表 + 点按钮），成功后停在商品列表。
 *
 * 用 id 而不是 getByLabel：卖家端表单是 daisyUI 的 `<fieldset><legend>` 结构，
 * legend 只命名分组，不给 input 起可访问名称，getByLabel('账号') 会找不到。
 */
export async function sellerLoginUi(page: Page, account = SELLER_ACCOUNT, password = SELLER_PASSWORD) {
  await page.goto(`${SELLER_URL}/login`);
  await page.locator('#account').fill(account);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 20_000 });
  await expect(page.getByRole('heading', { name: '商品列表' })).toBeVisible();
}

/**
 * 卖家侧确认弹窗。
 * 确认按钮的文案随场景变（确认发布 / 下架 / 确认订单 / 取消订单），所以文案要显式传。
 */
export async function confirmDialog(page: Page, confirmText = '确认') {
  const dialog = page.getByRole('dialog');
  await dialog.waitFor({ state: 'visible' });
  await dialog.getByRole('button', { name: confirmText, exact: true }).click();
  await dialog.waitFor({ state: 'hidden' }).catch(() => undefined);
}

/**
 * 卖家商品列表里按名称定位商品卡。
 * 卡片是整块可点的 RouterLink（没有专门类名），用 role=link + 名称文本定位。
 */
export function sellerGoodsCard(page: Page, name: string) {
  return page.getByRole('link').filter({ hasText: name });
}

/** 从卖家列表点进某个商品的详情。 */
export async function openSellerGoods(page: Page, name: string) {
  await sellerGoodsCard(page, name).first().click();
  await expect(page.getByRole('heading', { name: '商品详情' })).toBeVisible();
}

/** 卖家列表的搜索：关键字走「搜索」按钮（筛选项是即时生效的，关键字不是）。 */
export async function sellerSearchGoods(page: Page, keyword: string) {
  await page.getByLabel('搜索商品名称').fill(keyword);
  await page.getByRole('button', { name: '搜索' }).click();
}

/** 商品详情页的状态 / 下架原因徽章（同一元素复用，成对出现）。 */
export function statusTags(page: Page) {
  return page.getByTestId('status-tag');
}

/** 卖家端提示条（toast）：失败提示都用它断言，避免匹配到别处的同名字样。 */
export function toast(page: Page) {
  return page.getByTestId('toast-item');
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

/** 买家列表里按名称定位商品卡（买家端仍是原生 CSS，`.goods-card` 还在）。 */
export function buyerGoodsCard(page: Page, name: string) {
  return page.locator('.goods-card').filter({ hasText: name });
}

/** 买家打开某个商品详情。 */
export async function buyerOpenGoods(page: Page, name: string) {
  await buyerGoodsCard(page, name).first().click();
  await expect(page.locator('.summary .title')).toBeVisible();
}

/** 买家端搜索（placeholder 里写着「名称或描述」，但后端只匹配名称）。 */
export async function buyerSearch(page: Page, keyword: string) {
  await page.getByPlaceholder('搜索商品名称或描述').fill(keyword);
  await page.getByRole('button', { name: '搜索' }).click();
}

/** 买家详情页加购：点按钮并等「已加入购物车 / 已在购物车里了」的提示。 */
export async function buyerAddToCart(page: Page) {
  await page.getByRole('button', { name: '加入购物车' }).click();
  await expect(page.locator('.notice')).toBeVisible();
}

/** 买家购物车里按商品名定位一行。 */
export function buyerCartRow(page: Page, name: string) {
  return page.locator('.row-item').filter({ hasText: name });
}

/** 买家收藏页里按商品名定位卡片。 */
export function buyerFavoriteCard(page: Page, name: string) {
  return page.locator('.fav-card').filter({ hasText: name });
}

export async function buyerOpenCart(page: Page) {
  await page.goto(`${BUYER_URL}/cart`);
  await expect(page.getByRole('heading', { name: '购物车' })).toBeVisible();
}

export async function buyerOpenFavorites(page: Page) {
  await page.goto(`${BUYER_URL}/favorites`);
  await expect(page.getByRole('heading', { name: '我的收藏' })).toBeVisible();
}

// ---------------- 卖家订单 ----------------

/** 卖家订单列表里按订单号定位一行（整行可点，进详情）。 */
export function sellerOrderRow(page: Page, orderNo: string) {
  return page.getByRole('row').filter({ hasText: orderNo });
}

export async function openSellerOrder(page: Page, orderNo: string) {
  await sellerOrderRow(page, orderNo).click();
  await expect(page.getByRole('heading', { name: '订单详情' })).toBeVisible();
  await expect(page.getByText(orderNo).first()).toBeVisible();
}

/**
 * 点「下一步」并确认。
 * 详情页的推进按钮只有 data-testid="order-next" 一个（状态决定它叫什么），
 * 确认弹窗的确认按钮文案跟着动作变，所以要传 label。
 */
export async function sellerAdvance(page: Page, label: string) {
  await page.getByTestId('order-next').click();
  await confirmDialog(page, label);
}
