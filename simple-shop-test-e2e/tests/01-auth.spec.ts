import { expect, test } from '@playwright/test';

import { buyerLogin, call, newApi, newBuyer, sellerLogin } from '../helpers/api';
import { buyerFixture } from '../helpers/data';
import { SELLER_URL } from '../helpers/config';
import { buyerLoginUi, buyerRegisterUi, sellerLoginUi } from '../helpers/ui';

/**
 * 01 认证与令牌隔离
 *
 * 阶段三口径没变：买家账号体系 + 买卖家令牌不能互用。
 * 变化只有选择器（卖家端换 daisyUI，表单是 fieldset/legend，用 id 定位）与被调接口
 * （阶段二的 `/api/buyer/intentions` 已下线，换成 `/api/buyer/cart`）。
 */
test.describe('01 认证与令牌隔离', () => {
  test('T01-01 卖家未登录访问后台 → 跳登录页', async ({ page }) => {
    await page.goto(`${SELLER_URL}/`);
    await expect(page).toHaveURL(/\/login/);
    // 登录页没有 h1：品牌名与「卖家后台」是两个 <p>，用输入框 + 文案双重确认
    await expect(page.locator('#account')).toBeVisible();
    await expect(page.getByText('卖家后台')).toBeVisible();
  });

  test('T01-02 卖家登录成功 → 进入商品列表', async ({ page }) => {
    await sellerLoginUi(page);
    await expect(page.getByRole('heading', { name: '商品列表' })).toBeVisible();
    await expect(page.getByRole('link', { name: '发布商品' }).first()).toBeVisible();
  });

  test('T01-03 卖家密码错误 → 停留在登录页', async ({ page }) => {
    await page.goto(`${SELLER_URL}/login`);
    await page.locator('#account').fill('seller');
    await page.locator('#password').fill('definitely-wrong-password');
    await page.getByRole('button', { name: '登录', exact: true }).click();

    // 10001 回填到密码字段下方，而不是弹 toast
    await expect(page.getByText('账号或密码错误')).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
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
    // 约定：401 + 10002（角色不匹配与未登录同码）
    expect(
      result.status === 401 || result.body.code === 10002,
      `买家令牌不应能访问卖家接口，实际 HTTP ${result.status} code ${result.body.code}`,
    ).toBeTruthy();

    await ctx.dispose();
  });

  test('T01-07 卖家令牌调买家接口 → 拒绝', async () => {
    const ctx = await newApi();
    const sellerToken = await sellerLogin(ctx);

    // 阶段二这里打的是 /api/buyer/intentions（已下线），换成仍然存在的加购接口
    const result = await call(ctx, 'POST', '/api/buyer/cart', { goods_id: 'NOT-EXIST' }, {
      token: sellerToken,
    });
    expect(
      result.status === 401 || result.body.code === 10002,
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
