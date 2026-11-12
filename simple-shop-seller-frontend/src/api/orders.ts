import { request } from './http'
import type {
  OrderData,
  OrderItemsData,
  OrderStatus,
  OrderStatusData,
  PageResponse,
} from './types'

const ORDERS_PATH = '/seller/orders'

/**
 * 订单列表筛选。
 *
 * 「订单管理 / 按客户 / 按商品」在后端只是这三个条件的不同组合，同一份数据结构；
 * 详情一律按 `order_id` 查，与从哪个入口进来无关。
 */
export interface OrderListQuery {
  status?: OrderStatus
  buyer_id?: string
  goods_id?: string
  page?: number
  page_size?: number
}

/** 订单列表：按下单时间倒序；三个条件可任意组合，全空时返回全部订单。page 从 1 起。 */
export function listOrders(query: OrderListQuery = {}): Promise<PageResponse<OrderData>> {
  return request<PageResponse<OrderData>>({
    method: 'get',
    url: ORDERS_PATH,
    params: query,
  })
}

/** 订单详情：只回订单头，不含订单项（明细走 listOrderItems）。不存在返回 60001。 */
export function getOrder(orderId: string): Promise<OrderData> {
  return request<OrderData>({
    method: 'get',
    url: ORDERS_PATH + '/' + encodeURIComponent(orderId),
  })
}

/** 订单项：按订单 ID 单独查，不分页。 */
export function listOrderItems(orderId: string): Promise<OrderItemsData> {
  return request<OrderItemsData>({
    method: 'get',
    url: ORDERS_PATH + '/' + encodeURIComponent(orderId) + '/items',
  })
}

/**
 * 确认订单：待确认 → 已确认。
 * 五个推进动作共用一种返回（只回状态），当前状态不允许时返回 60002。
 */
export function confirmOrder(orderId: string): Promise<OrderStatusData> {
  return request<OrderStatusData>({
    method: 'post',
    url: ORDERS_PATH + '/' + encodeURIComponent(orderId) + '/confirm',
  })
}

/** 标记备货完成：已确认 → 备货完成。只是流程状态，不校验库存。 */
export function prepareOrder(orderId: string): Promise<OrderStatusData> {
  return request<OrderStatusData>({
    method: 'post',
    url: ORDERS_PATH + '/' + encodeURIComponent(orderId) + '/prepare',
  })
}

/** 开始发货：备货完成 → 已发货；此后买家不可取消。 */
export function shipOrder(orderId: string): Promise<OrderStatusData> {
  return request<OrderStatusData>({
    method: 'post',
    url: ORDERS_PATH + '/' + encodeURIComponent(orderId) + '/ship',
  })
}

/**
 * 交易完成：已发货 → 交易完成，按明细数量一次性扣减库存。
 * 任一商品库存不足则整单拒绝（60004），订单停在已发货，不做部分完成。
 */
export function completeOrder(orderId: string): Promise<OrderStatusData> {
  return request<OrderStatusData>({
    method: 'post',
    url: ORDERS_PATH + '/' + encodeURIComponent(orderId) + '/complete',
  })
}

/**
 * 取消订单：交易完成前可取消，终态。
 * 已取消时重复调用返回成功（幂等），交易完成后调用返回 60003。
 */
export function cancelOrder(orderId: string): Promise<OrderStatusData> {
  return request<OrderStatusData>({
    method: 'post',
    url: ORDERS_PATH + '/' + encodeURIComponent(orderId) + '/cancel',
  })
}
