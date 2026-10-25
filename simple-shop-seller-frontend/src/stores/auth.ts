import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { onUnauthorized } from '@/api/http'
import { login as loginApi, logout as logoutApi } from '@/api/session'
import { changePassword as changePasswordApi } from '@/api/account'
import { clearToken, getExpireAt, getToken, saveToken } from '@/api/token'

/** 卖家登录状态；令牌持久化在 localStorage，刷新后仍可复用（服务端会话 30 分钟滑动过期）。 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getToken())
  const expireAt = ref<string | null>(getExpireAt())
  const isAuthenticated = computed(() => token.value !== null)

  function applySession(nextToken: string, nextExpireAt: string): void {
    token.value = nextToken
    expireAt.value = nextExpireAt
    saveToken(nextToken, nextExpireAt)
  }

  function clearSession(): void {
    token.value = null
    expireAt.value = null
    clearToken()
  }

  /** 登录失败抛 ApiError：10001 = 账号或口令错误。 */
  async function login(account: string, password: string): Promise<void> {
    const data = await loginApi({ account, password })
    applySession(data.token, data.expire_at)
  }

  /** 退出登录；服务端幂等，本地状态无论如何都清空。 */
  async function logout(): Promise<void> {
    try {
      await logoutApi()
    } finally {
      clearSession()
    }
  }

  /** 改密成功后全部会话立即失效（含本次），因此本地状态必须同步清空。 */
  async function changePassword(oldPassword: string, newPassword: string): Promise<void> {
    await changePasswordApi({ old_password: oldPassword, new_password: newPassword })
    clearSession()
  }

  // 任意接口收到 10002 时同步清空本地登录状态。
  onUnauthorized(clearSession)

  return { token, expireAt, isAuthenticated, login, logout, changePassword, clearSession }
})
