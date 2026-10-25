import { ApiCode } from '../api/types'
import { fail, ok } from './shared'
import type { Validated } from './shared'

/** 账号长度上限（对应后端 DTO 的 @Size，按 UTF-16 长度计）。 */
export const ACCOUNT_MAX_LENGTH = 50

/** 新密码长度下限（后端用 String.length() 判定，故这里也用 .length）。 */
export const PASSWORD_MIN_LENGTH = 8

/** 登录账号：必填、≤50。不做 trim（前后空格按「账号不存在」处理）。 */
export function validateAccount(raw: string | null | undefined): Validated<string> {
  if (raw == null || raw.trim().length === 0) {
    return fail(ApiCode.PARAM_INVALID)
  }
  if (raw.length > ACCOUNT_MAX_LENGTH) {
    return fail(ApiCode.PARAM_INVALID)
  }
  return ok(raw)
}

/** 登录口令：仅必填，不设长度限制；不做 trim。 */
export function validateLoginPassword(raw: string | null | undefined): Validated<string> {
  if (raw == null || raw.trim().length === 0) {
    return fail(ApiCode.PARAM_INVALID)
  }
  return ok(raw)
}

/** 原密码：仅必填。 */
export function validateOldPassword(raw: string | null | undefined): Validated<string> {
  if (raw == null || raw.trim().length === 0) {
    return fail(ApiCode.PARAM_INVALID)
  }
  return ok(raw)
}

/** 新密码：必填；不足 8 位 → 10004。 */
export function validateNewPassword(raw: string | null | undefined): Validated<string> {
  if (raw == null || raw.trim().length === 0) {
    return fail(ApiCode.PARAM_INVALID)
  }
  if (raw.length < PASSWORD_MIN_LENGTH) {
    return fail(ApiCode.PASSWORD_INVALID)
  }
  return ok(raw)
}
