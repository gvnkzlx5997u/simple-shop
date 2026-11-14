import { describe, expect, it } from 'vitest'

import { M10 } from '@/constants/messages'
import { validatePublishForm } from '@/validation/goods'

const VALID = {
  name: ' 手机 ',
  price: '999.5',
  stock: 5,
  categoryL1: '1',
  categoryL2: '42',
}

describe('validatePublishForm', () => {
  it('全部通过时返回归一化后的值', () => {
    const { errors, values } = validatePublishForm(VALID)
    expect(errors).toEqual({
      name: undefined,
      price: undefined,
      stock: undefined,
      category: undefined,
    })
    expect(values).toEqual({ name: '手机', price: 999.5, stock: 5, categoryId: '42' })
  })

  it('名称为空 → 请填写商品名称；名称违规 → 名称规则文案', () => {
    expect(validatePublishForm({ ...VALID, name: '  ' }).errors.name).toBe(M10.nameRequired)
    expect(validatePublishForm({ ...VALID, name: '<b>' }).errors.name).toBe(M10.nameInvalid)
    expect(validatePublishForm({ ...VALID, name: '' }).values).toBeNull()
  })

  it('价格为空 → 格式文案；价格越界 → 价格规则文案', () => {
    expect(validatePublishForm({ ...VALID, price: '' }).errors.price).toBe(M10.priceRequired)
    expect(validatePublishForm({ ...VALID, price: '0' }).errors.price).toBe(M10.priceInvalid)
  })

  it('发布库存为 0 → 库存规则文案', () => {
    const { errors, values } = validatePublishForm({ ...VALID, stock: 0 })
    expect(errors.stock).toBe(M10.stockInvalid)
    expect(values).toBeNull()
  })

  it('缺一级 → 请填写一级类别；有一级缺二级 → 请选择二级类别', () => {
    expect(
      validatePublishForm({ ...VALID, categoryL1: null, categoryL2: null }).errors.category,
    ).toBe(M10.categoryL1Required)
    expect(validatePublishForm({ ...VALID, categoryL2: null }).errors.category).toBe(
      M10.categoryL2Required,
    )
  })
})