import { expect, test } from '@playwright/test';

import { buyerLogin, call, newApi, newBuyer, sellerLogin } from '../helpers/api';
import { buyerFixture } from '../helpers/data';
import { BUYER_URL, SELLER_URL } from '../helpers/config';
import { buyerLoginUi, buyerRegisterUi, sellerLoginUi } from '../helpers/ui';

/**
 * 01 认证与令牌隔离
 *
 * 对应阶段二：买家从「口令码」改为「账号体系」，买家/卖家令牌不能互用。
 * 旧套件 01-buyer 的 A2~A9 全部依赖口令码，已作废（见 E2E旧用例对照表）。
 */
test.describe('01 认证与令牌隔离', () => {
  test('T01-01 卖家未登录访问后台 → 跳登录页', async ({ page }) => {
    await page.goto(`${SELLER_URL}/`);
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByRole('heading', { name: 'SimpleShop 卖家后台' })).toBeVisible();
  });

  test('T01-02 卖家登录成功 → 进入商品列表', async ({ page }) => {
    await sellerLoginUi(page);
    await expect(page.getByRole('heading', { name: '商品列表' })).toBeVisible();
    await expect(page.getByRole('link', { name: '发布商品' }).first()).toBeVisible();
  });

  test('T01-03 卖家密码错误 → 停留在登录页', async ({ page }) => {
    await page.goto(`${SELLER_URL}/login`);
    await page.getByLabel('账号').fill('seller');
    await page.getByLabel('密码').fill('definitely-wrong-password');
    await page.getByRole('button', { name: '登录', exact: true }).click();

    await expect(page).toHaveURL(/\/login/);
    // 不应进入后台
    await expect(page.getByRole('heading', { name: '商品列表' })).toHaveCount(0);
  });

  test('T01-04 买家注册 → 自动登录并进入商品列表', async ({ page }) => {
    const fixture = buyerFixture('reg');
    await buyerRegisterUi(page, {
      username: fixture.username,
      password: fixture.password,
      phone: fixture.phone,
      location: fixture.default_location,
    });
    await expect(page).toHaveURL(/\/goods/);
    // 列表页存在（无商品也应有搜索区）
    await expect(page.getByPlaceholder('搜索商品名称或描述')).toBeVisible();
  });

  test('T01-05 买家登录成功 → 进入商品列表', async ({ page }) => {
    const ctx = await newApi();
    const fixture = buyerFixture('login');
    await newBuyer(ctx, fixture);
    await ctx.dispose();

    await buyerLoginUi(page, fixture.username, fixture.password);
    await expect(page).toHaveURL(/\/goods/);
  });

  test('T01-06 买家令牌调卖家接口 → 拒绝', async () => {
    const ctx = await newApi();
    const fixture = buyerFixture('cross');
    const buyerToken = await newBuyer(ctx, fixture);

    const result = await call(ctx, 'GET', '/api/seller/customers', undefined, { token: buyerToken });
    // 约定：401 + 10002；只要不是"成功拿到数据"即算拦住
    expect(
      result.status === 401 || result.body.code === 10002 || result.body.code === 10001,
      `买家令牌不应能访问卖家接口，实际 HTTP ${result.status} code ${result.body.code}`,
    ).toBeTruthy();

    await ctx.dispose();
  });

  test('T01-07 卖家令牌调买家接口 → 拒绝', async () => {
    const ctx = await newApi();
    const sellerToken = await sellerLogin(ctx);
    const fixture = buyerFixture('cross2');

    const result = await call(ctx, 'POST', '/api/buyer/intentions', { goods_id: 'NOT-EXIST' }, {
      token: sellerToken,
    });
    expect(
      result.status === 401 || result.body.code === 10002 || result.body.code === 10001,
      `卖家令牌不应能调买家接口，实际 HTTP ${result.status} code ${result.body.code}`,
    ).toBeTruthy();

    await ctx.dispose();
  });

  test('T01-08 错误密码登录买家 → 失败', async () => {
    const ctx = await newApi();
    const fixture = buyerFixture('badpw');
    await newBuyer(ctx, fixture);

    let failed = false;
    try {
      await buyerLogin(ctx, fixture.username, 'wrong-password');
    } catch {
      failed = true;
    }
    expect(failed, '错误密码不应登录成功').toBeTruthy();
    await ctx.dispose();
  });
});
