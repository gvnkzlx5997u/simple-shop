import { expect, test, type APIRequestContext } from '@playwright/test';

import {
  advanceOrder,
  call,
  createL1,
  createL2,
  getSellerGoods,
  newApi,
  newBuyer,
  placeOrder,
  publishGoods,
  sellerLogin,
  sellerOrderDetail,
  sellerOrderItems,
} from '../helpers/api';
import type { GoodsView, OrderStatus } from '../helpers/api';
import { buyerFixture, goodsFixture, uniqueName } from '../helpers/data';
import { SELLER_URL } from '../helpers/config';
import {
  confirmDialog,
  openSellerGoods,
  openSellerOrder,
  sellerAdvance,
  sellerLoginUi,
  sellerOrderRow,
  statusTags,
  toast,
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

  /** 造一笔订单，返回订单号与 id。 */
  async function newOrder(
    items: Array<{ goods: GoodsView; quantity: number }>,
  ): Promise<{ orderId: string; orderNo: string; buyerId: string }> {
    const token = await newBuyer(ctx, buyerFixture('ord'));
    const placed = await placeOrder(
      ctx,
      token,
      items.map((item) => ({ goods_id: item.goods.id, quantity: item.quantity })),
    );
    const detail = await sellerOrderDetail(ctx, sellerToken, placed.order_id);
    return { orderId: placed.order_id, orderNo: placed.order_no, buyerId: detail.buyer.id };
  }

  /** 用接口把订单推到指定状态（造前置状态用，断言仍走 UI）。 */
  async function drive(orderId: string, target: OrderStatus): Promise<void> {
    const path: Array<['confirm' | 'prepare' | 'ship' | 'complete', OrderStatus]> = [
      ['confirm', 'CONFIRMED'],
      ['prepare', 'PREPARED'],
      ['ship', 'SHIPPED'],
      ['complete', 'COMPLETED'],
    ];
    for (const [action, status] of path) {
      await advanceOrder(ctx, sellerToken, orderId, action);
      if (status === target) return;
    }
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

  test('T04-01 列表：七个状态 tab，已取消的订单也在列表里', async ({ page }) => {
    const pending = await newOrder([{ goods: await newGoods(3), quantity: 1 }]);
    const canceled = await newOrder([{ goods: await newGoods(3), quantity: 1 }]);
    await advanceOrder(ctx, sellerToken, canceled.orderId, 'cancel');

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/orders`);

    // 全部 + 六个状态
    await expect(page.getByRole('tab')).toHaveCount(7);
    await expect(sellerOrderRow(page, pending.orderNo)).toBeVisible();
    await expect(sellerOrderRow(page, canceled.orderNo)).toBeVisible();

    await page.getByRole('tab', { name: '已取消' }).click();
    await expect(sellerOrderRow(page, canceled.orderNo)).toBeVisible();
    await expect(sellerOrderRow(page, pending.orderNo)).toHaveCount(0);

    await page.getByRole('tab', { name: '待确认' }).click();
    await expect(sellerOrderRow(page, pending.orderNo)).toBeVisible();
    await expect(sellerOrderRow(page, canceled.orderNo)).toHaveCount(0);
  });

  test('T04-02 状态筛选写进 URL，刷新后仍是同一个视图', async ({ page }) => {
    const order = await newOrder([{ goods: await newGoods(2), quantity: 1 }]);

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/orders`);
    await page.getByRole('tab', { name: '待确认' }).click();
    await expect(page).toHaveURL(new RegExp(`status=PENDING_CONFIRM`));

    await page.reload();
    await expect(page.getByRole('tab', { name: '待确认' })).toHaveAttribute('aria-selected', 'true');
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

  test('T04-05 待确认：只有一个推进按钮，没有跳级入口', async ({ page }) => {
    const order = await newOrder([{ goods: await newGoods(2), quantity: 1 }]);

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/orders/${order.orderId}`);

    await expect(statusTags(page).filter({ hasText: '待确认' })).toBeVisible();
    await expect(page.getByTestId('order-next')).toHaveText('确认订单');
    await expect(page.getByTestId('order-next')).toHaveCount(1);
    await expect(page.getByTestId('order-cancel')).toBeVisible();
    // 流程图上只有「待确认」是当前步
    await expect(page.getByText('买家已下单，等待你确认')).toBeVisible();
  });

  test('T04-06 四步推进：确认 → 备货完成 → 已发货 → 交易完成', async ({ page }) => {
    const order = await newOrder([{ goods: await newGoods(3), quantity: 1 }]);

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/orders/${order.orderId}`);

    await sellerAdvance(page, '确认订单');
    await expect(statusTags(page).filter({ hasText: '已确认' })).toBeVisible();
    await expect(page.getByTestId('order-next')).toHaveText('标记备货完成');

    await sellerAdvance(page, '标记备货完成');
    await expect(statusTags(page).filter({ hasText: '备货完成' })).toBeVisible();
    await expect(page.getByTestId('order-next')).toHaveText('开始发货');

    await sellerAdvance(page, '开始发货');
    await expect(statusTags(page).filter({ hasText: '已发货' })).toBeVisible();
    await expect(page.getByTestId('order-next')).toHaveText('交易完成');

    await sellerAdvance(page, '交易完成');
    await expect(statusTags(page).filter({ hasText: '交易完成' })).toBeVisible();
    // 终态：动作区整行不渲染
    await expect(page.getByTestId('order-next')).toHaveCount(0);
    await expect(page.getByTestId('order-cancel')).toHaveCount(0);

    const after = await sellerOrderDetail(ctx, sellerToken, order.orderId);
    expect(after.status).toBe('COMPLETED');
  });

  test('T04-07 完成时按明细扣库存，扣到 0 商品自动下架（已售）', async ({ page }) => {
    const goods = await newGoods(5);
    const order = await newOrder([{ goods, quantity: 2 }]);
    await drive(order.orderId, 'SHIPPED');

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/orders/${order.orderId}`);
    await sellerAdvance(page, '交易完成');
    await expect(statusTags(page).filter({ hasText: '交易完成' })).toBeVisible();

    const after = await getSellerGoods(ctx, sellerToken, goods.id);
    expect(after.stock, '按明细数量扣 2').toBe(3);
    expect(after.status).toBe('ON_SALE');

    // 另一件库存正好等于下单量：完成后库存归 0，商品自动下架并记「已售」
    const lastOne = await newGoods(2);
    const lastOrder = await newOrder([{ goods: lastOne, quantity: 2 }]);
    await drive(lastOrder.orderId, 'SHIPPED');
    await advanceOrder(ctx, sellerToken, lastOrder.orderId, 'complete');

    const soldOut = await getSellerGoods(ctx, sellerToken, lastOne.id);
    expect(soldOut.stock).toBe(0);
    expect(soldOut.status).toBe('OFF_SHELF');
    expect(soldOut.off_shelf_reason).toBe('SOLD_OUT');
  });

  test('T04-08 完成缺货：整单拒绝（60004），订单停在已发货', async ({ page }) => {
    // 库存 1，两位买家各下一单——下单只校验不锁库存，两单都能成功
    const goods = await newGoods(1);
    const first = await newOrder([{ goods, quantity: 1 }]);
    const second = await newOrder([{ goods, quantity: 1 }]);
    await drive(first.orderId, 'SHIPPED');
    await drive(second.orderId, 'SHIPPED');

    // 先完成第一单：库存 1→0，商品自动下架
    await advanceOrder(ctx, sellerToken, first.orderId, 'complete');
    const afterFirst = await getSellerGoods(ctx, sellerToken, goods.id);
    expect(afterFirst.stock).toBe(0);

    // 第二单在界面上完成 → 被拒绝，提示整单拒绝且停在已发货
    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/orders/${second.orderId}`);
    await page.getByTestId('order-next').click();
    await confirmDialog(page, '交易完成');

    await expect(toast(page).filter({ hasText: '库存不足' })).toBeVisible();
    await expect(statusTags(page).filter({ hasText: '已发货' })).toBeVisible();
    const afterSecond = await sellerOrderDetail(ctx, sellerToken, second.orderId);
    expect(afterSecond.status).toBe('SHIPPED');
  });

  test('T04-09 卖家取消：待确认与已发货都能取消', async ({ page }) => {
    const pending = await newOrder([{ goods: await newGoods(2), quantity: 1 }]);
    const shipped = await newOrder([{ goods: await newGoods(2), quantity: 1 }]);
    await drive(shipped.orderId, 'SHIPPED');

    await sellerLoginUi(page);

    // 待确认
    await page.goto(`${SELLER_URL}/orders/${pending.orderId}`);
    await page.getByTestId('order-cancel').click();
    await confirmDialog(page, '取消订单');
    await expect(statusTags(page).filter({ hasText: '已取消' })).toBeVisible();

    // 已发货（买家侧已不能取消，卖家仍可）
    await page.goto(`${SELLER_URL}/orders/${shipped.orderId}`);
    await expect(page.getByTestId('order-cancel')).toBeVisible();
    await page.getByTestId('order-cancel').click();
    await confirmDialog(page, '取消订单');
    await expect(statusTags(page).filter({ hasText: '已取消' })).toBeVisible();
    await expect(page.getByTestId('order-next')).toHaveCount(0);
  });

  test('T04-10 交易完成后：界面没有任何动作，接口取消返回 60003', async ({ page }) => {
    const order = await newOrder([{ goods: await newGoods(2), quantity: 1 }]);
    await drive(order.orderId, 'COMPLETED');

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/orders/${order.orderId}`);
    await expect(statusTags(page).filter({ hasText: '交易完成' })).toBeVisible();
    await expect(page.getByTestId('order-next')).toHaveCount(0);
    await expect(page.getByTestId('order-cancel')).toHaveCount(0);

    const result = await call(ctx, 'POST', `/api/seller/orders/${order.orderId}/cancel`, {}, {
      token: sellerToken,
    });
    expect(result.body.code, '完成后不能再取消').toBe(60003);
  });

  test('T04-11 跳级推进 → 60002，状态不变', async () => {
    const order = await newOrder([{ goods: await newGoods(2), quantity: 1 }]);

    const skip = await call(ctx, 'POST', `/api/seller/orders/${order.orderId}/complete`, {}, {
      token: sellerToken,
    });
    expect(skip.body.code, '待确认不能直接完成').toBe(60002);

    const back = await call(ctx, 'POST', `/api/seller/orders/${order.orderId}/ship`, {}, {
      token: sellerToken,
    });
    expect(back.body.code, '待确认不能直接发货').toBe(60002);

    const after = await sellerOrderDetail(ctx, sellerToken, order.orderId);
    expect(after.status).toBe('PENDING_CONFIRM');
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
