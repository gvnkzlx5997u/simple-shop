<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { ApiError } from '../api/http'
import { ApiCode } from '../api/types'
import EmptyState from '../components/common/EmptyState.vue'
import StatusTag from '../components/common/StatusTag.vue'
import { useAddressStore } from '../stores/address'
import { useCartStore } from '../stores/cart'
import { describeSubmitError } from '../utils/errorText'
import { formatMoney } from '../utils/format'

const cart = useCartStore()
const addresses = useAddressStore()
const router = useRouter()

const selectedAddressId = ref('')
const submitting = ref(false)
const actionError = ref('')

const lines = computed(() => cart.checkedRows)
const loading = computed(() => cart.loading || addresses.loading)

/**
 * 勾选状态存在 localStorage，所以结算页自己拉一次就能还原清单——
 * 直接刷新本页、或从别处跳进来都不会丢，不依赖购物车页留在内存里的状态。
 */
async function loadAll(): Promise<void> {
  await Promise.all([cart.load(), addresses.load()])
  selectedAddressId.value = addresses.defaultAddress?.id ?? addresses.items[0]?.id ?? ''
}

async function submit(): Promise<void> {
  if (!selectedAddressId.value || lines.value.length === 0 || submitting.value) return
  submitting.value = true
  actionError.value = ''
  try {
    const placed = await cart.submitOrder(selectedAddressId.value)
    // 成功后跳详情：那里有单号、状态与逐件明细，购物车行已由 store 清掉。
    void router.push({ name: 'order-detail', params: { id: placed.order_id } })
  } catch (err) {
    actionError.value = describeSubmitError(err)
    // 失败多半是「提交瞬间商品下架 / 库存变了」，重拉让失效行当场变灰；
    // 有效行的勾选与数量由 syncLocal 原样保留。
    await cart.load()
    // 地址被删掉时后端回 80004，重拉地址簿并重新预选，避免再点一次还是同一个死 id。
    if (err instanceof ApiError && err.code === ApiCode.ADDRESS_REQUIRED) {
      await addresses.load()
      selectedAddressId.value = addresses.defaultAddress?.id ?? addresses.items[0]?.id ?? ''
    }
  } finally {
    submitting.value = false
  }
}

onMounted(loadAll)
</script>

<template>
  <div class="mx-auto max-w-[960px] space-y-3 p-4">
    <div class="flex items-baseline justify-between">
      <h1 class="text-lg">结算</h1>
      <RouterLink to="/cart" class="link text-xs text-base-content/60">返回购物车</RouterLink>
    </div>

    <p v-if="actionError" class="text-error">{{ actionError }}</p>

    <p v-if="loading && !lines.length" class="muted">加载中…</p>

    <div v-else-if="cart.errorText" class="panel flex items-center gap-3">
      <p class="flex-1 text-error">{{ cart.errorText }}</p>
      <button class="btn btn-outline btn-sm" @click="loadAll">重试</button>
    </div>

    <EmptyState
      v-else-if="!lines.length"
      title="没有要结算的商品"
      description="回购物车勾选想买的商品，再来结算。"
    >
      <RouterLink class="btn btn-primary btn-sm" to="/cart">去购物车</RouterLink>
    </EmptyState>

    <div v-else-if="addresses.errorText" class="panel flex items-center gap-3">
      <p class="flex-1 text-error">{{ addresses.errorText }}</p>
      <button class="btn btn-outline btn-sm" @click="loadAll">重试</button>
    </div>

    <EmptyState
      v-else-if="!addresses.items.length"
      title="还没有收货地址"
      description="下单必须先选一个已有地址，去地址管理新建一条再回来。"
    >
      <RouterLink class="btn btn-primary btn-sm" to="/addresses">去地址管理</RouterLink>
    </EmptyState>

    <template v-else>
      <div class="panel">
        <div class="mb-2.5 flex items-baseline justify-between">
          <h2 class="text-[15px]">收货地址</h2>
          <RouterLink to="/addresses" class="link text-xs text-base-content/60">
            管理地址
          </RouterLink>
        </div>

        <label
          v-for="item in addresses.items"
          :key="item.id"
          class="mb-2 flex cursor-pointer items-start gap-2.5 rounded-field border border-base-300 px-3 py-2 last:mb-0"
        >
          <input
            v-model="selectedAddressId"
            type="radio"
            name="checkout-address"
            class="radio radio-sm mt-0.5"
            :value="item.id"
            data-testid="checkout-address"
          />
          <span class="min-w-0 flex-1">
            <span class="flex flex-wrap items-center gap-2">
              <span class="font-semibold">{{ item.receiver_name }}</span>
              <span class="muted text-sm">{{ item.receiver_phone }}</span>
              <StatusTag v-if="item.is_default" label="默认" tone="info" />
            </span>
            <span class="mt-0.5 block text-sm">{{ item.detail_address }}</span>
          </span>
        </label>

        <p class="muted mt-2 text-xs">这里只能选已有地址，新建或修改请去地址管理。</p>
      </div>

      <div class="panel">
        <h2 class="mb-2.5 text-[15px]">商品清单</h2>
        <div class="overflow-x-auto">
          <table class="table table-sm">
            <thead>
              <tr>
                <th class="text-left">商品</th>
                <th class="text-right">单价</th>
                <th class="text-right">数量</th>
                <th class="text-right">小计</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in lines" :key="row.goods_id">
                <td class="text-left">{{ row.name }}</td>
                <td class="text-right whitespace-nowrap">¥{{ formatMoney(row.price) }}</td>
                <td class="text-right">{{ row.quantity }}</td>
                <td class="text-right whitespace-nowrap">¥{{ formatMoney(row.price * row.quantity) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="panel flex flex-wrap items-center gap-4">
        <span class="muted">共 {{ lines.length }} 件，{{ cart.totalQuantity }} 个</span>
        <span class="ml-auto">
          合计
          <strong class="text-lg text-error" data-testid="checkout-total">
            ¥{{ formatMoney(cart.totalAmount) }}
          </strong>
        </span>
        <button
          class="btn btn-primary"
          data-testid="checkout-submit"
          :disabled="submitting || !selectedAddressId"
          @click="submit"
        >
          {{ submitting ? '提交中…' : '提交订单' }}
        </button>
      </div>

      <p class="muted text-xs">提交后订单进入待支付，24 小时内未支付会被系统取消。</p>
    </template>
  </div>
</template>