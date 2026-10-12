<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { listHistory } from '../api/history'
import type { HistoryPageData } from '../api/types'
import HistoryTable from '../components/HistoryTable.vue'
import Pagination from '../components/Pagination.vue'
import { errorMessage } from '../utils/error'

const PAGE_SIZE = 10

const router = useRouter()

const data = ref<HistoryPageData | null>(null)
const page = ref(1)
const loading = ref(false)
const error = ref<string | null>(null)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    data.value = await listHistory(page.value, PAGE_SIZE)
  } catch (caught) {
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

onMounted(load)

function onPageChange(next: number): void {
  page.value = next
  void load()
}

function onViewDetail(id: string): void {
  void router.push({ name: 'history-detail', params: { id } })
}
</script>

<template>
  <div class="page">
    <h1 class="page__title">历史商品</h1>

    <p v-if="error" class="page__error">{{ error }}</p>

    <HistoryTable :items="data?.items ?? []" :loading="loading" @view-detail="onViewDetail" />

    <Pagination
      v-if="(data?.total ?? 0) > PAGE_SIZE"
      class="page__pagination"
      :page="page"
      :page-size="PAGE_SIZE"
      :total="data?.total ?? 0"
      :disabled="loading"
      @update:page="onPageChange"
    />
  </div>
</template>

<style scoped>
.page__title {
  margin-bottom: 16px;
}

.page__error {
  margin: 0 0 16px;
  padding: 10px 12px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  background: #fef2f2;
  color: var(--danger);
}

.page__pagination {
  margin-top: 12px;
}
</style>
