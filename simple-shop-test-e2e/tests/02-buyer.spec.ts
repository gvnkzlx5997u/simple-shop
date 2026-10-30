import { expect, test, type APIRequestContext } from '@playwright/test';

import {
  createL1,
  createL2,
  newApi,
  newBuyer,
  publishGoods,
  sellerLogin,
  submitIntention,
} from '../helpers/api';
import { buyerFixture, goodsFixture, uniqueName } from '../helpers/data';
import { BUYER_URL } from '../helpers/config';
import { buyerLoginUi, buyerOpenGoods } from '../helpers/ui';

/**
 * 02 买家端：列表 / 搜索 / 类别筛选 / 详情 / 提交意向 / 我的历史
 *
 * 造数全部走接口（快且稳），被测流程走真实 UI。
 * 关键口径：
 *  - 买家匿名可看在售商品（人工分析 §15；基线 AC-12 已作废）
 *  - 搜索只匹配名称（Backlog ID6）
 *  - 提交意向不等于下单完成，历史里看不到（分析§5、澄清#16）
 */
test.describe('02 买家端浏览与下单', () => {
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
    // A：名称含搜索词；B：名称不含，但描述含 —— 用来验证"只匹配名称"
    goodsA = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(`商品A-${keyword}`, l2Id, { stock: 2, price: '99.90' }),
    );
    goodsB = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('商品B'), otherL2Id, {
        stock: 1,
        description: [{ type: 'TEXT', text: `描述里含关键字 ${keyword}` }],
      }),
    );
  });

  test.afterAll(async () => {
    await ctx.dispose();
  });

  test('T02-01 匿名可看在售列表，且能搜到名称命中的商品', async ({ page }) => {
    await page.goto(`${BUYER_URL}/goods`);
    await page.getByPlaceholder('搜索商品名称或描述').fill(keyword);
    await page.getByRole('button', { name: '搜索' }).click();

    await expect(page.locator('.goods-card').filter({ hasText: goodsA.name })).toHaveCount(1);
  });

  test('T02-02 搜索不匹配描述（只匹配名称）', async ({ page }) => {
    await page.goto(`${BUYER_URL}/goods`);
    await page.getByPlaceholder('搜索商品名称或描述').fill(keyword);
    await page.getByRole('button', { name: '搜索' }).click();
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
    // 注：后端返回 99.90，前端当作数字渲染成「¥99.9」—— 末尾 0 不保留（见 README「已知现象」）
    await expect(page.locator('.summary .price')).toContainText(/¥\s*99\.9/);
    await expect(page.locator('.summary')).toContainText('库存 2');
    await expect(page.getByRole('heading', { name: '商品描述' })).toBeVisible();
    await expect(page.locator('.description')).toContainText('E2E 自动创建的商品描述');
  });

  test('T02-05 登录后提交意向 → 提示已提交', async ({ page }) => {
    const fixture = buyerFixture('want');
    await newBuyer(ctx, fixture);

    await buyerLoginUi(page, fixture.username, fixture.password);
    await page.goto(`${BUYER_URL}/goods`);
    await buyerOpenGoods(page, goodsA.name);

    await page.getByRole('button', { name: '我要购买' }).click();
    await expect(page.locator('.notice')).toContainText('已提交购买意向');
  });

  test('T02-06 我的历史：排队中的意向不可见', async ({ page }) => {
    const fixture = buyerFixture('hist');
    const token = await newBuyer(ctx, fixture);
    // 先制造一条"排队中"的意向
    await submitIntention(ctx, token, goodsA.id);

    await buyerLoginUi(page, fixture.username, fixture.password);
    await page.goto(`${BUYER_URL}/history`);

    // 没有终态记录 → 空态提示，且不出现商品名
    await expect(page.locator('.muted').filter({ hasText: '还没有已结束的下单记录' })).toBeVisible();
    await expect(page.locator('table')).toHaveCount(0);
  });
});
