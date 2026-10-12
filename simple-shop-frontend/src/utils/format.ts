/** 时间统一按东八区展示（后端下发 UTC ISO 8601）。 */
const DATE_TIME_FORMAT = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) {
    return '—'
  }
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return '—'
  }
  return DATE_TIME_FORMAT.format(date)
}

const PRICE_FORMAT = new Intl.NumberFormat('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

/** 金额字符串 → ¥1,234.00；空值或非法值返回 —。 */
export function formatPrice(price: string | null | undefined): string {
  if (price == null || price.trim().length === 0) {
    return '—'
  }
  const value = Number(price)
  if (!Number.isFinite(value)) {
    return '—'
  }
  return '¥' + PRICE_FORMAT.format(value)
}
