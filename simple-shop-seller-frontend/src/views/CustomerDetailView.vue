<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { getCustomerDetail } from '../api/customer'
import type { CustomerDetailData } from '../api/types'
import Pagination from '../components/common/Pagination.vue'
import StatusTag from '../components/common/StatusTag.vue'
import { FAIL_TYPE_LABELS, HISTORY_INTENTION_RESULT_LABELS } from '../constants/labels'
import type { TagTone } from '../constants/labels'
import { M10 } from '../constants/messages'
import { errorMessage } from '../utils/error'
import { formatDateTime } from '../utils/format'

const PAGE_SIZE = 10

const route = useRoute()

const customerId = computed(() => (typeof route.params.id === 'string' ? route.params.id : ''))

const data = ref<CustomerDetailData | null>(null)
const page = ref(1)
const loading = ref(false)
const error = ref<string | null>(null)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    data.value = await getCustomerDetail(customerId.value, page.value, PAGE_SIZE)
  } catch (caught) {
    error.value = errorMessage(caught)
    data.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(customerId, () => {
  page.value = 1
  void load()
})

const purchases = computed(() => data.value?.purchase_history.items ?? [])
const purchaseTotal = computed(() => data.value?.purchase_history.total ?? 0)

function resultTone(result: 'SUCCEEDED' | 'FAILED'): TagTone {
  return result === 'SUCCEEDED' ? 'success' : 'danger'
}

function onPageChange(next: number): void {
  page.value = next
  void load()
}
</script>

<template>
  <div class="page">
    <div class="page__header">
      <h1 class="page__title">客户详情</h1>
      <RouterLink to="/customers" class="btn">返回客户列表</RouterLink>
    </div>

    <p v-if="error" class="page__error">
      {{ error }}
      <RouterLink to="/customers">返回客户列表</RouterLink>
    </p>
    <div v-else-if="loading && !data" class="card muted">加载中…</div>

    <template v-else-if="data">
      <div class="card info">
        <dl class="info__meta">
          <div class="info__row">
            <dt>用户名</dt>
            <dd>{{ data.username }}</dd>
          </div>
          <div class="info__row">
            <dt>联系电话</dt>
            <dd>{{ data.phone }}</dd>
          </div>
          <div class="info__row">
            <dt>默认交易地点</dt>
            <dd>{{ data.default_location ?? '—' }}</dd>
          </div>
          <div class="info__row">
            <dt>注册时间</dt>
            <dd>{{ formatDateTime(data.create_at) }}</dd>
          </div>
        </dl>
      </div>

      <section class="card section">
        <h2 class="section__title">购买历史</h2>
        <p v-if="loading" class="muted">加载中…</p>
        <table v-else class="data-table">
          <thead>
            <tr>
              <th>商品名</th>
              <th>下单时间</th>
              <th>结果</th>
              <th>失败类型</th>
              <th>交易时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="purchases.length === 0">
              <td colspan="5" class="muted">{{ M10.emptyPurchases }}</td>
            </tr>
            <tr v-for="(entry, index) in purchases" v-else :key="index">
              <td>{{ entry.goods_name }}</td>
              <td>{{ formatDateTime(entry.create_at) }}</td>
              <td>
                <StatusTag
                  :label="HISTORY_INTENTION_RESULT_LABELS[entry.result]"
                  :tone="resultTone(entry.result)"
                />
              </td>
              <td>{{ entry.fail_type ? FAIL_TYPE_LABELS[entry.fail_type] : '—' }}</td>
              <td>{{ formatDateTime(entry.finish_at) }}</td>
            </tr>
          </tbody>
        </table>

        <Pagination
          v-if="purchaseTotal > PAGE_SIZE"
          class="section__pagination"
          :page="page"
          :page-size="PAGE_SIZE"
          :total="purchaseTotal"
          :disabled="loading"
          @update:page="onPageChange"
        />
      </section>
    </template>
  </div>
</template>

<style scoped>
.page__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page__title {
  margin: 0;
}

.page__error {
  margin: 0 0 16px;
  padding: 10px 12px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  background: #fef2f2;
  color: var(--danger);
}

.info__meta {
  margin: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
}

.info__row {
  display: flex;
  gap: 12px;
  align-items: baseline;
}

.info__row dt {
  flex: 0 0 96px;
  color: var(--muted);
  font-size: 13px;
}

.info__row dd {
  margin: 0;
}

.section {
  margin-top: 16px;
}

.section__title {
  margin: 0 0 12px;
  font-size: 16px;
}

.section__pagination {
  margin-top: 12px;
}
</style>
