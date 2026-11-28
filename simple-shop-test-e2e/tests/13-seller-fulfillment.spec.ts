import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

import {
  advanceOrder,
  applyAftersale,
  completedOrderViaApi,
  completeSellerAftersale,
  createL1,
  createL2,
  newApi,
  newBuyer,
  payOrder,
  placeOrder,
  publishGoods,
  sellerLogin,
  sellerOrderDetail,
  sellerOrderItems,
  shippedOrderViaApi,
} from '../helpers/api';
import type { GoodsView, OrderRow } from '../helpers/api';
import { SELLER_URL } from '../helpers/config';
import { aftersaleFixture, buyerFixture, goodsFixture, uniqueName } from '../helpers/data';
import { sellerLoginUi, toast } from '../helpers/ui';

/**
 * 13 卖家发货与物流（阶段四新增）
 *
 * 覆盖：发货必填校验、预设承运商 + 多包裹单号、承运商「其他」、
 * 改物流不改状态、交易完成仍可改物流、未发货无物流、
 * 「已退款」的两种来源不串味、订单详情的售后区块。
 */
test.describe.serial('13 卖家发货与物流', () => {
  let ctx: APIRequestContext;
  let sellerToken: string;
  let goodsA: GoodsView;
  let goodsB: GoodsView;
  let goodsC: GoodsView;

  test.beforeAll(async () => {
    ctx = await newApi();
    sellerToken = await sellerLogin(ctx);
    const tag = uniqueName('sf');
    const l1 = await createL1(ctx, sellerToken, `履约一级-${tag}`);
    const l2 = await createL2(ctx, sellerToken, l1.id, `履约二级-${tag}`);
    goodsA = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('履约商品A'), l2.id, { stock: 500, price: '18.00' }),
    );
    goodsB = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('履约商品B'), l2.id, { stock: 500, price: '25.00' }),
    );
    goodsC = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('履约商品C'), l2.id, { stock: 500, price: '10.00' }),
    );
  });

  test.afterAll(async () => {
    await ctx.dispose();
  });

  /** 造一张指定状态的订单（只支持本文件需要的几档）。 */
  async function orderAt(
    buyerToken: string,
    stage: 'PAID' | 'CONFIRMED' | 'PREPARED',
    items: Array<{ goods_id: string; quantity: number }>,
  ): Promise<OrderRow> {
    const order = await placeOrder(ctx, buyerToken, items);
    await payOrder(ctx, buyerToken, order.order_id, 'WECHAT');
    if (stage === 'PAID') return sellerOrderDetail(ctx, sellerToken, order.order_id);
    await advanceOrder(ctx, sellerToken, order.order_id, 'confirm');
    if (stage === 'CONFIRMED') return sellerOrderDetail(ctx, sellerToken, order.order_id);
    await advanceOrder(ctx, sellerToken, order.order_id, 'prepare');
    return sellerOrderDetail(ctx, sellerToken, order.order_id);
  }

  /** 详情里的「物流信息」整块（标题的父级只是标题行，不是整块）。 */
  function logisticsSection(page: Page) {
    return page
      .locator('section')
      .filter({ has: page.getByRole('heading', { name: '物流信息' }) });
  }

  /**
   * 每个用例只登录一次。
   * 同一个用例里要连开几笔订单，第二次再走登录会撞上「已登录访问 /login 被路由送走」，
   * 登录表单根本不在，fill 必然超时；所以清一次本地存储、登录一次，之后直接深链。
   */
  const loggedPages = new WeakSet<Page>();

  async function loginFresh(page: Page) {
    if (loggedPages.has(page)) return;
    await page.goto(`${SELLER_URL}/login`);
    await page.evaluate(() => window.localStorage.clear());
    await page.reload();
    await sellerLoginUi(page);
    loggedPages.add(page);
  }

  async function openOrder(page: Page, orderId: string) {
    await loginFresh(page);
    await page.goto(`${SELLER_URL}/orders/${orderId}`);
    await expect(page.getByRole('heading', { name: '订单详情' })).toBeVisible();
  }

  test('SF-01 发货必填校验：承运商与单号都空', async ({ page }) => {
    const buyer = buyerFixture('sf01');
    const token = await newBuyer(ctx, buyer);
    const order = await orderAt(token, 'PREPARED', [{ goods_id: goodsA.id, quantity: 1 }]);

    await openOrder(page, order.order_id);
    await page.getByTestId('order-next').click();
    const dialog = page.getByTestId('logistics-dialog');
    await expect(dialog).toBeVisible();

    await page.getByTestId('logistics-submit').click();
    // 下拉的占位项也写着「请选择承运商」，所以只认红字那一层
    await expect(dialog.locator('span.text-error').filter({ hasText: '请选择承运商' })).toBeVisible();
    await expect(dialog.locator('span.text-error').filter({ hasText: '请填写运单号' })).toBeVisible();
    await expect(dialog, '校验不过不关弹窗').toBeVisible();

    await dialog.getByRole('button', { name: '取消' }).click();
    await expect(dialog).toBeHidden();
    await page.reload();
    await expect(page.getByTestId('status-tag')).toHaveText('备货完成');

    const detail = await sellerOrderDetail(ctx, sellerToken, order.order_id);
    expect(detail.status).toBe('PREPARED');
    expect(detail.carrier).toBeNull();
  });

  test('SF-02 发货成功：预设承运商 + 两行单号（详情与列表）', async ({ page }) => {
    const buyer = buyerFixture('sf02');
    const token = await newBuyer(ctx, buyer);
    const order = await orderAt(token, 'PREPARED', [{ goods_id: goodsA.id, quantity: 1 }]);

    await openOrder(page, order.order_id);
    await page.getByTestId('order-next').click();
    await page.getByTestId('logistics-carrier').selectOption({ label: '顺丰' });
    await page.getByTestId('logistics-tracking-no').fill('SF123\nSF456');
    await page.getByTestId('logistics-submit').click();
    await expect(page.getByTestId('logistics-dialog')).toBeHidden();

    await expect(page.getByTestId('status-tag')).toHaveText('已发货');
    const logistics = logisticsSection(page);
    await expect(logistics).toContainText('顺丰速运');
    const tracking = logistics.locator('dd.whitespace-pre-line').first();
    await expect(tracking).toContainText('SF123');
    await expect(tracking).toContainText('SF456');
    await expect(page.getByTestId('order-cancel')).toBeVisible();
    await expect(page.getByTestId('order-next'), '已发货没有下一步').toHaveCount(0);

    const detail = await sellerOrderDetail(ctx, sellerToken, order.order_id);
    expect(detail.carrier).toBe('顺丰速运');
    expect(detail.tracking_no).toBe('SF123\nSF456');

    await page.goto(`${SELLER_URL}/orders`);
    const row = page.getByRole('row').filter({ hasText: order.order_no });
    const cell = row.locator('td').filter({ hasText: '顺丰速运' });
    await expect(cell).toContainText('SF123');
    await expect(cell).not.toContainText('SF456');
    await expect(cell.locator('[title]')).toHaveAttribute('title', 'SF123\nSF456');
  });

  test('SF-03 承运商选「其他」：空着报错、填了按原文存', async ({ page }) => {
    const buyer = buyerFixture('sf03');
    const token = await newBuyer(ctx, buyer);
    const order = await orderAt(token, 'PREPARED', [{ goods_id: goodsB.id, quantity: 1 }]);

    await openOrder(page, order.order_id);
    await page.getByTestId('order-next').click();
    await page.getByTestId('logistics-carrier').selectOption({ label: '其他（手动填写）' });
    await page.getByTestId('logistics-tracking-no').fill('DB001');
    await page.getByTestId('logistics-submit').click();
    await expect(page.getByText('请填写承运商名称')).toBeVisible();

    await page.getByTestId('logistics-carrier-other').fill('德邦物流');
    await page.getByTestId('logistics-submit').click();
    await expect(page.getByTestId('logistics-dialog')).toBeHidden();

    await expect(logisticsSection(page)).toContainText('德邦物流');
    const detail = await sellerOrderDetail(ctx, sellerToken, order.order_id);
    expect(detail.carrier).toBe('德邦物流');
    expect(detail.status).toBe('SHIPPED');
  });

  test('SF-04 改物流：回填原值、状态不变、提示已更新', async ({ page }) => {
    const buyer = buyerFixture('sf04');
    const token = await newBuyer(ctx, buyer);
    const order = await shippedOrderViaApi(ctx, sellerToken, token, [
      { goods_id: goodsA.id, quantity: 1 },
    ], { carrier: '顺丰速运', tracking_no: 'OLD001', logistics_remark: '原备注' });

    await openOrder(page, order.order_id);
    await page.getByTestId('order-logistics-edit').click();
    const dialog = page.getByTestId('logistics-dialog');
    await expect(dialog).toBeVisible();
    await expect(page.getByTestId('logistics-carrier')).toHaveValue('顺丰速运');
    await expect(page.getByTestId('logistics-tracking-no')).toHaveValue('OLD001');
    await expect(page.getByTestId('logistics-remark')).toHaveValue('原备注');

    await page.getByTestId('logistics-tracking-no').fill('NEW002');
    await page.getByTestId('logistics-submit').click();
    await expect(dialog).toBeHidden();
    await expect(toast(page)).toContainText('物流信息已更新');

    await expect(page.getByTestId('status-tag')).toHaveText('已发货');
    const detail = await sellerOrderDetail(ctx, sellerToken, order.order_id);
    expect(detail.status).toBe('SHIPPED');
    expect(detail.tracking_no).toBe('NEW002');
    expect(detail.carrier).toBe('顺丰速运');
  });

  test('SF-05 交易完成仍可改物流，但没有流程图与取消', async ({ page }) => {
    const buyer = buyerFixture('sf05');
    const token = await newBuyer(ctx, buyer);
    const order = await completedOrderViaApi(ctx, sellerToken, token, [
      { goods_id: goodsA.id, quantity: 1 },
    ]);

    await openOrder(page, order.order_id);
    await expect(page.getByTestId('status-tag')).toHaveText('交易完成');
    await expect(page.getByTestId('order-progress')).toHaveCount(0);
    await expect(page.getByTestId('order-cancel')).toHaveCount(0);
    await expect(page.getByTestId('order-next')).toHaveCount(0);
    await expect(page.getByTestId('order-logistics-edit')).toBeVisible();
  });

  test('SF-06 未发货没有物流：详情提示 + 列表「未发货」', async ({ page }) => {
    const buyer = buyerFixture('sf06');
    const token = await newBuyer(ctx, buyer);
    const paid = await orderAt(token, 'PAID', [{ goods_id: goodsA.id, quantity: 1 }]);
    const confirmed = await orderAt(token, 'CONFIRMED', [{ goods_id: goodsB.id, quantity: 1 }]);

    await openOrder(page, paid.order_id);
    await expect(page.getByText('尚未发货，没有物流信息')).toBeVisible();
    await expect(page.getByTestId('order-logistics-edit')).toHaveCount(0);

    await openOrder(page, confirmed.order_id);
    await expect(page.getByText('尚未发货，没有物流信息')).toBeVisible();

    await page.goto(`${SELLER_URL}/orders`);
    for (const order of [paid, confirmed]) {
      const row = page.getByRole('row').filter({ hasText: order.order_no });
      await expect(row).toContainText('未发货');
    }
  });

  test('SF-07 「已退款」两种来源不串味', async ({ page }) => {
    const buyer = buyerFixture('sf07');
    const token = await newBuyer(ctx, buyer);

    // ① 已支付后取消：退款 = 订单总额，售后数为 0
    const canceled = await placeOrder(ctx, token, [{ goods_id: goodsA.id, quantity: 2 }]);
    await payOrder(ctx, token, canceled.order_id, 'ALIPAY');
    await advanceOrder(ctx, sellerToken, canceled.order_id, 'cancel');

    // ② 交易完成、3 个明细、其中两项各退一次
    const order2 = await completedOrderViaApi(ctx, sellerToken, token, [
      { goods_id: goodsA.id, quantity: 1 },
      { goods_id: goodsB.id, quantity: 2 },
      { goods_id: goodsC.id, quantity: 1 },
    ]);
    const items = (await sellerOrderItems(ctx, sellerToken, order2.order_id)).items;
    for (const item of items.slice(0, 2)) {
      const applied = await applyAftersale(ctx, token, {
        order_item_id: item.order_item_id,
        ...aftersaleFixture(13),
      });
      await completeSellerAftersale(ctx, sellerToken, applied.aftersale_id, 'REFUND_ONLY');
    }

    // ③ 交易完成、无退款
    const order3 = await completedOrderViaApi(ctx, sellerToken, token, [
      { goods_id: goodsC.id, quantity: 1 },
    ]);

    const canceledView = await sellerOrderDetail(ctx, sellerToken, canceled.order_id);
    expect(canceledView.refunded_amount).toBe(36);
    expect(canceledView.aftersale_count).toBe(0);

    const refunded = await sellerOrderDetail(ctx, sellerToken, order2.order_id);
    expect(refunded.total_amount).toBe(78);
    expect(refunded.refunded_amount).toBe(68);
    expect(refunded.refunded_amount).toBeLessThan(refunded.total_amount);
    expect(refunded.aftersale_count).toBe(2);

    const clean = await sellerOrderDetail(ctx, sellerToken, order3.order_id);
    expect(clean.refunded_amount).toBe(0);

    await openOrder(page, canceled.order_id);
    await expect(page.getByText('已退款')).toBeVisible();
    await expect(page.getByText('¥36.00').first()).toBeVisible();

    await openOrder(page, order2.order_id);
    await expect(page.getByText('已退款')).toBeVisible();
    await expect(page.getByText('¥68.00').first()).toBeVisible();

    await openOrder(page, order3.order_id);
    await expect(page.getByText('已退款')).toHaveCount(0);
  });

  test('SF-08 订单详情的售后区块：列表、进详情、带订单号跳售后管理', async ({ page }) => {
    const buyer = buyerFixture('sf08');
    const token = await newBuyer(ctx, buyer);
    const order = await completedOrderViaApi(ctx, sellerToken, token, [
      { goods_id: goodsA.id, quantity: 1 },
    ]);
    const item = (await sellerOrderItems(ctx, sellerToken, order.order_id)).items[0]!;
    const applied = await applyAftersale(ctx, token, {
      order_item_id: item.order_item_id,
      ...aftersaleFixture(14),
    });
    await completeSellerAftersale(ctx, sellerToken, applied.aftersale_id, 'REFUND_ONLY');

    await openOrder(page, order.order_id);
    const block = page.getByRole('heading', { name: '该订单的售后' }).locator('../..');
    await expect(block).toContainText(applied.aftersale_no);
    await expect(block).toContainText('已完成');
    await expect(block).toContainText('仅退款');
    await expect(block).toContainText('退款 ¥18.00');

    const link = page.getByRole('link', { name: '在售后管理中查看' });
    await expect(link).toHaveAttribute('href', `/aftersales?order_no=${order.order_no}`);

    await page.getByRole('link', { name: applied.aftersale_no }).click();
    await expect(page.getByRole('heading', { name: '售后详情' })).toBeVisible();
    await expect(page).toHaveURL(`${SELLER_URL}/aftersales/${applied.aftersale_id}`);
  });
});
