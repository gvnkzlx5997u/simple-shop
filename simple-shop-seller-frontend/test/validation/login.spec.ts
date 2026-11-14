import { describe, expect, it } from 'vitest'

import { ApiCode } from '@/api/types'
import {
  validateAccount,
  validateLoginPassword,
  validateNewPassword,
  validateOldPassword,
} from '@/validation/login'

describe('validateAccount', () => {
  it('空值与纯空白拒绝为 50002', () => {
    for (const input of [null, undefined, '', '   ']) {
      expect(validateAccount(input)).toEqual({ ok: false, code: ApiCode.PARAM_INVALID })
    }
  })

  it('按 UTF-16 长度计：50 通过，51 拒绝', () => {
    expect(validateAccount('a'.repeat(50)).ok).toBe(true)
    expect(validateAccount('a'.repeat(51))).toEqual({ ok: false, code: ApiCode.PARAM_INVALID })
  })

  it('不做 trim：前后空格原样保留，交给后端按「账号不存在」处理', () => {
    expect(validateAccount(' seller ')).toEqual({ ok: true, value: ' seller ' })
  })
})

describe('validateLoginPassword / validateOldPassword', () => {
  it('仅必填，不设长度上限', () => {
    for (const validate of [validateLoginPassword, validateOldPassword]) {
      expect(validate('')).toEqual({ ok: false, code: ApiCode.PARAM_INVALID })
      expect(validate('  ')).toEqual({ ok: false, code: ApiCode.PARAM_INVALID })
      expect(validate('x')).toEqual({ ok: true, value: 'x' })
      expect(validate('a'.repeat(200))).toEqual({ ok: true, value: 'a'.repeat(200) })
    }
  })
})

describe('validateNewPassword', () => {
  it('空值拒绝为 50002', () => {
    expect(validateNewPassword('')).toEqual({ ok: false, code: ApiCode.PARAM_INVALID })
  })

  it('按 UTF-16 长度计：7 位拒绝为 10004，8 位通过', () => {
    expect(validateNewPassword('1234567')).toEqual({ ok: false, code: ApiCode.PASSWORD_INVALID })
    expect(validateNewPassword('12345678')).toEqual({ ok: true, value: '12345678' })
  })

  it('emoji 按 UTF-16 计长：4 个 emoji 恰好 8 位通过，3 个拒绝', () => {
    expect(validateNewPassword('😀'.repeat(4)).ok).toBe(true)
    expect(validateNewPassword('😀'.repeat(3))).toEqual({
      ok: false,
      code: ApiCode.PASSWORD_INVALID,
    })
  })
})