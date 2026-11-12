<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { getCustomerDetail } from '../api/customer'
import type { CustomerDetailData } from '../api/types'
import AppIcon from '../components/common/AppIcon.vue'
import OrderList from '../components/order/OrderList.vue'
import { errorMessage } from '../utils/error'
import { formatDateTime } from '../utils/format'

const route = useRoute()

const customerId = computed(() => (typeof route.params.id === 'string' ? route.params.id : ''))

const data = ref<CustomerDetailData | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    data.value = await getCustomerDetail(customerId.value)
  } catch (caught) {
    error.value = errorMessage(caught)
    data.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(customerId, load)
</script>

<template>
  <div class="flex flex-col gap-4">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <h1 class="text-lg font-semibold">客户详情</h1>
      <RouterLink to="/customers" class="btn btn-sm btn-outline">
        <AppIcon name="arrow-left" />
        返回客户列表
      </RouterLink>
    </header>

    <div v-if="error" role="alert" class="alert alert-error alert-soft text-sm">
      <AppIcon name="forbidden" />
      <span>{{ error }}</span>
    </div>
    <div v-else-if="loading && !data" class="panel text-sm text-base-content/60">加载中…</div>

    <div v-else-if="data" class="panel">
      <div class="flex items-center gap-3 border-b border-base-300 pb-3">
        <span
          class="flex size-9 items-center justify-center rounded-full bg-base-200 text-base-content/60"
        >
          <AppIcon name="user" class="text-[18px]" />
        </span>
        <div class="min-w-0">
          <p class="truncate text-base font-semibold">{{ data.username }}</p>
          <p class="text-xs text-base-content/60">注册于 {{ formatDateTime(data.create_at) }}</p>
        </div>
      </div>

      <dl class="mt-4 grid grid-cols-1 gap-x-10 gap-y-3 text-sm sm:grid-cols-2">
        <div class="flex flex-col gap-0.5">
          <dt class="text-xs text-base-content/60">联系电话</dt>
          <dd class="m-0">{{ data.phone }}</dd>
        </div>
        <div class="flex flex-col gap-0.5">
          <dt class="text-xs text-base-content/60">默认交易地点</dt>
          <dd class="m-0">{{ data.default_location ?? '—' }}</dd>
        </div>
      </dl>
    </div>

    <!-- 该客户的订单：内嵌同一份订单列表，按 buyer_id 固定筛选，不跳转到订单管理页 -->
    <section v-if="data" class="flex flex-col gap-3">
      <div class="flex flex-wrap items-baseline gap-2">
        <h2 class="text-base font-semibold">该客户的订单</h2>
        <span class="text-xs text-base-content/60">点任意一行进入订单详情</span>
      </div>
      <OrderList
        :buyer-id="customerId"
        :page-size="5"
        :sync-query="false"
        :show-buyer="false"
        empty-text="该客户还没有下过单"
      />
    </section>
  </div>
</template>
