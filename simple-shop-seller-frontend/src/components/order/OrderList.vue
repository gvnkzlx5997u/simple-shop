<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { listOrders } from '../../api/orders'
import type { OrderData, OrderStatus } from '../../api/types'
import {
  ORDER_STATUS_LABELS,
  ORDER_STATUS_ORDER,
  ORDER_STATUS_TONES,
} from '../../constants/labels'
import { M10 } from '../../constants/messages'
import { errorMessage } from '../../utils/error'
import { formatDateTime, formatPrice } from '../../utils/format'
import AppIcon from '../common/AppIcon.vue'
import Pagination from '../common/Pagination.vue'
import StatusTag from '../common/StatusTag.vue'

/**
 * 订单列表：订单管理页与客户/商品详情里的「该客户/该商品的订单」共用这一份实现，
 * 差别只在筛选条件与是否写回 URL。
 *
 * 组件负责「状态标签页 + 列表 + 分页」；标题与所在版式由宿主给。
 */
interface Props {
  /** 按买家筛选（客户详情的「该客户的订单」）。 */
  buyerId?: string | null
  /** 按商品筛选（商品详情的「该商品的订单」）。 */
  goodsId?: string | null
  pageSize?: number
  /** 一条订单都没有时的提示；状态 tab 在筛的时候统一用 emptyOrdersFiltered。 */
  emptyText?: string
  /**
   * 是否把状态与页码写回 URL。
   * 独立页面（/orders）要写，刷新与分享才能还原；嵌在客户/商品详情里时不写，
   * 否则会把宿主的地址改花，而宿主地址标识的是客户或商品，不是订单列表的筛选。
   */
  syncQuery?: boolean
  /** 是否显示「买家」列；嵌在客户详情里时每行都是同一个人，列出来是噪音。 */
  showBuyer?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  buyerId: null,
  goodsId: null,
  pageSize: 10,
  emptyText: M10.emptyOrders,
  syncQuery: true,
  showBuyer: true,
})

const route = useRoute()
const router = useRouter()

const TABS: { value: OrderStatus | ''; label: string }[] = [
  { value: '', label: '全部' },
  ...ORDER_STATUS_ORDER.map((status) => ({ value: status, label: ORDER_STATUS_LABELS[status] })),
]

function queryValue(key: string): string {
  const value = route.query[key]
  return typeof value === 'string' ? value : ''
}

function isOrderStatus(value: string): value is OrderStatus {
  return (ORDER_STATUS_ORDER as readonly string[]).includes(value)
}

// 初值从 URL 恢复（仅独立页面）：刷新、分享都能还原同一视图。
const initialStatus = props.syncQuery ? queryValue('status') : ''
const status = ref<OrderStatus | ''>(isOrderStatus(initialStatus) ? initialStatus : '')
const page = ref(props.syncQuery ? Math.max(1, Number(queryValue('page')) || 1) : 1)

const items = ref<OrderData[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref<string | null>(null)

/** 只有状态 tab 算「用户在筛」；buyer_id / goods_id 是入口自带的固定条件，不提示换条件。 */
const statusFiltered = computed(() => status.value !== '')

/** 空态与加载态要跨满整行；列数随「买家」列是否显示变化。 */
const columnCount = computed(() => (props.showBuyer ? 6 : 5))

/**
 * 只改动 status / page 两个键，其余查询参数原样保留：
 * 独立页面下别人带来的 buyer_id / goods_id 不能被冲掉。嵌入模式直接跳过。
 */
function writeQuery(): void {
  if (!props.syncQuery) {
    return
  }
  const query: Record<string, string> = {}
  for (const [key, value] of Object.entries(route.query)) {
    if (typeof value === 'string' && key !== 'status' && key !== 'page') {
      query[key] = value
    }
  }
  if (status.value) {
    query.status = status.value
  }
  if (page.value > 1) {
    query.page = String(page.value)
  }
  void router.replace({ query })
}

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const data = await listOrders({
      status: status.value || undefined,
      buyer_id: props.buyerId ?? undefined,
      goods_id: props.goodsId ?? undefined,
      page: page.value,
      page_size: props.pageSize,
    })
    items.value = data.items
    total.value = data.total
  } catch (caught) {
    error.value = errorMessage(caught)
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

onMounted(load)

// 切状态标签页即时生效；页码回到 1，否则会停在新筛选下不存在的页上。
watch(status, () => {
  page.value = 1
  writeQuery()
  void load()
})

// 宿主页换了筛选对象（同一个路由组件、不同 buyer_id）时重新拉取。
watch(
  () => [props.buyerId, props.goodsId],
  () => {
    page.value = 1
    void load()
  },
)

function selectTab(value: OrderStatus | ''): void {
  status.value = value
}

function onPageChange(next: number): void {
  page.value = next
  writeQuery()
  void load()
}

function resetFilters(): void {
  status.value = ''
}

/** 详情一律按订单主键进，与从哪个入口进来无关。 */
function openDetail(orderId: string): void {
  void router.push({ name: 'order-detail', params: { id: orderId } })
}
</script>

<template>
  <div class="flex flex-col gap-3">
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

    <div v-if="error" role="alert" class="alert alert-error alert-soft text-sm">
      <AppIcon name="forbidden" />
      <span>{{ error }}</span>
    </div>

    <div class="panel overflow-hidden p-0">
      <div class="overflow-x-auto">
        <table class="table table-sm">
          <thead>
            <tr>
              <th>订单号</th>
              <th v-if="showBuyer">买家</th>
              <th>状态</th>
              <th class="text-right">商品项</th>
              <th class="text-right">订单金额</th>
              <th>下单时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading && items.length === 0">
              <td :colspan="columnCount" class="text-sm text-base-content/60">加载中…</td>
            </tr>
            <tr v-else-if="items.length === 0 && !error">
              <td :colspan="columnCount" class="py-10 text-center">
                <p class="text-sm text-base-content/60">
                  {{ statusFiltered ? M10.emptyOrdersFiltered : emptyText }}
                </p>
                <button
                  v-if="statusFiltered"
                  type="button"
                  class="btn btn-sm btn-outline mt-3"
                  @click="resetFilters"
                >
                  查看全部订单
                </button>
              </td>
            </tr>
            <tr
              v-for="order in items"
              v-else
              :key="order.order_id"
              class="cursor-pointer transition-opacity hover:bg-base-200"
              :class="{ 'opacity-60': loading }"
              tabindex="0"
              :aria-label="`查看订单 ${order.order_no} 的详情`"
              @click="openDetail(order.order_id)"
              @keydown.enter.prevent="openDetail(order.order_id)"
            >
              <td class="font-mono text-xs">{{ order.order_no }}</td>
              <td v-if="showBuyer" class="font-medium">{{ order.buyer.username }}</td>
              <td>
                <StatusTag
                  :label="ORDER_STATUS_LABELS[order.status]"
                  :tone="ORDER_STATUS_TONES[order.status]"
                />
              </td>
              <td class="text-right tabular-nums">{{ order.item_count }}</td>
              <td class="text-right tabular-nums">{{ formatPrice(order.total_amount) }}</td>
              <td class="text-base-content/70">{{ formatDateTime(order.create_at) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="flex flex-wrap items-center gap-3">
      <span v-if="total > 0 && total <= pageSize" class="text-sm text-base-content/60">
        共 {{ total }} 笔订单
      </span>
      <Pagination
        v-if="total > pageSize"
        class="flex-1"
        :page="page"
        :page-size="pageSize"
        :total="total"
        :disabled="loading"
        @update:page="onPageChange"
      />
    </div>
  </div>
</template>
