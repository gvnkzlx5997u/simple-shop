import type { AftersaleStatus } from '../../api/types'

/**
 * 售后单「能做什么」的判定处，口径与 orderActions 一致：只在这里判断状态。
 *
 * - 待处理：可以「完成」（登记三种结果之一）或「取消」（同拒绝）；
 * - 已完成：终态，不能取消、不能改结果；
 * - 已取消：终态；后端按幂等处理重复取消，界面不再给按钮。
 */
export type AftersaleActionKey = 'complete' | 'cancel'

export interface AftersaleAction {
  key: AftersaleActionKey
  label: string
  primary: boolean
  danger?: boolean
}

const ACTIONS: Record<AftersaleActionKey, AftersaleAction> = {
  complete: { key: 'complete', label: '处理并完成', primary: true },
  cancel: { key: 'cancel', label: '取消 / 拒绝', primary: false, danger: true },
}

/** 待处理给两个动作；两个终态都给空数组（页面不渲染动作区）。 */
export function sellerAftersaleActions(status: AftersaleStatus): AftersaleAction[] {
  switch (status) {
    case 'PENDING':
      return [ACTIONS.complete, ACTIONS.cancel]
    case 'COMPLETED':
    case 'CANCELED':
      return []
    default:
      return []
  }
}
