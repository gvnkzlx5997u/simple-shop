import { describe, expect, it } from 'vitest'

import {
  IMAGE_MAX_BYTES,
  MAX_AFTERSALE_IMAGES,
  validateAftersale,
  validateAftersaleImage,
} from '../../src/validation/aftersale'

const ok = { title: '收到时外壳有裂痕', description: '开箱就发现裂痕，希望退货退款。' }

describe('validateAftersale', () => {
  it('合法输入通过', () => {
    expect(validateAftersale(ok)).toBe('')
  })

  it('标题：空 / 超 100 字 / 换行 / 尖括号', () => {
    expect(validateAftersale({ ...ok, title: '  ' })).toBe('请填标题')
    expect(validateAftersale({ ...ok, title: 'a'.repeat(100) })).toBe('')
    expect(validateAftersale({ ...ok, title: 'a'.repeat(101) })).toBe('标题最多 100 字')
    expect(validateAftersale({ ...ok, title: '第一行\n第二行' })).toBe(
      '标题不能含换行、控制字符或尖括号',
    )
    expect(validateAftersale({ ...ok, title: '<b>裂痕</b>' })).toBe(
      '标题不能含换行、控制字符或尖括号',
    )
  })

  it('描述：空 / 超 1000 字 / 尖括号', () => {
    expect(validateAftersale({ ...ok, description: '' })).toBe('请填描述')
    expect(validateAftersale({ ...ok, description: 'a'.repeat(1000) })).toBe('')
    expect(validateAftersale({ ...ok, description: 'a'.repeat(1001) })).toBe('描述最多 1000 字')
    expect(validateAftersale({ ...ok, description: '<script>' })).toBe(
      '描述不能含尖括号或异常控制字符',
    )
  })

  it('描述允许换行与制表符（与标题不同）', () => {
    expect(validateAftersale({ ...ok, description: '第一条\n第二条\r\n第三条\t补充' })).toBe('')
  })

  it('描述仍拒绝其它控制字符', () => {
    expect(validateAftersale({ ...ok, description: '异常\u0000字符' })).toBe(
      '描述不能含尖括号或异常控制字符',
    )
  })

  it('长度按码点算', () => {
    expect(validateAftersale({ ...ok, title: '😀'.repeat(100) })).toBe('')
    expect(validateAftersale({ ...ok, title: '😀'.repeat(101) })).toBe('标题最多 100 字')
  })

  it('首尾空白 trim 后校验', () => {
    expect(validateAftersale({ title: '  裂痕  ', description: '  说明  ' })).toBe('')
  })
})

describe('validateAftersaleImage', () => {
  const file = { name: 'a.png', type: 'image/png', size: 1024 }

  it('JPG / PNG 通过', () => {
    expect(validateAftersaleImage(file)).toBe('')
    expect(validateAftersaleImage({ ...file, name: 'b.jpg', type: 'image/jpeg' })).toBe('')
  })

  it('其它类型拒绝', () => {
    expect(validateAftersaleImage({ ...file, name: 'c.gif', type: 'image/gif' })).toBe(
      '图片只能是 JPG 或 PNG',
    )
    expect(validateAftersaleImage({ ...file, name: 'd.pdf', type: 'application/pdf' })).toBe(
      '图片只能是 JPG 或 PNG',
    )
  })

  it('MIME 为空时按扩展名兜底', () => {
    expect(validateAftersaleImage({ ...file, type: '' })).toBe('')
    expect(validateAftersaleImage({ ...file, name: 'e.JPEG', type: '' })).toBe('')
    expect(validateAftersaleImage({ ...file, name: 'f.txt', type: '' })).toBe('图片只能是 JPG 或 PNG')
  })

  it('超过 5MB 拒绝，恰好 5MB 通过', () => {
    expect(validateAftersaleImage({ ...file, size: IMAGE_MAX_BYTES })).toBe('')
    expect(validateAftersaleImage({ ...file, size: IMAGE_MAX_BYTES + 1 })).toBe(
      '单张图片不能超过 5MB',
    )
  })

  it('上限常量是 9 张', () => {
    expect(MAX_AFTERSALE_IMAGES).toBe(9)
  })
})