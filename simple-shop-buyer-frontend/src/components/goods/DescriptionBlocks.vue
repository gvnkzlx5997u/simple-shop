<script setup lang="ts">
import type { GoodsDetail } from '../../api/types'

defineProps<{ blocks: NonNullable<GoodsDetail['description']> }>()
</script>

<template>
  <div class="description space-y-3" data-testid="goods-description">
    <template v-for="(block, idx) in blocks" :key="idx">
      <p v-if="block.type === 'TEXT' && block.text">{{ block.text }}</p>
      <img
        v-else-if="block.type === 'IMAGE' && block.url"
        :src="block.url"
        class="max-w-full rounded-field"
      />
      <video
        v-else-if="block.type === 'VIDEO' && block.url"
        :src="block.url"
        controls
        class="max-w-full rounded-field"
      />
    </template>
  </div>
</template>