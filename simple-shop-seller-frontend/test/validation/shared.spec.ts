import { describe, expect, it } from 'vitest'

import { countChars, hasAngleBracket, hasLineBreak, trimToNull } from '@/validation/shared'

describe('countChars', () => {
  it('按码点计数：emoji 算 1 个字符', () => {
    expect(countChars('')).toBe(0)
    expect(countChars('abc')).toBe(3)
    expect(countChars('😀a')).toBe(2)
    expect(countChars('👨‍👩‍👧')).toBe(5)
  })
})

describe('hasAngleBracket', () => {
  it('任一尖括号即命中', () => {
    expect(hasAngleBracket('<')).toBe(true)
    expect(hasAngleBracket('>')).toBe(true)
    expect(hasAngleBracket('a<b')).toBe(true)
    expect(hasAngleBracket('普通文本')).toBe(false)
  })
})

describe('hasLineBreak', () => {
  it('\\n 与 \\r 都算换行', () => {
    expect(hasLineBreak('a\nb')).toBe(true)
    expect(hasLineBreak('a\rb')).toBe(true)
    expect(hasLineBreak('a\r\nb')).toBe(true)
    expect(hasLineBreak('ab')).toBe(false)
  })
})

describe('trimToNull', () => {
  it('空值与纯空白收敛为 null，其余 trim', () => {
    expect(trimToNull(null)).toBeNull()
    expect(trimToNull(undefined)).toBeNull()
    expect(trimToNull('')).toBeNull()
    expect(trimToNull('   ')).toBeNull()
    expect(trimToNull(' a ')).toBe('a')
  })
})