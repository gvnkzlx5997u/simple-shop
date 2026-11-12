<script setup lang="ts">
import { computed } from 'vue'

import type { OrderStatus } from '../../api/types'
import { ORDER_STATUS_LABELS } from '../../constants/labels'
import AppIcon from '../common/AppIcon.vue'

/**
 * 订单状态流程图：横向铺开正向流程，走过的格子换色、当前格子实心、未到的保持灰底。
 *
 * 「已取消」是出边不是阶段，不放进流程里；终态订单由调用方直接不渲染本组件。
 */
const FLOW: OrderStatus[] = ['PENDING_CONFIRM', 'CONFIRMED', 'PREPARED', 'SHIPPED', 'COMPLETED']

const props = defineProps<{ status: OrderStatus }>()

const currentIndex = computed(() => FLOW.indexOf(props.status))

/** 走过的用淡色、当前的用实心，避免「已走完」和「正停在这一步」看起来一样。 */
function stepClass(index: number): string {
  if (index === currentIndex.value) {
    return 'border-primary bg-primary font-semibold text-primary-content'
  }
  if (index < currentIndex.value) {
    return 'border-primary/30 bg-primary/10 text-primary'
  }
  return 'border-base-300 bg-base-200 text-base-content/45'
}

function arrowClass(index: number): string {
  return index < currentIndex.value ? 'text-primary' : 'text-base-300'
}
</script>

<template>
  <div
    class="flex items-center gap-2 overflow-x-auto pb-1"
    role="list"
    aria-label="订单状态流程"
    data-testid="order-progress"
  >
    <template v-for="(step, index) in FLOW" :key="step">
      <div
        role="listitem"
        class="shrink-0 rounded-field border px-3 py-1.5 text-xs whitespace-nowrap transition-colors"
        :class="stepClass(index)"
        :aria-current="index === currentIndex ? 'step' : undefined"
      >
        {{ ORDER_STATUS_LABELS[step] }}
      </div>
      <AppIcon
        v-if="index < FLOW.length - 1"
        name="arrow-right"
        class="shrink-0 text-[12px]"
        :class="arrowClass(index)"
        aria-hidden="true"
      />
    </template>
  </div>
</template>
