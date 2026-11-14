import { expect, test, type APIRequestContext } from '@playwright/test';

import {
  advanceOrder,
  addToCart,
  call,
  createL1,
  createL2,
  getSellerGoods,
  listBuyerOrders,
  listCart,
  listSellerOrders,
  newApi,
  newBuyer,
  offShelf,
  placeOrder,
  publishGoods,
  sellerLogin,
} from '../helpers/api';
import type { GoodsView } from '../helpers/api';
import { buyerFixture, goodsFixture, uniqueName } from '../helpers/data';
import { BUYER_URL, SELLER_URL } from '../helpers/config';
import {
  buyerCartRow,
  buyerLoginUi,
  buyerOpenCart,
  openSellerOrder,
  sellerLoginUi,
  statusTags,
  toast,
} from '../helpers/ui';

/**
 * 08 买卖两端联调（E4-1 的自动化证据，Backlog 1/3/8/16/20）
 *
 * 为什么单独一组：卖家端的 03/04 只证明「卖家自己那半边对」，买家端的订单页只证明「买家自己能看」。
 * 真正容易坏的是两端的口径对不上——同一笔订单两个状态、库存扣了但买家看不到、下架拦截只在某一端生效。
 * 这里统一走「买家 UI 下单 → 卖家接口/UI 推进 → 回到买家 UI 看结果」的闭环。
 */
test.describe.serial('08 买卖两端联调', () => {
  let ctx: APIRequestContext;
  let sellerToken: string;
  let l2Id: string;
  let seq = 0;

  async function newGoods(stock: number, price = '15.00'): Promise<GoodsView> {
    seq += 1;
    return publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName(`联调商品${seq}`), l2Id, { stock, price }),
    );
  }

  async function newBuyerToken() {
    const fixture = buyerFixture('x');
    const token = await newBuyer(ctx, fixture);
    return { fixture, token };
  }

  test.beforeAll(async () => {
    ctx = await newApi();
    sellerToken = await sellerLogin(ctx);
    const tag = uniqueName('ce');
    const l1 = await createL1(ctx, sellerToken, `联调一级-${tag}`);
    const l2 = await createL2(ctx, sellerToken, l1.id, `联调二级-${tag}`);
    l2Id = l2.id;
  });

  test.afterAll(async () => {
    await ctx.dispose();
  });

  test('T08-01 买家加购两件结算 → 一笔订单两行明细，卖家列表能看到', async ({ page }) => {
    const goodsA = await newGoods(4, '12.50');
    const goodsB = await newGoods(4, '20.00');
    const { fixture, token } = await newBuyerToken();

    await addToCart(ctx, token, goodsA.id);
    await addToCart(ctx, token, goodsB.id);

    await buyerLoginUi(page, fixture.username, fixture.password);
    await buyerOpenCart(page);
    const rowA = buyerCartRow(page, goodsA.name);
    await rowA.locator('.qty-input').fill('2');
    await rowA.locator('.qty-input').blur();
    await expect(page.locator('.total strong')).toHaveText('¥45.00');

    await page.getByRole('button', { name: '去结算' }).click();
    await expect(page).toHaveURL(/\/orders\/[^/]+$/);

    // 卖家侧：同一笔订单，两行明细
    const sellerPage = await listSellerOrders(ctx, sellerToken, { page_size: 100 });
    const orders = await listBuyerOrders(ctx, token);
    const latest = orders.items[0];
    const hit = sellerPage.items.find((item) => item.order_id === latest.order_id);
    expect(hit, '卖家订单列表应出现买家刚下的单').toBeTruthy();
    expect(hit!.item_count).toBe(2);
    expect(Number(hit!.total_amount)).toBe(45);

    // 购物车里已下单的行被清掉
    const cart = await listCartCount(token);
    expect(cart).toBe(0);
  });

  test('T08-02 卖家推进 → 买家侧状态同步可见', async ({ page }) => {
    const goods = await newGoods(3);
    const { fixture, token } = await newBuyerToken();
    const placed = await placeOrderViaApi(token, goods.id, 1);

    await advanceOrder(ctx, sellerToken, placed.order_id, 'confirm');
    await advanceOrder(ctx, sellerToken, placed.order_id, 'prepare');
    await advanceOrder(ctx, sellerToken, placed.order_id, 'ship');

    await buyerLoginUi(page, fixture.username, fixture.password);
    await page.goto(`${BUYER_URL}/orders/${placed.order_id}`);
    await expect(page.locator('.badge')).toHaveText('已发货');
    // 已发货后买家侧不再有取消入口
    await expect(page.getByRole('button', { name: '取消订单' })).toHaveCount(0);
  });

  test('T08-03 卖家完成 → 买家看到交易完成，库存按明细扣减', async ({ page }) => {
    const goods = await newGoods(5);
    const { fixture, token } = await newBuyerToken();
    const placed = await placeOrderViaApi(token, goods.id, 2);

    await advanceOrder(ctx, sellerToken, placed.order_id, 'confirm');
    await advanceOrder(ctx, sellerToken, placed.order_id, 'prepare');
    await advanceOrder(ctx, sellerToken, placed.order_id, 'ship');
    await advanceOrder(ctx, sellerToken, placed.order_id, 'complete');

    const after = await getSellerGoods(ctx, sellerToken, goods.id);
    expect(after.stock, '完成时按明细扣 2').toBe(3);

    await buyerLoginUi(page, fixture.username, fixture.password);
    await page.goto(`${BUYER_URL}/orders/${placed.order_id}`);
    await expect(page.locator('.badge')).toHaveText('交易完成');
    await expect(page.locator('.items tbody tr')).toHaveCount(1);
  });

  test('T08-04 发货前买家取消 → 卖家侧变已取消，推进按钮消失', async ({ page }) => {
    const goods = await newGoods(3);
    const { fixture, token } = await newBuyerToken();
    const placed = await placeOrderViaApi(token, goods.id, 1);

    await buyerLoginUi(page, fixture.username, fixture.password);
    await page.goto(`${BUYER_URL}/orders/${placed.order_id}`);
    await page.getByRole('button', { name: '取消订单' }).click();
    await page.getByRole('button', { name: '确认取消' }).click();
    await expect(page.locator('.badge')).toHaveText('已取消');

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/orders`);
    await openSellerOrder(page, placed.order_no);
    await expect(statusTags(page).filter({ hasText: '已取消' })).toBeVisible();
    await expect(page.getByTestId('order-next')).toHaveCount(0);
    await expect(page.getByTestId('order-cancel')).toHaveCount(0);
  });

  test('T08-05 发货后买家不能取消：界面无入口、接口 60003', async ({ page }) => {
    const goods = await newGoods(3);
    const { fixture, token } = await newBuyerToken();
    const placed = await placeOrderViaApi(token, goods.id, 1);
    await advanceOrder(ctx, sellerToken, placed.order_id, 'confirm');
    await advanceOrder(ctx, sellerToken, placed.order_id, 'prepare');
    await advanceOrder(ctx, sellerToken, placed.order_id, 'ship');

    await buyerLoginUi(page, fixture.username, fixture.password);
    await page.goto(`${BUYER_URL}/orders/${placed.order_id}`);
    await expect(page.locator('.badge')).toHaveText('已发货');
    await expect(page.getByRole('button', { name: '取消订单' })).toHaveCount(0);

    const result = await call(ctx, 'POST', `/api/buyer/orders/${placed.order_id}/cancel`, {}, {
      token,
    });
    expect(result.body.code, '已发货后买家取消应被拒').toBe(60003);
  });

  test('T08-06 有未完成订单时卖家下架被拒，取消订单后可以下架', async ({ page }) => {
    const goods = await newGoods(2);
    const { token } = await newBuyerToken();
    const placed = await placeOrderViaApi(token, goods.id, 1);

    // 界面上点「下架」→ 被 20020 拦住
    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/goods/${goods.id}`);
    await page.getByRole('button', { name: '下架', exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: '确认', exact: true }).click();
    await expect(toast(page).filter({ hasText: '未完成订单' })).toBeVisible();
    expect((await getSellerGoods(ctx, sellerToken, goods.id)).status).toBe('ON_SALE');

    // 卖家取消订单后再下架：成功
    await advanceOrder(ctx, sellerToken, placed.order_id, 'cancel');
    await offShelf(ctx, sellerToken, goods.id);
    const after = await getSellerGoods(ctx, sellerToken, goods.id);
    expect(after.status).toBe('OFF_SHELF');
    expect(after.off_shelf_reason).toBe('MANUAL');
  });

  test('T08-07 下单不锁库存：商品仍在售，另一个买家还能下单', async () => {
    const goods = await newGoods(3);
    const first = await newBuyerToken();
    const second = await newBuyerToken();

    const orderA = await placeOrderViaApi(first.token, goods.id, 1);
    expect(orderA.status).toBe('PENDING_CONFIRM');

    // 商品没被冻结/下架，仍然在售
    const mid = await getSellerGoods(ctx, sellerToken, goods.id);
    expect(mid.status).toBe('ON_SALE');

    // 第二个买家同一件商品下单也成功（下单只校验、不预占）
    const orderB = await placeOrderViaApi(second.token, goods.id, 1);
    expect(orderB.status).toBe('PENDING_CONFIRM');

    const orders = await listSellerOrders(ctx, sellerToken, { goods_id: goods.id, page_size: 50 });
    expect(orders.items.filter((item) => item.status === 'PENDING_CONFIRM')).toHaveLength(2);
  });

  test('T08-08 买家订单列表：全部订单、按状态筛选、按下单时间倒序', async ({ page }) => {
    const goods = await newGoods(5);
    const { fixture, token } = await newBuyerToken();
    const older = await placeOrderViaApi(token, goods.id, 1);
    // 下单时间在库里精确到秒：同秒创建的两笔单排序没有确定顺序，
    // 这里隔开一秒，才能稳定断言「倒序」。
    await page.waitForTimeout(1100);
    const newer = await placeOrderViaApi(token, goods.id, 1);
    await advanceOrder(ctx, sellerToken, older.order_id, 'cancel');

    await buyerLoginUi(page, fixture.username, fixture.password);
    await page.goto(`${BUYER_URL}/orders`);

    // 已取消的订单也在「全部」里
    await expect(page.locator('.order-row').filter({ hasText: older.order_no })).toBeVisible();
    await expect(page.locator('.order-row').filter({ hasText: newer.order_no })).toBeVisible();

    // 倒序：最新下的那笔在上
    await expect(page.locator('.order-row').first()).toContainText(newer.order_no);

    // 按状态筛选
    await page.locator('.filter select').selectOption({ label: '已取消' });
    await expect(page.locator('.order-row').filter({ hasText: older.order_no })).toBeVisible();
    await expect(page.locator('.order-row').filter({ hasText: newer.order_no })).toHaveCount(0);

    // 行上要能看到件数与金额
    const row = page.locator('.order-row').filter({ hasText: older.order_no });
    await expect(row).toContainText('共 1 件');
    await expect(row).toContainText('¥15.00');
  });

  // ---- 本文件的小工具 ----

  /** 直下：不经过购物车，造数更快（购物车流程由 02b 负责）。 */
  async function placeOrderViaApi(token: string, goodsId: string, quantity: number) {
    return placeOrder(ctx, token, [{ goods_id: goodsId, quantity }]);
  }

  async function listCartCount(token: string): Promise<number> {
    const cart = await listCart(ctx, token);
    return cart.items.length;
  }
});
