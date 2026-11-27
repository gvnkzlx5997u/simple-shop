import { get, post } from './http'
import type {
  OrderData,
  OrderItemsData,
  OrderPaymentData,
  OrderPlacedData,
  OrderStatus,
  OrderStatusData,
  PageData,
  PaymentChannel,
  PlaceOrderItem,
} from './types'

/**
 * 买家订单接口，全部需要登录令牌（`/api/buyer/orders`）。
 *
 * 订单归属由令牌决定，请求里不传买家 id；别人的订单一律按「不存在」返回（60001），
 * 这是后端的口径，前端不要试图猜。
 */

/**
 * 提交订单。
 * 只传地址与明细数量；金额由服务端按商品价快照计算，前端传了也不会被采纳。
 * 同单内同一商品出现两次会被整单拒绝，购物车行天然唯一，不会触发。
 */
export function placeOrder(
  addressId: string,
  items: PlaceOrderItem[],
): Promise<OrderPlacedData> {
  return post<OrderPlacedData>('/buyer/orders', { address_id: addressId, items })
}

/**
 * 我的订单：全部状态、按下单时间倒序、分页。
 *
 * ⚠️ `status` **只接受单个枚举值**（后端是 `OrderStatus.valueOf`，非法值回 50002）。
 * 所以「进行中」这种跨多态的组合筛不出来，只能按单状态查或查全部。
 */
export function fetchOrders(params?: {
  status?: OrderStatus
  page?: number
  page_size?: number
}): Promise<PageData<OrderData>> {
  return get<PageData<OrderData>>('/buyer/orders', params)
}

/** 订单头。**不含明细**，要展示商品得再调 `fetchOrderItems`。 */
export function fetchOrder(orderId: string): Promise<OrderData> {
  return get<OrderData>('/buyer/orders/' + encodeURIComponent(orderId))
}

/** 订单明细，不分页。 */
export function fetchOrderItems(orderId: string): Promise<OrderItemsData> {
  return get<OrderItemsData>('/buyer/orders/' + encodeURIComponent(orderId) + '/items')
}

/**
 * 支付。只有待支付且未超时的订单可付；渠道失败（83003）不改变订单，可重试。
 * 返回的 `redirect_url` 只有真实渠道需要跳转时才有值，Mock 恒为空。
 */
export function payOrder(orderId: string, channel: PaymentChannel): Promise<OrderPaymentData> {
  return post<OrderPaymentData>(
    '/buyer/orders/' + encodeURIComponent(orderId) + '/pay',
    { payment_channel: channel },
  )
}

/**
 * 确认收货：订单进入交易完成的唯一方式，成交扣库存发生在这一步。
 * 在售商品任一明细缺货则整单拒绝（60004），订单停在已发货，卖家补货后可重试。
 */
export function confirmReceipt(orderId: string): Promise<OrderStatusData> {
  return post<OrderStatusData>('/buyer/orders/' + encodeURIComponent(orderId) + '/confirm-receipt')
}

/**
 * 取消订单。
 * 待支付 / 已支付 / 已确认 / 备货完成四态可取消（已发货后不行，由后端判，回 60003）。
 * 已支付订单取消会全额原路退款；已经取消过的再调一次返回成功，只退一次。
 */
export function cancelOrder(orderId: string): Promise<OrderStatusData> {
  return post<OrderStatusData>('/buyer/orders/' + encodeURIComponent(orderId) + '/cancel')
}
