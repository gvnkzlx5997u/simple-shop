import { describe, expect, it } from 'vitest'

import { ApiError } from '@/api/http'
import { ApiCode } from '@/api/types'
import type { PageResponse } from '@/api/types'
import { usePagedList } from '@/composables/usePagedList'

function pageOf(items: string[], total = items.length): PageResponse<string> {
  return { items, total, page: 1, page_size: 10 }
}

const flush = (): Promise<void> => new Promise((resolve) => setTimeout(resolve, 0))

describe('usePagedList', () => {
  it('load 成功后写入 items/total 并复位 loading', async () => {
    const list = usePagedList(async () => pageOf(['a', 'b'], 2))
    await list.load()
    expect(list.items.value).toEqual(['a', 'b'])
    expect(list.total.value).toBe(2)
    expect(list.loading.value).toBe(false)
    expect(list.error.value).toBeNull()
  })

  it('fetcher 收到当前页码', async () => {
    const seen: number[] = []
    const list = usePagedList(
      async (currentPage) => {
        seen.push(currentPage)
        return pageOf([])
      },
      { initialPage: 3 },
    )
    await list.load()
    expect(seen).toEqual([3])
  })

  it('失败时映射错误文案，保留上一次的数据', async () => {
    let fail = false
    const list = usePagedList(async () => {
      if (fail) {
        throw new ApiError(ApiCode.INTERNAL_ERROR, 'boom')
      }
      return pageOf(['a'])
    })
    await list.load()
    fail = true
    await list.load()
    expect(list.error.value).toBe('服务端内部错误，请稍后重试')
    expect(list.items.value).toEqual(['a'])
    expect(list.loading.value).toBe(false)
  })

  it('乱序响应只落地最新一次', async () => {
    const pending: { resolve: (value: PageResponse<string>) => void }[] = []
    const list = usePagedList(
      () =>
        new Promise((resolve) => {
          pending.push({ resolve })
        }),
    )
    void list.load()
    void list.load()
    pending[1]!.resolve(pageOf(['new']))
    await flush()
    pending[0]!.resolve(pageOf(['old']))
    await flush()
    expect(list.items.value).toEqual(['new'])
    expect(list.loading.value).toBe(false)
  })

  it('initialPage 非法值收敛为 1，小数向下取整', () => {
    const cases: [number, number][] = [
      [0, 1],
      [-3, 1],
      [NaN, 1],
      [2.7, 2],
      [5, 5],
    ]
    for (const [raw, expected] of cases) {
      const list = usePagedList(async () => pageOf([]), { initialPage: raw })
      expect(list.page.value).toBe(expected)
    }
  })
})