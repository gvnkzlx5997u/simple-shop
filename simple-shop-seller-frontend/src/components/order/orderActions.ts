import type { OrderStatus } from '../../api/types'

/**
 * 「什么状态能点哪个按钮」的唯一判定处。
 *
 * 放在纯函数里而不是写在模板里，有两个原因：
 * ① 卖家端单测环境是 node，挂不起组件，只有纯函数能被测到；
 * ② 状态机规则来自《契约交接》§4.1，改规则时只改这一处，页面不用跟着动。
 *
 * 依据（契约 §4.1）：
 * - 待支付：卖家只能取消，确认要等付款之后；
 * - 已支付：确认、取消；
 * - 已确认：备货完成、取消；
 * - 备货完成：发货（必须带承运商与单号）、取消；
 * - 已发货：改物流、取消（已付款会全额退款）；
 * - 交易完成：只能改物流，没有取消（交易完成只由买家确认收货推进）；
 * - 已取消：没有任何动作。
 */
export type OrderActionKey = 'confirm' | 'prepare' | 'ship' | 'editLogistics' | 'cancel'

export interface OrderAction {
  key: OrderActionKey
  label: string
  /** true = 推进流程的主按钮（放右侧）；false = 次按钮（当前只有取消，放左侧）。 */
  primary: boolean
  danger?: boolean
}

const ACTIONS: Record<OrderActionKey, OrderAction> = {
  confirm: { key: 'confirm', label: '确认订单', primary: true },
  prepare: { key: 'prepare', label: '标记备货完成', primary: true },
  ship: { key: 'ship', label: '开始发货', primary: true },
  editLogistics: { key: 'editLogistics', label: '修改物流', primary: true },
  cancel: { key: 'cancel', label: '取消订单', primary: false, danger: true },
}

/** 该状态可执行的动作，主按钮在前、取消在后；页面对空数组不渲染动作区。 */
export function sellerOrderActions(status: OrderStatus): OrderAction[] {
  switch (status) {
    case 'PENDING_PAYMENT':
      return [ACTIONS.cancel]
    case 'PAID':
      return [ACTIONS.confirm, ACTIONS.cancel]
    case 'CONFIRMED':
      return [ACTIONS.prepare, ACTIONS.cancel]
    case 'PREPARED':
      return [ACTIONS.ship, ACTIONS.cancel]
    case 'SHIPPED':
      return [ACTIONS.editLogistics, ACTIONS.cancel]
    case 'COMPLETED':
      return [ACTIONS.editLogistics]
    case 'CANCELED':
      return []
    default:
      return []
  }
}

/** 主按钮：推进流程或改物流。待支付与两个终态为 null（终态里交易完成仍可改物流）。 */
export function primaryOrderAction(status: OrderStatus): OrderAction | null {
  return sellerOrderActions(status).find((action) => action.primary) ?? null
}

/** 是否有取消入口：交易完成后不可取消。 */
export function canCancelOrder(status: OrderStatus): boolean {
  return sellerOrderActions(status).some((action) => action.key === 'cancel')
}

/** 能否改物流：发货后（含交易完成）可以，与后端 Order#canUpdateLogistics 一致。 */
export function canEditLogistics(status: OrderStatus): boolean {
  return status === 'SHIPPED' || status === 'COMPLETED'
}

/** 终态：没有任何出边，也没有取消。 */
export function isTerminalOrder(status: OrderStatus): boolean {
  return status === 'COMPLETED' || status === 'CANCELED'
}

/**
 * 待支付：卖家不能推进任何一步。页面用它把「确认 / 发货」的位置换成一句解释，
 * 而不是留一个禁用按钮让人猜原因。
 */
export function isPendingPayment(status: OrderStatus): boolean {
  return status === 'PENDING_PAYMENT'
}
