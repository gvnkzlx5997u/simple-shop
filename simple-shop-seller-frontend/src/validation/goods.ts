import { ApiCode } from '../api/types'
import type { DescriptionBlockRequest } from '../api/types'
import { countChars, fail, hasAngleBracket, hasLineBreak, ok, trimToNull } from './shared'
import type { Validated } from './shared'

export const GOODS_NAME_MAX_CHARS = 50
/** 描述文本块的累计长度上限（各文本块合计，与后端口径一致）。 */
export const DESCRIPTION_TOTAL_MAX_CHARS = 1000
export const MAX_GALLERY_IMAGES = 8
export const MAX_TOTAL_IMAGES = 9
/** 上传接口回填的媒体 URL 前缀；发布时的图片与描述媒体块都应指向它。 */
export const MEDIA_URL_PREFIX = '/media/'

/** 商品名称：必填；≤50 码点；拒尖括号与换行；返回 trim 后的值。违规 20012。 */
export function validateGoodsName(raw: string | null | undefined): Validated<string> {
  const value = trimToNull(raw)
  if (value === null) {
    return fail(ApiCode.PARAM_INVALID)
  }
  if (countChars(value) > GOODS_NAME_MAX_CHARS || hasAngleBracket(value) || hasLineBreak(value)) {
    return fail(ApiCode.GOODS_NAME_INVALID)
  }
  return ok(value)
}

/** 价格文本的形状：十进制或科学计数法（对应后端的 BigDecimal 构造口径）。 */
const PRICE_PATTERN = /^[+-]?([0-9]+([.][0-9]*)?|[.][0-9]+)([eE][+-]?[0-9]+)?$/

/** 上限 999999.99 对应的「分」值。 */
const PRICE_MAX_MINOR = 99999999n

/**
 * 价格：必填；0 < price ≤ 999999.99；最多两位小数（支持科学计数法书写）。
 * 缺失/非数字 → 50002；越界/小数位过多 → 20014。返回两位小数字符串。
 */
export function validatePrice(raw: string | null | undefined): Validated<string> {
  if (raw == null || raw.trim().length === 0) {
    return fail(ApiCode.PARAM_INVALID)
  }
  const text = raw.trim()
  if (!PRICE_PATTERN.test(text)) {
    return fail(ApiCode.PARAM_INVALID)
  }

  const negative = text.startsWith('-')
  const body = text.replace(/^[+-]/, '')
  const eIndex = body.search(/[eE]/)
  const mantissa = eIndex < 0 ? body : body.slice(0, eIndex)
  const exponent = eIndex < 0 ? 0 : Number(body.slice(eIndex + 1))
  const dotIndex = mantissa.indexOf('.')
  const intPart = dotIndex < 0 ? mantissa : mantissa.slice(0, dotIndex)
  const fracPart = dotIndex < 0 ? '' : mantissa.slice(dotIndex + 1)
  const digits = (intPart + fracPart).replace(/^0+/, '')

  const scale = fracPart.length - exponent
  if (scale > 2) {
    return fail(ApiCode.PRICE_INVALID)
  }
  if (digits.length === 0 || negative) {
    return fail(ApiCode.PRICE_INVALID)
  }
  // 先按数量级挡掉过大值，避免为超大指数构造巨大整数。
  if (digits.length - 1 - scale >= 6) {
    return fail(ApiCode.PRICE_INVALID)
  }
  const minor = BigInt(digits) * 10n ** BigInt(2 - scale)
  if (minor <= 0n || minor > PRICE_MAX_MINOR) {
    return fail(ApiCode.PRICE_INVALID)
  }
  return ok(formatMinor(minor))
}

/** 把「分」值格式化为两位小数的字符串。 */
function formatMinor(minor: bigint): string {
  const text = minor.toString().padStart(3, '0')
  return text.slice(0, -2) + '.' + text.slice(-2)
}

/** 发布库存：必填；整数且 ≥1。违规 20015。 */
export function validatePublishStock(raw: unknown): Validated<number> {
  if (typeof raw !== 'number' || !Number.isInteger(raw) || raw < 1) {
    return fail(ApiCode.STOCK_INVALID)
  }
  return ok(raw)
}

/** 调整库存：必填；整数且 ≥0（OFF_ZERO 商品改为 >0 提交即自动重新上架）。违规 20015。 */
export function validateAdjustStock(raw: unknown): Validated<number> {
  if (typeof raw !== 'number' || !Number.isInteger(raw) || raw < 0) {
    return fail(ApiCode.STOCK_INVALID)
  }
  return ok(raw)
}

/** 发布类别：必选二级类别；未选 → 50002（页面映射「请选择二级类别」）。 */
export function validateCategoryId(raw: string | null | undefined): Validated<string> {
  const value = trimToNull(raw)
  if (value === null) {
    return fail(ApiCode.PARAM_INVALID)
  }
  return ok(value)
}

/**
 * 描述块数组：可为空；逐块校验——TEXT 必须带文本（不含尖括号），IMAGE/VIDEO 必须带
 * 已上传的媒体 URL；所有文本块合计 ≤1000 码点。违规 20013。
 */
export function validateDescriptionBlocks(
  blocks: DescriptionBlockRequest[],
): Validated<DescriptionBlockRequest[]> {
  let total = 0
  for (const block of blocks) {
    if (block.type === 'TEXT') {
      const text = block.text
      if (text == null || text.trim().length === 0 || block.url != null) {
        return fail(ApiCode.GOODS_DESCRIPTION_INVALID)
      }
      if (hasAngleBracket(text)) {
        return fail(ApiCode.GOODS_DESCRIPTION_INVALID)
      }
      total += countChars(text)
      if (total > DESCRIPTION_TOTAL_MAX_CHARS) {
        return fail(ApiCode.GOODS_DESCRIPTION_INVALID)
      }
    } else {
      const url = trimToNull(block.url)
      if (url === null || block.text != null || !url.startsWith(MEDIA_URL_PREFIX)) {
        return fail(ApiCode.GOODS_DESCRIPTION_INVALID)
      }
    }
  }
  return ok(blocks)
}

/**
 * 图片选择：有图集必须先有主图（20018）；图集 ≤8 张（20017）；
 * 所有图片 URL 必须指向本服务媒体且不得重复（20016）。
 */
export function validateImageSelection(
  mainImage: string | null,
  gallery: string[],
): Validated<void> {
  const main = trimToNull(mainImage)
  if (main === null && gallery.length > 0) {
    return fail(ApiCode.MAIN_IMAGE_REQUIRED)
  }
  if (gallery.length > MAX_GALLERY_IMAGES) {
    return fail(ApiCode.IMAGE_LIMIT_EXCEEDED)
  }
  const seen = new Set<string>()
  for (const url of [main, ...gallery]) {
    if (url == null) {
      continue
    }
    if (!url.startsWith(MEDIA_URL_PREFIX) || !seen.add(url)) {
      return fail(ApiCode.IMAGE_INVALID)
    }
  }
  return ok(undefined)
}
