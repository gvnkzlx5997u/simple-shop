<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { fetchGoodsDetail } from '../api/buyer'
import { ApiError } from '../api/http'
import { ApiCode } from '../api/types'
import type { GoodsDetail } from '../api/types'
import { describeError, useAuthStore } from '../stores/auth'
import { useCartStore } from '../stores/cart'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const cart = useCartStore()

const goods = ref<GoodsDetail | null>(null)
const activeImage = ref('')
const loading = ref(false)
const errorText = ref('')
/** 已下架与不存在都是正常结果，给空态而不是错误提示。 */
const notFound = ref(false)

const adding = ref(false)
const notice = ref('')
/** 加购失败单独一份，避免和「详情加载失败」互相覆盖。 */
const actionError = ref('')

const goodsId = computed(() => String(route.params.id ?? ''))
const allImages = computed(() => {
  if (!goods.value) return []
  // 防御：images 理论上不会为 null，但少一个兜底就多一种白屏可能。
  const list = (goods.value.images ?? []).map((i) => i.url)
  if (goods.value.main_image_url && !list.includes(goods.value.main_image_url)) {
    list.unshift(goods.value.main_image_url)
  }
  return list
})

/** 能进详情页的商品后端只放行在售，这里再按状态判一次，避免按钮在异常数据下亮着。 */
const canAddToCart = computed(() => goods.value?.status === 'ON_SALE')
/** 在售但没货：可以加进购物车等补货，但要先说明白。 */
const outOfStock = computed(() => !!goods.value && goods.value.status === 'ON_SALE' && goods.value.stock <= 0)

async function load() {
  loading.value = true
  errorText.value = ''
  notFound.value = false
  try {
    goods.value = await fetchGoodsDetail(goodsId.value)
    activeImage.value = goods.value.main_image_url || allImages.value[0] || ''
  } catch (err) {
    goods.value = null
    // 买家只在售商品可见；下架后详情返回 404 + 20011，不是故障。
    notFound.value =
      err instanceof ApiError && (err.code === ApiCode.GOODS_NOT_FOUND || err.httpStatus === 404)
    if (!notFound.value) {
      errorText.value = describeError(err)
    }
  } finally {
    loading.value = false
  }
}

async function onAddToCart() {
  if (!auth.isLoggedIn) {
    // 未登录不拦在详情页，跳登录后回原页（阶段D §2.2 的统一守卫口径）。
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  adding.value = true
  notice.value = ''
  actionError.value = ''
  try {
    const result = await cart.add(goodsId.value)
    // 后端加购幂等，重复点不会产生第二行，这里只是把结果说清楚。
    notice.value = result === 'existed' ? '这件商品已经在购物车里了' : '已加入购物车'
  } catch (err) {
    actionError.value = describeError(err)
  } finally {
    adding.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page stack">
    <p v-if="loading" class="muted">加载中…</p>

    <div v-else-if="notFound" class="card empty">
      <p class="empty-title">商品已下架或不存在</p>
      <p class="muted">该商品可能已被卖家下架，可以看看其他在售商品。</p>
      <RouterLink class="back-link" to="/goods">返回商品列表</RouterLink>
    </div>

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
            {{ goods.category?.l1_name ?? '未分类' }} / {{ goods.category?.l2_name ?? '' }}
          </div>
          <div class="muted">库存 {{ goods.stock }}</div>

          <p v-if="outOfStock" class="warn">当前缺货，可以先加入购物车，等补货后再下单</p>

          <button
            v-if="canAddToCart"
            class="primary buy"
            :disabled="adding"
            @click="onAddToCart"
          >
            {{ adding ? '加入中…' : '加入购物车' }}
          </button>

          <p v-if="notice" class="notice">
            {{ notice }}
            <RouterLink to="/cart">去购物车</RouterLink>
          </p>
          <p v-if="actionError" class="error">{{ actionError }}</p>
        </div>
      </div>

      <div class="card">
        <h2 class="section-title">商品描述</h2>
        <div v-if="goods.description?.length" class="description">
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

.empty {
  text-align: center;
  padding: 40px 16px;
}

.buy {
  width: 100%;
  margin-top: 16px;
  padding: 10px;
}

.notice {
  color: #15803d;
}

.warn {
  color: #92400e;
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 8px;
}

.back-link {
  display: inline-block;
  margin-top: 12px;
  color: var(--color-primary);
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
