import { request } from './http'
import type {
  GoodsDetailData,
  GoodsPublishRequest,
  GoodsStatus,
  GoodsSummaryData,
  PageResponse,
} from './types'

const GOODS_PATH = '/seller/goods'

export interface GoodsListQuery {
  status?: GoodsStatus
  category_id?: string
  keyword?: string
  page?: number
  page_size?: number
}

/** 商品列表；按创建时间倒序，支持状态/类别/关键字筛选；page 从 1 起。 */
export function listGoods(query: GoodsListQuery = {}): Promise<PageResponse<GoodsSummaryData>> {
  return request<PageResponse<GoodsSummaryData>>({
    method: 'get',
    url: GOODS_PATH,
    params: query,
  })
}

/** 发布商品；返回发布后的商品详情。 */
export function publishGoods(payload: GoodsPublishRequest): Promise<GoodsDetailData> {
  return request<GoodsDetailData>({ method: 'post', url: GOODS_PATH, data: payload })
}

/** 商品详情；商品不存在返回 20011 + HTTP 404。 */
export function getGoods(id: string): Promise<GoodsDetailData> {
  return request<GoodsDetailData>({ method: 'get', url: GOODS_PATH + '/' + encodeURIComponent(id) })
}

/** 修改库存；交易中（冻结）被拒 20021。OFF_ZERO 商品改为 >0 即自动重新上架。 */
export function updateGoodsStock(id: string, stock: number): Promise<GoodsDetailData> {
  return request<GoodsDetailData>({
    method: 'put',
    url: GOODS_PATH + '/' + encodeURIComponent(id) + '/stock',
    data: { stock },
  })
}

/** 改挂类别；交易中被拒 20021；目标必须是二级类别（20002）。 */
export function updateGoodsCategory(id: string, categoryId: string): Promise<GoodsDetailData> {
  return request<GoodsDetailData>({
    method: 'put',
    url: GOODS_PATH + '/' + encodeURIComponent(id) + '/category',
    data: { category_id: categoryId },
  })
}

/** 手动下架；队列非空被拒 20022。下架商品留在当前列表（OFF_MANUAL），不进历史。 */
export function takeGoodsOffShelf(id: string): Promise<GoodsDetailData> {
  return request<GoodsDetailData>({
    method: 'post',
    url: GOODS_PATH + '/' + encodeURIComponent(id) + '/off-shelf',
  })
}

/** 重新上架（仅手动下架的商品）；不带显式确认会被拒 20028。 */
export function reListGoods(id: string): Promise<GoodsDetailData> {
  return request<GoodsDetailData>({
    method: 'post',
    url: GOODS_PATH + '/' + encodeURIComponent(id) + '/re-list',
    data: { confirm: true },
  })
}
