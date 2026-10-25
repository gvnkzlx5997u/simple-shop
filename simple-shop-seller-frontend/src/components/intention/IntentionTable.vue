<script setup lang="ts">
import type { IntentionEntryData } from '../../api/types'
import {
  FAIL_TYPE_LABELS,
  INTENTION_STATUS_LABELS,
  INTENTION_STATUS_TONES,
} from '../../constants/labels'
import { M10 } from '../../constants/messages'
import { formatDateTime } from '../../utils/format'
import StatusTag from '../common/StatusTag.vue'

/**
 * 意向名单表。行序即 queue_order 升序;队首 = 第一条 QUEUED,
 * 由父级在第一页数据上算出 queueHeadId 传入(翻页后无法从当前页判断)。
 */
const props = withDefaults(
  defineProps<{
    items: IntentionEntryData[]
    /** 商品在售时才允许对队首「进入交易」。 */
    canEnterTrade?: boolean
    /** 商品冻结(交易中)时才允许对 TRADING 行裁决结果。 */
    canDecideTrade?: boolean
    /** 队首意向 id;非第一页时传 null。 */
    queueHeadId?: string | null
    loading?: boolean
  }>(),
  { canEnterTrade: false, canDecideTrade: false, queueHeadId: null, loading: false },
)

const emit = defineEmits<{
  'enter-trade': [item: IntentionEntryData]
  'trade-success': [item: IntentionEntryData]
  'trade-failure': [item: IntentionEntryData]
}>()

function canEnter(item: IntentionEntryData): boolean {
  return props.canEnterTrade && item.status === 'QUEUED' && item.id === props.queueHeadId
}
</script>

<template>
  <table class="data-table intention-table">
    <thead>
      <tr>
        <th>序号</th>
        <th>买家</th>
        <th>联系电话</th>
        <th>默认交易地点</th>
        <th>提交时间</th>
        <th>状态</th>
        <th>失败类型</th>
        <th>完成时间</th>
        <th>操作</th>
      </tr>
    </thead>
    <tbody>
      <tr v-if="loading">
        <td colspan="9" class="muted">加载中…</td>
      </tr>
      <tr v-else-if="items.length === 0">
        <td colspan="9" class="muted">{{ M10.emptyIntentions }}</td>
      </tr>
      <template v-else>
        <tr v-for="item in items" :key="item.id">
          <td>
            {{ item.queue_order }}
            <span v-if="item.id === queueHeadId" class="intention-table__head">队首</span>
          </td>
          <td>{{ item.buyer_username }}</td>
          <td>{{ item.buyer_phone }}</td>
          <td>{{ item.buyer_default_location ?? '—' }}</td>
          <td>{{ formatDateTime(item.create_at) }}</td>
          <td>
            <StatusTag
              :label="INTENTION_STATUS_LABELS[item.status]"
              :tone="INTENTION_STATUS_TONES[item.status]"
            />
          </td>
          <td>{{ item.fail_type ? FAIL_TYPE_LABELS[item.fail_type] : '—' }}</td>
          <td>{{ formatDateTime(item.finish_at) }}</td>
          <td>
            <div v-if="item.status === 'TRADING'" class="intention-table__actions">
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
.intention-table__head {
  margin-left: 4px;
  padding: 1px 6px;
  border-radius: 999px;
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: var(--primary);
  font-size: 12px;
}

.intention-table__actions {
  display: flex;
  gap: 6px;
}
</style>
