import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as cartApi from '../../src/api/cart'
import { ApiError } from '../../src/api/http'
import { placeOrder } from '../../src/api/order'
import { ApiCode } from '../../src/api/types'
import type { CartItemData } from '../../src/api/types'
import { useAuthStore } from '../../src/stores/auth'
import { useCartStore } from '../../src/stores/cart'
import { loadCartLocal, saveCartLocal } from '../../src/utils/cartStorage'

vi.mock('../../src/api/cart', () => ({
  addToCart: vi.fn(),
  fetchCart: vi.fn(),
  removeCartItem: vi.fn(),
  moveToFavorites: vi.fn(),
}))
vi.mock('../../src/api/order', () => ({ placeOrder: vi.fn() }))

function item(over: Partial<CartItemData> = {}): CartItemData {
  return {
    goods_id: 'g1',
    name: '商品1',
    price: 10,
    stock: 5,
    status: 'ON_SALE',
    off_shelf_reason: null,
    main_image_url: '',
    selectable: true,
    added_at: '2026-11-11T10:00:00Z',
    ...over,
  }
}

const offShelf = item({ goods_id: 'g3', selectable: false, status: 'OFF_SHELF', off_shelf_reason: 'MANUAL' })
const outOfStock = item({ goods_id: 'g2', stock: 0 })

let cart: ReturnType<typeof useCartStore>

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
  // 直接写本地会话模拟已登录（auth store 不暴露 setSession，登录态靠 token/username 两个键）。
  localStorage.setItem('simple-shop.buyer.token', 'tok')
  localStorage.setItem('simple-shop.buyer.username', 'alice')
  cart = useCartStore()
  vi.mocked(cartApi.fetchCart).mockResolvedValue({ items: [] })
  vi.mocked(cartApi.addToCart).mockResolvedValue(null)
  vi.mocked(cartApi.removeCartItem).mockResolvedValue(null)
  vi.mocked(cartApi.moveToFavorites).mockResolvedValue({ moved_count: 0 })
  vi.mocked(placeOrder).mockResolvedValue({
    order_id: 'o1',
    order_no: 'SO1',
    total_amount: 0,
    status: 'PENDING_PAYMENT',
    create_at: '2026-11-11T10:00:00Z',
    pay_deadline: '2026-11-12T10:00:00Z',
  })
})

describe('load 与 syncLocal', () => {
  it('未登录直接清空，不发请求', async () => {
    useAuthStore().clearSession()
    await cart.load()
    expect(cart.items).toEqual([])
    expect(cart.loaded).toBe(false)
    expect(cartApi.fetchCart).not.toHaveBeenCalled()
  })

  it('孤儿丢弃、缺失补默认、失效强制取消勾选', async () => {
    saveCartLocal('alice', { g1: { quantity: 3, checked: true }, g9: { quantity: 1, checked: true } })
    vi.mocked(cartApi.fetchCart).mockResolvedValue({ items: [item(), outOfStock, offShelf] })
    await cart.load()

    const byId = Object.fromEntries(cart.rows.map((r) => [r.goods_id, r]))
    expect(byId.g1!.quantity).toBe(3)
    expect(byId.g1!.checked).toBe(true)
    expect(byId.g2!.checkable).toBe(false)
    expect(byId.g2!.checked).toBe(false)
    expect(byId.g2!.stockShort).toBe(true)
    expect(byId.g3!.checkable).toBe(false)
    expect(byId.g3!.stockShort).toBe(false)
    // 孤儿 g9 被清掉
    expect(Object.keys(loadCartLocal('alice')).sort()).toEqual(['g1', 'g2', 'g3'])
  })
})

describe('数量夹取', () => {
  it('夹到 [1, 库存]，取整，非法回 1', async () => {
    vi.mocked(cartApi.fetchCart).mockResolvedValue({ items: [item()] })
    await cart.load()
    const qty = () => cart.rows.find((r) => r.goods_id === 'g1')!.quantity
    cart.setQuantity('g1', 99)
    expect(qty()).toBe(5)
    cart.setQuantity('g1', 0)
    expect(qty()).toBe(1)
    cart.setQuantity('g1', 2.7)
    expect(qty()).toBe(2)
    cart.setQuantity('g1', NaN)
    expect(qty()).toBe(1)
  })

  it('失效行改数量/勾选均 no-op', async () => {
    vi.mocked(cartApi.fetchCart).mockResolvedValue({ items: [outOfStock] })
    await cart.load()
    cart.setQuantity('g2', 3)
    cart.toggleChecked('g2', true)
    const row = cart.rows.find((r) => r.goods_id === 'g2')!
    expect(row.quantity).toBe(1)
    expect(row.checked).toBe(false)
  })
})

describe('合计与勾选', () => {
  it('按分累加避开浮点尾数', async () => {
    vi.mocked(cartApi.fetchCart).mockResolvedValue({
      items: [item({ goods_id: 'a', price: 0.1 }), item({ goods_id: 'b', price: 0.2 })],
    })
    await cart.load()
    expect(cart.totalAmount).toBe(0.3)
  })

  it('失效行与未勾选行不计入合计', async () => {
    vi.mocked(cartApi.fetchCart).mockResolvedValue({ items: [item(), outOfStock] })
    await cart.load()
    expect(cart.totalAmount).toBe(10)
    cart.toggleChecked('g1', false)
    expect(cart.totalAmount).toBe(0)
    expect(cart.checkedRows).toEqual([])
    expect(cart.invalidRows.map((r) => r.goods_id)).toEqual(['g2'])
  })

  it('全选只作用于可下单行', async () => {
    vi.mocked(cartApi.fetchCart).mockResolvedValue({ items: [item(), outOfStock] })
    await cart.load()
    cart.toggleAll(true)
    expect(cart.rows.find((r) => r.goods_id === 'g1')!.checked).toBe(true)
    expect(cart.rows.find((r) => r.goods_id === 'g2')!.checked).toBe(false)
    expect(cart.allChecked).toBe(true)
    cart.toggleAll(false)
    expect(cart.allChecked).toBe(false)
  })
})

describe('加购与移除', () => {
  it('已在车里返回 existed', async () => {
    vi.mocked(cartApi.fetchCart).mockResolvedValue({ items: [item()] })
    await cart.load()
    expect(await cart.add('g1')).toBe('existed')
    expect(cartApi.addToCart).toHaveBeenCalledWith('g1')
  })

  it('不在车里返回 added', async () => {
    expect(await cart.add('g1')).toBe('added')
    expect(cartApi.addToCart).toHaveBeenCalledWith('g1')
  })

  it('移除删行并清本地', async () => {
    vi.mocked(cartApi.fetchCart).mockResolvedValue({ items: [item(), outOfStock] })
    await cart.load()
    await cart.remove('g1')
    expect(cart.items.map((i) => i.goods_id)).toEqual(['g2'])
    expect(Object.keys(loadCartLocal('alice'))).toEqual(['g2'])
  })
})

describe('submitOrder', () => {
  it('成功：带地址与明细数量，成交后清行清本地', async () => {
    vi.mocked(cartApi.fetchCart)
      .mockResolvedValueOnce({ items: [item(), item({ goods_id: 'g2', price: 20 })] })
      .mockResolvedValue({ items: [] })
    await cart.load()
    cart.setQuantity('g1', 2)
    const placed = await cart.submitOrder('addr1')
    expect(placed.order_id).toBe('o1')
    expect(placeOrder).toHaveBeenCalledWith('addr1', [
      { goods_id: 'g1', quantity: 2 },
      { goods_id: 'g2', quantity: 1 },
    ])
    expect(cartApi.removeCartItem).toHaveBeenCalledWith('g1')
    expect(cartApi.removeCartItem).toHaveBeenCalledWith('g2')
    expect(loadCartLocal('alice')).toEqual({})
  })

  it('失败：勾选与数量原样保留，不删行', async () => {
    vi.mocked(cartApi.fetchCart)
      .mockResolvedValueOnce({ items: [item(), outOfStock] })
      .mockResolvedValue({ items: [] })
    await cart.load()
    cart.setQuantity('g1', 2)
    vi.mocked(placeOrder).mockRejectedValueOnce(new ApiError(ApiCode.GOODS_NOT_ON_SALE, 'x'))
    await expect(cart.submitOrder('addr1')).rejects.toThrow()
    expect(loadCartLocal('alice').g1).toEqual({ quantity: 2, checked: true })
    expect(cartApi.removeCartItem).not.toHaveBeenCalled()
  })

  it('无勾选行拒绝提交', async () => {
    await cart.load()
    await expect(cart.submitOrder('addr1')).rejects.toThrow('empty selection')
  })
})

describe('moveCheckedToFavorites', () => {
  it('成功：返回 moved_count 并清本地', async () => {
    vi.mocked(cartApi.fetchCart)
      .mockResolvedValueOnce({ items: [item(), outOfStock] })
      .mockResolvedValue({ items: [] })
    await cart.load()
    // 只勾选 g1（g2 失效不可勾）
    vi.mocked(cartApi.moveToFavorites).mockResolvedValue({ moved_count: 1 })
    expect(await cart.moveCheckedToFavorites()).toBe(1)
    expect(cartApi.moveToFavorites).toHaveBeenCalledWith(['g1'])
    expect(loadCartLocal('alice')).toEqual({})
  })

  it('失败：购物车行保持原样', async () => {
    vi.mocked(cartApi.fetchCart).mockResolvedValue({ items: [item()] })
    await cart.load()
    cart.setQuantity('g1', 2)
    vi.mocked(cartApi.moveToFavorites).mockRejectedValueOnce(new ApiError(ApiCode.CART_TO_FAVORITE_REJECTED, 'x'))
    await expect(cart.moveCheckedToFavorites()).rejects.toThrow()
    expect(loadCartLocal('alice').g1).toEqual({ quantity: 2, checked: true })
  })
})

describe('reset', () => {
  it('清空状态与本地键', async () => {
    vi.mocked(cartApi.fetchCart).mockResolvedValue({ items: [item()] })
    await cart.load()
    cart.reset()
    expect(cart.items).toEqual([])
    expect(cart.loaded).toBe(false)
    expect(loadCartLocal('alice')).toEqual({})
  })
})