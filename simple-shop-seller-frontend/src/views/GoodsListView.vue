<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { listGoods } from '../api/goods'
import type { GoodsStatus, GoodsSummaryData, OffShelfReason } from '../api/types'
import AppIcon from '../components/common/AppIcon.vue'
import Pagination from '../components/common/Pagination.vue'
import CategoryCascadeSelect from '../components/goods/CategoryCascadeSelect.vue'
import GoodsCard from '../components/goods/GoodsCard.vue'
import { M10 } from '../constants/messages'
import { usePagedList } from '../composables/usePagedList'

const PAGE_SIZE = 12

const route = useRoute()
const router = useRouter()

interface StatusOption {
  value: string
  label: string
  status?: GoodsStatus
  reason?: OffShelfReason
}

/**
 * 状态与下架原因合成一个下拉：后端要求「原因只能与已下架组合」，
 * 拆成两个控件时用户很容易选出会被 50002 拒绝的组合。
 */
const STATUS_OPTIONS: StatusOption[] = [
  { value: '', label: '全部状态' },
  { value: 'ON_SALE', label: '在售', status: 'ON_SALE' },
  { value: 'OFF_SHELF', label: '已下架（全部原因）', status: 'OFF_SHELF' },
  { value: 'OFF_SHELF:MANUAL', label: '手动下架', status: 'OFF_SHELF', reason: 'MANUAL' },
  { value: 'OFF_SHELF:STOCK_ZERO', label: '库存耗尽', status: 'OFF_SHELF', reason: 'STOCK_ZERO' },
  { value: 'OFF_SHELF:SOLD_OUT', label: '已售', status: 'OFF_SHELF', reason: 'SOLD_OUT' },
]

function queryValue(key: string): string {
  const value = route.query[key]
  return typeof value === 'string' ? value : ''
}

// 初值从 URL 恢复，刷新与分享后仍是同一个视图。
const statusFilter = ref(queryValue('status') + (queryValue('reason') ? ':' + queryValue('reason') : ''))
const filterL1Id = ref<string | null>(queryValue('l1') || null)
const filterL2Id = ref<string | null>(queryValue('l2') || null)
const keywordInput = ref(queryValue('keyword'))
const appliedKeyword = ref(queryValue('keyword'))

const hasFilters = computed(
  () => statusFilter.value !== '' || !!filterL1Id.value || !!appliedKeyword.value,
)

function splitStatus(value: string): { status?: GoodsStatus; reason?: OffShelfReason } {
  const [status, reason] = value.split(':')
  return {
    status: (status as GoodsStatus) || undefined,
    reason: (reason as OffShelfReason) || undefined,
  }
}

/** 把当前筛选写回 URL；page 为 1 时不写，保持地址干净。 */
function syncQuery(): void {
  const query: Record<string, string> = {}
  const { status, reason } = splitStatus(statusFilter.value)
  if (status) query.status = status
  if (reason) query.reason = reason
  if (filterL1Id.value) query.l1 = filterL1Id.value
  if (filterL2Id.value) query.l2 = filterL2Id.value
  if (appliedKeyword.value) query.keyword = appliedKeyword.value
  if (page.value > 1) query.page = String(page.value)
  void router.replace({ query })
}

const { items, total, page, loading, error, load } = usePagedList<GoodsSummaryData>(
  (currentPage) => {
    const { status, reason } = splitStatus(statusFilter.value)
    return listGoods({
      status,
      // 原因必须与已下架一起传，单独传会被后端按参数错误拒绝。
      off_shelf_reason: reason,
      // 后端接受一级或二级 id，一级会匹配其下全部二级。
      category_id: filterL2Id.value ?? filterL1Id.value ?? undefined,
      keyword: appliedKeyword.value || undefined,
      page: currentPage,
      page_size: PAGE_SIZE,
    })
  },
  { initialPage: Number(queryValue('page')) || 1 },
)

onMounted(load)

function applySearch(): void {
  appliedKeyword.value = keywordInput.value.trim()
  page.value = 1
  syncQuery()
  void load()
}

function resetFilters(): void {
  statusFilter.value = ''
  keywordInput.value = ''
  appliedKeyword.value = ''
  filterL1Id.value = null
  filterL2Id.value = null
  page.value = 1
  syncQuery()
  void load()
}

// 状态、原因、类别变化即时生效；关键字经「搜索」按钮应用。
watch([statusFilter, filterL1Id, filterL2Id], () => {
  page.value = 1
  syncQuery()
  void load()
})

function onPageChange(next: number): void {
  page.value = next
  syncQuery()
  void load()
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="text-lg font-semibold">商品列表</h1>
        <p class="text-xs text-base-content/60">
          {{ hasFilters ? `筛选出 ${total} 件商品` : `共 ${total} 件商品` }}
        </p>
      </div>
      <RouterLink to="/publish" class="btn btn-sm btn-primary">
        <AppIcon name="edit" />
        发布商品
      </RouterLink>
    </header>

    <form class="panel flex flex-wrap items-center gap-2" @submit.prevent="applySearch">
      <select
        v-model="statusFilter"
        class="select select-sm w-44"
        aria-label="按状态与下架原因筛选"
      >
        <option v-for="option in STATUS_OPTIONS" :key="option.value" :value="option.value">
          {{ option.label }}
        </option>
      </select>
      <CategoryCascadeSelect
        v-model:l1-id="filterL1Id"
        v-model:l2-id="filterL2Id"
        mode="filter"
        class="min-w-[280px] flex-1"
      />
      <input
        v-model="keywordInput"
        class="input input-sm w-52"
        type="search"
        placeholder="搜索商品名称"
        aria-label="搜索商品名称"
      />
      <button type="submit" class="btn btn-sm btn-outline" :disabled="loading">
        <AppIcon name="search" />
        搜索
      </button>
      <button type="button" class="btn btn-sm btn-ghost" :disabled="loading" @click="resetFilters">
        <AppIcon name="refresh" />
        重置
      </button>
    </form>

    <div v-if="error" role="alert" class="alert alert-error alert-soft text-sm">
      <AppIcon name="forbidden" />
      <span>{{ error }}</span>
    </div>

    <p v-if="loading" class="text-sm text-base-content/60">
      {{ items.length === 0 ? '加载中…' : '刷新中…' }}
    </p>

    <div
      v-if="items.length > 0"
      class="grid grid-cols-[repeat(auto-fill,minmax(210px,1fr))] gap-4 transition-opacity"
      :class="{ 'pointer-events-none opacity-60': loading }"
    >
      <GoodsCard v-for="goods in items" :key="goods.id" :goods="goods" />
    </div>

    <div
      v-else-if="!loading && !error"
      class="panel flex flex-col items-center gap-3 py-14 text-center"
    >
      <AppIcon name="product" class="text-[40px] text-base-content/20" />
      <p class="text-sm text-base-content/60">
        {{ hasFilters ? M10.emptyGoodsFiltered : M10.emptyGoods }}
      </p>
      <button v-if="hasFilters" type="button" class="btn btn-sm btn-outline" @click="resetFilters">
        清除筛选
      </button>
      <RouterLink v-else to="/publish" class="btn btn-sm btn-primary">发布商品</RouterLink>
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
