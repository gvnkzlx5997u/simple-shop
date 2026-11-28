import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

import {
  applyAftersale,
  cancelSellerAftersale,
  completedOrderViaApi,
  completeSellerAftersale,
  createL1,
  createL2,
  listSellerAftersales,
  newApi,
  newBuyer,
  publishGoods,
  sellerAftersaleDetail,
  sellerLogin,
  sellerOrderDetail,
  sellerOrderItems,
  uploadAftersaleImage,
} from '../helpers/api';
import type { AftersaleAppliedRow, GoodsView, OrderItemRow, OrderRow } from '../helpers/api';
import { SELLER_URL } from '../helpers/config';
import { aftersaleFixture, buyerFixture, goodsFixture, uniqueName } from '../helpers/data';
import { pngFile } from '../helpers/files';
import { confirmDialog, openSellerAftersale, sellerLoginUi, toast } from '../helpers/ui';

/**
 * 12 卖家售后（阶段四新增）
 *
 * 覆盖：三态 tab 与列、订单号搜索、从订单详情带筛选进入、详情展示、
 * 完成三选一（换货 / 仅退款）、取消拒绝、已完成终态、重复退款被拒。
 *
 * 前置订单与售后全部经接口造，断言看页面与接口返回。
 */
test.describe.serial('12 卖家售后', () => {
  let ctx: APIRequestContext;
  let sellerToken: string;
  let goodsA: GoodsView;
  let goodsB: GoodsView;

  test.beforeAll(async () => {
    ctx = await newApi();
    sellerToken = await sellerLogin(ctx);
    const tag = uniqueName('sa');
    const l1 = await createL1(ctx, sellerToken, `卖家售后一级-${tag}`);
    const l2 = await createL2(ctx, sellerToken, l1.id, `卖家售后二级-${tag}`);
    goodsA = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('卖家售后商品A'), l2.id, { stock: 500, price: '18.00' }),
    );
    goodsB = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('卖家售后商品B'), l2.id, { stock: 500, price: '25.00' }),
    );
  });

  test.afterAll(async () => {
    await ctx.dispose();
  });

  interface Prepared {
    order: OrderRow;
    item: OrderItemRow;
    aftersale: AftersaleAppliedRow;
  }

  /** 造一张交易完成订单并在指定明细行提一条待处理售后。 */
  async function preparePending(goods: GoodsView, quantity = 1, index = 1): Promise<Prepared> {
    const buyer = buyerFixture('sa');
    const token = await newBuyer(ctx, buyer);
    const order = await completedOrderViaApi(ctx, sellerToken, token, [
      { goods_id: goods.id, quantity },
    ]);
    const item = (await sellerOrderItems(ctx, sellerToken, order.order_id)).items[0]!;
    const aftersale = await applyAftersale(ctx, token, {
      order_item_id: item.order_item_id,
      ...aftersaleFixture(index),
    });
    return { order, item, aftersale };
  }

  /** 卖家登录后打开售后列表 / 详情。 */
  async function openList(page: Page, query = '') {
    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/aftersales${query}`);
    await expect(page.getByRole('heading', { name: '售后管理' })).toBeVisible();
  }

  async function openDetail(page: Page, aftersaleId: string) {
    await sellerLoginUi(page);
    await openSellerAftersale(page, aftersaleId);
  }

  /** 列表里状态列（第 5 列）的所有徽章文本。 */
  function statusColumn(page: Page) {
    return page.locator('tbody tr td:nth-child(5) [data-testid="status-tag"]');
  }

  test('SA-01 三态 tab 与列表列', async ({ page }) => {
    const pending = await preparePending(goodsA, 1, 1);
    const done = await preparePending(goodsB, 1, 2);
    await completeSellerAftersale(ctx, sellerToken, done.aftersale.aftersale_id, 'EXCHANGE');
    const canceled = await preparePending(goodsA, 1, 3);
    await cancelSellerAftersale(ctx, sellerToken, canceled.aftersale.aftersale_id);

    await openList(page);

    for (const name of ['全部', '待处理', '已完成', '已取消']) {
      await expect(page.getByRole('tab', { name, exact: true })).toBeVisible();
    }
    const headers = (await page.locator('thead th').allTextContents()).map((text) => text.trim());
    expect(headers).toEqual([
      '售后单号',
      '订单号',
      '商品',
      '标题',
      '状态',
      '处理结果',
      '退款金额',
      '申请时间',
    ]);

    await page.getByRole('tab', { name: '待处理', exact: true }).click();
    await expect(page).toHaveURL(/status=PENDING/);
    await expect(page.getByRole('row').filter({ hasText: pending.aftersale.aftersale_no })).toBeVisible();
    const pendingTexts = await statusColumn(page).allTextContents();
    expect(pendingTexts.length).toBeGreaterThan(0);
    expect(pendingTexts.every((text) => text.trim() === '待处理')).toBe(true);

    await page.getByRole('tab', { name: '已完成', exact: true }).click();
    await expect(page).toHaveURL(/status=COMPLETED/);
    const completedRow = page.getByRole('row').filter({ hasText: done.aftersale.aftersale_no });
    await expect(completedRow).toBeVisible();
    await expect(completedRow, '未完成时处理结果为 —').toContainText('换货');
    const completedTexts = await statusColumn(page).allTextContents();
    expect(completedTexts.every((text) => text.trim() === '已完成')).toBe(true);

    await page.getByRole('tab', { name: '已取消', exact: true }).click();
    await expect(page).toHaveURL(/status=CANCELED/);
    await expect(
      page.getByRole('row').filter({ hasText: canceled.aftersale.aftersale_no }),
    ).toBeVisible();

    await page.getByRole('tab', { name: '全部', exact: true }).click();
    await expect(page).not.toHaveURL(/status=/);

    const pendingRow = page.getByRole('row').filter({ hasText: pending.aftersale.aftersale_no });
    await expect(pendingRow.locator('td').nth(5), '未完成不给处理结果').toHaveText('—');
    await expect(pendingRow.locator('td').nth(6), '未完成不给退款额').toHaveText('—');
  });

  test('SA-02 按订单号搜索与未知单号空态', async ({ page }) => {
    const target = await preparePending(goodsA, 1, 4);

    await openList(page);
    const input = page.getByLabel('按订单号搜索');
    await input.fill(target.order.order_no);
    await page.getByRole('button', { name: '搜索' }).click();

    await expect(page).toHaveURL(/order_no=SO\d{14}/);
    const rows = page.getByRole('row').filter({ hasText: target.order.order_no });
    await expect(rows).toHaveCount(1);
    await expect(rows.first()).toContainText(target.aftersale.aftersale_no);

    await input.fill('SO00000000000000');
    await page.getByRole('button', { name: '搜索' }).click();
    await expect(page.getByText('当前筛选下没有售后单，换个条件或查看「全部」试试')).toBeVisible();
    await expect(page.getByRole('button', { name: '查看全部售后' })).toBeVisible();

    await page.getByRole('button', { name: '查看全部售后' }).click();
    await expect(page).not.toHaveURL(/order_no=/);
  });

  test('SA-03 从订单详情带筛选进入售后列表', async ({ page }) => {
    const prepared = await preparePending(goodsB, 1, 5);

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/orders/${prepared.order.order_id}`);
    await expect(page.getByRole('heading', { name: '订单详情' })).toBeVisible();

    await page.getByRole('link', { name: '在售后管理中查看' }).click();
    await expect(page).toHaveURL(
      (url) => url.pathname === '/aftersales' && url.searchParams.get('order_no') === prepared.order.order_no,
    );
    await expect(page.getByRole('heading', { name: '售后管理' })).toBeVisible();
    await expect(page.getByLabel('按订单号搜索')).toHaveValue(prepared.order.order_no);
    await expect(
      page.getByRole('row').filter({ hasText: prepared.aftersale.aftersale_no }),
    ).toBeVisible();
  });

  test('SA-04 详情展示：订单号链接、多行描述、凭证图原图地址', async ({ page }) => {
    const buyer = buyerFixture('sa04');
    const token = await newBuyer(ctx, buyer);
    const order = await completedOrderViaApi(ctx, sellerToken, token, [
      { goods_id: goodsA.id, quantity: 1 },
    ]);
    const item = (await sellerOrderItems(ctx, sellerToken, order.order_id)).items[0]!;
    const imageUrl = await uploadAftersaleImage(ctx, token, pngFile('sa04.png'));
    const fixture = aftersaleFixture(6);
    const aftersale = await applyAftersale(ctx, token, {
      order_item_id: item.order_item_id,
      ...fixture,
      image_urls: [imageUrl],
    });

    await openDetail(page, aftersale.aftersale_id);

    await expect(page.getByRole('heading', { name: '售后详情' })).toBeVisible();
    await expect(page.getByText(aftersale.aftersale_no)).toBeVisible();
    await expect(page.getByText(goodsA.name).first()).toBeVisible();
    await expect(page.getByText(fixture.title)).toBeVisible();
    await expect(page.getByRole('heading', { name: '买家描述' })).toBeVisible();
    const description = page.locator('p.whitespace-pre-line');
    await expect(description).toContainText('第一行说明 6');
    await expect(description).toContainText('第二行说明 6');

    await expect(page.getByRole('link', { name: order.order_no })).toHaveAttribute(
      'href',
      `/orders/${order.order_id}`,
    );
    const imageLink = page.locator('a[title="点击查看大图"]');
    await expect(imageLink).toHaveCount(1);
    await expect(imageLink).toHaveAttribute('href', imageUrl);
  });

  test('SA-05 完成（换货）：不选时禁用、换货不退款、按钮消失', async ({ page }) => {
    const prepared = await preparePending(goodsA, 2, 7);
    await openDetail(page, prepared.aftersale.aftersale_id);

    await page.getByTestId('aftersale-complete').click();
    const dialog = page.getByTestId('aftersale-result-dialog');
    await expect(dialog).toBeVisible();
    await expect(page.getByText('请选择处理结果')).toBeVisible();
    await expect(page.getByTestId('aftersale-result-submit')).toBeDisabled();
    for (const value of ['退货并退款', '仅退款', '换货']) {
      await expect(dialog.getByRole('radio', { name: new RegExp(value) })).not.toBeChecked();
    }

    await dialog.getByRole('radio', { name: /换货/ }).check();
    await expect(page.getByTestId('aftersale-result-submit')).toBeEnabled();
    await page.getByTestId('aftersale-result-submit').click();
    await expect(dialog).toBeHidden();

    await expect(page.getByTestId('status-tag').first()).toHaveText('已完成');
    await expect(page.getByText('换货').first()).toBeVisible();
    await expect(page.getByText('退款金额'), '换货不产生退款').toHaveCount(0);
    await expect(
      page.getByText('换货不产生退款，该订单项之后仍可再申请售后。'),
    ).toBeVisible();
    await expect(page.getByTestId('aftersale-complete')).toHaveCount(0);
    await expect(page.getByTestId('aftersale-cancel')).toHaveCount(0);

    const detail = await sellerAftersaleDetail(ctx, sellerToken, prepared.aftersale.aftersale_id);
    expect(detail.status).toBe('COMPLETED');
    expect(detail.result).toBe('EXCHANGE');
    expect(detail.refund_amount).toBeNull();
  });

  test('SA-06 完成（仅退款）：退款额 = 单价 × 数量，订单已退款增加', async ({ page }) => {
    const prepared = await preparePending(goodsB, 3, 8);
    const before = await sellerOrderDetail(ctx, sellerToken, prepared.order.order_id);
    expect(before.refunded_amount).toBe(0);

    await openDetail(page, prepared.aftersale.aftersale_id);
    await page.getByTestId('aftersale-complete').click();
    await page
      .getByTestId('aftersale-result-dialog')
      .getByRole('radio', { name: /仅退款/ })
      .check();
    await page.getByTestId('aftersale-result-submit').click();
    await expect(page.getByTestId('aftersale-result-dialog')).toBeHidden();

    await expect(page.getByTestId('status-tag').first()).toHaveText('已完成');
    await expect(page.getByText('退款金额')).toBeVisible();
    await expect(page.getByText('¥75.00').first()).toBeVisible();
    await expect(page.getByText('退款已登记，同一订单项只能退款一次。')).toBeVisible();

    const after = await sellerOrderDetail(ctx, sellerToken, prepared.order.order_id);
    expect(after.refunded_amount).toBe(75);
  });

  test('SA-07 取消 / 拒绝：状态已取消、可重新申请、按钮消失', async ({ page }) => {
    const prepared = await preparePending(goodsA, 1, 9);
    await openDetail(page, prepared.aftersale.aftersale_id);

    await page.getByTestId('aftersale-cancel').click();
    await confirmDialog(page, '取消售后');

    await expect(page.getByTestId('status-tag').first()).toHaveText('已取消');
    await expect(
      page.getByText('已取消（同拒绝）：买家可以重新申请，重新申请会生成新的售后单号。'),
    ).toBeVisible();
    await expect(page.getByTestId('aftersale-complete')).toHaveCount(0);
    await expect(page.getByTestId('aftersale-cancel')).toHaveCount(0);
    await expect(toast(page)).toContainText('售后已取消');
  });

  test('SA-08 已完成是终态：无按钮、接口 81002', async ({ page }) => {
    const prepared = await preparePending(goodsB, 1, 10);
    await completeSellerAftersale(ctx, sellerToken, prepared.aftersale.aftersale_id, 'EXCHANGE');

    await openDetail(page, prepared.aftersale.aftersale_id);
    await expect(page.getByTestId('status-tag').first()).toHaveText('已完成');
    await expect(page.getByTestId('aftersale-complete')).toHaveCount(0);
    await expect(page.getByTestId('aftersale-cancel')).toHaveCount(0);

    const failedComplete = await ctx.fetch(
      `/api/seller/aftersales/${prepared.aftersale.aftersale_id}/complete`,
      {
        method: 'POST',
        headers: { Authorization: `Bearer ${sellerToken}`, 'Content-Type': 'application/json' },
        data: JSON.stringify({ result: 'REFUND_ONLY' }),
      },
    );
    expect((await failedComplete.json() as { code: number }).code).toBe(81002);

    const failedCancel = await ctx.fetch(
      `/api/seller/aftersales/${prepared.aftersale.aftersale_id}/cancel`,
      {
        method: 'POST',
        headers: { Authorization: `Bearer ${sellerToken}`, 'Content-Type': 'application/json' },
        data: JSON.stringify({}),
      },
    );
    expect((await failedCancel.json() as { code: number }).code).toBe(81002);
  });

  test('SA-09 同一订单项重复退款被拒（换货不受限）', async ({ page }) => {
    const buyer = buyerFixture('sa09');
    const token = await newBuyer(ctx, buyer);
    const order = await completedOrderViaApi(ctx, sellerToken, token, [
      { goods_id: goodsA.id, quantity: 1 },
    ]);
    const item = (await sellerOrderItems(ctx, sellerToken, order.order_id)).items[0]!;

    const first = await applyAftersale(ctx, token, {
      order_item_id: item.order_item_id,
      ...aftersaleFixture(12),
    });
    await completeSellerAftersale(ctx, sellerToken, first.aftersale_id, 'REFUND_ONLY');
    const second = await applyAftersale(ctx, token, {
      order_item_id: item.order_item_id,
      title: '第二次申请',
      description: '同一件商品再申请一次',
    });

    await openDetail(page, second.aftersale_id);
    await page.getByTestId('aftersale-complete').click();
    await page
      .getByTestId('aftersale-result-dialog')
      .getByRole('radio', { name: /仅退款/ })
      .check();
    await page.getByTestId('aftersale-result-submit').click();

    await expect(toast(page)).toContainText(
      '该订单项已经退过款，同一订单项只能退款一次（换货不受限）',
    );
    await expect(page.getByTestId('status-tag').first()).toHaveText('待处理');

    const after = await sellerAftersaleDetail(ctx, sellerToken, second.aftersale_id);
    expect(after.status).toBe('PENDING');
    const orderAfter = await sellerOrderDetail(ctx, sellerToken, order.order_id);
    expect(orderAfter.refunded_amount, '只算第一笔退款').toBe(18);

    const list = await listSellerAftersales(ctx, sellerToken, { order_no: order.order_no });
    expect(list.total).toBe(2);
  });
});
