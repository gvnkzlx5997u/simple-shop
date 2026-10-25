<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'

import { M10 } from '../../constants/messages'
import { useToast } from '../../composables/useToast'
import { formatBytes } from '../../utils/format'
import { DESCRIPTION_TOTAL_MAX_CHARS } from '../../validation/goods'
import {
  checkMediaFile,
  countDescriptionText,
  createMediaLine,
  createTextLine,
} from './descriptionEditor'
import type { DescriptionEditorLine } from './descriptionEditor'

/**
 * 描述块编辑器：视觉上是一个连续编辑区，内部是行序列（文本行 + 原子媒体行）。
 *
 * - 媒体在编辑期不上传、不预览，显示为「文件名行」，发布编排统一上传并回填 url；
 * - 插入发生在当前光标处（把聚焦的文本行按光标切分为两段）；
 * - 媒体行可聚焦，Backspace／Delete 或右侧 × 删除；
 * - 组件不做提交校验（尖括号、累计长度超限由发布页拦截），只在角落展示累计字数。
 */
const lines = defineModel<DescriptionEditorLine[]>({ required: true })

const toast = useToast()

const imageInput = ref<HTMLInputElement | null>(null)
const videoInput = ref<HTMLInputElement | null>(null)

const textareas = new Map<number, HTMLTextAreaElement>()
let focusedTextId: number | null = null

const textCharCount = computed(() => countDescriptionText(lines.value))
const overLimit = computed(() => textCharCount.value > DESCRIPTION_TOTAL_MAX_CHARS)

function bindTextarea(id: number, el: unknown): void {
  if (el instanceof HTMLTextAreaElement) {
    textareas.set(id, el)
    void nextTick(() => autosize(el))
  } else {
    textareas.delete(id)
    if (focusedTextId === id) {
      focusedTextId = null
    }
  }
}

function autosize(el: HTMLTextAreaElement): void {
  el.style.height = 'auto'
  el.style.height = el.scrollHeight + 'px'
}

function onTextInput(event: Event): void {
  autosize(event.target as HTMLTextAreaElement)
}

function pickFiles(kind: 'IMAGE' | 'VIDEO'): void {
  // mousedown.prevent 保住文本行焦点与光标，插入位置才正确。
  if (kind === 'IMAGE') {
    imageInput.value?.click()
  } else {
    videoInput.value?.click()
  }
}

function onFilesPicked(event: Event): void {
  const input = event.target as HTMLInputElement
  insertFiles(input.files)
  input.value = ''
}

function insertFiles(fileList: FileList | null): void {
  if (!fileList || fileList.length === 0) {
    return
  }
  const accepted = []
  for (const file of Array.from(fileList)) {
    const check = checkMediaFile(file)
    if (!check.ok) {
      toast.error(check.message)
      continue
    }
    accepted.push(createMediaLine(file, check.mediaType))
  }
  if (accepted.length === 0) {
    return
  }
  insertAtCaret(accepted)
}

function insertAtCaret(media: ReturnType<typeof createMediaLine>[]): void {
  const index = lines.value.findIndex((line) => line.id === focusedTextId)
  if (index === -1) {
    // 没有聚焦的文本行：追加到末尾，并保证末尾留一个可继续输入的文本行。
    const tail = createTextLine()
    lines.value.push(...media, tail)
    focusText(tail.id, 'start')
    return
  }
  const target = lines.value[index]
  if (target === undefined || target.kind !== 'text') {
    return
  }
  const el = textareas.get(target.id)
  const caret = el ? (el.selectionStart ?? target.content.length) : target.content.length
  const before = createTextLine(target.content.slice(0, caret))
  const after = createTextLine(target.content.slice(caret))
  lines.value.splice(index, 1, before, ...media, after)
  focusText(after.id, 'start')
}

function removeMedia(id: number): void {
  const index = lines.value.findIndex((line) => line.id === id)
  if (index === -1) {
    return
  }
  lines.value.splice(index, 1)
  if (lines.value.length === 0) {
    const tail = createTextLine()
    lines.value.push(tail)
    focusText(tail.id, 'end')
    return
  }
  // 焦点落到前一个文本行末尾，保持连续输入的手感。
  for (let i = index - 1; i >= 0; i -= 1) {
    const candidate = lines.value[i]
    if (candidate !== undefined && candidate.kind === 'text') {
      focusText(candidate.id, 'end')
      break
    }
  }
}

function focusText(id: number, position: 'start' | 'end'): void {
  focusedTextId = id
  void nextTick(() => {
    const el = textareas.get(id)
    if (!el) {
      return
    }
    el.focus()
    const caret = position === 'start' ? 0 : el.value.length
    el.setSelectionRange(caret, caret)
  })
}

function mediaTypeLabel(mediaType: 'IMAGE' | 'VIDEO'): string {
  return mediaType === 'VIDEO' ? '视频' : '图片'
}
</script>

<template>
  <div class="desc-editor" data-testid="description-editor">
    <div class="desc-editor__toolbar">
      <button type="button" class="btn btn--small" @mousedown.prevent @click="pickFiles('IMAGE')">
        在光标处插入图片
      </button>
      <button type="button" class="btn btn--small" @mousedown.prevent @click="pickFiles('VIDEO')">
        在光标处插入视频
      </button>
      <span class="desc-editor__counter" :class="{ 'desc-editor__counter--over': overLimit }">
        {{ textCharCount }}/{{ DESCRIPTION_TOTAL_MAX_CHARS }}
      </span>
    </div>

    <div class="desc-editor__area">
      <template v-for="(line, index) in lines" :key="line.id">
        <textarea
          v-if="line.kind === 'text'"
          :ref="(el) => bindTextarea(line.id, el)"
          v-model="line.content"
          class="desc-editor__text"
          rows="1"
          :placeholder="
            index === 0 && lines.length === 1
              ? '填写商品描述，可用上方按钮在光标处插入图片／视频…'
              : undefined
          "
          @focus="focusedTextId = line.id"
          @input="onTextInput"
        ></textarea>
        <div
          v-else
          class="desc-editor__media"
          :title="'选中后可按 Backspace 删除'"
          tabindex="0"
          @keydown.backspace.prevent="removeMedia(line.id)"
          @keydown.delete.prevent="removeMedia(line.id)"
        >
          <span
            class="desc-editor__media-type"
            :class="'desc-editor__media-type--' + line.mediaType"
          >
            {{ mediaTypeLabel(line.mediaType) }}
          </span>
          <span class="desc-editor__media-name" :title="line.file.name">{{ line.file.name }}</span>
          <span class="desc-editor__media-size">{{ formatBytes(line.file.size) }}</span>
          <span class="desc-editor__media-status muted">
            {{ line.url ? '已上传' : '发布时上传' }}
          </span>
          <button
            type="button"
            class="btn btn--small desc-editor__remove"
            @click="removeMedia(line.id)"
          >
            删除
          </button>
        </div>
      </template>
    </div>

    <input
      ref="imageInput"
      type="file"
      accept=".jpg,.jpeg,.png,image/jpeg,image/png"
      multiple
      hidden
      @change="onFilesPicked"
    />
    <input
      ref="videoInput"
      type="file"
      accept=".mp4,video/mp4"
      multiple
      hidden
      @change="onFilesPicked"
    />
  </div>
</template>

<style scoped>
.desc-editor {
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--surface);
}

.desc-editor__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--border);
}

.desc-editor__counter {
  margin-left: auto;
  font-size: 12px;
  color: var(--muted);
}

.desc-editor__counter--over {
  color: var(--danger);
  font-weight: 600;
}

.desc-editor__area {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  min-height: 120px;
}

/* 文本行无边框无缝堆叠，视觉上就是一个编辑区。 */
.desc-editor__text {
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  padding: 0;
  font: inherit;
  color: inherit;
  line-height: 1.6;
  overflow: hidden;
}

.desc-editor__media {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px;
  border: 1px dashed var(--border);
  border-radius: 6px;
  background: #f9fafb;
}

.desc-editor__media:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 1px;
}

.desc-editor__media-type {
  flex: 0 0 auto;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 12px;
  border: 1px solid var(--border);
  background: #eff6ff;
  color: var(--primary);
}

.desc-editor__media-type--VIDEO {
  background: #f5f3ff;
  color: #6d28d9;
}

.desc-editor__media-name {
  flex: 0 1 auto;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.desc-editor__media-size {
  flex: 0 0 auto;
  font-size: 12px;
  color: var(--muted);
}

.desc-editor__media-status {
  flex: 0 0 auto;
  font-size: 12px;
}

.desc-editor__remove {
  margin-left: auto;
  flex: 0 0 auto;
}
</style>
