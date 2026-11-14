import type { ContentBlockType, DescriptionBlockRequest } from '../../api/types'
import { countChars } from '../../validation/shared'
import { M10 } from '../../constants/messages'

/**
 * 描述块编辑器的行视图模型。
 *
 * 用户视角是「一个连续编辑区」；行序列只是内部结构，不暴露块概念。
 * 文本行在提交时各自映射为一个 TEXT 块（行内换行保留），媒体行映射为 IMAGE/VIDEO 块。
 */
export type DescriptionEditorLine =
  | { kind: 'text'; id: number; content: string }
  | {
      kind: 'media'
      id: number
      file: File
      mediaType: Exclude<ContentBlockType, 'TEXT'>
      /** 上传成功后由发布编排回填缓存；重试只传没有 url 的行。 */
      url?: string
    }

export type MediaLine = Extract<DescriptionEditorLine, { kind: 'media' }>

export const IMAGE_MAX_BYTES = 5 * 1024 * 1024
export const VIDEO_MAX_BYTES = 20 * 1024 * 1024

let nextLineId = 1

export function createTextLine(content = ''): DescriptionEditorLine {
  return { kind: 'text', id: nextLineId++, content }
}

export function createMediaLine(file: File, mediaType: MediaLine['mediaType']): MediaLine {
  return { kind: 'media', id: nextLineId++, file, mediaType }
}

/** JPG/PNG 判定：MIME 或扩展名任一命中即可（扩展名大小写不敏感）。 */
function isJpgOrPng(file: File): boolean {
  const name = file.name.toLowerCase()
  return (
    file.type === 'image/jpeg' ||
    file.type === 'image/png' ||
    name.endsWith('.jpg') ||
    name.endsWith('.jpeg') ||
    name.endsWith('.png')
  )
}

/** 选文件时的本地预检：类型与大小都可在前端判定，把错误挡在上传之前。 */
export function checkMediaFile(
  file: File,
): { ok: true; mediaType: MediaLine['mediaType'] } | { ok: false; message: string } {
  const name = file.name.toLowerCase()
  if (name.endsWith('.mp4') || file.type === 'video/mp4') {
    if (file.size > VIDEO_MAX_BYTES) {
      return { ok: false, message: M10.videoInvalid }
    }
    return { ok: true, mediaType: 'VIDEO' }
  }
  if (!isJpgOrPng(file)) {
    return { ok: false, message: M10.mediaTypeInvalid }
  }
  if (file.size > IMAGE_MAX_BYTES) {
    return { ok: false, message: M10.imageInvalid }
  }
  return { ok: true, mediaType: 'IMAGE' }
}

/** 各文本行累计码点数（与后端「合计 ≤1000」的口径一致）。 */
export function countDescriptionText(lines: DescriptionEditorLine[]): number {
  return lines.reduce(
    (total, line) => (line.kind === 'text' ? total + countChars(line.content) : total),
    0,
  )
}

/** 尚无缓存 URL、发布时需要上传的媒体行（保持行序）。 */
export function pendingMediaLines(lines: DescriptionEditorLine[]): MediaLine[] {
  return lines.filter((line): line is MediaLine => line.kind === 'media' && !line.url)
}

/** 商品图片（主图／图集）的待上传项；url 为发布编排回填的缓存。 */
export interface PickedImage {
  id: number
  file: File
  url?: string
}

export function createPickedImage(file: File): PickedImage {
  return { id: nextLineId++, file }
}

/** 商品图片选文件预检：仅 JPG／PNG 且 ≤5MB（用途 GOODS_IMAGE 不接受视频）。违规 20016。 */
export function checkGoodsImageFile(file: File): { ok: true } | { ok: false; message: string } {
  if (!isJpgOrPng(file)) {
    return { ok: false, message: M10.imageInvalid }
  }
  if (file.size > IMAGE_MAX_BYTES) {
    return { ok: false, message: M10.imageInvalid }
  }
  return { ok: true }
}

/**
 * 提交时把行序列映射为描述块数组：空文本行丢弃，其余文本行各自成块；
 * 媒体行要求发布编排已回填 url（未回填的行以空 url 占位，校验会拦下）。
 */
export function toDescriptionBlocks(lines: DescriptionEditorLine[]): DescriptionBlockRequest[] {
  const blocks: DescriptionBlockRequest[] = []
  for (const line of lines) {
    if (line.kind === 'text') {
      if (line.content.trim().length === 0) {
        continue
      }
      blocks.push({ type: 'TEXT', text: line.content })
    } else {
      blocks.push({ type: line.mediaType, url: line.url ?? '' })
    }
  }
  return blocks
}

// ---- 行操作（纯数据变换，DOM 焦点由编辑器组件负责） ----

/** 行操作完成后要把焦点落到的文本行与光标位置。 */
export interface LineFocus {
  focusId: number
  position: 'start' | 'end' | number
}

/**
 * 在聚焦文本行的光标处插入媒体行：把该行按光标切分为两段，媒体夹在中间。
 * 没有聚焦的文本行时追加到末尾并补一个可继续输入的文本行。
 * 返回插入后应聚焦的文本行 id；聚焦行不是文本行时返回 null（不插入）。
 */
export function insertMediaAtCaret(
  lines: DescriptionEditorLine[],
  focusedTextId: number | null,
  caret: number,
  media: MediaLine[],
): number | null {
  const index = lines.findIndex((line) => line.id === focusedTextId)
  if (index === -1) {
    const tail = createTextLine()
    lines.push(...media, tail)
    return tail.id
  }
  const target = lines[index]
  if (target === undefined || target.kind !== 'text') {
    return null
  }
  const before = createTextLine(target.content.slice(0, caret))
  const after = createTextLine(target.content.slice(caret))
  lines.splice(index, 1, before, ...media, after)
  return after.id
}

export type BoundaryMerge =
  | { kind: 'merged'; focusId: number; caret: number }
  | { kind: 'media'; mediaId: number }

/**
 * 文本行在行首（backward）或行尾（forward）与相邻行合并：
 * 相邻是文本行则并入内容、光标落在接缝处；相邻是媒体行则返回其 id 交给调用方删除。
 * 已在边界或行类型不符时返回 null。
 */
export function mergeAtBoundary(
  lines: DescriptionEditorLine[],
  index: number,
  direction: 'backward' | 'forward',
): BoundaryMerge | null {
  const line = lines[index]
  if (line === undefined || line.kind !== 'text') {
    return null
  }
  if (direction === 'backward') {
    if (index === 0) {
      return null
    }
    const previous = lines[index - 1]
    if (previous === undefined) {
      return null
    }
    if (previous.kind !== 'text') {
      return { kind: 'media', mediaId: previous.id }
    }
    const caret = previous.content.length
    previous.content = previous.content + line.content
    lines.splice(index, 1)
    return { kind: 'merged', focusId: previous.id, caret }
  }
  if (index >= lines.length - 1) {
    return null
  }
  const next = lines[index + 1]
  if (next === undefined) {
    return null
  }
  if (next.kind !== 'text') {
    return { kind: 'media', mediaId: next.id }
  }
  const caret = line.content.length
  line.content = line.content + next.content
  lines.splice(index + 1, 1)
  return { kind: 'merged', focusId: line.id, caret }
}

/**
 * 删除媒体行：删后两侧都是文本行则合并（光标在接缝处），否则焦点落到后面最近的
 * 文本行、没有就落到前面的；删空时补一个文本行。返回焦点目标，行不存在或无文本行可聚焦时返回 null。
 */
export function removeMediaLine(lines: DescriptionEditorLine[], id: number): LineFocus | null {
  const index = lines.findIndex((line) => line.id === id)
  if (index === -1) {
    return null
  }
  lines.splice(index, 1)

  if (lines.length === 0) {
    const tail = createTextLine()
    lines.push(tail)
    return { focusId: tail.id, position: 'start' }
  }

  const previous = lines[index - 1]
  const next = lines[index]
  if (previous && next && previous.kind === 'text' && next.kind === 'text') {
    const caret = previous.content.length
    previous.content = previous.content + next.content
    lines.splice(index, 1)
    return { focusId: previous.id, position: caret }
  }

  const nextText = lines.slice(index).find((line) => line.kind === 'text')
  if (nextText) {
    return { focusId: nextText.id, position: 'start' }
  }
  const previousText = lines
    .slice(0, index)
    .reverse()
    .find((line) => line.kind === 'text')
  if (previousText) {
    return { focusId: previousText.id, position: 'end' }
  }
  return null
}
