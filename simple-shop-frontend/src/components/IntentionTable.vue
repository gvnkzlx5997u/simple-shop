<script setup lang="ts">
import type { IntentionItemData } from '../api/types'
import {
  FAIL_TYPE_LABELS,
  INTENTION_STATUS_LABELS,
  INTENTION_STATUS_TONES,
} from '../constants/labels'
import { M10 } from '../constants/messages'
import { formatDateTime } from '../utils/format'
import StatusTag from './StatusTag.vue'

const props = withDefaults(
  defineProps<{
    items: IntentionItemData[]
    /** 商品在售时才可能「进入交易」。 */
    canEnterTrade?: boolean
    /** 交易冻结时才允许标记交易结果。 */
    canDecideTrade?: boolean
    loading?: boolean
  }>(),
  { canEnterTrade: false, canDecideTrade: false, loading: false },
)

const emit = defineEmits<{
  'enter-trade': [item: IntentionItemData]
  'trade-success': [item: IntentionItemData]
  'trade-failure': [item: IntentionItemData]
}>()

function failTypeLabel(item: IntentionItemData): string {
  return item.fail_type ? FAIL_TYPE_LABELS[item.fail_type] : '—'
}

/** 队首 = 位次 1 且排队中（列表含终态行，不能用字面第 1 行）。 */
function canEnter(item: IntentionItemData): boolean {
  return props.canEnterTrade && item.status === 'queued' && item.rank === 1
}
</script>

<template>
  <table class="data-table intention-table">
    <thead>
      <tr>
        <th>位次</th>
        <th>姓名</th>
        <th>联系电话</th>
        <th>意向提交时间</th>
        <th>状态</th>
        <th>交易结果／失败类型</th>
        <th>操作</th>
      </tr>
    </thead>
    <tbody>
      <tr v-if="loading">
        <td colspan="7" class="muted">加载中…</td>
      </tr>
      <tr v-else-if="items.length === 0">
        <td colspan="7" class="muted">{{ M10.emptyIntentions }}</td>
      </tr>
      <template v-else>
        <tr v-for="item in items" :key="item.id">
          <td>{{ item.rank === null ? '—' : item.rank }}</td>
          <td>{{ item.name }}</td>
          <td>{{ item.tel }}</td>
          <td>{{ formatDateTime(item.create_at) }}</td>
          <td>
            <StatusTag
              :label="INTENTION_STATUS_LABELS[item.status]"
              :tone="INTENTION_STATUS_TONES[item.status]"
            />
          </td>
          <td>
            <span>{{ failTypeLabel(item) }}</span>
            <span v-if="item.fail_reason" class="muted">（{{ item.fail_reason }}）</span>
          </td>
          <td>
            <div v-if="item.status === 'trading'" class="intention-table__actions">
              <button
                type="button"
                class="btn btn--small btn--primary"
                :disabled="!canDecideTrade"
                @click="emit('trade-success', item)"
              >
                标记成功
              </button>
              <button
                type="button"
                class="btn btn--small"
                :disabled="!canDecideTrade"
                @click="emit('trade-failure', item)"
              >
                标记失败
              </button>
            </div>
            <button
              v-else-if="canEnter(item)"
              type="button"
              class="btn btn--small btn--primary"
              @click="emit('enter-trade', item)"
            >
              进入交易
            </button>
            <span v-else class="muted">—</span>
          </td>
        </tr>
      </template>
    </tbody>
  </table>
</template>

<style scoped>
.intention-table__actions {
  display: flex;
  gap: 6px;
}
</style>
