import type { TagTone } from '../components/common/StatusTag.vue'
import type { AftersaleResult, AftersaleStatus } from '../api/types'

/**
 * 售后状态与结果的展示口径。
 *
 * 「已取消」不区分买家撤销与卖家拒绝——后端没有独立的已拒绝态，两者是同一个动作。
 */
export const AFTERSALE_STATUS_TEXT: Record<AftersaleStatus, string> = {
  PENDING: '待处理',
  COMPLETED: '已完成',
  CANCELED: '已取消',
}

export const AFTERSALE_STATUS_TONES: Record<AftersaleStatus, TagTone> = {
  PENDING: 'warning',
  COMPLETED: 'success',
  CANCELED: 'default',
}

export function aftersaleStatusText(status: AftersaleStatus): string {
  return AFTERSALE_STATUS_TEXT[status] ?? String(status)
}

/** 处理结果由卖家完成时登记，未完成时后端给 null。 */
export const AFTERSALE_RESULT_TEXT: Record<AftersaleResult, string> = {
  RETURN_AND_REFUND: '退货并退款',
  REFUND_ONLY: '仅退款',
  EXCHANGE: '换货',
}

export function aftersaleResultText(result: AftersaleResult | null): string {
  return result ? (AFTERSALE_RESULT_TEXT[result] ?? String(result)) : ''
}

export const AFTERSALE_STATUS_FILTERS: ReadonlyArray<{
  value: AftersaleStatus | ''
  label: string
}> = [
  { value: '', label: '全部状态' },
  { value: 'PENDING', label: '待处理' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELED', label: '已取消' },
]