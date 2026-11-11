import { del, get, post } from './http'
import type { CartListData, MoveToFavoritesData } from './types'

/**
 * 买家购物车接口，全部需要登录令牌（`/api/buyer/cart`）。
 *
 * 服务端只记「买家 + 商品 + 加购时间」，**数量与勾选不在服务端**，
 * 所以这里没有任何「改数量」的请求——数量是纯前端状态。
 */

/** 加入购物车。重复加购不新增行（后端幂等）；商品不在售返回 70001。 */
export function addToCart(goodsId: string): Promise<null> {
  return post<null>('/buyer/cart', { goods_id: goodsId })
}

/** 购物车列表：不分页，已下架行也返回，`selectable=false` 需由界面拦下。 */
export function fetchCart(): Promise<CartListData> {
  return get<CartListData>('/buyer/cart')
}

/** 移除一件。行不存在也返回成功，因此调用方不必先确认存在。 */
export function removeCartItem(goodsId: string): Promise<null> {
  return del<null>('/buyer/cart/' + encodeURIComponent(goodsId))
}

/** 批量转收藏。含已下架商品时后端整批拒绝（70003），前端应先自行过滤。 */
export function moveToFavorites(goodsIds: string[]): Promise<MoveToFavoritesData> {
  return post<MoveToFavoritesData>('/buyer/cart/move-to-favorites', { goods_ids: goodsIds })
}
