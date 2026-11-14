import { ref } from 'vue'

import { publishGoods } from '../api/goods'
import { uploadMedia } from '../api/media'
import type { GoodsPublishRequest, MediaPurpose } from '../api/types'
import {
  pendingMediaLines,
  toDescriptionBlocks,
} from '../components/goods/descriptionEditor'
import type {
  DescriptionEditorLine,
  MediaLine,
  PickedImage,
} from '../components/goods/descriptionEditor'
import { apiCodeMessage } from '../constants/messages'
import { errorMessage } from '../utils/error'
import { validateDescriptionBlocks, validateImageSelection } from '../validation'
import { useToast } from './useToast'

export type PublishPhase = 'idle' | 'uploading' | 'publishing'

export interface PublishMedia {
  mainImages: PickedImage[]
  galleryImages: PickedImage[]
  lines: DescriptionEditorLine[]
}

export interface UploadTask {
  file: File
  purpose: MediaPurpose
  /** 上传成功后把 URL 回填到该条目，作为重试时跳过已传项的缓存。 */
  target: PickedImage | MediaLine
}

/** 按发布顺序构建上传队列：主图 → 图集 → 描述媒体（行序）；已缓存 URL 的条目跳过。 */
export function buildUploadTasks(
  mainImages: PickedImage[],
  galleryImages: PickedImage[],
  lines: DescriptionEditorLine[],
): UploadTask[] {
  const tasks: UploadTask[] = []
  for (const item of [...mainImages, ...galleryImages]) {
    if (!item.url) {
      tasks.push({ file: item.file, purpose: 'GOODS_IMAGE', target: item })
    }
  }
  for (const line of pendingMediaLines(lines)) {
    tasks.push({ file: line.file, purpose: 'DESCRIPTION_MEDIA', target: line })
  }
  return tasks
}

/**
 * 发布编排：串行上传（便于进度提示）→ 终检 → 一次性发布。
 * 任一环失败即中止并提示，已成功者的 URL 已回填缓存，重试只传剩余。
 */
export function usePublishFlow() {
  const toast = useToast()
  const submitting = ref(false)
  const phase = ref<PublishPhase>('idle')
  const uploadedCount = ref(0)
  const uploadTotal = ref(0)
  /** 上次尝试失败：按钮文案改「重试发布」，确认弹窗说明不会重复上传。 */
  const attemptFailed = ref(false)

  async function run(payload: GoodsPublishRequest, media: PublishMedia): Promise<boolean> {
    submitting.value = true
    attemptFailed.value = false
    phase.value = 'uploading'
    uploadedCount.value = 0
    try {
      const tasks = buildUploadTasks(media.mainImages, media.galleryImages, media.lines)
      uploadTotal.value = tasks.length
      for (const task of tasks) {
        task.target.url = (await uploadMedia(task.file, task.purpose)).url
        uploadedCount.value += 1
      }

      // 终检：此刻所有 URL 已回填，结构与前缀校验完整可做。
      const mainUrl = media.mainImages[0]?.url ?? null
      const galleryUrls = media.galleryImages
        .map((item) => item.url)
        .filter((url): url is string => url != null)
      const imageCheck = validateImageSelection(mainUrl, galleryUrls)
      if (!imageCheck.ok) {
        toast.error(apiCodeMessage(imageCheck.code))
        attemptFailed.value = true
        return false
      }
      const blocks = toDescriptionBlocks(media.lines)
      const descCheck = validateDescriptionBlocks(blocks)
      if (!descCheck.ok) {
        toast.error(apiCodeMessage(descCheck.code))
        attemptFailed.value = true
        return false
      }

      phase.value = 'publishing'
      await publishGoods({
        ...payload,
        description: blocks,
        main_image: mainUrl,
        gallery_images: galleryUrls,
      })
      return true
    } catch (caught) {
      toast.error(errorMessage(caught))
      attemptFailed.value = true
      return false
    } finally {
      submitting.value = false
      phase.value = 'idle'
    }
  }

  return { submitting, phase, uploadedCount, uploadTotal, attemptFailed, run }
}