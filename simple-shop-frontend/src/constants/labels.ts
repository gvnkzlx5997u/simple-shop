import type {
  FailType,
  FreezeBy,
  GoodsResult,
  GoodsStatus,
  IntentionStatus,
  TradeResult,
} from '../api/types'

export type TagTone = 'default' | 'success' | 'warning' | 'danger' | 'info'

export const GOODS_STATUS_LABELS: Record<GoodsStatus, string> = {
  on_sale: '在售',
  frozen: '已冻结',
  off_sale: '已下架',
}

export const FREEZE_BY_LABELS: Record<FreezeBy, string> = {
  manual: '手动冻结',
  trade: '交易冻结',
}

export const INTENTION_STATUS_LABELS: Record<IntentionStatus, string> = {
  queued: '排队中',
  trading: '交易中',
  succeeded: '交易成功',
  failed: '交易失败',
  revoked: '已撤销',
}

export const INTENTION_STATUS_TONES: Record<IntentionStatus, TagTone> = {
  queued: 'info',
  trading: 'warning',
  succeeded: 'success',
  failed: 'danger',
  revoked: 'default',
}

export const FAIL_TYPE_LABELS: Record<FailType, string> = {
  sold_out: '商品已售出',
  revoked: '买家撤销',
  voided: '卖家作废',
  requeued: '重新排队',
}

export const GOODS_RESULT_LABELS: Record<GoodsResult, string> = {
  sold: '成功卖出',
  offline: '手动下架',
}

export const TRADE_RESULT_LABELS: Record<TradeResult, string> = {
  sold: '交易成功',
  failed: '交易失败',
}

export const TRADE_RESULT_TONES: Record<TradeResult, TagTone> = {
  sold: 'success',
  failed: 'danger',
}
