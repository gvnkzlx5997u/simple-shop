/**
 * 后端接口的数据契约（阶段二，与 openapi.json / 后端 DTO 的 JSON 字段逐字对应）。
 *
 * 字段名保持后端返回的 snake_case，不做驼峰转换：少一层映射就少一处与契约漂移的地方。
 */

/** 统一响应结构：成功 code = 0；业务拒绝为 HTTP 200 + 非零业务码。 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/** 卖家端业务错误码（与后端 ErrorCode 一致）。 */
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
  DEFAULT_LOCATION_INVALID: 10008,
  ACCOUNT_NOT_FOUND: 10009,
  ACCOUNT_INVALID: 10010,
  // 2xxxx 类别（20001~20006）与商品、媒体（20011~20028）
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
  GOODS_NOT_OFF_SHELF: 20020,
  GOODS_FROZEN: 20021,
  QUEUE_NOT_EMPTY: 20022,
  GOODS_STATUS_MISMATCH: 20023,
  GOODS_STOCK_NOT_ZERO: 20024,
  VIDEO_INVALID: 20025,
  MEDIA_INVALID: 20026,
  MEDIA_TYPE_NOT_ALLOWED: 20027,
  CONFIRMATION_REQUIRED: 20028,
  // 3xxxx 意向与交易
  INTENTION_NOT_FOUND: 30001,
  NOT_QUEUE_HEAD: 30002,
  INTENTION_STATUS_MISMATCH: 30003,
  TRADING_ALREADY_EXISTS: 30004,
  QUEUE_FULL: 30005,
  INVALID_DISPOSAL: 30006,
  FAIL_REASON_TOO_LONG: 30007,
  // 4xxxx 历史
  HISTORY_GOODS_NOT_FOUND: 40001,
  HISTORY_INTENTION_NOT_FOUND: 40002,
  ARCHIVE_PRECONDITION_FAILED: 40003,
  // 5xxxx 通用
  INTERNAL_ERROR: 50000,
  TOO_MANY_REQUESTS: 50001,
  PARAM_INVALID: 50002,
  RESOURCE_NOT_FOUND: 50003,
  METHOD_NOT_ALLOWED: 50004,
  UNSUPPORTED_MEDIA_TYPE: 50005,
} as const

export type ApiCodeValue = (typeof ApiCode)[keyof typeof ApiCode]

/** 商品状态；OFF_MANUAL/OFF_ZERO 是两种下架终态，留在当前表，不进历史。 */
export type GoodsStatus = 'ON_SALE' | 'FROZEN' | 'OFF_MANUAL' | 'OFF_ZERO'
/** 意向状态。 */
export type IntentionStatus = 'QUEUED' | 'TRADING' | 'SUCCEEDED' | 'FAILED'
/** 失败类型。 */
export type FailType = 'SOLD_OUT' | 'SELLER_VOIDED'
/** 历史商品结果；阶段二只有售罄归档会进历史。 */
export type GoodsHistoryResult = 'SOLD_OUT'
/** 历史意向结果。 */
export type IntentionResult = 'SUCCEEDED' | 'FAILED'
/** 标记失败的裁决方式。 */
export type Disposal = 'VOIDED' | 'REQUEUED'
/** 描述块类型。 */
export type ContentBlockType = 'TEXT' | 'IMAGE' | 'VIDEO'
/** 媒体上传用途：GOODS_IMAGE 仅图片；DESCRIPTION_MEDIA 允许图片与视频。 */
export type MediaPurpose = 'GOODS_IMAGE' | 'DESCRIPTION_MEDIA'
/** 上传后的媒体实际类型。 */
export type MediaKind = 'IMAGE' | 'VIDEO'
/** 标记成功后商品去向：ON_SALE = 库存有余回在售；ARCHIVED = 售罄归档转入历史。 */
export type TradeGoodsStatus = 'ON_SALE' | 'ARCHIVED'

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
  category: CategoryRefData
  main_image_url: string | null
  images: GoodsImageData[]
  description: DescriptionBlockData[]
  create_at: string
  update_at: string
  /** 与公共详情共享的字段：买家当前是否可提交意向；卖家端仅作展示参考。 */
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
/** 重新上架必须显式确认；不带 confirm:true 会被拒 20028。 */
export interface ReListRequest {
  confirm: boolean
}

// ---- 意向与交易 ----
/** 意向名单条目；按 queue_order 升序，队首 = 排在最前的 QUEUED。 */
export interface IntentionEntryData {
  id: string
  /** 全库唯一序号，跨商品递增；界面展示「本商品内第几位」需另行计算。 */
  queue_order: number
  status: IntentionStatus
  fail_type: FailType | null
  buyer_username: string
  buyer_phone: string
  buyer_default_location: string | null
  create_at: string
  finish_at: string | null
}
export interface TradeFailureRequest {
  disposal: Disposal
}
export interface TradeResultData {
  intention: IntentionEntryData
  /** ON_SALE = 库存有余回在售；ARCHIVED = 售罄归档，商品转入历史记录。 */
  goods_status: TradeGoodsStatus
}

// ---- 历史记录 ----
export interface GoodsHistorySummaryData {
  id: string
  name: string
  /** JSON 数字（后端 BigDecimal）；金额展示统一走 formatPrice。 */
  price: number
  category_l1_name: string
  category_l2_name: string
  result: GoodsHistoryResult
  create_at: string
  archived_at: string
}
export interface HistoryDescriptionBlockData {
  type: ContentBlockType
  text: string | null
  url: string | null
}
export interface HistoryImageData {
  url: string
  is_main: boolean
  sort_no: number
}
export interface IntentionHistoryEntryData {
  id: string
  buyer_username: string
  buyer_phone: string
  create_at: string
  result: IntentionResult
  fail_type: FailType | null
  finish_at: string
}
export interface GoodsHistoryDetailData {
  id: string
  name: string
  /** JSON 数字（后端 BigDecimal）；金额展示统一走 formatPrice。 */
  price: number
  category_l1_name: string
  category_l2_name: string
  result: GoodsHistoryResult
  /** 归档时的快照；结构与商品详情的对应字段一致。 */
  description: HistoryDescriptionBlockData[]
  images: HistoryImageData[]
  create_at: string
  archived_at: string
  intentions: IntentionHistoryEntryData[]
}

// ---- 客户 ----
export interface CustomerSummaryData {
  id: string
  username: string
  phone: string
  default_location: string | null
  create_at: string
}
export interface CustomerPurchaseEntryData {
  goods_id: string
  goods_name: string
  create_at: string
  result: IntentionResult
  fail_type: FailType | null
  finish_at: string | null
}
export interface CustomerDetailData {
  id: string
  username: string
  phone: string
  default_location: string | null
  create_at: string
  /** 内嵌分页；按下单时间倒序。 */
  purchase_history: PageResponse<CustomerPurchaseEntryData>
}
