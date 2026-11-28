import { describe, expect, it } from 'vitest'

import { M10 } from '@/constants/messages'
import {
  CARRIER_MAX_CHARS,
  LOGISTICS_REMARK_MAX_CHARS,
  TRACKING_NO_MAX_CHARS,
  validateLogisticsForm,
} from '@/validation/logistics'

/**
 * 发货 / 改物流表单校验。口径照后端 ShipLogisticsRequest + TextRules：
 * 必填、长度按码点、允许换行（一单多包裹）、其余控制字符拒绝。
 */
function form(carrier: string, trackingNo: string, remark = '') {
  return { carrier, trackingNo, remark }
}

describe('validateLogisticsForm 通过的情况', () => {
  it('三字段齐全时返回后端字段名（snake_case）', () => {
    const result = validateLogisticsForm(form(' 顺丰速运 ', 'SF1234567890 ', ' 分两个包裹 '))
    expect(result.errors).toEqual({ carrier: undefined, trackingNo: undefined, remark: undefined })
    expect(result.values).toEqual({
      carrier: '顺丰速运',
      tracking_no: 'SF1234567890',
      logistics_remark: '分两个包裹',
    })
  })

  it('备注留空收敛为 null，而不是空串', () => {
    const result = validateLogisticsForm(form('京东物流', 'JD001', '   '))
    expect(result.values?.logistics_remark).toBeNull()
  })

  it('运单号保留内部换行：一个订单多个包裹一行一个', () => {
    const result = validateLogisticsForm(form('中通快递', 'ZT001\nZT002'))
    expect(result.values?.tracking_no).toBe('ZT001\nZT002')
  })

  it('制表符不算非法控制字符（与后端口径一致）', () => {
    const result = validateLogisticsForm(form('圆通速递', 'YT001\tYT002'))
    expect(result.errors.trackingNo).toBeUndefined()
    expect(result.values?.tracking_no).toBe('YT001\tYT002')
  })
})

describe('承运商', () => {
  it('空或纯空白 → 必填提示', () => {
    expect(validateLogisticsForm(form('', 'SF001')).errors.carrier).toBe(M10.carrierRequired)
    expect(validateLogisticsForm(form('   ', 'SF001')).errors.carrier).toBe(M10.carrierRequired)
  })

  it('超过 50 码点 → 过长提示；正好 50 个通过', () => {
    const ok = 'a'.repeat(CARRIER_MAX_CHARS)
    expect(validateLogisticsForm(form(ok, 'SF001')).errors.carrier).toBeUndefined()
    const tooLong = 'a'.repeat(CARRIER_MAX_CHARS + 1)
    expect(validateLogisticsForm(form(tooLong, 'SF001')).errors.carrier).toBe(M10.carrierTooLong)
  })

  it('长度按码点算：50 个 emoji 通过，51 个不通过', () => {
    expect(validateLogisticsForm(form('😀'.repeat(50), 'SF001')).errors.carrier).toBeUndefined()
    expect(validateLogisticsForm(form('😀'.repeat(51), 'SF001')).errors.carrier).toBe(
      M10.carrierTooLong,
    )
  })

  it('承运商不允许换行（多包裹信息应该写在运单号里）', () => {
    expect(validateLogisticsForm(form('顺丰\n速运', 'SF001')).errors.carrier).toBe(
      M10.carrierSingleLine,
    )
  })
})

describe('运单号', () => {
  it('空 → 必填提示', () => {
    expect(validateLogisticsForm(form('顺丰速运', '')).errors.trackingNo).toBe(
      M10.trackingNoRequired,
    )
  })

  it('超过 500 码点 → 过长提示；正好 500 通过', () => {
    expect(
      validateLogisticsForm(form('顺丰速运', '1'.repeat(TRACKING_NO_MAX_CHARS))).errors.trackingNo,
    ).toBeUndefined()
    expect(
      validateLogisticsForm(form('顺丰速运', '1'.repeat(TRACKING_NO_MAX_CHARS + 1))).errors
        .trackingNo,
    ).toBe(M10.trackingNoTooLong)
  })
})

describe('备注', () => {
  it('超过 500 码点 → 过长提示', () => {
    const tooLong = 'a'.repeat(LOGISTICS_REMARK_MAX_CHARS + 1)
    expect(validateLogisticsForm(form('顺丰速运', 'SF001', tooLong)).errors.remark).toBe(
      M10.logisticsRemarkTooLong,
    )
  })
})

describe('控制字符', () => {
  it('\\u0000 一类的控制字符被拒（换行与制表符除外）', () => {
    expect(validateLogisticsForm(form('顺丰\u0000速运', 'SF001')).errors.carrier).toBe(
      M10.carrierControlChar,
    )
    expect(validateLogisticsForm(form('顺丰速运', 'SF\u0000 001')).errors.trackingNo).toBe(
      M10.trackingNoControlChar,
    )
    expect(validateLogisticsForm(form('顺丰速运', 'SF001', '备注\u0007')).errors.remark).toBe(
      M10.remarkControlChar,
    )
  })
})

describe('整体结果', () => {
  it('任一字段不通过时 values 为 null，且只填有问题的字段', () => {
    const result = validateLogisticsForm(form('', 'SF001'))
    expect(result.values).toBeNull()
    expect(result.errors.carrier).toBe(M10.carrierRequired)
    expect(result.errors.trackingNo).toBeUndefined()
    expect(result.errors.remark).toBeUndefined()
  })
})
