<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { fetchAftersales } from '../api/aftersale'
import type { AftersaleData, AftersaleStatus } from '../api/types'
import EmptyState from '../components/common/EmptyState.vue'
import Pagination from '../components/common/Pagination.vue'
import StatusTag from '../components/common/StatusTag.vue'
import { usePagedList } from '../composables/usePagedList'
import {
  AFTERSALE_STATUS_FILTERS,
  AFTERSALE_STATUS_TONES,
  aftersaleResultText,
  aftersaleStatusText,
} from '../utils/aftersaleStatus'
import { formatMoney, formatTime } from '../utils/format'

const PAGE_SIZE = 10
const status = ref<AftersaleStatus | ''>('')

const { items, total, page, loading, errorText, load, reloadFromFirstPage, goPage } =
  usePagedList<AftersaleData>(
    (pageNo) =>
      fetchAftersales({
        status: status.value || undefined,
        page: pageNo,
        page_size: PAGE_SIZE,
      }),
    { pageSize: PAGE_SIZE, emptyPageFallback: true },
  )

function onFilter(): void {
  void reloadFromFirstPage()
}

function go(nextPage: number): void {
  goPage(nextPage)
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="mx-auto max-w-[960px] space-y-3 p-4">
    <div class="flex flex-wrap items-baseline gap-3">
      <h1 class="text-lg">我的售后</h1>
      <span v-if="total" class="muted text-xs">共 {{ total }} 条</span>
      <label class="ml-auto flex items-center gap-2 text-sm">
        <span class="muted">状态</span>
        <select
          v-model="status"
          class="select select-sm"
          aria-label="按状态筛选"
          :disabled="loading"
          @change="onFilter"
        >
          <option v-for="opt in AFTERSALE_STATUS_FILTERS" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
      </label>
    </div>

    <p v-if="loading && !items.length" class="muted">加载中…</p>
    <div v-else-if="errorText" class="panel flex items-center gap-3">
      <p class="flex-1 text-error">{{ errorText }}</p>
      <button class="btn btn-outline btn-sm" @click="load">重试</button>
    </div>

    <EmptyState
      v-else-if="!items.length"
      :title="status ? '没有该状态的售后单' : '还没有申请过售后'"
      :description="
        status ? '换个状态看看，或查看全部。' : '交易完成的订单可以在订单详情里按商品申请售后。'
      "
    >
      <button v-if="status" class="btn btn-outline btn-sm" @click="status = ''; onFilter()">
        查看全部
      </button>
      <RouterLink v-else class="btn btn-primary btn-sm" to="/orders">去看我的订单</RouterLink>
    </EmptyState>

    <template v-else>
      <p v-if="loading" class="muted text-xs">更新中…</p>
      <div class="space-y-3">
        <RouterLink
          v-for="item in items"
          :key="item.aftersale_id"
          :to="{ name: 'aftersale-detail', params: { id: item.aftersale_id } }"
          class="panel block transition-shadow hover:shadow-md"
          data-testid="aftersale-row"
        >
          <div class="mb-1.5 flex flex-wrap items-center gap-2.5">
            <span class="font-semibold tracking-wide">{{ item.aftersale_no }}</span>
            <StatusTag
              :label="aftersaleStatusText(item.status)"
              :tone="AFTERSALE_STATUS_TONES[item.status]"
            />
            <span v-if="item.result" class="muted text-sm">
              {{ aftersaleResultText(item.result) }}
            </span>
            <span v-if="item.refund_amount != null" class="ml-auto font-semibold text-error">
              退款 ¥{{ formatMoney(item.refund_amount) }}
            </span>
          </div>
          <p class="text-sm">{{ item.title }}</p>
          <p class="muted mt-0.5 text-xs">
            {{ item.goods_name }} · 订单 {{ item.order_no }} · 申请于 {{ formatTime(item.create_at) }}
          </p>
        </RouterLink>
      </div>

      <Pagination
        :page="page"
        :page-size="PAGE_SIZE"
        :total="total"
        unit="条"
        :disabled="loading"
        @update:page="go"
      />
    </template>
  </div>
</template>