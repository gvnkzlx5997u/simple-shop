import { expect, test, type APIRequestContext } from '@playwright/test';

import {
  call,
  createL1,
  createL2,
  getSellerGoods,
  newApi,
  offShelf,
  publishGoods,
  reList,
  sellerHistory,
  sellerLogin,
  submitIntention,
  updateStock,
} from '../helpers/api';
import { buyerFixture, goodsFixture, uniqueName } from '../helpers/data';
import { SELLER_URL } from '../helpers/config';
import { confirmDialog, goodsCard, openGoodsDetail, sellerLoginUi } from '../helpers/ui';
import { newBuyer } from '../helpers/api';

/**
 * 03 卖家商品：发布 / 库存 / 上下架 / 类别保护
 *
 * 关键口径：
 *  - 发布后名称/价格/图片/描述不可改，只有库存能调（澄清#12、#18）
 *  - 库存改 0 → 自动「已售罄」下架；改回 >0 → 自动回「在售」（分析§4、澄清#26）
 *  - 手动下架要求队列为空（BR-09），下架属可复售、非终态（状态模型 v1.2）
 */
test.describe('03 卖家商品与库存', () => {
  let ctx: APIRequestContext;
  let sellerToken: string;
  let l2Id: string;
  let l1Name: string;
  let l2Name: string;

  test.beforeAll(async () => {
    ctx = await newApi();
    sellerToken = await sellerLogin(ctx);
    const tag = uniqueName('g');
    l1Name = `货一级-${tag}`;
    l2Name = `货二级-${tag}`;
    const l1 = await createL1(ctx, sellerToken, l1Name);
    const l2 = await createL2(ctx, sellerToken, l1.id, l2Name);
    l2Id = l2.id;
  });

  test.afterAll(async () => {
    await ctx.dispose();
  });

  test('T03-01 发布商品：填表 + 二次确认 → 列表可见', async ({ page }) => {
    const name = uniqueName('发布');
    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/publish`);

    await page.getByLabel('商品名称').fill(name);
    await page.getByLabel('价格(元)').fill('123.45');
    await page.getByLabel('库存(件)').fill('4');
    await page.getByLabel('请选择一级类别').selectOption({ label: l1Name });
    await page.getByLabel('请选择二级类别').selectOption({ label: l2Name });

    await page.getByRole('button', { name: '发布商品' }).click();
    // 二次确认弹窗
    await page.getByRole('dialog').getByRole('button', { name: '确认发布' }).click();

    // 回到列表，用搜索定位（列表可能有很多历史商品）
    await expect(page.getByRole('heading', { name: '商品列表' })).toBeVisible();
    await page.getByLabel('搜索商品名称').fill(name);
    await page.getByRole('button', { name: '搜索' }).click();
    await expect(goodsCard(page, name)).toHaveCount(1);
  });

  test('T03-02 库存改 0 自动售罄，改回 >0 自动回在售', async ({ page }) => {
    const name = uniqueName('库存');
    const goods = await publishGoods(ctx, sellerToken, goodsFixture(name, l2Id, { stock: 3 }));

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/`);
    await page.getByLabel('搜索商品名称').fill(name);
    await page.getByRole('button', { name: '搜索' }).click();
    await openGoodsDetail(page, name);

    // 改 0 → 已售罄
    await page.getByRole('button', { name: '修改库存' }).click();
    await page.locator('.goods__stock-input').fill('0');
    await page.getByRole('button', { name: '保存' }).click();
    await expect(page.locator('.tag').filter({ hasText: '已售罄' })).toBeVisible();

    // 改回 5 → 在售
    await page.getByRole('button', { name: '修改库存' }).click();
    await page.locator('.goods__stock-input').fill('5');
    await page.getByRole('button', { name: '保存' }).click();
    await expect(page.locator('.tag').filter({ hasText: '在售' })).toBeVisible();

    const after = await getSellerGoods(ctx, sellerToken, goods.id);
    expect(after.stock).toBe(5);
    expect(after.status).toBe('ON_SALE');
  });

  test('T03-03 手动下架（队列为空）→ 已下架，且不进历史', async ({ page }) => {
    const name = uniqueName('下架');
    const goods = await publishGoods(ctx, sellerToken, goodsFixture(name, l2Id, { stock: 2 }));

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/`);
    await page.getByLabel('搜索商品名称').fill(name);
    await page.getByRole('button', { name: '搜索' }).click();
    await openGoodsDetail(page, name);

    await page.getByRole('button', { name: '下架', exact: true }).click();
    await confirmDialog(page);
    await expect(page.locator('.tag').filter({ hasText: '已下架' })).toBeVisible();

    // 口径注记：状态模型 v1.2 说手动下架非终态、不进归档；澄清#18 说"历史→已下架 分区可见"。
    // 这里按当前实现断言（不进历史），与 E2E旧用例对照表 §5 第 3 条一致。
    const history = await sellerHistory(ctx, sellerToken);
    const ids = (history.items as Array<{ id?: string; goods_id?: string }>).map(
      (item) => item.id ?? item.goods_id,
    );
    expect(ids).not.toContain(goods.id);
  });

  test('T03-04 重新上架 → 回到在售', async ({ page }) => {
    const name = uniqueName('上架');
    const goods = await publishGoods(ctx, sellerToken, goodsFixture(name, l2Id, { stock: 2 }));
    await offShelf(ctx, sellerToken, goods.id);

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/`);
    await page.getByLabel('搜索商品名称').fill(name);
    await page.getByRole('button', { name: '搜索' }).click();
    await openGoodsDetail(page, name);

    await page.getByRole('button', { name: '重新上架' }).click();
    await confirmDialog(page);
    await expect(page.locator('.tag').filter({ hasText: '在售' })).toBeVisible();

    const after = await getSellerGoods(ctx, sellerToken, goods.id);
    expect(after.status).toBe('ON_SALE');
  });

  test('T03-05 队列非空时手动下架 → 被拒绝并提示', async ({ page }) => {
    const name = uniqueName('有队列');
    const goods = await publishGoods(ctx, sellerToken, goodsFixture(name, l2Id, { stock: 2 }));

    const buyer = buyerFixture('q');
    const buyerToken = await newBuyer(ctx, buyer);
    await submitIntention(ctx, buyerToken, goods.id);

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/`);
    await page.getByLabel('搜索商品名称').fill(name);
    await page.getByRole('button', { name: '搜索' }).click();
    await openGoodsDetail(page, name);

    await page.getByRole('button', { name: '下架', exact: true }).click();
    await confirmDialog(page);

    await expect(page.locator('.toast').filter({ hasText: '队列中仍有买家' })).toBeVisible();
    const after = await getSellerGoods(ctx, sellerToken, goods.id);
    expect(after.status).toBe('ON_SALE');
  });

  test('T03-06 删除被商品引用的类别 → 拒绝（20006）', async () => {
    const result = await call(ctx, 'DELETE', `/api/seller/categories/l2/${l2Id}`, undefined, {
      token: sellerToken,
    });
    expect(result.body.code).toBe(20006);
  });

  test('T03-07 库存接口：负数被拒', async () => {
    const name = uniqueName('负数');
    const goods = await publishGoods(ctx, sellerToken, goodsFixture(name, l2Id, { stock: 2 }));
    const result = await call(ctx, 'PUT', `/api/seller/goods/${goods.id}/stock`, { stock: -1 }, {
      token: sellerToken,
    });
    expect(result.body.code).not.toBe(0);
  });
});
