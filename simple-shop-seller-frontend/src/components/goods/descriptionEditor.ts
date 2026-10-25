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
  const isImage =
    file.type === 'image/jpeg' ||
    file.type === 'image/png' ||
    name.endsWith('.jpg') ||
    name.endsWith('.jpeg') ||
    name.endsWith('.png')
  if (!isImage) {
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
  const name = file.name.toLowerCase()
  const isImage =
    file.type === 'image/jpeg' ||
    file.type === 'image/png' ||
    name.endsWith('.jpg') ||
    name.endsWith('.jpeg') ||
    name.endsWith('.png')
  if (!isImage) {
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
