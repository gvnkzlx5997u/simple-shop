import type { ShipLogisticsRequest } from '../api/types'
import { M10 } from '../constants/messages'
import { countChars, trimToNull } from './shared'

/**
 * 发货 / 改物流表单的本地校验。
 *
 * 口径照抄后端（`ShipLogisticsRequest` + `TextRules`）：
 * - 承运商必填、≤50 码点；单号必填、≤500 码点；备注可空、≤500 码点；
 * - 允许换行与制表符（一个订单可能分多个包裹，单号一行一个），
 *   其余控制字符一律拒绝——后端也是这么判的，前端先挡能少一次往返。
 *
 * 这里返回「逐字段文案 + 归一化后的请求体」，页面只负责把文案挂到对应输入框下面。
 */
export const CARRIER_MAX_CHARS = 50
export const TRACKING_NO_MAX_CHARS = 500
export const LOGISTICS_REMARK_MAX_CHARS = 500

export interface LogisticsFormInput {
  carrier: string
  trackingNo: string
  remark: string
}

export interface LogisticsFormErrors {
  carrier?: string
  trackingNo?: string
  remark?: string
}

/**
 * 含除 \n \r \t 以外的控制字符。与后端 TextRules.containsDisallowedControlChar 同口径；
 * 用码点遍历，保证代理对（emoji 等）不会被拆成两个字符误判。
 */
function hasDisallowedControlChar(value: string): boolean {
  for (const char of value) {
    const codePoint = char.codePointAt(0) ?? 0
    if (codePoint < 0x20 || codePoint === 0x7f) {
      if (char !== '\n' && char !== '\r' && char !== '\t') {
        return true
      }
    }
  }
  return false
}

/** 单行字段（承运商）：空白也算非法字符；换行不属于承运商该有的内容，一并拒绝。 */
function checkCarrier(raw: string): { error?: string; value: string | null } {
  const value = trimToNull(raw)
  if (value === null) {
    return { error: M10.carrierRequired, value: null }
  }
  if (value.includes('\n') || value.includes('\r')) {
    return { error: M10.carrierSingleLine, value: null }
  }
  if (hasDisallowedControlChar(value)) {
    return { error: M10.carrierControlChar, value: null }
  }
  if (countChars(value) > CARRIER_MAX_CHARS) {
    return { error: M10.carrierTooLong, value: null }
  }
  return { value }
}

/** 多行字段（单号）：保留内部换行，只去掉首尾空白。 */
function checkTrackingNo(raw: string): { error?: string; value: string | null } {
  const value = trimToNull(raw)
  if (value === null) {
    return { error: M10.trackingNoRequired, value: null }
  }
  if (hasDisallowedControlChar(value)) {
    return { error: M10.trackingNoControlChar, value: null }
  }
  if (countChars(value) > TRACKING_NO_MAX_CHARS) {
    return { error: M10.trackingNoTooLong, value: null }
  }
  return { value }
}

/** 可选字段（备注）：空串收敛为 null，与后端「不传即空」一致。 */
function checkRemark(raw: string): { error?: string; value: string | null } {
  const value = trimToNull(raw)
  if (value === null) {
    return { value: null }
  }
  if (hasDisallowedControlChar(value)) {
    return { error: M10.remarkControlChar, value: null }
  }
  if (countChars(value) > LOGISTICS_REMARK_MAX_CHARS) {
    return { error: M10.logisticsRemarkTooLong, value: null }
  }
  return { value }
}

/**
 * 一次性校验三个字段：任一不通过时 values 为 null，errors 里只填有问题的字段。
 * 返回的请求体已 trim，字段名与后端一致（tracking_no / logistics_remark）。
 */
export function validateLogisticsForm(form: LogisticsFormInput): {
  errors: LogisticsFormErrors
  values: ShipLogisticsRequest | null
} {
  const carrier = checkCarrier(form.carrier)
  const trackingNo = checkTrackingNo(form.trackingNo)
  const remark = checkRemark(form.remark)

  const errors: LogisticsFormErrors = {
    carrier: carrier.error,
    trackingNo: trackingNo.error,
    remark: remark.error,
  }

  if (carrier.value === null || trackingNo.value === null || Object.values(errors).some(Boolean)) {
    return { errors, values: null }
  }

  return {
    errors,
    values: {
      carrier: carrier.value,
      tracking_no: trackingNo.value,
      logistics_remark: remark.value,
    },
  }
}
