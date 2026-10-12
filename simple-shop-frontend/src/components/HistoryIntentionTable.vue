<script setup lang="ts">
import { ref } from 'vue'

import type { FailType, HistoryIntentionData } from '../api/types'
import {
  FAIL_TYPE_LABELS,
  INTENTION_STATUS_LABELS,
  INTENTION_STATUS_TONES,
  TRADE_RESULT_LABELS,
  TRADE_RESULT_TONES,
} from '../constants/labels'
import { M10 } from '../constants/messages'
import { formatDateTime } from '../utils/format'
import StatusTag from './StatusTag.vue'

defineProps<{
  items: HistoryIntentionData[]
}>()

const expanded = ref<string[]>([])

function toggle(id: string): void {
  expanded.value = expanded.value.includes(id)
    ? expanded.value.filter((item) => item !== id)
    : expanded.value.concat(id)
}

function isExpanded(id: string): boolean {
  return expanded.value.includes(id)
}

function failTypeLabel(failType: FailType | null): string {
  return failType ? FAIL_TYPE_LABELS[failType] : '—'
}
</script>

<template>
  <table class="data-table history-intention-table">
    <thead>
      <tr>
        <th>姓名</th>
        <th>联系电话</th>
        <th>意向提交时间</th>
        <th>状态</th>
        <th>交易结果</th>
        <th>交易次数</th>
        <th>操作</th>
      </tr>
    </thead>
    <tbody>
      <tr v-if="items.length === 0">
        <td colspan="7" class="muted">{{ M10.emptyIntentions }}</td>
      </tr>
      <template v-for="item in items" :key="item.id">
        <tr>
          <td>{{ item.name }}</td>
          <td>{{ item.tel }}</td>
          <td>{{ formatDateTime(item.create_at) }}</td>
          <td>
            <StatusTag
              :label="INTENTION_STATUS_LABELS[item.status]"
              :tone="INTENTION_STATUS_TONES[item.status]"
            />
          </td>
          <td>{{ failTypeLabel(item.fail_type) }}</td>
          <td>{{ item.trade_count }}</td>
          <td>
            <button
              type="button"
              class="btn btn--small"
              :disabled="item.trade_count === 0"
              @click="toggle(item.id)"
            >
              {{ isExpanded(item.id) ? '收起流水' : '展开流水' }}
            </button>
          </td>
        </tr>
        <tr v-if="isExpanded(item.id)" class="history-intention-table__detail">
          <td colspan="7">
            <table class="data-table">
              <thead>
                <tr>
                  <th>交易开始时间</th>
                  <th>交易结束时间</th>
                  <th>结果</th>
                  <th>失败类型</th>
                  <th>失败备注</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(trade, index) in item.trades" :key="index">
                  <td>{{ formatDateTime(trade.trade_start) }}</td>
                  <td>{{ formatDateTime(trade.trade_end) }}</td>
                  <td>
                    <StatusTag
                      :label="TRADE_RESULT_LABELS[trade.result]"
                      :tone="TRADE_RESULT_TONES[trade.result]"
                    />
                  </td>
                  <td>{{ failTypeLabel(trade.fail_type) }}</td>
                  <td>{{ trade.fail_reason || '—' }}</td>
                </tr>
              </tbody>
            </table>
          </td>
        </tr>
      </template>
    </tbody>
  </table>
</template>

<style scoped>
.history-intention-table__detail td {
  background: #fafbfc;
}
</style>
