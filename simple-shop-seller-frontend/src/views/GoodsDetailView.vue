<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  getGoods,
  reListGoods,
  takeGoodsOffShelf,
  updateGoodsCategory,
  updateGoodsStock,
} from '../api/goods'
import { enterTrade, listIntentions, markTradeFailure, markTradeSuccess } from '../api/intention'
import type { Disposal, GoodsDetailData, IntentionEntryData, IntentionStatus } from '../api/types'
import placeholderImage from '../assets/product.svg'
import ConfirmDialog from '../components/common/ConfirmDialog.vue'
import Pagination from '../components/common/Pagination.vue'
import StatusTag from '../components/common/StatusTag.vue'
import CategoryCascadeSelect from '../components/goods/CategoryCascadeSelect.vue'
import DescriptionBlocks from '../components/goods/DescriptionBlocks.vue'
import IntentionTable from '../components/intention/IntentionTable.vue'
import TradeFailureDialog from '../components/intention/TradeFailureDialog.vue'
import { GOODS_STATUS_LABELS, INTENTION_STATUS_LABELS } from '../constants/labels'
import { M10 } from '../constants/messages'
import { useToast } from '../composables/useToast'
import { errorMessage } from '../utils/error'
import { formatDateTime, formatPrice } from '../utils/format'
import { validateAdjustStock } from '../validation'

const PAGE_SIZE = 10

const route = useRoute()
const router = useRouter()
const toast = useToast()

const goods = ref<GoodsDetailData | null>(null)
const intentions = ref<IntentionEntryData[]>([])
const intentionTotal = ref(0)
const intentionPage = ref(1)
const intentionStatus = ref<IntentionStatus | ''>('')
const loadingGoods = ref(false)
const loadingIntentions = ref(false)
const error = ref<string | null>(null)

const goodsId = computed(() => (typeof route.params.id === 'string' ? route.params.id : ''))

const INTENTION_STATUS_OPTIONS = (Object.keys(INTENTION_STATUS_LABELS) as IntentionStatus[]).map(
  (value) => ({ value, label: INTENTION_STATUS_LABELS[value] }),
)

async function loadGoods(): Promise<void> {
  loadingGoods.value = true
  error.value = null
  try {
    goods.value = await getGoods(goodsId.value)
  } catch (caught) {
    error.value = errorMessage(caught)
    goods.value = null
  } finally {
    loadingGoods.value = false
  }
}

async function loadIntentions(): Promise<void> {
  loadingIntentions.value = true
  try {
    const data = await listIntentions(goodsId.value, {
      status: intentionStatus.value || undefined,
      page: intentionPage.value,
      page_size: PAGE_SIZE,
    })
    intentions.value = data.items
    intentionTotal.value = data.total
  } catch (caught) {
    toast.error(errorMessage(caught))
  } finally {
    loadingIntentions.value = false
  }
}

function loadAll(): Promise<void[]> {
  return Promise.all([loadGoods(), loadIntentions()])
}

onMounted(loadAll)
watch(goodsId, loadAll)
watch(intentionStatus, () => {
  intentionPage.value = 1
  void loadIntentions()
})

/** 队首 = 第一页数据里的第一条 QUEUED;翻页后无法从当前页判断,置空。 */
const queueHeadId = computed(() => {
  if (intentionPage.value !== 1) {
    return null
  }
  return intentions.value.find((item) => item.status === 'QUEUED')?.id ?? null
})

const isOnSale = computed(() => goods.value?.status === 'ON_SALE')
const isFrozen = computed(() => goods.value?.status === 'FROZEN')
const isOffManual = computed(() => goods.value?.status === 'OFF_MANUAL')
const isOffZero = computed(() => goods.value?.status === 'OFF_ZERO')

// ---- 商品操作:下架 / 重新上架 / 进入交易 / 标记成功(共用确认弹窗) ----

type PendingAction =
  | { kind: 'off-shelf' }
  | { kind: 're-list' }
  | { kind: 'enter-trade'; item: IntentionEntryData }
  | { kind: 'trade-success'; item: IntentionEntryData }

const pending = ref<PendingAction | null>(null)
const failureItem = ref<IntentionEntryData | null>(null)
const busy = ref(false)

const confirmMessage = computed(() => {
  switch (pending.value?.kind) {
    case 'off-shelf':
      return M10.confirmOffShelf
    case 're-list':
      return M10.confirmReList
    case 'enter-trade':
      return M10.confirmEnterTrade
    case 'trade-success':
      // 最后一件成交 → 售罄归档,提示语更重。
      return (goods.value?.stock ?? 0) <= 1
        ? M10.confirmTradeSuccessArchive
        : M10.confirmTradeSuccessWithStock
    default:
      return ''
  }
})
const confirmDanger = computed(
  () => pending.value?.kind === 'off-shelf' || pending.value?.kind === 'trade-success',
)

// ---- 大图与图集切换:默认主图,点击缩略图切换;路由切换商品时回默认 ----

const selectedImageUrl = ref<string | null>(null)
const heroFailed = ref(false)

const activeImageUrl = computed(() => selectedImageUrl.value ?? goods.value?.main_image_url ?? null)

const heroImage = computed(() =>
  activeImageUrl.value && !heroFailed.value ? activeImageUrl.value : placeholderImage,
)

function selectImage(url: string): void {
  selectedImageUrl.value = url
  heroFailed.value = false
}

function onHeroError(): void {
  heroFailed.value = true
}

watch(goodsId, () => {
  selectedImageUrl.value = null
  heroFailed.value = false
})

async function onConfirm(): Promise<void> {
  const action = pending.value
  if (!action) {
    return
  }
  busy.value = true
  try {
    if (action.kind === 'off-shelf') {
      await takeGoodsOffShelf(goodsId.value)
      toast.success(M10.offShelfDone)
      await loadGoods()
    } else if (action.kind === 're-list') {
      await reListGoods(goodsId.value)
      toast.success(M10.reListDone)
      await loadGoods()
    } else if (action.kind === 'enter-trade') {
      await enterTrade(action.item.id)
      toast.success(M10.enterTradeDone)
      await loadAll()
    } else {
      const result = await markTradeSuccess(action.item.id)
      if (result.goods_status === 'ARCHIVED') {
        // 售罄归档:商品已从当前表转入历史,详情页即将 404,直接回列表。
        toast.success(M10.tradeSuccessArchived)
        await router.replace({ name: 'goods-list' })
        return
      }
      toast.success(M10.tradeSuccessOnSale)
      await loadAll()
    }
  } catch (caught) {
    toast.error(errorMessage(caught))
    void loadAll()
  } finally {
    pending.value = null
    busy.value = false
  }
}

async function onFailureSubmit(disposal: Disposal): Promise<void> {
  const item = failureItem.value
  if (!item) {
    return
  }
  busy.value = true
  try {
    await markTradeFailure(item.id, disposal)
    toast.success(M10.tradeFailureDone)
    failureItem.value = null
    await loadAll()
  } catch (caught) {
    toast.error(errorMessage(caught))
    void loadAll()
  } finally {
    busy.value = false
  }
}

// ---- 库存行内编辑(OFF_ZERO 改 >0 保存即自动重新上架) ----

const stockEditing = ref(false)
const stockInput = ref<number | ''>(0)

function startStockEdit(): void {
  stockInput.value = goods.value?.stock ?? 0
  stockEditing.value = true
}

async function saveStock(): Promise<void> {
  const result = validateAdjustStock(stockInput.value)
  if (!result.ok) {
    // 同一业务码 20015 在发布(≥1)与调整(≥0)语境文案不同,这里按调整口径直接取文案。
    toast.error(M10.adjustStockInvalid)
    return
  }
  busy.value = true
  try {
    await updateGoodsStock(goodsId.value, result.value)
    toast.success(M10.stockUpdated)
    stockEditing.value = false
    await loadGoods()
  } catch (caught) {
    toast.error(errorMessage(caught))
    void loadGoods()
  } finally {
    busy.value = false
  }
}

// ---- 改挂类别行内编辑 ----

const categoryEditing = ref(false)
const categoryL1 = ref<string | null>(null)
const categoryL2 = ref<string | null>(null)

function startCategoryEdit(): void {
  categoryL1.value = goods.value?.category.l1_id ?? null
  categoryL2.value = goods.value?.category.l2_id ?? null
  categoryEditing.value = true
}

async function saveCategory(): Promise<void> {
  if (!categoryL1.value) {
    toast.error(M10.categoryL1Required)
    return
  }
  if (!categoryL2.value) {
    toast.error(M10.categoryL2Required)
    return
  }
  busy.value = true
  try {
    await updateGoodsCategory(goodsId.value, categoryL2.value)
    toast.success(M10.categoryUpdated)
    categoryEditing.value = false
    await loadGoods()
  } catch (caught) {
    toast.error(errorMessage(caught))
    void loadGoods()
  } finally {
    busy.value = false
  }
}

function onIntentionPageChange(next: number): void {
  intentionPage.value = next
  void loadIntentions()
}
</script>

<template>
  <div class="page">
    <div class="page__header">
      <h1 class="page__title">商品详情</h1>
      <RouterLink to="/" class="btn">返回列表</RouterLink>
    </div>

    <p v-if="error" class="page__error">
      {{ error }}
      <RouterLink to="/">返回商品列表</RouterLink>
    </p>
    <div v-else-if="loadingGoods && !goods" class="card muted">加载中…</div>

    <template v-else-if="goods">
      <div class="card goods">
        <div class="goods__media">
          <img class="goods__main-image" :src="heroImage" :alt="goods.name" @error="onHeroError" />
          <div v-if="goods.images.length > 1" class="goods__gallery">
            <button
              v-for="image in goods.images"
              :key="image.url"
              type="button"
              class="goods__thumb-btn"
              :class="{ 'goods__thumb-btn--active': image.url === activeImageUrl }"
              :title="image.is_main ? '主图（默认大图）' : '点击切换大图'"
              @click="selectImage(image.url)"
            >
              <img
                class="goods__thumb"
                :class="{ 'goods__thumb--main': image.is_main }"
                :src="image.url"
                :alt="image.is_main ? '主图' : '图集图片'"
                loading="lazy"
              />
            </button>
          </div>
        </div>

        <div class="goods__info">
          <div class="goods__title-row">
            <h2 class="goods__name">{{ goods.name }}</h2>
            <StatusTag :label="GOODS_STATUS_LABELS[goods.status]" />
          </div>

          <dl class="goods__meta">
            <div class="goods__meta-row">
              <dt>价格</dt>
              <dd class="goods__price">{{ formatPrice(goods.price) }}</dd>
            </div>
            <div class="goods__meta-row">
              <dt>库存</dt>
              <dd>
                <template v-if="!stockEditing">
                  {{ goods.stock }} 件
                  <button
                    type="button"
                    class="btn btn--small"
                    :disabled="isFrozen || busy"
                    :title="isFrozen ? '交易中的商品不能改库存' : undefined"
                    @click="startStockEdit"
                  >
                    修改库存
                  </button>
                </template>
                <template v-else>
                  <input
                    v-model.number="stockInput"
                    class="input goods__stock-input"
                    type="number"
                    min="0"
                    step="1"
                    :disabled="busy"
                  />
                  <button
                    type="button"
                    class="btn btn--small btn--primary"
                    :disabled="busy"
                    @click="saveStock"
                  >
                    保存
                  </button>
                  <button
                    type="button"
                    class="btn btn--small"
                    :disabled="busy"
                    @click="stockEditing = false"
                  >
                    取消
                  </button>
                </template>
              </dd>
            </div>
            <div class="goods__meta-row">
              <dt>类别</dt>
              <dd>
                <template v-if="!categoryEditing">
                  {{ goods.category.l1_name }} / {{ goods.category.l2_name }}
                  <button
                    type="button"
                    class="btn btn--small"
                    :disabled="isFrozen || busy"
                    :title="isFrozen ? '交易中的商品不能改挂类别' : undefined"
                    @click="startCategoryEdit"
                  >
                    改挂类别
                  </button>
                </template>
                <template v-else>
                  <CategoryCascadeSelect
                    v-model:l1-id="categoryL1"
                    v-model:l2-id="categoryL2"
                    :disabled="busy"
                  />
                  <button
                    type="button"
                    class="btn btn--small btn--primary"
                    :disabled="busy"
                    @click="saveCategory"
                  >
                    保存
                  </button>
                  <button
                    type="button"
                    class="btn btn--small"
                    :disabled="busy"
                    @click="categoryEditing = false"
                  >
                    取消
                  </button>
                </template>
              </dd>
            </div>
            <div class="goods__meta-row">
              <dt>创建时间</dt>
              <dd>{{ formatDateTime(goods.create_at) }}</dd>
            </div>
            <div class="goods__meta-row">
              <dt>更新时间</dt>
              <dd>{{ formatDateTime(goods.update_at) }}</dd>
            </div>
          </dl>

          <p v-if="isOffZero" class="goods__hint">{{ M10.offZeroHint }}</p>

          <div class="goods__actions">
            <button
              v-if="isOnSale"
              type="button"
              class="btn btn--danger"
              :disabled="busy"
              @click="pending = { kind: 'off-shelf' }"
            >
              下架
            </button>
            <button
              v-if="isOffManual"
              type="button"
              class="btn btn--primary"
              :disabled="busy"
              @click="pending = { kind: 're-list' }"
            >
              重新上架
            </button>
          </div>
        </div>
      </div>

      <section class="card section">
        <h2 class="section__title">商品描述</h2>
        <DescriptionBlocks v-if="goods.description.length > 0" :blocks="goods.description" />
        <p v-else class="muted">{{ M10.emptyDescription }}</p>
      </section>

      <section class="card section">
        <div class="section__header">
          <h2 class="section__title">意向名单</h2>
          <select v-model="intentionStatus" class="input section__filter" aria-label="意向状态筛选">
            <option value="">全部状态</option>
            <option
              v-for="option in INTENTION_STATUS_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </option>
          </select>
        </div>
        <IntentionTable
          :items="intentions"
          :loading="loadingIntentions"
          :can-enter-trade="isOnSale"
          :can-decide-trade="isFrozen"
          :queue-head-id="queueHeadId"
          @enter-trade="pending = { kind: 'enter-trade', item: $event }"
          @trade-success="pending = { kind: 'trade-success', item: $event }"
          @trade-failure="failureItem = $event"
        />
        <Pagination
          v-if="intentionTotal > PAGE_SIZE"
          class="section__pagination"
          :page="intentionPage"
          :page-size="PAGE_SIZE"
          :total="intentionTotal"
          :disabled="loadingIntentions"
          @update:page="onIntentionPageChange"
        />
      </section>
    </template>

    <ConfirmDialog
      :open="pending !== null"
      :message="confirmMessage"
      :danger="confirmDanger"
      :loading="busy"
      @confirm="onConfirm"
      @cancel="pending = null"
    />
    <TradeFailureDialog
      :open="failureItem !== null"
      :loading="busy"
      @submit="onFailureSubmit"
      @cancel="failureItem = null"
    />
  </div>
</template>

<style scoped>
.page__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page__title {
  margin: 0;
}

.page__error {
  margin: 0 0 16px;
  padding: 10px 12px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  background: #fef2f2;
  color: var(--danger);
}

.goods {
  display: flex;
  gap: 20px;
}

.goods__media {
  flex: 0 0 320px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.goods__main-image {
  width: 100%;
  aspect-ratio: 1 / 1;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid var(--border);
  background: #f3f4f6;
}

.goods__gallery {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.goods__thumb-btn {
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  border-radius: 4px;
  line-height: 0;
}

.goods__thumb-btn--active {
  box-shadow: 0 0 0 2px var(--primary);
  border-radius: 6px;
}

.goods__thumb {
  width: 48px;
  height: 48px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid var(--border);
}

/* .goods__thumb--main {
  border-color: var(--primary);
  border-width: 2px;
} */

.goods__info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.goods__title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.goods__name {
  margin: 0;
  font-size: 18px;
}

.goods__meta {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.goods__meta-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.goods__meta-row dt {
  flex: 0 0 72px;
  color: var(--muted);
  font-size: 13px;
}

.goods__meta-row dd {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.goods__price {
  color: var(--danger);
  font-weight: 600;
}

.goods__stock-input {
  width: 100px;
}

.goods__hint {
  margin: 0;
  font-size: 13px;
  color: var(--warning);
}

.goods__actions {
  display: flex;
  gap: 8px;
  margin-top: auto;
}

.section {
  margin-top: 16px;
}

.section__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section__title {
  margin: 0;
  font-size: 16px;
}

.section__filter {
  width: 140px;
}

.section__pagination {
  margin-top: 12px;
}
</style>
