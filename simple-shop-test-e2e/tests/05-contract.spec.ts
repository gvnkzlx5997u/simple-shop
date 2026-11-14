import { expect, test, type APIRequestContext } from '@playwright/test';

import { call, createL1, createL2, newApi, publishGoods, sellerLogin } from '../helpers/api';
import { goodsFixture, uniqueName } from '../helpers/data';

/**
 * 05 契约与参数校验
 *
 * 统一口径：
 *  - 参数错误 → HTTP 400 + 50002 + errors[{field,message}]
 *  - 语义错误 → HTTP 200 + 领域码（如价格精度 20014、库存 20015、状态不允许 60002）
 *  - 媒体上限：单文件 20MB、请求 25MB；商品图只收 JPG/PNG 且 ≤5MB
 *
 * 与阶段二相比只改了一条：T05-08 的「非法处置枚举」随意向接口下线，
 * 换成阶段三仍然存在的两类参数校验（订单状态非法、下架原因缺 status）。
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

  test('T05-08 非法查询参数 → 400 + 50002', async () => {
    // 订单状态只认六个枚举值，乱传按参数错误拒绝（阶段二的 disposal 枚举已随意向接口下线）
    const badStatus = await call(ctx, 'GET', '/api/seller/orders?status=NOT_A_STATUS', undefined, {
      token: sellerToken,
    });
    expect(badStatus.status).toBe(400);
    expect(badStatus.body.code).toBe(50002);

    // 下架原因只能与「已下架」组合：单独传原因是自相矛盾的查询，直接拒绝而不是返回空列表
    const badReason = await call(ctx, 'GET', '/api/seller/goods?off_shelf_reason=MANUAL', undefined, {
      token: sellerToken,
    });
    expect(badReason.status).toBe(400);
    expect(badReason.body.code).toBe(50002);
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

  test('T05-11 媒体超限：>5MB 商品图被拒（20016）', async () => {
    // 造一个 6MB 的伪 PNG（只测大小校验，不关心内容是否真能解析）
    const size = 6 * 1024 * 1024;
    const buffer = Buffer.alloc(size, 0);
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]).copy(buffer, 0);

    const res = await ctx.post('/api/seller/media', {
      headers: { Authorization: `Bearer ${sellerToken}` },
      multipart: {
        purpose: 'GOODS_IMAGE',
        file: { name: 'too-large.png', mimeType: 'image/png', buffer },
      },
    });
    const body = (await res.json()) as { code: number; message: string };
    expect(body.code, `期望 20016，实际 ${body.code} ${body.message}`).toBe(20016);
  });

  /** 合成一个"看起来是 MP4"的缓冲：偏移 4 起 ftyp，偏移 8 起 isom 品牌 */
  function fakeMp4(bytes: number): Buffer {
    const buffer = Buffer.alloc(bytes, 0);
    buffer.write('ftyp', 4, 'ascii');
    buffer.write('isom', 8, 'ascii');
    return buffer;
  }

  async function uploadMedia(buffer: Buffer, name: string, mimeType: string, purpose: string) {
    const res = await ctx.post('/api/seller/media', {
      headers: { Authorization: `Bearer ${sellerToken}` },
      multipart: { purpose, file: { name, mimeType, buffer } },
    });
    const body = (await res.json()) as { code: number; message: string; data?: { url?: string } };
    return { status: res.status(), body };
  }

  test('T05-12 视频正好 20MB → 允许上传（边界）', async () => {
    const limit = 20 * 1024 * 1024;
    const result = await uploadMedia(fakeMp4(limit), 'edge.mp4', 'video/mp4', 'DESCRIPTION_MEDIA');
    expect(result.body.code, `期望成功，实际 ${result.body.code} ${result.body.message}`).toBe(0);
    expect(result.body.data?.url ?? '').toMatch(/\.mp4$/);
  });

  test('T05-13 视频超过 20MB → 拒绝（实际是 20026，不是 20025）', async () => {
    const result = await uploadMedia(
      fakeMp4(21 * 1024 * 1024),
      'too-big.mp4',
      'video/mp4',
      'DESCRIPTION_MEDIA',
    );
    // 口径差异：ErrorCode.VIDEO_INVALID(20025) 实际不可达 ——
    // 单文件上限（max-file-size=20MB）与业务上限同为 20MB，超限在容器层就被拦成 MEDIA_INVALID(20026)。
    expect(result.body.code).toBe(20026);
  });

  test('T05-14 视频用于商品图用途 → 类型不允许（20027）', async () => {
    const result = await uploadMedia(
      fakeMp4(512 * 1024),
      'not-image.mp4',
      'video/mp4',
      'GOODS_IMAGE',
    );
    expect(result.body.code).toBe(20027);
  });

  test('T05-15 文本文件当商品图 → 内容非法（20016）', async () => {
    const result = await uploadMedia(
      Buffer.from('this is definitely not an image', 'utf-8'),
      'fake.png',
      'image/png',
      'GOODS_IMAGE',
    );
    expect(result.body.code).toBe(20016);
  });
});
