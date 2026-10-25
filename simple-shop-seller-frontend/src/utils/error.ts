import { ApiError, CLIENT_ERROR } from '../api/http'
import { apiCodeMessage } from '../constants/messages'

/** 把接口异常翻译成可展示的文案。 */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === CLIENT_ERROR.NETWORK) {
      return '无法连接服务器，请检查后端是否已启动'
    }
    if (error.code === CLIENT_ERROR.UNEXPECTED) {
      return '服务返回了无法识别的响应'
    }
    return apiCodeMessage(error.code)
  }
  return '操作失败，请稍后重试'
}
