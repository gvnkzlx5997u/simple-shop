<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import EmptyState from '../components/common/EmptyState.vue'
import CartRow from '../components/cart/CartRow.vue'
import CartSummaryBar from '../components/cart/CartSummaryBar.vue'
import { useCartStore } from '../stores/cart'
import { describeError, describeSubmitError } from '../utils/errorText'

const cart = useCartStore()
const router = useRouter()

const submitting = ref(false)
const movingFav = ref(false)
const notice = ref('')
const actionError = ref('')

/** 可结算的行数与件数分开算：3 件商品各 2 个，是 3 行、6 件。 */
const checkedLineCount = computed(() => cart.checkedRows.length)

function onQuantity(goodsId: string, value: number): void {
  cart.setQuantity(goodsId, value)
}

function onStep(goodsId: string, delta: number): void {
  const row = cart.rows.find((r) => r.goods_id === goodsId)
  if (row) cart.setQuantity(goodsId, row.quantity + delta)
}

function onToggle(goodsId: string, checked: boolean): void {
  cart.toggleChecked(goodsId, checked)
}

async function onRemove(goodsId: string): Promise<void> {
  const name = cart.rows.find((r) => r.goods_id === goodsId)?.name ?? ''
  actionError.value = ''
  notice.value = ''
  try {
    await cart.remove(goodsId)
    notice.value = '已从购物车移除《' + name + '》'
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
    // 比在购物车页弹一句话让人放心;购物车里的行已经由 store 清掉了。
    void router.push({ name: 'order-detail', params: { id: placed.order_id } })
  } catch (err) {
    actionError.value = describeSubmitError(err)
    // 失败后重新拉一次购物车：失败多半是「提交瞬间商品下架 / 库存变了」，
    // 不刷新的话页面上还是旧库存，用户照着旧数字再点一次还是失败。
    // 刷新只影响失效行（会被取消勾选），有效行的勾选与数量由 syncLocal 原样保留。
    await cart.load()
  } finally {
    submitting.value = false
  }
}

/**
 * 勾选行转收藏（D4-4）。
 * 后端「写收藏 + 删购物车行」在一个事务里，成功就会顺带把行删掉;
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
  <div class="mx-auto max-w-[960px] space-y-3 p-4">
    <div class="flex items-baseline justify-between">
      <h1 class="text-lg">购物车</h1>
      <RouterLink v-if="cart.rows.length" to="/goods" class="link text-sm text-base-content/60">
        继续挑选商品
      </RouterLink>
    </div>

    <p v-if="notice" class="notice text-success" data-testid="notice">{{ notice }}</p>
    <p v-if="actionError" class="text-error">{{ actionError }}</p>

    <p v-if="cart.loading && !cart.rows.length" class="muted">加载中…</p>
    <p v-else-if="cart.errorText" class="text-error">{{ cart.errorText }}</p>

    <EmptyState
      v-else-if="!cart.rows.length"
      title="购物车是空的"
      description="还没有加入任何商品，去看看有什么想买的。"
    >
      <RouterLink class="btn btn-primary btn-sm" to="/goods">去逛逛</RouterLink>
    </EmptyState>

    <template v-else>
      <p v-if="cart.invalidRows.length" class="warn-bar alert alert-warning alert-soft" data-testid="warn-bar">
        有 {{ cart.invalidRows.length }} 件商品已下架或缺货，已取消勾选，无法下单。可以移除或稍后再看。
      </p>

      <div class="panel overflow-hidden p-0">
        <div class="flex items-center gap-3 border-b border-base-300 px-4 py-3">
          <label class="check-all flex cursor-pointer items-center gap-1.5">
            <input
              type="checkbox"
              class="checkbox checkbox-sm"
              :checked="cart.allChecked"
              @change="cart.toggleAll(($event.target as HTMLInputElement).checked)"
            />
            <span>全选</span>
          </label>
          <span class="muted text-xs">共 {{ cart.rows.length }} 件商品</span>
        </div>

        <CartRow
          v-for="row in cart.rows"
          :key="row.goods_id"
          :row="row"
          @quantity="onQuantity"
          @step="onStep"
          @toggle="onToggle"
          @remove="onRemove"
        />
      </div>

      <CartSummaryBar
        :all-checked="cart.allChecked"
        :line-count="checkedLineCount"
        :total-quantity="cart.totalQuantity"
        :total-amount="cart.totalAmount"
        :moving-fav="movingFav"
        :submitting="submitting"
        @toggle-all="cart.toggleAll"
        @move-fav="onMoveToFavorites"
        @checkout="onSubmit"
      />
    </template>
  </div>
</template>