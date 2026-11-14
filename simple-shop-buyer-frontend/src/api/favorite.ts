import { del, get, post } from './http'
import type { FavoriteItemData, FavoriteStatusData, PageData } from './types'

/**
 * 买家收藏接口，全部需要登录令牌（`/api/buyer/favorites`）。
 *
 * 收藏行只存「买家 + 商品 + 收藏时间」，商品名称价格库存是查询时实时读的，
 * 所以卖家改价改名会直接反映到收藏列表里。
 */

/** 收藏。重复收藏不新增行（后端幂等）；商品不在售返回 70002。 */
export function addFavorite(goodsId: string): Promise<null> {
  return post<null>('/buyer/favorites', { goods_id: goodsId })
}

/** 我的收藏：按收藏时间倒序，已下架行也返回（`detail_available=false`）。 */
export function fetchFavorites(params?: {
  page?: number
  page_size?: number
}): Promise<PageData<FavoriteItemData>> {
  return get<PageData<FavoriteItemData>>('/buyer/favorites', params)
}

/** 单件商品的收藏状态。详情页进来时先问一次，决定按钮显示「收藏」还是「已收藏」。 */
export function fetchFavoriteStatus(goodsId: string): Promise<FavoriteStatusData> {
  return get<FavoriteStatusData>('/buyer/favorites/' + encodeURIComponent(goodsId))
}

/** 取消收藏。不要求商品在售，行不存在也返回成功。 */
export function removeFavorite(goodsId: string): Promise<null> {
  return del<null>('/buyer/favorites/' + encodeURIComponent(goodsId))
}
