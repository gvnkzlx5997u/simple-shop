import type {
  AftersaleResult,
  AftersaleStatus,
  GoodsStatus,
  OffShelfReason,
  OrderStatus,
  PaymentChannel,
} from '../api/types'

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
 * 状态标签页、详情页流程图与操作按钮都按这个顺序取，不要各自硬编码。
 */
export const ORDER_STATUS_ORDER: readonly OrderStatus[] = [
  'PENDING_PAYMENT',
  'PAID',
  'CONFIRMED',
  'PREPARED',
  'SHIPPED',
  'COMPLETED',
  'CANCELED',
]

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING_PAYMENT: '待支付',
  PAID: '已支付',
  CONFIRMED: '已确认',
  PREPARED: '备货完成',
  SHIPPED: '已发货',
  COMPLETED: '交易完成',
  CANCELED: '已取消',
}

/**
 * 配色口径：橙色 = 等着卖家动手（待支付要盯、已支付要确认），
 * 蓝色 = 进行中（已确认 / 备货完成 / 已发货），绿色 = 完成，灰色 = 已取消。
 */
export const ORDER_STATUS_TONES: Record<OrderStatus, TagTone> = {
  PENDING_PAYMENT: 'warning',
  PAID: 'warning',
  CONFIRMED: 'info',
  PREPARED: 'info',
  SHIPPED: 'info',
  COMPLETED: 'success',
  CANCELED: 'default',
}

/** 各状态对卖家意味着什么、下一步做什么；详情页直接用这段说明，不再另写文案。 */
export const ORDER_STATUS_HINTS: Record<OrderStatus, string> = {
  PENDING_PAYMENT:
    '买家已下单但还没付款。待支付订单只能取消：确认、备货、发货都要等买家付完款之后。',
  PAID: '买家已付款，等待你确认接单。确认后进入「已确认」，接下来要备货。',
  CONFIRMED: '已接单，去备货；备好后标记「备货完成」。',
  PREPARED: '备货完成，下一步开始发货；发货必须填承运商与运单号。',
  SHIPPED: '已发货，等买家确认收货。发货后仍可改物流单号；交易完成前也可以取消，已付款会全额退款。',
  COMPLETED: '订单已完成，库存已扣减；买家可就这笔订单申请售后，物流单号仍可在「物流信息」里修改。',
  CANCELED: '订单已取消，整单终止，没有后续操作。',
}

// ---- 支付 ----

/**
 * 支付渠道中文名。
 * 卖家端只在订单详情里展示买家用了哪个渠道，不做任何支付操作。
 */
export const PAYMENT_CHANNEL_LABELS: Record<PaymentChannel, string> = {
  BANK_CARD: '银行卡',
  CREDIT_CARD: '信用卡',
  ALIPAY: '支付宝',
  WECHAT: '微信支付',
}

// ---- 售后 ----

/** 售后三态顺序：待处理在前（要卖家动手），两个终态在后。 */
export const AFTERSALE_STATUS_ORDER: readonly AftersaleStatus[] = [
  'PENDING',
  'COMPLETED',
  'CANCELED',
]

/** 「已取消」不区分买家撤销与卖家拒绝——后端没有独立的已拒绝态，两者是同一个动作。 */
export const AFTERSALE_STATUS_LABELS: Record<AftersaleStatus, string> = {
  PENDING: '待处理',
  COMPLETED: '已完成',
  CANCELED: '已取消',
}

export const AFTERSALE_STATUS_TONES: Record<AftersaleStatus, TagTone> = {
  PENDING: 'warning',
  COMPLETED: 'success',
  CANCELED: 'default',
}

/** 处理结果只在「已完成」的售后单上出现。 */
export const AFTERSALE_RESULT_LABELS: Record<AftersaleResult, string> = {
  RETURN_AND_REFUND: '退货并退款',
  REFUND_ONLY: '仅退款',
  EXCHANGE: '换货',
}

/** 两种退款要动钱，用警示色；换货不动钱，用信息色。 */
export const AFTERSALE_RESULT_TONES: Record<AftersaleResult, TagTone> = {
  RETURN_AND_REFUND: 'warning',
  REFUND_ONLY: 'warning',
  EXCHANGE: 'info',
}

/**
 * 完成售后时的三种结果。文案直接说明「钱会不会动、占不占退款额度」：
 * 退款额度一旦用掉，同一订单项就不能再退（81004），换货不占额度。
 */
export const AFTERSALE_RESULT_OPTIONS: readonly {
  value: AftersaleResult
  label: string
  hint: string
}[] = [
  {
    value: 'RETURN_AND_REFUND',
    label: AFTERSALE_RESULT_LABELS.RETURN_AND_REFUND,
    hint: '按订单项「单价 × 数量」全额退款，会占用该订单项唯一的退款额度。',
  },
  {
    value: 'REFUND_ONLY',
    label: AFTERSALE_RESULT_LABELS.REFUND_ONLY,
    hint: '只退钱、不要求寄回，同样占用该订单项唯一的退款额度。',
  },
  {
    value: 'EXCHANGE',
    label: AFTERSALE_RESULT_LABELS.EXCHANGE,
    hint: '只换货、不退款，不占用退款额度，买家之后仍可再申请。',
  },
]
