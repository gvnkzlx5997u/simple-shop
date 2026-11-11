import { post } from './http'
import type { OrderPlacedData, PlaceOrderItem } from './types'

/**
 * 订单接口。**本阶段只放下单**——购物车结算要用。
 * 订单列表、详情、明细、取消属于 D2-1，那时把本文件补成完整模块。
 */

/**
 * 提交订单。
 * 只传明细与数量；金额由服务端按商品价快照计算，前端传了也不会被采纳。
 * 同单内同一商品出现两次会被整单拒绝，购物车行天然唯一，不会触发。
 */
export function placeOrder(items: PlaceOrderItem[]): Promise<OrderPlacedData> {
  return post<OrderPlacedData>('/buyer/orders', { items })
}
