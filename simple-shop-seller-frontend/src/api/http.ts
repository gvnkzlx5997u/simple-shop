import axios from 'axios'
import type { AxiosRequestConfig, AxiosResponse } from 'axios'

import { clearToken, getToken } from './token'
import { ApiCode } from './types'
import type { ApiResponse } from './types'

/** 非契约码：仅用于传输层失败与响应形状异常。 */
export const CLIENT_ERROR = {
  NETWORK: -1,
  UNEXPECTED: -2,
} as const

/**
 * 统一接口错误。
 * code 为后端业务码；message 是给日志与排查用的 ASCII 英文，界面不得直接展示。
 */
export class ApiError extends Error {
  readonly code: number
  readonly httpStatus?: number

  constructor(code: number, message: string, httpStatus?: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.httpStatus = httpStatus
  }
}

const http = axios.create({
  // 开发期默认 /api，由 Vite 代理转发到后端；部署时用 VITE_API_BASE_URL 覆盖。
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: 15000,
})

/** 允许运行时或测试替换后端地址。 */
export function setBaseUrl(baseURL: string): void {
  http.defaults.baseURL = baseURL
}

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.set('Authorization', 'Bearer ' + token)
  }
  return config
})

const unauthorizedHandlers = new Set<() => void>()

/** 注册会话失效回调；可注册多个（登录状态同步、跳转登录页）。 */
export function onUnauthorized(handler: () => void): void {
  unauthorizedHandlers.add(handler)
}

function fail(code: number, message: string, httpStatus?: number): never {
  if (code === ApiCode.SESSION_INVALID) {
    clearToken()
    unauthorizedHandlers.forEach((handler) => handler())
  }
  throw new ApiError(code, message, httpStatus)
}

/**
 * 发起请求并拆掉统一响应外壳。
 * 成功返回 data；业务拒绝（HTTP 200 + 非零 code）与 4xx/5xx 一律抛 ApiError。
 * 注意：后端把业务规则拒绝当作正常分支，其 HTTP 状态是 200。
 */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response: AxiosResponse<ApiResponse<T>> = await http
    .request<ApiResponse<T>>(config)
    .catch((error: unknown) => {
      if (axios.isAxiosError(error)) {
        const body = error.response?.data as ApiResponse<unknown> | undefined
        if (body && typeof body.code === 'number') {
          fail(body.code, body.message, error.response?.status)
        }
        throw new ApiError(CLIENT_ERROR.NETWORK, error.message)
      }
      throw new ApiError(CLIENT_ERROR.UNEXPECTED, String(error))
    })

  const body = response.data
  if (body && typeof body.code === 'number') {
    if (body.code === ApiCode.OK) {
      return body.data
    }
    fail(body.code, body.message, response.status)
  }
  throw new ApiError(CLIENT_ERROR.UNEXPECTED, 'unexpected response shape', response.status)
}
