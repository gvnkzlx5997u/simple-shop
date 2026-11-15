<script setup lang="ts">
import type { GoodsSummary } from '../../api/types'
import { formatMoney } from '../../utils/format'

defineProps<{ goods: GoodsSummary }>()
</script>

<template>
  <RouterLink
    class="card goods-card cursor-pointer overflow-hidden border border-base-300 bg-base-100 transition-shadow hover:shadow-md"
    :to="{ name: 'goods-detail', params: { id: goods.id } }"
    data-testid="goods-card"
  >
    <figure class="m-0 aspect-[4/3] overflow-hidden bg-base-300">
      <img
        v-if="goods.main_image_url"
        :src="goods.main_image_url"
        :alt="goods.name"
        class="size-full object-cover"
      />
      <div v-else class="flex size-full items-center justify-center text-base-content/60">暂无图片</div>
    </figure>
    <div class="card-body gap-1 p-3">
      <div class="name font-medium">{{ goods.name }}</div>
      <div class="price font-semibold text-error">¥{{ formatMoney(goods.price) }}</div>
      <div class="muted text-xs">
        {{ goods.category?.l1_name ?? '未分类' }} / {{ goods.category?.l2_name ?? '' }}
      </div>
    </div>
  </RouterLink>
</template>