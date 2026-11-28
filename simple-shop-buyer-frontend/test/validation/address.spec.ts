import { describe, expect, it } from 'vitest'

import { validateAddress } from '../../src/validation/address'

const ok = {
  receiver_name: '张三',
  receiver_phone: '13800138000',
  detail_address: '北京市海淀区中关村大街 1 号',
}

describe('validateAddress', () => {
  it('合法输入通过', () => {
    expect(validateAddress(ok)).toBe('')
  })

  it('首尾空白被 trim 后校验', () => {
    expect(
      validateAddress({
        receiver_name: '  张三  ',
        receiver_phone: ' 13800138000 ',
        detail_address: ' 中关村大街 1 号 ',
      }),
    ).toBe('')
  })

  it('收货人：空 / 超 50 字 / 换行 / 尖括号 / 控制字符', () => {
    expect(validateAddress({ ...ok, receiver_name: '   ' })).toBe('请填收货人')
    expect(validateAddress({ ...ok, receiver_name: 'a'.repeat(50) })).toBe('')
    expect(validateAddress({ ...ok, receiver_name: 'a'.repeat(51) })).toBe('收货人最多 50 字')
    expect(validateAddress({ ...ok, receiver_name: '张\n三' })).toBe(
      '收货人不能含换行、控制字符或尖括号',
    )
    expect(validateAddress({ ...ok, receiver_name: '张<b>三' })).toBe(
      '收货人不能含换行、控制字符或尖括号',
    )
    expect(validateAddress({ ...ok, receiver_name: '张\t三' })).toBe(
      '收货人不能含换行、控制字符或尖括号',
    )
  })

  it('长度按码点算：50 个 emoji 不算超长', () => {
    expect(validateAddress({ ...ok, receiver_name: '😀'.repeat(50) })).toBe('')
    expect(validateAddress({ ...ok, receiver_name: '😀'.repeat(51) })).toBe('收货人最多 50 字')
  })

  it('手机号：空 / 非法字符 / 全短横 / 数字不足 7 / 超 20 位', () => {
    expect(validateAddress({ ...ok, receiver_phone: '' })).toBe('请填手机号')
    expect(validateAddress({ ...ok, receiver_phone: 'abc1234' })).toBe(
      '手机号请填 7~20 位，只能用数字和短横',
    )
    // 比后端严的有意偏离：全短横虽匹配后端正则但等于没有电话
    expect(validateAddress({ ...ok, receiver_phone: '-------' })).toBe('手机号里至少要有 7 位数字')
    expect(validateAddress({ ...ok, receiver_phone: '123456-' })).toBe('手机号里至少要有 7 位数字')
    expect(validateAddress({ ...ok, receiver_phone: '1'.repeat(21) })).toBe('手机号最多 20 字')
    expect(validateAddress({ ...ok, receiver_phone: '138-0013-8000' })).toBe('')
  })

  it('详细地址：空 / 超 200 字 / 换行 / 尖括号', () => {
    expect(validateAddress({ ...ok, detail_address: '  ' })).toBe('请填详细地址')
    expect(validateAddress({ ...ok, detail_address: 'a'.repeat(200) })).toBe('')
    expect(validateAddress({ ...ok, detail_address: 'a'.repeat(201) })).toBe('详细地址最多 200 字')
    expect(validateAddress({ ...ok, detail_address: '一号\n二号' })).toBe(
      '详细地址不能含换行、控制字符或尖括号',
    )
    expect(validateAddress({ ...ok, detail_address: '<script>' })).toBe(
      '详细地址不能含换行、控制字符或尖括号',
    )
  })
})