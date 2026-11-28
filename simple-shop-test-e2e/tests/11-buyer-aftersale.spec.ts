import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

import {
  advanceOrder,
  applyAftersale,
  completedOrderViaApi,
  completeSellerAftersale,
  createL1,
  createL2,
  listBuyerAftersales,
  newApi,
  newBuyer,
  placeOrder,
  publishGoods,
  sellerLogin,
  sellerOrderItems,
  shippedOrderViaApi,
  uploadAftersaleImage,
} from '../helpers/api';
import type { GoodsView } from '../helpers/api';
import { BUYER_URL } from '../helpers/config';
import { aftersaleFixture, buyerFixture, goodsFixture, uniqueName } from '../helpers/data';
import { pngFile, tinyPng } from '../helpers/files';
import { buyerLoginUi } from '../helpers/ui';

/**
 * 11 买家售后（阶段四新增）
 *
 * 覆盖：入口只在交易完成出现、按订单项定位、标题描述必填、凭证图上传与删除、
 * 九张上限、提交进详情、三态筛选与空态、详情内容、换货后可再申请、
 * 非交易完成不可申请、订单详情里的售后区块。
 *
 * 造数全部走接口，断言只看接口返回与页面——不连数据库。
 */
test.describe.serial('11 买家售后', () => {
  let ctx: APIRequestContext;
  let sellerToken: string;
  let goodsA: GoodsView;
  let goodsB: GoodsView;
  let l2Id: string;

  test.beforeAll(async () => {
    ctx = await newApi();
    sellerToken = await sellerLogin(ctx);
    const tag = uniqueName('as');
    const l1 = await createL1(ctx, sellerToken, `售后一级-${tag}`);
    const l2 = await createL2(ctx, sellerToken, l1.id, `售后二级-${tag}`);
    l2Id = l2.id;
    goodsA = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('售后商品A'), l2Id, { stock: 500, price: '12.50' }),
    );
    goodsB = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('售后商品B'), l2Id, { stock: 500, price: '30.00' }),
    );
  });

  test.afterAll(async () => {
    await ctx.dispose();
  });

  /** 买家登录并打开某笔订单详情。 */
  async function openOrder(
    page: Page,
    buyer: { username: string; password: string },
    orderId: string,
  ) {
    await buyerLoginUi(page, buyer.username, buyer.password);
    await page.goto(`${BUYER_URL}/orders/${orderId}`);
    await expect(page.getByRole('heading', { name: '订单详情' })).toBeVisible();
  }

  /** 打开某笔订单第 index 行（从 0 起）的申请页。 */
  async function openApply(
    page: Page,
    buyer: { username: string; password: string },
    orderId: string,
    goodsName: string,
  ) {
    await openOrder(page, buyer, orderId);
    const row = page.locator('table tbody tr').filter({ hasText: goodsName });
    await row.getByTestId('aftersale-apply-entry').click();
    await expect(page.getByRole('heading', { name: '申请售后' })).toBeVisible();
  }

  test('AS-01 售后入口只在交易完成出现', async ({ page }) => {
    const buyer = buyerFixture('as01');
    const token = await newBuyer(ctx, buyer);
    const completed = await completedOrderViaApi(ctx, sellerToken, token, [
      { goods_id: goodsA.id, quantity: 1 },
    ]);
    const shipped = await shippedOrderViaApi(ctx, sellerToken, token, [
      { goods_id: goodsA.id, quantity: 1 },
    ]);
    const pending = await placeOrder(ctx, token, [{ goods_id: goodsA.id, quantity: 1 }]);
    await advanceOrder(ctx, sellerToken, pending.order_id, 'cancel');

    await openOrder(page, buyer, completed.order_id);
    const entry = page.getByTestId('aftersale-apply-entry');
    await expect(entry, '交易完成的订单每个明细行都有入口').toHaveCount(1);
    await entry.first().click();
    await expect(page).toHaveURL(
      (url) =>
        url.pathname === '/aftersales/apply' &&
        url.searchParams.get('order_id') === completed.order_id &&
        (url.searchParams.get('order_item_id') ?? '') !== '',
    );

    await openOrder(page, buyer, shipped.order_id);
    await expect(page.getByTestId('aftersale-apply-entry'), '已发货不能申请').toHaveCount(0);

    await openOrder(page, buyer, pending.order_id);
    await expect(page.getByTestId('aftersale-apply-entry'), '已取消不能申请').toHaveCount(0);
  });

  test('AS-02 申请页按订单项定位（两行明细取第 2 行）', async ({ page }) => {
    const buyer = buyerFixture('as02');
    const token = await newBuyer(ctx, buyer);
    const order = await completedOrderViaApi(ctx, sellerToken, token, [
      { goods_id: goodsA.id, quantity: 2 },
      { goods_id: goodsB.id, quantity: 1 },
    ]);

    await openApply(page, buyer, order.order_id, goodsB.name);

    const panel = page.locator('div.panel').filter({ hasText: '售后商品' });
    await expect(panel).toContainText(order.order_no);
    await expect(panel).toContainText(goodsB.name);
    await expect(panel, '单价 × 数量按本行原值展示').toContainText('¥30.00 × 1');
    await expect(panel).toContainText('本行金额');
    await expect(panel, '不能串到第 1 行的商品').not.toContainText(goodsA.name);
  });

  test('AS-03 标题与描述必填', async ({ page }) => {
    const buyer = buyerFixture('as03');
    const token = await newBuyer(ctx, buyer);
    const order = await completedOrderViaApi(ctx, sellerToken, token, [
      { goods_id: goodsA.id, quantity: 1 },
    ]);
    await openApply(page, buyer, order.order_id, goodsA.name);

    await page.getByTestId('aftersale-submit').click();
    await expect(page.getByText('请填标题')).toBeVisible();
    await expect(page).toHaveURL(/\/aftersales\/apply/);

    await page.getByLabel('标题').fill('只填标题');
    await page.getByTestId('aftersale-submit').click();
    await expect(page.getByText('请填描述')).toBeVisible();
    await expect(page).toHaveURL(/\/aftersales\/apply/);

    await page.getByLabel('描述').fill('两项都填了');
    await page.getByTestId('aftersale-submit').click();
    await expect(page).not.toHaveURL(/\/aftersales\/apply/);
  });

  test('AS-04 上传一张凭证图、可删除、提交后详情计数正确', async ({ page }) => {
    const buyer = buyerFixture('as04');
    const token = await newBuyer(ctx, buyer);
    const order = await completedOrderViaApi(ctx, sellerToken, token, [
      { goods_id: goodsA.id, quantity: 1 },
    ]);
    await openApply(page, buyer, order.order_id, goodsA.name);

    const upload = page.getByTestId('aftersale-upload');
    await upload.setInputFiles(pngFile('as04-1.png'));
    await expect(page.getByTestId('aftersale-image')).toHaveCount(1);

    await page.getByRole('button', { name: '删除第 1 张' }).click();
    await expect(page.getByTestId('aftersale-image')).toHaveCount(0);

    await upload.setInputFiles(pngFile('as04-2.png'));
    await expect(page.getByTestId('aftersale-image')).toHaveCount(1);

    const fixture = aftersaleFixture(4);
    await page.getByLabel('标题').fill(fixture.title);
    await page.getByLabel('描述').fill(fixture.description);
    await page.getByTestId('aftersale-submit').click();

    await expect(page.getByRole('heading', { name: '售后详情' })).toBeVisible();
    await expect(page.getByText('凭证图片（1 张）')).toBeVisible();
  });

  test('AS-05 凭证图最多 9 张', async ({ page }) => {
    const buyer = buyerFixture('as05');
    const token = await newBuyer(ctx, buyer);
    const order = await completedOrderViaApi(ctx, sellerToken, token, [
      { goods_id: goodsA.id, quantity: 1 },
    ]);
    await openApply(page, buyer, order.order_id, goodsA.name);

    const ten = Array.from({ length: 10 }, (_, index) => ({
      name: `as05-${index + 1}.png`,
      mimeType: 'image/png',
      buffer: tinyPng(),
    }));
    await page.getByTestId('aftersale-upload').setInputFiles(ten);

    await expect(page.getByText('最多上传 9 张图片')).toBeVisible();
    await expect(page.getByTestId('aftersale-image')).toHaveCount(9);

    const items = await sellerOrderItems(ctx, sellerToken, order.order_id);
    const urls: string[] = [];
    for (let index = 0; index < 10; index += 1) {
      urls.push(await uploadAftersaleImage(ctx, token, pngFile(`as05-api-${index}.png`)));
    }
    const rejected = await ctx.fetch('/api/buyer/aftersales', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: JSON.stringify({
        order_item_id: items.items[0]!.order_item_id,
        title: '十张图',
        description: '接口层也要拦',
        image_urls: urls,
      }),
    });
    expect((await rejected.json() as { code: number }).code, '超过 9 张返回 81007').toBe(81007);
  });

  test('AS-06 提交成功进详情：状态、单号格式、换行保留、列表可见', async ({ page }) => {
    const buyer = buyerFixture('as06');
    const token = await newBuyer(ctx, buyer);
    const order = await completedOrderViaApi(ctx, sellerToken, token, [
      { goods_id: goodsA.id, quantity: 1 },
    ]);
    await openApply(page, buyer, order.order_id, goodsA.name);

    const fixture = aftersaleFixture(6);
    await page.getByLabel('标题').fill(fixture.title);
    await page.getByLabel('描述').fill(fixture.description);
    await page.getByTestId('aftersale-upload').setInputFiles(pngFile('as06.png'));
    await expect(page.getByTestId('aftersale-image')).toHaveCount(1);
    await page.getByTestId('aftersale-submit').click();

    await expect(page).toHaveURL(/\/aftersales\/AS[0-9a-f-]{36}$/);
    await expect(page.getByTestId('status-tag')).toHaveText('待处理');
    const aftersaleNo =
      (await page.getByText(/^AS\d{12}$/).first().textContent())?.trim() ?? '';
    expect(aftersaleNo, '售后号应为 AS + 8 位日期 + 4 位序列').toMatch(/^AS\d{12}$/);

    await expect(page.getByRole('heading', { name: fixture.title })).toBeVisible();
    const description = page.locator('p.whitespace-pre-line');
    await expect(description).toContainText('第一行说明 6');
    await expect(description).toContainText('第二行说明 6');

    await page.goto(`${BUYER_URL}/aftersales`);
    await expect(page.getByTestId('aftersale-row').filter({ hasText: aftersaleNo })).toBeVisible();
  });

  test('AS-07 我的售后：三态筛选与空态', async ({ page }) => {
    const buyer = buyerFixture('as07');
    const token = await newBuyer(ctx, buyer);

    const orderPending = await completedOrderViaApi(ctx, sellerToken, token, [
      { goods_id: goodsA.id, quantity: 1 },
    ]);
    const itemPending = (await sellerOrderItems(ctx, sellerToken, orderPending.order_id)).items[0]!;
    await applyAftersale(ctx, token, {
      order_item_id: itemPending.order_item_id,
      ...aftersaleFixture(7),
    });

    const orderDone = await completedOrderViaApi(ctx, sellerToken, token, [
      { goods_id: goodsB.id, quantity: 1 },
    ]);
    const itemDone = (await sellerOrderItems(ctx, sellerToken, orderDone.order_id)).items[0]!;
    const done = await applyAftersale(ctx, token, {
      order_item_id: itemDone.order_item_id,
      ...aftersaleFixture(8),
    });
    await completeSellerAftersale(ctx, sellerToken, done.aftersale_id, 'EXCHANGE');

    await buyerLoginUi(page, buyer.username, buyer.password);
    await page.goto(`${BUYER_URL}/aftersales`);
    const filter = page.locator('select[aria-label="按状态筛选"]');

    await filter.selectOption({ label: '待处理' });
    await expect(page.getByTestId('aftersale-row')).toHaveCount(1);
    await expect(page.getByTestId('aftersale-row').first()).toContainText('待处理');

    await filter.selectOption({ label: '已完成' });
    await expect(page.getByTestId('aftersale-row')).toHaveCount(1);
    await expect(page.getByTestId('aftersale-row').first()).toContainText('已完成');

    await filter.selectOption({ label: '已取消' });
    await expect(page.getByText('没有该状态的售后单')).toBeVisible();
    await expect(page.getByRole('button', { name: '查看全部' })).toBeVisible();

    await page.getByRole('button', { name: '查看全部' }).click();
    await expect(page.getByTestId('aftersale-row')).toHaveCount(2);
    await expect(page.getByText('条', { exact: false }).first()).toBeVisible();
  });

  test('AS-08 售后详情内容：关联订单、换行描述、处理中提示', async ({ page }) => {
    const buyer = buyerFixture('as08');
    const token = await newBuyer(ctx, buyer);
    const order = await completedOrderViaApi(ctx, sellerToken, token, [
      { goods_id: goodsA.id, quantity: 1 },
    ]);
    const item = (await sellerOrderItems(ctx, sellerToken, order.order_id)).items[0]!;
    const applied = await applyAftersale(ctx, token, {
      order_item_id: item.order_item_id,
      ...aftersaleFixture(9),
    });

    await buyerLoginUi(page, buyer.username, buyer.password);
    await page.goto(`${BUYER_URL}/aftersales/${applied.aftersale_id}`);

    await expect(page.getByTestId('status-tag')).toHaveText('待处理');
    await expect(
      page.getByText('等待卖家线下协商后登记结果，期间你不能撤销或修改。'),
    ).toBeVisible();
    const description = page.locator('p.whitespace-pre-line');
    await expect(description).toContainText('第一行说明 9');
    await expect(description).toContainText('第二行说明 9');

    await page.getByRole('link', { name: order.order_no }).click();
    await expect(page).toHaveURL(`${BUYER_URL}/orders/${order.order_id}`);
  });

  test('AS-09 换货完成后同一订单项可再申请（新单号）', async ({ page }) => {
    const buyer = buyerFixture('as09');
    const token = await newBuyer(ctx, buyer);
    const order = await completedOrderViaApi(ctx, sellerToken, token, [
      { goods_id: goodsA.id, quantity: 1 },
    ]);
    const item = (await sellerOrderItems(ctx, sellerToken, order.order_id)).items[0]!;

    const first = await applyAftersale(ctx, token, {
      order_item_id: item.order_item_id,
      ...aftersaleFixture(10),
    });
    await completeSellerAftersale(ctx, sellerToken, first.aftersale_id, 'EXCHANGE');

    const second = await applyAftersale(ctx, token, {
      order_item_id: item.order_item_id,
      title: '换货后仍有问题',
      description: '换回来的还是不对',
    });
    expect(second.aftersale_no).not.toBe(first.aftersale_no);
    const seq = (no: string) => Number(no.slice(-4));
    expect(seq(second.aftersale_no), '同日序号应 +1').toBe(seq(first.aftersale_no) + 1);

    const list = await listBuyerAftersales(ctx, token, { order_id: order.order_id });
    expect(list.total).toBe(2);

    await buyerLoginUi(page, buyer.username, buyer.password);
    await page.goto(`${BUYER_URL}/aftersales`);
    await expect(page.getByTestId('aftersale-row')).toHaveCount(2);
  });

  test('AS-10 非交易完成订单不能申请', async ({ page }) => {
    const buyer = buyerFixture('as10');
    const token = await newBuyer(ctx, buyer);
    const shipped = await shippedOrderViaApi(ctx, sellerToken, token, [
      { goods_id: goodsA.id, quantity: 1 },
    ]);
    const item = (await sellerOrderItems(ctx, sellerToken, shipped.order_id)).items[0]!;

    await buyerLoginUi(page, buyer.username, buyer.password);
    await page.goto(
      `${BUYER_URL}/aftersales/apply?order_id=${shipped.order_id}&order_item_id=${item.order_item_id}`,
    );

    await expect(page.getByText('这件商品当前不可申请售后')).toBeVisible();
    await page.getByRole('link', { name: '回订单详情', exact: true }).click();
    await expect(page).toHaveURL(`${BUYER_URL}/orders/${shipped.order_id}`);
  });

  test('AS-11 订单详情的售后区块', async ({ page }) => {
    const buyer = buyerFixture('as11');
    const token = await newBuyer(ctx, buyer);
    const order = await completedOrderViaApi(ctx, sellerToken, token, [
      { goods_id: goodsA.id, quantity: 1 },
    ]);
    const item = (await sellerOrderItems(ctx, sellerToken, order.order_id)).items[0]!;
    const applied = await applyAftersale(ctx, token, {
      order_item_id: item.order_item_id,
      ...aftersaleFixture(11),
    });
    await completeSellerAftersale(ctx, sellerToken, applied.aftersale_id, 'REFUND_ONLY');

    await openOrder(page, buyer, order.order_id);

    await expect(page.getByText('已退款')).toBeVisible();
    await expect(page.getByTestId('order-refunded')).toHaveText('¥12.50');
    await expect(page.getByText('售后单')).toBeVisible();
    await expect(page.getByText('1 条')).toBeVisible();

    const block = page.getByTestId('order-aftersales');
    await expect(block).toBeVisible();
    await expect(block).toContainText(applied.aftersale_no);
    await expect(block).toContainText('已完成');
    await expect(block).toContainText(goodsA.name);
    await expect(block).toContainText('退款 ¥12.50');

    await block.getByRole('link', { name: applied.aftersale_no }).click();
    await expect(page).toHaveURL(`${BUYER_URL}/aftersales/${applied.aftersale_id}`);
    await expect(page.getByRole('heading', { name: '售后详情' })).toBeVisible();
  });
});
