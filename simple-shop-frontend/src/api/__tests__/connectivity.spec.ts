import { afterAll, describe, expect, it } from 'vitest'

import { ApiError, CLIENT_ERROR, setBaseUrl } from '../http'
import { ApiCode } from '../types'
import { login, logout } from '../session'
import { getCurrentGoods } from '../product'

const rawBase = process.env.SMOKE_BASE_URL ?? 'http://localhost:8080'
const BASE_URL = rawBase.endsWith('/') ? rawBase.slice(0, -1) : rawBase
setBaseUrl(BASE_URL + '/api')

const ACCOUNT = process.env.SMOKE_ACCOUNT ?? 'seller'
const PASSWORD = process.env.SMOKE_PASSWORD ?? 'Abcd@1234'

/** 只有传输层失败才算不可达；服务端回了任何业务响应都说明连通。 */
async function backendReachable(): Promise<boolean> {
  try {
    await getCurrentGoods()
    return true
  } catch (error) {
    return error instanceof ApiError && error.code !== CLIENT_ERROR.NETWORK
  }
}

const reachable = await backendReachable()
if (!reachable) {
  console.warn('[connectivity] 后端不可达，跳过连通性用例：' + BASE_URL)
}

describe.skipIf(!reachable)('卖家端接口连通性', () => {
  afterAll(async () => {
    await logout().catch(() => undefined)
  })

  it('未登录访问受保护接口，连通并返回会话失效（10002）', async () => {
    const error = await getCurrentGoods().then(
      () => null,
      (reason: unknown) => reason,
    )
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).code).toBe(ApiCode.SESSION_INVALID)
  })

  it('初始账号可以登录，并能幂等退出', async () => {
    const data = await login({ account: ACCOUNT, password: PASSWORD })
    expect(data.token.length).toBeGreaterThan(20)
    expect(Number.isNaN(Date.parse(data.expire_at))).toBe(false)

    await expect(logout()).resolves.toBeUndefined()
    await expect(logout()).resolves.toBeUndefined()
  })
})
