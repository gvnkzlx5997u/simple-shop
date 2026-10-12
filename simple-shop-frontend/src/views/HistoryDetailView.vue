<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { getHistoryDetail } from '../api/history'
import type { HistoryGoodsDetailData } from '../api/types'
import HistoryIntentionTable from '../components/HistoryIntentionTable.vue'
import ProductCard from '../components/ProductCard.vue'
import { errorMessage } from '../utils/error'

const route = useRoute()

const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : ''))
const data = ref<HistoryGoodsDetailData | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    data.value = await getHistoryDetail(id.value)
  } catch (caught) {
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page__header">
      <h1 class="page__title">历史商品详情</h1>
      <RouterLink to="/history" class="btn">返回列表</RouterLink>
    </div>

    <p v-if="error" class="page__error">{{ error }}</p>
    <div v-else-if="loading" class="card muted">加载中…</div>

    <template v-else-if="data">
      <ProductCard :goods="data" mode="readonly" />

      <section class="section">
        <h2 class="section__title">意向名单</h2>
        <HistoryIntentionTable :items="data.intentions" />
      </section>
    </template>
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

.section {
  margin-top: 24px;
}

.section__title {
  margin-bottom: 12px;
  font-size: 16px;
}
</style>
