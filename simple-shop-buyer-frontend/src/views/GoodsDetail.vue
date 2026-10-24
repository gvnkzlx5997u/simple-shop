<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { fetchGoodsDetail, submitIntention } from '../api/buyer'
import type { GoodsDetail } from '../api/types'
import { describeError, useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const goods = ref<GoodsDetail | null>(null)
const activeImage = ref('')
const loading = ref(false)
const errorText = ref('')
const submitting = ref(false)
const notice = ref('')

const goodsId = computed(() => String(route.params.id ?? ''))
const allImages = computed(() => {
  if (!goods.value) return []
  const list = goods.value.images.map((i) => i.url)
  if (goods.value.main_image_url && !list.includes(goods.value.main_image_url)) {
    list.unshift(goods.value.main_image_url)
  }
  return list
})

/** 交易中的商品能看但不能买；purchasable 由后端给出，前端不自行推断。 */
const canBuy = computed(() => !!goods.value?.purchasable)

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    goods.value = await fetchGoodsDetail(goodsId.value)
    activeImage.value = goods.value.main_image_url || allImages.value[0] || ''
  } catch (err) {
    errorText.value = describeError(err)
    goods.value = null
  } finally {
    loading.value = false
  }
}

async function onBuy() {
  if (!auth.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  submitting.value = true
  notice.value = ''
  errorText.value = ''
  try {
    await submitIntention(goodsId.value)
    // 回执不含位次，这里只能确认"已提交"，不展示排队位置。
    notice.value = '已提交购买意向'
  } catch (err) {
    errorText.value = describeError(err)
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page stack">
    <p v-if="loading" class="muted">加载中…</p>
    <p v-else-if="errorText && !goods" class="error">{{ errorText }}</p>

    <template v-else-if="goods">
      <div class="card detail">
        <div class="gallery">
          <img
            v-if="activeImage"
            :src="activeImage"
            :alt="goods.name"
            class="main-image"
          />
          <div v-else class="main-image placeholder">暂无图片</div>

          <div v-if="allImages.length > 1" class="thumbs">
            <img
              v-for="url in allImages"
              :key="url"
              :src="url"
              :class="['thumb', { active: url === activeImage }]"
              @click="activeImage = url"
            />
          </div>
        </div>

        <div class="summary">
          <h1 class="title">{{ goods.name }}</h1>
          <div class="price">¥{{ goods.price }}</div>
          <div class="muted">
            {{ goods.category.l1_name }} / {{ goods.category.l2_name }}
          </div>
          <div class="muted">库存 {{ goods.stock }}</div>

          <p v-if="notice" class="notice">{{ notice }}</p>
          <p v-if="errorText" class="error">{{ errorText }}</p>

          <button class="primary buy" :disabled="!canBuy || submitting" @click="onBuy">
            {{ canBuy ? (submitting ? '提交中…' : '我要购买') : '商品交易中，暂停接收新意向' }}
          </button>
        </div>
      </div>

      <div class="card">
        <h2 class="section-title">商品描述</h2>
        <div v-if="goods.description.length" class="description">
          <template v-for="(block, idx) in goods.description" :key="idx">
            <p v-if="block.type === 'TEXT' && block.text">{{ block.text }}</p>
            <img
              v-else-if="block.type === 'IMAGE' && block.url"
              :src="block.url"
              class="desc-image"
            />
            <video
              v-else-if="block.type === 'VIDEO' && block.url"
              :src="block.url"
              controls
              class="desc-image"
            />
          </template>
        </div>
        <p v-else class="muted">暂无描述</p>
      </div>
    </template>
  </div>
</template>

<style scoped>
.detail {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 20px;
}

@media (max-width: 720px) {
  .detail {
    grid-template-columns: 1fr;
  }
}

.main-image {
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  border-radius: 6px;
  background: #f1f2f4;
}

.main-image.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-weak);
}

.thumbs {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  flex-wrap: wrap;
}

.thumb {
  width: 64px;
  height: 64px;
  object-fit: cover;
  border-radius: 4px;
  border: 2px solid transparent;
  cursor: pointer;
}

.thumb.active {
  border-color: var(--color-primary);
}

.title {
  font-size: 20px;
  margin: 0 0 8px;
}

.price {
  color: var(--color-danger);
  font-size: 22px;
  font-weight: 600;
  margin-bottom: 8px;
}

.buy {
  width: 100%;
  margin-top: 16px;
  padding: 10px;
}

.notice {
  color: #15803d;
}

.section-title {
  font-size: 16px;
  margin: 0 0 12px;
}

.desc-image {
  max-width: 100%;
  border-radius: 4px;
}
</style>
