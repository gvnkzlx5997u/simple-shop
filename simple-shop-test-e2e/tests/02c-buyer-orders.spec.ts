import { expect, test, type APIRequestContext } from '@playwright/test';

import {
  addToCart,
  advanceOrder,
  createL1,
  createL2,
  listBuyerOrders,
  newApi,
  newBuyer,
  placeOrder,
  publishGoods,
  sellerLogin,
} from '../helpers/api';
import type { GoodsView } from '../helpers/api';
import { buyerFixture, goodsFixture, uniqueName } from '../helpers/data';
import { BUYER_URL } from '../helpers/config';
import { buyerLoginUi, buyerOpenCart, buyerOpenOrders } from '../helpers/ui';

/**
 * 02c 买家订单列表与详情（阶段F 新增，收编 d5 的 D 组缺口）
 *
 * 覆盖：单件下单跳详情、分页与翻页、切筛选回第 1 页、空态清筛选、
 * 订单头与明细两次独立请求 + 明细失败可单独重试。
 * 造数走接口（placeOrder 循环造多笔），不连数据库。
 */
test.describe.serial('02c 买家订单列表与详情', () => {
  let ctx: APIRequestContext;
  let sellerToken: string;
  let buyerToken: string;
  let goods: GoodsView;
  const buyer = buyerFixture('orders');
  /** beforeAll 预造 12 笔，保证分页存在;T02c-01 再经购物车加 1 笔。 */
  const PREORDERS = 12;

  test.beforeAll(async () => {
    ctx = await newApi();
    sellerToken = await sellerLogin(ctx);
    const tag = uniqueName('ord');
    const l1 = await createL1(ctx, sellerToken, `订单一级-${tag}`);
    const l2 = await createL2(ctx, sellerToken, l1.id, `订单二级-${tag}`);
    goods = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('订单商品'), l2.id, { stock: 100, price: '15.00' }),
    );
    buyerToken = await newBuyer(ctx, buyer);
    for (let i = 0; i < PREORDERS; i += 1) {
      await placeOrder(ctx, buyerToken, [{ goods_id: goods.id, quantity: 1 }]);
    }
  });

  test.afterAll(async () => {
    await ctx.dispose();
  });

  test('T02c-01 单件经购物车下单 → 跳详情、待支付、金额正确', async ({ page }) => {
    await addToCart(ctx, buyerToken, goods.id);
    await buyerLoginUi(page, buyer.username, buyer.password);
    await buyerOpenCart(page);
    await page.getByRole('button', { name: '去结算' }).click();

    await expect(page).toHaveURL(/\/checkout$/);
    await expect(page.getByTestId('checkout-address').first()).toBeChecked();
    await page.getByTestId('checkout-submit').click();

    await expect(page).toHaveURL(/\/orders\/[^/]+$/);
    await expect(page.getByTestId('status-tag')).toHaveText('待支付');
    await expect(page.getByTestId('order-total')).toContainText('¥15.00');
    await expect(page.getByTestId('order-items').locator('tbody tr')).toHaveCount(1);
    await expect(page.getByTestId('order-pay-entry')).toBeVisible();
    await expect(page.getByTestId('pay-countdown')).toBeVisible();
  });

  test('T02c-02 订单分页：第 1 页 10 笔，翻第 2 页余量正确', async ({ page }) => {
    const { total } = await listBuyerOrders(ctx, buyerToken);
    expect(total, '预造 + 购物车单应超过一页').toBeGreaterThan(10);
    const totalPages = Math.ceil(total / 10);

    await buyerLoginUi(page, buyer.username, buyer.password);
    await buyerOpenOrders(page);
    await expect(page.getByTestId('order-row')).toHaveCount(10);
    await expect(page.getByText(`第 1 / ${totalPages} 页 · 共 ${total} 笔`)).toBeVisible();

    await page.getByRole('button', { name: '下一页' }).click();
    await expect(page.getByTestId('order-row')).toHaveCount(total - 10);
    await expect(page.getByText(`第 2 / ${totalPages} 页 · 共 ${total} 笔`)).toBeVisible();
  });

  test('T02c-03 第 2 页切筛选回第 1 页；空态可清筛选', async ({ page }) => {
    // 造两笔已取消，供筛选
    const { items } = await listBuyerOrders(ctx, buyerToken);
    await advanceOrder(ctx, sellerToken, items[0]!.order_id, 'cancel');
    await advanceOrder(ctx, sellerToken, items[1]!.order_id, 'cancel');

    await buyerLoginUi(page, buyer.username, buyer.password);
    await buyerOpenOrders(page);
    await page.getByRole('button', { name: '下一页' }).click();
    await expect(page.getByText(/第 2 \//)).toBeVisible();

    // 切筛选必须回第 1 页
    await page.getByLabel('按状态筛选').selectOption({ label: '已取消' });
    await expect(page.getByText('第 1 / 1 页 · 共 2 笔')).toBeVisible();
    await expect(page.getByTestId('order-row')).toHaveCount(2);

    // 选一个没有订单的状态 → 空态 + 查看全部
    await page.getByLabel('按状态筛选').selectOption({ label: '已发货' });
    await expect(page.getByTestId('empty-state')).toContainText('没有「已发货」的订单');
    await page.getByRole('button', { name: '查看全部订单' }).click();
    await expect(page.getByTestId('order-row')).toHaveCount(10);
  });

  test('T02c-04 订单头与明细独立请求：明细失败头可读、可单独重试', async ({ page }) => {
    const { items } = await listBuyerOrders(ctx, buyerToken);
    const orderId = items[0]!.order_id;

    const urls: string[] = [];
    page.on('request', (r) => urls.push(r.url()));

    // 先拦掉明细请求：头应照常渲染，明细给重试
    await page.route('**/api/buyer/orders/*/items', (route) => route.abort());
    await buyerLoginUi(page, buyer.username, buyer.password);
    await page.goto(`${BUYER_URL}/orders/${orderId}`);

    await expect(page.getByTestId('status-tag')).toBeVisible();
    await expect(page.getByTestId('order-total')).toBeVisible();
    await expect(page.getByTestId('order-items')).toHaveCount(0);
    await expect(page.getByRole('button', { name: '重试' })).toBeVisible();

    // 头与明细确实是两次独立请求
    expect(urls.some((u) => u.includes(`/api/buyer/orders/${orderId}`))).toBeTruthy();
    expect(urls.some((u) => u.includes(`/api/buyer/orders/${orderId}/items`))).toBeTruthy();

    // 放行后单独重试明细，不需要重拉整页
    await page.unroute('**/api/buyer/orders/*/items');
    await page.getByRole('button', { name: '重试' }).click();
    await expect(page.getByTestId('order-items').locator('tbody tr')).toHaveCount(1);
  });
});