/** 请求序号守卫：只允许最新一次请求的结果落地，过期响应（含错误）整体丢弃，避免乱序响应覆盖新状态。 */
export function useRequestSequence() {
  let seq = 0
  return {
    begin(): number {
      seq += 1
      return seq
    },
    isCurrent(token: number): boolean {
      return token === seq
    },
  }
}