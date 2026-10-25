import { request } from './http'
import type { GoodsHistoryDetailData, GoodsHistorySummaryData, PageResponse } from './types'

const HISTORY_PATH = '/seller/history/goods'

/** 历史商品列表；按归档时间倒序分页，不支持筛选。 */
export function listHistory(
  page = 1,
  pageSize = 10,
): Promise<PageResponse<GoodsHistorySummaryData>> {
  return request<PageResponse<GoodsHistorySummaryData>>({
    method: 'get',
    url: HISTORY_PATH,
    params: { page, page_size: pageSize },
  })
}

/** 历史商品详情（归档快照 + 意向名单）；不存在返回 40001 + HTTP 404。 */
export function getHistoryDetail(id: string): Promise<GoodsHistoryDetailData> {
  return request<GoodsHistoryDetailData>({
    method: 'get',
    url: HISTORY_PATH + '/' + encodeURIComponent(id),
  })
}
