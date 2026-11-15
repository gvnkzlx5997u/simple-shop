/** 登录后回跳地址消毒：只接受站内路径。`//evil.com` 是协议相对地址，浏览器会当成外站跳走。 */
export function safeRedirect(raw: unknown): string {
  if (typeof raw !== 'string') return ''
  return raw.startsWith('/') && !raw.startsWith('//') ? raw : ''
}