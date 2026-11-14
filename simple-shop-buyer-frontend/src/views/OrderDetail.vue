<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { ApiError } from '../api/http'
import { cancelOrder, fetchOrder, fetchOrderItems } from '../api/order'
import type { OrderData, OrderItemData } from '../api/types'
import { describeError } from '../stores/auth'
import { formatMoney, formatTime } from '../utils/format'
import { canCancelByBuyer, isTerminal, orderStatusText } from '../utils/orderStatus'

const route = useRoute()
const orderId = computed(() => String(route.params.id ?? ''))

/**
 * 订单头与明细**分开加载、分开报错**（D2-3 的硬要求）。
 * 明细接口挂了不该让整页变成错误页——订单号、状态、金额这些照样能读。
 */
const order = ref<OrderData | null>(null)
const orderLoading = ref(false)
const orderError = ref('')
const notFound = ref(false)

const items = ref<OrderItemData[]>([])
const itemsLoading = ref(false)
const itemsError = ref('')

const cancelling = ref(false)
const confirming = ref(false)
const notice = ref('')
const actionError = ref('')

const cancellable = computed(() => !!order.value && canCancelByBuyer(order.value.status))
const finished = computed(() => !!order.value && isTerminal(order.value.status))

async function loadOrder(): Promise<void> {
  orderLoading.value = true
  orderError.value = ''
  notFound.value = false
  try {
    order.value = await fetchOrder(orderId.value)
  } catch (err) {
    order.value = null
    // 别人的订单后端也按「不存在」返回，所以 404 / 60001 都当空态处理。
    notFound.value =
      err instanceof ApiError && (err.code === 60001 || err.httpStatus === 404)
    if (!notFound.value) {
      orderError.value = describeError(err)
    }
  } finally {
    orderLoading.value = false
  }
}

async function loadItems(): Promise<void> {
  itemsLoading.value = true
  itemsError.value = ''
  try {
    const data = await fetchOrderItems(orderId.value)
    // 顺序直接沿用后端：后端按商品名升序（`OrderByGoodsNameAscIdAsc`）返回，前端不重排。
    items.value = data.items ?? []
  } catch (err) {
    itemsError.value = describeError(err)
    items.value = []
  } finally {
    itemsLoading.value = false
  }
}

async function loadAll(): Promise<void> {
  await loadOrder()
  if (order.value) {
    void loadItems()
  }
}

/** 第一次点先问，第二次点才真取消——比 window.confirm 可控，也不会被浏览器拦。 */
function askCancel(): void {
  actionError.value = ''
  notice.value = ''
  confirming.value = true
}

async function doCancel(): Promise<void> {
  if (!order.value || cancelling.value) return
  cancelling.value = true
  actionError.value = ''
  try {
    const result = await cancelOrder(order.value.order_id)
    // 就地更新状态，不整页重拉：明细和金额都没变，重拉只会闪一下。
    order.value = { ...order.value, status: result.status, update_at: result.update_at }
    notice.value = '订单已取消'
    confirming.value = false
  } catch (err) {
    // 后端拒绝时按错误码给中文（60003 已发货不能取消等）。
    actionError.value = describeError(err)
  } finally {
    cancelling.value = false
  }
}

onMounted(loadAll)
</script>

<template>
  <div class="page stack">
    <div class="head">
      <h1 class="page-title">订单详情</h1>
      <RouterLink to="/orders" class="muted small">返回订单列表</RouterLink>
    </div>

    <p v-if="orderLoading" class="muted">加载中…</p>

    <div v-else-if="notFound" class="card empty">
      <p class="empty-title">订单不存在</p>
      <p class="muted">这笔订单可能已被删除，或者不属于当前账号。</p>
      <RouterLink class="primary-link" to="/orders">返回我的订单</RouterLink>
    </div>

    <!-- 订单头自己也要能重试：明细挂了有重试、头挂了却只能干看，就不叫「两块独立处理」了 -->
    <div v-else-if="orderError" class="card head-error">
      <p class="error">{{ orderError }}</p>
      <button @click="loadAll">重试</button>
    </div>

    <template v-else-if="order">
      <p v-if="notice" class="notice">{{ notice }}</p>
      <p v-if="actionError" class="error">{{ actionError }}</p>

      <div class="card">
        <div class="row1">
          <span class="no">{{ order.order_no }}</span>
          <span :class="['badge', 'st-' + order.status.toLowerCase()]">
            {{ orderStatusText(order.status) }}
          </span>
        </div>
        <div class="grid">
          <span class="muted">下单时间</span>
          <span>{{ formatTime(order.create_at) }}</span>
          <span class="muted">最近更新</span>
          <span>{{ formatTime(order.update_at) }}</span>
          <span class="muted">商品件数</span>
          <span>{{ order.item_count }} 件</span>
          <span class="muted">订单总额</span>
          <span class="amount">¥{{ formatMoney(order.total_amount) }}</span>
        </div>
      </div>

      <div class="card">
        <h2 class="section-title">商品明细</h2>

        <p v-if="itemsLoading" class="muted">明细加载中…</p>

        <div v-else-if="itemsError" class="items-error">
          <p class="error">{{ itemsError }}</p>
          <button @click="loadItems">重试</button>
        </div>

        <table v-else-if="items.length" class="items">
          <thead>
            <tr>
              <th class="left">商品</th>
              <th class="num">单价</th>
              <th class="num">数量</th>
              <th class="num">小计</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, idx) in items" :key="item.goods_id + '-' + idx">
              <td class="left">{{ item.goods_name }}</td>
              <td class="num">¥{{ formatMoney(item.unit_price) }}</td>
              <td class="num">{{ item.quantity }}</td>
              <td class="num">¥{{ formatMoney(item.amount) }}</td>
            </tr>
          </tbody>
        </table>

        <p v-else class="muted">这笔订单没有明细</p>
      </div>

      <div v-if="cancellable || finished" class="card actions">
        <template v-if="cancellable">
          <template v-if="confirming">
            <span class="muted">确认取消这笔订单吗？取消后不可恢复。</span>
            <button :disabled="cancelling" @click="doCancel">
              {{ cancelling ? '取消中…' : '确认取消' }}
            </button>
            <button :disabled="cancelling" @click="confirming = false">再想想</button>
          </template>
          <template v-else>
            <span class="muted">发货前可以取消，已发货后不能取消。</span>
            <button @click="askCancel">取消订单</button>
          </template>
        </template>
        <span v-else-if="finished" class="muted">这笔订单已经结束，不能再操作。</span>
      </div>
    </template>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.page-title {
  font-size: 18px;
  margin: 0;
}

.small {
  font-size: 12px;
}

.notice {
  color: #15803d;
  margin: 0;
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
  border-radius: 4px;
}

.row1 {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.no {
  font-weight: 600;
  font-size: 15px;
  letter-spacing: 0.3px;
}

.grid {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 6px 12px;
  font-size: 13px;
}

.amount {
  color: var(--color-danger);
  font-weight: 600;
}

.section-title {
  font-size: 15px;
  margin: 0 0 10px;
}

.items {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.items th {
  text-align: left;
  font-weight: 500;
  color: var(--color-text-weak);
  padding: 6px 8px;
  border-bottom: 1px solid var(--color-border);
}

.items td {
  padding: 8px;
  border-bottom: 1px solid var(--color-border);
}

.items tr:last-child td {
  border-bottom: none;
}

.left {
  text-align: left;
}

.num {
  text-align: right;
  white-space: nowrap;
}

.items-error {
  display: flex;
  align-items: center;
  gap: 12px;
}

.head-error {
  display: flex;
  align-items: center;
  gap: 12px;
}

.head-error .error {
  margin: 0;
  flex: 1;
}

.actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.actions .muted {
  flex: 1;
  min-width: 160px;
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
</style>
