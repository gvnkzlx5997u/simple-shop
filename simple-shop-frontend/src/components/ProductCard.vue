<script setup lang="ts">
import { computed, ref } from 'vue'

import type { FreezeBy, GoodsResult, GoodsStatus } from '../api/types'
import productPlaceholder from '../assets/product.svg'
import {
  FREEZE_BY_LABELS,
  GOODS_RESULT_LABELS,
  GOODS_STATUS_LABELS,
  type TagTone,
} from '../constants/labels'
import { M10 } from '../constants/messages'
import { formatDateTime, formatPrice } from '../utils/format'
import StatusTag from './StatusTag.vue'

const props = withDefaults(
  defineProps<{
    goods: {
      name: string
      description: string | null
      pic_url: string | null
      price: string
      status: GoodsStatus
      create_at: string
      freeze_by?: FreezeBy | null
      trade_end?: string | null
      result?: GoodsResult | null
    }
    mode?: 'manage' | 'readonly'
    queueCount?: number
  }>(),
  { mode: 'manage', queueCount: 0 },
)

const emit = defineEmits<{
  freeze: []
  unfreeze: []
  offline: []
}>()

const imageFailed = ref(false)

const isPlaceholder = computed(() => !props.goods.pic_url || imageFailed.value)
const imageSrc = computed(() =>
  isPlaceholder.value ? productPlaceholder : (props.goods.pic_url ?? productPlaceholder),
)

const statusLabel = computed(() => GOODS_STATUS_LABELS[props.goods.status])
const statusTone = computed<TagTone>(() => {
  if (props.goods.status === 'on_sale') {
    return 'success'
  }
  if (props.goods.status === 'frozen') {
    return 'warning'
  }
  return 'default'
})
const freezeByLabel = computed(() =>
  props.goods.freeze_by ? FREEZE_BY_LABELS[props.goods.freeze_by] : null,
)
const resultLabel = computed(() =>
  props.goods.result ? GOODS_RESULT_LABELS[props.goods.result] : '—',
)

const canFreeze = computed(() => props.mode === 'manage' && props.goods.status === 'on_sale')
const canUnfreeze = computed(
  () =>
    props.mode === 'manage' &&
    props.goods.status === 'frozen' &&
    props.goods.freeze_by === 'manual',
)
const unfreezeBlockedByTrade = computed(
  () =>
    props.mode === 'manage' && props.goods.status === 'frozen' && props.goods.freeze_by === 'trade',
)
const canOffline = computed(
  () => props.mode === 'manage' && props.goods.status === 'on_sale' && props.queueCount === 0,
)
const offlineBlockedByQueue = computed(
  () => props.mode === 'manage' && props.goods.status === 'on_sale' && props.queueCount > 0,
)
</script>

<template>
  <section class="product-card card">
    <div class="product-card__media">
      <img
        :src="imageSrc"
        :class="{ 'product-card__image--placeholder': isPlaceholder }"
        alt="商品图片"
        @error="imageFailed = true"
      />
    </div>

    <div class="product-card__body">
      <div class="product-card__header">
        <h2 class="product-card__name">{{ goods.name }}</h2>
        <StatusTag :label="statusLabel" :tone="statusTone" />
        <StatusTag v-if="freezeByLabel" :label="freezeByLabel" tone="info" />
      </div>

      <p class="product-card__price">{{ formatPrice(goods.price) }}</p>
      <p class="product-card__desc">{{ goods.description || '（无描述）' }}</p>

      <dl class="product-card__meta">
        <div>
          <dt>发布时间</dt>
          <dd>{{ formatDateTime(goods.create_at) }}</dd>
        </div>
        <div v-if="mode === 'readonly'">
          <dt>交易结束时间</dt>
          <dd>{{ formatDateTime(goods.trade_end) }}</dd>
        </div>
        <div v-if="mode === 'readonly'">
          <dt>交易结果</dt>
          <dd>{{ resultLabel }}</dd>
        </div>
      </dl>

      <div v-if="mode === 'manage'" class="product-card__actions">
        <button type="button" class="btn" :disabled="!canFreeze" @click="emit('freeze')">
          手动冻结
        </button>
        <button
          type="button"
          class="btn"
          :disabled="!canUnfreeze"
          :title="unfreezeBlockedByTrade ? M10.frozenByTrade : undefined"
          @click="emit('unfreeze')"
        >
          手动解冻
        </button>
        <button
          type="button"
          class="btn btn--danger"
          :disabled="!canOffline"
          :title="offlineBlockedByQueue ? M10.queueNotEmpty : undefined"
          @click="emit('offline')"
        >
          手动下架
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.product-card {
  display: flex;
  gap: 18px;
}

.product-card__media {
  flex: 0 0 160px;
  height: 160px;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: #f3f4f6;
}

.product-card__media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.product-card__media img.product-card__image--placeholder {
  object-fit: contain;
  padding: 24px;
}

.product-card__body {
  flex: 1;
  min-width: 0;
}

.product-card__header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.product-card__name {
  margin: 0;
  font-size: 18px;
}

.product-card__price {
  margin: 8px 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--danger);
}

.product-card__desc {
  margin: 0 0 12px;
  color: var(--text);
  white-space: pre-wrap;
}

.product-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  margin: 0 0 14px;
}

.product-card__meta div {
  display: flex;
  gap: 6px;
}

.product-card__meta dt {
  color: var(--muted);
}

.product-card__meta dd {
  margin: 0;
}

.product-card__actions {
  display: flex;
  gap: 8px;
}
</style>
