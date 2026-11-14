<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { ApiError } from '../api/http'
import {
  cancelOrder,
  completeOrder,
  confirmOrder,
  getOrder,
  listOrderItems,
  prepareOrder,
  shipOrder,
} from '../api/orders'
import { ApiCode } from '../api/types'
import type { OrderData, OrderItemData, OrderStatus, OrderStatusData } from '../api/types'
import AppIcon from '../components/common/AppIcon.vue'
import ConfirmDialog from '../components/common/ConfirmDialog.vue'
import StatusTag from '../components/common/StatusTag.vue'
import OrderProgress from '../components/order/OrderProgress.vue'
import {
  ORDER_STATUS_HINTS,
  ORDER_STATUS_LABELS,
  ORDER_STATUS_TONES,
} from '../constants/labels'
import type { TagTone } from '../constants/labels'
import { M10 } from '../constants/messages'
import { useRequestSequence } from '../composables/useRequestSequence'
import { useToast } from '../composables/useToast'
import { errorMessage } from '../utils/error'
import { formatDateTime, formatPrice } from '../utils/format'

/**
 * 订单详情。卖家端只有卖家一个角色，操作只按订单状态判断，不涉及角色维度。
 *
 * 订单头与明细是两次独立请求：任一块失败都不该拖垮另一块，所以各自持有
 * loading / error / 重试，互不阻塞。
 */
const route = useRoute()
const toast = useToast()

const orderId = computed(() => (typeof route.params.id === 'string' ? route.params.id : ''))

const order = ref<OrderData | null>(null)
const orderLoading = ref(false)
const orderError = ref<string | null>(null)
/** 订单不存在（60001）时重试没有意义，只给返回列表的出口。 */
const orderMissing = ref(false)

const items = ref<OrderItemData[]>([])
const itemsLoading = ref(false)
const itemsError = ref<string | null>(null)

const orderSeq = useRequestSequence()
const itemsSeq = useRequestSequence()

async function loadOrder(): Promise<void> {
  const token = orderSeq.begin()
  orderLoading.value = true
  orderError.value = null
  orderMissing.value = false
  try {
    const data = await getOrder(orderId.value)
    if (!orderSeq.isCurrent(token)) {
      return
    }
    order.value = data
  } catch (caught) {
    if (!orderSeq.isCurrent(token)) {
      return
    }
    order.value = null
    orderError.value = errorMessage(caught)
    orderMissing.value = caught instanceof ApiError && caught.code === ApiCode.ORDER_NOT_FOUND
  } finally {
    if (orderSeq.isCurrent(token)) {
      orderLoading.value = false
    }
  }
}

async function loadItems(): Promise<void> {
  const token = itemsSeq.begin()
  itemsLoading.value = true
  itemsError.value = null
  try {
    const data = await listOrderItems(orderId.value)
    if (!itemsSeq.isCurrent(token)) {
      return
    }
    items.value = data.items
  } catch (caught) {
    if (!itemsSeq.isCurrent(token)) {
      return
    }
    items.value = []
    itemsError.value = errorMessage(caught)
  } finally {
    if (itemsSeq.isCurrent(token)) {
      itemsLoading.value = false
    }
  }
}

function loadAll(): void {
  void loadOrder()
  void loadItems()
}

onMounted(loadAll)
watch(orderId, loadAll)

const statusTone = computed<TagTone>(() =>
  order.value ? ORDER_STATUS_TONES[order.value.status] : 'default',
)

/** 终态没有任何出边：既不推进也不能取消。 */
const isTerminal = computed(
  () => order.value?.status === 'COMPLETED' || order.value?.status === 'CANCELED',
)

/** 明细合计；订单头没加载出来时也能从明细算出一个数，避免整页空白。 */
const itemsTotal = computed(() => items.value.reduce((sum, item) => sum + item.amount, 0))
const quantityTotal = computed(() => items.value.reduce((sum, item) => sum + item.quantity, 0))

// ---- 五个动作：确认 / 备货完成 / 开始发货 / 交易完成 / 取消 ----

type ActionKey = 'confirm' | 'prepare' | 'ship' | 'complete' | 'cancel'

interface OrderAction {
  key: ActionKey
  label: string
  /** 二次确认文案：说清做什么、之后会怎样。 */
  message: string
  /** 成功后的提示。 */
  done: string
  danger?: boolean
}

/** 推进只能按序走一格：状态决定「下一步」是什么，界面上不存在跳级入口。 */
const NEXT_ACTION: Partial<Record<OrderStatus, OrderAction>> = {
  PENDING_CONFIRM: {
    key: 'confirm',
    label: '确认订单',
    message: M10.confirmOrder,
    done: M10.orderConfirmed,
  },
  CONFIRMED: {
    key: 'prepare',
    label: '标记备货完成',
    message: M10.confirmPrepare,
    done: M10.orderPrepared,
  },
  PREPARED: {
    key: 'ship',
    label: '开始发货',
    message: M10.confirmShip,
    done: M10.orderShipped,
  },
  SHIPPED: {
    key: 'complete',
    label: '交易完成',
    message: M10.confirmComplete,
    done: M10.orderCompleted,
  },
}

const CANCEL_ACTION: OrderAction = {
  key: 'cancel',
  label: '取消订单',
  message: M10.confirmCancelOrder,
  done: M10.orderCanceled,
  danger: true,
}

const RUNNERS: Record<ActionKey, (orderId: string) => Promise<OrderStatusData>> = {
  confirm: confirmOrder,
  prepare: prepareOrder,
  ship: shipOrder,
  complete: completeOrder,
  cancel: cancelOrder,
}

const nextAction = computed<OrderAction | null>(() =>
  order.value ? (NEXT_ACTION[order.value.status] ?? null) : null,
)

const confirmOpen = ref(false)
const pendingAction = ref<OrderAction | null>(null)
/** 提交中：按钮与弹窗一起禁用，避免重复点击推进两次。 */
const acting = ref(false)

function ask(action: OrderAction | null): void {
  if (!action || acting.value) {
    return
  }
  pendingAction.value = action
  confirmOpen.value = true
}

function closeConfirm(): void {
  if (acting.value) {
    return
  }
  confirmOpen.value = false
  pendingAction.value = null
}

async function runAction(): Promise<void> {
  const action = pendingAction.value
  if (!action || acting.value) {
    return
  }
  acting.value = true
  try {
    await RUNNERS[action.key](orderId.value)
    toast.success(action.done)
    confirmOpen.value = false
    // 就地刷新状态；明细不随推进变化，不重复请求。
    await loadOrder()
  } catch (caught) {
    toast.error(errorMessage(caught))
    confirmOpen.value = false
    // 被拒时状态可能已被别处改动（例如买家已取消），重拉一次再决定按钮。
    await loadOrder()
  } finally {
    acting.value = false
    pendingAction.value = null
  }
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <h1 class="text-lg font-semibold">订单详情</h1>
      <RouterLink to="/orders" class="btn btn-sm btn-outline">
        <AppIcon name="arrow-left" />
        返回订单列表
      </RouterLink>
    </header>

    <!-- 订单头：单独失败与重试 -->
    <div v-if="orderError" role="alert" class="alert alert-error alert-soft text-sm">
      <AppIcon name="forbidden" />
      <span>{{ orderError }}</span>
      <div class="ml-auto flex gap-2">
        <RouterLink v-if="orderMissing" to="/orders" class="btn btn-xs btn-outline">返回列表</RouterLink>
        <button v-else type="button" class="btn btn-xs btn-outline" @click="loadOrder">重试</button>
      </div>
    </div>
    <div v-else-if="orderLoading && !order" class="panel text-sm text-base-content/60">
      订单加载中…
    </div>

    <div v-else-if="order" class="panel flex flex-col gap-5">
      <div class="flex flex-wrap items-center gap-3">
        <h2 class="font-mono text-base font-semibold">{{ order.order_no }}</h2>
        <StatusTag :label="ORDER_STATUS_LABELS[order.status]" :tone="statusTone" />
        <span class="text-xs text-base-content/60">共 {{ order.item_count }} 项商品</span>
      </div>

      <!-- 流程图只在未结束时出现：终态没有「下一步」，画出来反而误导 -->
      <OrderProgress v-if="!isTerminal" :status="order.status" />

      <dl class="grid grid-cols-1 gap-x-8 gap-y-3 text-sm sm:grid-cols-2 lg:grid-cols-4">
        <div class="flex flex-col gap-0.5">
          <dt class="text-xs text-base-content/60">买家</dt>
          <dd class="m-0 font-medium">{{ order.buyer.username }}</dd>
        </div>
        <div class="flex flex-col gap-0.5">
          <dt class="text-xs text-base-content/60">订单金额</dt>
          <dd class="m-0 font-semibold text-error">{{ formatPrice(order.total_amount) }}</dd>
        </div>
        <div class="flex flex-col gap-0.5">
          <dt class="text-xs text-base-content/60">下单时间</dt>
          <dd class="m-0">{{ formatDateTime(order.create_at) }}</dd>
        </div>
        <div class="flex flex-col gap-0.5">
          <dt class="text-xs text-base-content/60">最后更新</dt>
          <dd class="m-0">{{ formatDateTime(order.update_at) }}</dd>
        </div>
      </dl>

      <div
        class="rounded-box border border-base-300 bg-base-200 px-3 py-2 text-sm"
        data-testid="order-hint"
      >
        <p class="m-0">{{ ORDER_STATUS_HINTS[order.status] }}</p>
        <p v-if="!isTerminal" class="m-0 mt-1 text-xs text-base-content/60">
          交易完成前可以取消；取消是终态，不能恢复。
        </p>
      </div>

      <!-- 动作区：取消在左、推进一步在右；终态时整行不渲染 -->
      <div
        v-if="!isTerminal"
        class="flex flex-wrap items-center justify-end gap-2 border-t border-base-300 pt-4"
      >
        <button
          type="button"
          class="btn btn-sm btn-outline btn-error"
          :disabled="acting"
          data-testid="order-cancel"
          @click="ask(CANCEL_ACTION)"
        >
          取消订单
        </button>
        <button
          v-if="nextAction"
          type="button"
          class="btn btn-sm btn-primary"
          :disabled="acting"
          data-testid="order-next"
          @click="ask(nextAction)"
        >
          {{ acting ? '处理中…' : nextAction.label }}
        </button>
      </div>
    </div>

    <!-- 明细：独立失败与重试；订单本身不存在时不必再问一次明细 -->
    <div v-if="!orderMissing" class="panel flex flex-col gap-3">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <h2 class="text-sm font-semibold">商品明细</h2>
        <span v-if="items.length > 0" class="text-xs text-base-content/60">
          {{ items.length }} 行 · 共 {{ quantityTotal }} 件
        </span>
      </div>

      <div v-if="itemsError" role="alert" class="alert alert-error alert-soft text-sm">
        <AppIcon name="forbidden" />
        <span>{{ itemsError }}</span>
        <button type="button" class="btn btn-xs btn-outline ml-auto" @click="loadItems">重试</button>
      </div>

      <p v-else-if="itemsLoading && items.length === 0" class="text-sm text-base-content/60">
        明细加载中…
      </p>

      <div v-else-if="items.length === 0" class="text-sm text-base-content/60">
        {{ M10.emptyOrderItems }}
      </div>

      <template v-else>
        <div class="overflow-x-auto">
          <table class="table table-sm">
            <thead>
              <tr>
                <th>商品</th>
                <th class="text-right">单价</th>
                <th class="text-right">数量</th>
                <th class="text-right">小计</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in items" :key="item.goods_id">
                <td>
                  <RouterLink
                    :to="'/goods/' + item.goods_id"
                    class="link link-hover font-medium"
                    :title="`查看商品：${item.goods_name}`"
                  >
                    {{ item.goods_name }}
                  </RouterLink>
                </td>
                <td class="text-right tabular-nums">{{ formatPrice(item.unit_price) }}</td>
                <td class="text-right tabular-nums">{{ item.quantity }}</td>
                <td class="text-right tabular-nums">{{ formatPrice(item.amount) }}</td>
              </tr>
            </tbody>
            <tfoot>
              <tr>
                <th colspan="3" class="text-right font-normal text-base-content/60">合计</th>
                <th class="text-right tabular-nums">
                  {{ formatPrice(order ? order.total_amount : itemsTotal) }}
                </th>
              </tr>
            </tfoot>
          </table>
        </div>
        <p v-if="!order" class="m-0 text-xs text-base-content/60">
          订单头未加载成功，合计按明细小计相加得出。
        </p>
      </template>
    </div>

    <ConfirmDialog
      :open="confirmOpen"
      :title="pendingAction?.danger ? '取消订单' : '请确认'"
      :message="pendingAction?.message ?? ''"
      :confirm-text="pendingAction?.label ?? '确认'"
      :cancel-text="pendingAction?.danger ? '再想想' : '取消'"
      :danger="pendingAction?.danger ?? false"
      :loading="acting"
      @confirm="runAction"
      @cancel="closeConfirm"
    />
  </div>
</template>
