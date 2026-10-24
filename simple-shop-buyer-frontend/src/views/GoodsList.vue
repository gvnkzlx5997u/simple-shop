<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { fetchCategories, fetchGoods } from '../api/buyer'
import type { CategoryNode, GoodsSummary } from '../api/types'
import { describeError } from '../stores/auth'

const router = useRouter()

const keyword = ref('')
const categoryId = ref('')
const page = ref(1)
const pageSize = 10

const items = ref<GoodsSummary[]>([])
const total = ref(0)
const categories = ref<CategoryNode[]>([])
const loading = ref(false)
const errorText = ref('')

/** 二级类别拉平成下拉选项，value 用二级 id（商品只挂二级）。 */
const flatCategories = ref<{ id: string; name: string; l1: string }[]>([])

async function loadCategories() {
  try {
    const tree = await fetchCategories()
    categories.value = tree
    flatCategories.value = tree.flatMap((l1) =>
      l1.children.map((l2) => ({ id: l2.id, name: l2.name, l1: l1.name })),
    )
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
      category_id: categoryId.value || undefined,
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

watch(categoryId, () => search())

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
        <select v-model="categoryId" class="select">
          <option value="">全部类别</option>
          <option v-for="c in flatCategories" :key="c.id" :value="c.id">
            {{ c.l1 }} / {{ c.name }}
          </option>
        </select>
        <button class="primary" @click="search">搜索</button>
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
          <div class="muted">{{ g.category.l1_name }} / {{ g.category.l2_name }}</div>
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
  min-width: 160px;
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
