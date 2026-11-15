import { describe, expect, it } from 'vitest'

import { clearCartLocal, loadCartLocal, saveCartLocal } from '../../src/utils/cartStorage'

const KEY = 'simple-shop.buyer.cart.'

describe('cartStorage', () => {
  it('读写往返', () => {
    saveCartLocal('alice', { g1: { quantity: 2, checked: false } })
    expect(loadCartLocal('alice')).toEqual({ g1: { quantity: 2, checked: false } })
  })

  it('按用户名隔离，换账号不串数据', () => {
    saveCartLocal('alice', { g1: { quantity: 1, checked: true } })
    saveCartLocal('bob', { g2: { quantity: 3, checked: true } })
    expect(Object.keys(loadCartLocal('alice'))).toEqual(['g1'])
    expect(Object.keys(loadCartLocal('bob'))).toEqual(['g2'])
  })

  it('坏 JSON 与非对象当空，不拖垮页面', () => {
    localStorage.setItem(KEY + 'alice', '{bad json')
    expect(loadCartLocal('alice')).toEqual({})
    localStorage.setItem(KEY + 'alice', '"a string"')
    expect(loadCartLocal('alice')).toEqual({})
    localStorage.setItem(KEY + 'alice', '123')
    expect(loadCartLocal('alice')).toEqual({})
  })

  it('数量下限 1、向下取整、非法回 1', () => {
    localStorage.setItem(
      KEY + 'alice',
      JSON.stringify({
        a: { quantity: 0, checked: true },
        b: { quantity: 2.7, checked: true },
        c: { quantity: 'x', checked: true },
        d: { quantity: -3, checked: true },
      }),
    )
    const s = loadCartLocal('alice')
    expect(s.a!.quantity).toBe(1)
    expect(s.b!.quantity).toBe(2)
    expect(s.c!.quantity).toBe(1)
    expect(s.d!.quantity).toBe(1)
  })

  it('checked 缺省为 true，显式 false 保留', () => {
    localStorage.setItem(
      KEY + 'alice',
      JSON.stringify({ a: { quantity: 1 }, b: { quantity: 1, checked: false } }),
    )
    const s = loadCartLocal('alice')
    expect(s.a!.checked).toBe(true)
    expect(s.b!.checked).toBe(false)
  })

  it('空状态删除键而不是留空对象', () => {
    saveCartLocal('alice', { g1: { quantity: 1, checked: true } })
    saveCartLocal('alice', {})
    expect(localStorage.getItem(KEY + 'alice')).toBeNull()
  })

  it('clear 只清当前用户，不动别人', () => {
    saveCartLocal('alice', { g1: { quantity: 1, checked: true } })
    saveCartLocal('bob', { g2: { quantity: 1, checked: true } })
    clearCartLocal('alice')
    expect(loadCartLocal('alice')).toEqual({})
    expect(Object.keys(loadCartLocal('bob'))).toEqual(['g2'])
  })

  it('空用户名 no-op', () => {
    expect(loadCartLocal('')).toEqual({})
    saveCartLocal('', { g1: { quantity: 1, checked: true } })
    expect(localStorage.length).toBe(0)
    expect(() => clearCartLocal('')).not.toThrow()
  })
})