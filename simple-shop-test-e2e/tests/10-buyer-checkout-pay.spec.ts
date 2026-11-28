import { expect, test, type APIRequestContext } from '@playwright/test';

import {
  addToCart,
  advanceOrder,
  createAddress,
  createL1,
  createL2,
  getSellerGoods,
  newApi,
  newBuyer,
  payOrder,
  placeOrder,
  publishGoods,
  sellerLogin,
  sellerOrderDetail,
} from '../helpers/api';
import type { GoodsView } from '../helpers/api';
import { BUYER_URL } from '../helpers/config';
import { addressFixture, buyerFixture, goodsFixture, uniqueName } from '../helpers/data';
import { buyerLoginUi, buyerOpenCart, buyerOpenCheckout, buyerPayOnPage } from '../helpers/ui';

/**
 * 10 买家结算与支付（阶段四新增）
 *
 * 覆盖：结算页预选与总额、两种空态、提交下单、倒计时与支付入口、四渠道支付、
 * 未选渠道禁用、已支付不可再付、支付失败重试（独立批次）、已支付取消退款、
 * 待支付取消不退款、参数校验、超时取消（独立批次）。
 */
test.describe.serial('10 买家结算与支付', () => {
  let ctx: APIRequestContext;
  let sellerToken: string;
  let goodsA: GoodsView;
  let goodsB: GoodsView;

  test.beforeAll(async () => {
    ctx = await newApi();
    sellerToken = await sellerLogin(ctx);
    const tag = uniqueName('pay');
    const l1 = await createL1(ctx, sellerToken, `结算一级-${tag}`);
    const l2 = await createL2(ctx, sellerToken, l1.id, `结算二级-${tag}`);
    goodsA = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('结算商品A'), l2.id, { stock: 100, price: '15.00' }),
    );
    goodsB = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('结算商品B'), l2.id, { stock: 100, price: '20.00' }),
    );
  });

  test.afterAll(async () => {
    await ctx.dispose();
  });

  test('CO-01 去结算：默认地址预选、清单与总额一致', async ({ page }) => {
    const buyer = buyerFixture('co01');
    const token = await newBuyer(ctx, buyer);
    await createAddress(ctx, token, addressFixture(1));
    await addToCart(ctx, token, goodsA.id);
    await addToCart(ctx, token, goodsB.id);

    await buyerLoginUi(page, buyer.username, buyer.password);
    await buyerOpenCart(page);
    await page.getByRole('button', { name: '去结算' }).click();

    await expect(page).toHaveURL(/\/checkout$/);
    await expect(page.getByTestId('checkout-address').first()).toBeChecked();
    await expect(page.locator('table tbody tr')).toHaveCount(2);
    await expect(page.getByTestId('checkout-total')).toHaveText('¥35.00');
  });

  test('CO-02 无地址空态', async ({ page }) => {
    const buyer = buyerFixture('co02');
    const token = await newBuyer(ctx, buyer);
    await addToCart(ctx, token, goodsA.id);

    await buyerLoginUi(page, buyer.username, buyer.password);
    await buyerOpenCheckout(page);

    await expect(page.getByText('还没有收货地址')).toBeVisible();
    await expect(page.getByRole('link', { name: '去地址管理' })).toBeVisible();
    await expect(page.getByTestId('checkout-submit')).toHaveCount(0);
  });

  test('CO-03 无勾选行空态', async ({ page }) => {
    const buyer = buyerFixture('co03');
    const token = await newBuyer(ctx, buyer);
    await createAddress(ctx, token, addressFixture(1));
    await addToCart(ctx, token, goodsA.id);

    await buyerLoginUi(page, buyer.username, buyer.password);
    await buyerOpenCart(page);
    await page.getByTestId('cart-check-all').uncheck();
    await page.goto(`${BUYER_URL}/checkout`);

    await expect(page.getByText('没有要结算的商品')).toBeVisible();
    await expect(page.getByRole('link', { name: '去购物车' })).toBeVisible();
    await expect(page.getByTestId('checkout-submit')).toHaveCount(0);
  });

  test('CO-04 提交订单成功 → 待支付、单号格式、购物车清空', async ({ page }) => {
    const buyer = buyerFixture('co04');
    const token = await newBuyer(ctx, buyer);
    await createAddress(ctx, token, addressFixture(1));
    await addToCart(ctx, token, goodsA.id);

    await buyerLoginUi(page, buyer.username, buyer.password);
    await buyerOpenCart(page);
    await page.getByRole('button', { name: '去结算' }).click();
    await page.getByTestId('checkout-submit').click();

    await expect(page).toHaveURL(/\/orders\/[^/]+$/);
    await expect(page.getByTestId('status-tag')).toHaveText('待支付');
    const orderNo = (await page.getByText(/^SO\d{14}$/).first().textContent())?.trim() ?? '';
    expect(orderNo, '订单号应为 SO + 8 位日期 + 6 位序列').toMatch(/^SO\d{14}$/);

    await buyerOpenCart(page);
    await expect(page.getByTestId('cart-row')).toHaveCount(0);
  });

  test('CO-05 待支付详情：倒计时递减、支付入口可见', async ({ page }) => {
    const buyer = buyerFixture('co05');
    const token = await newBuyer(ctx, buyer);
    const order = await placeOrder(ctx, token, [{ goods_id: goodsA.id, quantity: 1 }]);

    await buyerLoginUi(page, buyer.username, buyer.password);
    await page.goto(`${BUYER_URL}/orders/${order.order_id}`);

    await expect(page.getByTestId('order-pay-entry')).toBeVisible();
    const countdown = page.getByTestId('pay-countdown');
    await expect(countdown).toBeVisible();
    const first = await countdown.textContent();
    await page.waitForTimeout(1600);
    const second = await countdown.textContent();
    expect(second).not.toBe(first);

    await page.getByTestId('order-pay-entry').click();
    await expect(page.getByTestId('pay-amount')).toContainText('¥15.00');
  });

  test('CO-06 四渠道支付（数据驱动）', async ({ page }) => {
    const buyer = buyerFixture('co06');
    const token = await newBuyer(ctx, buyer);
    const channels: Array<{ label: string; value: string }> = [
      { label: '银行卡', value: 'BANK_CARD' },
      { label: '信用卡', value: 'CREDIT_CARD' },
      { label: '支付宝', value: 'ALIPAY' },
      { label: '微信', value: 'WECHAT' },
    ];
    const orders = [];
    for (const _ of channels) {
      orders.push(await placeOrder(ctx, token, [{ goods_id: goodsA.id, quantity: 1 }]));
    }

    await buyerLoginUi(page, buyer.username, buyer.password);
    for (const [index, channel] of channels.entries()) {
      const order = orders[index]!;
      await page.goto(`${BUYER_URL}/orders/${order.order_id}/pay`);
      await buyerPayOnPage(page, channel.label);

      await expect(page.getByTestId('notice')).toContainText('支付成功');
      await expect(page.getByTestId('status-tag')).toHaveText('已支付');
      await expect(page.getByText(channel.label).first()).toBeVisible();

      const detail = await sellerOrderDetail(ctx, sellerToken, order.order_id);
      expect(detail.payment_channel).toBe(channel.value);
      expect(detail.paid_at).not.toBeNull();
    }
  });

  test('CO-07 未选渠道不可提交', async ({ page }) => {
    const buyer = buyerFixture('co07');
    const token = await newBuyer(ctx, buyer);
    const order = await placeOrder(ctx, token, [{ goods_id: goodsA.id, quantity: 1 }]);

    await buyerLoginUi(page, buyer.username, buyer.password);
    await page.goto(`${BUYER_URL}/orders/${order.order_id}/pay`);

    await expect(page.getByTestId('pay-submit')).toBeDisabled();
    await page.locator('label').filter({ hasText: '微信' }).getByTestId('pay-channel').check();
    await expect(page.getByTestId('pay-submit')).toBeEnabled();
  });

  test('CO-08 已支付不可再付', async ({ page }) => {
    const buyer = buyerFixture('co08');
    const token = await newBuyer(ctx, buyer);
    const order = await placeOrder(ctx, token, [{ goods_id: goodsA.id, quantity: 1 }]);
    await payOrder(ctx, token, order.order_id, 'WECHAT');

    await buyerLoginUi(page, buyer.username, buyer.password);
    await page.goto(`${BUYER_URL}/orders/${order.order_id}/pay`);
    await expect(page.getByText('这笔订单当前不可支付，可能已经支付或已被取消')).toBeVisible();
    await expect(page.getByRole('link', { name: '回订单详情查看' })).toBeVisible();

    const again = await ctx.fetch(`/api/buyer/orders/${order.order_id}/pay`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: JSON.stringify({ payment_channel: 'ALIPAY' }),
    });
    expect((await again.json() as { code: number }).code).toBe(83002);
  });

  test('CO-09 支付失败仍待支付、可换渠道重试（独立批次）', async ({ page }) => {
    test.skip(
      !process.env.E2E_PAYMENT_FAIL_BATCH,
      '需要后端以 PAYMENT_MOCK_FAIL_CHANNELS=ALIPAY 重启后单独跑',
    );
    const buyer = buyerFixture('co09');
    const token = await newBuyer(ctx, buyer);
    const order = await placeOrder(ctx, token, [{ goods_id: goodsA.id, quantity: 1 }]);

    await buyerLoginUi(page, buyer.username, buyer.password);
    await page.goto(`${BUYER_URL}/orders/${order.order_id}/pay`);
    await page.locator('label').filter({ hasText: '支付宝' }).getByTestId('pay-channel').check();
    await page.getByTestId('pay-submit').click();

    await expect(page.getByText('支付未成功')).toBeVisible();
    const failed = await sellerOrderDetail(ctx, sellerToken, order.order_id);
    expect(failed.status).toBe('PENDING_PAYMENT');
    expect(failed.payment_channel).toBeNull();
    expect(failed.paid_at).toBeNull();

    await buyerPayOnPage(page, '微信');
    const ok = await sellerOrderDetail(ctx, sellerToken, order.order_id);
    expect(ok.status).toBe('PAID');
  });

  test('CO-10 已支付取消 → 全额退款', async ({ page }) => {
    const buyer = buyerFixture('co10');
    const token = await newBuyer(ctx, buyer);
    const order = await placeOrder(ctx, token, [{ goods_id: goodsA.id, quantity: 2 }]);
    await payOrder(ctx, token, order.order_id, 'ALIPAY');

    await buyerLoginUi(page, buyer.username, buyer.password);
    await page.goto(`${BUYER_URL}/orders/${order.order_id}`);
    await page.getByRole('button', { name: '取消订单' }).click();
    await expect(page.getByText('确认取消这笔订单？已支付金额将全额原路退回。')).toBeVisible();
    await page.getByTestId('order-cancel').click();

    await expect(page.getByTestId('status-tag')).toHaveText('已取消');
    await expect(page.getByTestId('order-refunded')).toContainText('¥30.00');

    const sellerView = await sellerOrderDetail(ctx, sellerToken, order.order_id);
    expect(sellerView.refunded_amount).toBe(30);
  });

  test('CO-11 待支付取消不退款', async ({ page }) => {
    const buyer = buyerFixture('co11');
    const token = await newBuyer(ctx, buyer);
    const order = await placeOrder(ctx, token, [{ goods_id: goodsA.id, quantity: 1 }]);

    await buyerLoginUi(page, buyer.username, buyer.password);
    await page.goto(`${BUYER_URL}/orders/${order.order_id}`);
    await page.getByRole('button', { name: '取消订单' }).click();
    await expect(page.getByText('确认取消这笔订单吗？取消后不可恢复。')).toBeVisible();
    await page.getByTestId('order-cancel').click();

    await expect(page.getByTestId('status-tag')).toHaveText('已取消');
    await expect(page.getByTestId('order-refunded')).toHaveCount(0);

    const detail = await sellerOrderDetail(ctx, sellerToken, order.order_id);
    expect(detail.refunded_amount).toBe(0);
  });

  test('CO-12 支付参数校验（接口层）', async () => {
    const buyer = buyerFixture('co12');
    const token = await newBuyer(ctx, buyer);
    const order = await placeOrder(ctx, token, [{ goods_id: goodsA.id, quantity: 1 }]);

    const missing = await ctx.fetch(`/api/buyer/orders/${order.order_id}/pay`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: JSON.stringify({}),
    });
    expect(missing.status()).toBe(400);
    const missingBody = (await missing.json()) as { code: number; errors?: unknown[] };
    expect(missingBody.code).toBe(50002);
    expect(Array.isArray(missingBody.errors)).toBe(true);

    const illegal = await ctx.fetch(`/api/buyer/orders/${order.order_id}/pay`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: JSON.stringify({ payment_channel: 'BITCOIN' }),
    });
    expect((await illegal.json() as { code: number }).code).toBe(83001);

    const detail = await sellerOrderDetail(ctx, sellerToken, order.order_id);
    expect(detail.status, '两次非法请求都不应改变订单').toBe('PENDING_PAYMENT');

    // 顺带把该单推走，避免影响后续断言里「待支付」计数
    await advanceOrder(ctx, sellerToken, order.order_id, 'cancel');
  });

  test('CO-13 24 小时超时取消（独立批次）', async ({ page }) => {
    test.skip(
      !process.env.E2E_TIMEOUT_BATCH,
      '需要后端以 ORDER_PAYMENT_TIMEOUT=1m、ORDER_TIMEOUT_SCAN_INTERVAL_MS=5000 重启后单独跑',
    );
    const buyer = buyerFixture('co13');
    const token = await newBuyer(ctx, buyer);
    const order = await placeOrder(ctx, token, [{ goods_id: goodsA.id, quantity: 1 }]);

    await buyerLoginUi(page, buyer.username, buyer.password);
    await page.waitForTimeout(65_000);
    await page.goto(`${BUYER_URL}/orders/${order.order_id}`);
    await page.reload();

    await expect(page.getByTestId('status-tag')).toHaveText('已取消');
    const detail = await sellerOrderDetail(ctx, sellerToken, order.order_id);
    expect(detail.refunded_amount).toBe(0);

    // 超时取消不改库存：商品仍是在售，库存未变
    const goods = await getSellerGoods(ctx, sellerToken, goodsA.id);
    expect(goods.status).toBe('ON_SALE');
    expect(goods.stock).toBe(100);
  });
});
