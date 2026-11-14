<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { fetchOrders } from '../api/order'
import type { OrderData, OrderStatus } from '../api/types'
import { usePagedList } from '../composables/usePagedList'
import { formatMoney, formatTime } from '../utils/format'
import { ORDER_STATUS_FILTERS, orderStatusText } from '../utils/orderStatus'

const PAGE_SIZE = 10

const status = ref<OrderStatus | ''>('')

/** 筛选条件留在页面里，分页与加载交给公共 composable。 */
const {
  items,
  total,
  page,
  loading,
  errorText,
  totalPages,
  canPrev,
  canNext,
  hasPager,
  load,
  reloadFromFirstPage,
  goPage,
} = usePagedList<OrderData>(
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
  <div class="page stack">
    <div class="head">
      <h1 class="page-title">我的订单</h1>
      <label class="filter">
        <span class="muted small">状态</span>
        <select v-model="status" :disabled="loading" @change="onFilterChange">
          <option v-for="opt in ORDER_STATUS_FILTERS" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
        <!-- 切筛选/翻页时旧列表还在，得有个可见的「正在更新」，否则像按了没反应 -->
        <span v-if="loading" class="muted small">更新中…</span>
      </label>
    </div>

    <p v-if="loading && !items.length" class="muted">加载中…</p>
    <p v-else-if="errorText" class="error">{{ errorText }}</p>

    <div v-else-if="!items.length" class="card empty">
      <p class="empty-title">
        {{ status ? '没有「' + filterLabel + '」的订单' : '还没有订单' }}
      </p>
      <p class="muted">
        {{ status ? '换个状态看看，或者去看全部订单。' : '从购物车结算后，订单会出现在这里。' }}
      </p>
      <button v-if="status" class="primary-link" @click="onClearFilter">
        查看全部订单
      </button>
      <RouterLink v-else class="primary-link" to="/goods">去逛逛</RouterLink>
    </div>

    <template v-else>
      <div class="card list">
        <RouterLink
          v-for="order in items"
          :key="order.order_id"
          :to="'/orders/' + order.order_id"
          class="order-row"
        >
          <div class="line1">
            <span class="no">{{ order.order_no }}</span>
            <span :class="['badge', 'st-' + order.status.toLowerCase()]">
              {{ orderStatusText(order.status) }}
            </span>
          </div>
          <div class="line2 muted small">
            下单时间 {{ formatTime(order.create_at) }} · 共 {{ order.item_count }} 件
          </div>
          <div class="line3">
            <span class="amount">¥{{ formatMoney(order.total_amount) }}</span>
            <span class="detail-hint">查看详情 ›</span>
          </div>
        </RouterLink>
      </div>

      <!-- 页码保留，按钮只在一页以上时出现 -->
      <div class="pager">
        <button v-if="hasPager" :disabled="!canPrev" @click="go(page - 1)">上一页</button>
        <span class="muted small">第 {{ page }} / {{ totalPages }} 页 · 共 {{ total }} 笔</span>
        <button v-if="hasPager" :disabled="!canNext" @click="go(page + 1)">下一页</button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.page-title {
  font-size: 18px;
  margin: 0;
}

.filter {
  display: flex;
  align-items: center;
  gap: 6px;
}

.filter select {
  font: inherit;
  padding: 4px 8px;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: #fff;
}

.small {
  font-size: 12px;
}

.empty {
  text-align: center;
  padding: 40px 16px;
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 8px;
}

.primary-link {
  display: inline-block;
  margin-top: 12px;
  padding: 6px 16px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 4px;
  font-size: inherit;
  cursor: pointer;
}

.list {
  padding: 0;
}

.order-row {
  display: block;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
  color: var(--color-text);
}

.order-row:last-child {
  border-bottom: none;
}

.order-row:hover {
  background: var(--color-bg);
}

.line1 {
  display: flex;
  align-items: center;
  gap: 10px;
}

.no {
  font-weight: 600;
  letter-spacing: 0.3px;
}

.line2 {
  margin-top: 2px;
}

.line3 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
}

.amount {
  color: var(--color-danger);
  font-weight: 600;
  font-size: 16px;
}

.detail-hint {
  color: var(--color-primary);
  font-size: 13px;
}

.badge {
  font-size: 12px;
  padding: 1px 8px;
  border-radius: 10px;
  border: 1px solid transparent;
}

.st-pending_confirm {
  color: #b45309;
  background: #fffbeb;
  border-color: #fde68a;
}

.st-confirmed,
.st-prepared,
.st-shipped {
  color: #1d4ed8;
  background: #eff6ff;
  border-color: #bfdbfe;
}

.st-completed {
  color: #15803d;
  background: #f0fdf4;
  border-color: #bbf7d0;
}

.st-canceled {
  color: var(--color-text-weak);
  background: var(--color-fill);
  border-color: var(--color-border);
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 8px 0;
}
</style>
