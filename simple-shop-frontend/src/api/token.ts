/** 会话令牌的本地持久化；令牌是 Bearer 凭据，不得写入日志。 */
const TOKEN_KEY = 'simple-shop.seller.token'
const EXPIRE_AT_KEY = 'simple-shop.seller.expire-at'

// 测试等非浏览器环境没有 localStorage，降级为不持久化。
const storage = typeof localStorage === 'undefined' ? null : localStorage

export function getToken(): string | null {
  return storage?.getItem(TOKEN_KEY) ?? null
}

export function getExpireAt(): string | null {
  return storage?.getItem(EXPIRE_AT_KEY) ?? null
}

export function saveToken(token: string, expireAt: string): void {
  storage?.setItem(TOKEN_KEY, token)
  storage?.setItem(EXPIRE_AT_KEY, expireAt)
}

export function clearToken(): void {
  storage?.removeItem(TOKEN_KEY)
  storage?.removeItem(EXPIRE_AT_KEY)
}
