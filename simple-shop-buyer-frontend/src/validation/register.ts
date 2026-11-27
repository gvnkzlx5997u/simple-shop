/*
 * 注册字段规则与后端 AccountRules 保持一致（交接文档没写，这里是照后端源码抄的）：
 *   用户名：非空、≤50 字、不含空格/换行/尖括号
 *   密码  ：≥8 字（后端另有 UTF-8 ≤72 字节的限制，正常使用不会碰到）
 *   电话  ：7~20 位、只能用数字和短横
 *
 * ⚠️ 电话额外加了一条后端正则没有的约束：**数字至少 7 位**。
 * 后端的 `[0-9-]{7,20}` 允许 "-------" 这种全短横的值（实测能注册成功），
 * 而电话是商家的交易联系方式，全短横等于没有电话，属于脏数据。
 * 前端比后端严是安全的;根治仍需后端正则加一条。
 */
const USERNAME_MAX = 50
const PASSWORD_MIN = 8
const PHONE_MIN_DIGITS = 7
const PHONE_RE = /^[0-9-]{7,20}$/

export interface RegisterForm {
  username: string
  password: string
  phone: string
}

/** 返回空串表示通过，否则返回给用户看的具体原因。 */
export function validateRegister(form: RegisterForm): string {
  const u = form.username.trim()
  if (!u) return '请填用户名'
  if (u.length > USERNAME_MAX) return `用户名最多 ${USERNAME_MAX} 个字符`
  if (/[\s<>]/.test(u)) return '用户名不能包含空格、换行或尖括号'

  if (!form.password) return '请填密码'
  if (form.password.length < PASSWORD_MIN) return `密码至少 ${PASSWORD_MIN} 位`

  const p = form.phone.trim()
  if (!p) return '请填电话'
  if (!PHONE_RE.test(p)) return '电话请填 7~20 位，只能用数字和短横'
  if ((p.match(/[0-9]/g) ?? []).length < PHONE_MIN_DIGITS) {
    return `电话里至少要有 ${PHONE_MIN_DIGITS} 位数字`
  }

  return ''
}