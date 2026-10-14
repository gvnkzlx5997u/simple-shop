/**
 * 接口辅助 —— 用于「前置准备」与「绕过前端的负向调用」（验收方式 V-02）
 *
 * 说明：这里发的是真的 HTTP 请求，不是"点页面"。
 * 它有两个用途：
 *   ① 前置准备：确保测试库里有在售商品（否则买家端用例跑不动）
 *   ② 负向验证：规格书 §13.2.2 明确要求"必须包含绕过前端的直接调用"
 */
import { request, type APIRequestContext } from '@playwright/test';
import { API_URL, SELLER_ACCOUNT, SELLER_PASSWORD } from './config';

export async function newApi(): Promise<APIRequestContext> {
  return request.newContext({
    baseURL: API_URL,
    extraHTTPHeaders: { 'Content-Type': 'application/json' },
  });
}

/** I11-01 卖家登录 → token */
export async function sellerLogin(ctx: APIRequestContext): Promise<string> {
  const res = await ctx.post('/api/seller/session', {
    data: { account: SELLER_ACCOUNT, password: SELLER_PASSWORD },
  });
  const body = await res.json();
  if (body.code !== 0) throw new Error(`卖家登录失败: ${JSON.stringify(body)}`);
  return body.data.token as string;
}

/** I11-04 查询当前商品 → 商品对象或 null */
export async function currentProduct(ctx: APIRequestContext, token: string) {
  const res = await ctx.get('/api/seller/product', {
    headers: { Authorization: `Bearer ${token}` },
  });
  const body = await res.json();
  // 无商品时约定 code=0 且 data 为 null（也可能是 20004，两种都当"没有"处理）
  if (body.code !== 0) return null;
  return body.data ?? null;
}

/** I11-06 发布商品 */
export async function publishProduct(
  ctx: APIRequestContext,
  token: string,
  data: { name: string; description?: string; price: string },
) {
  const res = await ctx.post('/api/seller/product', {
    data,
    headers: { Authorization: `Bearer ${token}` },
  });
  return { status: res.status(), body: await res.json() };
}

/**
 * 确保测试库里有「在售」商品。
 * 测试库是空库 ⇒ 第一次跑会发布一件；已下架归档后再次跑也会重新发布。
 * 返回：{ product, published } —— published=true 表示本次是新发布的
 */
export async function ensureOnSaleProduct(ctx: APIRequestContext) {
  const token = await sellerLogin(ctx);
  const cur = await currentProduct(ctx, token);

  if (cur && cur.status === 'on_sale') {
    return { token, product: cur, published: false };
  }
  if (cur && cur.status === 'frozen') {
    // 冻结态不自动处理（那会改变被测状态），交由用例自行决定
    return { token, product: cur, published: false };
  }

  const stamp = new Date().toISOString().slice(0, 19).replace('T', ' ');
  const { body } = await publishProduct(ctx, token, {
    name: `E2E测试商品 ${stamp}`,
    description: '由自动化验收测试创建，用于验证买家端与卖家端流程',
    price: '199.00',
  });
  if (body.code !== 0) throw new Error(`发布商品失败: ${JSON.stringify(body)}`);

  const after = await currentProduct(ctx, token);
  return { token, product: after, published: true };
}

/** 通用：带 token 的请求（负向用例直接调用接口时用） */
export async function apiPost(
  ctx: APIRequestContext,
  path: string,
  token: string | null,
  data?: unknown,
) {
  const res = await ctx.post(path, {
    data: data ?? {},
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  let body: any = null;
  try {
    body = await res.json();
  } catch {
    /* 非 JSON 响应 */
  }
  return { status: res.status(), body };
}

/**
 * 买家端提交意向（页面端点 POST /intent，表单编码）→ 从返回 HTML 里取口令码。
 * 用途：卖家端剧本的「造数据」前置。注意 Content-Type 必须是表单编码，不能带 JSON 头。
 */
export async function apiSubmitIntention(name: string, tel: string): Promise<string> {
  const html = await apiPostForm('/intent', { name, tel });
  const m = html.match(/id="tokenCode"[^>]*>\s*([A-Z0-9]{12})\s*</);
  if (!m) {
    throw new Error(`提交意向失败：未能从成功页取到口令码。响应片段：\n${html.slice(0, 500)}`);
  }
  return m[1];
}

/** 以【表单编码】提交并取回响应文本（买家端端点是表单，不能用 JSON 头） */
export async function apiPostForm(path: string, form: Record<string, string>): Promise<string> {
  const ctx = await request.newContext({ baseURL: API_URL });
  try {
    const res = await ctx.post(path, { form });
    return await res.text();
  } finally {
    await ctx.dispose();
  }
}
