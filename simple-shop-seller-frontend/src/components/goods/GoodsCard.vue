<script setup lang="ts">
import { computed, ref } from 'vue'

import type { GoodsSummaryData } from '../../api/types'
import placeholderImage from '../../assets/product.svg'
import {
  GOODS_STATUS_LABELS,
  GOODS_STATUS_TONES,
  OFF_SHELF_REASON_LABELS,
  OFF_SHELF_REASON_TONES,
} from '../../constants/labels'
import type { TagTone } from '../../constants/labels'
import { formatPrice } from '../../utils/format'
import StatusTag from '../common/StatusTag.vue'

/** 商品宫格卡片：主图 + 摘要；整卡可点，进入详情。 */
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

/** 下架原因与状态成对出现；在售时 reason 为 null，不渲染。 */
const reasonLabel = computed(() =>
  props.goods.off_shelf_reason ? OFF_SHELF_REASON_LABELS[props.goods.off_shelf_reason] : '',
)
const reasonTone = computed<TagTone>(() =>
  props.goods.off_shelf_reason ? OFF_SHELF_REASON_TONES[props.goods.off_shelf_reason] : 'default',
)
</script>

<template>
  <RouterLink
    class="card border border-base-300 bg-base-100 transition-shadow hover:shadow-md"
    :to="{ name: 'goods-detail', params: { id: goods.id } }"
  >
    <figure class="relative m-0 aspect-square overflow-hidden bg-base-300">
      <img
        class="size-full object-cover"
        :src="imageSrc"
        :alt="goods.name"
        loading="lazy"
        @error="onImageError"
      />
      <span class="absolute top-2 left-2 flex flex-wrap items-center gap-1">
        <StatusTag :label="statusLabel" :tone="statusTone" />
        <StatusTag v-if="reasonLabel" :label="reasonLabel" :tone="reasonTone" />
      </span>
    </figure>
    <div class="card-body gap-1 p-3">
      <p class="line-clamp-2 text-sm leading-snug font-semibold" :title="goods.name">
        {{ goods.name }}
      </p>
      <p class="truncate text-xs text-base-content/60">
        {{ goods.category.l1_name }} / {{ goods.category.l2_name }}
      </p>
      <div class="mt-auto flex items-baseline justify-between gap-2 pt-1">
        <span class="font-semibold text-error">{{ formatPrice(goods.price) }}</span>
        <span class="text-xs text-base-content/60">库存 {{ goods.stock }}</span>
      </div>
    </div>
  </RouterLink>
</template>
