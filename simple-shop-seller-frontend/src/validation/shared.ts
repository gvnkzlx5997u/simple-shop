/** 校验结果：通过时带归一化后的值；失败时带与后端一致的业务码。 */
export type Validated<T> = { ok: true; value: T } | { ok: false; code: number }

export function ok<T>(value: T): Validated<T> {
  return { ok: true, value }
}

export function fail<T = never>(code: number): Validated<T> {
  return { ok: false, code }
}

/** 按码点计字符数（与后端对商品名/描述/失败备注的口径一致）。 */
export function countChars(value: string): number {
  return Array.from(value).length
}

/** 是否含尖括号（统一文本规则：完全拒绝，不做转义放行）。 */
export function hasAngleBracket(value: string): boolean {
  return value.includes('<') || value.includes('>')
}

/** 是否含换行；\r 一并算，避免 \r\n 漏网。 */
export function hasLineBreak(value: string): boolean {
  return value.includes('\n') || value.includes('\r')
}

/** 首尾 trim；空串返回 null（可选字段统一收敛为 null）。 */
export function trimToNull(raw: string | null | undefined): string | null {
  if (raw == null) {
    return null
  }
  const trimmed = raw.trim()
  return trimmed.length === 0 ? null : trimmed
}
