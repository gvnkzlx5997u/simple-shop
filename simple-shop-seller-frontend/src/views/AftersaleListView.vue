<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { listSellerAftersales } from '../api/aftersale'
import type { AftersaleData, AftersaleStatus } from '../api/types'
import AppIcon from '../components/common/AppIcon.vue'
import Pagination from '../components/common/Pagination.vue'
import StatusTag from '../components/common/StatusTag.vue'
import {
  AFTERSALE_RESULT_LABELS,
  AFTERSALE_RESULT_TONES,
  AFTERSALE_STATUS_LABELS,
  AFTERSALE_STATUS_ORDER,
  AFTERSALE_STATUS_TONES,
} from '../constants/labels'
import { M10 } from '../constants/messages'
import { usePagedList } from '../composables/usePagedList'
import { formatDateTime, formatPrice } from '../utils/format'

/**
 * 售后管理（卖家）。
 *
 * 只有「全部 + 三态」一个筛选维度，外加订单号搜索：买家端是按自己的单看，
 * 卖家端是按「哪笔订单出事」找，所以订单号比商品名更常用。
 * 状态与订单号都写回 URL，刷新与分享能还原同一视图。
 */
const PAGE_SIZE = 10

const route = useRoute()
const router = useRouter()

const TABS: { value: AftersaleStatus | ''; label: string }[] = [
  { value: '', label: '全部' },
  ...AFTERSALE_STATUS_ORDER.map((status) => ({
    value: status,
    label: AFTERSALE_STATUS_LABELS[status],
  })),
]

function queryValue(key: string): string {
  const value = route.query[key]
  return typeof value === 'string' ? value : ''
}

function isAftersaleStatus(value: string): value is AftersaleStatus {
  return (AFTERSALE_STATUS_ORDER as readonly string[]).includes(value)
}

const initialStatus = queryValue('status')
const status = ref<AftersaleStatus | ''>(
  isAftersaleStatus(initialStatus) ? initialStatus : '',
)
const orderNoInput = ref(queryValue('order_no').trim())
const appliedOrderNo = ref(queryValue('order_no').trim())

const { items, total, page, loading, error, load } = usePagedList<AftersaleData>(
  (currentPage) =>
    listSellerAftersales({
      status: status.value || undefined,
      order_no: appliedOrderNo.value || undefined,
      page: currentPage,
      page_size: PAGE_SIZE,
    }),
  { initialPage: Number(queryValue('page')) || 1 },
)

/** 只有这两个条件算「用户在筛」（订单号是入口也可能带来的，所以两者都算）。 */
const filtered = computed(() => status.value !== '' || appliedOrderNo.value !== '')

function writeQuery(): void {
  const query: Record<string, string> = {}
  if (status.value) {
    query.status = status.value
  }
  if (appliedOrderNo.value) {
    query.order_no = appliedOrderNo.value
  }
  if (page.value > 1) {
    query.page = String(page.value)
  }
  void router.replace({ query })
}

function reloadFromFirstPage(): void {
  page.value = 1
  writeQuery()
  void load()
}

function applySearch(): void {
  appliedOrderNo.value = orderNoInput.value.trim()
  reloadFromFirstPage()
}

function selectTab(value: AftersaleStatus | ''): void {
  status.value = value
  reloadFromFirstPage()
}

function resetFilters(): void {
  status.value = ''
  orderNoInput.value = ''
  appliedOrderNo.value = ''
  reloadFromFirstPage()
}

function onPageChange(next: number): void {
  page.value = next
  writeQuery()
  void load()
}

function openDetail(id: string): void {
  void router.push({ name: 'aftersale-detail', params: { id } })
}

onMounted(load)

/**
 * 外部带过来的筛选（例如订单详情里的「在售后管理中查看」）要在同一个路由组件内生效：
 * 组件已挂载时改 query 不会重新 onMounted，必须显式监听。
 */
watch(
  () => [route.query.status, route.query.order_no],
  () => {
    const nextStatus = queryValue('status')
    const nextOrderNo = queryValue('order_no').trim()
    const statusChanged = (isAftersaleStatus(nextStatus) ? nextStatus : '') !== status.value
    const orderNoChanged = nextOrderNo !== appliedOrderNo.value
    if (!statusChanged && !orderNoChanged) {
      return
    }
    status.value = isAftersaleStatus(nextStatus) ? nextStatus : ''
    orderNoInput.value = nextOrderNo
    appliedOrderNo.value = nextOrderNo
    page.value = 1
    void load()
  },
)
</script>

<template>
  <div class="flex flex-col gap-4">
    <header class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 class="text-lg font-semibold">售后管理</h1>
        <p class="text-xs text-base-content/60">
          买家对「交易完成」的订单申请售后；你在这里登记处理结果，或取消（同拒绝）。
        </p>
      </div>
    </header>

    <div role="tablist" class="tabs tabs-box tabs-sm w-fit max-w-full overflow-x-auto">
      <button
        v-for="tab in TABS"
        :key="tab.value || 'all'"
        type="button"
        role="tab"
        class="tab whitespace-nowrap"
        :class="{ 'tab-active': status === tab.value }"
        :aria-selected="status === tab.value"
        @click="selectTab(tab.value)"
      >
        {{ tab.label }}
      </button>
    </div>

    <form class="panel flex flex-wrap items-center gap-2" @submit.prevent="applySearch">
      <input
        v-model="orderNoInput"
        class="input input-sm w-56 font-mono"
        type="search"
        :placeholder="M10.aftersaleOrderNoPlaceholder"
        aria-label="按订单号搜索"
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

    <div class="panel overflow-hidden p-0">
      <div class="overflow-x-auto">
        <table class="table table-sm">
          <thead>
            <tr>
              <th>售后单号</th>
              <th>订单号</th>
              <th>商品</th>
              <th>标题</th>
              <th>状态</th>
              <th>处理结果</th>
              <th class="text-right">退款金额</th>
              <th>申请时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading && items.length === 0">
              <td colspan="8" class="text-sm text-base-content/60">加载中…</td>
            </tr>
            <tr v-else-if="items.length === 0 && !error">
              <td colspan="8" class="py-10 text-center">
                <p class="text-sm text-base-content/60">
                  {{ filtered ? M10.emptyAftersalesFiltered : M10.emptyAftersales }}
                </p>
                <button
                  v-if="filtered"
                  type="button"
                  class="btn btn-sm btn-outline mt-3"
                  @click="resetFilters"
                >
                  查看全部售后
                </button>
              </td>
            </tr>
            <tr
              v-for="item in items"
              v-else
              :key="item.aftersale_id"
              class="cursor-pointer transition-opacity hover:bg-base-200"
              :class="{ 'opacity-60': loading }"
              tabindex="0"
              :aria-label="`查看售后 ${item.aftersale_no} 的详情`"
              @click="openDetail(item.aftersale_id)"
              @keydown.enter.prevent="openDetail(item.aftersale_id)"
            >
              <td class="font-mono text-xs">{{ item.aftersale_no }}</td>
              <td class="font-mono text-xs text-base-content/70">{{ item.order_no }}</td>
              <td class="max-w-[180px] truncate" :title="item.goods_name">{{ item.goods_name }}</td>
              <td class="max-w-[200px] truncate" :title="item.title">{{ item.title }}</td>
              <td>
                <StatusTag
                  :label="AFTERSALE_STATUS_LABELS[item.status]"
                  :tone="AFTERSALE_STATUS_TONES[item.status]"
                />
              </td>
              <td>
                <StatusTag
                  v-if="item.result"
                  :label="AFTERSALE_RESULT_LABELS[item.result]"
                  :tone="AFTERSALE_RESULT_TONES[item.result]"
                />
                <span v-else class="text-xs text-base-content/40">—</span>
              </td>
              <td class="text-right tabular-nums">
                {{ item.refund_amount ? formatPrice(item.refund_amount) : '—' }}
              </td>
              <td class="text-base-content/70">{{ formatDateTime(item.create_at) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="flex flex-wrap items-center gap-3">
      <span v-if="total > 0 && total <= PAGE_SIZE" class="text-sm text-base-content/60">
        共 {{ total }} 条售后
      </span>
      <Pagination
        v-if="total > PAGE_SIZE"
        class="flex-1"
        :page="page"
        :page-size="PAGE_SIZE"
        :total="total"
        :disabled="loading"
        @update:page="onPageChange"
      />
    </div>
  </div>
</template>
