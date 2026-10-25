import { request } from './http'
import type { ChangePasswordRequest } from './types'

const PASSWORD_PATH = '/seller/auth/password'

/** 修改密码；成功后后端吊销该账号全部会话（含本次请求的 token）。 */
export function changePassword(payload: ChangePasswordRequest): Promise<void> {
  return request<null>({ method: 'put', url: PASSWORD_PATH, data: payload }).then(() => undefined)
}
