<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { ApiError } from '../api/http'
import { fetchOrder, payOrder } from '../api/order'
import { ApiCode } from '../api/types'
import type { OrderData, PaymentChannel } from '../api/types'
import EmptyState from '../components/common/EmptyState.vue'
import StatusTag from '../components/common/StatusTag.vue'
import { useCountdown } from '../composables/useCountdown'
import { describeError } from '../utils/errorText'
import { formatMoney } from '../utils/format'
import {
  ORDER_STATUS_TONES,
  PAYMENT_CHANNELS,
  canPayByBuyer,
  orderStatusText,
} from '../utils/orderStatus'

const route = useRoute()
const router = useRouter()
const orderId = computed(() => String(route.params.id ?? ''))

const order = ref<OrderData | null>(null)
const loading = ref(false)
const errorText = ref('')
const notFound = ref(false)

/** 每次进页面都从空开始：支付失败时后端不记渠道，前端也不该替它记住上次选的。 */
const channel = ref<PaymentChannel | ''>('')
const paying = ref(false)
const actionError = ref('')

const deadline = computed(() => order.value?.pay_deadline ?? null)
const { text: countdown, expired } = useCountdown(deadline)
const payable = computed(() => !!order.value && canPayByBuyer(order.value.status))

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  notFound.value = false
  try {
    order.value = await fetchOrder(orderId.value)
  } catch (err) {
    order.value = null
    notFound.value = err instanceof ApiError && (err.code === ApiCode.ORDER_NOT_FOUND || err.httpStatus === 404)
    if (!notFound.value) errorText.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function pay(): Promise<void> {
  if (!order.value || !channel.value || paying.value) return
  paying.value = true
  actionError.value = ''
  try {
    const result = await payOrder(order.value.order_id, channel.value)
    // 真实渠道要跳转时才有值；Mock 恒为空，直接回详情看已支付。
    if (result.redirect_url) {
      window.location.assign(result.redirect_url)
      return
    }
    void router.replace({
      name: 'order-detail',
      params: { id: result.order_id },
      query: { paid: '1' },
    })
  } catch (err) {
    actionError.value = describeError(err)
    // 不可支付多半是超时任务已把订单取消，重拉一次让页面说实话。
    if (err instanceof ApiError && err.code === ApiCode.PAYMENT_NOT_ALLOWED) {
      await load()
    }
  } finally {
    paying.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="mx-auto max-w-[960px] space-y-3 p-4">
    <div class="flex items-baseline justify-between">
      <h1 class="text-lg">支付订单</h1>
      <RouterLink
        :to="{ name: 'order-detail', params: { id: orderId } }"
        class="link text-xs text-base-content/60"
      >
        返回订单详情
      </RouterLink>
    </div>

    <p v-if="loading && !order" class="muted">加载中…</p>

    <EmptyState
      v-else-if="notFound"
      title="订单不存在"
      description="这笔订单可能已被删除，或者不属于当前账号。"
    >
      <RouterLink class="btn btn-primary btn-sm" to="/orders">返回我的订单</RouterLink>
    </EmptyState>

    <div v-else-if="errorText" class="panel flex items-center gap-3">
      <p class="flex-1 text-error">{{ errorText }}</p>
      <button class="btn btn-outline btn-sm" @click="load">重试</button>
    </div>

    <template v-else-if="order">
      <div class="panel">
        <div class="mb-3 flex items-center gap-2.5">
          <span class="text-[15px] font-semibold tracking-wide">{{ order.order_no }}</span>
          <StatusTag :label="orderStatusText(order.status)" :tone="ORDER_STATUS_TONES[order.status]" />
        </div>
        <div class="flex items-baseline gap-2">
          <span class="muted">应付金额</span>
          <span class="text-xl font-semibold text-error" data-testid="pay-amount">
            ¥{{ formatMoney(order.total_amount) }}
          </span>
        </div>
      </div>

      <div v-if="!payable" class="panel space-y-3">
        <p class="alert alert-warning alert-soft">
          这笔订单当前不可支付，可能已经支付或已被取消。
        </p>
        <RouterLink
          class="btn btn-primary btn-sm"
          :to="{ name: 'order-detail', params: { id: order.order_id } }"
        >
          回订单详情查看
        </RouterLink>
      </div>

      <template v-else>
        <p v-if="expired" class="alert alert-warning alert-soft">
          <span class="flex-1">支付时限已过，订单将被系统取消。</span>
          <button class="btn btn-outline btn-sm" @click="load">刷新</button>
        </p>
        <p v-else class="muted">
          距支付截止还有
          <span class="font-semibold text-base-content" data-testid="pay-countdown">{{ countdown }}</span>
        </p>

        <div class="panel space-y-2.5">
          <h2 class="text-[15px]">选择支付渠道</h2>
          <label
            v-for="item in PAYMENT_CHANNELS"
            :key="item.value"
            class="flex cursor-pointer items-center gap-2.5 rounded-field border border-base-300 px-3 py-2"
          >
            <input
              v-model="channel"
              type="radio"
              name="payment-channel"
              class="radio radio-sm"
              :value="item.value"
              data-testid="pay-channel"
            />
            <span>{{ item.label }}</span>
          </label>

          <p v-if="actionError" class="text-error">{{ actionError }}</p>

          <button
            class="btn btn-primary w-full"
            data-testid="pay-submit"
            :disabled="!channel || paying || expired"
            @click="pay"
          >
            {{ paying ? '支付中…' : '确认支付' }}
          </button>
          <p class="muted text-xs">支付失败不会改变订单，可以换个渠道重试。</p>
        </div>
      </template>
    </template>
  </div>
</template>