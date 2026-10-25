<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { getHistoryDetail } from '../api/history'
import type { GoodsHistoryDetailData } from '../api/types'
import placeholderImage from '../assets/product.svg'
import StatusTag from '../components/common/StatusTag.vue'
import DescriptionBlocks from '../components/goods/DescriptionBlocks.vue'
import {
  FAIL_TYPE_LABELS,
  HISTORY_INTENTION_RESULT_LABELS,
  HISTORY_RESULT_LABELS,
} from '../constants/labels'
import type { TagTone } from '../constants/labels'
import { M10 } from '../constants/messages'
import { errorMessage } from '../utils/error'
import { formatDateTime, formatPrice } from '../utils/format'

const route = useRoute()

const historyId = computed(() => (typeof route.params.id === 'string' ? route.params.id : ''))

const data = ref<GoodsHistoryDetailData | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    data.value = await getHistoryDetail(historyId.value)
  } catch (caught) {
    error.value = errorMessage(caught)
    data.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(historyId, load)

// ---- 快照图集:默认主图(无主图取第一张),点击缩略图切换 ----

const selectedImageUrl = ref<string | null>(null)
const heroFailed = ref(false)

const defaultImageUrl = computed(() => {
  const images = data.value?.images ?? []
  return images.find((image) => image.is_main)?.url ?? images[0]?.url ?? null
})

const activeImageUrl = computed(() => selectedImageUrl.value ?? defaultImageUrl.value)

const heroImage = computed(() =>
  activeImageUrl.value && !heroFailed.value ? activeImageUrl.value : placeholderImage,
)

function selectImage(url: string): void {
  selectedImageUrl.value = url
  heroFailed.value = false
}

function onHeroError(): void {
  heroFailed.value = true
}

watch(historyId, () => {
  selectedImageUrl.value = null
  heroFailed.value = false
})

function resultTone(result: 'SUCCEEDED' | 'FAILED'): TagTone {
  return result === 'SUCCEEDED' ? 'success' : 'danger'
}
</script>

<template>
  <div class="page">
    <div class="page__header">
      <h1 class="page__title">历史商品详情</h1>
      <RouterLink to="/history" class="btn">返回历史列表</RouterLink>
    </div>

    <p v-if="error" class="page__error">
      {{ error }}
      <RouterLink to="/history">返回历史列表</RouterLink>
    </p>
    <div v-else-if="loading && !data" class="card muted">加载中…</div>

    <template v-else-if="data">
      <div class="card goods">
        <div class="goods__media">
          <img class="goods__main-image" :src="heroImage" :alt="data.name" @error="onHeroError" />
          <div v-if="data.images.length > 1" class="goods__gallery">
            <button
              v-for="image in data.images"
              :key="image.url"
              type="button"
              class="goods__thumb-btn"
              :class="{ 'goods__thumb-btn--active': image.url === activeImageUrl }"
              :title="image.is_main ? '主图（默认大图）' : '点击切换大图'"
              @click="selectImage(image.url)"
            >
              <img
                class="goods__thumb"
                :class="{ 'goods__thumb--main': image.is_main }"
                :src="image.url"
                :alt="image.is_main ? '主图' : '图集图片'"
                loading="lazy"
              />
            </button>
          </div>
        </div>

        <div class="goods__info">
          <div class="goods__title-row">
            <h2 class="goods__name">{{ data.name }}</h2>
            <StatusTag :label="HISTORY_RESULT_LABELS[data.result]" />
          </div>

          <dl class="goods__meta">
            <div class="goods__meta-row">
              <dt>价格</dt>
              <dd class="goods__price">{{ formatPrice(data.price) }}</dd>
            </div>
            <div class="goods__meta-row">
              <dt>类别</dt>
              <dd>{{ data.category_l1_name }} / {{ data.category_l2_name }}</dd>
            </div>
            <div class="goods__meta-row">
              <dt>发布时间</dt>
              <dd>{{ formatDateTime(data.create_at) }}</dd>
            </div>
            <div class="goods__meta-row">
              <dt>归档时间</dt>
              <dd>{{ formatDateTime(data.archived_at) }}</dd>
            </div>
          </dl>
        </div>
      </div>

      <section class="card section">
        <h2 class="section__title">商品描述（归档快照）</h2>
        <DescriptionBlocks v-if="data.description.length > 0" :blocks="data.description" />
        <p v-else class="muted">{{ M10.emptyDescription }}</p>
      </section>

      <section class="card section">
        <h2 class="section__title">意向名单</h2>
        <table class="data-table">
          <thead>
            <tr>
              <th>买家</th>
              <th>联系电话</th>
              <th>下单时间</th>
              <th>结果</th>
              <th>失败类型</th>
              <th>交易时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="data.intentions.length === 0">
              <td colspan="6" class="muted">{{ M10.emptyHistoryIntentions }}</td>
            </tr>
            <tr v-for="item in data.intentions" v-else :key="item.id">
              <td>{{ item.buyer_username }}</td>
              <td>{{ item.buyer_phone }}</td>
              <td>{{ formatDateTime(item.create_at) }}</td>
              <td>
                <StatusTag
                  :label="HISTORY_INTENTION_RESULT_LABELS[item.result]"
                  :tone="resultTone(item.result)"
                />
              </td>
              <td>{{ item.fail_type ? FAIL_TYPE_LABELS[item.fail_type] : '—' }}</td>
              <td>{{ formatDateTime(item.finish_at) }}</td>
            </tr>
          </tbody>
        </table>
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

.goods {
  display: flex;
  gap: 20px;
}

.goods__media {
  flex: 0 0 320px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.goods__main-image {
  width: 100%;
  aspect-ratio: 1 / 1;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid var(--border);
  background: #f3f4f6;
}

.goods__gallery {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.goods__thumb-btn {
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  border-radius: 4px;
  line-height: 0;
}

.goods__thumb-btn--active {
  box-shadow: 0 0 0 2px var(--primary);
  border-radius: 6px;
}

.goods__thumb {
  width: 48px;
  height: 48px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid var(--border);
}

.goods__thumb--main {
  border-color: var(--primary);
  border-width: 2px;
}

.goods__info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.goods__title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.goods__name {
  margin: 0;
  font-size: 18px;
}

.goods__meta {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.goods__meta-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.goods__meta-row dt {
  flex: 0 0 72px;
  color: var(--muted);
  font-size: 13px;
}

.goods__meta-row dd {
  margin: 0;
}

.goods__price {
  color: var(--danger);
  font-weight: 600;
}

.section {
  margin-top: 16px;
}

.section__title {
  margin: 0 0 12px;
  font-size: 16px;
}
</style>
