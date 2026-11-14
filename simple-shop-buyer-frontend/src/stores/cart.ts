import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import * as api from '../api/cart'
import { placeOrder } from '../api/order'
import type { CartItemData, OrderPlacedData } from '../api/types'
import { clearCartLocal, loadCartLocal, saveCartLocal } from '../utils/cartStorage'
import type { CartLocalState } from '../utils/cartStorage'
import { describeError, useAuthStore } from './auth'

/**
 * 购物车状态。
 *
 * 数据分两半：
 * - **服务端行**（商品、价格、库存、是否在售）每次进页面重新拉，是唯一事实来源；
 * - **本地状态**（数量、勾选）只存 localStorage，按用户名隔离，服务端没有这两列。
 *
 * `rows` 是两者合并后的展示结果，页面只读它，不各自拼。
 */

/** 单行数量上限：库存足够时也不让一个数量框无限加。 */
const MAX_QUANTITY = 999

export interface CartRow extends CartItemData {
  /** 本地数量；有效行至少 1。 */
  quantity: number
  /** 本地勾选；不可买的行恒为 false。 */
  checked: boolean
  /**
   * 能否勾选下单 = 在售**且**有库存。
   * 在售但缺货的行仍保留展示（等补货），但不能带进订单。
   */
  checkable: boolean
  /** 在售但库存为 0：文案要写「库存不足」而不是「已下架」。 */
  stockShort: boolean
}

export const useCartStore = defineStore('cart', () => {
  const auth = useAuthStore()

  const items = ref<CartItemData[]>([])
  const local = ref<CartLocalState>(auth.username ? loadCartLocal(auth.username) : {})
  const loading = ref(false)
  const loaded = ref(false)
  const errorText = ref('')

  const username = computed(() => auth.username ?? '')

  function persist(): void {
    saveCartLocal(username.value, local.value)
  }

  /**
   * 本地状态与服务端行对齐（D3-4）。三件事一次做完：
   * 1. 本地有、服务端没有 → 丢弃（孤儿记录，商品可能在别处被移除）；
   * 2. 服务端有、本地没有 → 补 `{quantity: 1, checked: true}`；
   * 3. 不可买的行（已下架或库存 0）→ 强制 `checked = false`，不许带进下单。
   */
  function syncLocal(serverItems: CartItemData[]): void {
    const next: CartLocalState = {}
    for (const item of serverItems) {
      const prev = local.value[item.goods_id]
      next[item.goods_id] = {
        quantity: prev ? clampQuantity(prev.quantity, item) : 1,
        checked: isCheckable(item) ? (prev ? prev.checked : true) : false,
      }
    }
    local.value = next
    persist()
  }

  /** 能下单的条件统一放一处：在售且有库存。 */
  function isCheckable(item: CartItemData): boolean {
    return item.selectable && item.stock > 0
  }

  /** 数量夹到 [1, 库存]；库存为 0 时按 1 保留，下单会由后端给出库存不足。 */
  function clampQuantity(quantity: number, item: CartItemData): number {
    const max = item.stock > 0 ? Math.min(item.stock, MAX_QUANTITY) : 1
    const n = Math.floor(Number(quantity))
    if (!Number.isFinite(n) || n < 1) return 1
    return Math.min(n, max)
  }

  const rows = computed<CartRow[]>(() =>
    items.value.map((item) => {
      const entry = local.value[item.goods_id]
      const checkable = isCheckable(item)
      return {
        ...item,
        quantity: clampQuantity(entry?.quantity ?? 1, item),
        checked: checkable ? (entry?.checked ?? true) : false,
        checkable,
        stockShort: item.selectable && item.stock <= 0,
      }
    }),
  )

  /** 可下单的行：在售、有库存、已勾选。 */
  const checkedRows = computed(() => rows.value.filter((r) => r.checkable && r.checked))
  /** 不可下单的行：已下架或缺货，界面要分开说明原因。 */
  const invalidRows = computed(() => rows.value.filter((r) => !r.checkable))

  /** 合计按「分」累加再折回元，避开 0.1 + 0.2 这类浮点尾数（仅用于展示）。 */
  const totalCents = computed(() =>
    checkedRows.value.reduce((sum, r) => sum + Math.round(r.price * 100) * r.quantity, 0),
  )
  const totalAmount = computed(() => totalCents.value / 100)
  const totalQuantity = computed(() => checkedRows.value.reduce((s, r) => s + r.quantity, 0))

  /** 全选态：只对可下单的行有意义；一行都不能买时返回 false。 */
  const selectableRows = computed(() => rows.value.filter((r) => r.checkable))
  const allChecked = computed(
    () => selectableRows.value.length > 0 && selectableRows.value.every((r) => r.checked),
  )

  /**
   * 拉购物车并自愈本地状态。
   * 未登录直接清空：购物车接口需要令牌，匿名状态下调用只会拿 401。
   */
  async function load(): Promise<void> {
    if (!auth.isLoggedIn) {
      items.value = []
      local.value = {}
      loaded.value = false
      return
    }
    loading.value = true
    errorText.value = ''
    try {
      // 先读本地：别的标签页可能刚改过数量或勾选。
      local.value = loadCartLocal(username.value)
      const data = await api.fetchCart()
      items.value = data?.items ?? []
      syncLocal(items.value)
      loaded.value = true
    } catch (err) {
      errorText.value = describeError(err)
    } finally {
      loading.value = false
    }
  }

  function setQuantity(goodsId: string, quantity: number): void {
    const item = items.value.find((i) => i.goods_id === goodsId)
    if (!item || !isCheckable(item)) return
    local.value = {
      ...local.value,
      [goodsId]: {
        quantity: clampQuantity(quantity, item),
        checked: local.value[goodsId]?.checked ?? true,
      },
    }
    persist()
  }

  function toggleChecked(goodsId: string, checked: boolean): void {
    const item = items.value.find((i) => i.goods_id === goodsId)
    if (!item || !isCheckable(item)) return
    local.value = {
      ...local.value,
      [goodsId]: { quantity: local.value[goodsId]?.quantity ?? 1, checked },
    }
    persist()
  }

  function toggleAll(checked: boolean): void {
    const next = { ...local.value }
    for (const item of items.value) {
      const entry = next[item.goods_id]
      next[item.goods_id] = {
        quantity: entry?.quantity ?? 1,
        // 不可买的行无论全选与否都保持 false。
        checked: isCheckable(item) ? checked : false,
      }
    }
    local.value = next
    persist()
  }

  /** 移除一行。后端对不存在的行也返回成功，本地直接删即可。 */
  async function remove(goodsId: string): Promise<void> {
    await api.removeCartItem(goodsId)
    items.value = items.value.filter((i) => i.goods_id !== goodsId)
    const next = { ...local.value }
    delete next[goodsId]
    local.value = next
    persist()
  }

  /**
   * 加购。返回是否「本来就在购物车」，供详情页决定提示文案。
   *
   * 判断分两路：已加载过列表就看服务端行，没加载过就看本地记录
   * （本地记录在上次加购时就写下了），避免为了判断多打一次列表请求。
   */
  async function add(goodsId: string): Promise<'added' | 'existed'> {
    const existed =
      items.value.some((i) => i.goods_id === goodsId) || Boolean(local.value[goodsId])
    await api.addToCart(goodsId)
    // 先补本地状态，再拉列表：否则 syncLocal 会补成默认值，看不出差异。
    if (!local.value[goodsId]) {
      local.value = { ...local.value, [goodsId]: { quantity: 1, checked: true } }
      persist()
    }
    await load()
    return existed ? 'existed' : 'added'
  }

  /**
   * 结算：把勾选且有效的行提交成订单（D3-5）。
   *
   * 后端下单**不会**顺带清购物车（订单服务与购物车表互不引用），
   * 所以成交后要自己把这几行删掉，否则同一件商品还能再被下单一次。
   * 删除失败不影响订单已成立，吞掉即可——下一次 load 仍会按服务端为准。
   *
   * 失败时**原样保留**勾选与数量，让用户能直接回购物车调整。
   */
  async function submitOrder(): Promise<OrderPlacedData> {
    const targets = checkedRows.value
    if (targets.length === 0) {
      throw new Error('empty selection')
    }
    const placed = await placeOrder(
      targets.map((r) => ({ goods_id: r.goods_id, quantity: r.quantity })),
    )
    await Promise.allSettled(targets.map((r) => api.removeCartItem(r.goods_id)))
    // 提交成功才动本地状态：这些行在后端与购物车都该消失了。
    const next = { ...local.value }
    for (const r of targets) delete next[r.goods_id]
    local.value = next
    persist()
    await load()
    return placed
  }

  /**
   * 把勾选的行转成收藏（D4-4）。
   *
   * 后端在同一个事务里「写收藏 → 删对应购物车行」，删几行就回几件，
   * 所以前端不用自己再删一次，只要把本地状态同步跟上。
   *
   * 若批次里混进已下架商品，后端整批拒绝（70003），此时**购物车行保持原样**。
   */
  async function moveCheckedToFavorites(): Promise<number> {
    const targets = checkedRows.value
    if (targets.length === 0) {
      throw new Error('empty selection')
    }
    const data = await api.moveToFavorites(targets.map((r) => r.goods_id))
    const next = { ...local.value }
    for (const r of targets) delete next[r.goods_id]
    local.value = next
    persist()
    await load()
    return data.moved_count
  }

  /** 退出登录时由 auth 之外的地方兜底调用（auth 已自行清理，这里用于切账号场景）。 */
  function reset(): void {
    items.value = []
    local.value = {}
    loaded.value = false
    errorText.value = ''
    if (username.value) clearCartLocal(username.value)
  }

  return {
    items,
    rows,
    checkedRows,
    invalidRows,
    loading,
    loaded,
    errorText,
    totalAmount,
    totalQuantity,
    allChecked,
    selectableRows,
    load,
    add,
    remove,
    setQuantity,
    toggleChecked,
    toggleAll,
    submitOrder,
    moveCheckedToFavorites,
    reset,
  }
})
