import { describe, expect, it } from 'vitest'

import { validateRegister } from '../../src/validation/register'

const ok = { username: 'buyer1', password: 'Abcd1234', phone: '13800138000' }

describe('validateRegister', () => {
  it('合法输入通过', () => {
    expect(validateRegister(ok)).toBe('')
  })

  it('用户名：空 / 超 50 / 空格 / 尖括号', () => {
    expect(validateRegister({ ...ok, username: '  ' })).toBe('请填用户名')
    expect(validateRegister({ ...ok, username: 'a'.repeat(51) })).toBe('用户名最多 50 个字符')
    expect(validateRegister({ ...ok, username: 'a'.repeat(50) })).toBe('')
    expect(validateRegister({ ...ok, username: 'has space' })).toBe('用户名不能包含空格、换行或尖括号')
    expect(validateRegister({ ...ok, username: 'a<b>' })).toBe('用户名不能包含空格、换行或尖括号')
  })

  it('密码：空 / 少于 8 位', () => {
    expect(validateRegister({ ...ok, password: '' })).toBe('请填密码')
    expect(validateRegister({ ...ok, password: 'Ab1' })).toBe('密码至少 8 位')
    expect(validateRegister({ ...ok, password: 'Abcd1234' })).toBe('')
  })

  it('电话：空 / 非法字符 / 全短横 / 数字不足 7', () => {
    expect(validateRegister({ ...ok, phone: '' })).toBe('请填电话')
    expect(validateRegister({ ...ok, phone: 'abc1234' })).toBe('电话请填 7~20 位，只能用数字和短横')
    // 比后端严的有意偏离：全短横虽匹配后端正则但等于没有电话
    expect(validateRegister({ ...ok, phone: '-------' })).toBe('电话里至少要有 7 位数字')
    expect(validateRegister({ ...ok, phone: '123456-' })).toBe('电话里至少要有 7 位数字')
    expect(validateRegister({ ...ok, phone: '138-0013-8000' })).toBe('')
  })

  it('首尾空白被 trim 后校验', () => {
    expect(validateRegister({ ...ok, username: '  buyer1  ' })).toBe('')
    expect(validateRegister({ ...ok, phone: ' 13800138000 ' })).toBe('')
  })
})