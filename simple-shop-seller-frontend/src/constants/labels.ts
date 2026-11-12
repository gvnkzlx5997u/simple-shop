import type { GoodsStatus, OffShelfReason, OrderStatus } from '../api/types'

export type TagTone = 'default' | 'success' | 'warning' | 'danger' | 'info'

/** 商品状态：只有两态，下架原因单独展示。 */
export const GOODS_STATUS_LABELS: Record<GoodsStatus, string> = {
  ON_SALE: '在售',
  OFF_SHELF: '已下架',
}

export const GOODS_STATUS_TONES: Record<GoodsStatus, TagTone> = {
  ON_SALE: 'success',
  OFF_SHELF: 'default',
}

/** 下架原因；只在 status = OFF_SHELF 时出现，与状态成对展示。 */
export const OFF_SHELF_REASON_LABELS: Record<OffShelfReason, string> = {
  MANUAL: '手动下架',
  STOCK_ZERO: '库存耗尽',
  SOLD_OUT: '已售',
}

/** 原因配色：手动下架是中性终态，库存耗尽要提示补货，已售属于正常成交。 */
export const OFF_SHELF_REASON_TONES: Record<OffShelfReason, TagTone> = {
  MANUAL: 'default',
  STOCK_ZERO: 'warning',
  SOLD_OUT: 'info',
}

/**
 * 订单状态的展示顺序：与流转顺序一致，终态「已取消」放最后。
 * 状态标签页与详情页的操作按钮都按这个顺序取，不要各自硬编码。
 */
export const ORDER_STATUS_ORDER: readonly OrderStatus[] = [
  'PENDING_CONFIRM',
  'CONFIRMED',
  'PREPARED',
  'SHIPPED',
  'COMPLETED',
  'CANCELED',
]

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING_CONFIRM: '待确认',
  CONFIRMED: '已确认',
  PREPARED: '备货完成',
  SHIPPED: '已发货',
  COMPLETED: '交易完成',
  CANCELED: '已取消',
}

/** 待确认要卖家动手，用警示色；中间三态是进行中用信息色；完成用成功色；取消是中性终态。 */
export const ORDER_STATUS_TONES: Record<OrderStatus, TagTone> = {
  PENDING_CONFIRM: 'warning',
  CONFIRMED: 'info',
  PREPARED: 'info',
  SHIPPED: 'info',
  COMPLETED: 'success',
  CANCELED: 'default',
}

/** 各状态对卖家意味着什么、下一步做什么；详情页直接用这段说明，不再另写文案。 */
export const ORDER_STATUS_HINTS: Record<OrderStatus, string> = {
  PENDING_CONFIRM: '买家已下单，等待你确认。确认后进入「已确认」。',
  CONFIRMED: '已接单，去备货；备好后标记「备货完成」。',
  PREPARED: '备货完成，下一步开始发货。发货后买家不能再取消。',
  SHIPPED: '已发货，等待交易完成。完成时按明细数量扣减库存，库存不足会整单拒绝。',
  COMPLETED: '订单已完成，库存已扣减，没有后续操作。',
  CANCELED: '订单已取消，整单终止，没有后续操作。',
}
