import { describe, expect, it } from 'vitest'

import { ApiCode } from '@/api/types'
import type { DescriptionBlockRequest } from '@/api/types'
import {
  validateAdjustStock,
  validateCategoryId,
  validateDescriptionBlocks,
  validateGoodsName,
  validateImageSelection,
  validatePrice,
  validatePublishStock,
} from '@/validation/goods'

describe('validateGoodsName', () => {
  it('空值与纯空白拒绝为 50002', () => {
    for (const input of [null, undefined, '', '   ']) {
      expect(validateGoodsName(input)).toEqual({ ok: false, code: ApiCode.PARAM_INVALID })
    }
  })

  it('返回 trim 后的值', () => {
    expect(validateGoodsName(' 手机 ')).toEqual({ ok: true, value: '手机' })
  })

  it('按码点计数：50 个 emoji 通过，51 个拒绝为 20012', () => {
    expect(validateGoodsName('😀'.repeat(50)).ok).toBe(true)
    expect(validateGoodsName('😀'.repeat(51))).toEqual({
      ok: false,
      code: ApiCode.GOODS_NAME_INVALID,
    })
  })

  it('拒绝尖括号与换行（\\n 与 \\r 都算）', () => {
    for (const input of ['<b>', 'a>b', 'a\nb', 'a\rb']) {
      expect(validateGoodsName(input)).toEqual({ ok: false, code: ApiCode.GOODS_NAME_INVALID })
    }
  })
})

describe('validatePrice', () => {
  it('空值与形状非法拒绝为 50002', () => {
    const inputs = [
      null,
      undefined,
      '',
      '  ',
      'abc',
      '1,000',
      '¥1',
      '1 2',
      'NaN',
      'Infinity',
      '1e',
      'e2',
      '1.2.3',
      '--1',
      '1e2.5',
      '.',
    ]
    for (const input of inputs) {
      expect(validatePrice(input)).toEqual({ ok: false, code: ApiCode.PARAM_INVALID })
    }
  })

  it('零、负数与超过两位小数拒绝为 20014', () => {
    for (const input of ['0', '0.00', '-0', '-1', '-0.01', '0.001', '1.234', '1e-3', '0e2']) {
      expect(validatePrice(input)).toEqual({ ok: false, code: ApiCode.PRICE_INVALID })
    }
  })

  it('超过上限 999999.99 拒绝为 20014（含超大指数，不得构造巨大整数）', () => {
    for (const input of ['1000000', '999999.999', '1e6', '1e100', '9'.repeat(30)]) {
      expect(validatePrice(input)).toEqual({ ok: false, code: ApiCode.PRICE_INVALID })
    }
  })

  const accepted: [string, string][] = [
    ['1', '1.00'],
    ['0.01', '0.01'],
    ['999999.99', '999999.99'],
    [' 12.5 ', '12.50'],
    ['+3', '3.00'],
    ['.5', '0.50'],
    ['5.', '5.00'],
    ['00.5', '0.50'],
    ['1e2', '100.00'],
    ['1E2', '100.00'],
    ['1.5e1', '15.00'],
    ['1e-2', '0.01'],
    ['0.1e1', '1.00'],
    ['1.234e2', '123.40'],
    ['9.99e5', '999000.00'],
    ['9.9999999e5', '999999.99'],
  ]
  it.each(accepted)('接受 %s，归一化为两位小数 %s', (input, expected) => {
    expect(validatePrice(input)).toEqual({ ok: true, value: expected })
  })
})

describe('validatePublishStock', () => {
  it('≥1 的整数通过', () => {
    expect(validatePublishStock(1)).toEqual({ ok: true, value: 1 })
    expect(validatePublishStock(999)).toEqual({ ok: true, value: 999 })
  })

  it('0、负数、小数与非数字拒绝为 20015', () => {
    for (const input of [0, -1, 1.5, NaN, Infinity, '1', null, undefined, true]) {
      expect(validatePublishStock(input)).toEqual({ ok: false, code: ApiCode.STOCK_INVALID })
    }
  })
})

describe('validateAdjustStock', () => {
  it('0 也合法：在售商品清零触发库存耗尽下架', () => {
    expect(validateAdjustStock(0)).toEqual({ ok: true, value: 0 })
    expect(validateAdjustStock(5)).toEqual({ ok: true, value: 5 })
  })

  it('负数、小数与非数字拒绝为 20015', () => {
    for (const input of [-1, 0.5, '0', null, undefined]) {
      expect(validateAdjustStock(input)).toEqual({ ok: false, code: ApiCode.STOCK_INVALID })
    }
  })
})

describe('validateCategoryId', () => {
  it('空值拒绝为 50002，非空返回 trim 后的值', () => {
    for (const input of [null, undefined, '', '  ']) {
      expect(validateCategoryId(input)).toEqual({ ok: false, code: ApiCode.PARAM_INVALID })
    }
    expect(validateCategoryId(' 42 ')).toEqual({ ok: true, value: '42' })
  })
})

describe('validateDescriptionBlocks', () => {
  const text = (value: string | null, url: string | null = null): DescriptionBlockRequest => ({
    type: 'TEXT',
    text: value,
    url,
  })
  const media = (
    type: 'IMAGE' | 'VIDEO',
    url: string | null,
    extra: Partial<DescriptionBlockRequest> = {},
  ): DescriptionBlockRequest => ({ type, text: null, url, ...extra })

  it('空数组合法', () => {
    expect(validateDescriptionBlocks([])).toEqual({ ok: true, value: [] })
  })

  it('文本块：空文本、尖括号、携带 url 都拒绝为 20013', () => {
    const blocks = [
      [text(null)],
      [text('')],
      [text('   ')],
      [text('<b>')],
      [text('好', '/media/x.jpg')],
    ]
    for (const input of blocks) {
      expect(validateDescriptionBlocks(input)).toEqual({
        ok: false,
        code: ApiCode.GOODS_DESCRIPTION_INVALID,
      })
    }
  })

  it('文本合计按码点计数：1000 通过，1001 拒绝', () => {
    expect(validateDescriptionBlocks([text('😀'.repeat(1000))]).ok).toBe(true)
    expect(validateDescriptionBlocks([text('😀'.repeat(1001))])).toEqual({
      ok: false,
      code: ApiCode.GOODS_DESCRIPTION_INVALID,
    })
  })

  it('文本合计跨块累计：600+400 通过，600+401 拒绝', () => {
    const ok = validateDescriptionBlocks([text('字'.repeat(600)), text('字'.repeat(400))])
    expect(ok.ok).toBe(true)
    expect(validateDescriptionBlocks([text('字'.repeat(600)), text('字'.repeat(401))])).toEqual({
      ok: false,
      code: ApiCode.GOODS_DESCRIPTION_INVALID,
    })
  })

  it('媒体块：url 必须指向 /media/，不得携带文本', () => {
    expect(validateDescriptionBlocks([media('IMAGE', '/media/a.jpg')]).ok).toBe(true)
    expect(validateDescriptionBlocks([media('VIDEO', '/media/a.mp4')]).ok).toBe(true)
    const invalid = [
      [media('IMAGE', null)],
      [media('IMAGE', '')],
      [media('IMAGE', 'https://cdn.example.com/a.jpg')],
      [media('VIDEO', '/media/a.mp4', { text: '说明' })],
    ]
    for (const input of invalid) {
      expect(validateDescriptionBlocks(input)).toEqual({
        ok: false,
        code: ApiCode.GOODS_DESCRIPTION_INVALID,
      })
    }
  })

  it('通过时原样返回块数组', () => {
    const blocks = [text('你好'), media('IMAGE', '/media/a.jpg')]
    expect(validateDescriptionBlocks(blocks)).toEqual({ ok: true, value: blocks })
  })
})

describe('validateImageSelection', () => {
  const gallery = (count: number): string[] =>
    Array.from({ length: count }, (_, i) => `/media/g${i}.jpg`)

  it('无主图无图集合法', () => {
    expect(validateImageSelection(null, [])).toEqual({ ok: true, value: undefined })
  })

  it('有主图、图集 ≤8 张合法', () => {
    expect(validateImageSelection('/media/main.jpg', gallery(8))).toEqual({
      ok: true,
      value: undefined,
    })
  })

  it('有图集必须先有主图：20018', () => {
    expect(validateImageSelection(null, gallery(1))).toEqual({
      ok: false,
      code: ApiCode.MAIN_IMAGE_REQUIRED,
    })
    expect(validateImageSelection('   ', gallery(1))).toEqual({
      ok: false,
      code: ApiCode.MAIN_IMAGE_REQUIRED,
    })
  })

  it('图集超过 8 张：20017', () => {
    expect(validateImageSelection('/media/main.jpg', gallery(9))).toEqual({
      ok: false,
      code: ApiCode.IMAGE_LIMIT_EXCEEDED,
    })
  })

  it('url 必须指向 /media/ 且不得重复（主图与图集、图集内部）：20016', () => {
    const invalid: [string | null, string[]][] = [
      ['/media/main.jpg', ['/media/main.jpg']],
      ['/media/main.jpg', ['/media/a.jpg', '/media/a.jpg']],
      ['https://cdn.example.com/main.jpg', []],
      ['/media/main.jpg', ['https://cdn.example.com/a.jpg']],
    ]
    for (const [main, urls] of invalid) {
      expect(validateImageSelection(main, urls)).toEqual({
        ok: false,
        code: ApiCode.IMAGE_INVALID,
      })
    }
  })
})