import { request } from './http'
import type { HistoryGoodsDetailData, HistoryPageData } from './types'

const PRODUCTS_PATH = '/seller/history/products'

/** I11-15 历史商品列表；按 trade_end 倒序，不支持筛选。 */
export function listHistory(page = 1, pageSize = 10): Promise<HistoryPageData> {
  return request<HistoryPageData>({
    method: 'get',
    url: PRODUCTS_PATH,
    params: { page, page_size: pageSize },
  })
}

/** I11-16 历史商品详情；商品不存在时返回 20011 + HTTP 404。 */
export function getHistoryDetail(id: string): Promise<HistoryGoodsDetailData> {
  return request<HistoryGoodsDetailData>({
    method: 'get',
    url: PRODUCTS_PATH + '/' + encodeURIComponent(id),
  })
}
