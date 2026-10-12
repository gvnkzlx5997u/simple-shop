import { request } from './http'
import type { IntentionPageData, PasscodeData, TradeFailureRequest } from './types'

const INTENTIONS_PATH = '/seller/product/intentions'
const TRADE_PATH = '/seller/intention/trade'
const SUCCESS_PATH = '/seller/intention/success'
const FAILURE_PATH = '/seller/intention/failure'

//查看口令码原文。本期（最小可行性产品）不做，后端有预留接口。
// const PASSCODE_PATH = '/seller/intention/passcode'

/**
 * I11-10 查当前商品的意向名单；无商品时 data 为 null。
 * page 从 1 起，page_size 默认 10、上限 100。
 */
export function listIntentions(page = 1, pageSize = 10): Promise<IntentionPageData | null> {
  return request<IntentionPageData | null>({
    method: 'get',
    url: INTENTIONS_PATH,
    params: { page, page_size: pageSize },
  })
}

/** I11-11 对队首意向进入交易；非队首返回 30002。 */
export function enterTrade(intentionId: string): Promise<void> {
  return request<null>({
    method: 'post',
    url: TRADE_PATH,
    data: { intention_id: intentionId },
  }).then(() => undefined)
}

/** I11-12 标记交易成功。 */
export function markTradeSuccess(intentionId: string): Promise<void> {
  return request<null>({
    method: 'post',
    url: SUCCESS_PATH,
    data: { intention_id: intentionId },
  }).then(() => undefined)
}

/** I11-13 标记交易失败；disposal 取 voided（作废）或 requeued（重新排队）。 */
export function markTradeFailure(payload: TradeFailureRequest): Promise<void> {
  return request<null>({ method: 'post', url: FAILURE_PATH, data: payload }).then(() => undefined)
}

/** I11-14 查看口令码原文；终态意向返回 40002。本期不做，后端有预留接口。 */
// export function getPasscode(intentionId: string): Promise<PasscodeData> {
//   return request<PasscodeData>({
//     method: 'get',
//     url: PASSCODE_PATH,
//     params: { intention_id: intentionId },
//   })
// }
