/**
 * 购物车本地状态：数量与勾选。
 *
 * 为什么在前端：后端购物车表只有「买家 + 商品 + 加购时间」，没有数量列（阶段三契约），
 * 所以数量与勾选只能存在浏览器里，并且**按买家隔离**——
 * 换账号不能串数据，退出登录要清掉。
 *
 * 为什么单独一个模块而不是放进 store：`stores/auth.ts` 退出时要清购物车本地状态，
 * 如果 auth 直接 import cart store 就形成 store 互相依赖。这里只做纯读写，
 * 两边都能安全引用。
 */

export interface CartLocalEntry {
  quantity: number
  checked: boolean
}

/** 键为 goods_id。 */
export type CartLocalState = Record<string, CartLocalEntry>

const PREFIX = 'simple-shop.buyer.cart.'

function key(username: string): string {
  return PREFIX + username
}

/** 读本地状态；JSON 坏掉或结构不对时当作空，不让脏数据把购物车页拖垮。 */
export function loadCartLocal(username: string): CartLocalState {
  if (!username) return {}
  try {
    const raw = localStorage.getItem(key(username))
    if (!raw) return {}
    const parsed: unknown = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object') return {}
    const out: CartLocalState = {}
    for (const [goodsId, entry] of Object.entries(parsed as Record<string, unknown>)) {
      const e = entry as Partial<CartLocalEntry> | null
      if (!e || typeof e !== 'object') continue
      const quantity = Number(e.quantity)
      out[goodsId] = {
        // 数量下限 1，上限交给页面按库存再夹一次（本地状态不知道库存）。
        quantity: Number.isFinite(quantity) && quantity >= 1 ? Math.floor(quantity) : 1,
        checked: e.checked !== false,
      }
    }
    return out
  } catch {
    return {}
  }
}

export function saveCartLocal(username: string, state: CartLocalState): void {
  if (!username) return
  try {
    if (Object.keys(state).length === 0) {
      localStorage.removeItem(key(username))
      return
    }
    localStorage.setItem(key(username), JSON.stringify(state))
  } catch {
    // 存满了或被禁用时不影响页面本身可用。
  }
}

/** 退出登录时调用：只清当前买家那一份，不动别人的键。 */
export function clearCartLocal(username: string): void {
  if (!username) return
  try {
    localStorage.removeItem(key(username))
  } catch {
    // 同上，清不掉也不该阻断退出流程。
  }
}
