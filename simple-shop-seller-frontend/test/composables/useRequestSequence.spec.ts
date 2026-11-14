import { describe, expect, it } from 'vitest'

import { useRequestSequence } from '@/composables/useRequestSequence'

describe('useRequestSequence', () => {
  it('首次 begin 的 token 有效', () => {
    const seq = useRequestSequence()
    expect(seq.isCurrent(seq.begin())).toBe(true)
  })

  it('新 begin 使旧 token 失效', () => {
    const seq = useRequestSequence()
    const old = seq.begin()
    const latest = seq.begin()
    expect(seq.isCurrent(old)).toBe(false)
    expect(seq.isCurrent(latest)).toBe(true)
  })

  it('各实例互不影响', () => {
    const a = useRequestSequence()
    const b = useRequestSequence()
    const token = a.begin()
    b.begin()
    expect(a.isCurrent(token)).toBe(true)
  })
})

/** 模拟视图里的 guarded load：请求可手动 resolve/reject，守卫写法与视图一致（先守卫再写状态）。 */
function createGuardedLoader() {
  const seq = useRequestSequence()
  const state = { data: '', error: '', loading: false }
  const pending: { resolve: (value: string) => void; reject: (reason: Error) => void }[] = []

  function load(): void {
    const token = seq.begin()
    state.loading = true
    state.error = ''
    const request = new Promise<string>((resolve, reject) => {
      pending.push({ resolve, reject })
    })
    void request
      .then((value) => {
        if (!seq.isCurrent(token)) {
          return
        }
        state.data = value
      })
      .catch((reason: Error) => {
        if (!seq.isCurrent(token)) {
          return
        }
        state.error = reason.message
      })
      .finally(() => {
        if (seq.isCurrent(token)) {
          state.loading = false
        }
      })
  }

  return { state, pending, load }
}

const flush = (): Promise<void> => new Promise((resolve) => setTimeout(resolve, 0))

describe('乱序响应落地（与视图同款守卫写法）', () => {
  it('后发先回：只有最新请求的数据落地', async () => {
    const { state, pending, load } = createGuardedLoader()
    load()
    load()
    pending[1]!.resolve('新')
    await flush()
    expect(state.data).toBe('新')
    pending[0]!.resolve('旧')
    await flush()
    expect(state.data).toBe('新')
  })

  it('过期错误不覆盖新数据', async () => {
    const { state, pending, load } = createGuardedLoader()
    load()
    load()
    pending[1]!.resolve('新')
    await flush()
    pending[0]!.reject(new Error('旧错误'))
    await flush()
    expect(state.data).toBe('新')
    expect(state.error).toBe('')
  })

  it('过期成功不覆盖新错误', async () => {
    const { state, pending, load } = createGuardedLoader()
    load()
    load()
    pending[1]!.reject(new Error('新错误'))
    await flush()
    expect(state.error).toBe('新错误')
    pending[0]!.resolve('旧')
    await flush()
    expect(state.error).toBe('新错误')
    expect(state.data).toBe('')
  })

  it('loading 只由最新请求复位', async () => {
    const { state, pending, load } = createGuardedLoader()
    load()
    load()
    pending[0]!.resolve('旧')
    await flush()
    expect(state.loading).toBe(true)
    pending[1]!.resolve('新')
    await flush()
    expect(state.loading).toBe(false)
  })
})