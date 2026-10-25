import { request } from './http'
import type { CategoryData, CategoryNodeData } from './types'

const CATEGORIES_PATH = '/seller/categories'

/** 类别树（一级 → 二级）；卖家端与公共端同构，管理页用卖家接口即可。 */
export function listCategoryTree(): Promise<CategoryNodeData[]> {
  return request<CategoryNodeData[]>({ method: 'get', url: CATEGORIES_PATH })
}

/** 新增一级类别；同级同名被拒 20004。 */
export function createCategoryL1(name: string): Promise<CategoryData> {
  return request<CategoryData>({ method: 'post', url: CATEGORIES_PATH + '/l1', data: { name } })
}

/** 一级类别改名。 */
export function renameCategoryL1(id: string, name: string): Promise<CategoryData> {
  return request<CategoryData>({
    method: 'put',
    url: CATEGORIES_PATH + '/l1/' + encodeURIComponent(id),
    data: { name },
  })
}

/** 删除一级类别；仍有二级类别被拒 20005，仍被商品引用被拒 20006。 */
export function deleteCategoryL1(id: string): Promise<void> {
  return request<null>({
    method: 'delete',
    url: CATEGORIES_PATH + '/l1/' + encodeURIComponent(id),
  }).then(() => undefined)
}

/** 新增二级类别；挂在指定一级类别下，同级同名被拒 20004。 */
export function createCategoryL2(l1Id: string, name: string): Promise<CategoryData> {
  return request<CategoryData>({
    method: 'post',
    url: CATEGORIES_PATH + '/l2',
    data: { l1_id: l1Id, name },
  })
}

/** 二级类别改名。 */
export function renameCategoryL2(id: string, name: string): Promise<CategoryData> {
  return request<CategoryData>({
    method: 'put',
    url: CATEGORIES_PATH + '/l2/' + encodeURIComponent(id),
    data: { name },
  })
}

/** 删除二级类别；仍被商品引用被拒 20006。 */
export function deleteCategoryL2(id: string): Promise<void> {
  return request<null>({
    method: 'delete',
    url: CATEGORIES_PATH + '/l2/' + encodeURIComponent(id),
  }).then(() => undefined)
}
