<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { listCustomers } from '../api/customer'
import type { CustomerSummaryData } from '../api/types'
import Pagination from '../components/common/Pagination.vue'
import { M10 } from '../constants/messages'
import { errorMessage } from '../utils/error'
import { formatDateTime } from '../utils/format'

const PAGE_SIZE = 10

const router = useRouter()

const customers = ref<CustomerSummaryData[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const error = ref<string | null>(null)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const data = await listCustomers(page.value, PAGE_SIZE)
    customers.value = data.items
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
  void router.push({ name: 'customer-detail', params: { id } })
}
</script>

<template>
  <div class="page">
    <h1 class="page__title">客户管理</h1>
    <p class="muted page__hint">
      买家注册即成为客户;详情页可查看其全部购买历史（含排队失败记录）。
    </p>

    <p v-if="error" class="page__error">{{ error }}</p>

    <table class="data-table">
      <thead>
        <tr>
          <th>用户名</th>
          <th>联系电话</th>
          <th>默认交易地点</th>
          <th>注册时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="loading && customers.length === 0">
          <td colspan="5" class="muted">加载中…</td>
        </tr>
        <tr v-else-if="customers.length === 0 && !error">
          <td colspan="5" class="muted">{{ M10.emptyCustomers }}</td>
        </tr>
        <tr v-for="customer in customers" v-else :key="customer.id">
          <td>{{ customer.username }}</td>
          <td>{{ customer.phone }}</td>
          <td>{{ customer.default_location ?? '—' }}</td>
          <td>{{ formatDateTime(customer.create_at) }}</td>
          <td>
            <button type="button" class="btn btn--small" @click="viewDetail(customer.id)">
              详情
            </button>
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
