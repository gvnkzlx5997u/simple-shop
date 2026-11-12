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

/**
 * 媒体在视觉上是「整块」的，用户自然会用 Backspace／Delete 删它。
 * 光标在文本行首按 Backspace、行尾按 Delete 时，直接删掉相邻的媒体行，
 * 不再要求先把焦点移到媒体块上。
 */
/**
 * 媒体在视觉上是「整块」的，用户自然会用 Backspace／Delete 删它；
 * 文本行之间也要能像普通文本那样前后相连（行首 Backspace / 行尾 Delete 合并两行），
 * 否则删完媒体会留下两个互不相通的文本框。
 */
function onTextKeydown(event: KeyboardEvent, line: DescriptionEditorLine, index: number): void {
  if (line.kind !== 'text') {
    return
  }
  const el = event.target as HTMLTextAreaElement
  if (el.selectionStart !== el.selectionEnd) {
    return
  }
  const atStart = el.selectionStart === 0
  const atEnd = el.selectionStart === el.value.length

  if (event.key === 'Backspace' && atStart && index > 0) {
    const previous = lines.value[index - 1]
    if (!previous) {
      return
    }
    event.preventDefault()
    if (previous.kind !== 'text') {
      removeMedia(previous.id)
      return
    }
    // 上一行也是文本：把当前行并进去。空行因此能被 Backspace 删掉。
    const caret = previous.content.length
    previous.content = previous.content + line.content
    lines.value.splice(index, 1)
    focusText(previous.id, caret)
    return
  }

  if (event.key === 'Delete' && atEnd && index < lines.value.length - 1) {
    const next = lines.value[index + 1]
    if (!next) {
      return
    }
    event.preventDefault()
    if (next.kind !== 'text') {
      removeMedia(next.id)
      return
    }
    const caret = line.content.length
    line.content = line.content + next.content
    lines.value.splice(index + 1, 1)
    focusText(line.id, caret)
  }
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

/**
 * 去掉媒体后，若它左右都是文本行就并成一行。
 * 不合并的话会留下两个互不相通的文本框：方向键跨不过去，空行也删不掉。
 * 返回合并后的行 id 与接缝处光标位置。
 */
function mergeNeighbours(index: number): { id: number; caret: number } | null {
  const previous = lines.value[index - 1]
  const next = lines.value[index]
  if (!previous || !next || previous.kind !== 'text' || next.kind !== 'text') {
    return null
  }
  const caret = previous.content.length
  previous.content = previous.content + next.content
  lines.value.splice(index, 1)
  return { id: previous.id, caret }
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
    focusText(tail.id, 'start')
    return
  }

  const merged = mergeNeighbours(index)
  if (merged) {
    focusText(merged.id, merged.caret)
    return
  }

  // 两侧不都是文本：焦点落到后面最近的文本行，没有就落到前面的。
  const nextText = lines.value.slice(index).find((line) => line.kind === 'text')
  if (nextText) {
    focusText(nextText.id, 'start')
    return
  }
  const previousText = lines.value
    .slice(0, index)
    .reverse()
    .find((line) => line.kind === 'text')
  if (previousText) {
    focusText(previousText.id, 'end')
  }
}

/** position 支持具体偏移量：合并行后光标要落在两段文本的接缝处。 */
function focusText(id: number, position: 'start' | 'end' | number): void {
  focusedTextId = id
  void nextTick(() => {
    const el = textareas.get(id)
    if (!el) {
      return
    }
    el.focus()
    const caret =
      position === 'start'
        ? 0
        : position === 'end'
          ? el.value.length
          : Math.min(position, el.value.length)
    el.setSelectionRange(caret, caret)
    autosize(el)
  })
}

function mediaTypeLabel(mediaType: 'IMAGE' | 'VIDEO'): string {
  return mediaType === 'VIDEO' ? '视频' : '图片'
}
</script>

<template>
  <div class="desc-editor" data-testid="description-editor">
    <div class="desc-editor__toolbar">
      <button type="button" class="btn btn-sm btn-outline" @mousedown.prevent @click="pickFiles('IMAGE')">
        在光标处插入图片
      </button>
      <button type="button" class="btn btn-sm btn-outline" @mousedown.prevent @click="pickFiles('VIDEO')">
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
          @keydown="onTextKeydown($event, line, index)"
        ></textarea>
        <div
          v-else
          class="desc-editor__media"
          :title="'在相邻文本行的行首按 Backspace、行尾按 Delete，或选中本行后按 Backspace 删除'"
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
            class="btn btn-sm desc-editor__remove"
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
  border: 1px solid var(--color-base-300);
  border-radius: var(--radius-box);
  background: var(--color-base-100);
}

.desc-editor__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--color-base-300);
}

.desc-editor__counter {
  margin-left: auto;
  font-size: 12px;
  color: color-mix(in oklab, var(--color-base-content) 60%, transparent);
}

.desc-editor__counter--over {
  color: var(--color-error);
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
  border: 1px dashed var(--color-base-300);
  border-radius: var(--radius-box);
  background: var(--color-base-200);
}

.desc-editor__media:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}

.desc-editor__media-type {
  flex: 0 0 auto;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 12px;
  border: 1px solid var(--color-base-300);
  background: color-mix(in oklab, var(--color-primary) 10%, transparent);
  color: var(--color-primary);
}

.desc-editor__media-type--VIDEO {
  background: color-mix(in oklab, var(--color-accent) 12%, transparent);
  color: var(--color-accent);
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
  color: color-mix(in oklab, var(--color-base-content) 60%, transparent);
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
