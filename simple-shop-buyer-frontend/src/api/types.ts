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
  /** 图片不是 JPG/PNG 或超过 5MB；买家上传售后凭证时会遇到。 */
  IMAGE_INVALID: 20016,
  /** 上传用途不被允许；买家只开放 AFTERSALE_IMAGE。 */
  MEDIA_TYPE_NOT_ALLOWED: 20027,
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
  /** 地址不存在；他人地址同样按不存在处理，后端回 HTTP 404。 */
  ADDRESS_NOT_FOUND: 80001,
  ADDRESS_LIMIT_EXCEEDED: 80002,
  ADDRESS_FIELD_INVALID: 80003,
  /** 下单未选地址，或所选地址已删除/不属于当前买家。 */
  ADDRESS_REQUIRED: 80004,
  /** 售后单不存在；后端回 HTTP 404。 */
  AFTERSALE_NOT_FOUND: 81001,
  AFTERSALE_STATUS_INVALID: 81002,
  /** 订单项不可申请售后：不存在、不属于本人、订单未交易完成。 */
  AFTERSALE_NOT_APPLICABLE: 81003,
  AFTERSALE_REFUND_DUPLICATED: 81004,
  AFTERSALE_RESULT_REQUIRED: 81005,
  AFTERSALE_CONTENT_INVALID: 81006,
  AFTERSALE_IMAGE_LIMIT_EXCEEDED: 81007,
  /** 只有已发货且未取消的订单能改物流（买家端不可达，预防性登记）。 */
  LOGISTICS_NOT_EDITABLE: 82002,
  PAYMENT_CHANNEL_INVALID: 83001,
  /** 订单不在待支付，或已超过支付时限。 */
  PAYMENT_NOT_ALLOWED: 83002,
  /** 渠道返回失败：订单仍停在待支付，可重试。 */
  PAYMENT_FAILED: 83003,
  REFUND_FAILED: 83004,
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

/** 注册入参。后端拒收未定义字段，多传一个就是 400，不是被忽略。 */
export interface RegisterRequest {
  username: string
  password: string
  phone: string
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

/** 订单状态，与后端 `OrderStatus` 七个值逐字一致，顺序也是后端声明的顺序。 */
export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'CONFIRMED'
  | 'PREPARED'
  | 'SHIPPED'
  | 'COMPLETED'
  | 'CANCELED'

/** 支付渠道，与后端 `PaymentChannel` 一致。本期四渠道都由 Mock 承载。 */
export type PaymentChannel = 'BANK_CARD' | 'CREDIT_CARD' | 'ALIPAY' | 'WECHAT'

/** 下单明细：只带商品与数量，**不带金额**（总额由服务端按商品价快照算）。 */
export interface PlaceOrderItem {
  goods_id: string
  quantity: number
}

/** 下单入参。地址必填且必须是地址簿里已有的一条，不支持下单时新建。 */
export interface PlaceOrderRequest {
  address_id: string
  items: PlaceOrderItem[]
}

/** 下单成功回执。`pay_deadline` 让前端下单后立刻能起倒计时，不必再查详情。 */
export interface OrderPlacedData {
  order_id: string
  order_no: string
  total_amount: number
  status: OrderStatus
  create_at: string
  pay_deadline: string
}

/** 订单所属买家。买家端自己看不显示，字段仍按后端保留。 */
export interface OrderBuyerRef {
  id: string
  username: string
}

/**
 * 订单头。**不含明细**——明细要按订单 id 单独查一次（见 `OrderItemsData`），
 * 所以类型上也刻意分成两种结构，不合成一个。
 *
 * 可空性按契约交接建模（openapi 快照没输出 nullable）：收货三项是下单快照恒有值；
 * 支付两项未支付时为空；`pay_deadline` 只在待支付时有值；物流三项发货后才有。
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
  receiver_name: string
  receiver_phone: string
  receiver_address: string
  payment_channel: PaymentChannel | null
  paid_at: string | null
  pay_deadline: string | null
  carrier: string | null
  /** 多包裹时含换行，展示要保留换行。 */
  tracking_no: string | null
  logistics_remark: string | null
  /** 取消退款 + 售后退款的和，后端查询时算，前端不再相加。 */
  refunded_amount: number
  /** 售后条数（含待处理与换货），不等于退款笔数。 */
  aftersale_count: number
}

/**
 * 订单明细行。`amount` 是后端按单价×数量算好的，前端不重算。
 * 售后申请用 `order_item_id` 定位，不用 `goods_id`——同一商品可能出现在多个订单里。
 */
export interface OrderItemData {
  order_item_id: string
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

/** 取消订单、确认收货后的状态回执。 */
export interface OrderStatusData {
  order_id: string
  order_no: string
  status: OrderStatus
  update_at: string
}

/** 支付入参。渠道非法时后端回 83001 而不是参数错误。 */
export interface PayOrderRequest {
  payment_channel: PaymentChannel
}

/**
 * 支付结果。`redirect_url` 只有真实渠道需要跳转时才有值，Mock 恒为空；
 * 前端按「有值就跳转」写，接网关时不用改契约。
 */
export interface OrderPaymentData {
  order_id: string
  order_no: string
  status: OrderStatus
  payment_channel: PaymentChannel
  paid_at: string
  redirect_url: string | null
}

/** 地址条目。只有三项内容，不做省市区结构化。 */
export interface AddressData {
  id: string
  receiver_name: string
  receiver_phone: string
  detail_address: string
  is_default: boolean
  create_at: string
  update_at: string
}

/** 新建与编辑共用同一入参。**不含默认标记**：设默认是独立动作，编辑不会顺手改默认。 */
export interface AddressRequest {
  receiver_name: string
  receiver_phone: string
  detail_address: string
}

/** 地址簿：不分页，默认在前，上限 10 条。 */
export interface AddressListData {
  items: AddressData[]
}

/** 售后状态：待处理 → 已完成 / 已取消。没有独立的「已拒绝」，拒绝与取消同义。 */
export type AftersaleStatus = 'PENDING' | 'COMPLETED' | 'CANCELED'

/** 售后处理结果，由卖家完成时登记；未完成时为空。 */
export type AftersaleResult = 'RETURN_AND_REFUND' | 'REFUND_ONLY' | 'EXCHANGE'

/** 售后摘要。**不含描述与图片**，那两项只在详情里给。 */
export interface AftersaleData {
  aftersale_id: string
  aftersale_no: string
  order_id: string
  order_no: string
  order_item_id: string
  goods_name: string
  title: string
  status: AftersaleStatus
  result: AftersaleResult | null
  /** 换货与未完成时为空。 */
  refund_amount: number | null
  refunded_at: string | null
  create_at: string
}

/** 售后详情：摘要 + 描述 + 图片（按后端给的顺序，前端不再排序）。 */
export interface AftersaleDetailData extends AftersaleData {
  description: string
  image_urls: string[]
  update_at: string
}

/**
 * 售后申请。按订单项申请，申请时**不选类型**——退/换/仅退款是卖家处理时登记的结果。
 * 图片先走买家上传接口，这里只收返回的 URL，外链会被拒。
 */
export interface AftersaleApplyRequest {
  order_item_id: string
  title: string
  description: string
  image_urls: string[]
}

/** 申请成功回执：只回单号与状态。 */
export interface AftersaleAppliedData {
  aftersale_id: string
  aftersale_no: string
  status: AftersaleStatus
  create_at: string
}

/** 上传结果。买家只会拿到 IMAGE（JPG/PNG ≤5MB）。 */
export interface MediaUploadData {
  url: string
  kind: string
  size_bytes: number
}
