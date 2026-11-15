<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { fetchOrders } from '../api/order'
import type { OrderData, OrderStatus } from '../api/types'
import EmptyState from '../components/common/EmptyState.vue'
import Pagination from '../components/common/Pagination.vue'
import OrderRow from '../components/order/OrderRow.vue'
import { usePagedList } from '../composables/usePagedList'
import { ORDER_STATUS_FILTERS } from '../utils/orderStatus'

const PAGE_SIZE = 10

const status = ref<OrderStatus | ''>('')

/** 筛选条件留在页面里，分页与加载交给公共 composable。 */
const { items, total, page, loading, errorText, load, reloadFromFirstPage, goPage } =
  usePagedList<OrderData>(
    (pageNo) =>
      fetchOrders({
        status: status.value || undefined,
        page: pageNo,
        page_size: PAGE_SIZE,
      }),
    // 订单要空页回退：刚取消的订单可能让当前页变空，这时应退到最后一页而不是报「还没有订单」。
    { pageSize: PAGE_SIZE, emptyPageFallback: true },
  )

const filterLabel = computed(
  () => ORDER_STATUS_FILTERS.find((o) => o.value === status.value)?.label ?? '全部状态',
)

function onFilterChange(): void {
  // 换筛选必须回到第 1 页：第 3 页的新筛选结果可能只有 1 页，会看到空白。
  reloadFromFirstPage()
}

/**
 * 清空筛选回到全部。
 *
 * 注意不能只把 `status` 置空就完事：这个链接出现在「某状态没订单」的空态里，
 * 只改下拉值时列表已经是空的，页面会立刻显示「还没有订单」——
 * 但用户其实是有订单的，只是刚才那个状态没有。必须重新拉一次。
 */
function onClearFilter(): void {
  status.value = ''
  reloadFromFirstPage()
}

function go(nextPage: number): void {
  // 翻页后回到列表顶部，否则 10 条之外的用户点完还停在页脚，以为没反应。
  window.scrollTo({ top: 0, behavior: 'smooth' })
  goPage(nextPage)
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="mx-auto max-w-[960px] space-y-3 p-4">
    <div class="flex items-center justify-between gap-3">
      <h1 class="text-lg">我的订单</h1>
      <label class="filter flex items-center gap-1.5">
        <span class="muted text-xs">状态</span>
        <select
          v-model="status"
          class="select select-sm"
          aria-label="按状态筛选"
          :disabled="loading"
          @change="onFilterChange"
        >
          <option v-for="opt in ORDER_STATUS_FILTERS" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
        <!-- 切筛选/翻页时旧列表还在，得有个可见的「正在更新」，否则像按了没反应 -->
        <span v-if="loading" class="muted text-xs">更新中…</span>
      </label>
    </div>

    <p v-if="loading && !items.length" class="muted">加载中…</p>
    <p v-else-if="errorText" class="text-error">{{ errorText }}</p>

    <EmptyState
      v-else-if="!items.length"
      :title="status ? '没有「' + filterLabel + '」的订单' : '还没有订单'"
      :description="status ? '换个状态看看，或者去看全部订单。' : '从购物车结算后，订单会出现在这里。'"
    >
      <button v-if="status" class="btn btn-primary btn-sm" @click="onClearFilter">查看全部订单</button>
      <RouterLink v-else class="btn btn-primary btn-sm" to="/goods">去逛逛</RouterLink>
    </EmptyState>

    <template v-else>
      <div class="panel overflow-hidden p-0">
        <OrderRow v-for="order in items" :key="order.order_id" :order="order" />
      </div>

      <Pagination
        :page="page"
        :page-size="PAGE_SIZE"
        :total="total"
        unit="笔"
        :disabled="loading"
        @update:page="go"
      />
    </template>
  </div>
</template>