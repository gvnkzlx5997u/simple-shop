import { request } from './http'
import type {
  Disposal,
  IntentionEntryData,
  IntentionStatus,
  PageResponse,
  TradeResultData,
} from './types'

const INTENTIONS_PATH = '/seller/intentions'

export interface IntentionListQuery {
  status?: IntentionStatus
  page?: number
  page_size?: number
}

/**
 * 某商品的意向名单；按 queue_order 升序（队首 = 排在最前的 QUEUED 行）。
 * 支持按状态筛选；page 从 1 起。
 */
export function listIntentions(
  goodsId: string,
  query: IntentionListQuery = {},
): Promise<PageResponse<IntentionEntryData>> {
  return request<PageResponse<IntentionEntryData>>({
    method: 'get',
    url: '/seller/goods/' + encodeURIComponent(goodsId) + '/intentions',
    params: query,
  })
}

/** 对队首意向进入交易；非队首 30002，已有交易中 30004。返回进入交易后的意向。 */
export function enterTrade(intentionId: string): Promise<IntentionEntryData> {
  return request<IntentionEntryData>({
    method: 'post',
    url: INTENTIONS_PATH + '/' + encodeURIComponent(intentionId) + '/trade',
  })
}

/**
 * 标记交易成功（扣 1 件库存）。
 * goods_status = ARCHIVED 表示售罄归档，商品转入历史记录；ON_SALE 表示库存有余回在售。
 */
export function markTradeSuccess(intentionId: string): Promise<TradeResultData> {
  return request<TradeResultData>({
    method: 'post',
    url: INTENTIONS_PATH + '/' + encodeURIComponent(intentionId) + '/success',
  })
}

/**
 * 标记交易失败；disposal 取 VOIDED（作废，终态）或 REQUEUED（重排队尾，下单时间不变）。
 * 商品回在售、库存不变。
 */
export function markTradeFailure(
  intentionId: string,
  disposal: Disposal,
): Promise<TradeResultData> {
  return request<TradeResultData>({
    method: 'post',
    url: INTENTIONS_PATH + '/' + encodeURIComponent(intentionId) + '/failure',
    data: { disposal },
  })
}
