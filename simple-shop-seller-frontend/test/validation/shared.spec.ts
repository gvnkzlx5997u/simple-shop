import { describe, expect, it } from 'vitest'

import { apiCodeMessage, M10 } from '@/constants/messages'
import {
  countChars,
  fail,
  fieldErrorMessage,
  hasAngleBracket,
  hasLineBreak,
  ok,
  trimToNull,
} from '@/validation/shared'

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

describe('fieldErrorMessage', () => {
  const codeText = apiCodeMessage

  it('通过时不产生文案', () => {
    expect(fieldErrorMessage('seller', ok('seller'), M10.accountRequired, codeText)).toBeUndefined()
  })

  it('空值与纯空白给「请输入…」，而不是按业务码翻成「请检查输入内容」', () => {
    // 后端对「空」与「格式不对」回的都是 50002；不分开的话用户看到的是「请检查输入内容」。
    expect(fieldErrorMessage('', fail(50002), M10.accountRequired, codeText)).toBe(
      M10.accountRequired,
    )
    expect(fieldErrorMessage('   ', fail(50002), M10.passwordRequired, codeText)).toBe(
      M10.passwordRequired,
    )
  })

  it('非空但不合法时仍按业务码翻译', () => {
    const tooLong = 'a'.repeat(51)
    expect(fieldErrorMessage(tooLong, fail(50002), M10.accountRequired, codeText)).toBe(
      M10.paramInvalid,
    )
  })
})