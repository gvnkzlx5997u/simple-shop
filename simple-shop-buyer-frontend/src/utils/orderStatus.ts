import type { TagTone } from '../components/common/StatusTag.vue'
import type { OrderStatus, PaymentChannel } from '../api/types'

/**
 * 订单状态的展示口径，只在买家端用。
 *
 * 七个值与后端 `OrderStatus` 枚举逐字对应，顺序也是后端声明的顺序。
 */
export const ORDER_STATUS_TEXT: Record<OrderStatus, string> = {
  PENDING_PAYMENT: '待支付',
  PAID: '已支付',
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
 * 订单七态 → 徽章色调（StatusTag 消费）。待支付用 warning 催付款，
 * 履约中四态用 info、完成用 success、取消用 default（ghost，走 base-*，两套主题自动翻转）。
 */
export const ORDER_STATUS_TONES: Record<OrderStatus, TagTone> = {
  PENDING_PAYMENT: 'warning',
  PAID: 'info',
  CONFIRMED: 'info',
  PREPARED: 'info',
  SHIPPED: 'info',
  COMPLETED: 'success',
  CANCELED: 'default',
}

/**
 * 买家能做的四个动作，逐格照抄契约的买家状态矩阵。
 *
 * 接口没有返回 can_* 标记，只能在前端按状态派生，所以判断集中在这里：
 * 页面只调函数，不各自写 `status === '...'`，后端调整边界时只改这一处。
 */

/** 只有待支付能付款；支付失败仍停在本状态，可重试。 */
export function canPayByBuyer(status: OrderStatus): boolean {
  return status === 'PENDING_PAYMENT'
}

/** 发货前可取消（含待支付）；已发货后只能由卖家取消，回 60003。 */
export function canCancelByBuyer(status: OrderStatus): boolean {
  return (
    status === 'PENDING_PAYMENT' ||
    status === 'PAID' ||
    status === 'CONFIRMED' ||
    status === 'PREPARED'
  )
}

/** 确认收货是交易完成的唯一入口，只在已发货出现；成交扣库存也在这一步。 */
export function canConfirmReceipt(status: OrderStatus): boolean {
  return status === 'SHIPPED'
}

/** 售后只对交易完成开放，按订单项申请。 */
export function canApplyAftersale(status: OrderStatus): boolean {
  return status === 'COMPLETED'
}

/** 终态：不会再变，详情页也就没有可做的操作（完成态另有售后入口）。 */
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
  { value: 'PENDING_PAYMENT', label: '待支付' },
  { value: 'PAID', label: '已支付' },
  { value: 'CONFIRMED', label: '已确认' },
  { value: 'PREPARED', label: '备货完成' },
  { value: 'SHIPPED', label: '已发货' },
  { value: 'COMPLETED', label: '交易完成' },
  { value: 'CANCELED', label: '已取消' },
]

/** 支付渠道中文名。退款按原渠道退回，所以详情要把实际用的渠道说清楚。 */
export const PAYMENT_CHANNEL_TEXT: Record<PaymentChannel, string> = {
  BANK_CARD: '银行卡',
  CREDIT_CARD: '信用卡',
  ALIPAY: '支付宝',
  WECHAT: '微信',
}

export function paymentChannelText(channel: PaymentChannel | null): string {
  return channel ? (PAYMENT_CHANNEL_TEXT[channel] ?? String(channel)) : ''
}

/** 支付页的渠道选项，顺序与后端枚举一致。 */
export const PAYMENT_CHANNELS: ReadonlyArray<{ value: PaymentChannel; label: string }> = [
  { value: 'BANK_CARD', label: '银行卡' },
  { value: 'CREDIT_CARD', label: '信用卡' },
  { value: 'ALIPAY', label: '支付宝' },
  { value: 'WECHAT', label: '微信' },
]