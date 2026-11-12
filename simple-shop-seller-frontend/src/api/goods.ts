import { request } from './http'
import type {
  GoodsDetailData,
  GoodsPublishRequest,
  GoodsStatus,
  GoodsSummaryData,
  OffShelfReason,
  PageResponse,
} from './types'

const GOODS_PATH = '/seller/goods'

export interface GoodsListQuery {
  status?: GoodsStatus
  /** 只能与 status=OFF_SHELF 组合；带原因却不筛已下架会被后端按参数错误（50002）拒绝。 */
  off_shelf_reason?: OffShelfReason
  category_id?: string
  keyword?: string
  page?: number
  page_size?: number
}

/** 商品列表；按创建时间倒序，支持状态/下架原因/类别/关键字筛选；page 从 1 起。 */
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

/**
 * 修改库存。在售改成 0 触发库存耗尽下架（20020：有未完成订单时被拒）；
 * 库存耗尽/已售下架的商品改成大于 0 保存即自动上架；手动下架的商品库存只读（20023）。
 */
export function updateGoodsStock(id: string, stock: number): Promise<GoodsDetailData> {
  return request<GoodsDetailData>({
    method: 'put',
    url: GOODS_PATH + '/' + encodeURIComponent(id) + '/stock',
    data: { stock },
  })
}

/** 改挂类别；所有状态都能改挂。目标必须是二级类别（20002）。 */
export function updateGoodsCategory(id: string, categoryId: string): Promise<GoodsDetailData> {
  return request<GoodsDetailData>({
    method: 'put',
    url: GOODS_PATH + '/' + encodeURIComponent(id) + '/category',
    data: { category_id: categoryId },
  })
}

/**
 * 手动下架。手动下架是终态：没有重新上架接口，库存变为只读、类别仍可改挂。
 * 有未完成订单（待确认至已发货）时被拒 20020，非在售状态被拒 20019。
 */
export function takeGoodsOffShelf(id: string): Promise<GoodsDetailData> {
  return request<GoodsDetailData>({
    method: 'post',
    url: GOODS_PATH + '/' + encodeURIComponent(id) + '/off-shelf',
  })
}
