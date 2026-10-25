import { ApiCode } from '../api/types'
import { countChars, fail, hasAngleBracket, hasLineBreak, ok, trimToNull } from './shared'
import type { Validated } from './shared'

export const CATEGORY_NAME_MAX_CHARS = 50

/** 类别名：必填；≤50 码点；拒尖括号与换行；返回 trim 后的值。违规 20003。 */
export function validateCategoryName(raw: string | null | undefined): Validated<string> {
  const value = trimToNull(raw)
  if (value === null) {
    return fail(ApiCode.PARAM_INVALID)
  }
  if (
    countChars(value) > CATEGORY_NAME_MAX_CHARS ||
    hasAngleBracket(value) ||
    hasLineBreak(value)
  ) {
    return fail(ApiCode.CATEGORY_NAME_INVALID)
  }
  return ok(value)
}
