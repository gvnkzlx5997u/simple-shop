import { get, post } from './http'
import type {
  OrderData,
  OrderItemsData,
  OrderPlacedData,
  OrderStatus,
  OrderStatusData,
  PageData,
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
 * 只传明细与数量；金额由服务端按商品价快照计算，前端传了也不会被采纳。
 * 同单内同一商品出现两次会被整单拒绝，购物车行天然唯一，不会触发。
 */
export function placeOrder(items: PlaceOrderItem[]): Promise<OrderPlacedData> {
  return post<OrderPlacedData>('/buyer/orders', { items })
}

/**
 * 我的订单：全部状态、按下单时间倒序、分页。
 *
 * ⚠️ `status` **只接受单个枚举值**（后端是 `OrderStatus.valueOf`，非法值回 50002）。
 * 所以「进行中」这种跨四态的组合筛不出来，只能按单状态查或查全部。
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
 * 取消订单。
 * 只有待确认 / 已确认 / 备货完成三种状态可取消（已发货后不行，由后端判，回 60003）。
 * 已经取消过的再调一次返回成功，不报错。
 */
export function cancelOrder(orderId: string): Promise<OrderStatusData> {
  return post<OrderStatusData>('/buyer/orders/' + encodeURIComponent(orderId) + '/cancel')
}
