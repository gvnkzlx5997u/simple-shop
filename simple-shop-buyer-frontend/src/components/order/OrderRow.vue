<script setup lang="ts">
import type { OrderData } from '../../api/types'
import { formatMoney, formatTime } from '../../utils/format'
import { ORDER_STATUS_TONES, orderStatusText } from '../../utils/orderStatus'
import StatusTag from '../common/StatusTag.vue'

defineProps<{ order: OrderData }>()
</script>

<template>
  <RouterLink
    :to="'/orders/' + order.order_id"
    class="order-row block border-b border-base-300 px-4 py-3 text-base-content last:border-b-0 hover:bg-base-200"
    data-testid="order-row"
  >
    <div class="flex items-center gap-2.5">
      <span class="font-semibold tracking-wide">{{ order.order_no }}</span>
      <StatusTag :label="orderStatusText(order.status)" :tone="ORDER_STATUS_TONES[order.status]" />
    </div>
    <div class="muted mt-0.5 text-xs">
      下单时间 {{ formatTime(order.create_at) }} · 共 {{ order.item_count }} 件
    </div>
    <div class="mt-1.5 flex items-center justify-between">
      <span class="amount text-base font-semibold text-error">¥{{ formatMoney(order.total_amount) }}</span>
      <span class="text-sm text-primary">查看详情 ›</span>
    </div>
  </RouterLink>
</template>