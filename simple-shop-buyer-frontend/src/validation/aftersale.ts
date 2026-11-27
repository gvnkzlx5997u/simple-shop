/*
 * 售后申请字段规则照后端 BuyerAftersaleServiceImpl 抄：
 *   标题：非空、≤100 字（码点）、一行文本
 *   描述：非空、≤1000 字（码点）、**允许换行与制表符**（买家会分几条写）
 *   图片：≤9 张，JPG/PNG，单张 ≤5MB
 * 两者都拒绝尖括号；控制字符按各自口径拒。
 */
const TITLE_MAX = 100
const DESCRIPTION_MAX = 1000
export const MAX_AFTERSALE_IMAGES = 9
export const IMAGE_MAX_BYTES = 5 * 1024 * 1024

export interface AftersaleForm {
  title: string
  description: string
}

/** ISO 控制字符；描述放行换行、回车、制表符，与后端 containsDisallowedControlChar 对齐。 */
function hasControlChar(value: string, allowLineBreaks: boolean): boolean {
  for (const ch of value) {
    const code = ch.codePointAt(0) ?? 0
    const isControl = code <= 0x1f || (code >= 0x7f && code <= 0x9f)
    if (!isControl) continue
    if (allowLineBreaks && (code === 0x0a || code === 0x0d || code === 0x09)) continue
    return true
  }
  return false
}

function hasAngleBracket(value: string): boolean {
  return value.includes('<') || value.includes('>')
}

/** 返回空串表示通过，否则返回给用户看的具体原因。 */
export function validateAftersale(form: AftersaleForm): string {
  const title = form.title.trim()
  if (!title) return '请填标题'
  if ([...title].length > TITLE_MAX) return `标题最多 ${TITLE_MAX} 字`
  if (hasControlChar(title, false) || hasAngleBracket(title)) {
    return '标题不能含换行、控制字符或尖括号'
  }

  const description = form.description.trim()
  if (!description) return '请填描述'
  if ([...description].length > DESCRIPTION_MAX) return `描述最多 ${DESCRIPTION_MAX} 字`
  if (hasControlChar(description, true) || hasAngleBracket(description)) {
    return '描述不能含尖括号或异常控制字符'
  }
  return ''
}

/**
 * 上传前的本地筛查。后端按魔数判定，这里只能看 MIME 与扩展名——
 * 目的是早点给出可读提示，真正的判定仍以后端为准。
 */
export function validateAftersaleImage(file: { name: string; type: string; size: number }): string {
  const byType = file.type === 'image/jpeg' || file.type === 'image/png'
  const byName = /\.(jpe?g|png)$/i.test(file.name)
  if (!byType && !(file.type === '' && byName)) return '图片只能是 JPG 或 PNG'
  if (file.size > IMAGE_MAX_BYTES) return '单张图片不能超过 5MB'
  return ''
}