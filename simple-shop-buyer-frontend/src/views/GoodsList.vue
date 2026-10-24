<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { fetchCategories, fetchGoods } from '../api/buyer'
import type { CategoryNode, GoodsSummary } from '../api/types'
import { describeError } from '../stores/auth'

const router = useRouter()

const keyword = ref('')
const l1Id = ref('')
const l2Id = ref('')
const page = ref(1)
const pageSize = 10

const items = ref<GoodsSummary[]>([])
const total = ref(0)
const categories = ref<CategoryNode[]>([])
const loading = ref(false)
const errorText = ref('')

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

/** 选了二级就用二级，只选一级就用一级；都没选则不带该参数。 */
function categoryParam(): string | undefined {
  return l2Id.value || l1Id.value || undefined
}

async function loadCategories() {
  try {
    categories.value = await fetchCategories()
  } catch {
    // 类别加载失败不阻塞列表；下拉留空即可。
  }
}

async function loadGoods() {
  loading.value = true
  errorText.value = ''
  try {
    const data = await fetchGoods({
      keyword: keyword.value.trim() || undefined,
      category_id: categoryParam(),
      page: page.value,
      page_size: pageSize,
    })
    items.value = data.items
    total.value = data.total
  } catch (err) {
    errorText.value = describeError(err)
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  loadGoods()
}

function changePage(next: number) {
  page.value = next
  loadGoods()
}

// 同时监听两级：改一级时清空二级，Vue 会把同一轮的两处变更合并成一次触发。
watch([l1Id, l2Id], () => {
  if (!l1Id.value) l2Id.value = ''
  search()
})

onMounted(() => {
  loadCategories()
  loadGoods()
})
</script>

<template>
  <div class="page stack">
    <div class="card">
      <div class="row">
        <input
          v-model="keyword"
          placeholder="搜索商品名称或描述"
          @keyup.enter="search"
        />
        <select v-model="l1Id" class="select">
          <option value="">一级类别</option>
          <option v-for="c in categories" :key="c.id" :value="c.id">
            {{ c.name }}
          </option>
        </select>
        <select v-model="l2Id" class="select" :disabled="!l1Id">
          <option value="">二级类别</option>
          <option v-for="c in l2Options" :key="c.id" :value="c.id">
            {{ c.name }}
          </option>
        </select>
        <button class="primary search-btn" @click="search">搜索</button>
      </div>
    </div>

    <p v-if="errorText" class="error">{{ errorText }}</p>
    <p v-else-if="loading" class="muted">加载中…</p>
    <p v-else-if="!items.length" class="muted">还没有在售的商品</p>

    <div v-else class="grid">
      <div
        v-for="g in items"
        :key="g.id"
        class="card goods-card"
        @click="router.push({ name: 'goods-detail', params: { id: g.id } })"
      >
        <img
          v-if="g.main_image_url"
          :src="g.main_image_url"
          :alt="g.name"
          class="thumb"
        />
        <div v-else class="thumb placeholder">暂无图片</div>
        <div class="info">
          <div class="name">{{ g.name }}</div>
          <div class="price">¥{{ g.price }}</div>
          <div class="muted">
            {{ g.category?.l1_name ?? '未分类' }} / {{ g.category?.l2_name ?? '' }}
          </div>
        </div>
      </div>
    </div>

    <div v-if="total > pageSize" class="row pager">
      <button :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
      <span class="muted">第 {{ page }} 页 / 共 {{ Math.ceil(total / pageSize) }} 页</span>
      <button :disabled="page * pageSize >= total" @click="changePage(page + 1)">
        下一页
      </button>
    </div>
  </div>
</template>

<style scoped>
.select {
  padding: 6px 10px;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: #fff;
  min-width: 132px;
  flex-shrink: 0;
}

.select:disabled {
  color: var(--color-text-weak);
  background: #f7f8fa;
}

/* 搜索按钮不被输入框挤压，文字不换行 */
.search-btn {
  flex-shrink: 0;
  white-space: nowrap;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}

.goods-card {
  padding: 0;
  overflow: hidden;
  cursor: pointer;
}

.thumb {
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  display: block;
  background: #f1f2f4;
}

.thumb.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-weak);
}

.info {
  padding: 10px 12px;
}

.name {
  font-weight: 500;
}

.price {
  color: var(--color-danger);
  font-weight: 600;
}

.pager {
  justify-content: center;
}
</style>
