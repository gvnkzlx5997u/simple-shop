<script setup lang="ts">
import type { HistoryGoodsItemData } from '../api/types'
import { GOODS_RESULT_LABELS } from '../constants/labels'
import { M10 } from '../constants/messages'
import { formatDateTime } from '../utils/format'

withDefaults(
  defineProps<{
    items: HistoryGoodsItemData[]
    loading?: boolean
  }>(),
  { loading: false },
)

const emit = defineEmits<{
  'view-detail': [id: string]
}>()
</script>

<template>
  <table class="data-table">
    <thead>
      <tr>
        <th>商品名称</th>
        <th>发布时间</th>
        <th>交易结束时间</th>
        <th>交易结果</th>
        <th>操作</th>
      </tr>
    </thead>
    <tbody>
      <tr v-if="loading">
        <td colspan="5" class="muted">加载中…</td>
      </tr>
      <tr v-else-if="items.length === 0">
        <td colspan="5" class="muted">{{ M10.emptyHistory }}</td>
      </tr>
      <template v-else>
        <tr v-for="item in items" :key="item.id">
          <td>{{ item.name }}</td>
          <td>{{ formatDateTime(item.create_at) }}</td>
          <td>{{ formatDateTime(item.trade_end) }}</td>
          <td>{{ GOODS_RESULT_LABELS[item.result] }}</td>
          <td>
            <button type="button" class="btn btn--small" @click="emit('view-detail', item.id)">
              查看详情
            </button>
          </td>
        </tr>
      </template>
    </tbody>
  </table>
</template>
