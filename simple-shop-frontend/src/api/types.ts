/**
 * 后端接口的数据契约（与 Java DTO 的 JSON 字段逐字对应）。
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
  // 1xxxx 认证与会话
  LOGIN_FAILED: 10001,
  SESSION_INVALID: 10002,
  OLD_PASSWORD_MISMATCH: 10003,
  NEW_PASSWORD_TOO_SHORT: 10004,
  // 2xxxx 商品
  GOODS_ALREADY_EXISTS: 20001,
  NO_CURRENT_GOODS: 20002,
  GOODS_NOT_ON_SALE: 20003,
  GOODS_NOT_FROZEN: 20004,
  GOODS_FROZEN_BY_TRADE: 20005,
  QUEUE_NOT_EMPTY: 20006,
  GOODS_NAME_INVALID: 20007,
  GOODS_DESCRIPTION_INVALID: 20008,
  IMAGE_INVALID: 20009,
  PRICE_OUT_OF_RANGE: 20010,
  HISTORY_GOODS_NOT_FOUND: 20011,
  // 3xxxx 意向与交易
  NOT_QUEUE_HEAD: 30002,
  INTENTION_STATUS_MISMATCH: 30003,
  TRADING_ALREADY_EXISTS: 30004,
  ARCHIVE_PRECONDITION_FAILED: 30005,
  INVALID_DISPOSAL: 30006,
  FAIL_REASON_TOO_LONG: 30007,
  INTENTION_NOT_FOUND: 30008,
  // 4xxxx 口令码
  PASSCODE_EXPIRED: 40002,
  // 5xxxx 通用
  INTERNAL_ERROR: 50000,
  TOO_MANY_REQUESTS: 50001,
  PARAM_INVALID: 50002,
} as const

export type ApiCodeValue = (typeof ApiCode)[keyof typeof ApiCode]

/** 商品状态；off_sale 只出现在历史商品。 */
export type GoodsStatus = 'on_sale' | 'frozen' | 'off_sale'
/** 冻结来源；status = frozen 时才有值。 */
export type FreezeBy = 'manual' | 'trade'
/** 意向状态。 */
export type IntentionStatus = 'queued' | 'trading' | 'succeeded' | 'failed' | 'revoked'
/** 归档后的意向状态（终态）。 */
export type TerminalIntentionStatus = Extract<IntentionStatus, 'succeeded' | 'failed' | 'revoked'>
/** 失败类型。 */
export type FailType = 'sold_out' | 'revoked' | 'voided' | 'requeued'
/** 商品级结果（历史表）。 */
export type GoodsResult = 'sold' | 'offline'
/** 流水级结果（与商品级结果不是同一口径）。 */
export type TradeResult = 'sold' | 'failed'
/** 标记失败的裁决方式。 */
export type Disposal = 'voided' | 'requeued'

// ---- I11-01 / I11-02 会话 ----
export interface LoginRequest {
  account: string
  password: string
}
export interface LoginData {
  /** 会话令牌，不是口令码。 */
  token: string
  /** ISO 8601 UTC 带 Z。 */
  expire_at: string
}

// ---- I11-03 账号 ----
export interface ChangePasswordRequest {
  old_password: string
  new_password: string
}

// ---- I11-04 ~ I11-09 商品 ----
export interface GoodsData {
  id: string
  name: string
  description: string | null
  pic_url: string | null
  /** 字符串金额，两位小数；不要转 number。 */
  price: string
  status: GoodsStatus
  freeze_by: FreezeBy | null
  create_at: string
  trade_start: string | null
}
export interface ImageData {
  /** 形如 /images/2026/10/<uuid>.jpg，可直接作为 img 的 src。 */
  pic_url: string
}
export interface PublishGoodsRequest {
  name: string
  description?: string | null
  pic_url?: string | null
  price: string
}
export interface PublishResultData {
  id: string
}

// ---- I11-10 ~ I11-14 意向与交易 ----
export interface IntentionItemData {
  id: string
  /** trading 行为 null（交易中不占位次）。 */
  rank: number | null
  queue_order: number
  name: string
  tel: string
  create_at: string
  status: IntentionStatus
  fail_type: FailType | null
  fail_reason: string | null
}
export interface IntentionPageData {
  total: number
  page: number
  page_size: number
  /** 当前非终态意向数。 */
  queue_count: number
  items: IntentionItemData[]
}
export interface IntentionIdRequest {
  intention_id: string
}
export interface TradeFailureRequest {
  intention_id: string
  disposal: Disposal
  fail_reason?: string | null
}
export interface PasscodeData {
  token: string
}

// ---- I11-15 / I11-16 历史 ----
export interface HistoryGoodsItemData {
  id: string
  name: string
  create_at: string
  trade_end: string
  result: GoodsResult
}
export interface HistoryPageData {
  total: number
  page: number
  page_size: number
  items: HistoryGoodsItemData[]
}
export interface TradeData {
  trade_start: string
  trade_end: string
  result: TradeResult
  fail_type: FailType | null
  fail_reason: string | null
}
export interface HistoryIntentionData {
  id: string
  name: string
  tel: string
  create_at: string
  status: TerminalIntentionStatus
  fail_type: FailType | null
  fail_reason: string | null
  trade_count: number
  trades: TradeData[]
}
export interface HistoryGoodsDetailData {
  id: string
  name: string
  description: string | null
  pic_url: string | null
  price: string
  status: GoodsStatus
  create_at: string
  trade_end: string
  result: GoodsResult
  intentions: HistoryIntentionData[]
}
