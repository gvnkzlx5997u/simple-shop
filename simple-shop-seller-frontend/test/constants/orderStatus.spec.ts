import { describe, expect, it } from 'vitest'

import type { OrderStatus } from '@/api/types'
import {
  ORDER_STATUS_HINTS,
  ORDER_STATUS_LABELS,
  ORDER_STATUS_ORDER,
  ORDER_STATUS_TONES,
  PAYMENT_CHANNEL_LABELS,
} from '@/constants/labels'

/**
 * 订单状态表的完整性。
 *
 * 这几张是 Record<OrderStatus, ...>，平时靠类型检查兜底；但类型检查拦不住
 * 「顺序写错」与「旧状态没删干净」，而 tab 顺序、流程图顺序都直接读 ORDER_STATUS_ORDER。
 */
const EXPECTED_ORDER: OrderStatus[] = [
  'PENDING_PAYMENT',
  'PAID',
  'CONFIRMED',
  'PREPARED',
  'SHIPPED',
  'COMPLETED',
  'CANCELED',
]

describe('ORDER_STATUS_ORDER', () => {
  it('正好七态，且顺序与流转顺序一致（已取消殿后）', () => {
    expect(ORDER_STATUS_ORDER).toEqual(EXPECTED_ORDER)
  })

  it('去掉终态「已取消」后正好是详情页流程图的六格', () => {
    expect(ORDER_STATUS_ORDER.filter((status) => status !== 'CANCELED')).toEqual([
      'PENDING_PAYMENT',
      'PAID',
      'CONFIRMED',
      'PREPARED',
      'SHIPPED',
      'COMPLETED',
    ])
  })

  it('不再残留阶段三的「待确认」', () => {
    expect(ORDER_STATUS_ORDER).not.toContain('PENDING_CONFIRM')
    expect(Object.keys(ORDER_STATUS_LABELS)).not.toContain('PENDING_CONFIRM')
    expect(JSON.stringify(ORDER_STATUS_HINTS)).not.toContain('PENDING_CONFIRM')
  })
})

describe('订单标签三张表', () => {
  it.each(EXPECTED_ORDER)('%s 都有中文名、配色与状态说明', (status) => {
    expect(ORDER_STATUS_LABELS[status]?.length).toBeGreaterThan(0)
    expect(ORDER_STATUS_TONES[status]).toBeDefined()
    expect(ORDER_STATUS_HINTS[status]?.length).toBeGreaterThan(0)
  })

  it('三张表的键集合与状态顺序表完全一致（不多不少）', () => {
    expect(Object.keys(ORDER_STATUS_LABELS).sort()).toEqual([...EXPECTED_ORDER].sort())
    expect(Object.keys(ORDER_STATUS_TONES).sort()).toEqual([...EXPECTED_ORDER].sort())
    expect(Object.keys(ORDER_STATUS_HINTS).sort()).toEqual([...EXPECTED_ORDER].sort())
  })

  it('配色只用约定的五种取值', () => {
    const allowed = ['default', 'success', 'warning', 'danger', 'info']
    for (const status of EXPECTED_ORDER) {
      expect(allowed).toContain(ORDER_STATUS_TONES[status])
    }
  })

  it('待支付与已支付用警示色：都在等卖家/买家下一步动作', () => {
    expect(ORDER_STATUS_TONES.PENDING_PAYMENT).toBe('warning')
    expect(ORDER_STATUS_TONES.PAID).toBe('warning')
  })
})

describe('PAYMENT_CHANNEL_LABELS', () => {
  it('四个渠道都有中文名', () => {
    expect(PAYMENT_CHANNEL_LABELS).toEqual({
      BANK_CARD: '银行卡',
      CREDIT_CARD: '信用卡',
      ALIPAY: '支付宝',
      WECHAT: '微信支付',
    })
  })
})
