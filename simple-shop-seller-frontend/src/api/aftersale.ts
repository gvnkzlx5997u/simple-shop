import { request } from './http'
import type {
  AftersaleCompleteRequest,
  AftersaleData,
  AftersaleDetailData,
  AftersaleResult,
  AftersaleStatus,
  PageResponse,
} from './types'

const AFTERSALES_PATH = '/seller/aftersales'

/**
 * 卖家售后。买家只能发起与查看，改状态（完成 / 取消）只在这里。
 *
 * 与买家端的差别只有路径与动作：卖家多「完成」（登记处理结果）与「取消」（同拒绝），
 * 少了申请与上传凭证。
 */
export interface AftersaleListQuery {
  status?: AftersaleStatus
  /** 按订单号搜索；订单号不存在时后端返回空页而不是 404。 */
  order_no?: string
  page?: number
  page_size?: number
}

/** 售后列表：按申请时间倒序，分页；两个条件可任意组合。page 从 1 起。 */
export function listSellerAftersales(
  query: AftersaleListQuery = {},
): Promise<PageResponse<AftersaleData>> {
  return request<PageResponse<AftersaleData>>({
    method: 'get',
    url: AFTERSALES_PATH,
    params: query,
  })
}

/** 售后详情：摘要 + 买家描述 + 凭证图。不存在返回 81001。 */
export function getSellerAftersale(id: string): Promise<AftersaleDetailData> {
  return request<AftersaleDetailData>({
    method: 'get',
    url: AFTERSALES_PATH + '/' + encodeURIComponent(id),
  })
}

/**
 * 完成售后并登记处理结果。
 *
 * EXCHANGE 不退款；退货退款 / 仅退款由后端按「订单项单价 × 数量」写退款金额，
 * 同一订单项只能退一次款（81004），换货不占额度。完成后是终态，不能再改。
 */
export function completeSellerAftersale(
  id: string,
  result: AftersaleResult,
): Promise<AftersaleData> {
  const body: AftersaleCompleteRequest = { result }
  return request<AftersaleData>({
    method: 'post',
    url: AFTERSALES_PATH + '/' + encodeURIComponent(id) + '/complete',
    data: body,
  })
}

/**
 * 取消售后，与「拒绝」同义：买家可重新申请，生成新单号。
 * 已取消时重复调用返回成功（幂等），已完成返回 81002。
 */
export function cancelSellerAftersale(id: string): Promise<AftersaleData> {
  return request<AftersaleData>({
    method: 'post',
    url: AFTERSALES_PATH + '/' + encodeURIComponent(id) + '/cancel',
  })
}
