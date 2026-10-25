<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

import { listGoods } from '../api/goods'
import type { GoodsStatus, GoodsSummaryData } from '../api/types'
import Pagination from '../components/common/Pagination.vue'
import CategoryCascadeSelect from '../components/goods/CategoryCascadeSelect.vue'
import GoodsCard from '../components/goods/GoodsCard.vue'
import { GOODS_STATUS_LABELS } from '../constants/labels'
import { M10 } from '../constants/messages'
import { errorMessage } from '../utils/error'

const PAGE_SIZE = 12

const items = ref<GoodsSummaryData[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const error = ref<string | null>(null)

const statusFilter = ref<GoodsStatus | ''>('')
const keywordInput = ref('')
const appliedKeyword = ref('')
const filterL1Id = ref<string | null>(null)
const filterL2Id = ref<string | null>(null)

const STATUS_OPTIONS = (Object.keys(GOODS_STATUS_LABELS) as GoodsStatus[]).map((value) => ({
  value,
  label: GOODS_STATUS_LABELS[value],
}))

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const data = await listGoods({
      status: statusFilter.value || undefined,
      // 后端接受一级或二级 id,一级会匹配其下全部二级。
      category_id: filterL2Id.value ?? filterL1Id.value ?? undefined,
      keyword: appliedKeyword.value || undefined,
      page: page.value,
      page_size: PAGE_SIZE,
    })
    items.value = data.items
    total.value = data.total
  } catch (caught) {
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

onMounted(load)

function applySearch(): void {
  appliedKeyword.value = keywordInput.value.trim()
  page.value = 1
  void load()
}

function resetFilters(): void {
  statusFilter.value = ''
  keywordInput.value = ''
  appliedKeyword.value = ''
  filterL1Id.value = null
  filterL2Id.value = null
  page.value = 1
  void load()
}

// 状态与类别变化即时生效;关键字经「搜索」按钮应用。
watch([statusFilter, filterL1Id, filterL2Id], () => {
  page.value = 1
  void load()
})

function onPageChange(next: number): void {
  page.value = next
  void load()
}
</script>

<template>
  <div class="page">
    <div class="page__header">
      <h1 class="page__title">商品列表</h1>
      <RouterLink to="/publish" class="btn btn--primary">发布商品</RouterLink>
    </div>

    <form class="card filters" @submit.prevent="applySearch">
      <select v-model="statusFilter" class="input filters__status" aria-label="状态筛选">
        <option value="">全部状态</option>
        <option v-for="option in STATUS_OPTIONS" :key="option.value" :value="option.value">
          {{ option.label }}
        </option>
      </select>
      <CategoryCascadeSelect
        v-model:l1-id="filterL1Id"
        v-model:l2-id="filterL2Id"
        mode="filter"
        class="filters__category"
      />
      <input
        v-model="keywordInput"
        class="input filters__keyword"
        type="search"
        placeholder="搜索商品名称"
        aria-label="搜索商品名称"
      />
      <button type="submit" class="btn" :disabled="loading">搜索</button>
      <button type="button" class="btn" :disabled="loading" @click="resetFilters">重置</button>
    </form>

    <p v-if="error" class="page__error">{{ error }}</p>
    <p v-if="loading" class="muted">{{ items.length === 0 ? '加载中…' : '刷新中…' }}</p>

    <div v-if="items.length > 0" class="goods-grid" :class="{ 'goods-grid--dimmed': loading }">
      <GoodsCard v-for="goods in items" :key="goods.id" :goods="goods" />
    </div>
    <div v-else-if="!loading && !error" class="card empty">
      <p>{{ M10.emptyGoods }}</p>
      <RouterLink to="/publish" class="btn btn--primary">发布商品</RouterLink>
    </div>

    <Pagination
      v-if="total > PAGE_SIZE"
      class="page__pagination"
      :page="page"
      :page-size="PAGE_SIZE"
      :total="total"
      :disabled="loading"
      @update:page="onPageChange"
    />
  </div>
</template>

<style scoped>
.page__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page__title {
  margin: 0;
}

.page__error {
  margin: 0 0 16px;
  padding: 10px 12px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  background: #fef2f2;
  color: var(--danger);
}

.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.filters__status {
  flex: 0 0 140px;
}

.filters__category {
  flex: 1 1 280px;
}

.filters__keyword {
  flex: 1 1 180px;
}

.goods-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.goods-grid--dimmed {
  opacity: 0.6;
}

.page__pagination {
  margin-top: 16px;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 48px;
}
</style>
