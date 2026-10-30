import { expect, test, type APIRequestContext } from '@playwright/test';

import {
  buyerHistory,
  call,
  createL1,
  createL2,
  enterTrade,
  getSellerGoods,
  listIntentions,
  markFailure,
  markSuccess,
  newApi,
  newBuyer,
  publishGoods,
  sellerHistory,
  sellerHistoryDetail,
  sellerLogin,
  submitIntention,
} from '../helpers/api';
import { buyerFixture, goodsFixture, uniqueName } from '../helpers/data';
import { BUYER_URL, SELLER_URL } from '../helpers/config';
import { buyerLoginUi, openGoodsDetail, sellerLoginUi } from '../helpers/ui';

/**
 * 04 意向队列与交易裁决（状态机核心）
 *
 * 用例之间有状态依赖，用 describe.serial 固定顺序（workers:1）。
 * 关键口径：
 *  - 只能对队首进入交易（BR-12）
 *  - 进入交易自动冻结：买家列表消失、直链可看不可买（澄清#19）
 *  - 成交后有余→回在售；卖最后一件→归档并清其余意向（分析§11、状态模型 v1.2）
 *  - 失败裁决二选一：作废 / 重新排队（回队尾、下单时间不变，DEC-10/15）
 */
test.describe.serial('04 交易队列与裁决', () => {
  interface Buyer {
    username: string;
    password: string;
    phone: string;
    token: string;
  }

  let ctx: APIRequestContext;
  let sellerToken: string;
  let l2Id: string;
  let goodsId: string;
  let goodsName: string;
  let buyers: Buyer[] = [];
  /** T04-03 进入交易的那条意向：后续失败裁决必须作用在它上面，而不是"当前队首" */
  let tradingIntentionId = '';
  /** T04-03 进入交易的那条意向：后续失败裁决必须作用在它上面，而不是"当前队首" */

  /** 取当前队首：状态 QUEUED 且 queue_order 最小（列表里会混入已终态的历史行） */
  function headOf(items: Array<{ id: string; status: string; queue_order: number }>) {
    const queued = items
      .filter((item) => item.status === 'QUEUED')
      .sort((a, b) => a.queue_order - b.queue_order);
    if (queued.length === 0) throw new Error('队列里没有排队中的意向');
    return queued[0];
  }

  async function addBuyer(prefix: string): Promise<Buyer> {
    const fixture = buyerFixture(prefix);
    const token = await newBuyer(ctx, fixture);
    const buyer: Buyer = { ...fixture, token };
    buyers.push(buyer);
    return buyer;
  }

  test.beforeAll(async () => {
    ctx = await newApi();
    sellerToken = await sellerLogin(ctx);
    const tag = uniqueName('t');
    const l1 = await createL1(ctx, sellerToken, `交一级-${tag}`);
    const l2 = await createL2(ctx, sellerToken, l1.id, `交二级-${tag}`);
    l2Id = l2.id;

    goodsName = uniqueName('交易商品');
    const goods = await publishGoods(ctx, sellerToken, goodsFixture(goodsName, l2Id, { stock: 2 }));
    goodsId = goods.id;

    // 甲、乙、丙 依次排队
    for (const prefix of ['甲', '乙', '丙']) {
      const buyer = await addBuyer(prefix);
      await submitIntention(ctx, buyer.token, goodsId);
    }
  });

  test.afterAll(async () => {
    await ctx.dispose();
  });

  test('T04-01 名单先到先得，只有队首能进入交易', async ({ page }) => {
    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/`);
    await page.getByLabel('搜索商品名称').fill(goodsName);
    await page.getByRole('button', { name: '搜索' }).click();
    await openGoodsDetail(page, goodsName);

    const rows = page.locator('tbody tr');
    await expect(rows).toHaveCount(3);
    await expect(rows.first()).toContainText('队首');
    await expect(page.getByRole('button', { name: '进入交易' })).toHaveCount(1);
  });

  test('T04-02 对非队首进入交易 → 拒绝（30002）', async () => {
    const list = await listIntentions(ctx, sellerToken, goodsId);
    const second = list.items[1];
    const result = await call(ctx, 'POST', `/api/seller/intentions/${second.id}/trade`, {}, {
      token: sellerToken,
    });
    expect(result.body.code).toBe(30002);
  });

  test('T04-03 队首进入交易 → 商品交易中；买家直链可看不可买', async ({ page }) => {
    const list = await listIntentions(ctx, sellerToken, goodsId);
    tradingIntentionId = headOf(list.items).id;
    await enterTrade(ctx, sellerToken, tradingIntentionId);

    const goods = await getSellerGoods(ctx, sellerToken, goodsId);
    expect(goods.status).toBe('FROZEN');

    await sellerLoginUi(page);
    await page.goto(`${SELLER_URL}/goods/${goodsId}`);
    await expect(page.locator('.tag').filter({ hasText: '交易中' }).first()).toBeVisible();

    await page.goto(`${BUYER_URL}/goods/${goodsId}`);
    const buy = page.getByRole('button', { name: '商品交易中，暂停接收新意向' });
    await expect(buy).toBeVisible();
    await expect(buy).toBeDisabled();
  });

  test('T04-04 失败 + 重新排队 → 回队尾，下单时间不变', async () => {
    const before = await listIntentions(ctx, sellerToken, goodsId);
    const head = before.items.find((item) => item.id === tradingIntentionId)!;
    expect(head, 'T04-03 进入交易的那条意向应还在名单里').toBeTruthy();

    await markFailure(ctx, sellerToken, tradingIntentionId, 'REQUEUED');

    const after = await listIntentions(ctx, sellerToken, goodsId);
    const moved = after.items.find((item) => item.id === tradingIntentionId)!;
    expect(moved.status).toBe('QUEUED');
    expect(moved.queue_order).toBeGreaterThan(head.queue_order);
    expect(moved.create_at, '重新排队不改变原下单时间').toBe(head.create_at);

    const goods = await getSellerGoods(ctx, sellerToken, goodsId);
    expect(goods.status).toBe('ON_SALE');
  });

  test('T04-05 失败 + 作废 → 意向终态失败，商品回在售且不扣库存', async () => {
    const list = await listIntentions(ctx, sellerToken, goodsId);
    const head = headOf(list.items);
    await enterTrade(ctx, sellerToken, head.id);
    await markFailure(ctx, sellerToken, head.id, 'VOIDED');

    const after = await listIntentions(ctx, sellerToken, goodsId);
    const voided = after.items.find((item) => item.id === head.id)!;
    expect(voided.status).toBe('FAILED');
    expect(voided.fail_type).toBe('SELLER_VOIDED');

    const goods = await getSellerGoods(ctx, sellerToken, goodsId);
    expect(goods.status).toBe('ON_SALE');
    expect(goods.stock, '作废不扣库存').toBe(2);
  });

  test('T04-06 库存有余成交 → 回在售、扣 1 件、其余仍排队', async () => {
    const list = await listIntentions(ctx, sellerToken, goodsId);
    const head = headOf(list.items);
    await enterTrade(ctx, sellerToken, head.id);
    const result = await markSuccess(ctx, sellerToken, head.id);

    expect(result.goods_status).toBe('ON_SALE');
    const goods = await getSellerGoods(ctx, sellerToken, goodsId);
    expect(goods.stock).toBe(1);
    expect(goods.status).toBe('ON_SALE');

    const after = await listIntentions(ctx, sellerToken, goodsId);
    expect(after.items.filter((item) => item.status === 'QUEUED').length).toBeGreaterThan(0);
  });

  test('T04-07 卖最后一件 → 归档；其余排队转「商品已售出」', async () => {
    // 再拉一人排队，验证"售罄时其余人自动失败"
    const extra = await addBuyer('丁');
    await submitIntention(ctx, extra.token, goodsId);

    const list = await listIntentions(ctx, sellerToken, goodsId);
    const head = headOf(list.items);
    await enterTrade(ctx, sellerToken, head.id);
    const result = await markSuccess(ctx, sellerToken, head.id);
    expect(result.goods_status).toBe('ARCHIVED');

    // 卖家历史能看到这条归档
    const history = await sellerHistory(ctx, sellerToken);
    const summary = (history.items as Array<{ id?: string; goods_id?: string }>).find(
      (item) => (item.id ?? item.goods_id) === goodsId,
    );
    expect(summary, '归档后应出现在卖家历史').toBeTruthy();

    // 归档详情里有失败意向（商品已售出）
    const detail = await sellerHistoryDetail(ctx, sellerToken, goodsId);
    expect(JSON.stringify(detail)).toContain('FAILED');
    expect(JSON.stringify(detail)).toContain('SOLD_OUT');

    // 买家端详情已不可访问
    const pub = await call(ctx, 'GET', `/api/public/goods/${goodsId}`);
    expect(pub.status === 404 || pub.body.code !== 0).toBeTruthy();
  });

  test('T04-08 成交买家「我的历史」显示交易成功', async ({ page }) => {
    // 找出最后成交那位买家：历史里 SUCCEEDED 且商品名匹配
    let winner: Buyer | undefined;
    for (const buyer of buyers) {
      const history = await buyerHistory(ctx, buyer.token);
      const hit = (history.items as Array<{ goods_name?: string; result?: string }>).find(
        (item) => item.goods_name === goodsName && item.result === 'SUCCEEDED',
      );
      if (hit) {
        winner = buyer;
        break;
      }
    }
    expect(winner, '应有一位买家看到交易成功').toBeTruthy();

    await buyerLoginUi(page, winner!.username, winner!.password);
    await page.goto(`${BUYER_URL}/history`);
    const table = page.locator('table');
    await expect(table).toBeVisible();
    await expect(table).toContainText(goodsName);
    await expect(table).toContainText('交易成功');
  });
});
