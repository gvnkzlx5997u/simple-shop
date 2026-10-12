import { ApiCode } from '../api/types'
import { countChars, fail, hasAngleBracket, hasLineBreak, ok, trimToNull } from './shared'
import type { Validated } from './shared'

export const GOODS_NAME_MAX_CHARS = 50
export const GOODS_DESCRIPTION_MAX_CHARS = 500
export const DEFAULT_IMAGE_URL_PREFIX = '/images'

/** 商品名称：必填；≤50 码点；拒尖括号；不允许换行；返回 trim 后的值。违规 20007。 */
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

/** 商品描述：可选；≤500 码点；拒尖括号；允许换行；空收敛为 null。违规 20008。 */
export function validateGoodsDescription(raw: string | null | undefined): Validated<string | null> {
  const value = trimToNull(raw)
  if (value === null) {
    return ok(null)
  }
  if (countChars(value) > GOODS_DESCRIPTION_MAX_CHARS || hasAngleBracket(value)) {
    return fail(ApiCode.GOODS_DESCRIPTION_INVALID)
  }
  return ok(value)
}

/** 价格文本的形状：十进制或科学计数法（对应后端的 BigDecimal 构造口径）。 */
const PRICE_PATTERN = /^[+-]?([0-9]+([.][0-9]*)?|[.][0-9]+)([eE][+-]?[0-9]+)?$/

/** 上限 999999.99 对应的「分」值。 */
const PRICE_MAX_MINOR = 99999999n

/**
 * 价格：必填；0 < price ≤ 999999.99；最多两位小数。
 * 缺失/非数字 → 50002；越界/小数位过多 → 20010。返回两位小数字符串。
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
    return fail(ApiCode.PRICE_OUT_OF_RANGE)
  }
  if (digits.length === 0) {
    return fail(ApiCode.PRICE_OUT_OF_RANGE)
  }
  if (negative) {
    return fail(ApiCode.PRICE_OUT_OF_RANGE)
  }
  // 先按数量级挡掉过大值，避免为超大指数构造巨大整数。
  if (digits.length - 1 - scale >= 6) {
    return fail(ApiCode.PRICE_OUT_OF_RANGE)
  }
  const minor = BigInt(digits) * 10n ** BigInt(2 - scale)
  if (minor > PRICE_MAX_MINOR) {
    return fail(ApiCode.PRICE_OUT_OF_RANGE)
  }
  return ok(formatMinor(minor))
}

/** 把「分」值格式化为两位小数的字符串。 */
function formatMinor(minor: bigint): string {
  const text = minor.toString().padStart(3, '0')
  return text.slice(0, -2) + '.' + text.slice(-2)
}

/** 图片路径的形状：年/月/UUID 扩展名。 */
const PIC_TAIL =
  /^[0-9]{4}[/][0-9]{2}[/][0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}[.](jpg|png)$/

/**
 * 图片路径：可选；须是上传接口回填的 前缀/年/月/UUID.jpg|png 形状。违规 20009。
 * 只校验形状，文件是否存在由服务端判定。
 */
export function validatePicUrl(
  raw: string | null | undefined,
  urlPrefix: string = DEFAULT_IMAGE_URL_PREFIX,
): Validated<string | null> {
  const value = trimToNull(raw)
  if (value === null) {
    return ok(null)
  }
  const prefix = urlPrefix + '/'
  if (!value.startsWith(prefix)) {
    return fail(ApiCode.IMAGE_INVALID)
  }
  if (!PIC_TAIL.test(value.slice(prefix.length))) {
    return fail(ApiCode.IMAGE_INVALID)
  }
  return ok(value)
}
