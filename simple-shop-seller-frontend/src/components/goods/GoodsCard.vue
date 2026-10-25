<script setup lang="ts">
import { computed, ref } from 'vue'

import type { GoodsSummaryData } from '../../api/types'
import placeholderImage from '../../assets/product.svg'
import { GOODS_STATUS_LABELS, GOODS_STATUS_TONES } from '../../constants/labels'
import type { TagTone } from '../../constants/labels'
import { formatPrice } from '../../utils/format'
import StatusTag from '../common/StatusTag.vue'

/** 商品宫格卡片：只展示主图与摘要信息;详情入口在详情页步骤接入。 */
const props = defineProps<{
  goods: GoodsSummaryData
}>()

const imageFailed = ref(false)

const imageSrc = computed(() =>
  props.goods.main_image_url && !imageFailed.value ? props.goods.main_image_url : placeholderImage,
)

function onImageError(): void {
  imageFailed.value = true
}

const statusLabel = computed(() => GOODS_STATUS_LABELS[props.goods.status])
const statusTone = computed<TagTone>(() => GOODS_STATUS_TONES[props.goods.status])
</script>

<template>
  <RouterLink class="goods-card card" :to="{ name: 'goods-detail', params: { id: goods.id } }">
    <div class="goods-card__media">
      <img
        class="goods-card__image"
        :src="imageSrc"
        :alt="goods.name"
        loading="lazy"
        @error="onImageError"
      />
      <span class="goods-card__status">
        <StatusTag :label="statusLabel" :tone="statusTone" />
      </span>
    </div>
    <div class="goods-card__body">
      <p class="goods-card__name" :title="goods.name">{{ goods.name }}</p>
      <p class="goods-card__category muted">
        {{ goods.category.l1_name }} / {{ goods.category.l2_name }}
      </p>
      <div class="goods-card__footer">
        <span class="goods-card__price">{{ formatPrice(goods.price) }}</span>
        <span class="goods-card__stock muted">库存 {{ goods.stock }}</span>
      </div>
    </div>
  </RouterLink>
</template>

<style scoped>
.goods-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 0;
  color: inherit;
  text-decoration: none;
  transition: box-shadow 0.15s ease;
}

.goods-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
}

.goods-card__media {
  position: relative;
  aspect-ratio: 1 / 1;
  background: #f3f4f6;
}

.goods-card__image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.goods-card__status {
  position: absolute;
  top: 8px;
  left: 8px;
}

.goods-card__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px 12px;
}

.goods-card__name {
  margin: 0;
  font-weight: 600;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.goods-card__category {
  margin: 0;
  font-size: 12px;
}

.goods-card__footer {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 4px;
}

.goods-card__price {
  color: var(--danger);
  font-weight: 600;
}

.goods-card__stock {
  font-size: 12px;
}
</style>
