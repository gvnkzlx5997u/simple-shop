import { request } from './http'
import type { LoginData, LoginRequest } from './types'

const SESSION_PATH = '/seller/session'

/** I11-01 登录。 */
export function login(payload: LoginRequest): Promise<LoginData> {
  return request<LoginData>({ method: 'post', url: SESSION_PATH, data: payload })
}

/** I11-02 退出登录；服务端幂等，token 无效也返回成功。 */
export function logout(): Promise<void> {
  return request<null>({ method: 'delete', url: SESSION_PATH }).then(() => undefined)
}
