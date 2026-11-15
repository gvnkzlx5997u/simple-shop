import { describe, expect, it } from 'vitest'

import type { OrderStatus } from '../../src/api/types'
import {
  ORDER_STATUS_FILTERS,
  ORDER_STATUS_TONES,
  canCancelByBuyer,
  isTerminal,
  orderStatusText,
} from '../../src/utils/orderStatus'

const ALL: OrderStatus[] = [
  'PENDING_CONFIRM',
  'CONFIRMED',
  'PREPARED',
  'SHIPPED',
  'COMPLETED',
  'CANCELED',
]

describe('orderStatusText', () => {
  it('六态文案与后端枚举顺序一致', () => {
    expect(ALL.map(orderStatusText)).toEqual(['待确认', '已确认', '备货完成', '已发货', '交易完成', '已取消'])
  })

  it('未知状态回退原值而不是 undefined', () => {
    expect(orderStatusText('BOGUS' as OrderStatus)).toBe('BOGUS')
  })
})

describe('canCancelByBuyer', () => {
  it('仅前三态可取消（与后端 Order.canCancelByBuyer 对齐）', () => {
    expect(ALL.map(canCancelByBuyer)).toEqual([true, true, true, false, false, false])
  })
})

describe('isTerminal', () => {
  it('仅交易完成与已取消是终态', () => {
    expect(ALL.map(isTerminal)).toEqual([false, false, false, false, true, true])
  })
})

describe('筛选与色调', () => {
  it('筛选只含单值，首项为全部状态', () => {
    expect(ORDER_STATUS_FILTERS).toHaveLength(7)
    expect(ORDER_STATUS_FILTERS[0]!.value).toBe('')
    expect(ORDER_STATUS_FILTERS.slice(1).map((o) => o.value)).toEqual(ALL)
  })

  it('六态 tone 齐全且取消用中性 default', () => {
    expect(Object.keys(ORDER_STATUS_TONES).sort()).toEqual([...ALL].sort())
    expect(ORDER_STATUS_TONES.PENDING_CONFIRM).toBe('warning')
    expect(ORDER_STATUS_TONES.COMPLETED).toBe('success')
    expect(ORDER_STATUS_TONES.CANCELED).toBe('default')
  })
})