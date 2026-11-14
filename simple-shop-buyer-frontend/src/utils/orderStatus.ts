import type { OrderStatus } from '../api/types'

/**
 * 订单状态的展示口径，只在买家端用。
 *
 * 六个值与后端 `OrderStatus` 枚举逐字对应，顺序也是后端声明的顺序。
 */
export const ORDER_STATUS_TEXT: Record<OrderStatus, string> = {
  PENDING_CONFIRM: '待确认',
  CONFIRMED: '已确认',
  PREPARED: '备货完成',
  SHIPPED: '已发货',
  COMPLETED: '交易完成',
  CANCELED: '已取消',
}

export function orderStatusText(status: OrderStatus): string {
  // 后端加了新状态而前端还没跟上时，宁可显示原始值也不要显示 undefined。
  return ORDER_STATUS_TEXT[status] ?? String(status)
}

/**
 * 买家能否取消：后端 `Order.canCancelByBuyer()` 只放行前三态。
 * 已发货后不可取消（60003），两个终态自然也不行。
 */
export function canCancelByBuyer(status: OrderStatus): boolean {
  return status === 'PENDING_CONFIRM' || status === 'CONFIRMED' || status === 'PREPARED'
}

/** 终态：不会再变，详情页也就没有可做的操作。 */
export function isTerminal(status: OrderStatus): boolean {
  return status === 'COMPLETED' || status === 'CANCELED'
}

/**
 * 筛选下拉的选项。
 *
 * ⚠️ 只能是**单个状态**：后端筛参是 `OrderStatus.valueOf(status)`，
 * 传「进行中」这种跨状态组合会直接按参数非法拒绝（50002）。
 * 想看进行中的订单就选「全部状态」——列表按下单时间倒序，新单在最前。
 */
export const ORDER_STATUS_FILTERS: ReadonlyArray<{ value: OrderStatus | ''; label: string }> = [
  { value: '', label: '全部状态' },
  { value: 'PENDING_CONFIRM', label: '待确认' },
  { value: 'CONFIRMED', label: '已确认' },
  { value: 'PREPARED', label: '备货完成' },
  { value: 'SHIPPED', label: '已发货' },
  { value: 'COMPLETED', label: '交易完成' },
  { value: 'CANCELED', label: '已取消' },
]
