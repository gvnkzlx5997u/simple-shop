<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { fetchFavorites, removeFavorite } from '../api/favorite'
import type { FavoriteItemData } from '../api/types'
import { usePagedList } from '../composables/usePagedList'
import { describeError } from '../stores/auth'
import { formatMoney, formatTime } from '../utils/format'

const PAGE_SIZE = 12

/** 操作结果（取消收藏、点失效行）统一走这一条，避免页面到处飘提示。 */
const notice = ref('')
const actionError = ref('')
const busyId = ref('')

/** 分页与加载交给公共 composable。 */
const {
  items,
  total,
  page,
  loading,
  errorText,
  totalPages,
  canPrev,
  canNext,
  hasPager,
  load,
  goPage,
} = usePagedList<FavoriteItemData>(
  (pageNo) => fetchFavorites({ page: pageNo, page_size: PAGE_SIZE }),
  // 收藏要空页回退：把第 2 页删空时，应退回第 1 页而不是显示「还没有收藏任何商品」。
  { pageSize: PAGE_SIZE, emptyPageFallback: true },
)

function go(nextPage: number): void {
  notice.value = ''
  goPage(nextPage)
}

async function onRemove(item: FavoriteItemData): Promise<void> {
  if (busyId.value) return
  busyId.value = item.goods_id
  notice.value = ''
  actionError.value = ''
  try {
    await removeFavorite(item.goods_id)
    notice.value = '已取消收藏《' + item.name + '》'
    // 删掉最后一页的最后一条时，页数会缩一页，这里回退一页再拉，避免停在空页。
    if (items.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await load()
  } catch (err) {
    actionError.value = describeError(err)
  } finally {
    busyId.value = ''
  }
}

/** 失效行不可进详情（进去只会是 404 空态），点了给一句提示。 */
function onUnavailable(item: FavoriteItemData): void {
  notice.value = '《' + item.name + '》已下架，暂时看不了详情'
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page stack">
    <div class="head">
      <h1 class="page-title">我的收藏</h1>
      <span v-if="total" class="muted small">共 {{ total }} 件</span>
    </div>

    <p v-if="notice" class="notice">{{ notice }}</p>
    <p v-if="actionError" class="error">{{ actionError }}</p>

    <p v-if="loading && !items.length" class="muted">加载中…</p>
    <p v-else-if="errorText" class="error">{{ errorText }}</p>

    <div v-else-if="!items.length" class="card empty">
      <p class="empty-title">还没有收藏任何商品</p>
      <p class="muted">在商品详情页点「收藏」，之后就能在这里找到。</p>
      <RouterLink class="primary-link" to="/goods">去逛逛</RouterLink>
    </div>

    <template v-else>
      <div class="grid">
        <div
          v-for="item in items"
          :key="item.goods_id"
          :class="['card', 'fav-card', { disabled: !item.detail_available }]"
        >
          <RouterLink v-if="item.detail_available" :to="'/goods/' + item.goods_id">
            <img
              v-if="item.main_image_url"
              :src="item.main_image_url"
              :alt="item.name"
              class="cover"
            />
            <div v-else class="cover placeholder">暂无图片</div>
          </RouterLink>
          <div v-else @click="onUnavailable(item)" title="已下架，不可查看详情">
            <img
              v-if="item.main_image_url"
              :src="item.main_image_url"
              :alt="item.name"
              class="cover"
            />
            <div v-else class="cover placeholder">暂无图片</div>
          </div>

          <div class="body">
            <RouterLink v-if="item.detail_available" :to="'/goods/' + item.goods_id" class="name">
              {{ item.name }}
            </RouterLink>
            <span
              v-else
              class="name unavailable"
              title="已下架，不可查看详情"
              @click="onUnavailable(item)"
            >
              {{ item.name }}
            </span>

            <div class="line">
              <span class="price">¥{{ formatMoney(item.price) }}</span>
              <span v-if="!item.detail_available" class="badge">已下架</span>
              <span v-else-if="item.stock <= 0" class="badge">缺货</span>
            </div>

            <div class="muted small">收藏于 {{ formatTime(item.favorited_at) }}</div>

            <button class="remove" :disabled="busyId === item.goods_id" @click="onRemove(item)">
              取消收藏
            </button>
          </div>
        </div>
      </div>

      <!-- 页码保留，按钮只在一页以上时出现 -->
      <div class="pager">
        <button v-if="hasPager" :disabled="!canPrev" @click="go(page - 1)">上一页</button>
        <span class="muted small">第 {{ page }} / {{ totalPages }} 页</span>
        <button v-if="hasPager" :disabled="!canNext" @click="go(page + 1)">下一页</button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.page-title {
  font-size: 18px;
  margin: 0;
}

.small {
  font-size: 12px;
}

.notice {
  color: #15803d;
  margin: 0;
}

.empty {
  text-align: center;
  padding: 40px 16px;
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 8px;
}

.primary-link {
  display: inline-block;
  margin-top: 12px;
  padding: 6px 16px;
  background: var(--color-primary);
  color: #fff;
  border-radius: 4px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}

.fav-card {
  padding: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.fav-card.disabled {
  background: var(--color-fill);
}

.cover {
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  background: var(--color-fill);
  display: block;
}

.cover.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-weak);
  font-size: 12px;
}

.fav-card.disabled .cover {
  opacity: 0.55;
  cursor: not-allowed;
}

.body {
  padding: 10px 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.name {
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fav-card.disabled .name {
  color: var(--color-text-weak);
}

/* 失效行的名字也要能点出提示：不然用户点哪儿都没反应，像是页面坏了。 */
.name.unavailable {
  cursor: not-allowed;
}

.line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.price {
  color: var(--color-danger);
  font-weight: 600;
}

.badge {
  font-size: 12px;
  padding: 0 6px;
  border: 1px solid var(--color-border);
  border-radius: 3px;
  color: var(--color-text-weak);
}

.remove {
  margin-top: 6px;
  align-self: flex-start;
  padding: 2px 10px;
  font-size: 12px;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 8px 0;
}
</style>
