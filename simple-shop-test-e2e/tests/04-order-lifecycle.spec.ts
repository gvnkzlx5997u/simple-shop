import { expect, test, type APIRequestContext } from '@playwright/test';

import {
  advanceOrder,
  call,
  confirmReceipt,
  createL1,
  createL2,
  getSellerGoods,
  newApi,
  newBuyer,
  offShelf,
  payOrder,
  placeOrder,
  publishGoods,
  sellerLogin,
  sellerOrderDetail,
  sellerOrderItems,
  updateStock,
} from '../helpers/api';
import type { GoodsView, OrderStatus } from '../helpers/api';
import { buyerFixture, goodsFixture, uniqueName } from '../helpers/data';
import { SELLER_URL } from '../helpers/config';
import {
  openSellerGoods,
  openSellerOrder,
  sellerAdvance,
  sellerLoginUi,
  sellerOrderRow,
  sellerShip,
  statusTags,
} from '../helpers/ui';

/**
 * 04 订单生命周期（卖家侧，Backlog 4/5/6/7/8/9）
 *
 * 口径：
 *  - 六态整单流转，只能按序推进一格，不可跳级、不可回退；终态没有任何动作；
 *  - 交易完成时按明细数量一次性扣库存，任一明细不足整单拒绝（60004），订单停在已发货；
 *  - 卖家在交易完成前都能取消（发货后也行）；完成后 60003；
 *  - 重复取消按成功处理（防连点），重复推进返回 60002。
 *
 * 阶段二的 `04-trade`（意向队列与交易裁决）已整组删除，本文件是新写的订单用例。
 */
test.describe.serial('04 订单生命周期', () => {
  let ctx: APIRequestContext;
  let sellerToken: string;
  let l2Id: string;
  let seq = 0;

  /** 每个用例自己造商品，避免库存被别的用例吃掉。 */
  async function newGoods(stock: number, price = '10.00'): Promise<GoodsView> {
    seq += 1;
    return publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName(`订单商品${seq}`), l2Id, { stock, price }),
    );
  }

  type PlacedOrder = { orderId: string; orderNo: string; buyerId: string; token: string };

  /** 造一笔订单，返回订单号、id 与买家 token（支付、确认收货都要用它）。 */
  async function newOrder(
    items: Array<{ goods: GoodsView; quantity: number }>,
  ): Promise<PlacedOrder> {
    const token = await newBuyer(ctx, buyerFixture('ord'));
    const placed = await placeOrder(
      ctx,
      token,
      items.map((item) => ({ goods_id: item.goods.id, quantity: item.quantity })),
    );
    const detail = await sellerOrderDetail(ctx, sellerToken, placed.order_id);
    return { orderId: placed.order_id, orderNo: placed.order_no, buyerId: detail.buyer.id, token };
  }

  /**
   * 用接口把订单推到指定状态（造前置状态用，断言仍走 UI）。
   *
   * 阶段四的推进链是「买家付款 → 卖家确认 → 备货 → 发货 → 买家确认收货」：
   * 卖家没有「完成」这个动作了，所以 target=COMPLETED 的最后一步必须由买家调。
   */
  async function drive(order: PlacedOrder, target: OrderStatus): Promise<void> {
    await payOrder(ctx, order.token, order.orderId, 'ALIPAY');
    if (target === 'PAID') return;
    await advanceOrder(ctx, sellerToken, order.orderId, 'confirm');
    if (target === 'CONFIRMED') return;
    await advanceOrder(ctx, sellerToken, order.orderId, 'prepare');
    if (target === 'PREPARED') return;
    await advanceOrder(ctx, sellerToken, order.orderId, 'ship', {
      carrier: '顺丰速运',
      tracking_no: 'SF0000000009',
    });
    if (target === 'SHIPPED') return;
    await confirmReceipt(ctx, order.token, order.orderId);
  }

  test.beforeAll(async () => {
    ctx = await newApi();
    sellerToken = await sellerLogin(ctx);
    const tag = uniqueName('od');
    const l1 = await createL1(ctx, sellerToken, `订单一级-${tag}`);
    const l2 = await createL2(ctx, sellerToken, l1.id, `订单二级-${tag}`);
    l2Id = l2.id;
  });

  test.afterAll(async () => {
    await ctx.dispose();
  });

  test('T04-01 列表：八个 tab（全部 + 七态），已取消的订单也在列表里', async ({ page }) => {
    const pending = await newOrder([{ goods: await newGoods(3), quantity: 1 }]);
    const canceled = await newOrder([{ goods: await newGoods(3), quantity: 1 }]);
    await advanceOrder(ctx, sellerToken, canceled.orderId, 'cancel');

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/orders`);

    // 全部 + 待支付 / 已支付 / 已确认 / 备货完成 / 已发货 / 交易完成 / 已取消
    await expect(page.getByRole('tab')).toHaveCount(8);
    const labels = (await page.getByRole('tab').allTextContents()).map((text) => text.trim());
    expect(labels).toEqual([
      '全部',
      '待支付',
      '已支付',
      '已确认',
      '备货完成',
      '已发货',
      '交易完成',
      '已取消',
    ]);
    await expect(sellerOrderRow(page, pending.orderNo)).toBeVisible();
    await expect(sellerOrderRow(page, canceled.orderNo)).toBeVisible();

    await page.getByRole('tab', { name: '已取消' }).click();
    await expect(sellerOrderRow(page, canceled.orderNo)).toBeVisible();
    await expect(sellerOrderRow(page, pending.orderNo)).toHaveCount(0);

    await page.getByRole('tab', { name: '待支付' }).click();
    await expect(sellerOrderRow(page, pending.orderNo)).toBeVisible();
    await expect(sellerOrderRow(page, canceled.orderNo)).toHaveCount(0);
  });

  test('T04-02 状态筛选写进 URL，刷新后仍是同一个视图', async ({ page }) => {
    const order = await newOrder([{ goods: await newGoods(2), quantity: 1 }]);

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/orders`);
    await page.getByRole('tab', { name: '待支付' }).click();
    await expect(page).toHaveURL(/status=PENDING_PAYMENT/);

    await page.reload();
    await expect(page.getByRole('tab', { name: '待支付' })).toHaveAttribute('aria-selected', 'true');
    await expect(sellerOrderRow(page, order.orderNo)).toBeVisible();
  });

  test('T04-03 点订单行进详情，地址按订单主键进', async ({ page }) => {
    const order = await newOrder([{ goods: await newGoods(2), quantity: 1 }]);

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/orders`);
    await openSellerOrder(page, order.orderNo);

    await expect(page).toHaveURL(new RegExp(`/orders/${order.orderId}$`));
  });

  test('T04-04 详情：订单头与明细各自加载，合计等于明细之和', async ({ page }) => {
    const goodsA = await newGoods(3, '12.50');
    const goodsB = await newGoods(3, '20.00');
    const order = await newOrder([
      { goods: goodsA, quantity: 2 },
      { goods: goodsB, quantity: 1 },
    ]);

    // 明细接口先核对一遍金额口径（45.00）
    const items = await sellerOrderItems(ctx, sellerToken, order.orderId);
    expect(items.items).toHaveLength(2);
    expect(items.items.reduce((sum, item) => sum + Number(item.amount), 0)).toBe(45);

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/orders/${order.orderId}`);

    await expect(page.getByText(order.orderNo)).toBeVisible();
    // exact：否则会同时命中「买家」字段名与下面的状态说明「买家已下单…」
    await expect(page.getByText('买家', { exact: true })).toBeVisible();
    await expect(page.locator('table tbody tr')).toHaveCount(2);
    // 合计优先用订单头的 total_amount
    await expect(page.locator('table tfoot')).toContainText('¥45.00');
  });

  test('T04-05 待支付：卖家只能取消，没有确认入口', async ({ page }) => {
    const order = await newOrder([{ goods: await newGoods(2), quantity: 1 }]);

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/orders/${order.orderId}`);

    await expect(statusTags(page).filter({ hasText: '待支付' })).toBeVisible();
    await expect(page.getByTestId('order-next'), '待支付不给推进按钮').toHaveCount(0);
    await expect(page.getByTestId('order-cancel')).toBeVisible();
    await expect(page.getByText('买家还没付款，这一步要等付款之后才能操作')).toBeVisible();

    // 流程条 6 格（待支付 → 交易完成），当前格是待支付
    const progress = page.getByTestId('order-progress');
    await expect(progress.getByRole('listitem')).toHaveCount(6);
    await expect(progress.locator('[aria-current="step"]')).toHaveText('待支付');
  });

  test('T04-06 推进链：确认 → 备货完成 → 发货 → 买家收货完成', async ({ page }) => {
    const order = await newOrder([{ goods: await newGoods(3), quantity: 1 }]);
    // 卖家第一步是「确认已支付单」，所以先让买家付款
    await payOrder(ctx, order.token, order.orderId, 'ALIPAY');

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/orders/${order.orderId}`);

    await sellerAdvance(page, '确认');
    await expect(statusTags(page).filter({ hasText: '已确认' })).toBeVisible();
    await expect(page.getByTestId('order-next')).toHaveText('标记备货完成');

    await sellerAdvance(page, '确认');
    await expect(statusTags(page).filter({ hasText: '备货完成' })).toBeVisible();
    await expect(page.getByTestId('order-next')).toHaveText('开始发货');

    // 发货走物流弹窗：承运商与单号都必填
    await sellerShip(page, { carrierLabel: '顺丰', trackingNo: 'SF0001' });
    await expect(statusTags(page).filter({ hasText: '已发货' })).toBeVisible();
    await expect(page.getByTestId('order-next'), '发货后卖家没有「完成」按钮').toHaveCount(0);

    // 最后一步只能由买家确认收货推进
    await confirmReceipt(ctx, order.token, order.orderId);
    await page.reload();
    await expect(statusTags(page).filter({ hasText: '交易完成' })).toBeVisible();
    await expect(page.getByTestId('order-next')).toHaveCount(0);
    await expect(page.getByTestId('order-cancel')).toHaveCount(0);

    const after = await sellerOrderDetail(ctx, sellerToken, order.orderId);
    expect(after.status).toBe('COMPLETED');
  });

  test('T04-07 成交时按明细扣库存，扣到 0 自动下架（已售）', async ({ page }) => {
    const goods = await newGoods(5);
    const order = await newOrder([{ goods, quantity: 2 }]);
    await drive(order, 'SHIPPED');

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/orders/${order.orderId}`);
    await expect(statusTags(page).filter({ hasText: '已发货' })).toBeVisible();
    // 扣减时机在「买家确认收货」，卖家侧只观察结果
    await confirmReceipt(ctx, order.token, order.orderId);
    await page.reload();
    await expect(statusTags(page).filter({ hasText: '交易完成' })).toBeVisible();

    const after = await getSellerGoods(ctx, sellerToken, goods.id);
    expect(after.stock, '按明细数量扣 2').toBe(3);
    expect(after.status).toBe('ON_SALE');

    // 库存正好等于下单量：成交后归 0，商品自动下架并记「已售」
    const lastOne = await newGoods(2);
    const lastOrder = await newOrder([{ goods: lastOne, quantity: 2 }]);
    await drive(lastOrder, 'SHIPPED');
    await confirmReceipt(ctx, lastOrder.token, lastOrder.orderId);

    const soldOut = await getSellerGoods(ctx, sellerToken, lastOne.id);
    expect(soldOut.stock).toBe(0);
    expect(soldOut.status).toBe('OFF_SHELF');
    expect(soldOut.off_shelf_reason).toBe('SOLD_OUT');

    // 例外：商品在成交前就下架，成交不扣库存（下架商品不再参与库存扣减）
    const manual = await newGoods(5);
    const manualOrder = await newOrder([{ goods: manual, quantity: 1 }]);
    await drive(manualOrder, 'SHIPPED');
    await offShelf(ctx, sellerToken, manual.id);
    await confirmReceipt(ctx, manualOrder.token, manualOrder.orderId);

    const untouched = await getSellerGoods(ctx, sellerToken, manual.id);
    expect(untouched.stock, '下架商品成交不扣库存').toBe(5);
    expect(untouched.off_shelf_reason).toBe('MANUAL');
  });

  test('T04-08 成交缺货：整单拒绝（60004），订单停在已发货', async ({ page }) => {
    // 库存 3、买家下单 2：把库存改到 1（>0 不触发下架），才测得到缺货拒绝
    const goods = await newGoods(3);
    const order = await newOrder([{ goods, quantity: 2 }]);
    await drive(order, 'SHIPPED');
    await updateStock(ctx, sellerToken, goods.id, 1);

    const rejected = await call(
      ctx,
      'POST',
      `/api/buyer/orders/${order.orderId}/confirm-receipt`,
      {},
      { token: order.token },
    );
    expect(rejected.body.code, '库存不足整单拒绝').toBe(60004);

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/orders/${order.orderId}`);
    await expect(statusTags(page).filter({ hasText: '已发货' }), '被拒后停在已发货').toBeVisible();
    const afterFirst = await getSellerGoods(ctx, sellerToken, goods.id);
    expect(afterFirst.stock, '被拒不扣库存').toBe(1);

    // 补货到 2 后买家重试 → 成交，库存 1→0 并自动下架
    await updateStock(ctx, sellerToken, goods.id, 2);
    await confirmReceipt(ctx, order.token, order.orderId);
    await page.reload();
    await expect(statusTags(page).filter({ hasText: '交易完成' })).toBeVisible();

    const afterSecond = await getSellerGoods(ctx, sellerToken, goods.id);
    expect(afterSecond.stock).toBe(0);
    expect(afterSecond.off_shelf_reason).toBe('SOLD_OUT');
  });

  test('T04-09 卖家取消：待支付与已发货都能取消，退款口径不同', async ({ page }) => {
    const pending = await newOrder([{ goods: await newGoods(2), quantity: 1 }]);
    const shipped = await newOrder([{ goods: await newGoods(2), quantity: 1 }]);
    await drive(shipped, 'SHIPPED');

    await sellerLoginUi(page);

    // 待支付：没付钱，取消不提退款
    await page.goto(`${SELLER_URL}/orders/${pending.orderId}`);
    await page.getByTestId('order-cancel').click();
    const unpaidDialog = page.getByRole('dialog');
    await expect(unpaidDialog).toContainText('这笔订单还没付款，取消不产生退款，也不影响库存');
    await unpaidDialog.getByRole('button', { name: '取消订单', exact: true }).click();
    await expect(statusTags(page).filter({ hasText: '已取消' })).toBeVisible();

    const pendingAfter = await sellerOrderDetail(ctx, sellerToken, pending.orderId);
    expect(pendingAfter.refunded_amount).toBe(0);

    // 已支付并已发货（买家侧已不能取消，卖家仍可）：按实付金额全额退款
    await page.goto(`${SELLER_URL}/orders/${shipped.orderId}`);
    await expect(page.getByTestId('order-cancel')).toBeVisible();
    await page.getByTestId('order-cancel').click();
    const paidDialog = page.getByRole('dialog');
    await expect(paidDialog).toContainText('按实付金额全额原路退款');
    await paidDialog.getByRole('button', { name: '取消订单', exact: true }).click();
    await expect(statusTags(page).filter({ hasText: '已取消' })).toBeVisible();
    await expect(page.getByTestId('order-next')).toHaveCount(0);

    const shippedAfter = await sellerOrderDetail(ctx, sellerToken, shipped.orderId);
    expect(shippedAfter.refunded_amount, '全额退款').toBe(Number(shippedAfter.total_amount));
  });

  test('T04-10 交易完成后：无推进与取消，仍可改物流，接口取消 60003', async ({ page }) => {
    const order = await newOrder([{ goods: await newGoods(2), quantity: 1 }]);
    await drive(order, 'COMPLETED');

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/orders/${order.orderId}`);
    await expect(statusTags(page).filter({ hasText: '交易完成' })).toBeVisible();
    await expect(page.getByTestId('order-next')).toHaveCount(0);
    await expect(page.getByTestId('order-cancel')).toHaveCount(0);
    // 完成后仍可改物流（改单号不改状态）
    await expect(page.getByTestId('order-logistics-edit')).toBeVisible();

    const result = await call(ctx, 'POST', `/api/seller/orders/${order.orderId}/cancel`, {}, {
      token: sellerToken,
    });
    expect(result.body.code, '完成后不能再取消').toBe(60003);
  });

  test('T04-11 跳级推进 → 60002，状态不变', async () => {
    const order = await newOrder([{ goods: await newGoods(2), quantity: 1 }]);

    for (const action of ['confirm', 'prepare'] as const) {
      const result = await call(ctx, 'POST', `/api/seller/orders/${order.orderId}/${action}`, {}, {
        token: sellerToken,
      });
      expect(result.body.code, `待支付不能直接 ${action}`).toBe(60002);
    }

    const ship = await call(
      ctx,
      'POST',
      `/api/seller/orders/${order.orderId}/ship`,
      { carrier: '顺丰速运', tracking_no: 'SF0002' },
      { token: sellerToken },
    );
    expect(ship.body.code, '待支付不能直接发货').toBe(60002);

    const after = await sellerOrderDetail(ctx, sellerToken, order.orderId);
    expect(after.status).toBe('PENDING_PAYMENT');
  });

  test('T04-12 重复取消按成功处理（防连点），状态停在已取消', async () => {
    const order = await newOrder([{ goods: await newGoods(2), quantity: 1 }]);

    const first = await advanceOrder(ctx, sellerToken, order.orderId, 'cancel');
    expect(first.status).toBe('CANCELED');

    // 幂等：第二次不报错（只是防抖设计，不是可依赖的业务契约）
    const second = await advanceOrder(ctx, sellerToken, order.orderId, 'cancel');
    expect(second.status).toBe('CANCELED');

    const after = await sellerOrderDetail(ctx, sellerToken, order.orderId);
    expect(after.status).toBe('CANCELED');
  });

  test('T04-13 空态：当前筛选没有订单 → 说明 + 查看全部订单', async ({ page }) => {
    await sellerLoginUi(page);
    // 用不存在的买家 id 深链，稳定制造「筛选后无结果」，不依赖库里已有数据
    await page.goto(`${SELLER_URL}/orders?status=COMPLETED&buyer_id=NOT-EXIST`);
    await expect(page.getByText('当前筛选下没有订单')).toBeVisible();

    await page.getByRole('button', { name: '查看全部订单' }).click();
    await expect(page).not.toHaveURL(/status=/);
    await expect(page.getByText('当前筛选下没有订单')).toHaveCount(0);
    await expect(page.getByText('暂无订单')).toBeVisible();
  });

  test('T04-14 商品详情内嵌「该商品的订单」，点行进同一个详情页', async ({ page }) => {
    const goods = await newGoods(2);
    const order = await newOrder([{ goods, quantity: 1 }]);

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/`);
    await page.getByLabel('搜索商品名称').fill(goods.name);
    await page.getByRole('button', { name: '搜索' }).click();
    await openSellerGoods(page, goods.name);

    await expect(page.getByRole('heading', { name: '该商品的订单' })).toBeVisible();
    const row = page.getByRole('row').filter({ hasText: order.orderNo });
    await expect(row).toBeVisible();
    await row.click();

    await expect(page.getByRole('heading', { name: '订单详情' })).toBeVisible();
    await expect(page).toHaveURL(new RegExp(`/orders/${order.orderId}$`));
  });
});
