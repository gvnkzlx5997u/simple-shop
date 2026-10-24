import { del, get, post } from './http'
import type {
  BuyerHistoryEntry,
  CategoryNode,
  GoodsDetail,
  GoodsSummary,
  IntentionAccepted,
  LoginData,
  LoginRequest,
  PageData,
  RegisterRequest,
} from './types'

/**
 * 买家端只涉及 6 个接口，全部集中在 `simple-shop-doc/阶段二/前端交接文档.md` 4.2 节。
 * 浏览类走 /api/public/**（免令牌），其余走 /api/buyer/**（需登录）。
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

/** 商品详情：在售、交易中可见；下架/归档返回 404。 */
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

/**
 * 提交购买意向：请求体只有 goods_id，不需要任何表单。
 * 响应只含意向 id 与创建时间，**不含排队位次**，界面不得展示位置。
 */
export function submitIntention(goodsId: string): Promise<IntentionAccepted> {
  return post<IntentionAccepted>('/buyer/intentions', { goods_id: goodsId })
}

/** 我的历史：只含已结束的记录，按下单时间倒序。 */
export function fetchHistory(page = 1, pageSize = 10): Promise<PageData<BuyerHistoryEntry>> {
  return get<PageData<BuyerHistoryEntry>>('/buyer/history', { page, page_size: pageSize })
}
