<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { listSellerAftersales } from '../api/aftersale'
import { ApiError } from '../api/http'
import {
  cancelOrder,
  confirmOrder,
  getOrder,
  listOrderItems,
  prepareOrder,
  shipOrder,
  updateOrderLogistics,
} from '../api/orders'
import { ApiCode } from '../api/types'
import type {
  AftersaleData,
  OrderData,
  OrderItemData,
  OrderStatusData,
  ShipLogisticsRequest,
} from '../api/types'
import AppIcon from '../components/common/AppIcon.vue'
import ConfirmDialog from '../components/common/ConfirmDialog.vue'
import StatusTag from '../components/common/StatusTag.vue'
import LogisticsDialog from '../components/order/LogisticsDialog.vue'
import OrderProgress from '../components/order/OrderProgress.vue'
import {
  canCancelOrder,
  isPendingPayment,
  isTerminalOrder,
  primaryOrderAction,
} from '../components/order/orderActions'
import type { OrderActionKey } from '../components/order/orderActions'
import {
  AFTERSALE_RESULT_LABELS,
  AFTERSALE_RESULT_TONES,
  AFTERSALE_STATUS_LABELS,
  AFTERSALE_STATUS_TONES,
  ORDER_STATUS_HINTS,
  ORDER_STATUS_LABELS,
  ORDER_STATUS_TONES,
  PAYMENT_CHANNEL_LABELS,
} from '../constants/labels'
import type { TagTone } from '../constants/labels'
import { M10 } from '../constants/messages'
import { useRequestSequence } from '../composables/useRequestSequence'
import { useToast } from '../composables/useToast'
import { errorMessage } from '../utils/error'
import { formatDateTime, formatPrice } from '../utils/format'

/**
 * 订单详情
 *
 * 「能点哪些按钮」来自 orderActions
 *
 * 订单头、明细、售后是三次独立请求，各自 loading / error / 重试。
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

const aftersales = ref<AftersaleData[]>([])
const aftersalesLoading = ref(false)
const aftersalesError = ref<string | null>(null)

const orderSeq = useRequestSequence()
const itemsSeq = useRequestSequence()
const aftersalesSeq = useRequestSequence()

async function loadAftersales(orderNo: string): Promise<void> {
  const token = aftersalesSeq.begin()
  aftersalesLoading.value = true
  aftersalesError.value = null
  try {
    const data = await listSellerAftersales({ order_no: orderNo, page: 1, page_size: 50 })
    if (!aftersalesSeq.isCurrent(token)) {
      return
    }
    aftersales.value = data.items
  } catch (caught) {
    if (!aftersalesSeq.isCurrent(token)) {
      return
    }
    aftersales.value = []
    aftersalesError.value = errorMessage(caught)
  } finally {
    if (aftersalesSeq.isCurrent(token)) {
      aftersalesLoading.value = false
    }
  }
}

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
    // 售后按订单号查：售后单里存的是 order_no，契约 §4.6 要求单独查、不内嵌。
    void loadAftersales(data.order_no)
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

//终态不画流程图
const terminal = computed(() => (order.value ? isTerminalOrder(order.value.status) : false))
const pendingPayment = computed(() => (order.value ? isPendingPayment(order.value.status) : false))

// ---- 动作：确认 / 备货完成 / 发货 / 改物流 / 取消 ----

interface ActionCopy {
  /** 二次确认文案：说清做什么、之后会怎样。 */
  message: string
  /** 成功后的提示。 */
  done: string
}

const ACTION_COPY: Record<OrderActionKey, ActionCopy | null> = {
  confirm: { message: M10.confirmOrder, done: M10.orderConfirmed },
  prepare: { message: M10.confirmPrepare, done: M10.orderPrepared },
  // 发货与改物流要先填表单，说明文字在弹窗里，不再套一层二次确认。
  ship: null,
  editLogistics: null,
  cancel: { message: M10.confirmCancelOrder, done: M10.orderCanceled },
}

const RUNNERS: Partial<Record<OrderActionKey, (id: string) => Promise<OrderStatusData>>> = {
  confirm: confirmOrder,
  prepare: prepareOrder,
  cancel: cancelOrder,
}

const nextAction = computed(() => (order.value ? primaryOrderAction(order.value.status) : null))
const cancelable = computed(() => (order.value ? canCancelOrder(order.value.status) : false))

const confirmOpen = ref(false)
const pendingKey = ref<OrderActionKey | null>(null)

/** 物流弹窗：发货与改物流共用，区别只在 mode 与初始值。 */
const logisticsOpen = ref(false)
const logisticsMode = ref<'ship' | 'edit'>('ship')

const logisticsInitial = computed(() => ({
  carrier: order.value?.carrier ?? '',
  trackingNo: order.value?.tracking_no ?? '',
  remark: order.value?.logistics_remark ?? '',
}))

/** 提交中：按钮与弹窗一起禁用，避免重复点击推进两次。 */
const acting = ref(false)
const logisticsBusy = ref(false)

const pendingMessage = computed(() => {
  if (pendingKey.value === 'cancel') {
    // 待支付单取消不退款，文案要跟着状态走，否则会让人以为要退钱。
    return order.value?.paid_at ? M10.confirmCancelOrder : M10.confirmCancelUnpaidOrder
  }
  return pendingKey.value ? (ACTION_COPY[pendingKey.value]?.message ?? '') : ''
})

function ask(key: OrderActionKey | null): void {
  if (!key || acting.value) {
    return
  }
  if (key === 'ship' || key === 'editLogistics') {
    logisticsMode.value = key === 'ship' ? 'ship' : 'edit'
    logisticsOpen.value = true
    return
  }
  pendingKey.value = key
  confirmOpen.value = true
}

function closeConfirm(): void {
  if (acting.value) {
    return
  }
  confirmOpen.value = false
  pendingKey.value = null
}

async function runAction(): Promise<void> {
  const key = pendingKey.value
  const runner = key ? RUNNERS[key] : undefined
  if (!key || !runner || acting.value) {
    return
  }
  acting.value = true
  try {
    await runner(orderId.value)
    toast.success(ACTION_COPY[key]?.done ?? '操作成功')
    confirmOpen.value = false
    // 就地刷新状态；明细不随推进变化，不重复请求。售后条数可能变，一并刷新。
    await loadOrder()
  } catch (caught) {
    toast.error(errorMessage(caught))
    confirmOpen.value = false
    // 被拒时状态可能已被别处改动（例如买家已取消），重拉一次再决定按钮。
    await loadOrder()
  } finally {
    acting.value = false
    pendingKey.value = null
  }
}

/** 发货与改物流：同一个提交入口，按 mode 决定调哪个接口。 */
async function submitLogistics(payload: ShipLogisticsRequest): Promise<void> {
  if (logisticsBusy.value) {
    return
  }
  logisticsBusy.value = true
  try {
    if (logisticsMode.value === 'ship') {
      await shipOrder(orderId.value, payload)
      toast.success(M10.orderShipped)
    } else {
      await updateOrderLogistics(orderId.value, payload)
      toast.success(M10.logisticsUpdated)
    }
    logisticsOpen.value = false
    await loadOrder()
  } catch (caught) {
    toast.error(errorMessage(caught))
    logisticsOpen.value = false
    await loadOrder()
  } finally {
    logisticsBusy.value = false
  }
}

// ---- 明细合计：订单头没加载出来时也能从明细算出一个数，避免整页空白 ----

const itemsTotal = computed(() => items.value.reduce((sum, item) => sum + item.amount, 0))
const quantityTotal = computed(() => items.value.reduce((sum, item) => sum + item.quantity, 0))
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
        <RouterLink v-if="orderMissing" to="/orders" class="btn btn-xs btn-outline">
          返回列表
        </RouterLink>
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
        <span v-if="order.aftersale_count > 0" class="text-xs text-base-content/60">
          · {{ order.aftersale_count }} 条售后
        </span>
      </div>

      <!-- 流程图只在未结束时出现：终态没有「下一步」 -->
      <OrderProgress v-if="!terminal" :status="order.status" />

      <dl class="grid grid-cols-1 gap-x-8 gap-y-3 text-sm sm:grid-cols-2 lg:grid-cols-4">
        <div class="flex flex-col gap-0.5">
          <dt class="text-xs text-base-content/60">买家</dt>
          <dd class="m-0 font-medium">{{ order.buyer.username }}</dd>
        </div>
        <div class="flex flex-col gap-0.5">
          <dt class="text-xs text-base-content/60">订单金额</dt>
          <dd class="m-0 font-semibold text-error">{{ formatPrice(order.total_amount) }}</dd>
        </div>
        <div v-if="order.refunded_amount > 0" class="flex flex-col gap-0.5">
          <dt class="text-xs text-base-content/60">已退款</dt>
          <dd class="m-0 font-semibold text-warning">{{ formatPrice(order.refunded_amount) }}</dd>
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
        <p v-if="cancelable && order.paid_at" class="m-0 mt-1 text-xs text-base-content/60">
          取消是终态，不能恢复；已付款（含已发货）的订单取消会按实付金额全额原路退款。
        </p>
        <p v-else-if="cancelable" class="m-0 mt-1 text-xs text-base-content/60">
          取消是终态，不能恢复；这笔订单还没付款，取消不产生退款、也不改库存。
        </p>
      </div>

      <!-- 收货信息：下单时的快照，地址簿后续改名或删除都不影响这里 -->
      <section class="flex flex-col gap-2">
        <h3 class="text-sm font-semibold">收货信息</h3>
        <dl class="grid grid-cols-1 gap-x-8 gap-y-2 text-sm sm:grid-cols-3">
          <div class="flex flex-col gap-0.5">
            <dt class="text-xs text-base-content/60">收货人</dt>
            <dd class="m-0">{{ order.receiver_name ?? '—' }}</dd>
          </div>
          <div class="flex flex-col gap-0.5">
            <dt class="text-xs text-base-content/60">联系电话</dt>
            <dd class="m-0">{{ order.receiver_phone ?? '—' }}</dd>
          </div>
          <div class="flex min-w-0 flex-col gap-0.5">
            <dt class="text-xs text-base-content/60">收货地址</dt>
            <dd class="m-0 break-words">{{ order.receiver_address ?? '—' }}</dd>
          </div>
        </dl>
      </section>

      <!-- 支付信息：未支付时后端给 null，这里只说清状态，不摆一张空表 -->
      <section class="flex flex-col gap-2">
        <h3 class="text-sm font-semibold">支付信息</h3>
        <p v-if="!order.paid_at" class="m-0 text-sm text-base-content/60">
          买家尚未付款；待支付订单超过 24 小时未付款会被系统自动取消。
        </p>
        <p v-if="!order.paid_at && order.pay_deadline" class="m-0 text-xs text-base-content/60">
          付款截止：{{ formatDateTime(order.pay_deadline) }}
        </p>
        <!-- 支付渠道/时间只在真有付款时画：未支付单挂一张全是「—」的表只会让人以为漏了数据 -->
        <dl v-if="order.paid_at" class="grid grid-cols-1 gap-x-8 gap-y-2 text-sm sm:grid-cols-3">
          <div class="flex flex-col gap-0.5">
            <dt class="text-xs text-base-content/60">支付渠道</dt>
            <dd class="m-0">
              {{ order.payment_channel ? PAYMENT_CHANNEL_LABELS[order.payment_channel] : '—' }}
            </dd>
          </div>
          <div class="flex flex-col gap-0.5">
            <dt class="text-xs text-base-content/60">支付时间</dt>
            <dd class="m-0">{{ formatDateTime(order.paid_at) }}</dd>
          </div>
        </dl>
      </section>

      <!-- 物流：发货后才有；多包裹单号用换行展示，不折成一长串 -->
      <section class="flex flex-col gap-2">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <h3 class="text-sm font-semibold">物流信息</h3>
          <button
            v-if="nextAction?.key === 'editLogistics'"
            type="button"
            class="btn btn-xs btn-outline"
            :disabled="acting || logisticsBusy"
            data-testid="order-logistics-edit"
            @click="ask('editLogistics')"
          >
            <AppIcon name="edit" class="text-[13px]" />
            修改物流
          </button>
        </div>
        <p v-if="!order.carrier && !order.tracking_no" class="m-0 text-sm text-base-content/60">
          {{ M10.logisticsEmpty }}
        </p>
        <dl v-else class="grid grid-cols-1 gap-x-8 gap-y-2 text-sm sm:grid-cols-3">
          <div class="flex flex-col gap-0.5">
            <dt class="text-xs text-base-content/60">承运商</dt>
            <dd class="m-0">{{ order.carrier ?? '—' }}</dd>
          </div>
          <div class="flex flex-col gap-0.5">
            <dt class="text-xs text-base-content/60">运单号</dt>
            <dd class="m-0 whitespace-pre-line break-all font-mono">
              {{ order.tracking_no ?? '—' }}
            </dd>
          </div>
          <div v-if="order.logistics_remark" class="flex min-w-0 flex-col gap-0.5">
            <dt class="text-xs text-base-content/60">备注</dt>
            <dd class="m-0 whitespace-pre-line break-words">{{ order.logistics_remark }}</dd>
          </div>
        </dl>
      </section>

      <!-- 动作区：能做什么由 orderActions 决定；待支付只解释原因，不给一个按不动的灰按钮。
           改物流的入口在「物流信息」卡片里，这里不再重复放一个主按钮。 -->
      <div
        v-if="cancelable || pendingPayment || (nextAction && nextAction.key !== 'editLogistics')"
        class="flex flex-wrap items-center justify-end gap-2 border-t border-base-300 pt-4"
      >
        <span v-if="pendingPayment" class="mr-auto text-xs text-base-content/60">
          {{ M10.pendingPaymentHint }}
        </span>
        <button
          v-if="cancelable"
          type="button"
          class="btn btn-sm btn-outline btn-error"
          :disabled="acting"
          data-testid="order-cancel"
          @click="ask('cancel')"
        >
          取消订单
        </button>
        <button
          v-if="nextAction && nextAction.key !== 'editLogistics'"
          type="button"
          class="btn btn-sm btn-primary"
          :disabled="acting || logisticsBusy"
          data-testid="order-next"
          @click="ask(nextAction.key)"
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
        <button type="button" class="btn btn-xs btn-outline ml-auto" @click="loadItems">
          重试
        </button>
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
              <tr v-for="item in items" :key="item.order_item_id">
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

    <!-- 该订单的售后：这里只读，处理动作在「售后管理」里做（契约 §4.6） -->
    <section v-if="order" class="panel flex flex-col gap-3">
      <div class="flex flex-wrap items-baseline justify-between gap-2">
        <h2 class="text-sm font-semibold">该订单的售后</h2>
        <RouterLink
          :to="{ name: 'aftersales', query: { order_no: order.order_no } }"
          class="link link-hover text-xs text-base-content/60"
        >
          在售后管理中查看
        </RouterLink>
      </div>

      <div v-if="aftersalesError" role="alert" class="alert alert-error alert-soft text-sm">
        <AppIcon name="forbidden" />
        <span>{{ aftersalesError }}</span>
        <button
          type="button"
          class="btn btn-xs btn-outline ml-auto"
          @click="loadAftersales(order.order_no)"
        >
          重试
        </button>
      </div>

      <p
        v-else-if="aftersalesLoading && aftersales.length === 0"
        class="text-sm text-base-content/60"
      >
        售后记录加载中…
      </p>

      <p v-else-if="aftersales.length === 0" class="m-0 text-sm text-base-content/60">
        {{ M10.emptyOrderAftersales }}
      </p>

      <ul v-else class="flex flex-col divide-y divide-base-300">
        <li
          v-for="item in aftersales"
          :key="item.aftersale_id"
          class="flex flex-wrap items-center gap-3 py-2"
        >
          <RouterLink
            :to="{ name: 'aftersale-detail', params: { id: item.aftersale_id } }"
            class="link link-hover font-mono text-xs"
          >
            {{ item.aftersale_no }}
          </RouterLink>
          <span class="text-sm">{{ item.title }}</span>
          <StatusTag
            :label="AFTERSALE_STATUS_LABELS[item.status]"
            :tone="AFTERSALE_STATUS_TONES[item.status]"
          />
          <StatusTag
            v-if="item.result"
            :label="AFTERSALE_RESULT_LABELS[item.result]"
            :tone="AFTERSALE_RESULT_TONES[item.result]"
          />
          <span v-if="item.refund_amount" class="text-xs text-warning">
            退款 {{ formatPrice(item.refund_amount) }}
          </span>
          <span class="ml-auto text-xs text-base-content/60">
            {{ formatDateTime(item.create_at) }}
          </span>
        </li>
      </ul>
    </section>

    <ConfirmDialog
      :open="confirmOpen"
      :title="pendingKey === 'cancel' ? '取消订单' : '请确认'"
      :message="pendingMessage"
      :confirm-text="pendingKey === 'cancel' ? '取消订单' : '确认'"
      :cancel-text="pendingKey === 'cancel' ? '再想想' : '取消'"
      :danger="pendingKey === 'cancel'"
      :loading="acting"
      @confirm="runAction"
      @cancel="closeConfirm"
    />

    <LogisticsDialog
      :open="logisticsOpen"
      :mode="logisticsMode"
      :carrier="logisticsInitial.carrier"
      :tracking-no="logisticsInitial.trackingNo"
      :remark="logisticsInitial.remark"
      :loading="logisticsBusy"
      @submit="submitLogistics"
      @cancel="logisticsOpen = false"
    />
  </div>
</template>
