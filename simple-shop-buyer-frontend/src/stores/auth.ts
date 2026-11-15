import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import * as api from '../api/buyer'
import { onUnauthorized } from '../api/http'
import { clearToken, getToken, saveToken } from '../api/token'
import type { LoginRequest, RegisterRequest } from '../api/types'
import { clearCartLocal } from '../utils/cartStorage'

/**
 * 登录态。
 *
 * 后端没有「查当前登录态」的接口，所以刷新页面后只能靠 localStorage 里有没有 token
 * 判断已登录；令牌真失效时由第一个业务请求返回 401 / code 10002 触发统一跳登录。
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
    // 先按当前用户名清掉购物车本地状态：数量与勾选只存本地，换账号不能串数据（D3-1）。
    if (username.value) {
      clearCartLocal(username.value)
    }
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
