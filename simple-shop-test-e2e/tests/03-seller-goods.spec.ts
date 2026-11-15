import { expect, test, type APIRequestContext } from '@playwright/test';

import {
  call,
  categoryTree,
  changeGoodsCategory,
  createL1,
  createL2,
  getSellerGoods,
  newApi,
  offShelf,
  newBuyer,
  placeOrder,
  publishGoods,
  sellerLogin,
  updateStock,
} from '../helpers/api';
import { buyerFixture, goodsFixture, uniqueName } from '../helpers/data';
import { SELLER_URL } from '../helpers/config';
import {
  confirmDialog,
  openSellerGoods,
  sellerGoodsCard,
  sellerLoginUi,
  sellerSearchGoods,
  statusTags,
  toast,
} from '../helpers/ui';

/**
 * 03 卖家商品：发布 / 库存 / 两态下架 / 类别保护
 *
 * 阶段三口径：
 *  - 商品只有 ON_SALE / OFF_SHELF 两态，下架原因 MANUAL / STOCK_ZERO / SOLD_OUT 与状态成对出现；
 *  - 手动下架是终态：没有重新上架接口，库存只读，类别仍可改挂；
 *  - 库存耗尽 / 已售下架的商品，库存改回 >0 保存即自动上架；
 *  - 有未完成订单时禁止手动下架、禁止手动清零（20020）。
 *
 * 阶段二的三条已删除：T03-04「重新上架」、T03-10「缺 confirm → 20028」（接口与码位都已下线），
 * 以及「下架后不进历史」（历史页已删除，改由「已下架 + 原因」筛选承担）。
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

    // 卖家端表单是 fieldset/legend 结构，legend 不给 input 起名，用 id 定位
    await page.locator('#goods-name').fill(name);
    await page.locator('#goods-price').fill('123.45');
    await page.locator('#goods-stock').fill('4');

    // 类别树是异步拉的：先等选项出现再选，否则 selectOption 会抢跑
    const l1Select = page.getByLabel('请选择一级类别');
    await expect(l1Select.locator('option', { hasText: l1Name })).toHaveCount(1);
    await l1Select.selectOption({ label: l1Name });
    await page.getByLabel('请选择二级类别').selectOption({ label: l2Name });

    await page.getByRole('button', { name: '发布商品' }).click();
    await confirmDialog(page, '确认发布');

    await expect(page.getByRole('heading', { name: '商品列表' })).toBeVisible();
    await sellerSearchGoods(page, name);
    await expect(sellerGoodsCard(page, name)).toHaveCount(1);
  });

  test('T03-02 库存改 0 自动下架（库存耗尽），改回 >0 自动上架', async ({ page }) => {
    const name = uniqueName('库存');
    const goods = await publishGoods(ctx, sellerToken, goodsFixture(name, l2Id, { stock: 3 }));

    await sellerLoginUi(page);
    await sellerSearchGoods(page, name);
    await openSellerGoods(page, name);

    // 改 0 → 已下架 + 库存耗尽
    await page.getByRole('button', { name: '修改库存' }).click();
    await page.locator('.goods__stock-input').fill('0');
    await page.getByRole('button', { name: '保存' }).click();
    await expect(statusTags(page).filter({ hasText: '已下架' })).toBeVisible();
    await expect(statusTags(page).filter({ hasText: '库存耗尽' })).toBeVisible();

    // 改回 5 → 在售，且原因清空
    await page.getByRole('button', { name: '修改库存' }).click();
    await page.locator('.goods__stock-input').fill('5');
    await page.getByRole('button', { name: '保存' }).click();
    await expect(statusTags(page).filter({ hasText: '在售' })).toBeVisible();
    await expect(statusTags(page).filter({ hasText: '库存耗尽' })).toHaveCount(0);

    const after = await getSellerGoods(ctx, sellerToken, goods.id);
    expect(after.stock).toBe(5);
    expect(after.status).toBe('ON_SALE');
    expect(after.off_shelf_reason).toBeNull();
  });

  test('T03-03 手动下架 → 终态：库存只读、无上架入口，但可按原因筛出', async ({ page }) => {
    const name = uniqueName('下架');
    const goods = await publishGoods(ctx, sellerToken, goodsFixture(name, l2Id, { stock: 2 }));

    await sellerLoginUi(page);
    await sellerSearchGoods(page, name);
    await openSellerGoods(page, name);

    await page.getByRole('button', { name: '下架', exact: true }).click();
    await confirmDialog(page);

    await expect(statusTags(page).filter({ hasText: '已下架' })).toBeVisible();
    await expect(statusTags(page).filter({ hasText: '手动下架' })).toBeVisible();
    // 终态：库存不可改，但没有重新上架入口，类别仍可改挂
    await expect(page.getByRole('button', { name: '修改库存' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: '重新上架' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: '改挂类别' })).toBeVisible();
    await expect(page.getByText('手动下架为终态：库存不可再修改，类别仍可改挂')).toBeVisible();

    const after = await getSellerGoods(ctx, sellerToken, goods.id);
    expect(after.status).toBe('OFF_SHELF');
    expect(after.off_shelf_reason).toBe('MANUAL');
    expect(after.off_shelf_at).toBeTruthy();

    // 「已下架 + 手动下架」筛选能查到（承担原「历史商品」的查询视图）
    await page.goto(`${SELLER_URL}/?status=OFF_SHELF&reason=MANUAL`);
    await sellerSearchGoods(page, name);
    await expect(sellerGoodsCard(page, name)).toHaveCount(1);
    await expect(sellerGoodsCard(page, name)).toContainText('手动下架');
  });

  test('T03-05 有未完成订单时手动下架 → 拒绝（20020），状态不变', async ({ page }) => {
    const name = uniqueName('有订单');
    const goods = await publishGoods(ctx, sellerToken, goodsFixture(name, l2Id, { stock: 2 }));

    const buyerToken = await newBuyer(ctx, buyerFixture('q'));
    await placeOrder(ctx, buyerToken, [{ goods_id: goods.id, quantity: 1 }]);

    await sellerLoginUi(page);
    await sellerSearchGoods(page, name);
    await openSellerGoods(page, name);

    await page.getByRole('button', { name: '下架', exact: true }).click();
    await confirmDialog(page);

    await expect(toast(page).filter({ hasText: '未完成订单' })).toBeVisible();
    const after = await getSellerGoods(ctx, sellerToken, goods.id);
    expect(after.status, '被拒后商品状态不应变化').toBe('ON_SALE');
  });

  test('T03-06 删除被商品引用的类别 → 拒绝（20006）', async () => {
    const result = await call(ctx, 'DELETE', `/api/seller/categories/l2/${l2Id}`, undefined, {
      token: sellerToken,
    });
    expect(result.body.code).toBe(20006);
  });

  test('T03-07 库存接口：负数被拒（400 + 50002）', async () => {
    const goods = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('负数'), l2Id, { stock: 2 }),
    );
    const result = await call(ctx, 'PUT', `/api/seller/goods/${goods.id}/stock`, { stock: -1 }, {
      token: sellerToken,
    });
    // 负数在 DTO 的 @Min(0) 就被拦下，走的是参数错误而不是业务码
    expect(result.status).toBe(400);
    expect(result.body.code).toBe(50002);
  });

  test('T03-08 按「状态 + 下架原因」筛选：库存耗尽只出该类商品', async ({ page }) => {
    const saleName = uniqueName('筛在售');
    const zeroName = uniqueName('筛缺货');
    await publishGoods(ctx, sellerToken, goodsFixture(saleName, l2Id, { stock: 3 }));
    const zero = await publishGoods(ctx, sellerToken, goodsFixture(zeroName, l2Id, { stock: 3 }));
    await updateStock(ctx, sellerToken, zero.id, 0);

    await sellerLoginUi(page);
    // 状态与原因是同一个下拉（拆开容易选出后端会拒的组合）
    await page.getByLabel('按状态与下架原因筛选').selectOption({ label: '库存耗尽' });

    await expect(sellerGoodsCard(page, zeroName)).toHaveCount(1);
    await expect(sellerGoodsCard(page, saleName)).toHaveCount(0);
  });

  test('T03-09 类别可改名，空类别可删除', async () => {
    const tag = uniqueName('类改');
    const l1 = await createL1(ctx, sellerToken, `改名一级-${tag}`);
    const l2 = await createL2(ctx, sellerToken, l1.id, `改名二级-${tag}`);

    const renamed = await call(ctx, 'PUT', `/api/seller/categories/l2/${l2.id}`, {
      name: `已改名-${tag}`,
    }, { token: sellerToken });
    expect(renamed.body.code).toBe(0);

    const tree = await categoryTree(ctx);
    const found = tree.find((node) => node.id === l1.id);
    expect(found?.children.map((child) => child.name)).toContain(`已改名-${tag}`);

    const removed = await call(ctx, 'DELETE', `/api/seller/categories/l2/${l2.id}`, undefined, {
      token: sellerToken,
    });
    expect(removed.body.code).toBe(0);
  });

  test('T03-11 类别管理页：手工新增一级与二级类别', async ({ page }) => {
    const tag = uniqueName('界面类');
    const l1Name = `界面一级-${tag}`;
    const l2Name = `界面二级-${tag}`;

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/categories`);
    await expect(page.getByRole('heading', { name: '类别管理' })).toBeVisible();

    // 新增一级
    await page.getByLabel('新一级类别名称').fill(l1Name);
    await page.getByRole('button', { name: '添加一级类别' }).click();
    const card = page.locator('.card').filter({ hasText: l1Name });
    await expect(card).toBeVisible();

    // 在这一级下新增二级（行内表单：填名 + 保存）
    await card.getByRole('button', { name: '添加二级类别' }).click();
    // 用 role=textbox：输入框的 aria-label 与保存按钮的 aria-label（保存：新二级类别名称）会同时命中 getByLabel
    await card.getByRole('textbox', { name: '新二级类别名称' }).fill(l2Name);
    await card.getByRole('button', { name: '保存：新二级类别名称' }).click();
    await expect(card).toContainText(l2Name);

    // 服务端确认树结构
    const tree = await categoryTree(ctx);
    const node = tree.find((item) => item.name === l1Name);
    expect(node, '新建的一级类别应出现在类别树里').toBeTruthy();
    expect(node!.children.map((child) => child.name)).toContain(l2Name);
  });

  test('T03-12 手动下架后：改库存被拒 20023，但类别仍可改挂', async () => {
    const name = uniqueName('终态');
    const goods = await publishGoods(ctx, sellerToken, goodsFixture(name, l2Id, { stock: 2 }));
    await offShelf(ctx, sellerToken, goods.id);

    // 终态商品库存只读：界面上没有入口，绕过前端直接调也会被拒
    const stockResult = await call(ctx, 'PUT', `/api/seller/goods/${goods.id}/stock`, { stock: 5 }, {
      token: sellerToken,
    });
    expect(stockResult.body.code, '手动下架后不能改库存').toBe(20023);

    // 类别仍可改挂（否则永久引用会阻塞类别删除）
    const tag = uniqueName('改挂');
    const otherL1 = await createL1(ctx, sellerToken, `改挂一级-${tag}`);
    const otherL2 = await createL2(ctx, sellerToken, otherL1.id, `改挂二级-${tag}`);
    const moved = await changeGoodsCategory(ctx, sellerToken, goods.id, otherL2.id);
    expect(moved.status).toBe('OFF_SHELF');
    expect(moved.off_shelf_reason).toBe('MANUAL');
  });

  test('T03-13 状态+原因筛选项写进 URL，刷新后仍是同一个视图', async ({ page }) => {
    const zeroName = uniqueName('筛URL');
    const zero = await publishGoods(ctx, sellerToken, goodsFixture(zeroName, l2Id, { stock: 2 }));
    await updateStock(ctx, sellerToken, zero.id, 0);

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/`);
    await page.getByLabel('按状态与下架原因筛选').selectOption({ label: '库存耗尽' });

    await expect(page).toHaveURL(/status=OFF_SHELF/);
    await expect(page).toHaveURL(/reason=STOCK_ZERO/);
    await expect(sellerGoodsCard(page, zeroName)).toHaveCount(1);

    // 刷新后筛选条件与结果都还原
    await page.reload();
    await expect(page.getByLabel('按状态与下架原因筛选')).toHaveValue('OFF_SHELF:STOCK_ZERO');
    await expect(sellerGoodsCard(page, zeroName)).toHaveCount(1);
  });
});
