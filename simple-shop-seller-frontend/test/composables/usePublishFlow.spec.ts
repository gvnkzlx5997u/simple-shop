import { beforeEach, describe, expect, it, vi } from 'vitest'

import { publishGoods } from '@/api/goods'
import { uploadMedia } from '@/api/media'
import type { GoodsPublishRequest, MediaUploadData } from '@/api/types'
import {
  createMediaLine,
  createPickedImage,
  createTextLine,
} from '@/components/goods/descriptionEditor'
import { buildUploadTasks, usePublishFlow } from '@/composables/usePublishFlow'
import { M10 } from '@/constants/messages'

vi.mock('@/api/media', () => ({ uploadMedia: vi.fn() }))
vi.mock('@/api/goods', () => ({ publishGoods: vi.fn() }))

// toast 依赖 window（node 环境没有），单测里替换为哑实现并顺带断言错误提示。
const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn(), info: vi.fn() }))
vi.mock('@/composables/useToast', () => ({ useToast: () => toastMock }))

function makeFile(name: string): File {
  return new File([new Uint8Array(8)], name, { type: 'image/jpeg' })
}

function uploadResult(file: File): MediaUploadData {
  return { url: '/media/' + file.name, kind: 'IMAGE', size_bytes: file.size }
}

const PAYLOAD: GoodsPublishRequest = { name: '手机', price: 999, stock: 5, category_id: '42' }

beforeEach(() => {
  vi.mocked(uploadMedia).mockReset()
  vi.mocked(publishGoods).mockReset()
  toastMock.success.mockClear()
  toastMock.error.mockClear()
})

describe('buildUploadTasks', () => {
  it('顺序为主图 → 图集 → 描述媒体，跳过已缓存 URL 的条目', () => {
    const main = createPickedImage(makeFile('main.jpg'))
    const cached = createPickedImage(makeFile('cached.jpg'))
    cached.url = '/media/cached.jpg'
    const gallery = createPickedImage(makeFile('g1.jpg'))
    const lines = [createTextLine('文本'), createMediaLine(makeFile('d1.jpg'), 'IMAGE')]
    const tasks = buildUploadTasks([main], [cached, gallery], lines)
    expect(tasks.map((task) => task.file.name)).toEqual(['main.jpg', 'g1.jpg', 'd1.jpg'])
    expect(tasks.map((task) => task.purpose)).toEqual([
      'GOODS_IMAGE',
      'GOODS_IMAGE',
      'DESCRIPTION_MEDIA',
    ])
  })

  it('全部已缓存时返回空队列', () => {
    const main = createPickedImage(makeFile('main.jpg'))
    main.url = '/media/main.jpg'
    expect(buildUploadTasks([main], [], [])).toEqual([])
  })
})

describe('usePublishFlow.run', () => {
  it('串行按序上传，用回填的 URL 组装发布请求', async () => {
    const order: string[] = []
    vi.mocked(uploadMedia).mockImplementation(async (file) => {
      order.push(file.name)
      return uploadResult(file)
    })
    vi.mocked(publishGoods).mockResolvedValue({} as never)

    const main = createPickedImage(makeFile('main.jpg'))
    const gallery = createPickedImage(makeFile('g1.jpg'))
    const flow = usePublishFlow()
    const ok = await flow.run(PAYLOAD, {
      mainImages: [main],
      galleryImages: [gallery],
      lines: [createTextLine('描述')],
    })

    expect(ok).toBe(true)
    expect(order).toEqual(['main.jpg', 'g1.jpg'])
    expect(flow.uploadedCount.value).toBe(2)
    expect(flow.uploadTotal.value).toBe(2)
    expect(flow.submitting.value).toBe(false)
    expect(flow.phase.value).toBe('idle')
    expect(flow.attemptFailed.value).toBe(false)
    expect(publishGoods).toHaveBeenCalledWith({
      ...PAYLOAD,
      description: [{ type: 'TEXT', text: '描述' }],
      main_image: '/media/main.jpg',
      gallery_images: ['/media/g1.jpg'],
    })
  })

  it('上传中途失败即停：保留已传缓存、不发布、标记失败', async () => {
    vi.mocked(uploadMedia)
      .mockImplementationOnce(async (file) => uploadResult(file))
      .mockRejectedValueOnce(new Error('boom'))

    const main = createPickedImage(makeFile('main.jpg'))
    const gallery = createPickedImage(makeFile('g1.jpg'))
    const flow = usePublishFlow()
    const ok = await flow.run(PAYLOAD, { mainImages: [main], galleryImages: [gallery], lines: [] })

    expect(ok).toBe(false)
    expect(flow.attemptFailed.value).toBe(true)
    expect(flow.submitting.value).toBe(false)
    expect(main.url).toBe('/media/main.jpg')
    expect(gallery.url).toBeUndefined()
    expect(publishGoods).not.toHaveBeenCalled()
    expect(toastMock.error).toHaveBeenCalledTimes(1)
  })

  it('重试只上传剩余条目', async () => {
    vi.mocked(uploadMedia)
      .mockImplementationOnce(async (file) => uploadResult(file))
      .mockRejectedValueOnce(new Error('boom'))
      .mockImplementation(async (file) => uploadResult(file))
    vi.mocked(publishGoods).mockResolvedValue({} as never)

    const main = createPickedImage(makeFile('main.jpg'))
    const gallery = createPickedImage(makeFile('g1.jpg'))
    const media = { mainImages: [main], galleryImages: [gallery], lines: [] }
    const flow = usePublishFlow()
    await flow.run(PAYLOAD, media)
    vi.mocked(uploadMedia).mockClear()
    const ok = await flow.run(PAYLOAD, media)

    expect(ok).toBe(true)
    expect(uploadMedia).toHaveBeenCalledTimes(1)
    expect(vi.mocked(uploadMedia).mock.calls[0]![0].name).toBe('g1.jpg')
    expect(flow.attemptFailed.value).toBe(false)
  })

  it('终检失败（有图集无主图）不发布', async () => {
    vi.mocked(uploadMedia).mockImplementation(async (file) => uploadResult(file))
    const gallery = createPickedImage(makeFile('g1.jpg'))
    const flow = usePublishFlow()
    const ok = await flow.run(PAYLOAD, { mainImages: [], galleryImages: [gallery], lines: [] })
    expect(ok).toBe(false)
    expect(flow.attemptFailed.value).toBe(true)
    expect(publishGoods).not.toHaveBeenCalled()
    expect(toastMock.error).toHaveBeenCalledWith(M10.mainImageRequired)
  })

  it('发布接口失败标记失败并复位状态', async () => {
    vi.mocked(publishGoods).mockRejectedValue(new Error('boom'))
    const flow = usePublishFlow()
    const ok = await flow.run(PAYLOAD, { mainImages: [], galleryImages: [], lines: [] })
    expect(ok).toBe(false)
    expect(flow.attemptFailed.value).toBe(true)
    expect(flow.submitting.value).toBe(false)
    expect(flow.phase.value).toBe('idle')
  })
})