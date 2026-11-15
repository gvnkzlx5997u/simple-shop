<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { ApiError } from '../api/http'
import { cancelOrder, fetchOrder, fetchOrderItems } from '../api/order'
import type { OrderData, OrderItemData } from '../api/types'
import EmptyState from '../components/common/EmptyState.vue'
import StatusTag from '../components/common/StatusTag.vue'
import OrderItemsTable from '../components/order/OrderItemsTable.vue'
import { describeError } from '../utils/errorText'
import { formatMoney, formatTime } from '../utils/format'
import { canCancelByBuyer, isTerminal, ORDER_STATUS_TONES, orderStatusText } from '../utils/orderStatus'

const route = useRoute()
const orderId = computed(() => String(route.params.id ?? ''))

/**
 * 订单头与明细**分开加载、分开报错**（D2-3 的硬要求）。
 * 明细接口挂了不该让整页变成错误页——订单号、状态、金额这些照样能读。
 */
const order = ref<OrderData | null>(null)
const orderLoading = ref(false)
const orderError = ref('')
const notFound = ref(false)

const items = ref<OrderItemData[]>([])
const itemsLoading = ref(false)
const itemsError = ref('')

const cancelling = ref(false)
const confirming = ref(false)
const notice = ref('')
const actionError = ref('')

const cancellable = computed(() => !!order.value && canCancelByBuyer(order.value.status))
const finished = computed(() => !!order.value && isTerminal(order.value.status))

async function loadOrder(): Promise<void> {
  orderLoading.value = true
  orderError.value = ''
  notFound.value = false
  try {
    order.value = await fetchOrder(orderId.value)
  } catch (err) {
    order.value = null
    // 别人的订单后端也按「不存在」返回，所以 404 / 60001 都当空态处理。
    notFound.value =
      err instanceof ApiError && (err.code === 60001 || err.httpStatus === 404)
    if (!notFound.value) {
      orderError.value = describeError(err)
    }
  } finally {
    orderLoading.value = false
  }
}

async function loadItems(): Promise<void> {
  itemsLoading.value = true
  itemsError.value = ''
  try {
    const data = await fetchOrderItems(orderId.value)
    // 顺序直接沿用后端：后端按商品名升序（`OrderByGoodsNameAscIdAsc`）返回，前端不重排。
    items.value = data.items ?? []
  } catch (err) {
    itemsError.value = describeError(err)
    items.value = []
  } finally {
    itemsLoading.value = false
  }
}

async function loadAll(): Promise<void> {
  await loadOrder()
  if (order.value) {
    void loadItems()
  }
}

/** 第一次点先问，第二次点才真取消——比 window.confirm 可控，也不会被浏览器拦。 */
function askCancel(): void {
  actionError.value = ''
  notice.value = ''
  confirming.value = true
}

async function doCancel(): Promise<void> {
  if (!order.value || cancelling.value) return
  cancelling.value = true
  actionError.value = ''
  try {
    const result = await cancelOrder(order.value.order_id)
    // 就地更新状态，不整页重拉：明细和金额都没变，重拉只会闪一下。
    order.value = { ...order.value, status: result.status, update_at: result.update_at }
    notice.value = '订单已取消'
    confirming.value = false
  } catch (err) {
    // 后端拒绝时按错误码给中文（60003 已发货不能取消等）。
    actionError.value = describeError(err)
  } finally {
    cancelling.value = false
  }
}

onMounted(loadAll)
</script>

<template>
  <div class="mx-auto max-w-[960px] space-y-3 p-4">
    <div class="flex items-baseline justify-between">
      <h1 class="text-lg">订单详情</h1>
      <RouterLink to="/orders" class="link text-xs text-base-content/60">返回订单列表</RouterLink>
    </div>

    <p v-if="orderLoading" class="muted">加载中…</p>

    <EmptyState
      v-else-if="notFound"
      title="订单不存在"
      description="这笔订单可能已被删除，或者不属于当前账号。"
    >
      <RouterLink class="btn btn-primary btn-sm" to="/orders">返回我的订单</RouterLink>
    </EmptyState>

    <!-- 订单头自己也要能重试：明细挂了有重试、头挂了却只能干看，就不叫「两块独立处理」了 -->
    <div v-else-if="orderError" class="panel flex items-center gap-3">
      <p class="flex-1 text-error">{{ orderError }}</p>
      <button class="btn btn-outline btn-sm" @click="loadAll">重试</button>
    </div>

    <template v-else-if="order">
      <p v-if="notice" class="notice text-success" data-testid="notice">{{ notice }}</p>
      <p v-if="actionError" class="text-error">{{ actionError }}</p>

      <div class="panel">
        <div class="mb-3 flex items-center gap-2.5">
          <span class="text-[15px] font-semibold tracking-wide">{{ order.order_no }}</span>
          <StatusTag :label="orderStatusText(order.status)" :tone="ORDER_STATUS_TONES[order.status]" />
        </div>
        <div class="grid grid-cols-[88px_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
          <span class="muted">下单时间</span>
          <span>{{ formatTime(order.create_at) }}</span>
          <span class="muted">最近更新</span>
          <span>{{ formatTime(order.update_at) }}</span>
          <span class="muted">商品件数</span>
          <span>{{ order.item_count }} 件</span>
          <span class="muted">订单总额</span>
          <span class="amount font-semibold text-error" data-testid="order-total">
            ¥{{ formatMoney(order.total_amount) }}
          </span>
        </div>
      </div>

      <div class="panel">
        <h2 class="mb-2.5 text-[15px]">商品明细</h2>

        <p v-if="itemsLoading" class="muted">明细加载中…</p>

        <div v-else-if="itemsError" class="flex items-center gap-3">
          <p class="flex-1 text-error">{{ itemsError }}</p>
          <button class="btn btn-outline btn-sm" @click="loadItems">重试</button>
        </div>

        <OrderItemsTable v-else-if="items.length" :items="items" />

        <p v-else class="muted">这笔订单没有明细</p>
      </div>

      <div v-if="cancellable || finished" class="panel flex flex-wrap items-center gap-3">
        <template v-if="cancellable">
          <template v-if="confirming">
            <span class="muted min-w-[160px] flex-1">确认取消这笔订单吗？取消后不可恢复。</span>
            <button class="btn btn-error btn-sm" :disabled="cancelling" @click="doCancel">
              {{ cancelling ? '取消中…' : '确认取消' }}
            </button>
            <button class="btn btn-ghost btn-sm" :disabled="cancelling" @click="confirming = false">
              再想想
            </button>
          </template>
          <template v-else>
            <span class="muted min-w-[160px] flex-1">发货前可以取消，已发货后不能取消。</span>
            <button class="btn btn-outline btn-sm" @click="askCancel">取消订单</button>
          </template>
        </template>
        <span v-else-if="finished" class="muted">这笔订单已经结束，不能再操作。</span>
      </div>
    </template>
  </div>
</template>