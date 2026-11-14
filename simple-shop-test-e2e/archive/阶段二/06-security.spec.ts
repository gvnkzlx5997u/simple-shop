import { expect, test, type APIRequestContext } from '@playwright/test';

import {
  buyerLogin,
  call,
  changePassword,
  createL1,
  createL2,
  newApi,
  newBuyer,
  publishGoods,
  sellerLogin,
  submitIntention,
} from '../helpers/api';
import { buyerFixture, goodsFixture, uniqueName } from '../helpers/data';
import { SELLER_PASSWORD } from '../helpers/config';

/**
 * 06 服务端拦截（绕过前端的负向调用）
 *
 * 对应旧套件 03-security：D2（解冻）、D4（单商品）已作废，
 * 其余保留并换成阶段二的错误码。
 */
test.describe.serial('06 服务端拦截', () => {
  let ctx: APIRequestContext;
  let sellerToken: string;
  let l2Id: string;

  test.beforeAll(async () => {
    ctx = await newApi();
    sellerToken = await sellerLogin(ctx);
    const tag = uniqueName('s');
    const l1 = await createL1(ctx, sellerToken, `拦截一级-${tag}`);
    const l2 = await createL2(ctx, sellerToken, l1.id, `拦截二级-${tag}`);
    l2Id = l2.id;
  });

  test.afterAll(async () => {
    await ctx.dispose();
  });

  test('T06-01 队列非空时绕过前端下架 → 拒绝（20022）', async () => {
    const goods = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('拦截下架'), l2Id, { stock: 2 }),
    );
    const buyer = buyerFixture('q1');
    const token = await newBuyer(ctx, buyer);
    await submitIntention(ctx, token, goods.id);

    const result = await call(ctx, 'POST', `/api/seller/goods/${goods.id}/off-shelf`, {}, {
      token: sellerToken,
    });
    expect(result.body.code).toBe(20022);
  });

  test('T06-02 冻结期绕过前端提交意向 → 拒绝（20019）', async () => {
    const goods = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('拦截冻结'), l2Id, { stock: 2 }),
    );
    const first = await newBuyer(ctx, buyerFixture('q2'));
    await submitIntention(ctx, first, goods.id);

    // 卖家对队首进入交易 → 商品冻结
    const list = (await call<{ items: Array<{ id: string }> }>(
      ctx,
      'GET',
      `/api/seller/goods/${goods.id}/intentions`,
      undefined,
      { token: sellerToken },
    )).body.data.items;
    await call(ctx, 'POST', `/api/seller/intentions/${list[0].id}/trade`, {}, { token: sellerToken });

    // 另一个买家再提交 → 被拒
    const other = await newBuyer(ctx, buyerFixture('q3'));
    const result = await call(ctx, 'POST', '/api/buyer/intentions', { goods_id: goods.id }, {
      token: other,
    });
    expect(result.body.code).toBe(20019);
  });

  test('T06-03 对非队首进入交易 → 拒绝（30002）', async () => {
    const goods = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('拦截队首'), l2Id, { stock: 3 }),
    );
    const a = await newBuyer(ctx, buyerFixture('q4'));
    const b = await newBuyer(ctx, buyerFixture('q5'));
    await submitIntention(ctx, a, goods.id);
    await submitIntention(ctx, b, goods.id);

    const list = (await call<{ items: Array<{ id: string }> }>(
      ctx,
      'GET',
      `/api/seller/goods/${goods.id}/intentions`,
      undefined,
      { token: sellerToken },
    )).body.data.items;

    const result = await call(ctx, 'POST', `/api/seller/intentions/${list[1].id}/trade`, {}, {
      token: sellerToken,
    });
    expect(result.body.code).toBe(30002);
  });

  test('T06-04 改密后旧令牌立即失效（用完即恢复原口令）', async () => {
    const token = await sellerLogin(ctx);
    const temp = 'Temp@E2E12345';
    let restored = false;

    try {
      await changePassword(ctx, token, SELLER_PASSWORD, temp);

      // 旧令牌失效
      const old = await call(ctx, 'GET', '/api/seller/goods', undefined, { token });
      expect(old.status, '改密后旧令牌应 401').toBe(401);

      // 旧口令登录失败
      let oldPasswordFailed = false;
      try {
        await sellerLogin(ctx);
      } catch {
        oldPasswordFailed = true;
      }
      expect(oldPasswordFailed, '旧口令应无法登录').toBeTruthy();

      // 新口令可以登录
      const fresh = await buyerLoginSafe(ctx, temp);
      expect(fresh).toBeTruthy();
    } finally {
      // 无论断言是否失败，都把口令改回去
      try {
        const tempToken = await sellerLoginWith(ctx, temp);
        await changePassword(ctx, tempToken, temp, SELLER_PASSWORD);
        restored = true;
      } catch {
        /* 恢复失败时下面的断言会暴露 */
      }
    }

    expect(restored, '测试结束必须把卖家口令恢复为原值').toBeTruthy();
  });
});

/** 用给定口令登录卖家，返回 token（不改 helpers/config 的默认值） */
async function sellerLoginWith(ctx: APIRequestContext, password: string): Promise<string> {
  const data = await call<{ token: string }>(ctx, 'POST', '/api/seller/auth/session', {
    account: 'seller',
    password,
  });
  if (data.body.code !== 0) throw new Error(`卖家登录失败: ${data.body.message}`);
  return data.body.data.token;
}

/** 语义化别名：验证"新口令能登录" */
async function buyerLoginSafe(ctx: APIRequestContext, password: string): Promise<string> {
  return sellerLoginWith(ctx, password);
}
