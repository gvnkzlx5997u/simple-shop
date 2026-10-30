/**
 * 阶段二接口封装。
 *
 * 两个用途（与阶段一思路一致，接口换成阶段二的）：
 *   ① 前置造数：建类别、发商品、造意向——比点页面快且稳；
 *   ② 负向验证：绕过前端直接调用，验证服务端拦截（交接文档 §2.3 INT-12/13）。
 *
 * 约定：响应外壳 {code, message, data}；业务错误多数是 HTTP 200 + 非 0 code，
 * 参数错误是 HTTP 400 + 50002 + errors[]。这里统一抛 ApiError，调用方按需断言。
 */
import { request, type APIRequestContext } from '@playwright/test';

import { API_URL, SELLER_ACCOUNT, SELLER_PASSWORD } from './config';

export interface Envelope<T> {
  code: number;
  message: string;
  data: T;
  errors?: Array<{ field: string; message: string }>;
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: number,
    message: string,
    readonly errors: Array<{ field: string; message: string }> = [],
  ) {
    super(`HTTP ${status} / code ${code}: ${message}`);
    this.name = 'ApiError';
  }
}

export interface CallOptions {
  token?: string;
  /** multipart 上传时传 FormData，此时不要带 json */
  form?: FormData;
}

/** 发一次请求并返回 { status, body }；不抛业务错误，交给调用方断言。 */
export async function call<T = unknown>(
  ctx: APIRequestContext,
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  path: string,
  json?: unknown,
  options: CallOptions = {},
): Promise<{ status: number; body: Envelope<T> }> {
  const headers: Record<string, string> = {};
  if (options.token) headers.Authorization = `Bearer ${options.token}`;

  const res = await ctx.fetch(path, {
    method,
    headers,
    data: options.form ?? (json === undefined ? undefined : JSON.stringify(json)),
    ...(options.form
      ? {}
      : { headers: { ...headers, 'Content-Type': 'application/json' } }),
  });

  const text = await res.text();
  let body: Envelope<T>;
  try {
    body = JSON.parse(text) as Envelope<T>;
  } catch {
    throw new Error(`${method} ${path} 返回非 JSON：HTTP ${res.status()} ${text.slice(0, 200)}`);
  }
  return { status: res.status(), body };
}

/** 发请求并要求 code === 0，否则抛 ApiError。 */
export async function ok<T = unknown>(
  ctx: APIRequestContext,
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  path: string,
  json?: unknown,
  options: CallOptions = {},
): Promise<T> {
  const { status, body } = await call<T>(ctx, method, path, json, options);
  if (body.code !== 0) throw new ApiError(status, body.code, body.message, body.errors);
  return body.data;
}

export async function newApi(): Promise<APIRequestContext> {
  return request.newContext({ baseURL: API_URL, timeout: 20_000 });
}

// ---------- 账号 ----------

export async function sellerLogin(ctx: APIRequestContext): Promise<string> {
  const data = await ok<{ token: string }>(ctx, 'POST', '/api/seller/auth/session', {
    account: SELLER_ACCOUNT,
    password: SELLER_PASSWORD,
  });
  return data.token;
}

export interface BuyerInput {
  username: string;
  password: string;
  phone: string;
  default_location?: string;
}

export async function buyerRegister(ctx: APIRequestContext, input: BuyerInput) {
  return ok<{ id: string; username: string }>(ctx, 'POST', '/api/buyer/auth/register', input);
}

export async function buyerLogin(ctx: APIRequestContext, username: string, password: string) {
  const data = await ok<{ token: string }>(ctx, 'POST', '/api/buyer/auth/session', {
    username,
    password,
  });
  return data.token;
}

/** 注册并登录，返回 token（E2E 造买家账号的常用组合）。 */
export async function newBuyer(ctx: APIRequestContext, input: BuyerInput): Promise<string> {
  await buyerRegister(ctx, input);
  return buyerLogin(ctx, input.username, input.password);
}

// ---------- 类别 ----------

export async function createL1(ctx: APIRequestContext, token: string, name: string) {
  return ok<{ id: string; name: string }>(ctx, 'POST', '/api/seller/categories/l1', { name }, { token });
}

export async function createL2(ctx: APIRequestContext, token: string, l1Id: string, name: string) {
  return ok<{ id: string; name: string }>(
    ctx,
    'POST',
    '/api/seller/categories/l2',
    { l1_id: l1Id, name },
    { token },
  );
}

export async function categoryTree(ctx: APIRequestContext) {
  return ok<Array<{ id: string; name: string; children: Array<{ id: string; name: string }> }>>(
    ctx,
    'GET',
    '/api/public/categories',
  );
}

// ---------- 商品 ----------

export interface PublishInput {
  name: string;
  price: string | number;
  stock: number;
  category_id: string;
  description?: Array<{ type: 'TEXT' | 'IMAGE' | 'VIDEO'; text?: string; url?: string }>;
  main_image?: string;
  gallery_images?: string[];
}

export async function publishGoods(ctx: APIRequestContext, token: string, input: PublishInput) {
  return ok<{ id: string; name: string; status: string; stock: number }>(
    ctx,
    'POST',
    '/api/seller/goods',
    input,
    { token },
  );
}

export async function getSellerGoods(ctx: APIRequestContext, token: string, id: string) {
  return ok<{ id: string; name: string; status: string; stock: number }>(
    ctx,
    'GET',
    `/api/seller/goods/${id}`,
    undefined,
    { token },
  );
}

export async function updateStock(ctx: APIRequestContext, token: string, id: string, stock: number) {
  return ok(ctx, 'PUT', `/api/seller/goods/${id}/stock`, { stock }, { token });
}

export async function offShelf(ctx: APIRequestContext, token: string, id: string) {
  return ok(ctx, 'POST', `/api/seller/goods/${id}/off-shelf`, {}, { token });
}

export async function reList(ctx: APIRequestContext, token: string, id: string, confirm = true) {
  return ok(ctx, 'POST', `/api/seller/goods/${id}/re-list`, { confirm }, { token });
}

/** 买家侧公共列表（匿名可访问） */
export async function publicGoods(
  ctx: APIRequestContext,
  query: Record<string, string | number> = {},
) {
  const search = new URLSearchParams(
    Object.entries(query).map(([k, v]) => [k, String(v)]),
  ).toString();
  return ok<{
    items: Array<{ id: string; name: string; price: string; stock: number }>;
    page: number;
    page_size: number;
    total: number;
  }>(ctx, 'GET', `/api/public/goods${search ? `?${search}` : ''}`);
}

// ---------- 意向与交易 ----------

export async function submitIntention(ctx: APIRequestContext, buyerToken: string, goodsId: string) {
  return ok(ctx, 'POST', '/api/buyer/intentions', { goods_id: goodsId }, { token: buyerToken });
}

export interface IntentionEntry {
  id: string;
  buyer_name?: string;
  status: 'QUEUED' | 'TRADING' | 'SUCCEEDED' | 'FAILED';
  queue_order: number;
  create_at: string;
}

export async function listIntentions(
  ctx: APIRequestContext,
  sellerToken: string,
  goodsId: string,
) {
  return ok<{ items: IntentionEntry[] }>(
    ctx,
    'GET',
    `/api/seller/goods/${goodsId}/intentions`,
    undefined,
    { token: sellerToken },
  );
}

export async function enterTrade(ctx: APIRequestContext, sellerToken: string, intentionId: string) {
  return ok(ctx, 'POST', `/api/seller/intentions/${intentionId}/trade`, {}, { token: sellerToken });
}

export async function markSuccess(ctx: APIRequestContext, sellerToken: string, intentionId: string) {
  return ok<{ goods_status: 'ON_SALE' | 'ARCHIVED' }>(
    ctx,
    'POST',
    `/api/seller/intentions/${intentionId}/success`,
    {},
    { token: sellerToken },
  );
}

export async function markFailure(
  ctx: APIRequestContext,
  sellerToken: string,
  intentionId: string,
  disposal: 'VOIDED' | 'REQUEUED',
) {
  return ok<{ goods_status: 'ON_SALE' | 'ARCHIVED' }>(
    ctx,
    'POST',
    `/api/seller/intentions/${intentionId}/failure`,
    { disposal },
    { token: sellerToken },
  );
}

// ---------- 历史与客户 ----------

export async function buyerHistory(ctx: APIRequestContext, buyerToken: string) {
  return ok<{ items: Array<Record<string, unknown>>; total: number }>(
    ctx,
    'GET',
    '/api/buyer/history?page=1&page_size=50',
    undefined,
    { token: buyerToken },
  );
}

export async function sellerHistory(ctx: APIRequestContext, token: string) {
  return ok<{ items: Array<Record<string, unknown>>; total: number }>(
    ctx,
    'GET',
    '/api/seller/history/goods?page=1&page_size=50',
    undefined,
    { token },
  );
}

export async function sellerHistoryDetail(ctx: APIRequestContext, token: string, goodsId: string) {
  return ok<Record<string, unknown>>(ctx, 'GET', `/api/seller/history/goods/${goodsId}`, undefined, {
    token,
  });
}

export async function customers(ctx: APIRequestContext, token: string) {
  return ok<{ items: Array<Record<string, unknown>>; total: number }>(
    ctx,
    'GET',
    '/api/seller/customers?page=1&page_size=50',
    undefined,
    { token },
  );
}

export async function customerDetail(ctx: APIRequestContext, token: string, id: string) {
  return ok<Record<string, unknown>>(ctx, 'GET', `/api/seller/customers/${id}`, undefined, { token });
}

// ---------- 改密 ----------

export async function changePassword(
  ctx: APIRequestContext,
  token: string,
  oldPassword: string,
  newPassword: string,
) {
  return ok(ctx, 'PUT', '/api/seller/auth/password', {
    old_password: oldPassword,
    new_password: newPassword,
  }, { token });
}
