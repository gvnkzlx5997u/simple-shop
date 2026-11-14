import { ref } from 'vue'

import type { PageResponse } from '../api/types'
import { errorMessage } from '../utils/error'
import { useRequestSequence } from './useRequestSequence'

export interface PagedListOptions {
  /** 初始页码（通常从 URL 恢复）；非法值收敛为 1。 */
  initialPage?: number
}

/**
 * 分页列表的公共状态与加载：items/total/page/loading/error + 请求竞态守卫。
 * 筛选条件与 URL 同步由调用方负责：fetcher 闭包引用调用方的筛选状态，只接收页码。
 * 失败时保留上一次的数据（错误横幅已说明失败），不清空列表。
 */
export function usePagedList<T>(
  fetcher: (page: number) => Promise<PageResponse<T>>,
  options: PagedListOptions = {},
) {
  const items = ref<T[]>([])
  const total = ref(0)
  const page = ref(normalizePage(options.initialPage))
  const loading = ref(false)
  const error = ref<string | null>(null)
  const seq = useRequestSequence()

  async function load(): Promise<void> {
    const token = seq.begin()
    loading.value = true
    error.value = null
    try {
      const data = await fetcher(page.value)
      if (!seq.isCurrent(token)) {
        return
      }
      items.value = data.items
      total.value = data.total
    } catch (caught) {
      if (!seq.isCurrent(token)) {
        return
      }
      error.value = errorMessage(caught)
    } finally {
      if (seq.isCurrent(token)) {
        loading.value = false
      }
    }
  }

  return { items, total, page, loading, error, load }
}

function normalizePage(raw: number | undefined): number {
  const value = Math.trunc(Number(raw))
  return Number.isFinite(value) && value >= 1 ? value : 1
}