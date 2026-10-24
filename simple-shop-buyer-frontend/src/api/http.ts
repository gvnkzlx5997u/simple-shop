import axios from 'axios'
import type { AxiosResponse } from 'axios'

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
 * code 为后端业务码；message 是给日志排查用的英文，界面不得直接展示。
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

/** 注册会话失效回调（如跳转登录页），可注册多个。 */
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

/** 解包统一响应体：code=0 返回 data，否则抛 ApiError。 */
function unwrap<T>(res: AxiosResponse<ApiResponse<T>>): T {
  const body = res.data
  if (body && typeof body.code === 'number') {
    if (body.code === ApiCode.OK) {
      return body.data
    }
    fail(body.code, body.message || 'biz error', res.status)
  }
  // 后端异常路径可能不返回统一体（如 401/500 的裸响应）。
  fail(CLIENT_ERROR.UNEXPECTED, 'unexpected response shape', res.status)
}

/** 把任何异常统一成 ApiError，调用方只处理一种错误类型。 */
function toApiError(err: unknown): ApiError {
  if (err instanceof ApiError) return err
  const e = err as { response?: { status: number; data?: ApiResponse<unknown> }; message?: string }
  if (e?.response) {
    const body = e.response.data
    if (body && typeof body.code === 'number') {
      return new ApiError(body.code, body.message || 'biz error', e.response.status)
    }
    // 401 裸响应：按会话失效处理。
    if (e.response.status === 401) {
      return new ApiError(ApiCode.SESSION_INVALID, 'session invalid', 401)
    }
    return new ApiError(CLIENT_ERROR.UNEXPECTED, 'http ' + e.response.status, e.response.status)
  }
  return new ApiError(CLIENT_ERROR.NETWORK, e?.message || 'network error')
}

export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  try {
    const res = await http.get<ApiResponse<T>>(url, { params })
    return unwrap(res)
  } catch (err) {
    throw toApiError(err)
  }
}

export async function post<T>(url: string, body?: unknown): Promise<T> {
  try {
    const res = await http.post<ApiResponse<T>>(url, body)
    return unwrap(res)
  } catch (err) {
    throw toApiError(err)
  }
}

export async function del<T>(url: string): Promise<T> {
  try {
    const res = await http.delete<ApiResponse<T>>(url)
    return unwrap(res)
  } catch (err) {
    throw toApiError(err)
  }
}

export default http
