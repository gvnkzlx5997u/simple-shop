import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as buyerApi from '../../src/api/buyer'
import { getToken } from '../../src/api/token'
import { useAuthStore } from '../../src/stores/auth'
import { loadCartLocal, saveCartLocal } from '../../src/utils/cartStorage'

vi.mock('../../src/api/buyer', () => ({
  login: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
}))

let auth: ReturnType<typeof useAuthStore>

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
  auth = useAuthStore()
  vi.mocked(buyerApi.login).mockResolvedValue({ token: 't', expire_at: '2026-12-01T00:00:00Z' })
  vi.mocked(buyerApi.register).mockResolvedValue(null)
  vi.mocked(buyerApi.logout).mockResolvedValue(null)
})

describe('login / setSession', () => {
  it('登录写入令牌与用户名并持久化', async () => {
    await auth.login({ username: 'alice', password: 'p' })
    expect(auth.token).toBe('t')
    expect(auth.username).toBe('alice')
    expect(auth.isLoggedIn).toBe(true)
    expect(getToken()).toBe('t')
    expect(localStorage.getItem('simple-shop.buyer.username')).toBe('alice')
  })
})

describe('clearSession', () => {
  it('只清当前用户的购物车本地键，不动别人', async () => {
    saveCartLocal('alice', { g1: { quantity: 1, checked: true } })
    saveCartLocal('bob', { g2: { quantity: 1, checked: true } })
    await auth.login({ username: 'alice', password: 'p' })
    auth.clearSession()
    expect(loadCartLocal('alice')).toEqual({})
    expect(Object.keys(loadCartLocal('bob'))).toEqual(['g2'])
    expect(auth.token).toBeNull()
    expect(auth.username).toBeNull()
    expect(auth.isLoggedIn).toBe(false)
    expect(getToken()).toBeNull()
  })
})

describe('logout', () => {
  it('接口失败也清本地会话', async () => {
    await auth.login({ username: 'alice', password: 'p' })
    vi.mocked(buyerApi.logout).mockRejectedValueOnce(new Error('network'))
    await auth.logout()
    expect(auth.isLoggedIn).toBe(false)
    expect(getToken()).toBeNull()
  })
})

describe('registerAndLogin', () => {
  it('注册后再登录一次（注册不返回令牌）', async () => {
    vi.mocked(buyerApi.login).mockResolvedValue({ token: 't2', expire_at: '2026-12-01T00:00:00Z' })
    const payload = { username: 'bob', password: 'p', phone: '13800138000' }
    await auth.registerAndLogin(payload)
    expect(buyerApi.register).toHaveBeenCalledWith(payload)
    expect(buyerApi.login).toHaveBeenCalledWith({ username: 'bob', password: 'p' })
    expect(auth.token).toBe('t2')
    expect(auth.username).toBe('bob')
  })
})