<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { listHistory } from '../api/history'
import type { GoodsHistorySummaryData } from '../api/types'
import Pagination from '../components/common/Pagination.vue'
import StatusTag from '../components/common/StatusTag.vue'
import { HISTORY_RESULT_LABELS } from '../constants/labels'
import { M10 } from '../constants/messages'
import { errorMessage } from '../utils/error'
import { formatDateTime, formatPrice } from '../utils/format'

const PAGE_SIZE = 10

const router = useRouter()

const items = ref<GoodsHistorySummaryData[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const error = ref<string | null>(null)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const data = await listHistory(page.value, PAGE_SIZE)
    items.value = data.items
    total.value = data.total
  } catch (caught) {
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

onMounted(load)

function onPageChange(next: number): void {
  page.value = next
  void load()
}

function viewDetail(id: string): void {
  void router.push({ name: 'history-detail', params: { id } })
}
</script>

<template>
  <div class="page">
    <h1 class="page__title">历史记录</h1>
    <p class="muted page__hint">售罄归档的商品进入历史;按归档时间倒序,内容为归档时的快照。</p>

    <p v-if="error" class="page__error">{{ error }}</p>

    <table class="data-table">
      <thead>
        <tr>
          <th>商品名</th>
          <th>价格</th>
          <th>一级类别</th>
          <th>二级类别</th>
          <th>结果</th>
          <th>发布时间</th>
          <th>归档时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="loading && items.length === 0">
          <td colspan="8" class="muted">加载中…</td>
        </tr>
        <tr v-else-if="items.length === 0 && !error">
          <td colspan="8" class="muted">{{ M10.emptyHistory }}</td>
        </tr>
        <tr v-for="item in items" v-else :key="item.id">
          <td>{{ item.name }}</td>
          <td>{{ formatPrice(item.price) }}</td>
          <td>{{ item.category_l1_name }}</td>
          <td>{{ item.category_l2_name }}</td>
          <td>
            <StatusTag :label="HISTORY_RESULT_LABELS[item.result]" tone="default" />
          </td>
          <td>{{ formatDateTime(item.create_at) }}</td>
          <td>{{ formatDateTime(item.archived_at) }}</td>
          <td>
            <button type="button" class="btn btn--small" @click="viewDetail(item.id)">详情</button>
          </td>
        </tr>
      </tbody>
    </table>

    <Pagination
      v-if="total > PAGE_SIZE"
      class="page__pagination"
      :page="page"
      :page-size="PAGE_SIZE"
      :total="total"
      :disabled="loading"
      @update:page="onPageChange"
    />
  </div>
</template>

<style scoped>
.page__title {
  margin-bottom: 8px;
}

.page__hint {
  margin: 0 0 16px;
  font-size: 13px;
}

.page__error {
  margin: 0 0 16px;
  padding: 10px 12px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  background: #fef2f2;
  color: var(--danger);
}

.page__pagination {
  margin-top: 12px;
}
</style>
