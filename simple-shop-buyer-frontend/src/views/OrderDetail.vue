<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { fetchAftersales } from '../api/aftersale'
import { ApiError } from '../api/http'
import { cancelOrder, confirmReceipt, fetchOrder, fetchOrderItems } from '../api/order'
import { ApiCode } from '../api/types'
import type { AftersaleData, OrderData, OrderItemData } from '../api/types'
import EmptyState from '../components/common/EmptyState.vue'
import StatusTag from '../components/common/StatusTag.vue'
import OrderItemsTable from '../components/order/OrderItemsTable.vue'
import { useCountdown } from '../composables/useCountdown'
import {
  AFTERSALE_STATUS_TONES,
  aftersaleResultText,
  aftersaleStatusText,
} from '../utils/aftersaleStatus'
import { describeError } from '../utils/errorText'
import { formatMoney, formatTime } from '../utils/format'
import {
  ORDER_STATUS_TONES,
  canApplyAftersale,
  canCancelByBuyer,
  canConfirmReceipt,
  canPayByBuyer,
  isTerminal,
  orderStatusText,
  paymentChannelText,
} from '../utils/orderStatus'

const route = useRoute()
const router = useRouter()
const orderId = computed(() => String(route.params.id ?? ''))

/**
 * 订单头与明细**分开加载、分开报错**。
 * 明细接口挂了不该让整页变成错误页——订单号、状态、金额这些照样能读。
 */
const order = ref<OrderData | null>(null)
const orderLoading = ref(false)
const orderError = ref('')
const notFound = ref(false)

const items = ref<OrderItemData[]>([])
const itemsLoading = ref(false)
const itemsError = ref('')

/** 售后列表不内嵌在订单详情里，按 order_id 单独查，也单独报错。 */
const aftersales = ref<AftersaleData[]>([])
const aftersalesLoading = ref(false)
const aftersalesError = ref('')

const cancelling = ref(false)
const confirmingCancel = ref(false)
const receiving = ref(false)
const confirmingReceipt = ref(false)
const notice = ref('')
const actionError = ref('')

const payable = computed(() => !!order.value && canPayByBuyer(order.value.status))
const cancellable = computed(() => !!order.value && canCancelByBuyer(order.value.status))
const receivable = computed(() => !!order.value && canConfirmReceipt(order.value.status))
const finished = computed(() => !!order.value && isTerminal(order.value.status))
/** 已支付的单取消要全额退款，确认文案要说清楚，待支付的没有这回事。 */
const paid = computed(() => !!order.value?.paid_at)

const deadline = computed(() => order.value?.pay_deadline ?? null)
const { text: countdown, expired } = useCountdown(deadline)

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
      err instanceof ApiError && (err.code === ApiCode.ORDER_NOT_FOUND || err.httpStatus === 404)
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
    // 顺序直接沿用后端返回，前端不重排。
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
    void loadAftersales()
  }
}

async function loadAftersales(): Promise<void> {
  aftersalesLoading.value = true
  aftersalesError.value = ''
  try {
    const data = await fetchAftersales({ order_id: orderId.value, page: 1, page_size: 100 })
    aftersales.value = data.items ?? []
  } catch (err) {
    aftersalesError.value = describeError(err)
    aftersales.value = []
  } finally {
    aftersalesLoading.value = false
  }
}

/** 售后按订单项申请，入口带 order_item_id，不用 goods_id。 */
function onApply(orderItemId: string): void {
  void router.push({
    name: 'aftersale-apply',
    query: { order_id: orderId.value, order_item_id: orderItemId },
  })
}

/** 第一次点先问，第二次点才真做——比 window.confirm 可控，也不会被浏览器拦。 */
function askCancel(): void {
  actionError.value = ''
  notice.value = ''
  confirmingCancel.value = true
}

function askReceipt(): void {
  actionError.value = ''
  notice.value = ''
  confirmingReceipt.value = true
}

async function doCancel(): Promise<void> {
  if (!order.value || cancelling.value) return
  cancelling.value = true
  actionError.value = ''
  try {
    const result = await cancelOrder(order.value.order_id)
    // 就地更新状态，不整页重拉：明细没变，重拉只会闪一下。
    order.value = { ...order.value, status: result.status, update_at: result.update_at }
    notice.value = paid.value ? '订单已取消，已支付金额将原路退回' : '订单已取消'
    confirmingCancel.value = false
    // 已支付的单取消会产生退款，而「已退款」是后端查询时算的派生值，只有重拉才拿得到。
    if (paid.value) await loadOrder()
  } catch (err) {
    actionError.value = describeError(err)
  } finally {
    cancelling.value = false
  }
}

async function doConfirmReceipt(): Promise<void> {
  if (!order.value || receiving.value) return
  receiving.value = true
  actionError.value = ''
  try {
    const result = await confirmReceipt(order.value.order_id)
    order.value = { ...order.value, status: result.status, update_at: result.update_at }
    notice.value = '已确认收货，交易完成'
    confirmingReceipt.value = false
  } catch (err) {
    // 缺货（60004）时订单停在已发货，状态不动，卖家补货后可以再来一次。
    actionError.value = describeError(err)
  } finally {
    receiving.value = false
  }
}

onMounted(() => {
  // 支付页付完跳回来时带这个标记，给一句明确的成功反馈。
  if (route.query.paid === '1') notice.value = '支付成功'
  void loadAll()
})
</script>

<template>
  <div class="mx-auto max-w-[960px] space-y-3 p-4">
    <div class="flex items-baseline justify-between">
      <h1 class="text-lg">订单详情</h1>
      <RouterLink to="/orders" class="link text-xs text-base-content/60">返回订单列表</RouterLink>
    </div>

    <p v-if="orderLoading && !order" class="muted">加载中…</p>

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
          <template v-if="order.refunded_amount > 0">
            <span class="muted">已退款</span>
            <span class="font-semibold" data-testid="order-refunded">
              ¥{{ formatMoney(order.refunded_amount) }}
            </span>
          </template>
          <template v-if="order.aftersale_count > 0">
            <span class="muted">售后单</span>
            <span>{{ order.aftersale_count }} 条</span>
          </template>
        </div>
      </div>

      <div v-if="payable" class="alert alert-warning alert-soft flex-wrap">
        <span v-if="expired" class="flex-1">支付时限已过，订单将被系统取消。</span>
        <span v-else class="flex-1">
          待支付，距截止还有
          <span class="font-semibold" data-testid="pay-countdown">{{ countdown }}</span>
        </span>
        <button v-if="expired" class="btn btn-outline btn-sm" @click="loadOrder">刷新</button>
        <RouterLink
          v-else
          class="btn btn-primary btn-sm"
          data-testid="order-pay-entry"
          :to="{ name: 'order-pay', params: { id: order.order_id } }"
        >
          去支付
        </RouterLink>
      </div>

      <div class="panel">
        <h2 class="mb-2.5 text-[15px]">收货信息</h2>
        <div class="grid grid-cols-[88px_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
          <span class="muted">收货人</span>
          <span>{{ order.receiver_name }}</span>
          <span class="muted">手机号</span>
          <span>{{ order.receiver_phone }}</span>
          <span class="muted">详细地址</span>
          <span>{{ order.receiver_address }}</span>
        </div>
        <p class="muted mt-2 text-xs">这是下单时填写的地址，之后改地址簿不会影响这笔订单。</p>
      </div>

      <div v-if="order.paid_at" class="panel">
        <h2 class="mb-2.5 text-[15px]">支付信息</h2>
        <div class="grid grid-cols-[88px_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
          <span class="muted">支付渠道</span>
          <span>{{ paymentChannelText(order.payment_channel) }}</span>
          <span class="muted">支付时间</span>
          <span>{{ formatTime(order.paid_at) }}</span>
        </div>
      </div>

      <div v-if="order.carrier || order.tracking_no" class="panel" data-testid="order-logistics">
        <h2 class="mb-2.5 text-[15px]">物流信息</h2>
        <div class="grid grid-cols-[88px_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
          <span class="muted">承运商</span>
          <span>{{ order.carrier }}</span>
          <span class="muted">物流单号</span>
          <!-- 一个订单可能拆成多个包裹，单号是多行文本，换行要保留 -->
          <span class="whitespace-pre-line break-all">{{ order.tracking_no }}</span>
          <template v-if="order.logistics_remark">
            <span class="muted">物流备注</span>
            <span class="whitespace-pre-line">{{ order.logistics_remark }}</span>
          </template>
        </div>
      </div>

      <div class="panel">
        <h2 class="mb-2.5 text-[15px]">商品明细</h2>

        <p v-if="itemsLoading" class="muted">明细加载中…</p>

        <div v-else-if="itemsError" class="flex items-center gap-3">
          <p class="flex-1 text-error">{{ itemsError }}</p>
          <button class="btn btn-outline btn-sm" @click="loadItems">重试</button>
        </div>

        <OrderItemsTable
          v-else-if="items.length"
          :items="items"
          :apply-enabled="canApplyAftersale(order.status)"
          @apply="onApply"
        />

        <p v-else class="muted">这笔订单没有明细</p>
      </div>

      <div
        v-if="aftersalesLoading || aftersalesError || aftersales.length"
        class="panel"
        data-testid="order-aftersales"
      >
        <h2 class="mb-2.5 text-[15px]">售后记录</h2>

        <p v-if="aftersalesLoading" class="muted">售后记录加载中…</p>

        <div v-else-if="aftersalesError" class="flex items-center gap-3">
          <p class="flex-1 text-error">{{ aftersalesError }}</p>
          <button class="btn btn-outline btn-sm" @click="loadAftersales">重试</button>
        </div>

        <div v-else class="space-y-2">
          <RouterLink
            v-for="entry in aftersales"
            :key="entry.aftersale_id"
            :to="{ name: 'aftersale-detail', params: { id: entry.aftersale_id } }"
            class="flex flex-wrap items-center gap-2.5 rounded-field border border-base-300 px-3 py-2"
          >
            <span class="font-semibold">{{ entry.aftersale_no }}</span>
            <StatusTag
              :label="aftersaleStatusText(entry.status)"
              :tone="AFTERSALE_STATUS_TONES[entry.status]"
            />
            <span class="muted text-sm">{{ entry.goods_name }}</span>
            <span v-if="entry.result" class="muted text-sm">
              {{ aftersaleResultText(entry.result) }}
            </span>
            <span v-if="entry.refund_amount != null" class="ml-auto font-semibold text-error">
              退款 ¥{{ formatMoney(entry.refund_amount) }}
            </span>
          </RouterLink>
        </div>
      </div>

      <div v-if="cancellable || receivable || finished" class="panel flex flex-wrap items-center gap-3">
        <template v-if="receivable">
          <template v-if="confirmingReceipt">
            <span class="muted min-w-[160px] flex-1">
              确认已收到货？确认后交易完成，在售商品会按明细扣减库存。
            </span>
            <button
              class="btn btn-primary btn-sm"
              data-testid="order-confirm-receipt"
              :disabled="receiving"
              @click="doConfirmReceipt"
            >
              {{ receiving ? '提交中…' : '确认收货' }}
            </button>
            <button class="btn btn-ghost btn-sm" :disabled="receiving" @click="confirmingReceipt = false">
              再想想
            </button>
          </template>
          <template v-else>
            <span class="muted min-w-[160px] flex-1">收到货后请确认，交易完成由你确认收货推进。</span>
            <button class="btn btn-primary btn-sm" @click="askReceipt">确认收货</button>
          </template>
        </template>

        <template v-else-if="cancellable">
          <template v-if="confirmingCancel">
            <span class="muted min-w-[160px] flex-1">
              {{ paid ? '确认取消这笔订单？已支付金额将全额原路退回。' : '确认取消这笔订单吗？取消后不可恢复。' }}
            </span>
            <button
              class="btn btn-error btn-sm"
              data-testid="order-cancel"
              :disabled="cancelling"
              @click="doCancel"
            >
              {{ cancelling ? '取消中…' : '确认取消' }}
            </button>
            <button class="btn btn-ghost btn-sm" :disabled="cancelling" @click="confirmingCancel = false">
              再想想
            </button>
          </template>
          <template v-else>
            <span class="muted min-w-[160px] flex-1">发货前可以取消，已发货后请联系卖家。</span>
            <button class="btn btn-outline btn-sm" @click="askCancel">取消订单</button>
          </template>
        </template>

        <span v-else-if="finished" class="muted">
          {{ order.status === 'COMPLETED' ? '这笔订单已交易完成。' : '这笔订单已取消，不能再操作。' }}
        </span>
      </div>
    </template>
  </div>
</template>