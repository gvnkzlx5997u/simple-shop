import { request } from './http'
import type { GoodsData, ImageData, PublishGoodsRequest, PublishResultData } from './types'

const PRODUCT_PATH = '/seller/product'

/** I11-04 查当前商品；无商品时 data 为 null（不是错误）。 */
export function getCurrentGoods(): Promise<GoodsData | null> {
  return request<GoodsData | null>({ method: 'get', url: PRODUCT_PATH })
}

/** I11-05 上传图片；multipart 字段名必须是 file。 */
export function uploadImage(file: File): Promise<ImageData> {
  const form = new FormData()
  form.append('file', file)
  return request<ImageData>({ method: 'post', url: PRODUCT_PATH + '/image', data: form })
}

/** I11-06 发布商品；已有在售或冻结商品时返回 20001。 */
export function publishGoods(payload: PublishGoodsRequest): Promise<PublishResultData> {
  return request<PublishResultData>({ method: 'post', url: PRODUCT_PATH, data: payload })
}

/** I11-07 手动冻结当前商品。 */
export function freezeGoods(): Promise<GoodsData> {
  return request<GoodsData>({ method: 'post', url: PRODUCT_PATH + '/freeze' })
}

/** I11-08 手动解冻当前商品；交易冻结会被服务端以 20005 拒绝。 */
export function unfreezeGoods(): Promise<GoodsData> {
  return request<GoodsData>({ method: 'post', url: PRODUCT_PATH + '/unfreeze' })
}

/** I11-09 手动下架当前商品（同事务归档）；队列非空时返回 20006。 */
export function takeGoodsOffline(): Promise<void> {
  return request<null>({ method: 'post', url: PRODUCT_PATH + '/offline' }).then(() => undefined)
}
