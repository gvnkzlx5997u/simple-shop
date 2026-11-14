import { describe, expect, it } from 'vitest'

import {
  checkGoodsImageFile,
  checkMediaFile,
  createMediaLine,
  createTextLine,
  insertMediaAtCaret,
  mergeAtBoundary,
  removeMediaLine,
} from '@/components/goods/descriptionEditor'
import type { DescriptionEditorLine } from '@/components/goods/descriptionEditor'
import { M10 } from '@/constants/messages'

const MB = 1024 * 1024

function makeFile(name: string, type: string, size = 8): File {
  return new File([new Uint8Array(size)], name, { type })
}

describe('checkMediaFile（描述媒体本地预检）', () => {
  it('JPG/PNG 按 MIME 或扩展名识别为 IMAGE（扩展名大小写不敏感）', () => {
    expect(checkMediaFile(makeFile('a.jpg', 'image/jpeg'))).toEqual({ ok: true, mediaType: 'IMAGE' })
    expect(checkMediaFile(makeFile('a.png', 'image/png'))).toEqual({ ok: true, mediaType: 'IMAGE' })
    expect(checkMediaFile(makeFile('a.JPEG', ''))).toEqual({ ok: true, mediaType: 'IMAGE' })
  })

  it('MP4 按 MIME 或扩展名识别为 VIDEO，且优先于图片判定', () => {
    expect(checkMediaFile(makeFile('a.mp4', 'video/mp4'))).toEqual({ ok: true, mediaType: 'VIDEO' })
    expect(checkMediaFile(makeFile('a.MP4', ''))).toEqual({ ok: true, mediaType: 'VIDEO' })
    expect(checkMediaFile(makeFile('a.mp4', 'image/png'))).toEqual({ ok: true, mediaType: 'VIDEO' })
  })

  it('其他类型拒绝', () => {
    expect(checkMediaFile(makeFile('a.gif', 'image/gif'))).toEqual({
      ok: false,
      message: M10.mediaTypeInvalid,
    })
    expect(checkMediaFile(makeFile('a.txt', 'text/plain'))).toEqual({
      ok: false,
      message: M10.mediaTypeInvalid,
    })
  })

  it('图片恰好 5MB 通过，超出拒绝', () => {
    expect(checkMediaFile(makeFile('a.jpg', 'image/jpeg', 5 * MB)).ok).toBe(true)
    expect(checkMediaFile(makeFile('a.jpg', 'image/jpeg', 5 * MB + 1))).toEqual({
      ok: false,
      message: M10.imageInvalid,
    })
  })

  it('视频恰好 20MB 通过，超出拒绝', () => {
    expect(checkMediaFile(makeFile('a.mp4', 'video/mp4', 20 * MB)).ok).toBe(true)
    expect(checkMediaFile(makeFile('a.mp4', 'video/mp4', 20 * MB + 1))).toEqual({
      ok: false,
      message: M10.videoInvalid,
    })
  })
})

describe('checkGoodsImageFile（商品图本地预检）', () => {
  it('仅接受 JPG/PNG', () => {
    expect(checkGoodsImageFile(makeFile('a.jpg', 'image/jpeg'))).toEqual({ ok: true })
    expect(checkGoodsImageFile(makeFile('a.png', 'image/png'))).toEqual({ ok: true })
    expect(checkGoodsImageFile(makeFile('a.JPG', ''))).toEqual({ ok: true })
  })

  it('视频与其他类型拒绝（商品图用途不接受 MP4）', () => {
    expect(checkGoodsImageFile(makeFile('a.mp4', 'video/mp4'))).toEqual({
      ok: false,
      message: M10.imageInvalid,
    })
    expect(checkGoodsImageFile(makeFile('a.gif', 'image/gif'))).toEqual({
      ok: false,
      message: M10.imageInvalid,
    })
  })

  it('恰好 5MB 通过，超出拒绝', () => {
    expect(checkGoodsImageFile(makeFile('a.jpg', 'image/jpeg', 5 * MB))).toEqual({ ok: true })
    expect(checkGoodsImageFile(makeFile('a.jpg', 'image/jpeg', 5 * MB + 1))).toEqual({
      ok: false,
      message: M10.imageInvalid,
    })
  })
})

describe('insertMediaAtCaret', () => {
  it('按光标切分聚焦文本行，媒体夹在中间，焦点落在后半段', () => {
    const text = createTextLine('abcdef')
    const lines: DescriptionEditorLine[] = [text]
    const media = createMediaLine(makeFile('a.jpg', 'image/jpeg'), 'IMAGE')
    const focusId = insertMediaAtCaret(lines, text.id, 3, [media])
    expect(lines.map((line) => line.kind)).toEqual(['text', 'media', 'text'])
    expect(lines[0]).toMatchObject({ kind: 'text', content: 'abc' })
    expect(lines[2]).toMatchObject({ kind: 'text', content: 'def' })
    expect(focusId).toBe(lines[2]!.id)
  })

  it('没有聚焦文本行时追加到末尾并补一个空文本行', () => {
    const lines: DescriptionEditorLine[] = [createTextLine('已有')]
    const media = createMediaLine(makeFile('a.jpg', 'image/jpeg'), 'IMAGE')
    const focusId = insertMediaAtCaret(lines, null, 0, [media])
    expect(lines.map((line) => line.kind)).toEqual(['text', 'media', 'text'])
    expect(lines[2]).toMatchObject({ content: '' })
    expect(focusId).toBe(lines[2]!.id)
  })

  it('聚焦行是媒体行时不插入', () => {
    const media = createMediaLine(makeFile('a.jpg', 'image/jpeg'), 'IMAGE')
    const lines: DescriptionEditorLine[] = [media]
    const another = createMediaLine(makeFile('b.jpg', 'image/jpeg'), 'IMAGE')
    expect(insertMediaAtCaret(lines, media.id, 0, [another])).toBeNull()
    expect(lines).toHaveLength(1)
  })
})

describe('mergeAtBoundary', () => {
  it('backward：并入上一文本行，光标在接缝处', () => {
    const a = createTextLine('abc')
    const b = createTextLine('def')
    const lines: DescriptionEditorLine[] = [a, b]
    expect(mergeAtBoundary(lines, 1, 'backward')).toEqual({ kind: 'merged', focusId: a.id, caret: 3 })
    expect(lines).toHaveLength(1)
    expect(a.content).toBe('abcdef')
  })

  it('forward：把下一文本行并入当前行', () => {
    const a = createTextLine('abc')
    const b = createTextLine('def')
    const lines: DescriptionEditorLine[] = [a, b]
    expect(mergeAtBoundary(lines, 0, 'forward')).toEqual({ kind: 'merged', focusId: a.id, caret: 3 })
    expect(lines).toHaveLength(1)
    expect(a.content).toBe('abcdef')
  })

  it('相邻是媒体行时返回其 id 交给调用方删除', () => {
    const media = createMediaLine(makeFile('a.jpg', 'image/jpeg'), 'IMAGE')
    const text = createTextLine('abc')
    expect(mergeAtBoundary([media, text], 1, 'backward')).toEqual({
      kind: 'media',
      mediaId: media.id,
    })
    expect(mergeAtBoundary([text, media], 0, 'forward')).toEqual({
      kind: 'media',
      mediaId: media.id,
    })
  })

  it('已在边界时返回 null', () => {
    const lines: DescriptionEditorLine[] = [createTextLine('abc')]
    expect(mergeAtBoundary(lines, 0, 'backward')).toBeNull()
    expect(mergeAtBoundary(lines, 0, 'forward')).toBeNull()
  })
})

describe('removeMediaLine', () => {
  it('删后两侧都是文本行则合并，光标在接缝处', () => {
    const a = createTextLine('abc')
    const media = createMediaLine(makeFile('a.jpg', 'image/jpeg'), 'IMAGE')
    const b = createTextLine('def')
    const lines: DescriptionEditorLine[] = [a, media, b]
    expect(removeMediaLine(lines, media.id)).toEqual({ focusId: a.id, position: 3 })
    expect(lines).toHaveLength(1)
    expect(a.content).toBe('abcdef')
  })

  it('两侧不都是文本时焦点落到后面最近的文本行', () => {
    const media1 = createMediaLine(makeFile('a.jpg', 'image/jpeg'), 'IMAGE')
    const media2 = createMediaLine(makeFile('b.jpg', 'image/jpeg'), 'IMAGE')
    const text = createTextLine('tail')
    const lines: DescriptionEditorLine[] = [media1, media2, text]
    expect(removeMediaLine(lines, media1.id)).toEqual({ focusId: text.id, position: 'start' })
  })

  it('后面没有文本行时焦点落到前面的文本行末尾', () => {
    const text = createTextLine('head')
    const media1 = createMediaLine(makeFile('a.jpg', 'image/jpeg'), 'IMAGE')
    const media2 = createMediaLine(makeFile('b.jpg', 'image/jpeg'), 'IMAGE')
    const lines: DescriptionEditorLine[] = [text, media1, media2]
    expect(removeMediaLine(lines, media2.id)).toEqual({ focusId: text.id, position: 'end' })
  })

  it('删空时补一个文本行', () => {
    const media = createMediaLine(makeFile('a.jpg', 'image/jpeg'), 'IMAGE')
    const lines: DescriptionEditorLine[] = [media]
    const focus = removeMediaLine(lines, media.id)
    expect(lines).toHaveLength(1)
    expect(lines[0]!.kind).toBe('text')
    expect(focus).toEqual({ focusId: lines[0]!.id, position: 'start' })
  })

  it('行不存在时返回 null 且不改动', () => {
    const lines: DescriptionEditorLine[] = [createTextLine('abc')]
    expect(removeMediaLine(lines, 99999)).toBeNull()
    expect(lines).toHaveLength(1)
  })
})