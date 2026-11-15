<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { fetchCategories, fetchGoods } from '../api/buyer'
import type { CategoryNode, GoodsSummary } from '../api/types'
import Pagination from '../components/common/Pagination.vue'
import GoodsCard from '../components/goods/GoodsCard.vue'
import { usePagedList } from '../composables/usePagedList'

const keyword = ref('')
const l1Id = ref('')
const l2Id = ref('')
// 12 = 列表 3 行 × 4 列刚好排满一屏，不留残缺。
// 后端 simplexhop.pagination.max-size = 100，超过会返回 400。
const pageSize = 12

const categories = ref<CategoryNode[]>([])

/**
 * 分页状态交给公共 composable;筛选条件仍留在页面里，
 * fetcher 通过闭包读当前的 keyword / 类别，只把页码交给它。
 */
const { items, total, page, loading, errorText, load, reloadFromFirstPage, goPage } =
  usePagedList<GoodsSummary>(
    (pageNo) =>
      fetchGoods({
        keyword: keyword.value.trim() || undefined,
        category_id: categoryParam(),
        page: pageNo,
        page_size: pageSize,
      }),
    // 商品列表的特点：失败时连 total 一起归零，分页条随之隐藏。
    { pageSize, resetTotalOnError: true },
  )

/**
 * 两级类别分开筛选。
 * 后端 GoodsRepository 的查询支持 categoryLevel：传一级 id 就筛该一级下所有商品，
 * 传二级 id 就筛该二级。所以这里只要「二级优先、否则一级」地传一个 id 即可。
 */
const l2Options = computed<CategoryNode[]>(() => {
  if (!l1Id.value) return []
  const l1 = categories.value.find((c) => c.id === l1Id.value)
  return l1 ? l1.children : []
})

/** 选了二级就用二级，只选一级就用一级;都没选则不带该参数。 */
function categoryParam(): string | undefined {
  return l2Id.value || l1Id.value || undefined
}

async function loadCategories() {
  try {
    categories.value = await fetchCategories()
  } catch {
    // 类别加载失败不阻塞列表;下拉留空即可。
  }
}

function search() {
  // 换关键字或类别必须回到第 1 页。
  reloadFromFirstPage()
}

/** 清除搜索与类别筛选，回到全部商品第 1 页。 */
function clearFilters() {
  keyword.value = ''
  l1Id.value = ''
  l2Id.value = ''
  reloadFromFirstPage()
}

function changePage(next: number) {
  goPage(next)
}

/**
 * 切换一级类别时必须无条件清空二级。
 * 之前用 `if (!l1Id.value)` 只在"清空一级"时清二级，
 * 结果一级从「图书」改成「家电」时 l2Id 还留着「技术书」，
 * 查询就带着旧二级 id 去了 —— 下拉框看着重置了，实际筛选仍按旧类别。
 */
function onL1Change() {
  l2Id.value = ''
  search()
}

function onL2Change() {
  search()
}

onMounted(() => {
  void loadCategories()
  void load()
})
</script>

<template>
  <div class="mx-auto max-w-[960px] space-y-3 p-4">
    <div class="panel">
      <!-- 宽屏单行;窄屏搜索输入独占一行，类别与按钮作为整组另起一行，避免标签与下拉错位。 -->
      <div class="flex flex-wrap items-center gap-3">
        <input
          v-model="keyword"
          class="input w-full min-w-[180px] sm:w-auto sm:flex-1"
          placeholder="搜索商品名称或描述"
          @keyup.enter="search"
        />
        <div class="flex w-full flex-wrap items-center gap-3 sm:w-auto">
          <label class="flex items-center gap-2 whitespace-nowrap">
            <span class="text-sm text-base-content/60">一级类别</span>
            <select v-model="l1Id" class="select w-[118px]" aria-label="一级类别" @change="onL1Change">
              <option value="">全选</option>
              <option v-for="c in categories" :key="c.id" :value="c.id">
                {{ c.name }}
              </option>
            </select>
          </label>
          <label class="flex items-center gap-2 whitespace-nowrap">
            <span class="text-sm text-base-content/60">二级类别</span>
            <select
              v-model="l2Id"
              class="select w-[118px]"
              aria-label="二级类别"
              :disabled="!l1Id"
              @change="onL2Change"
            >
              <option value="">全选</option>
              <option v-for="c in l2Options" :key="c.id" :value="c.id">
                {{ c.name }}
              </option>
            </select>
          </label>
          <div class="flex items-center gap-2">
            <button class="btn btn-primary search-btn" @click="search">搜索</button>
            <button class="btn btn-ghost" @click="clearFilters">清除</button>
          </div>
        </div>
      </div>
    </div>

    <p v-if="errorText" class="text-error">{{ errorText }}</p>
    <p v-else-if="loading" class="muted">加载中…</p>
    <p v-else-if="!items.length" class="muted">还没有在售的商品</p>

    <div v-else class="grid grid-cols-[repeat(auto-fill,minmax(200px,1fr))] gap-3">
      <GoodsCard v-for="g in items" :key="g.id" :goods="g" />
    </div>

    <!-- 页码始终告诉用户「现在在第几页」，按钮只在真有多页时出现（Pagination 内部处理）。 -->
    <Pagination
      v-if="items.length"
      :page="page"
      :page-size="pageSize"
      :total="total"
      unit="件"
      :disabled="loading"
      @update:page="changePage"
    />
  </div>
</template>