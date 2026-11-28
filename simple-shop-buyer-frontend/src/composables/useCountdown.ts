import { computed, onScopeDispose, ref } from 'vue'
import type { Ref } from 'vue'

import { formatCountdown } from '../utils/format'

/**
 * 支付倒计时。
 *
 * 时限的唯一来源是后端给的 `pay_deadline`——时长是后端配置，前端按 24 小时自己算
 * 只会和订单实际过期时间对不上。`now` 可注入，便于测试时推进时间。
 */
export function useCountdown(deadline: Ref<string | null>, now: () => number = Date.now) {
  const tick = ref(now())

  const remainingMs = computed(() => {
    if (!deadline.value) return 0
    const end = new Date(deadline.value).getTime()
    if (Number.isNaN(end)) return 0
    return Math.max(0, end - tick.value)
  })

  const text = computed(() => formatCountdown(remainingMs.value))

  /** 没有 deadline 时不算过期：那只是「这单不需要倒计时」。 */
  const expired = computed(() => deadline.value != null && remainingMs.value === 0)

  const timer = setInterval(() => {
    tick.value = now()
  }, 1000)
  onScopeDispose(() => clearInterval(timer))

  return { remainingMs, text, expired }
}