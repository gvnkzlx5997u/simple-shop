/**
 * 后端接口数据契约（与 Java DTO 的 JSON 字段逐字对应）。
 * 字段保持后端返回的 snake_case，不做驼峰转换。
 */

/** 统一响应结构：成功 code = 0；业务拒绝为 HTTP 200 + 非零业务码。 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/** 买家端会用到的业务码（与后端 ErrorCode 一致）。 */
export const ApiCode = {
  OK: 0,
  LOGIN_FAILED: 10001,
  SESSION_INVALID: 10002,
  TOO_MANY_REQUESTS: 50001,
  PARAM_INVALID: 50002,
} as const

/** 分页响应。page 从 1 起，page_size 默认 10。 */
export interface PageData<T> {
  items: T[]
  page: number
  page_size: number
  total: number
}

/** 类别引用（商品所属的一级/二级类别）。 */
export interface CategoryRef {
  l1_id: string
  l1_name: string
  l2_id: string
  l2_name: string
}

/** 类别树节点：一级带 children（二级）。 */
export interface CategoryNode {
  id: string
  name: string
  children: CategoryNode[]
}

/** 商品图片。 */
export interface GoodsImage {
  url: string
  is_main: boolean
  sort_no: number
}

/** 描述块：type 为 text 时读 text，为 image/video 时读 url。 */
export interface DescriptionBlock {
  type: string
  text: string
  url: string
}

/** 列表项。 */
export interface GoodsSummary {
  id: string
  name: string
  price: number
  stock: number
  status: string
  main_image_url: string
  category: CategoryRef
  create_at: string
  update_at: string
}

/**
 * 商品详情。
 * purchasable 为 true 表示可以提交购买意向（在售且有库存）；
 * 交易中的商品也能打开，但 purchasable 为 false。
 */
export interface GoodsDetail {
  id: string
  name: string
  price: number
  stock: number
  status: string
  category: CategoryRef
  main_image_url: string
  images: GoodsImage[]
  description: DescriptionBlock[]
  create_at: string
  update_at: string
  purchasable: boolean
}

/** 我的历史条目：只有四个字段，不含价格与图片。 */
export interface BuyerHistoryEntry {
  goods_name: string
  create_at: string
  result: string
  finish_at: string
}

export interface RegisterRequest {
  username: string
  password: string
  phone: string
  default_location?: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginData {
  token: string
  expire_at: string
}
