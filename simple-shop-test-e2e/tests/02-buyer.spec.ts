import { expect, test, type APIRequestContext } from '@playwright/test';

import { createL1, createL2, newApi, publishGoods, sellerLogin } from '../helpers/api';
import { goodsFixture, uniqueName } from '../helpers/data';
import { BUYER_URL } from '../helpers/config';
import { buyerOpenGoods, buyerSearch } from '../helpers/ui';

/**
 * 02 买家端：列表 / 搜索 / 类别筛选 / 详情
 *
 * 阶段二的两条「提交意向」「我的历史」已删除：阶段三买家走购物车下单，
 * `/history` 整页下线。购物车与订单行为留给买家端自己的 spec（下一轮并入）。
 *
 * 造数全部走接口（快且稳），被测流程走真实 UI。
 * 关键口径：
 *  - 买家匿名可看在售商品
 *  - 搜索只匹配名称（后端 `g.name like`，描述不参与）
 */
test.describe('02 买家端浏览与搜索', () => {
  let ctx: APIRequestContext;
  let sellerToken: string;
  let l1Name: string;
  let l2Name: string;
  let otherL1Name: string;
  let otherL2Name: string;
  let l2Id: string;
  let otherL2Id: string;
  /** 只在本轮数据里唯一的搜索词 */
  let keyword: string;
  let goodsA: { id: string; name: string };
  let goodsB: { id: string; name: string };

  test.beforeAll(async () => {
    ctx = await newApi();
    sellerToken = await sellerLogin(ctx);

    const tag = uniqueName('c');
    l1Name = `一级-${tag}`;
    l2Name = `二级-${tag}`;
    otherL1Name = `另类-${tag}`;
    otherL2Name = `另二-${tag}`;

    const l1 = await createL1(ctx, sellerToken, l1Name);
    const l2 = await createL2(ctx, sellerToken, l1.id, l2Name);
    const l1b = await createL1(ctx, sellerToken, otherL1Name);
    const l2b = await createL2(ctx, sellerToken, l1b.id, otherL2Name);
    l2Id = l2.id;
    otherL2Id = l2b.id;

    keyword = uniqueName('kw');
    // A：名称含搜索词；B：名称不含，但描述含 —— 用来验证「只匹配名称」
    const a = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(`商品A-${keyword}`, l2Id, { stock: 2, price: '99.90' }),
    );
    const b = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('商品B'), otherL2Id, {
        stock: 1,
        description: [{ type: 'TEXT', text: `描述里含关键字 ${keyword}` }],
      }),
    );
    goodsA = { id: a.id, name: a.name };
    goodsB = { id: b.id, name: b.name };
  });

  test.afterAll(async () => {
    await ctx.dispose();
  });

  test('T02-01 匿名可看在售列表，且能搜到名称命中的商品', async ({ page }) => {
    await page.goto(`${BUYER_URL}/goods`);
    await buyerSearch(page, keyword);

    await expect(page.locator('.goods-card').filter({ hasText: goodsA.name })).toHaveCount(1);
  });

  test('T02-02 搜索不匹配描述（只匹配名称）', async ({ page }) => {
    await page.goto(`${BUYER_URL}/goods`);
    await buyerSearch(page, keyword);
    await expect(page.locator('.goods-card').filter({ hasText: goodsA.name })).toHaveCount(1);

    // B 的描述里有同一个词，但名称没有 —— 不应出现在结果里
    await expect(page.locator('.goods-card').filter({ hasText: goodsB.name })).toHaveCount(0);
  });

  test('T02-03 两级类别筛选只出该二级下的商品', async ({ page }) => {
    await page.goto(`${BUYER_URL}/goods`);
    const selects = page.locator('select');
    await selects.nth(0).selectOption({ label: l1Name });
    await selects.nth(1).selectOption({ label: l2Name });

    await expect(page.locator('.goods-card').filter({ hasText: goodsA.name })).toHaveCount(1);
    await expect(page.locator('.goods-card').filter({ hasText: goodsB.name })).toHaveCount(0);
  });

  test('T02-04 商品详情展示价格、库存与描述', async ({ page }) => {
    await page.goto(`${BUYER_URL}/goods`);
    await buyerOpenGoods(page, goodsA.name);

    await expect(page.locator('.summary .title')).toHaveText(goodsA.name);
    // 买家端金额统一两位小数（formatMoney → toFixed(2)），不再出现阶段二的 ¥99.9
    await expect(page.locator('.summary .price')).toContainText('¥99.90');
    await expect(page.locator('.summary')).toContainText('库存 2');
    await expect(page.locator('.description')).toContainText('E2E 自动创建的商品描述');
  });
});
