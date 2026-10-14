/**
 * 服务端独立拦截 —— 绕过前端的直接调用（验收方式 V-02）
 *
 * 规格书 §13.2.2 明确要求：「接口调用」验收**必须包含绕过前端的负向调用**，
 * 用来证明服务端自己会拦，而不是只靠前端按钮禁用（第 10 章 S10-07 / 第 6 章 G6-02）。
 *
 * 覆盖的验收标准：
 *   AC-17（P0）冻结期不可提交意向            → D1
 *   AC-20（P0）交易冻结禁止手动解冻           → D2
 *   AC-21（P0）手动下架要求队列为空           → D3
 *   AC-22（P1）进入交易只能选队首             → D7
 *   AC-23（P0）已有在售商品时拒绝发布第二条    → D4
 *   会话鉴权（10002）                        → D5
 *
 * ⚠️ 本组全部走 HTTP 接口，不点界面 —— 这正是它的意义所在。
 */
import { test, expect, type APIRequestContext } from '@playwright/test';
import {
  newApi,
  ensureOnSaleProduct,
  apiPost,
  apiSubmitIntention,
  apiPostForm,
} from '../helpers/api';

test.describe.serial('服务端独立拦截 · 绕过前端的直接调用（V-02）', () => {
  let ctx: APIRequestContext;
  let token = '';
  let intentIds: string[] = [];

  const auth = () => ({ Authorization: `Bearer ${token}` });

  async function countIntentions(): Promise<number> {
    const res = await ctx.get('/api/seller/product/intentions?page=1&page_size=10', {
      headers: auth(),
    });
    const body = await res.json();
    return body?.data?.total ?? 0;
  }

  test.beforeAll(async () => {
    ctx = await newApi();
    const { product, token: t } = await ensureOnSaleProduct(ctx);
    token = t;
    expect(product, '前置失败：需要一件在售商品').toBeTruthy();
    expect(product.status).toBe('on_sale');
  });

  test.afterAll(async () => {
    await ctx.dispose();
  });

  test('D5 未登录（无令牌）直接调用卖家接口 → 401 拒绝（10002）', async () => {
    const r = await apiPost(ctx, '/api/seller/product', null, { name: 'x', price: '1.00' });
    expect(r.status, '未带令牌应返回 401').toBe(401);
    expect(r.body?.code).toBe(10002);
  });

  test('D4 已有在售商品时绕过前端直接发布第二条 → 拒绝（AC-23、BR-11、INV-01）', async () => {
    const r = await apiPost(ctx, '/api/seller/product', token, {
      name: '企图发布的第二条商品',
      price: '9.90',
    });
    expect(r.body?.code, '已有在售/冻结商品时必须拒绝（20001）').toBe(20001);
  });

  test('D0 前置：造 2 条排队意向（供后面的拦截用例使用）', async () => {
    await apiSubmitIntention('拦截测试甲', '13700001111');
    await apiSubmitIntention('拦截测试乙', '13700002222');

    const res = await ctx.get('/api/seller/product/intentions?page=1&page_size=10', {
      headers: auth(),
    });
    const body = await res.json();
    intentIds = (body?.data?.items ?? []).map((i: any) => i.id);
    expect(intentIds.length, '应能看到刚提交的 2 条意向').toBeGreaterThanOrEqual(2);
  });

  test('D3 队列非空时绕过前端直接下架 → 拒绝（AC-21③、BR-09、PS-08）', async () => {
    expect(await countIntentions(), '前置：队列应非空').toBeGreaterThan(0);

    const r = await apiPost(ctx, '/api/seller/product/offline', token);
    expect(r.body?.code, '队列非空时必须拒绝下架（20006）').toBe(20006);
  });

  test('D7 对非队首意向直接调用「进入交易」→ 拒绝（AC-22、BR-12）', async () => {
    expect(intentIds.length).toBeGreaterThanOrEqual(2);
    const notFirst = intentIds[1];

    const r = await apiPost(ctx, '/api/seller/intention/trade', token, {
      intention_id: notFirst,
    });
    expect(r.body?.code, '非队首必须拒绝（30002）').toBe(30002);
  });

  test('D1 冻结期绕过前端直接提交意向 → 被拒且不新增记录（AC-17、INV-03、B11-01）', async () => {
    // 先把手动冻结打上
    const f = await apiPost(ctx, '/api/seller/product/freeze', token);
    expect(f.body?.code, '手动冻结应成功').toBe(0);

    const before = await countIntentions();

    // ⚠️ 绕开页面，直接 POST 买家端端点
    const html = await apiPostForm('/intent', { name: '冻结期偷偷提交', tel: '13600001111' });

    expect(html, '被拒时不应渲染出口令码').not.toContain('id="tokenCode"');
    expect(html, '应展示 M10-01 提示').toContain('商品交易中，暂停接收新意向');

    const after = await countIntentions();
    expect(after, '冻结期提交必须【不新增】意向记录').toBe(before);
  });

  test('D2 交易冻结时绕过前端直接解冻 → 拒绝（AC-20②、PS-06、FIX-01）', async () => {
    // 手动冻结可以先手动解冻
    const u = await apiPost(ctx, '/api/seller/product/unfreeze', token);
    expect(u.body?.code, '手动冻结的商品应可解冻').toBe(0);

    // 对队首发起交易 ⇒ 商品转为「交易冻结」
    const t = await apiPost(ctx, '/api/seller/intention/trade', token, {
      intention_id: intentIds[0],
    });
    expect(t.body?.code, '对队首进入交易应成功').toBe(0);

    // ⭐ 关键：交易冻结的商品，直接调接口解冻也必须被拒
    const r = await apiPost(ctx, '/api/seller/product/unfreeze', token);
    expect(r.body?.code, '交易冻结必须拒绝解冻（20005）').toBe(20005);
  });
});
