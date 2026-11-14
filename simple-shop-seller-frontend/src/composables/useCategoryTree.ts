import { ref } from 'vue'

import { listCategoryTree } from '../api/category'
import type { CategoryNodeData } from '../api/types'
import { errorMessage } from '../utils/error'

// 模块级单例（与 useToast/useTheme 同风格）：类别树跨页面共享，
// 级联选择器不再每次挂载都重新请求；类别增删改后由管理页 refresh 更新缓存。
const tree = ref<CategoryNodeData[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const error = ref<string | null>(null)
let loaded = false
let inflight: Promise<void> | null = null

async function fetchTree(): Promise<void> {
  loading.value = true
  loadFailed.value = false
  error.value = null
  try {
    tree.value = await listCategoryTree()
    loaded = true
  } catch (caught) {
    loadFailed.value = true
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

function runFetch(): Promise<void> {
  if (!inflight) {
    inflight = fetchTree().finally(() => {
      inflight = null
    })
  }
  return inflight
}

export function useCategoryTree() {
  /** 有缓存直接用；未加载或上次失败则拉取，并发调用共享同一请求。 */
  function ensureLoaded(): Promise<void> {
    if (loaded) {
      return Promise.resolve()
    }
    return runFetch()
  }

  /** 强制重新拉取（类别增删改后调用），共享缓存随之更新。 */
  function refresh(): Promise<void> {
    loaded = false
    return runFetch()
  }

  return { tree, loading, loadFailed, error, ensureLoaded, refresh }
}