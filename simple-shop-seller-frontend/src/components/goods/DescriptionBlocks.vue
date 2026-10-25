<script setup lang="ts">
import type { ContentBlockType } from '../../api/types'

/** 结构化描述块(商品详情与历史详情同构):TEXT 段落、IMAGE 图片、VIDEO 视频,按序渲染。 */
defineProps<{
  blocks: Array<{ type: ContentBlockType; text: string | null; url: string | null }>
}>()
</script>

<template>
  <div class="description-blocks">
    <template v-for="(block, index) in blocks" :key="index">
      <p v-if="block.type === 'TEXT'" class="description-blocks__text">{{ block.text }}</p>
      <img
        v-else-if="block.type === 'IMAGE'"
        class="description-blocks__image"
        :src="block.url ?? ''"
        :alt="`描述图片 ${index + 1}`"
        loading="lazy"
      />
      <video
        v-else
        class="description-blocks__video"
        :src="block.url ?? ''"
        controls
        preload="metadata"
      ></video>
    </template>
  </div>
</template>

<style scoped>
.description-blocks {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.description-blocks__text {
  margin: 0;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.description-blocks__image {
  max-width: 75%;
  margin: auto;
  border-radius: 6px;
  border: 1px solid var(--border);
}

.description-blocks__video {
  max-width: 75%;
  margin: auto;
  border-radius: 6px;
  background: #000;
}
</style>
