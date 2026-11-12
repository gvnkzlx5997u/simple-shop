<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { getGoods, takeGoodsOffShelf, updateGoodsCategory, updateGoodsStock } from '../api/goods'
import type { GoodsDetailData } from '../api/types'
import placeholderImage from '../assets/product.svg'
import AppIcon from '../components/common/AppIcon.vue'
import ConfirmDialog from '../components/common/ConfirmDialog.vue'
import StatusTag from '../components/common/StatusTag.vue'
import CategoryCascadeSelect from '../components/goods/CategoryCascadeSelect.vue'
import DescriptionBlocks from '../components/goods/DescriptionBlocks.vue'
import OrderList from '../components/order/OrderList.vue'
import {
  GOODS_STATUS_LABELS,
  GOODS_STATUS_TONES,
  OFF_SHELF_REASON_LABELS,
  OFF_SHELF_REASON_TONES,
} from '../constants/labels'
import type { TagTone } from '../constants/labels'
import { M10 } from '../constants/messages'
import { useToast } from '../composables/useToast'
import { errorMessage } from '../utils/error'
import { formatDateTime, formatPrice } from '../utils/format'
import { validateAdjustStock } from '../validation'

const route = useRoute()
const toast = useToast()

const goods = ref<GoodsDetailData | null>(null)
const loadingGoods = ref(false)
const error = ref<string | null>(null)

const goodsId = computed(() => (typeof route.params.id === 'string' ? route.params.id : ''))

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

onMounted(loadGoods)
watch(goodsId, loadGoods)

const isOnSale = computed(() => goods.value?.status === 'ON_SALE')
/** 库存耗尽 / 已售下架的商品补货即自动上架；手动下架是终态。 */
const canRestock = computed(
  () => goods.value?.status === 'OFF_SHELF' && goods.value.off_shelf_reason !== 'MANUAL',
)

const statusTone = computed<TagTone>(() =>
  goods.value ? GOODS_STATUS_TONES[goods.value.status] : 'default',
)

/** 状态与原因成对展示：在售时没有原因。 */
const statusBadges = computed(() => {
  const current = goods.value
  if (!current) {
    return [] as { label: string; tone: TagTone }[]
  }
  const badges = [{ label: GOODS_STATUS_LABELS[current.status], tone: statusTone.value }]
  if (current.off_shelf_reason) {
    badges.push({
      label: OFF_SHELF_REASON_LABELS[current.off_shelf_reason],
      tone: OFF_SHELF_REASON_TONES[current.off_shelf_reason],
    })
  }
  return badges
})

/** 手动下架是终态，库存只读；在售与可补货的已下架都能改。 */
const stockEditable = computed(() => isOnSale.value || canRestock.value)

// ---- 商品操作：手动下架（终态，没有重新上架） ----

const offShelfPending = ref(false)

const busy = ref(false)

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

async function onConfirmOffShelf(): Promise<void> {
  busy.value = true
  try {
    await takeGoodsOffShelf(goodsId.value)
    toast.success(M10.offShelfDone)
    await loadGoods()
  } catch (caught) {
    toast.error(errorMessage(caught))
    void loadGoods()
  } finally {
    offShelfPending.value = false
    busy.value = false
  }
}

// ---- 库存行内编辑（库存耗尽下架的商品改成 >0 保存即自动上架） ----

const stockEditing = ref(false)
const stockInput = ref<number | ''>(0)

/** 在售商品把库存改成 0 保存后会立刻下架，保存前先说清后果。 */
const willOffShelf = computed(
  () => stockEditing.value && isOnSale.value && Number(stockInput.value) === 0,
)

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

// ---- 改挂类别行内编辑（所有状态都可改） ----

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
</script>

<template>
  <div class="flex flex-col gap-4">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <h1 class="text-lg font-semibold">商品详情</h1>
      <RouterLink to="/" class="btn btn-sm btn-outline">
        <AppIcon name="arrow-left" />
        返回列表
      </RouterLink>
    </header>

    <div v-if="error" role="alert" class="alert alert-error alert-soft text-sm">
      <AppIcon name="forbidden" />
      <span>{{ error }}</span>
    </div>
    <div v-else-if="loadingGoods && !goods" class="panel text-sm text-base-content/60">
      加载中…
    </div>

    <template v-else-if="goods">
      <div class="panel flex flex-col gap-6 lg:flex-row">
        <div class="flex shrink-0 flex-col gap-2 lg:w-[320px]">
          <img
            class="aspect-square w-full rounded-box border border-base-300 bg-base-300 object-cover"
            :src="heroImage"
            :alt="goods.name"
            @error="onHeroError"
          />
          <div v-if="goods.images.length > 1" class="flex flex-wrap gap-1.5">
            <button
              v-for="image in goods.images"
              :key="image.url"
              type="button"
              class="cursor-pointer rounded-md p-0 leading-none"
              :class="image.url === activeImageUrl ? 'ring-2 ring-primary' : ''"
              :title="image.is_main ? '主图（默认大图）' : '点击切换大图'"
              @click="selectImage(image.url)"
            >
              <img
                class="size-12 rounded-md border border-base-300 object-cover"
                :src="image.url"
                :alt="image.is_main ? '主图' : '图集图片'"
                loading="lazy"
              />
            </button>
          </div>
        </div>

        <div class="flex min-w-0 flex-1 flex-col gap-3">
          <div class="flex flex-wrap items-center gap-2.5">
            <h2 class="text-lg font-semibold">{{ goods.name }}</h2>
            <StatusTag
              v-for="badge in statusBadges"
              :key="badge.label"
              :label="badge.label"
              :tone="badge.tone"
            />
          </div>

          <dl class="flex flex-col gap-2.5 text-sm">
            <div class="flex flex-wrap items-center gap-3">
              <dt class="w-20 shrink-0 text-xs text-base-content/60">价格</dt>
              <dd class="m-0 flex flex-wrap items-center gap-2 font-semibold text-error">
                {{ formatPrice(goods.price) }}
              </dd>
            </div>

            <div class="flex flex-wrap items-center gap-3">
              <dt class="w-20 shrink-0 text-xs text-base-content/60">库存</dt>
              <dd class="m-0 flex flex-wrap items-center gap-2">
                <template v-if="!stockEditing">
                  <span>{{ goods.stock }} 件</span>
                  <button
                    v-if="stockEditable"
                    type="button"
                    class="btn btn-sm btn-outline"
                    :disabled="busy"
                    @click="startStockEdit"
                  >
                    <AppIcon name="edit" />
                    修改库存
                  </button>
                </template>
                <template v-else>
                  <input
                    v-model.number="stockInput"
                    class="input input-sm goods__stock-input w-24"
                    type="number"
                    min="0"
                    step="1"
                    :disabled="busy"
                  />
                  <button
                    type="button"
                    class="btn btn-sm btn-primary"
                    :disabled="busy"
                    @click="saveStock"
                  >
                    保存
                  </button>
                  <button
                    type="button"
                    class="btn btn-sm btn-ghost"
                    :disabled="busy"
                    @click="stockEditing = false"
                  >
                    取消
                  </button>
                </template>
                <span v-if="willOffShelf" class="text-xs text-warning">
                  {{ M10.stockZeroHint }}
                </span>
              </dd>
            </div>

            <div class="flex flex-wrap items-center gap-3">
              <dt class="w-20 shrink-0 text-xs text-base-content/60">类别</dt>
              <dd class="m-0 flex flex-wrap items-center gap-2">
                <template v-if="!categoryEditing">
                  <span>{{ goods.category.l1_name }} / {{ goods.category.l2_name }}</span>
                  <button
                    type="button"
                    class="btn btn-sm btn-outline"
                    :disabled="busy"
                    @click="startCategoryEdit"
                  >
                    <AppIcon name="file" />
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
                    class="btn btn-sm btn-primary"
                    :disabled="busy"
                    @click="saveCategory"
                  >
                    保存
                  </button>
                  <button
                    type="button"
                    class="btn btn-sm btn-ghost"
                    :disabled="busy"
                    @click="categoryEditing = false"
                  >
                    取消
                  </button>
                </template>
              </dd>
            </div>

            <div v-if="goods.off_shelf_at" class="flex flex-wrap items-center gap-3">
              <dt class="w-20 shrink-0 text-xs text-base-content/60">下架时间</dt>
              <dd class="m-0">{{ formatDateTime(goods.off_shelf_at) }}</dd>
            </div>

            <div class="flex flex-wrap items-center gap-3">
              <dt class="w-20 shrink-0 text-xs text-base-content/60">创建时间</dt>
              <dd class="m-0">{{ formatDateTime(goods.create_at) }}</dd>
            </div>

            <div class="flex flex-wrap items-center gap-3">
              <dt class="w-20 shrink-0 text-xs text-base-content/60">更新时间</dt>
              <dd class="m-0">{{ formatDateTime(goods.update_at) }}</dd>
            </div>
          </dl>

          <p v-if="canRestock" class="text-xs text-warning">{{ M10.restockHint }}</p>
          <p v-if="!stockEditable" class="text-xs text-base-content/60">
            {{ M10.manualOffShelfHint }}
          </p>

          <div class="mt-auto flex flex-wrap gap-2 pt-2">
            <button
              v-if="isOnSale"
              type="button"
              class="btn btn-sm btn-error"
              :disabled="busy"
              @click="offShelfPending = true"
            >
              <AppIcon name="forbidden" />
              下架
            </button>
          </div>
        </div>
      </div>

      <section class="panel">
        <h2 class="mb-3 text-base font-semibold">商品描述</h2>
        <DescriptionBlocks v-if="goods.description.length > 0" :blocks="goods.description" />
        <p v-else class="text-sm text-base-content/60">{{ M10.emptyDescription }}</p>
      </section>

      <!-- 该商品的订单：内嵌同一份订单列表，按 goods_id 固定筛选，不跳转到订单管理页 -->
      <section class="flex flex-col gap-3">
        <div class="flex flex-wrap items-baseline gap-2">
          <h2 class="text-base font-semibold">该商品的订单</h2>
          <span class="text-xs text-base-content/60">点任意一行进入订单详情</span>
        </div>
        <OrderList
          :goods-id="goodsId"
          :page-size="5"
          :sync-query="false"
          empty-text="该商品还没有被下单"
        />
      </section>
    </template>

    <ConfirmDialog
      :open="offShelfPending"
      :message="M10.confirmOffShelf"
      danger
      :loading="busy"
      @confirm="onConfirmOffShelf"
      @cancel="offShelfPending = false"
    />
  </div>
</template>
