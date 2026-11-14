<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { ApiError } from '../api/http'
import { ApiCode } from '../api/types'
import { describeError } from '../stores/auth'
import { useCartStore } from '../stores/cart'
import type { CartRow } from '../stores/cart'
import { formatMoney, formatTime } from '../utils/format'

const cart = useCartStore()
const router = useRouter()

const submitting = ref(false)
const movingFav = ref(false)
const notice = ref('')
const actionError = ref('')

/** 可结算的行数与件数分开算：3 件商品各 2 个，是 3 行、6 件。 */
const checkedLineCount = computed(() => cart.checkedRows.length)

function stockText(row: CartRow): string {
  if (!row.selectable) return '已下架'
  if (row.stock <= 0) return '缺货'
  return '库存 ' + row.stock
}

/** 数量输入用 change 而不是 input：避免每敲一个数字就夹一次，还没输完就被改掉。 */
function onQuantityChange(row: CartRow, event: Event): void {
  const el = event.target as HTMLInputElement
  cart.setQuantity(row.goods_id, Number(el.value))
  // 夹取后的值可能和输入值不同（比如输入 0 被夹回 1），这里回写让输入框跟着变。
  const applied = cart.rows.find((r) => r.goods_id === row.goods_id)?.quantity ?? 1
  if (el.value !== String(applied)) {
    el.value = String(applied)
  }
}

function step(row: CartRow, delta: number): void {
  cart.setQuantity(row.goods_id, row.quantity + delta)
}

async function onRemove(row: CartRow): Promise<void> {
  actionError.value = ''
  notice.value = ''
  try {
    await cart.remove(row.goods_id)
    notice.value = '已从购物车移除《' + row.name + '》'
  } catch (err) {
    actionError.value = describeError(err)
  }
}

async function onSubmit(): Promise<void> {
  if (checkedLineCount.value === 0) return
  submitting.value = true
  actionError.value = ''
  notice.value = ''
  try {
    const placed = await cart.submitOrder()
    // D2-5：成功直接进订单详情——那里有订单号、状态和逐件明细，
    // 比在购物车页弹一句话让人放心；购物车里的行已经由 store 清掉了。
    void router.push({ name: 'order-detail', params: { id: placed.order_id } })
  } catch (err) {
    actionError.value = submitFailText(err)
    // 失败后重新拉一次购物车：失败多半是「提交瞬间商品下架 / 库存变了」，
    // 不刷新的话页面上还是旧库存，用户照着旧数字再点一次还是失败。
    // 刷新只影响失效行（会被取消勾选），有效行的勾选与数量由 syncLocal 原样保留。
    await cart.load()
  } finally {
    submitting.value = false
  }
}

/** 下单失败的提示。参数类错误单独说，因为后端那句话是给表单用的，放在下单场景看不懂。 */
function submitFailText(err: unknown): string {
  if (err instanceof ApiError && err.code === ApiCode.PARAM_INVALID) {
    return '下单信息有误，请回购物车重新勾选后再提交'
  }
  return describeError(err)
}

/**
 * 勾选行转收藏（D4-4）。
 * 后端「写收藏 + 删购物车行」在一个事务里，成功就会顺带把行删掉；
 * 若批次里有已下架商品，后端整批拒绝，此时购物车一行不动。
 */
async function onMoveToFavorites(): Promise<void> {
  if (checkedLineCount.value === 0) return
  movingFav.value = true
  actionError.value = ''
  notice.value = ''
  try {
    const moved = await cart.moveCheckedToFavorites()
    notice.value = moved > 0 ? '已把 ' + moved + ' 件商品移到收藏' : '这些商品已不在购物车里'
  } catch (err) {
    actionError.value = describeError(err)
  } finally {
    movingFav.value = false
  }
}

onMounted(() => {
  void cart.load()
})
</script>

<template>
  <div class="page stack">
    <div class="head">
      <h1 class="page-title">购物车</h1>
      <RouterLink v-if="cart.rows.length" to="/goods" class="muted">继续挑选商品</RouterLink>
    </div>

    <p v-if="notice" class="notice">{{ notice }}</p>
    <p v-if="actionError" class="error">{{ actionError }}</p>

    <p v-if="cart.loading && !cart.rows.length" class="muted">加载中…</p>
    <p v-else-if="cart.errorText" class="error">{{ cart.errorText }}</p>

    <div v-else-if="!cart.rows.length" class="card empty">
      <p class="empty-title">购物车是空的</p>
      <p class="muted">还没有加入任何商品，去看看有什么想买的。</p>
      <RouterLink class="primary-link" to="/goods">去逛逛</RouterLink>
    </div>

    <template v-else>
      <p v-if="cart.invalidRows.length" class="warn-bar">
        有 {{ cart.invalidRows.length }} 件商品已下架或缺货，已取消勾选，无法下单。可以移除或稍后再看。
      </p>

      <div class="card list">
        <div class="list-head">
          <label class="check-all">
            <input
              type="checkbox"
              :checked="cart.allChecked"
              @change="cart.toggleAll(($event.target as HTMLInputElement).checked)"
            />
            <span>全选</span>
          </label>
          <span class="muted small">共 {{ cart.rows.length }} 件商品</span>
        </div>

        <div
          v-for="row in cart.rows"
          :key="row.goods_id"
          :class="['row-item', { disabled: !row.checkable }]"
        >
          <input
            class="row-check"
            type="checkbox"
            :checked="row.checked"
            :disabled="!row.checkable"
            @change="cart.toggleChecked(row.goods_id, ($event.target as HTMLInputElement).checked)"
          />

          <img v-if="row.main_image_url" :src="row.main_image_url" :alt="row.name" class="thumb" />
          <div v-else class="thumb placeholder">无图</div>

          <div class="info">
            <RouterLink v-if="row.selectable" :to="'/goods/' + row.goods_id" class="name">
              {{ row.name }}
            </RouterLink>
            <span v-else class="name">{{ row.name }}</span>

            <div class="sub">
              <span class="price">¥{{ formatMoney(row.price) }}</span>
              <span :class="['stock', { bad: !row.selectable || row.stock <= 0 }]">
                {{ stockText(row) }}
              </span>
            </div>
            <div class="sub muted small">加入时间 {{ formatTime(row.added_at) }}</div>
          </div>

          <div class="qty">
            <button
              class="step"
              :disabled="!row.checkable || row.quantity <= 1"
              @click="step(row, -1)"
            >
              −
            </button>
            <input
              class="qty-input"
              type="number"
              :min="1"
              :max="row.stock > 0 ? row.stock : 1"
              :value="row.quantity"
              :disabled="!row.checkable"
              @change="onQuantityChange(row, $event)"
            />
            <button
              class="step"
              :disabled="!row.checkable || row.quantity >= (row.stock > 0 ? row.stock : 1)"
              @click="step(row, 1)"
            >
              +
            </button>
          </div>

          <div class="line-total">¥{{ formatMoney(row.price * row.quantity) }}</div>

          <button class="remove" @click="onRemove(row)">移除</button>
        </div>
      </div>

      <div class="card bar">
        <label class="check-all">
          <input
            type="checkbox"
            :checked="cart.allChecked"
            @change="cart.toggleAll(($event.target as HTMLInputElement).checked)"
          />
          <span>全选</span>
        </label>

        <div class="summary">
          <span class="muted">已选 {{ checkedLineCount }} 件，共 {{ cart.totalQuantity }} 个</span>
          <span class="total">
            合计 <strong>¥{{ formatMoney(cart.totalAmount) }}</strong>
          </span>
        </div>

        <button
          class="to-fav"
          :disabled="checkedLineCount === 0 || movingFav || submitting"
          @click="onMoveToFavorites"
        >
          {{ movingFav ? '转移中…' : '转收藏' }}
        </button>

        <button
          class="primary checkout"
          :disabled="checkedLineCount === 0 || submitting || movingFav"
          @click="onSubmit"
        >
          {{ submitting ? '提交中…' : '去结算' }}
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.page-title {
  font-size: 18px;
  margin: 0;
}

.notice {
  color: #15803d;
  margin: 0;
}

.warn-bar {
  margin: 0;
  padding: 8px 12px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 6px;
  color: #92400e;
}

.empty {
  text-align: center;
  padding: 40px 16px;
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 8px;
}

.primary-link {
  display: inline-block;
  margin-top: 12px;
  padding: 6px 16px;
  background: var(--color-primary);
  color: #fff;
  border-radius: 4px;
}

.list {
  padding: 0;
}

.list-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
}

.check-all {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}

.small {
  font-size: 12px;
}

.row-item {
  display: grid;
  grid-template-columns: 24px 64px minmax(0, 1fr) 132px 88px 56px;
  gap: 12px;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
}

.row-item:last-child {
  border-bottom: none;
}

.row-item.disabled {
  background: var(--color-fill);
}

.row-item.disabled .name,
.row-item.disabled .price {
  color: var(--color-text-weak);
}

.thumb {
  width: 64px;
  height: 64px;
  object-fit: cover;
  border-radius: 4px;
  background: var(--color-fill);
}

.thumb.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--color-text-weak);
}

.info {
  min-width: 0;
}

.name {
  display: block;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sub {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 2px;
}

.price {
  color: var(--color-danger);
  font-weight: 600;
}

.stock {
  font-size: 12px;
  color: var(--color-text-weak);
}

.stock.bad {
  color: var(--color-danger);
}

.qty {
  display: flex;
  align-items: center;
  gap: 4px;
}

.step {
  width: 28px;
  padding: 2px 0;
  text-align: center;
}

.qty-input {
  width: 52px;
  padding: 2px 4px;
  text-align: center;
}

.line-total {
  text-align: right;
  font-weight: 600;
}

.remove {
  padding: 2px 8px;
  font-size: 12px;
  color: var(--color-text-weak);
}

.bar {
  display: flex;
  align-items: center;
  gap: 16px;
  position: sticky;
  bottom: 0;
}

.summary {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-left: auto;
}

.total strong {
  color: var(--color-danger);
  font-size: 18px;
}

.checkout {
  padding: 8px 24px;
}

@media (max-width: 720px) {
  .row-item {
    grid-template-columns: 24px 56px minmax(0, 1fr) 56px;
    grid-template-areas:
      'check thumb info remove'
      '. qty qty .'
      '. total total .';
  }

  .row-check {
    grid-area: check;
  }

  .thumb {
    grid-area: thumb;
    width: 56px;
    height: 56px;
  }

  .info {
    grid-area: info;
  }

  .qty {
    grid-area: qty;
  }

  .line-total {
    grid-area: total;
    text-align: left;
  }

  .remove {
    grid-area: remove;
  }
}
</style>
