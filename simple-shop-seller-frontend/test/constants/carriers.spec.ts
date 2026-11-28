import { describe, expect, it } from 'vitest'

import { CARRIER_OTHER, CARRIER_PRESETS, resolveCarrierSelection } from '@/constants/carriers'

/**
 * 承运商固定清单：需求总结要求「顺丰、京东、中通、圆通、申通、韵达、邮政 EMS、其他」，
 * 选「其他」时填名称。清单缺项或顺序乱，验收时一眼就能看出来，所以这里钉死。
 */
describe('CARRIER_PRESETS', () => {
  it('七家预设，顺序与文档一致', () => {
    expect(CARRIER_PRESETS.map((preset) => preset.label)).toEqual([
      '顺丰',
      '京东',
      '中通',
      '圆通',
      '申通',
      '韵达',
      '邮政 EMS',
    ])
  })

  it('存的值是公司全名且互不重复', () => {
    const values = CARRIER_PRESETS.map((preset) => preset.value)
    expect(new Set(values).size).toBe(values.length)
    expect(values).toContain('顺丰速运')
  })

  it('「其他」不是某家承运商的名字', () => {
    expect(CARRIER_PRESETS.some((preset) => preset.value === CARRIER_OTHER)).toBe(false)
  })
})

describe('resolveCarrierSelection', () => {
  it('清单内的名字直接选中，不留手填值', () => {
    expect(resolveCarrierSelection('中通快递')).toEqual({ choice: '中通快递', other: '' })
  })

  it('清单外的名字落到「其他」并带回原名：改物流时不能把原承运商冲掉', () => {
    expect(resolveCarrierSelection('德邦物流')).toEqual({
      choice: CARRIER_OTHER,
      other: '德邦物流',
    })
  })

  it('空值等于还没选，下拉停在占位项', () => {
    expect(resolveCarrierSelection('')).toEqual({ choice: '', other: '' })
    expect(resolveCarrierSelection(null)).toEqual({ choice: '', other: '' })
    expect(resolveCarrierSelection('   ')).toEqual({ choice: '', other: '' })
  })

  it('前后空格先 trim 再匹配', () => {
    expect(resolveCarrierSelection('  顺丰速运  ')).toEqual({ choice: '顺丰速运', other: '' })
  })
})
