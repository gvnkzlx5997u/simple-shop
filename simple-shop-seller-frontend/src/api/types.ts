/**
 * 后端接口的数据契约（阶段四，与 openapi.json / 后端 DTO 的 JSON 字段逐字对应）。
 *
 * 字段名保持后端返回的 snake_case，不做驼峰转换：少一层映射就少一处与契约漂移的地方。
 */

/** 统一响应结构：成功 code = 0；业务拒绝为 HTTP 200 + 非零业务码。 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  /**
   * 参数校验错误明细，只在 HTTP 400（50002）时可能返回。
   * 卖家端不逐字段展示：能到这里的都是前端校验漏掉的输入，按业务码给一句中文提示即可。
   */
  errors?: FieldErrorData[]
}

export interface FieldErrorData {
  field: string
  message: string
}

/**
 * 卖家端业务错误码（与后端 ErrorCode 一致）。
 *
 * 3xxxx（意向与交易）与 4xxxx（历史与归档）随阶段二交易模型下线整段停用，
 * 码位不得复用：旧前端与旧日志里出现过这些编号，复用会被误读成新语义。
 */
export const ApiCode = {
  OK: 0,
  // 1xxxx 账号与会话（10005~10008 仅买家注册会触发，卖家端一般遇不到）
  LOGIN_FAILED: 10001,
  SESSION_INVALID: 10002,
  OLD_PASSWORD_MISMATCH: 10003,
  PASSWORD_INVALID: 10004,
  USERNAME_TAKEN: 10005,
  USERNAME_INVALID: 10006,
  PHONE_INVALID: 10007,
  /**
   * 已停用：阶段四起注册不再收 default_location，码位不复用
   * （旧日志里出现过 10008，复用会被误读成新语义）。
   */
  DEFAULT_LOCATION_INVALID: 10008,
  ACCOUNT_NOT_FOUND: 10009,
  ACCOUNT_INVALID: 10010,
  // 2xxxx 类别（20001~20006）与商品、媒体（20011~20027）
  CATEGORY_NOT_FOUND: 20001,
  CATEGORY_NOT_LEAF: 20002,
  CATEGORY_NAME_INVALID: 20003,
  CATEGORY_NAME_DUPLICATED: 20004,
  CATEGORY_HAS_CHILDREN: 20005,
  CATEGORY_IN_USE: 20006,
  GOODS_NOT_FOUND: 20011,
  GOODS_NAME_INVALID: 20012,
  GOODS_DESCRIPTION_INVALID: 20013,
  PRICE_INVALID: 20014,
  STOCK_INVALID: 20015,
  IMAGE_INVALID: 20016,
  IMAGE_LIMIT_EXCEEDED: 20017,
  MAIN_IMAGE_REQUIRED: 20018,
  GOODS_NOT_ON_SALE: 20019,
  /**
   * 已停用：阶段四起有未完成订单也能手动下架、也能把库存改成 0，
   * 后端不再返回本码。码位保留不复用。
   */
  GOODS_HAS_UNFINISHED_ORDER: 20020,
  GOODS_STATUS_MISMATCH: 20023,
  VIDEO_INVALID: 20025,
  MEDIA_INVALID: 20026,
  MEDIA_TYPE_NOT_ALLOWED: 20027,
  // 6xxxx 订单
  ORDER_NOT_FOUND: 60001,
  ORDER_STATUS_INVALID: 60002,
  ORDER_NOT_CANCELABLE: 60003,
  ORDER_STOCK_SHORTAGE: 60004,
  // 8xxxx 售后与物流（80001–80004 是买家地址簿，卖家端到不了）
  AFTERSALE_NOT_FOUND: 81001,
  AFTERSALE_STATUS_INVALID: 81002,
  AFTERSALE_REFUND_DUPLICATED: 81004,
  AFTERSALE_RESULT_REQUIRED: 81005,
  AFTERSALE_IMAGE_LIMIT_EXCEEDED: 81007,
  LOGISTICS_NOT_EDITABLE: 82002,
  // 5xxxx 通用
  INTERNAL_ERROR: 50000,
  TOO_MANY_REQUESTS: 50001,
  PARAM_INVALID: 50002,
  RESOURCE_NOT_FOUND: 50003,
  METHOD_NOT_ALLOWED: 50004,
  UNSUPPORTED_MEDIA_TYPE: 50005,
} as const

export type ApiCodeValue = (typeof ApiCode)[keyof typeof ApiCode]

/** 商品状态：只有两态；下架的具体原因记在 off_shelf_reason。 */
export type GoodsStatus = 'ON_SALE' | 'OFF_SHELF'
/**
 * 下架原因，只在 status = OFF_SHELF 时非空。
 * MANUAL 是终态（库存只读）；STOCK_ZERO 与 SOLD_OUT 补货即自动上架。
 */
export type OffShelfReason = 'MANUAL' | 'STOCK_ZERO' | 'SOLD_OUT'
/** 描述块类型。 */
export type ContentBlockType = 'TEXT' | 'IMAGE' | 'VIDEO'
/** 媒体上传用途：GOODS_IMAGE 仅图片；DESCRIPTION_MEDIA 允许图片与视频。 */
export type MediaPurpose = 'GOODS_IMAGE' | 'DESCRIPTION_MEDIA'
/** 上传后的媒体实际类型。 */
export type MediaKind = 'IMAGE' | 'VIDEO'

// ---- 会话与账号 ----
export interface SellerLoginRequest {
  account: string
  password: string
}
export interface AuthTokenData {
  token: string
  /** ISO 8601 UTC 带 Z。 */
  expire_at: string
}
export interface ChangePasswordRequest {
  old_password: string
  new_password: string
}

// ---- 分页（请求 page 从 1 起，page_size 默认 10、上限 100） ----
export interface PageResponse<T> {
  items: T[]
  page: number
  page_size: number
  total: number
}

// ---- 类别 ----
/** 类别树节点；children 为二级类别，叶子节点 children 为空数组。 */
export interface CategoryNodeData {
  id: string
  name: string
  children: CategoryNodeData[]
}
/** 单个类别；parent_id 为 null 表示一级类别。 */
export interface CategoryData {
  id: string
  name: string
  parent_id: string | null
}
export interface CreateCategoryL1Request {
  name: string
}
export interface CreateCategoryL2Request {
  l1_id: string
  name: string
}
export interface RenameCategoryRequest {
  name: string
}

// ---- 媒体 ----
export interface MediaUploadData {
  /** 形如 /media/goods/... 或 /media/description/...，可直接作为资源地址。 */
  url: string
  kind: MediaKind
  size_bytes: number
}

// ---- 商品 ----
/** 商品所属类别（两级拍平展示）。 */
export interface CategoryRefData {
  l1_id: string
  l1_name: string
  l2_id: string
  l2_name: string
}
export interface GoodsSummaryData {
  id: string
  name: string
  /** JSON 数字（后端 BigDecimal）；金额展示统一走 formatPrice。 */
  price: number
  stock: number
  status: GoodsStatus
  /** 在售时为 null；已下架时必为三种原因之一。 */
  off_shelf_reason: OffShelfReason | null
  /** 在售时为 null。 */
  off_shelf_at: string | null
  main_image_url: string | null
  category: CategoryRefData
  create_at: string
  update_at: string
}
/** 描述块；TEXT 带 text，IMAGE/VIDEO 带 url。 */
export interface DescriptionBlockData {
  type: ContentBlockType
  text: string | null
  url: string | null
}
export interface DescriptionBlockRequest {
  type: ContentBlockType
  text?: string | null
  url?: string | null
}
export interface GoodsImageData {
  url: string
  is_main: boolean
  sort_no: number
}
export interface GoodsDetailData {
  id: string
  name: string
  /** JSON 数字（后端 BigDecimal）；金额展示统一走 formatPrice。 */
  price: number
  stock: number
  status: GoodsStatus
  /** 在售时为 null；已下架时必为三种原因之一。 */
  off_shelf_reason: OffShelfReason | null
  /** 在售时为 null。 */
  off_shelf_at: string | null
  category: CategoryRefData
  main_image_url: string | null
  images: GoodsImageData[]
  description: DescriptionBlockData[]
  create_at: string
  update_at: string
  /** 等价于「在售且有库存」，由后端判定；买家可购买性以此为准。 */
  purchasable: boolean
}
export interface GoodsPublishRequest {
  name: string
  /** JSON 数字（后端 BigDecimal）；校验器产出的两位小数字符串需经 Number() 转换。 */
  price: number
  stock: number
  category_id: string
  description?: DescriptionBlockRequest[]
  main_image?: string | null
  gallery_images?: string[]
}
export interface GoodsStockRequest {
  stock: number
}
export interface GoodsCategoryRequest {
  category_id: string
}

// ---- 订单 ----
/**
 * 订单状态：整单流转，只能按序前进一格，不可跳级或回退。
 * 明细没有独立状态，状态判断只读订单头。
 *
 * 与阶段三的差别只在两端：前面多了待支付与已支付（付款后才轮到卖家确认），
 * 后面交易完成改由买家确认收货推进，卖家不再有「完成订单」这个动作。
 */
export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'CONFIRMED'
  | 'PREPARED'
  | 'SHIPPED'
  | 'COMPLETED'
  | 'CANCELED'

/** 支付渠道：本期全走 Mock，恒成功；失败时后端不写 payment_channel 与 paid_at。 */
export type PaymentChannel = 'BANK_CARD' | 'CREDIT_CARD' | 'ALIPAY' | 'WECHAT'

/** 订单里的买家引用：只有 ID 与实时用户名，不含电话等账号字段。 */
export interface BuyerRefData {
  id: string
  username: string
}
/**
 * 订单头：列表项与详情共用同一形状，**不含订单项**。
 * 注意主键字段是 order_id（不是 id），明细由 listOrderItems 按订单 ID 另行查询。
 *
 * 「快照类」字段（收货三项）是下单时的值，地址簿后续改名或删除都不影响这里；
 * 「支付/物流类」字段按状态先后出现，未到那一步时后端给 null，界面据此隐藏整块。
 */
export interface OrderData {
  order_id: string
  order_no: string
  status: OrderStatus
  /** JSON 数字（后端 BigDecimal）；金额展示统一走 formatPrice。 */
  total_amount: number
  /** 订单项（明细行）条数：同一商品买多件仍算一行，不是件数合计。 */
  item_count: number
  create_at: string
  update_at: string
  buyer: BuyerRefData
  /** 收货人快照，下单时写入。 */
  receiver_name: string | null
  receiver_phone: string | null
  receiver_address: string | null
  /** 未支付时为 null；支付成功后才有值，失败时两者必须都为空。 */
  payment_channel: PaymentChannel | null
  paid_at: string | null
  /** 仅待支付订单有值；倒计时用它，不要自己按 24 小时算。 */
  pay_deadline: string | null
  /** 发货后才有；tracking_no 可能含换行（一个订单多个包裹）。 */
  carrier: string | null
  tracking_no: string | null
  logistics_remark: string | null
  /** 已退金额 = 取消退款 + 售后退款（只算「已完成 + 退款类结果」）。 */
  refunded_amount: number
  /** 该订单售后条数（含待处理与换货），不等于退款笔数。 */
  aftersale_count: number
}
/** 订单项：商品引用 + 下单快照 + 数量，小计由后端给（前端不做浮点乘法）。 */
export interface OrderItemData {
  /** 售后申请用它定位，不要用 goods_id：同一商品可能出现在多个订单里。 */
  order_item_id: string
  goods_id: string
  goods_name: string
  unit_price: number
  quantity: number
  amount: number
}
/** 一笔订单的全部明细；不分页。 */
export interface OrderItemsData {
  items: OrderItemData[]
}
/** 推进与取消只回状态，明细与买家信息仍由订单详情提供。 */
export interface OrderStatusData {
  order_id: string
  order_no: string
  status: OrderStatus
  update_at: string
}
/**
 * 发货与改物流共用一份请求体：承运商、单号必填，备注可空。
 * 与后端 ShipLogisticsRequest 的校验口径一致（承运商 ≤50、单号与备注 ≤500 码点）。
 */
export interface ShipLogisticsRequest {
  carrier: string
  tracking_no: string
  logistics_remark?: string | null
}

// ---- 售后 ----
/** 售后三态；「已拒绝」没有独立状态，拒绝 = 取消。 */
export type AftersaleStatus = 'PENDING' | 'COMPLETED' | 'CANCELED'
/** 处理结果由卖家完成时登记；申请阶段不选类型。 */
export type AftersaleResult = 'RETURN_AND_REFUND' | 'REFUND_ONLY' | 'EXCHANGE'
/** 售后摘要：列表项用，不含描述与图片。 */
export interface AftersaleData {
  aftersale_id: string
  aftersale_no: string
  order_id: string
  order_no: string
  order_item_id: string
  goods_name: string
  title: string
  status: AftersaleStatus
  /** 未完成时为 null；换货不产生退款，refund_amount 与 refunded_at 都是 null。 */
  result: AftersaleResult | null
  refund_amount: number | null
  refunded_at: string | null
  create_at: string
}
/** 售后详情：摘要之外另给买家描述与凭证图（按申请时的顺序）。 */
export interface AftersaleDetailData extends AftersaleData {
  description: string
  image_urls: string[]
  update_at: string
}
/** 完成售后：result 必填三选一，缺失或非法后端返回 81005。 */
export interface AftersaleCompleteRequest {
  result: AftersaleResult
}

// ---- 客户 ----
/** 客户摘要：账号基本信息。买家注册即成为客户。 */
export interface CustomerSummaryData {
  id: string
  username: string
  phone: string
  create_at: string
}
/** 客户详情：只有基本信息。购买历史改查卖家订单列表（buyer_id 筛选）。 */
export interface CustomerDetailData {
  id: string
  username: string
  phone: string
  create_at: string
}
