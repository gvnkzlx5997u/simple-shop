import { request } from './http'
import type { AuthTokenData, SellerLoginRequest } from './types'

const SESSION_PATH = '/seller/auth/session'

/** 登录；账号或口令错误返回 10001。 */
export function login(payload: SellerLoginRequest): Promise<AuthTokenData> {
  return request<AuthTokenData>({ method: 'post', url: SESSION_PATH, data: payload })
}

/** 退出登录；服务端幂等，token 无效也返回成功。 */
export function logout(): Promise<void> {
  return request<null>({ method: 'delete', url: SESSION_PATH }).then(() => undefined)
}
