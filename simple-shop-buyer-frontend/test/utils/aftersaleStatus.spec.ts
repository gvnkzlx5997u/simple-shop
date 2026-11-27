import { describe, expect, it } from 'vitest'

import type { AftersaleResult, AftersaleStatus } from '../../src/api/types'
import {
  AFTERSALE_STATUS_FILTERS,
  AFTERSALE_STATUS_TONES,
  aftersaleResultText,
  aftersaleStatusText,
} from '../../src/utils/aftersaleStatus'

const ALL: AftersaleStatus[] = ['PENDING', 'COMPLETED', 'CANCELED']

describe('售后状态', () => {
  it('三态文案与后端枚举同序', () => {
    expect(ALL.map(aftersaleStatusText)).toEqual(['待处理', '已完成', '已取消'])
  })

  it('未知状态回退原值', () => {
    expect(aftersaleStatusText('BOGUS' as AftersaleStatus)).toBe('BOGUS')
  })

  it('tone 齐全：待处理 warning、完成 success、取消中性', () => {
    expect(Object.keys(AFTERSALE_STATUS_TONES).sort()).toEqual([...ALL].sort())
    expect(AFTERSALE_STATUS_TONES.PENDING).toBe('warning')
    expect(AFTERSALE_STATUS_TONES.COMPLETED).toBe('success')
    expect(AFTERSALE_STATUS_TONES.CANCELED).toBe('default')
  })

  it('筛选首项为全部，其余为三个单值', () => {
    expect(AFTERSALE_STATUS_FILTERS).toHaveLength(4)
    expect(AFTERSALE_STATUS_FILTERS[0]!.value).toBe('')
    expect(AFTERSALE_STATUS_FILTERS.slice(1).map((o) => o.value)).toEqual(ALL)
  })
})

describe('处理结果', () => {
  it('三种结果文案', () => {
    const results: AftersaleResult[] = ['RETURN_AND_REFUND', 'REFUND_ONLY', 'EXCHANGE']
    expect(results.map(aftersaleResultText)).toEqual(['退货并退款', '仅退款', '换货'])
  })

  it('未完成时结果为空，文案回空串', () => {
    expect(aftersaleResultText(null)).toBe('')
  })
})