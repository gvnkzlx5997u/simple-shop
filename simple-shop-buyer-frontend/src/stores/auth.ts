import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import * as api from '../api/buyer'
import { ApiError, onUnauthorized } from '../api/http'
import { clearToken, getToken, saveToken } from '../api/token'
import { ApiCode } from '../api/types'
import type { LoginRequest, RegisterRequest } from '../api/types'

/**
 * 登录态。
 *
 * 说明：后端没有"查当前登录态"的接口（见交接文档 4.2，买家端只有 6 个接口），
 * 所以刷新页面后只能靠 localStorage 里有没有 token 判断已登录；
 * 令牌真失效时由第一个请求返回 401（code 10002）触发统一跳登录。
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getToken())
  const username = ref<string | null>(localStorage.getItem('simple-shop.buyer.username'))

  const isLoggedIn = computed(() => !!token.value)

  function setSession(t: string, expireAt: string, name: string): void {
    saveToken(t, expireAt)
    localStorage.setItem('simple-shop.buyer.username', name)
    token.value = t
    username.value = name
  }

  function clearSession(): void {
    clearToken()
    localStorage.removeItem('simple-shop.buyer.username')
    token.value = null
    username.value = null
  }

  async function login(payload: LoginRequest): Promise<void> {
    const data = await api.login(payload)
    setSession(data.token, data.expire_at, payload.username)
  }

  async function registerAndLogin(payload: RegisterRequest): Promise<void> {
    await api.register(payload)
    // 注册接口不返回令牌，按交接文档需再登录一次。
    await login({ username: payload.username, password: payload.password })
  }

  async function logout(): Promise<void> {
    try {
      await api.logout()
    } catch {
      // 令牌已失效时退出接口也会报错，本地清掉即可。
    }
    clearSession()
  }

  // 401 / code 10002 统一清会话；跳转由 main.ts 里注册的回调负责。
  onUnauthorized(() => {
    clearSession()
  })

  return { token, username, isLoggedIn, login, registerAndLogin, logout, clearSession }
})

/** 把 ApiError 翻译成界面能显示的中文；后端 message 一律不直接展示。 */
export function describeError(err: unknown): string {
  if (err instanceof ApiError) {
    switch (err.code) {
      case ApiCode.LOGIN_FAILED:
        return '用户名或密码不正确'
      case ApiCode.SESSION_INVALID:
        return '登录已失效，请重新登录'
      case ApiCode.TOO_MANY_REQUESTS:
        return '操作太频繁了，请稍后再试'
      case ApiCode.PARAM_INVALID:
        return '填写的内容不符合要求'
      case 404:
        return '商品不存在或已下架'
      default:
        return '操作失败，请稍后再试'
    }
  }
  return '网络异常，请检查后重试'
}
