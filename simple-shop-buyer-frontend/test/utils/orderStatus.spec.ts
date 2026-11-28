import { describe, expect, it } from 'vitest'

import type { OrderStatus } from '../../src/api/types'
import {
  ORDER_STATUS_FILTERS,
  ORDER_STATUS_TONES,
  PAYMENT_CHANNELS,
  canApplyAftersale,
  canCancelByBuyer,
  canConfirmReceipt,
  canPayByBuyer,
  isTerminal,
  orderStatusText,
  paymentChannelText,
} from '../../src/utils/orderStatus'

const ALL: OrderStatus[] = [
  'PENDING_PAYMENT',
  'PAID',
  'CONFIRMED',
  'PREPARED',
  'SHIPPED',
  'COMPLETED',
  'CANCELED',
]

describe('orderStatusText', () => {
  it('七态文案与后端枚举顺序一致', () => {
    expect(ALL.map(orderStatusText)).toEqual([
      '待支付',
      '已支付',
      '已确认',
      '备货完成',
      '已发货',
      '交易完成',
      '已取消',
    ])
  })

  it('未知状态回退原值而不是 undefined', () => {
    expect(orderStatusText('BOGUS' as OrderStatus)).toBe('BOGUS')
  })
})

/**
 * 买家动作矩阵，逐格照抄契约的买家状态表。
 * 接口没有 can_* 标记，这份判断是前端唯一的依据，所以按整行断言而不是抽查。
 */
describe('买家动作矩阵', () => {
  it('只有待支付能支付', () => {
    expect(ALL.map(canPayByBuyer)).toEqual([true, false, false, false, false, false, false])
  })

  it('发货前四态可取消（含待支付）', () => {
    expect(ALL.map(canCancelByBuyer)).toEqual([true, true, true, true, false, false, false])
  })

  it('只有已发货能确认收货', () => {
    expect(ALL.map(canConfirmReceipt)).toEqual([false, false, false, false, true, false, false])
  })

  it('只有交易完成能申请售后', () => {
    expect(ALL.map(canApplyAftersale)).toEqual([false, false, false, false, false, true, false])
  })

  it('仅交易完成与已取消是终态', () => {
    expect(ALL.map(isTerminal)).toEqual([false, false, false, false, false, true, true])
  })
})

describe('筛选与色调', () => {
  it('筛选只含单值，首项为全部状态', () => {
    expect(ORDER_STATUS_FILTERS).toHaveLength(8)
    expect(ORDER_STATUS_FILTERS[0]!.value).toBe('')
    expect(ORDER_STATUS_FILTERS.slice(1).map((o) => o.value)).toEqual(ALL)
    expect(ORDER_STATUS_FILTERS.slice(1).map((o) => o.label)).toEqual(ALL.map(orderStatusText))
  })

  it('七态 tone 齐全：待支付 warning、完成 success、取消中性 default', () => {
    expect(Object.keys(ORDER_STATUS_TONES).sort()).toEqual([...ALL].sort())
    expect(ORDER_STATUS_TONES.PENDING_PAYMENT).toBe('warning')
    expect(ORDER_STATUS_TONES.COMPLETED).toBe('success')
    expect(ORDER_STATUS_TONES.CANCELED).toBe('default')
  })
})

describe('支付渠道', () => {
  it('四渠道齐全且与后端枚举同序', () => {
    expect(PAYMENT_CHANNELS.map((c) => c.value)).toEqual([
      'BANK_CARD',
      'CREDIT_CARD',
      'ALIPAY',
      'WECHAT',
    ])
    expect(PAYMENT_CHANNELS.map((c) => c.label)).toEqual(['银行卡', '信用卡', '支付宝', '微信'])
  })

  it('未支付时渠道为空，文案回空串', () => {
    expect(paymentChannelText(null)).toBe('')
    expect(paymentChannelText('ALIPAY')).toBe('支付宝')
  })
})