import { get, post } from './http'
import type {
  AftersaleAppliedData,
  AftersaleApplyRequest,
  AftersaleData,
  AftersaleDetailData,
  AftersaleStatus,
  PageData,
} from './types'

/**
 * 买家售后。只能发起与查看，改状态（完成 / 取消）只在卖家端。
 *
 * 申请按 `order_item_id` 定位，不用 `goods_id`：同一商品可能出现在多个订单里。
 */

export function applyAftersale(payload: AftersaleApplyRequest): Promise<AftersaleAppliedData> {
  return post<AftersaleAppliedData>('/buyer/aftersales', payload)
}

/**
 * 我的售后：按申请时间倒序、分页。
 * `status` 只接受单个枚举值；`order_id` 用于订单详情里的售后区块（那里不内嵌售后）。
 */
export function fetchAftersales(params?: {
  status?: AftersaleStatus
  order_id?: string
  page?: number
  page_size?: number
}): Promise<PageData<AftersaleData>> {
  return get<PageData<AftersaleData>>('/buyer/aftersales', params)
}

/** 售后详情：摘要之外另给描述与图片。 */
export function fetchAftersale(id: string): Promise<AftersaleDetailData> {
  return get<AftersaleDetailData>('/buyer/aftersales/' + encodeURIComponent(id))
}