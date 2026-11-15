import { expect, test, type APIRequestContext } from '@playwright/test';

import {
  createL1,
  createL2,
  customerDetail,
  customers,
  newApi,
  newBuyer,
  placeOrder,
  publishGoods,
  sellerLogin,
} from '../helpers/api';
import { buyerFixture, goodsFixture, uniqueName } from '../helpers/data';
import { SELLER_URL } from '../helpers/config';
import { sellerLoginUi } from '../helpers/ui';

/**
 * 07 客户管理
 *
 * 阶段三口径：客户详情只回基本信息，**不内嵌购买历史**；
 * 该客户的订单改由详情页内嵌的订单列表承担（按 buyer_id 固定筛选）。
 *
 * 阶段二删除两条：T07-03「历史列表」与 T07-04「历史详情」——
 * `/history` 两页随归档概念一起下线，历史商品改由「已下架 + 原因」筛选查询。
 */
test.describe.serial('07 客户管理', () => {
  let ctx: APIRequestContext;
  let sellerToken: string;
  let winnerUsername: string;
  let loserUsername: string;
  let winnerId: string;
  let orderNo: string;

  test.beforeAll(async () => {
    ctx = await newApi();
    sellerToken = await sellerLogin(ctx);
    const tag = uniqueName('h');
    const l1 = await createL1(ctx, sellerToken, `客户一级-${tag}`);
    const l2 = await createL2(ctx, sellerToken, l1.id, `客户二级-${tag}`);

    const goods = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('客户商品'), l2.id, { stock: 2 }),
    );

    const winner = buyerFixture('成交');
    const loser = buyerFixture('未下单');
    winnerUsername = winner.username;
    loserUsername = loser.username;

    const winnerToken = await newBuyer(ctx, winner);
    await newBuyer(ctx, loser);

    const placed = await placeOrder(ctx, winnerToken, [{ goods_id: goods.id, quantity: 1 }]);
    orderNo = placed.order_no;

    const page = await customers(ctx, sellerToken);
    const found = (page.items as Array<{ id: string; username: string }>).find(
      (item) => item.username === winnerUsername,
    );
    winnerId = found!.id;
  });

  test.afterAll(async () => {
    await ctx.dispose();
  });

  test('T07-01 客户列表：新注册买家即出现在客户里', async ({ page }) => {
    const pageData = await customers(ctx, sellerToken);
    const names = (pageData.items as Array<{ username: string }>).map((item) => item.username);
    expect(names, `客户列表应含 ${winnerUsername}`).toContain(winnerUsername);
    expect(names).toContain(loserUsername);

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/customers`);
    await expect(page.getByRole('heading', { name: '客户管理' })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: '用户名' })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: '联系电话' })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: '默认交易地点' })).toBeVisible();
    await expect(page.locator('tbody tr').first()).toBeVisible();
  });

  test('T07-02 客户详情：内嵌该客户的订单，且不再返回购买历史', async ({ page }) => {
    // 接口：详情只有基本信息，没有历史字段
    const detail = await customerDetail(ctx, sellerToken, winnerId);
    expect(JSON.stringify(detail)).not.toContain('history');
    expect(JSON.stringify(detail)).not.toContain('purchase');

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/customers/${winnerId}`);
    await expect(page.getByRole('heading', { name: '客户详情' })).toBeVisible();
    await expect(page.getByText(winnerUsername)).toBeVisible();

    // 内嵌订单列表：标题 + 该客户刚下的那一单
    await expect(page.getByRole('heading', { name: '该客户的订单' })).toBeVisible();
    await expect(page.getByRole('cell', { name: orderNo })).toBeVisible();
  });

  test('T07-05 改密页前端校验：空提交与新密码过短都就地提示', async ({ page }) => {
    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/password`);
    await expect(page.getByRole('heading', { name: '修改密码' })).toBeVisible();

    // 空提交：两个字段各自报「请填写…」
    await page.getByRole('button', { name: '提交修改' }).click();
    await expect(page.getByText('请填写原密码')).toBeVisible();
    await expect(page.getByText('请填写新密码')).toBeVisible();

    // 新密码 7 位（< 8）→ 前端拦截，不发请求
    await page.locator('#old-password').fill('whatever-old');
    await page.locator('#new-password').fill('Abc1234');
    await page.locator('#confirm-password').fill('Abc1234');
    await page.getByRole('button', { name: '提交修改' }).click();
    await expect(page.getByText(/新密码至少 8 位/)).toBeVisible();
    // 没有改成功，仍停留在这个页面
    await expect(page).toHaveURL(/\/password/);
  });
});
