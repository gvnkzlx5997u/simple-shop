<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { enterTrade, listIntentions, markTradeFailure, markTradeSuccess } from '../api/intention'
import { freezeGoods, getCurrentGoods, takeGoodsOffline, unfreezeGoods } from '../api/product'
import type {
  GoodsData,
  IntentionItemData,
  IntentionPageData,
  TradeFailureRequest,
} from '../api/types'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import IntentionTable from '../components/IntentionTable.vue'
import Pagination from '../components/Pagination.vue'
import ProductCard from '../components/ProductCard.vue'
import TradeFailureDialog from '../components/TradeFailureDialog.vue'
import { M10 } from '../constants/messages'
import { useToast } from '../composables/useToast'
import { errorMessage } from '../utils/error'

const PAGE_SIZE = 10

const toast = useToast()

const goods = ref<GoodsData | null>(null)
const intentions = ref<IntentionPageData | null>(null)
const page = ref(1)
const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)

const items = computed(() => intentions.value?.items ?? [])
const total = computed(() => intentions.value?.total ?? 0)
const queueCount = computed(() => intentions.value?.queue_count ?? 0)
const canEnterTrade = computed(() => goods.value?.status === 'on_sale')
const canDecideTrade = computed(() => goods.value?.freeze_by === 'trade')

type PendingAction =
  | { kind: 'freeze' }
  | { kind: 'offline' }
  | { kind: 'enter-trade'; item: IntentionItemData }
  | { kind: 'trade-success'; item: IntentionItemData }

const pending = ref<PendingAction | null>(null)
const failureOpen = ref(false)
const failureItem = ref<IntentionItemData | null>(null)

const confirmMessage = computed(() => {
  switch (pending.value?.kind) {
    case 'freeze':
      return M10.confirmFreeze
    case 'offline':
      return M10.confirmOffline
    case 'enter-trade':
      return M10.confirmEnterTrade
    case 'trade-success':
      return M10.confirmTradeSuccess
    default:
      return ''
  }
})
const confirmDanger = computed(
  () => pending.value?.kind === 'offline' || pending.value?.kind === 'trade-success',
)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const [currentGoods, pageData] = await Promise.all([
      getCurrentGoods(),
      listIntentions(page.value, PAGE_SIZE),
    ])
    goods.value = currentGoods
    intentions.value = pageData
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

function onFreeze(): void {
  pending.value = { kind: 'freeze' }
}

function onOffline(): void {
  pending.value = { kind: 'offline' }
}

function onEnterTrade(item: IntentionItemData): void {
  pending.value = { kind: 'enter-trade', item }
}

function onTradeSuccess(item: IntentionItemData): void {
  pending.value = { kind: 'trade-success', item }
}

function onTradeFailure(item: IntentionItemData): void {
  failureItem.value = item
  failureOpen.value = true
}

async function onUnfreeze(): Promise<void> {
  busy.value = true
  try {
    await unfreezeGoods()
    await load()
    toast.success('已解冻')
  } catch (caught) {
    toast.error(errorMessage(caught))
  } finally {
    busy.value = false
  }
}

async function onConfirm(): Promise<void> {
  const action = pending.value
  if (!action) {
    return
  }
  busy.value = true
  try {
    if (action.kind === 'freeze') {
      await freezeGoods()
    } else if (action.kind === 'offline') {
      await takeGoodsOffline()
    } else if (action.kind === 'enter-trade') {
      await enterTrade(action.item.id)
    } else if (action.kind === 'trade-success') {
      await markTradeSuccess(action.item.id)
    }
    pending.value = null
    await load()
    toast.success('操作成功')
  } catch (caught) {
    pending.value = null
    toast.error(errorMessage(caught))
  } finally {
    busy.value = false
  }
}

async function onFailureSubmit(payload: TradeFailureRequest): Promise<void> {
  busy.value = true
  try {
    await markTradeFailure(payload)
    failureOpen.value = false
    await load()
    toast.success('已标记交易失败')
  } catch (caught) {
    toast.error(errorMessage(caught))
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="page">
    <h1 class="page__title">当前商品</h1>

    <p v-if="error" class="page__error">{{ error }}</p>

    <div v-if="loading && !goods" class="card muted">加载中…</div>

    <template v-else-if="goods">
      <ProductCard
        :goods="goods"
        mode="manage"
        :queue-count="queueCount"
        @freeze="onFreeze"
        @unfreeze="onUnfreeze"
        @offline="onOffline"
      />

      <section class="section">
        <div class="section__header">
          <h2 class="section__title">意向名单</h2>
          <span class="muted">当前队列：{{ queueCount }} / 1000</span>
        </div>
        <p v-if="queueCount >= 1000" class="section__notice">
          当前排队人数已达上限，暂时无法提交新意向。
        </p>
        <IntentionTable
          :items="items"
          :can-enter-trade="canEnterTrade"
          :can-decide-trade="canDecideTrade"
          :loading="loading"
          @enter-trade="onEnterTrade"
          @trade-success="onTradeSuccess"
          @trade-failure="onTradeFailure"
        />
        <Pagination
          v-if="total > PAGE_SIZE"
          class="section__pagination"
          :page="page"
          :page-size="PAGE_SIZE"
          :total="total"
          :disabled="loading"
          @update:page="onPageChange"
        />
      </section>
    </template>

    <section v-else-if="!error" class="empty card">
      <p>当前没有商品。</p>
      <RouterLink to="/publish" class="btn btn--primary">发布商品</RouterLink>
    </section>

    <ConfirmDialog
      :open="pending !== null"
      :message="confirmMessage"
      :danger="confirmDanger"
      :loading="busy"
      @confirm="onConfirm"
      @cancel="pending = null"
    />
    <TradeFailureDialog
      :open="failureOpen"
      :intention-id="failureItem?.id ?? ''"
      :loading="busy"
      @submit="onFailureSubmit"
      @cancel="failureOpen = false"
    />
  </div>
</template>

<style scoped>
.page__title {
  margin-bottom: 16px;
}

.page__error {
  margin: 0 0 16px;
  padding: 10px 12px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  background: #fef2f2;
  color: var(--danger);
}

.section {
  margin-top: 24px;
}

.section__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section__title {
  margin: 0;
  font-size: 16px;
}

.section__notice {
  margin: 0 0 12px;
  color: var(--warning);
}

.section__pagination {
  margin-top: 12px;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 48px;
}
</style>
