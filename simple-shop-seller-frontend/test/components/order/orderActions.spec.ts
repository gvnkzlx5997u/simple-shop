import { describe, expect, it } from 'vitest'

import type { OrderStatus } from '@/api/types'
import {
  canCancelOrder,
  canEditLogistics,
  isPendingPayment,
  isTerminalOrder,
  primaryOrderAction,
  sellerOrderActions,
} from '@/components/order/orderActions'

/**
 * 卖家订单动作表：把《契约交接》§4.1 的状态矩阵逐行钉住。
 *
 * 这张表是「按钮什么时候出现」的唯一依据，页面不再自己判断状态，
 * 所以用例按「状态 → 允许的动作集合」整体断言，避免只测了某一条边。
 */
const EXPECTED: Record<OrderStatus, string[]> = {
  // 待支付：卖家不能确认，只能取消。这是阶段四最容易写错的一条。
  PENDING_PAYMENT: ['cancel'],
  PAID: ['confirm', 'cancel'],
  CONFIRMED: ['prepare', 'cancel'],
  PREPARED: ['ship', 'cancel'],
  SHIPPED: ['editLogistics', 'cancel'],
  COMPLETED: ['editLogistics'],
  CANCELED: [],
}

const ALL_STATUSES = Object.keys(EXPECTED) as OrderStatus[]

describe('sellerOrderActions', () => {
  it.each(ALL_STATUSES)('%s 只允许契约里的动作', (status) => {
    expect(sellerOrderActions(status).map((action) => action.key)).toEqual(EXPECTED[status])
  })

  it('待支付没有「确认」：确认要等买家付款之后', () => {
    expect(sellerOrderActions('PENDING_PAYMENT').some((a) => a.key === 'confirm')).toBe(false)
  })

  it('已发货没有推进动作：交易完成只由买家确认收货推进', () => {
    const keys = sellerOrderActions('SHIPPED').map((action) => action.key)
    expect(keys).not.toContain('complete')
    expect(primaryOrderAction('SHIPPED')?.key).toBe('editLogistics')
  })

  it('已取消没有任何动作', () => {
    expect(sellerOrderActions('CANCELED')).toEqual([])
    expect(primaryOrderAction('CANCELED')).toBeNull()
  })

  it('主按钮最多一个，且排在取消之前', () => {
    for (const status of ALL_STATUSES) {
      const actions = sellerOrderActions(status)
      expect(actions.filter((action) => action.primary).length).toBeLessThanOrEqual(1)
      const primaryIndex = actions.findIndex((action) => action.primary)
      const cancelIndex = actions.findIndex((action) => action.key === 'cancel')
      if (primaryIndex >= 0 && cancelIndex >= 0) {
        expect(primaryIndex).toBeLessThan(cancelIndex)
      }
    }
  })

  it('取消按钮永远是次按钮（不能是主按钮）', () => {
    for (const status of ALL_STATUSES) {
      const cancel = sellerOrderActions(status).find((action) => action.key === 'cancel')
      if (cancel) {
        expect(cancel.primary).toBe(false)
        expect(cancel.danger).toBe(true)
      }
    }
  })
})

describe('primaryOrderAction', () => {
  const EXPECTED_PRIMARY: Partial<Record<OrderStatus, string>> = {
    PENDING_PAYMENT: undefined,
    PAID: 'confirm',
    CONFIRMED: 'prepare',
    PREPARED: 'ship',
    SHIPPED: 'editLogistics',
    COMPLETED: 'editLogistics',
    CANCELED: undefined,
  }

  it.each(ALL_STATUSES)('%s 的主按钮符合预期', (status) => {
    expect(primaryOrderAction(status)?.key).toBe(EXPECTED_PRIMARY[status])
  })
})

describe('canCancelOrder', () => {
  it('交易完成后不可取消，其余非终态都可取消', () => {
    for (const status of ALL_STATUSES) {
      expect(canCancelOrder(status)).toBe(EXPECTED[status].includes('cancel'))
    }
    expect(canCancelOrder('COMPLETED')).toBe(false)
    expect(canCancelOrder('SHIPPED')).toBe(true)
  })
})

describe('canEditLogistics', () => {
  it('只有已发货与交易完成能改物流', () => {
    for (const status of ALL_STATUSES) {
      expect(canEditLogistics(status)).toBe(status === 'SHIPPED' || status === 'COMPLETED')
    }
  })
})

describe('isTerminalOrder', () => {
  it('只有交易完成与已取消是终态', () => {
    for (const status of ALL_STATUSES) {
      expect(isTerminalOrder(status)).toBe(status === 'COMPLETED' || status === 'CANCELED')
    }
  })
})

describe('isPendingPayment', () => {
  it('只对待支付为真', () => {
    for (const status of ALL_STATUSES) {
      expect(isPendingPayment(status)).toBe(status === 'PENDING_PAYMENT')
    }
  })
})
