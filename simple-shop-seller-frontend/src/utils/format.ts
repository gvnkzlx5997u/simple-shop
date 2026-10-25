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

/** 金额 → ¥1,234.00；兼容数字与字符串（空值或非法值返回 —）。 */
export function formatPrice(price: string | number | null | undefined): string {
  if (price == null || (typeof price === 'string' && price.trim().length === 0)) {
    return '—'
  }
  const value = Number(price)
  if (!Number.isFinite(value)) {
    return '—'
  }
  return '¥' + PRICE_FORMAT.format(value)
}

/** 字节数 → 12.4 MB / 800 KB / 512 B；非法值返回 —。 */
export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) {
    return '—'
  }
  if (bytes >= 1024 * 1024) {
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  }
  if (bytes >= 1024) {
    return Math.round(bytes / 1024) + ' KB'
  }
  return bytes + ' B'
}
