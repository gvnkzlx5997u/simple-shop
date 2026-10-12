import { ApiCode } from '../api/types'
import { countChars, fail, hasAngleBracket, ok, trimToNull } from './shared'
import type { Disposal } from '../api/types'
import type { Validated } from './shared'

/** 失败备注长度上限。 */
export const FAIL_REASON_MAX_CHARS = 300

/** 意向 ID：必填（存在性与状态由服务端判定）。 */
export function validateIntentionId(raw: string | null | undefined): Validated<string> {
  const value = trimToNull(raw)
  if (value === null) {
    return fail(ApiCode.PARAM_INVALID)
  }
  return ok(value)
}

/** 裁决方式：只接受 voided / requeued；未选或其它值 → 30006。 */
export function validateDisposal(raw: string | null | undefined): Validated<Disposal> {
  if (raw === 'voided' || raw === 'requeued') {
    return ok(raw)
  }
  return fail(ApiCode.INVALID_DISPOSAL)
}

/** 失败备注：可选；≤300 码点；拒尖括号；允许换行；空收敛为 null。违规 30007。 */
export function validateFailReason(raw: string | null | undefined): Validated<string | null> {
  const value = trimToNull(raw)
  if (value === null) {
    return ok(null)
  }
  if (countChars(value) > FAIL_REASON_MAX_CHARS || hasAngleBracket(value)) {
    return fail(ApiCode.FAIL_REASON_TOO_LONG)
  }
  return ok(value)
}
