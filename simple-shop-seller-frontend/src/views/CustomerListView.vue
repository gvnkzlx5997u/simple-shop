<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { listCustomers } from '../api/customer'
import type { CustomerSummaryData } from '../api/types'
import AppIcon from '../components/common/AppIcon.vue'
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
  <div class="flex flex-col gap-4">
    <header>
      <h1 class="text-lg font-semibold">客户管理</h1>
      <p class="text-xs text-base-content/60">买家注册即成为客户；详情页展示其基本信息。</p>
    </header>

    <div v-if="error" role="alert" class="alert alert-error alert-soft text-sm">
      <AppIcon name="forbidden" />
      <span>{{ error }}</span>
    </div>

    <div class="panel p-0">
      <div class="overflow-x-auto">
        <table class="table table-sm">
          <thead>
            <tr>
              <th>用户名</th>
              <th>联系电话</th>
              <th>默认交易地点</th>
              <th>注册时间</th>
              <th class="w-16 text-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading && customers.length === 0">
              <td colspan="5" class="text-sm text-base-content/60">加载中…</td>
            </tr>
            <tr v-else-if="customers.length === 0 && !error">
              <td colspan="5" class="text-sm text-base-content/60">{{ M10.emptyCustomers }}</td>
            </tr>
            <tr
              v-for="customer in customers"
              v-else
              :key="customer.id"
              class="cursor-pointer hover:bg-base-200"
              tabindex="0"
              :aria-label="`查看 ${customer.username} 的详情`"
              @click="viewDetail(customer.id)"
              @keydown.enter.prevent="viewDetail(customer.id)"
            >
              <td class="font-medium">{{ customer.username }}</td>
              <td>{{ customer.phone }}</td>
              <td>{{ customer.default_location ?? '—' }}</td>
              <td class="text-base-content/70">{{ formatDateTime(customer.create_at) }}</td>
              <td class="text-right">
                <button
                  type="button"
                  class="btn btn-square btn-xs btn-ghost text-base-content/50 hover:text-primary"
                  title="查看详情"
                  aria-hidden="true"
                  tabindex="-1"
                  @click.stop="viewDetail(customer.id)"
                >
                  <AppIcon name="arrow-right" class="text-[13px]" />
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <Pagination
      v-if="total > PAGE_SIZE"
      :page="page"
      :page-size="PAGE_SIZE"
      :total="total"
      :disabled="loading"
      @update:page="onPageChange"
    />
  </div>
</template>
