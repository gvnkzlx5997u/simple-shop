/**
 * 展示用格式化。
 *
 * 两个约束来自阶段D 计划 §2.2：
 * 1. 时间——后端一律返回 UTC ISO-8601（形如 `2026-11-11T10:00:00Z`），
 *    这里转成浏览器本地时区显示，不在页面里各自 `new Date()`。
 * 2. 金额——只用于**展示**。下单不带金额，合计也就地展示，
 *    不复用于任何提交或校验，避免前端重算与后端对不上。
 */

const pad = (n: number): string => String(n).padStart(2, '0')

/**
 * UTC ISO-8601 → 本地时区的 `YYYY-MM-DD HH:mm`。
 * 空值与无法解析的字符串一律回空串，让调用方决定怎么兜底，避免页面出现 "Invalid Date"。
 */
export function formatTime(iso: string | null | undefined): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return (
    d.getFullYear() +
    '-' +
    pad(d.getMonth() + 1) +
    '-' +
    pad(d.getDate()) +
    ' ' +
    pad(d.getHours()) +
    ':' +
    pad(d.getMinutes())
  )
}

/** 金额保留两位小数；后端给的是 BigDecimal 转来的数字或字符串，两种都收。 */
export function formatMoney(value: number | string | null | undefined): string {
  const n = typeof value === 'string' ? Number(value) : value
  if (n == null || !Number.isFinite(n)) return '0.00'
  return n.toFixed(2)
}
