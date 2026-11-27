/*
 * 地址字段规则照后端 AddressRules + Bean Validation 抄：
 *   收货人  ：非空、≤50 字（按码点）
 *   手机号  ：非空、≤20 字、只用数字与短横、7~20 位
 *   详细地址：非空、≤200 字（按码点）
 * 三项都是「一行文本」：拒绝换行、控制字符与尖括号——地址簿、订单快照、物流面单
 * 展示的是同一段文字，换行只会在其中一处错位。
 *
 * ⚠️ 手机号额外要求**数字至少 7 位**：后端的 `[0-9-]{7,20}` 允许 "-------" 这种
 * 全短横的值，等于没有电话。前端比后端严是安全的。
 */
const NAME_MAX = 50
const PHONE_MAX = 20
const DETAIL_MAX = 200
const PHONE_MIN_DIGITS = 7
const PHONE_RE = /^[0-9-]{7,20}$/

export interface AddressForm {
  receiver_name: string
  receiver_phone: string
  detail_address: string
}

/** ISO 控制字符（含换行与制表符），与后端 Character.isISOControl 同范围。 */
function hasControlChar(value: string): boolean {
  for (const ch of value) {
    const code = ch.codePointAt(0) ?? 0
    if (code <= 0x1f || (code >= 0x7f && code <= 0x9f)) return true
  }
  return false
}

/** 长度按码点算，与后端 charCount 和数据库字符数口径一致（emoji 算一个字）。 */
function singleLineError(raw: string, max: number, label: string): string {
  const value = raw.trim()
  if (!value) return `请填${label}`
  if ([...value].length > max) return `${label}最多 ${max} 字`
  if (hasControlChar(value) || value.includes('<') || value.includes('>')) {
    return `${label}不能含换行、控制字符或尖括号`
  }
  return ''
}

/** 返回空串表示通过，否则返回给用户看的具体原因。 */
export function validateAddress(form: AddressForm): string {
  const nameError = singleLineError(form.receiver_name, NAME_MAX, '收货人')
  if (nameError) return nameError

  const phoneError = singleLineError(form.receiver_phone, PHONE_MAX, '手机号')
  if (phoneError) return phoneError
  const phone = form.receiver_phone.trim()
  if (!PHONE_RE.test(phone)) return '手机号请填 7~20 位，只能用数字和短横'
  if ((phone.match(/[0-9]/g) ?? []).length < PHONE_MIN_DIGITS) {
    return `手机号里至少要有 ${PHONE_MIN_DIGITS} 位数字`
  }

  return singleLineError(form.detail_address, DETAIL_MAX, '详细地址')
}