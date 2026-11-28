import { describe, expect, it } from 'vitest'

import type { AftersaleStatus } from '@/api/types'
import { sellerAftersaleActions } from '@/components/aftersale/aftersaleActions'
import {
  AFTERSALE_RESULT_LABELS,
  AFTERSALE_RESULT_OPTIONS,
  AFTERSALE_RESULT_TONES,
  AFTERSALE_STATUS_LABELS,
  AFTERSALE_STATUS_ORDER,
  AFTERSALE_STATUS_TONES,
} from '@/constants/labels'

const EXPECTED_STATUS: AftersaleStatus[] = ['PENDING', 'COMPLETED', 'CANCELED']

describe('售后状态表', () => {
  it('三态顺序：待处理在最前，两个终态在后', () => {
    expect(AFTERSALE_STATUS_ORDER).toEqual(EXPECTED_STATUS)
  })

  it.each(EXPECTED_STATUS)('%s 都有中文名与配色', (status) => {
    expect(AFTERSALE_STATUS_LABELS[status]?.length).toBeGreaterThan(0)
    expect(AFTERSALE_STATUS_TONES[status]).toBeDefined()
  })

  it('「已取消」用中性色：拒绝与买家撤销共用这一个状态', () => {
    expect(AFTERSALE_STATUS_LABELS.CANCELED).toBe('已取消')
    expect(AFTERSALE_STATUS_TONES.CANCELED).toBe('default')
  })
})

describe('售后处理结果表', () => {
  it('三种结果都有中文名与配色', () => {
    expect(Object.keys(AFTERSALE_RESULT_LABELS).sort()).toEqual(
      ['EXCHANGE', 'REFUND_ONLY', 'RETURN_AND_REFUND'].sort(),
    )
    expect(Object.keys(AFTERSALE_RESULT_TONES).sort()).toEqual(
      ['EXCHANGE', 'REFUND_ONLY', 'RETURN_AND_REFUND'].sort(),
    )
  })

  it('结果选项与文案表一一对应，且每项都带说明', () => {
    expect(AFTERSALE_RESULT_OPTIONS.map((option) => option.value)).toEqual([
      'RETURN_AND_REFUND',
      'REFUND_ONLY',
      'EXCHANGE',
    ])
    for (const option of AFTERSALE_RESULT_OPTIONS) {
      expect(option.label).toBe(AFTERSALE_RESULT_LABELS[option.value])
      expect(option.hint.length).toBeGreaterThan(0)
    }
  })

  it('换货的说明里点明「不退款」，两种退款都点明占用退款额度', () => {
    const exchange = AFTERSALE_RESULT_OPTIONS.find((option) => option.value === 'EXCHANGE')
    expect(exchange?.hint).toContain('不退款')
    for (const value of ['RETURN_AND_REFUND', 'REFUND_ONLY'] as const) {
      const option = AFTERSALE_RESULT_OPTIONS.find((item) => item.value === value)
      expect(option?.hint).toContain('额度')
    }
  })
})

describe('sellerAftersaleActions', () => {
  it('待处理可以完成或取消，且完成是主按钮', () => {
    const actions = sellerAftersaleActions('PENDING')
    expect(actions.map((action) => action.key)).toEqual(['complete', 'cancel'])
    expect(actions[0].primary).toBe(true)
    expect(actions[1].primary).toBe(false)
    expect(actions[1].danger).toBe(true)
  })

  it.each(['COMPLETED', 'CANCELED'] as AftersaleStatus[])('%s 是终态，没有动作', (status) => {
    expect(sellerAftersaleActions(status)).toEqual([])
  })
})
