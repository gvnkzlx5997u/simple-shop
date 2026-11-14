import { describe, expect, it, vi } from 'vitest'

import type { CategoryNodeData } from '@/api/types'

vi.mock('@/api/category', () => ({ listCategoryTree: vi.fn() }))

const TREE: CategoryNodeData[] = [{ id: '1', name: '数码', children: [] }]

/** 缓存是模块级单例：每个用例重置模块注册表，拿到全新的缓存与 mock。 */
async function fresh() {
  vi.resetModules()
  const api = await import('@/api/category')
  const { useCategoryTree } = await import('@/composables/useCategoryTree')
  return { fetchTree: vi.mocked(api.listCategoryTree), useCategoryTree }
}

describe('useCategoryTree', () => {
  it('首次 ensureLoaded 拉取，命中缓存后不再请求', async () => {
    const { fetchTree, useCategoryTree } = await fresh()
    fetchTree.mockResolvedValue(TREE)
    const cat = useCategoryTree()
    await cat.ensureLoaded()
    await cat.ensureLoaded()
    expect(fetchTree).toHaveBeenCalledTimes(1)
    expect(cat.tree.value).toEqual(TREE)
    expect(cat.loadFailed.value).toBe(false)
  })

  it('并发 ensureLoaded 共享同一请求（单飞）', async () => {
    const { fetchTree, useCategoryTree } = await fresh()
    let resolveFetch!: (value: CategoryNodeData[]) => void
    fetchTree.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveFetch = resolve
        }),
    )
    const cat = useCategoryTree()
    const first = cat.ensureLoaded()
    const second = cat.ensureLoaded()
    resolveFetch(TREE)
    await Promise.all([first, second])
    expect(fetchTree).toHaveBeenCalledTimes(1)
    expect(cat.tree.value).toEqual(TREE)
  })

  it('失败置 loadFailed 与错误文案；再次 ensureLoaded 自动重试', async () => {
    const { fetchTree, useCategoryTree } = await fresh()
    fetchTree.mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce(TREE)
    const cat = useCategoryTree()
    await cat.ensureLoaded()
    expect(cat.loadFailed.value).toBe(true)
    expect(cat.error.value).toBe('操作失败，请稍后重试')
    await cat.ensureLoaded()
    expect(cat.loadFailed.value).toBe(false)
    expect(cat.error.value).toBeNull()
    expect(cat.tree.value).toEqual(TREE)
    expect(fetchTree).toHaveBeenCalledTimes(2)
  })

  it('refresh 强制重新拉取并更新共享树', async () => {
    const { fetchTree, useCategoryTree } = await fresh()
    fetchTree
      .mockResolvedValueOnce(TREE)
      .mockResolvedValueOnce([{ id: '2', name: '服饰', children: [] }])
    const cat = useCategoryTree()
    await cat.ensureLoaded()
    await cat.refresh()
    expect(fetchTree).toHaveBeenCalledTimes(2)
    expect(cat.tree.value[0]!.id).toBe('2')
  })

  it('不同调用点共享同一份缓存', async () => {
    const { fetchTree, useCategoryTree } = await fresh()
    fetchTree.mockResolvedValue(TREE)
    const a = useCategoryTree()
    await a.ensureLoaded()
    const b = useCategoryTree()
    await b.ensureLoaded()
    expect(fetchTree).toHaveBeenCalledTimes(1)
    expect(b.tree.value).toEqual(TREE)
  })
})