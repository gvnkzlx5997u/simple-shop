import { expect, test, type APIRequestContext } from '@playwright/test';

import {
  call,
  changePassword,
  createL1,
  createL2,
  newApi,
  newBuyer,
  offShelf,
  placeOrder,
  publishGoods,
  sellerLogin,
  updateStock,
} from '../helpers/api';
import { buyerFixture, goodsFixture, uniqueName } from '../helpers/data';
import { SELLER_PASSWORD } from '../helpers/config';

/**
 * 06 服务端拦截（绕过前端的负向调用）
 *
 * 阶段三口径：
 *  - 有未完成订单（待确认~已发货）时，手动下架与手动清零都被拒（20020），成交扣减到 0 不在此限；
 *  - 商品只有两态，下架后买家既不能加购（70001）也不能下单（20019）；
 *  - 改密会让该账号全部会话立即失效。
 *
 * 阶段二删除两条：T06-02「冻结期提交意向」与 T06-03「非队首 30002」——
 * 商品冻结、意向队列在阶段三都不存在了。
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

  test('T06-01 有未完成订单时绕过前端下架 / 清零 → 20020', async () => {
    const goods = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('拦截下架'), l2Id, { stock: 2 }),
    );
    const buyerToken = await newBuyer(ctx, buyerFixture('q1'));
    await placeOrder(ctx, buyerToken, [{ goods_id: goods.id, quantity: 1 }]);

    const off = await call(ctx, 'POST', `/api/seller/goods/${goods.id}/off-shelf`, {}, {
      token: sellerToken,
    });
    expect(off.body.code, '有未完成订单时手动下架应被拒').toBe(20020);

    const zero = await call(ctx, 'PUT', `/api/seller/goods/${goods.id}/stock`, { stock: 0 }, {
      token: sellerToken,
    });
    expect(zero.body.code, '有未完成订单时手动清零应被拒').toBe(20020);

    // 改成其他正数不受限
    const plus = await updateStock(ctx, sellerToken, goods.id, 5);
    expect(plus.stock).toBe(5);
    expect(plus.status).toBe('ON_SALE');
  });

  test('T06-02 已下架商品：加购 70001、下单 20019', async () => {
    const goods = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('拦截下架购买'), l2Id, { stock: 2 }),
    );
    await offShelf(ctx, sellerToken, goods.id);

    const buyerToken = await newBuyer(ctx, buyerFixture('q2'));

    const cart = await call(ctx, 'POST', '/api/buyer/cart', { goods_id: goods.id }, {
      token: buyerToken,
    });
    expect(cart.body.code, '已下架商品不能加购').toBe(70001);

    const order = await call(
      ctx,
      'POST',
      '/api/buyer/orders',
      { items: [{ goods_id: goods.id, quantity: 1 }] },
      { token: buyerToken },
    );
    expect(order.body.code, '已下架商品不能下单').toBe(20019);

    // 顺带确认加购接口对在售商品是通的，避免上面两条因为别的原因失败而"看起来正确"
    const saleGoods = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('拦截可加购'), l2Id, { stock: 2 }),
    );
    const added = await call(ctx, 'POST', '/api/buyer/cart', { goods_id: saleGoods.id }, {
      token: buyerToken,
    });
    expect(added.body.code, '在售商品应能加购（对照组）').toBe(0);
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
      const fresh = await sellerLoginWith(ctx, temp);
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
