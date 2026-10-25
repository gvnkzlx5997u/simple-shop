import { ApiCode } from '../api/types'
import type { Disposal } from '../api/types'
import { fail, ok } from './shared'
import type { Validated } from './shared'

/** 裁决方式：只接受 VOIDED / REQUEUED；未选或其它值 → 30006。 */
export function validateDisposal(raw: string | null | undefined): Validated<Disposal> {
  if (raw === 'VOIDED' || raw === 'REQUEUED') {
    return ok(raw)
  }
  return fail(ApiCode.INVALID_DISPOSAL)
}
