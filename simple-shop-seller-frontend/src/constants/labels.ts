import type {
  Disposal,
  FailType,
  GoodsHistoryResult,
  GoodsStatus,
  IntentionResult,
  IntentionStatus,
} from '../api/types'

export type TagTone = 'default' | 'success' | 'warning' | 'danger' | 'info'

/** 商品状态；两种下架均留在当前列表，只有售罄归档才进历史。 */
export const GOODS_STATUS_LABELS: Record<GoodsStatus, string> = {
  ON_SALE: '在售',
  FROZEN: '交易中',
  OFF_MANUAL: '已下架',
  OFF_ZERO: '已售罄',
}

export const GOODS_STATUS_TONES: Record<GoodsStatus, TagTone> = {
  ON_SALE: 'success',
  FROZEN: 'warning',
  OFF_MANUAL: 'default',
  OFF_ZERO: 'default',
}

export const INTENTION_STATUS_LABELS: Record<IntentionStatus, string> = {
  QUEUED: '排队中',
  TRADING: '交易中',
  SUCCEEDED: '交易成功',
  FAILED: '交易失败',
}

export const INTENTION_STATUS_TONES: Record<IntentionStatus, TagTone> = {
  QUEUED: 'info',
  TRADING: 'warning',
  SUCCEEDED: 'success',
  FAILED: 'danger',
}

export const FAIL_TYPE_LABELS: Record<FailType, string> = {
  SOLD_OUT: '商品已售出',
  SELLER_VOIDED: '卖家作废',
}

export const DISPOSAL_LABELS: Record<Disposal, string> = {
  VOIDED: '作废（终态）',
  REQUEUED: '重新排队（回队尾）',
}

export const HISTORY_RESULT_LABELS: Record<GoodsHistoryResult, string> = {
  SOLD_OUT: '售罄归档',
}

export const HISTORY_INTENTION_RESULT_LABELS: Record<IntentionResult, string> = {
  SUCCEEDED: '交易成功',
  FAILED: '交易失败',
}
