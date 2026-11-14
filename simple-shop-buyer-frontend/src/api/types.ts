/**
 * 后端接口数据契约（与 Java DTO 的 JSON 字段逐字对应）。
 * 字段保持后端返回的 snake_case，不做驼峰转换。
 */

/** 统一响应结构：成功 code = 0；业务拒绝为 HTTP 200 + 非零业务码。 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/** 买家端会用到的业务码（与后端 ErrorCode 一致）。 */
export const ApiCode = {
  OK: 0,
  LOGIN_FAILED: 10001,
  SESSION_INVALID: 10002,
  PASSWORD_INVALID: 10004,
  USERNAME_TAKEN: 10005,
  USERNAME_INVALID: 10006,
  PHONE_INVALID: 10007,
  DEFAULT_LOCATION_INVALID: 10008,
  /** 账号已不存在（令牌还在但账号被删）；后端回 HTTP 404。 */
  ACCOUNT_NOT_FOUND: 10009,
  /** 账号标识格式不合法。 */
  ACCOUNT_INVALID: 10010,
  /** 商品不存在或已下架；后端同时回 HTTP 404。 */
  GOODS_NOT_FOUND: 20011,
  /** 下单数量超过库存。 */
  STOCK_INVALID: 20015,
  /** 目标商品当前不在售（加购、下单时可能遇到）。 */
  GOODS_NOT_ON_SALE: 20019,
  ORDER_NOT_FOUND: 60001,
  ORDER_STATUS_INVALID: 60002,
  ORDER_NOT_CANCELABLE: 60003,
  ORDER_STOCK_SHORTAGE: 60004,
  /** 加购的商品已下架。 */
  CART_GOODS_NOT_SELECTABLE: 70001,
  /** 收藏的商品已下架。 */
  FAVORITE_GOODS_NOT_SELECTABLE: 70002,
  /** 转收藏的批次里含已下架商品，整批拒绝。 */
  CART_TO_FAVORITE_REJECTED: 70003,
  TOO_MANY_REQUESTS: 50001,
  PARAM_INVALID: 50002,
  INTERNAL_ERROR: 50000,
} as const

/** 分页响应。page 从 1 起，page_size 默认 10。 */
export interface PageData<T> {
  items: T[]
  page: number
  page_size: number
  total: number
}

/** 类别引用（商品所属的一级/二级类别）。 */
export interface CategoryRef {
  l1_id: string
  l1_name: string
  l2_id: string
  l2_name: string
}

/** 类别树节点：一级带 children（二级）。 */
export interface CategoryNode {
  id: string
  name: string
  children: CategoryNode[]
}

/** 商品图片。 */
export interface GoodsImage {
  url: string
  is_main: boolean
  sort_no: number
}

/**
 * 描述块类型。
 * 注意：后端返回与接收的都是**大写下划线枚举**（TEXT / IMAGE / VIDEO），
 * Swagger 里没有标出可选值，联调时按实际返回为准。
 */
export type DescriptionBlockType = 'TEXT' | 'IMAGE' | 'VIDEO'

/** 描述块：type 为 TEXT 时读 text，为 IMAGE/VIDEO 时读 url。 */
export interface DescriptionBlock {
  type: DescriptionBlockType
  text: string | null
  url: string | null
}

/** 列表项。 */
export interface GoodsSummary {
  id: string
  name: string
  price: number
  stock: number
  status: string
  main_image_url: string
  category: CategoryRef
  create_at: string
  update_at: string
}

/**
 * 商品详情。
 * purchasable 为 true 表示「在售且有库存」，由后端给出，前端不自行推断。
 */
export interface GoodsDetail {
  id: string
  name: string
  price: number
  stock: number
  status: string
  category: CategoryRef
  main_image_url: string
  images: GoodsImage[]
  description: DescriptionBlock[]
  create_at: string
  update_at: string
  purchasable: boolean
}

export interface RegisterRequest {
  username: string
  password: string
  phone: string
  default_location?: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginData {
  token: string
  expire_at: string
}

/** 商品状态：阶段三买家只能看到在售，因此只有两态。 */
export type GoodsStatus = 'ON_SALE' | 'OFF_SHELF'

/** 下架原因：手动下架 / 库存归零 / 售罄归档。买家端只用来生成提示文案。 */
export type OffShelfReason = 'MANUAL' | 'STOCK_ZERO' | 'SOLD_OUT'

/**
 * 购物车行。**没有数量字段**——数量与勾选只存在前端（见 utils/cartStorage.ts）。
 *
 * `selectable` 的口径是「在售」，**在售但库存为 0 时仍为 true**；
 * 别和商品详情的 `purchasable`（在售且有库存）混用。
 */
export interface CartItemData {
  goods_id: string
  name: string
  price: number
  stock: number
  status: GoodsStatus
  off_shelf_reason: OffShelfReason | null
  main_image_url: string
  selectable: boolean
  added_at: string
}

/** 购物车列表：不分页，一次返回全部行（含已下架行）。 */
export interface CartListData {
  items: CartItemData[]
}

/** 转收藏结果：只回成功转了几件。 */
export interface MoveToFavoritesData {
  moved_count: number
}

/**
 * 收藏行。商品信息是**实时读取**的，商品改价改名这里会跟着变。
 *
 * `detail_available` 的口径是「商品在售」，与购物车的 `selectable` 相同；
 * 为 false 时不给跳详情（后端详情会返回 404 + 20011）。
 */
export interface FavoriteItemData {
  goods_id: string
  name: string
  price: number
  stock: number
  status: GoodsStatus
  off_shelf_reason: OffShelfReason | null
  main_image_url: string
  detail_available: boolean
  favorited_at: string
}

/** 某件商品当前是否已被收藏，详情页据此显示「收藏 / 已收藏」。 */
export interface FavoriteStatusData {
  favorited: boolean
}

/** 订单状态，与后端 `OrderStatus` 六个值逐字一致。 */
export type OrderStatus =
  | 'PENDING_CONFIRM'
  | 'CONFIRMED'
  | 'PREPARED'
  | 'SHIPPED'
  | 'COMPLETED'
  | 'CANCELED'

/** 下单入参：只带明细与数量，**不带金额**（总额由服务端按商品价快照算）。 */
export interface PlaceOrderItem {
  goods_id: string
  quantity: number
}

/** 下单成功回执。 */
export interface OrderPlacedData {
  order_id: string
  order_no: string
  total_amount: number
  status: OrderStatus
  create_at: string
}

/** 订单所属买家。买家端自己看不显示，字段仍按后端保留。 */
export interface OrderBuyerRef {
  id: string
  username: string
}

/**
 * 订单头。**不含明细**——明细要按订单 id 单独查一次（见 `OrderItemsData`），
 * 所以类型上也刻意分成两种结构，不合成一个。
 */
export interface OrderData {
  order_id: string
  order_no: string
  status: OrderStatus
  total_amount: number
  item_count: number
  create_at: string
  update_at: string
  buyer: OrderBuyerRef
}

/** 订单明细行。`amount` 是后端按单价×数量算好的，前端不重算。 */
export interface OrderItemData {
  goods_id: string
  goods_name: string
  unit_price: number
  quantity: number
  amount: number
}

/** 明细列表：不分页，一次给全。 */
export interface OrderItemsData {
  items: OrderItemData[]
}

/** 取消订单后的状态回执。 */
export interface OrderStatusData {
  order_id: string
  order_no: string
  status: OrderStatus
  update_at: string
}
