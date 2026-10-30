import { expect, test, type APIRequestContext } from '@playwright/test';

import {
  call,
  createL1,
  createL2,
  newApi,
  publishGoods,
  sellerLogin,
} from '../helpers/api';
import { goodsFixture, uniqueName } from '../helpers/data';

/**
 * 05 契约与参数校验
 *
 * 阶段二统一口径（交接文档 §2.2）：
 *  - 参数错误 → HTTP 400 + 50002 + errors[{field,message}]
 *  - 语义错误 → HTTP 200 + 领域码（如价格精度 20014、库存 20015）
 * 这是 S4 新增的行为，旧套件没有；写断言时以"能不能定位到字段"为准。
 */
test.describe('05 契约与参数校验', () => {
  let ctx: APIRequestContext;
  let sellerToken: string;
  let l2Id: string;

  test.beforeAll(async () => {
    ctx = await newApi();
    sellerToken = await sellerLogin(ctx);
    const tag = uniqueName('v');
    const l1 = await createL1(ctx, sellerToken, `校验一级-${tag}`);
    const l2 = await createL2(ctx, sellerToken, l1.id, `校验二级-${tag}`);
    l2Id = l2.id;
  });

  test.afterAll(async () => {
    await ctx.dispose();
  });

  test('T05-01 空 body → 400 + 50002', async () => {
    const result = await call(ctx, 'POST', '/api/seller/auth/session', {});
    expect(result.status).toBe(400);
    expect(result.body.code).toBe(50002);
  });

  test('T05-02 缺必填字段 → errors 能定位到字段', async () => {
    const result = await call(ctx, 'POST', '/api/seller/goods', {}, { token: sellerToken });
    expect(result.status).toBe(400);
    expect(result.body.code).toBe(50002);
    const fields = (result.body.errors ?? []).map((item) => item.field);
    expect(fields, `errors 应包含 name，实际 ${JSON.stringify(fields)}`).toContain('name');
  });

  test('T05-03 名称 51 字 → 拒绝', async () => {
    const name = 'a'.repeat(51);
    const result = await call(
      ctx,
      'POST',
      '/api/seller/goods',
      goodsFixture(name, l2Id),
      { token: sellerToken },
    );
    expect(result.body.code).not.toBe(0);
  });

  test('T05-04 名称 50 字 → 通过（边界）', async () => {
    const name = `${uniqueName('边界')}${'a'.repeat(40)}`.slice(0, 50);
    const created = await publishGoods(ctx, sellerToken, goodsFixture(name, l2Id, { stock: 1 }));
    expect(created.id).toBeTruthy();
    expect(created.name.length).toBeLessThanOrEqual(50);
  });

  test('T05-05 价格 0 → 拒绝', async () => {
    const result = await call(
      ctx,
      'POST',
      '/api/seller/goods',
      goodsFixture(uniqueName('价格0'), l2Id, { price: '0' }),
      { token: sellerToken },
    );
    expect(result.body.code).not.toBe(0);
  });

  test('T05-06 价格三位小数 → 拒绝', async () => {
    const result = await call(
      ctx,
      'POST',
      '/api/seller/goods',
      goodsFixture(uniqueName('精度'), l2Id, { price: '1.999' }),
      { token: sellerToken },
    );
    expect(result.body.code).not.toBe(0);
  });

  test('T05-07 发布库存 0 → 拒绝（发布要求 ≥1）', async () => {
    const result = await call(
      ctx,
      'POST',
      '/api/seller/goods',
      goodsFixture(uniqueName('零库存'), l2Id, { stock: 0 }),
      { token: sellerToken },
    );
    expect(result.body.code).not.toBe(0);
  });

  test('T05-08 非法枚举 disposal → 400 + 50002', async () => {
    // 先造一条意向，再拿非法处置值调用失败裁决
    const goods = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('枚举'), l2Id, { stock: 1 }),
    );
    const result = await call(
      ctx,
      'POST',
      `/api/seller/intentions/ANY-ID/failure`,
      { disposal: 'not-a-valid-disposal' },
      { token: sellerToken },
    );
    expect(result.status).toBe(400);
    expect(result.body.code).toBe(50002);
    expect((result.body.errors ?? []).map((item) => item.field)).toContain('disposal');
    expect(goods.id).toBeTruthy();
  });

  test('T05-09 未登录调卖家接口 → 401', async () => {
    const result = await call(ctx, 'GET', '/api/seller/goods');
    expect(result.status).toBe(401);
  });

  test('T05-10 分页默认 10 条', async () => {
    const result = await call<{ page: number; page_size: number }>(ctx, 'GET', '/api/public/goods');
    expect(result.body.code).toBe(0);
    expect(result.body.data.page_size).toBe(10);
    expect(result.body.data.page).toBe(1);
  });
});
