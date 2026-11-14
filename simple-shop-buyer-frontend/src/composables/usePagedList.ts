import { computed, ref } from 'vue'

import type { PageData } from '../api/types'
import { describeError } from '../stores/auth'

/**
 * 分页列表的公共状态与加载。
 *
 * 之前商品列表、我的订单、我的收藏三处各写了一遍「page / total / 加载 / 错误 / 翻页」，
 * 其中订单与收藏还各写了一遍「当前页被删空自动回退」。这个 composable 把它们收成一份。
 *
 * **筛选条件不进这里**：fetcher 闭包引用调用方自己的筛选状态（关键字、类别、订单状态），
 * 只接收页码——这样换筛选的逻辑仍然留在页面里，composable 不需要知道筛的是什么。
 *
 * 三处现状存在细微差异，用选项保留，避免重构顺手改了行为：
 * - 商品列表：没有「空页回退」，失败时连 total 一起归零（分页条随之隐藏）；
 * - 订单 / 收藏：有「空页回退」，失败时保留上一次的 total。
 *
 * 与卖家端同名 composable 的差别：那份带请求竞态守卫（useRequestSequence），
 * 买家端这三个页面在加载期间都会禁用翻页与筛选入口，不会出现旧响应覆盖新响应，
 * 因此这里没有引入守卫——**这是有意为之，不是漏做**。
 */
export interface PagedListOptions {
  /** 每页条数，只用于算总页数；实际请求参数由调用方的 fetcher 传。 */
  pageSize: number
  /** 初始页码，非法值收敛为 1。 */
  initialPage?: number
  /**
   * 当前页被别处删空时，自动退到真正的最后一页（最多回退一次）。
   * 订单与收藏需要：取消订单、取消收藏会让当前页变空；商品列表不需要。
   */
  emptyPageFallback?: boolean
  /** 加载失败时是否把 total 归零。商品列表为 true，订单与收藏为 false（保留上次的值）。 */
  resetTotalOnError?: boolean
}

export function usePagedList<T>(
  fetcher: (page: number) => Promise<PageData<T>>,
  options: PagedListOptions,
) {
  const items = ref<T[]>([])
  const total = ref(0)
  const page = ref(normalizePage(options.initialPage))
  const loading = ref(false)
  const errorText = ref('')

  const totalPages = computed(() => Math.max(1, Math.ceil(total.value / options.pageSize)))
  const canPrev = computed(() => page.value > 1 && !loading.value)
  const canNext = computed(() => page.value < totalPages.value && !loading.value)
  /**
   * 是否需要显示分页条：只有超过一页时才有意义。
   *
   * 之前三处不一致——商品列表用 `total > pageSize` 自己判，订单与收藏只要有数据就画一条，
   * 于是「只有 1 页」时也会出现一条两个按钮都是灰的横条，看着像坏了。现在统一到这一处。
   */
  const hasPager = computed(() => total.value > options.pageSize)

  async function load(): Promise<void> {
    loading.value = true
    errorText.value = ''
    try {
      // 不开启空页回退时，循环第一轮就会 return，等价于只请求一次。
      for (let guard = 0; guard < 2; guard += 1) {
        const data = await fetcher(page.value)
        items.value = data.items ?? []
        total.value = data.total ?? 0
        if (!options.emptyPageFallback) return
        const lastPage = Math.max(1, Math.ceil(total.value / options.pageSize))
        if (items.value.length > 0 || page.value <= lastPage) return
        // 例如第 2 页的订单全被取消，只剩 1 页数据：退到第 1 页再拉一次，
        // 否则页面会显示「还没有订单」，而其实第 1 页还有。
        page.value = lastPage
      }
    } catch (err) {
      errorText.value = describeError(err)
      items.value = []
      if (options.resetTotalOnError) {
        total.value = 0
      }
    } finally {
      loading.value = false
    }
  }

  /** 换筛选、重新搜索时用：回到第 1 页再拉，避免停在一个不存在的页码上。 */
  function reloadFromFirstPage(): void {
    page.value = 1
    void load()
  }

  /** 单纯翻页。滚动位置、提示清理等副作用留在页面里做。 */
  function goPage(nextPage: number): void {
    page.value = nextPage
    void load()
  }

  return {
    items,
    total,
    page,
    loading,
    errorText,
    totalPages,
    canPrev,
    canNext,
    hasPager,
    load,
    reloadFromFirstPage,
    goPage,
  }
}

function normalizePage(raw: number | undefined): number {
  const value = Math.trunc(Number(raw))
  return Number.isFinite(value) && value >= 1 ? value : 1
}
