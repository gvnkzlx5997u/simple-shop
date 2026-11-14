import { expect, test, type APIRequestContext } from '@playwright/test';

import {
  createL1,
  createL2,
  customerDetail,
  customers,
  enterTrade,
  listIntentions,
  markSuccess,
  newApi,
  newBuyer,
  publishGoods,
  sellerHistory,
  sellerLogin,
  submitIntention,
} from '../helpers/api';
import { buyerFixture, goodsFixture, uniqueName } from '../helpers/data';
import { SELLER_URL } from '../helpers/config';
import { sellerLoginUi } from '../helpers/ui';

/**
 * 07 客户管理与历史归档（旧套件 S12 / S13 保留项 + 阶段二新增的客户模块）
 *
 * 对应交接文档 P0 剧本 INT-09（归档）、INT-16（客户与历史）。
 * 造数：一件库存 1 的商品，买家甲成交 → 归档；买家乙排队 → 售罄失败。
 * 这样一屏就能同时验证"成功"和"失败"两种记录。
 */
test.describe.serial('07 客户管理与历史', () => {
  let ctx: APIRequestContext;
  let sellerToken: string;
  let goodsId: string;
  let goodsName: string;
  let winnerUsername: string;
  let loserUsername: string;
  let winnerId: string;

  test.beforeAll(async () => {
    ctx = await newApi();
    sellerToken = await sellerLogin(ctx);
    const tag = uniqueName('h');
    const l1 = await createL1(ctx, sellerToken, `历一级-${tag}`);
    const l2 = await createL2(ctx, sellerToken, l1.id, `历二级-${tag}`);

    goodsName = uniqueName('归档商品');
    const goods = await publishGoods(ctx, sellerToken, goodsFixture(goodsName, l2.id, { stock: 1 }));
    goodsId = goods.id;

    const winner = buyerFixture('成交');
    const loser = buyerFixture('排队');
    winnerUsername = winner.username;
    loserUsername = loser.username;

    const winnerToken = await newBuyer(ctx, winner);
    const loserToken = await newBuyer(ctx, loser);
    await submitIntention(ctx, winnerToken, goodsId);
    await submitIntention(ctx, loserToken, goodsId);

    const list = await listIntentions(ctx, sellerToken, goodsId);
    const head = list.items
      .filter((item) => item.status === 'QUEUED')
      .sort((a, b) => a.queue_order - b.queue_order)[0];
    await enterTrade(ctx, sellerToken, head.id);
    await markSuccess(ctx, sellerToken, head.id);

    // 买家 id 供客户详情页直链使用
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
    // 接口侧先确认（不受分页影响）
    const pageData = await customers(ctx, sellerToken);
    const names = (pageData.items as Array<{ username: string }>).map((item) => item.username);
    expect(names, `客户列表应含 ${winnerUsername}`).toContain(winnerUsername);
    expect(names).toContain(loserUsername);

    // UI 侧确认表头与数据行（需求：用户名 / 电话 / 默认地点 / 注册时间）
    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/customers`);
    await expect(page.getByRole('heading', { name: '客户管理' })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: '用户名' })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: '联系电话' })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: '默认交易地点' })).toBeVisible();
    await expect(page.locator('tbody tr').first()).toBeVisible();
  });

  test('T07-02 客户详情：购买历史含交易成功记录', async ({ page }) => {
    // 接口：详情里能看到购买历史
    const detail = await customerDetail(ctx, sellerToken, winnerId);
    expect(JSON.stringify(detail)).toContain(goodsName);

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/customers/${winnerId}`);
    await expect(page.getByRole('heading', { name: '客户详情' })).toBeVisible();
    await expect(page.locator('.info')).toContainText(winnerUsername);
    await expect(page.getByRole('heading', { name: '购买历史' })).toBeVisible();
    await expect(page.locator('table')).toContainText(goodsName);
    await expect(page.locator('table')).toContainText('交易成功');
  });

  test('T07-03 历史列表：刚归档的商品在列表里', async ({ page }) => {
    const history = await sellerHistory(ctx, sellerToken);
    const ids = (history.items as Array<{ id?: string; goods_id?: string }>).map(
      (item) => item.id ?? item.goods_id,
    );
    expect(ids, '归档商品应进历史').toContain(goodsId);

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/history`);
    await expect(page.getByRole('heading', { name: '历史记录' })).toBeVisible();
    // 列表按归档时间倒序，刚归档的应在第一行
    await expect(page.locator('tbody tr').first()).toContainText(goodsName);
    await expect(page.locator('tbody tr').first()).toContainText('售罄归档');
  });

  test('T07-04 历史详情：意向名单含成功与失败两种记录', async ({ page }) => {
    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/history/${goodsId}`);
    await expect(page.getByRole('heading', { name: '历史商品详情' })).toBeVisible();
    await expect(page.locator('.goods')).toContainText(goodsName);
    await expect(page.getByRole('heading', { name: '意向名单' })).toBeVisible();

    const table = page.locator('table');
    await expect(table).toContainText(winnerUsername);
    await expect(table).toContainText(loserUsername);
    await expect(table).toContainText('交易成功');
    await expect(table).toContainText('交易失败');
    // 失败类型是"商品已售出"（售罄清队），不是卖家作废
    await expect(table).toContainText('商品已售出');
  });

  test('T07-05 改密页前端校验：空提交与新密码过短都就地提示', async ({ page }) => {
    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/password`);
    await expect(page.getByRole('heading', { name: '修改密码' })).toBeVisible();

    // 空提交
    await page.getByRole('button', { name: '提交修改' }).click();
    await expect(page.locator('.field__error').first()).toBeVisible();

    // 新密码 7 位（< 8）→ 前端拦截，不应发请求
    await page.getByLabel('原密码').fill('whatever-old');
    await page.getByLabel('新密码（至少 8 位）').fill('Abc1234');
    await page.getByRole('button', { name: '提交修改' }).click();
    await expect(page.locator('.field__error').filter({ hasText: '新密码至少 8 位' })).toBeVisible();
    // 没有改成功，仍停留在这个页面
    await expect(page).toHaveURL(/\/password/);
  });
});
