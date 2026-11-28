import { effectScope } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

import { useCountdown } from '../../src/composables/useCountdown'

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-11-27T10:00:00Z'))
})

afterEach(() => {
  vi.useRealTimers()
})

/** 组件外调用时补一个 effect scope，否则 onScopeDispose 没有归属。 */
function withScope<T>(fn: () => T): { result: T; stop: () => void } {
  const scope = effectScope()
  const result = scope.run(fn)!
  return { result, stop: () => scope.stop() }
}

describe('useCountdown', () => {
  it('按 pay_deadline 算剩余，不自己按 24 小时推', () => {
    const deadline = ref<string | null>('2026-11-27T10:02:03Z')
    const { result, stop } = withScope(() => useCountdown(deadline))
    expect(result.remainingMs.value).toBe(123_000)
    expect(result.text.value).toBe('00:02:03')
    expect(result.expired.value).toBe(false)
    stop()
  })

  it('每秒推进一次', () => {
    const deadline = ref<string | null>('2026-11-27T10:00:05Z')
    const { result, stop } = withScope(() => useCountdown(deadline))
    expect(result.text.value).toBe('00:00:05')
    vi.advanceTimersByTime(3000)
    expect(result.text.value).toBe('00:00:02')
    stop()
  })

  it('到点归零并置 expired，不出现负数', () => {
    const deadline = ref<string | null>('2026-11-27T10:00:02Z')
    const { result, stop } = withScope(() => useCountdown(deadline))
    vi.advanceTimersByTime(5000)
    expect(result.remainingMs.value).toBe(0)
    expect(result.text.value).toBe('00:00:00')
    expect(result.expired.value).toBe(true)
    stop()
  })

  it('打开时就已过期', () => {
    const deadline = ref<string | null>('2026-11-27T09:00:00Z')
    const { result, stop } = withScope(() => useCountdown(deadline))
    expect(result.expired.value).toBe(true)
    stop()
  })

  it('deadline 为空或非法时不算过期', () => {
    const none = ref<string | null>(null)
    const a = withScope(() => useCountdown(none))
    expect(a.result.expired.value).toBe(false)
    expect(a.result.text.value).toBe('00:00:00')
    a.stop()

    const bad = ref<string | null>('not-a-date')
    const b = withScope(() => useCountdown(bad))
    expect(b.result.remainingMs.value).toBe(0)
    b.stop()
  })

  it('scope 销毁后停掉定时器', () => {
    const deadline = ref<string | null>('2026-11-27T10:00:10Z')
    const { result, stop } = withScope(() => useCountdown(deadline))
    stop()
    vi.advanceTimersByTime(5000)
    expect(result.text.value).toBe('00:00:10')
  })
})