import { describe, expect, it } from 'vitest'

import { formatMoney, formatTime } from '../../src/utils/format'

// 时区由 vitest.config 的 env.TZ=UTC 固定（node 只在启动时读 TZ，运行时改无效）。

describe('formatTime', () => {
  it('UTC ISO-8601 转本地 YYYY-MM-DD HH:mm', () => {
    expect(formatTime('2026-11-11T10:00:00Z')).toBe('2026-11-11 10:00')
  })

  it('空值与无法解析的串一律回空串', () => {
    expect(formatTime(null)).toBe('')
    expect(formatTime(undefined)).toBe('')
    expect(formatTime('')).toBe('')
    expect(formatTime('not-a-date')).toBe('')
  })
})

describe('formatMoney', () => {
  it('数字与字符串都保留两位小数', () => {
    expect(formatMoney(199)).toBe('199.00')
    expect(formatMoney('15.5')).toBe('15.50')
    expect(formatMoney(1.5)).toBe('1.50')
    expect(formatMoney(0)).toBe('0.00')
  })

  it('空值与非法值回 0.00', () => {
    expect(formatMoney(null)).toBe('0.00')
    expect(formatMoney(undefined)).toBe('0.00')
    expect(formatMoney(NaN)).toBe('0.00')
    expect(formatMoney('abc')).toBe('0.00')
  })
})