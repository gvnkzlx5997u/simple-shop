/**
 * 接口封装（阶段三口径）。
 *
 * 两个用途：
 *   ① 前置造数：建类别、发商品、造买家、下单——比点页面快且稳；
 *   ② 负向验证：绕过前端直接调用，验证服务端拦截。
 *
 * 约定：响应外壳 {code, message, data}；业务拒绝多数是 HTTP 200 + 非 0 code，
 * 参数错误是 HTTP 400 + 50002 + errors[]。call() 不抛业务错误，交给调用方断言；
 * ok() 要求 code === 0，否则抛 ApiError。
 *
 * 阶段二下线的接口（意向、交易裁决、历史、重新上架）已整段移除，不要再加回来。
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

/** 把查询条件拼成 query string：跳过 undefined/null 与空串，其余按字符串传。 */
function toSearch(query: object): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && String(value) !== '') {
      params.set(key, String(value));
    }
  }
  return params.toString();
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

/** 商品两态 + 下架原因；断言只需要这几个字段，不必映射整个详情 DTO。 */
export interface GoodsView {
  id: string;
  name: string;
  status: 'ON_SALE' | 'OFF_SHELF';
  stock: number;
  off_shelf_reason: 'MANUAL' | 'STOCK_ZERO' | 'SOLD_OUT' | null;
  off_shelf_at: string | null;
}

export async function publishGoods(ctx: APIRequestContext, token: string, input: PublishInput) {
  return ok<GoodsView>(ctx, 'POST', '/api/seller/goods', input, { token });
}

export async function getSellerGoods(ctx: APIRequestContext, token: string, id: string) {
  return ok<GoodsView>(ctx, 'GET', `/api/seller/goods/${id}`, undefined, { token });
}

export async function updateStock(ctx: APIRequestContext, token: string, id: string, stock: number) {
  return ok<GoodsView>(ctx, 'PUT', `/api/seller/goods/${id}/stock`, { stock }, { token });
}

/** 手动下架（终态：没有重新上架接口）。有未完成订单时返回 20020。 */
export async function offShelf(ctx: APIRequestContext, token: string, id: string) {
  return ok<GoodsView>(ctx, 'POST', `/api/seller/goods/${id}/off-shelf`, {}, { token });
}

/** 改挂二级类别；所有状态都能改（包括手动下架的终态商品）。 */
export async function changeGoodsCategory(
  ctx: APIRequestContext,
  token: string,
  id: string,
  categoryId: string,
) {
  return ok<GoodsView>(ctx, 'PUT', `/api/seller/goods/${id}/category`, {
    category_id: categoryId,
  }, { token });
}

/** 买家侧公共列表（匿名可访问）。 */
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

// ---------- 购物车、收藏与订单 ----------

export type OrderStatus = 'PENDING_CONFIRM' | 'CONFIRMED' | 'PREPARED' | 'SHIPPED' | 'COMPLETED' | 'CANCELED';

export interface CartRow {
  goods_id: string;
  name: string;
  price: number;
  stock: number;
  status: 'ON_SALE' | 'OFF_SHELF';
  off_shelf_reason: string | null;
  main_image_url: string | null;
  /** 在售可勾选；已下架或库存不足不可勾选 */
  selectable: boolean;
  added_at: string;
}

export interface FavoriteRow {
  goods_id: string;
  name: string;
  price: number;
  stock: number;
  status: 'ON_SALE' | 'OFF_SHELF';
  main_image_url: string | null;
  /** 已下架的收藏仍保留，但不可进详情 */
  detail_available: boolean;
  favorited_at: string;
}

export interface OrderRow {
  order_id: string;
  order_no: string;
  status: OrderStatus;
  total_amount: number;
  /** 明细行数（同一商品买多件仍算一行） */
  item_count: number;
  create_at: string;
  update_at: string;
  buyer: { id: string; username: string };
}

export interface OrderItemRow {
  goods_id: string;
  goods_name: string;
  unit_price: number;
  quantity: number;
  amount: number;
}

/** 加购；非在售商品返回 70001。接口只回成功/失败，不回明细。 */
export async function addToCart(ctx: APIRequestContext, buyerToken: string, goodsId: string) {
  return ok(ctx, 'POST', '/api/buyer/cart', { goods_id: goodsId }, { token: buyerToken });
}

export async function listCart(ctx: APIRequestContext, buyerToken: string) {
  return ok<{ items: CartRow[] }>(ctx, 'GET', '/api/buyer/cart', undefined, { token: buyerToken });
}

export async function removeFromCart(ctx: APIRequestContext, buyerToken: string, goodsId: string) {
  return ok(ctx, 'DELETE', `/api/buyer/cart/${goodsId}`, undefined, { token: buyerToken });
}

export async function addFavorite(ctx: APIRequestContext, buyerToken: string, goodsId: string) {
  return ok(ctx, 'POST', '/api/buyer/favorites', { goods_id: goodsId }, { token: buyerToken });
}

export async function removeFavorite(ctx: APIRequestContext, buyerToken: string, goodsId: string) {
  return ok(ctx, 'DELETE', `/api/buyer/favorites/${goodsId}`, undefined, { token: buyerToken });
}

export async function listFavorites(ctx: APIRequestContext, buyerToken: string) {
  return ok<{ items: FavoriteRow[]; page: number; page_size: number; total: number }>(
    ctx,
    'GET',
    '/api/buyer/favorites',
    undefined,
    { token: buyerToken },
  );
}

export interface PlaceOrderItem {
  goods_id: string;
  quantity: number;
}

/** 下单（不经购物车的直下，造数用）；已下架 20019、超库存 20015、商品不存在 20011。 */
export async function placeOrder(
  ctx: APIRequestContext,
  buyerToken: string,
  items: PlaceOrderItem[],
) {
  return ok<{ order_id: string; order_no: string; total_amount: number; status: OrderStatus }>(
    ctx,
    'POST',
    '/api/buyer/orders',
    { items },
    { token: buyerToken },
  );
}

export async function listBuyerOrders(
  ctx: APIRequestContext,
  buyerToken: string,
  query: { status?: OrderStatus; page?: number; page_size?: number } = {},
) {
  const search = toSearch(query);
  return ok<{ items: OrderRow[]; page: number; page_size: number; total: number }>(
    ctx,
    'GET',
    `/api/buyer/orders${search ? `?${search}` : ''}`,
    undefined,
    { token: buyerToken },
  );
}

export async function buyerOrderDetail(ctx: APIRequestContext, buyerToken: string, orderId: string) {
  return ok<OrderRow>(ctx, 'GET', `/api/buyer/orders/${orderId}`, undefined, { token: buyerToken });
}

export async function buyerCancelOrder(ctx: APIRequestContext, buyerToken: string, orderId: string) {
  return ok<{ order_id: string; status: OrderStatus }>(
    ctx,
    'POST',
    `/api/buyer/orders/${orderId}/cancel`,
    {},
    { token: buyerToken },
  );
}

export interface SellerOrderQuery {
  status?: OrderStatus;
  buyer_id?: string;
  goods_id?: string;
  page?: number;
  page_size?: number;
}

export async function listSellerOrders(
  ctx: APIRequestContext,
  token: string,
  query: SellerOrderQuery = {},
) {
  const search = toSearch(query);
  return ok<{ items: OrderRow[]; page: number; page_size: number; total: number }>(
    ctx,
    'GET',
    `/api/seller/orders${search ? `?${search}` : ''}`,
    undefined,
    { token },
  );
}

export async function sellerOrderDetail(ctx: APIRequestContext, token: string, orderId: string) {
  return ok<OrderRow>(ctx, 'GET', `/api/seller/orders/${orderId}`, undefined, { token });
}

export async function sellerOrderItems(ctx: APIRequestContext, token: string, orderId: string) {
  return ok<{ items: OrderItemRow[] }>(
    ctx,
    'GET',
    `/api/seller/orders/${orderId}/items`,
    undefined,
    { token },
  );
}

/** 四个推进动作 + 卖家取消；都只回状态。跳级/终态推进返回 60002，完成缺货 60004。 */
export async function advanceOrder(
  ctx: APIRequestContext,
  token: string,
  orderId: string,
  action: 'confirm' | 'prepare' | 'ship' | 'complete' | 'cancel',
) {
  return ok<{ order_id: string; order_no: string; status: OrderStatus }>(
    ctx,
    'POST',
    `/api/seller/orders/${orderId}/${action}`,
    {},
    { token },
  );
}

// ---------- 客户 ----------

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
