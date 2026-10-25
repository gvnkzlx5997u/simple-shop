import { request } from './http'
import type { MediaPurpose, MediaUploadData } from './types'

const MEDIA_PATH = '/seller/media'

/**
 * 上传媒体；multipart 字段名必须是 file，用途经查询参数 purpose 传入。
 * GOODS_IMAGE 仅 JPG/PNG（≤5MB）；DESCRIPTION_MEDIA 另允许 MP4（≤20MB）。
 * 发布商品时回填返回的 url，后端会回验该文件确为本服务落盘。
 */
export function uploadMedia(file: File, purpose: MediaPurpose): Promise<MediaUploadData> {
  const form = new FormData()
  form.append('file', file)
  return request<MediaUploadData>({
    method: 'post',
    url: MEDIA_PATH,
    params: { purpose },
    data: form,
  })
}
