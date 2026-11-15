<script setup lang="ts">
import type { FavoriteItemData } from '../../api/types'
import { formatMoney, formatTime } from '../../utils/format'
import StatusTag from '../common/StatusTag.vue'

defineProps<{
  item: FavoriteItemData
  busy: boolean
}>()

const emit = defineEmits<{
  remove: [item: FavoriteItemData]
  unavailable: [item: FavoriteItemData]
}>()
</script>

<template>
  <div
    :class="[
      'card fav-card flex flex-col overflow-hidden border border-base-300 bg-base-100',
      { disabled: !item.detail_available, 'bg-base-200': !item.detail_available },
    ]"
    data-testid="fav-card"
  >
    <RouterLink v-if="item.detail_available" :to="'/goods/' + item.goods_id">
      <img
        v-if="item.main_image_url"
        :src="item.main_image_url"
        :alt="item.name"
        class="block aspect-[4/3] w-full bg-base-300 object-cover"
      />
      <div
        v-else
        class="flex aspect-[4/3] w-full items-center justify-center bg-base-300 text-xs text-base-content/60"
      >
        暂无图片
      </div>
    </RouterLink>
    <div v-else title="已下架，不可查看详情" @click="emit('unavailable', item)">
      <img
        v-if="item.main_image_url"
        :src="item.main_image_url"
        :alt="item.name"
        class="block aspect-[4/3] w-full cursor-not-allowed bg-base-300 object-cover opacity-55"
      />
      <div
        v-else
        class="flex aspect-[4/3] w-full cursor-not-allowed items-center justify-center bg-base-300 text-xs text-base-content/60 opacity-55"
      >
        暂无图片
      </div>
    </div>

    <div class="flex flex-col gap-1 p-3">
      <RouterLink
        v-if="item.detail_available"
        :to="'/goods/' + item.goods_id"
        class="name truncate text-base-content"
      >
        {{ item.name }}
      </RouterLink>
      <span
        v-else
        class="name cursor-not-allowed truncate text-base-content/60"
        title="已下架，不可查看详情"
        @click="emit('unavailable', item)"
      >
        {{ item.name }}
      </span>

      <div class="flex items-center gap-2">
        <span class="font-semibold text-error">¥{{ formatMoney(item.price) }}</span>
        <StatusTag v-if="!item.detail_available" label="已下架" tone="default" />
        <StatusTag v-else-if="item.stock <= 0" label="缺货" tone="warning" />
      </div>

      <div class="muted text-xs">收藏于 {{ formatTime(item.favorited_at) }}</div>

      <button
        class="btn btn-outline btn-xs remove mt-1 self-start"
        :disabled="busy"
        @click="emit('remove', item)"
      >
        取消收藏
      </button>
    </div>
  </div>
</template>