import { del, get, post } from './http'
import type {
  CategoryNode,
  GoodsDetail,
  GoodsSummary,
  LoginData,
  LoginRequest,
  PageData,
  RegisterRequest,
} from './types'

/**
 * 买家端接口：浏览类走 /api/public/**（免令牌），账号类走 /api/buyer/**（需登录）。
 * 字段名、枚举值与错误码以 simple-shop-doc/阶段三/契约交接.md 与 openapi.json 为准。
 */

/** 商品列表/搜索：仅在售；keyword 与 category_id 可叠加。 */
export function fetchGoods(params: {
  keyword?: string
  category_id?: string
  page?: number
  page_size?: number
}): Promise<PageData<GoodsSummary>> {
  return get<PageData<GoodsSummary>>('/public/goods', params)
}

/** 商品详情：仅在售可见；已下架或不存在返回 404 + 20011。 */
export function fetchGoodsDetail(id: string): Promise<GoodsDetail> {
  return get<GoodsDetail>('/public/goods/' + encodeURIComponent(id))
}

/** 类别树（一级带 children）。 */
export function fetchCategories(): Promise<CategoryNode[]> {
  return get<CategoryNode[]>('/public/categories')
}

/** 注册；成功后不返回令牌，需再调登录。 */
export function register(payload: RegisterRequest): Promise<null> {
  return post<null>('/buyer/auth/register', payload)
}

/** 登录；返回令牌与过期时间。 */
export function login(payload: LoginRequest): Promise<LoginData> {
  return post<LoginData>('/buyer/auth/session', payload)
}

/** 退出登录。 */
export function logout(): Promise<null> {
  return del<null>('/buyer/auth/session')
}
