import { upload } from './http'
import type { MediaUploadData } from './types'

/**
 * 买家上传：只开放售后凭证图，其他用途一律 20027。
 *
 * `purpose` 是**查询参数**不是表单字段（后端用 @RequestParam 收），
 * multipart 里只有 `file` 一段。返回的 url 才能放进售后申请，外链会被拒（20016）。
 */
export function uploadAftersaleImage(file: File): Promise<MediaUploadData> {
  const form = new FormData()
  form.append('file', file)
  return upload<MediaUploadData>('/buyer/media', form, { purpose: 'AFTERSALE_IMAGE' })
}