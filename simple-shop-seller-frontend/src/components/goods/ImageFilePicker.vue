<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'

import { M10 } from '../../constants/messages'
import { useToast } from '../../composables/useToast'
import { formatBytes } from '../../utils/format'
import { checkGoodsImageFile, createPickedImage } from './descriptionEditor'
import type { PickedImage } from './descriptionEditor'

/**
 * 商品图片选择器(主图/图集)。编辑期只保留本地文件与缩略预览,
 * 统一在发布时上传并回填 url(PickedImage.url 缓存,重试不重传)。
 */
const props = withDefaults(
  defineProps<{
    label: string
    /** 最多可选张数;主图传 1(重选即替换),图集传 8。 */
    max: number
    hint?: string
    disabled?: boolean
  }>(),
  { hint: undefined, disabled: false },
)

const images = defineModel<PickedImage[]>({ required: true })

const toast = useToast()

const fileInput = ref<HTMLInputElement | null>(null)

// 本地缩略预览的 objectURL,按条目 id 管理;移除与卸载时释放。
const previews = new Map<number, string>()

function previewUrl(item: PickedImage): string {
  let url = previews.get(item.id)
  if (!url) {
    url = URL.createObjectURL(item.file)
    previews.set(item.id, url)
  }
  return url
}

function revoke(id: number): void {
  const url = previews.get(id)
  if (url) {
    URL.revokeObjectURL(url)
    previews.delete(id)
  }
}

onBeforeUnmount(() => {
  for (const url of previews.values()) {
    URL.revokeObjectURL(url)
  }
  previews.clear()
})

function pick(): void {
  fileInput.value?.click()
}

function onFilesPicked(event: Event): void {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''

  const accepted: PickedImage[] = []
  for (const file of files) {
    const check = checkGoodsImageFile(file)
    if (!check.ok) {
      toast.error(check.message)
      continue
    }
    accepted.push(createPickedImage(file))
  }
  if (accepted.length === 0) {
    return
  }

  if (props.max === 1) {
    for (const item of images.value) {
      revoke(item.id)
    }
    images.value = accepted.slice(0, 1)
    return
  }

  const room = props.max - images.value.length
  if (room <= 0) {
    toast.error(M10.imageCountExceeded)
    return
  }
  images.value.push(...accepted.slice(0, room))
  if (accepted.length > room) {
    toast.error(M10.imageCountExceeded)
  }
}

function remove(id: number): void {
  const index = images.value.findIndex((item) => item.id === id)
  if (index === -1) {
    return
  }
  revoke(id)
  images.value.splice(index, 1)
}
</script>

<template>
  <div class="image-picker">
    <div class="image-picker__header">
      <span class="image-picker__label">{{ label }}</span>
      <button type="button" class="btn btn--small" :disabled="disabled" @click="pick">
        选择图片
      </button>
      <span v-if="hint" class="image-picker__hint muted">{{ hint }}</span>
    </div>

    <p v-if="images.length === 0" class="image-picker__empty muted">未选择图片</p>
    <ul v-else class="image-picker__list">
      <li v-for="item in images" :key="item.id" class="image-picker__item">
        <img class="image-picker__thumb" :src="previewUrl(item)" :alt="item.file.name" />
        <div class="image-picker__meta">
          <span class="image-picker__name" :title="item.file.name">{{ item.file.name }}</span>
          <span class="image-picker__size"
            >{{ formatBytes(item.file.size) }} · {{ item.url ? '已上传' : '发布时上传' }}</span
          >
        </div>
        <button type="button" class="btn btn--small" :disabled="disabled" @click="remove(item.id)">
          移除
        </button>
      </li>
    </ul>

    <input
      ref="fileInput"
      type="file"
      accept=".jpg,.jpeg,.png,image/jpeg,image/png"
      :multiple="max > 1"
      hidden
      @change="onFilesPicked"
    />
  </div>
</template>

<style scoped>
.image-picker__header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.image-picker__label {
  font-weight: 600;
}

.image-picker__hint {
  font-size: 12px;
}

.image-picker__empty {
  margin: 8px 0 0;
  font-size: 13px;
}

.image-picker__list {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.image-picker__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: #f9fafb;
}

.image-picker__thumb {
  flex: 0 0 auto;
  width: 48px;
  height: 48px;
  object-fit: cover;
  border-radius: 4px;
  background: #fff;
}

.image-picker__meta {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.image-picker__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.image-picker__size {
  font-size: 12px;
  color: var(--muted);
}
</style>
