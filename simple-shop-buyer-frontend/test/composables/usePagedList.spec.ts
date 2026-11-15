import { describe, expect, it } from 'vitest'

import { ApiError } from '../../src/api/http'
import { ApiCode } from '../../src/api/types'
import type { PageData } from '../../src/api/types'
import { usePagedList } from '../../src/composables/usePagedList'

function pageOf(items: string[], total = items.length, page = 1): PageData<string> {
  return { items, total, page, page_size: 10 }
}

describe('usePagedList', () => {
  it('load 成功写入 items/total 并复位 loading 与错误', async () => {
    const list = usePagedList(async () => pageOf(['a', 'b'], 2), { pageSize: 10 })
    await list.load()
    expect(list.items.value).toEqual(['a', 'b'])
    expect(list.total.value).toBe(2)
    expect(list.loading.value).toBe(false)
    expect(list.errorText.value).toBe('')
  })

  it('fetcher 收到当前页码', async () => {
    const seen: number[] = []
    const list = usePagedList(
      async (p) => {
        seen.push(p)
        return pageOf([])
      },
      { pageSize: 10, initialPage: 3 },
    )
    await list.load()
    expect(seen).toEqual([3])
  })

  it('失败映射错误文案;resetTotalOnError 决定是否归零 total', async () => {
    let fail = false
    const fetcher = async () => {
      if (fail) throw new ApiError(ApiCode.INTERNAL_ERROR, 'boom')
      return pageOf(['a'], 1)
    }
    const reset = usePagedList(fetcher, { pageSize: 10, resetTotalOnError: true })
    await reset.load()
    fail = true
    await reset.load()
    expect(reset.errorText.value).toBe('服务器出错了，请稍后再试')
    expect(reset.items.value).toEqual([])
    expect(reset.total.value).toBe(0)

    fail = false
    const keep = usePagedList(fetcher, { pageSize: 10, resetTotalOnError: false })
    await keep.load()
    fail = true
    await keep.load()
    expect(keep.total.value).toBe(1) // 保留上一次 total
    expect(keep.items.value).toEqual([])
  })

  it('emptyPageFallback：当前页被删空自动退到最后一页且只重试一次', async () => {
    const seen: number[] = []
    const list = usePagedList(
      async (p) => {
        seen.push(p)
        if (p === 2) return pageOf([], 10, 2) // 第 2 页空，但总共 10 条 = 1 页
        return pageOf(['a'], 10, 1)
      },
      { pageSize: 10, initialPage: 2, emptyPageFallback: true },
    )
    await list.load()
    expect(seen).toEqual([2, 1])
    expect(list.page.value).toBe(1)
    expect(list.items.value).toEqual(['a'])
  })

  it('不开 emptyPageFallback 时空页也只请求一次', async () => {
    const seen: number[] = []
    const list = usePagedList(
      async (p) => {
        seen.push(p)
        return pageOf([], 10, p)
      },
      { pageSize: 10, initialPage: 2, emptyPageFallback: false },
    )
    await list.load()
    expect(seen).toEqual([2])
  })

  it('hasPager / canPrev / canNext 随 total 与 page 变化', async () => {
    const list = usePagedList(async () => pageOf(['a'], 25), { pageSize: 10 })
    await list.load()
    expect(list.hasPager.value).toBe(true)
    expect(list.totalPages.value).toBe(3)
    expect(list.canPrev.value).toBe(false)
    expect(list.canNext.value).toBe(true)
    list.goPage(3)
    await new Promise((r) => setTimeout(r, 0))
    expect(list.canNext.value).toBe(false)
    expect(list.canPrev.value).toBe(true)
  })

  it('单页时 hasPager 为 false（不画灰按钮）', async () => {
    const list = usePagedList(async () => pageOf(['a'], 5), { pageSize: 10 })
    await list.load()
    expect(list.hasPager.value).toBe(false)
  })

  it('reloadFromFirstPage 回到第 1 页', async () => {
    const seen: number[] = []
    const list = usePagedList(
      async (p) => {
        seen.push(p)
        return pageOf(['a'], 30, p)
      },
      { pageSize: 10, initialPage: 3 },
    )
    await list.load()
    list.reloadFromFirstPage()
    await new Promise((r) => setTimeout(r, 0))
    expect(list.page.value).toBe(1)
    expect(seen).toEqual([3, 1])
  })

  it('initialPage 非法值收敛为 1，小数向下取整', () => {
    const cases: Array<[number, number]> = [
      [0, 1],
      [-3, 1],
      [NaN, 1],
      [2.7, 2],
      [5, 5],
    ]
    for (const [raw, expected] of cases) {
      const list = usePagedList(async () => pageOf([]), { pageSize: 10, initialPage: raw })
      expect(list.page.value).toBe(expected)
    }
  })
})