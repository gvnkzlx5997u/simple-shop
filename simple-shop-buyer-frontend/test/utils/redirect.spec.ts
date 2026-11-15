import { describe, expect, it } from 'vitest'

import { safeRedirect } from '../../src/utils/redirect'

describe('safeRedirect', () => {
  it('放行站内路径（含查询串）', () => {
    expect(safeRedirect('/cart')).toBe('/cart')
    expect(safeRedirect('/goods/1?x=1')).toBe('/goods/1?x=1')
  })

  it('拒绝协议相对地址与外站', () => {
    expect(safeRedirect('//evil.com')).toBe('')
    expect(safeRedirect('http://evil.com')).toBe('')
    expect(safeRedirect('https://evil.com/x')).toBe('')
  })

  it('空值与非字符串回空串', () => {
    expect(safeRedirect('')).toBe('')
    expect(safeRedirect(null)).toBe('')
    expect(safeRedirect(undefined)).toBe('')
    expect(safeRedirect(123)).toBe('')
    expect(safeRedirect({ path: '/cart' })).toBe('')
  })
})