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

/** 订单七态：下单即待支付，付款后卖家才能确认，交易完成由买家确认收货推进。 */
export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'CONFIRMED'
  | 'PREPARED'
  | 'SHIPPED'
  | 'COMPLETED'
  | 'CANCELED';

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
  /** 下单时的收货快照；地址簿之后改名或删除都不影响这里 */
  receiver_name: string | null;
  receiver_phone: string | null;
  receiver_address: string | null;
  /** 未支付时两者都为 null */
  payment_channel: 'BANK_CARD' | 'CREDIT_CARD' | 'ALIPAY' | 'WECHAT' | null;
  paid_at: string | null;
  /** 仅待支付单有值 */
  pay_deadline: string | null;
  /** 发货后才有；tracking_no 可能含换行（多包裹） */
  carrier: string | null;
  tracking_no: string | null;
  logistics_remark: string | null;
  /** 取消退款 + 售后退款（只算「已完成 + 退款类结果」） */
  refunded_amount: number;
  /** 该订单售后条数（含待处理与换货） */
  aftersale_count: number;
}

export interface OrderItemRow {
  /** 售后申请用它定位，不要用 goods_id */
  order_item_id: string;
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

// ---------- 收货地址 ----------

export interface AddressRow {
  id: string;
  receiver_name: string;
  receiver_phone: string;
  detail_address: string;
  is_default: boolean;
  create_at: string;
  update_at: string;
}

export interface AddressInput {
  receiver_name: string;
  receiver_phone: string;
  detail_address: string;
}

export async function createAddress(ctx: APIRequestContext, buyerToken: string, input: AddressInput) {
  return ok<AddressRow>(ctx, 'POST', '/api/buyer/addresses', input, { token: buyerToken });
}

export async function listAddresses(ctx: APIRequestContext, buyerToken: string) {
  return ok<{ items: AddressRow[] }>(ctx, 'GET', '/api/buyer/addresses', undefined, {
    token: buyerToken,
  });
}

export async function updateAddress(
  ctx: APIRequestContext,
  buyerToken: string,
  id: string,
  input: AddressInput,
) {
  return ok<AddressRow>(ctx, 'PUT', `/api/buyer/addresses/${id}`, input, { token: buyerToken });
}

export async function deleteAddress(ctx: APIRequestContext, buyerToken: string, id: string) {
  return ok(ctx, 'DELETE', `/api/buyer/addresses/${id}`, undefined, { token: buyerToken });
}

export async function setDefaultAddress(ctx: APIRequestContext, buyerToken: string, id: string) {
  return ok<AddressRow>(ctx, 'POST', `/api/buyer/addresses/${id}/default`, {}, {
    token: buyerToken,
  });
}

/**
 * 下单必须带地址，但保留用例的下单点不应关心它：
 * 第一次调用时为该买家建一条默认地址并缓存，之后复用。
 */
const defaultAddressCache = new Map<string, string>();

/** 取该买家的默认地址 id；没有就建一条（首条自动默认）。 */
export async function ensureAddress(ctx: APIRequestContext, buyerToken: string): Promise<string> {
  const cached = defaultAddressCache.get(buyerToken);
  if (cached) return cached;
  const book = await listAddresses(ctx, buyerToken);
  const existing = book.items.find((item) => item.is_default) ?? book.items[0];
  const id =
    existing?.id ??
    (
      await createAddress(ctx, buyerToken, {
        receiver_name: 'E2E 收货人',
        receiver_phone: '13900000000',
        detail_address: 'E2E 省 E2E 市 E2E 路 1 号',
      })
    ).id;
  defaultAddressCache.set(buyerToken, id);
  return id;
}

/** 下单（不经购物车的直下，造数用）；已下架 20019、超库存 20015、缺地址 80004。 */
export async function placeOrder(
  ctx: APIRequestContext,
  buyerToken: string,
  items: PlaceOrderItem[],
  addressId?: string,
) {
  const address = addressId ?? (await ensureAddress(ctx, buyerToken));
  return ok<{ order_id: string; order_no: string; total_amount: number; status: OrderStatus }>(
    ctx,
    'POST',
    '/api/buyer/orders',
    { address_id: address, items },
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

export interface LogisticsInput {
  carrier: string;
  tracking_no: string;
  logistics_remark?: string;
}

/**
 * 卖家推进：确认 → 备货完成 → 发货，以及取消。
 * 发货必须带 `{carrier, tracking_no}`（缺一项 50002）；状态不符 / 跳级 60002。
 */
export async function advanceOrder(
  ctx: APIRequestContext,
  token: string,
  orderId: string,
  action: 'confirm' | 'prepare' | 'ship' | 'cancel',
  logistics?: LogisticsInput,
) {
  return ok<{ order_id: string; order_no: string; status: OrderStatus }>(
    ctx,
    'POST',
    `/api/seller/orders/${orderId}/${action}`,
    action === 'ship' ? logistics : {},
    { token },
  );
}

/** 发货后改物流，状态不变；未发货返回 82002。 */
export async function updateLogistics(
  ctx: APIRequestContext,
  token: string,
  orderId: string,
  input: LogisticsInput,
) {
  return ok<{ order_id: string; order_no: string; status: OrderStatus }>(
    ctx,
    'PUT',
    `/api/seller/orders/${orderId}/logistics`,
    input,
    { token },
  );
}

/** 买家支付（Mock 渠道恒成功）；已支付/已取消 83002，渠道不合法 83001。 */
export async function payOrder(
  ctx: APIRequestContext,
  buyerToken: string,
  orderId: string,
  channel: 'BANK_CARD' | 'CREDIT_CARD' | 'ALIPAY' | 'WECHAT',
) {
  return ok<{ order_id: string; order_no: string; status: OrderStatus; paid_at: string }>(
    ctx,
    'POST',
    `/api/buyer/orders/${orderId}/pay`,
    { payment_channel: channel },
    { token: buyerToken },
  );
}

/** 买家确认收货：成交并扣库存；库存不足整单 60004。 */
export async function confirmReceipt(ctx: APIRequestContext, buyerToken: string, orderId: string) {
  return ok<{ order_id: string; order_no: string; status: OrderStatus }>(
    ctx,
    'POST',
    `/api/buyer/orders/${orderId}/confirm-receipt`,
    {},
    { token: buyerToken },
  );
}

/** 造一张「已发货」订单：下单 → 支付 → 确认 → 备货完成 → 发货。 */
export async function shippedOrderViaApi(
  ctx: APIRequestContext,
  sellerToken: string,
  buyerToken: string,
  items: PlaceOrderItem[],
  logistics: LogisticsInput = { carrier: '顺丰速运', tracking_no: 'SF0000000001' },
) {
  const order = await placeOrder(ctx, buyerToken, items);
  await payOrder(ctx, buyerToken, order.order_id, 'ALIPAY');
  await advanceOrder(ctx, sellerToken, order.order_id, 'confirm');
  await advanceOrder(ctx, sellerToken, order.order_id, 'prepare');
  await advanceOrder(ctx, sellerToken, order.order_id, 'ship', logistics);
  return sellerOrderDetail(ctx, sellerToken, order.order_id);
}

/**
 * 造一张「交易完成」订单（售后用例的前置）。
 * 卖家没有「完成」动作，最后一步必须由买家确认收货推进。
 */
export async function completedOrderViaApi(
  ctx: APIRequestContext,
  sellerToken: string,
  buyerToken: string,
  items: PlaceOrderItem[],
  logistics: LogisticsInput = { carrier: '顺丰速运', tracking_no: 'SF0000000002' },
) {
  const order = await shippedOrderViaApi(ctx, sellerToken, buyerToken, items, logistics);
  await confirmReceipt(ctx, buyerToken, order.order_id);
  return sellerOrderDetail(ctx, sellerToken, order.order_id);
}

// ---------- 售后 ----------

export type AftersaleStatus = 'PENDING' | 'COMPLETED' | 'CANCELED';
export type AftersaleResult = 'RETURN_AND_REFUND' | 'REFUND_ONLY' | 'EXCHANGE';

export interface AftersaleRow {
  aftersale_id: string;
  aftersale_no: string;
  order_id: string;
  order_no: string;
  order_item_id: string;
  goods_name: string;
  title: string;
  status: AftersaleStatus;
  result: AftersaleResult | null;
  refund_amount: number | null;
  refunded_at: string | null;
  create_at: string;
}

export interface AftersaleDetailRow extends AftersaleRow {
  description: string;
  image_urls: string[];
  update_at: string;
}

export interface AftersaleApplyInput {
  order_item_id: string;
  title: string;
  description: string;
  image_urls?: string[];
}

/** 申请成功只回这四项：标题、描述等要看详情接口。 */
export interface AftersaleAppliedRow {
  aftersale_id: string;
  aftersale_no: string;
  status: AftersaleStatus;
  create_at: string;
}

export async function applyAftersale(
  ctx: APIRequestContext,
  buyerToken: string,
  input: AftersaleApplyInput,
) {
  return ok<AftersaleAppliedRow>(ctx, 'POST', '/api/buyer/aftersales', input, {
    token: buyerToken,
  });
}

export async function listBuyerAftersales(
  ctx: APIRequestContext,
  buyerToken: string,
  query: { status?: AftersaleStatus; order_id?: string; page?: number; page_size?: number } = {},
) {
  const search = toSearch(query);
  return ok<{ items: AftersaleRow[]; page: number; page_size: number; total: number }>(
    ctx,
    'GET',
    `/api/buyer/aftersales${search ? `?${search}` : ''}`,
    undefined,
    { token: buyerToken },
  );
}

export async function buyerAftersaleDetail(
  ctx: APIRequestContext,
  buyerToken: string,
  id: string,
) {
  return ok<AftersaleDetailRow>(ctx, 'GET', `/api/buyer/aftersales/${id}`, undefined, {
    token: buyerToken,
  });
}

/**
 * 上传售后凭证图，返回可直接引用的 `/media/aftersale/...` 地址。
 *
 * 用 Playwright 的 `multipart` 而不是手搓 FormData：`data: FormData` 不会自动带
 * multipart 边界，后端会按 `application/json` 收，直接 415。
 */
export async function uploadAftersaleImage(
  ctx: APIRequestContext,
  buyerToken: string,
  file: { name: string; mimeType: string; buffer: Buffer },
) {
  const res = await ctx.fetch('/api/buyer/media?purpose=AFTERSALE_IMAGE', {
    method: 'POST',
    headers: { Authorization: `Bearer ${buyerToken}` },
    multipart: {
      file: { name: file.name, mimeType: file.mimeType, buffer: file.buffer },
    },
  });
  const body = (await res.json()) as { code: number; message: string; data?: { url: string } };
  if (body.code !== 0 || !body.data) {
    throw new ApiError(res.status(), body.code, body.message);
  }
  return body.data.url;
}

export async function listSellerAftersales(
  ctx: APIRequestContext,
  token: string,
  query: { status?: AftersaleStatus; order_no?: string; page?: number; page_size?: number } = {},
) {
  const search = toSearch(query);
  return ok<{ items: AftersaleRow[]; page: number; page_size: number; total: number }>(
    ctx,
    'GET',
    `/api/seller/aftersales${search ? `?${search}` : ''}`,
    undefined,
    { token },
  );
}

export async function sellerAftersaleDetail(
  ctx: APIRequestContext,
  token: string,
  id: string,
) {
  return ok<AftersaleDetailRow>(ctx, 'GET', `/api/seller/aftersales/${id}`, undefined, { token });
}

/** 卖家登记结果并完成：仅退款 / 退货退款会写退款额，换货不写；重复退款 81004。 */
export async function completeSellerAftersale(
  ctx: APIRequestContext,
  token: string,
  id: string,
  result: AftersaleResult,
) {
  return ok<AftersaleRow>(ctx, 'POST', `/api/seller/aftersales/${id}/complete`, { result }, {
    token,
  });
}

/** 卖家取消 / 拒绝：与「拒绝」同义，已取消幂等，已完成 81002。 */
export async function cancelSellerAftersale(
  ctx: APIRequestContext,
  token: string,
  id: string,
) {
  return ok<AftersaleRow>(ctx, 'POST', `/api/seller/aftersales/${id}/cancel`, {}, { token });
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
