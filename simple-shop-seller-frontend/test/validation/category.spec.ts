import { describe, expect, it } from 'vitest'

import { ApiCode } from '@/api/types'
import { validateCategoryName } from '@/validation/category'

describe('validateCategoryName', () => {
  it('空值与纯空白拒绝为 50002', () => {
    for (const input of [null, undefined, '', '   ']) {
      expect(validateCategoryName(input)).toEqual({ ok: false, code: ApiCode.PARAM_INVALID })
    }
  })

  it('返回 trim 后的值', () => {
    expect(validateCategoryName(' 数码 ')).toEqual({ ok: true, value: '数码' })
  })

  it('按码点计数：50 通过，51 拒绝为 20003', () => {
    expect(validateCategoryName('类'.repeat(50)).ok).toBe(true)
    expect(validateCategoryName('类'.repeat(51))).toEqual({
      ok: false,
      code: ApiCode.CATEGORY_NAME_INVALID,
    })
  })

  it('拒绝尖括号与换行', () => {
    for (const input of ['<b>', 'a>b', 'a\nb', 'a\rb']) {
      expect(validateCategoryName(input)).toEqual({
        ok: false,
        code: ApiCode.CATEGORY_NAME_INVALID,
      })
    }
  })
})