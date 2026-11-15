<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { fetchFavorites, removeFavorite } from '../api/favorite'
import type { FavoriteItemData } from '../api/types'
import EmptyState from '../components/common/EmptyState.vue'
import Pagination from '../components/common/Pagination.vue'
import FavoriteCard from '../components/favorite/FavoriteCard.vue'
import { usePagedList } from '../composables/usePagedList'
import { describeError } from '../utils/errorText'

const PAGE_SIZE = 12

/** 操作结果（取消收藏、点失效行）统一走这一条，避免页面到处飘提示。 */
const notice = ref('')
const actionError = ref('')
const busyId = ref('')

/** 分页与加载交给公共 composable。 */
const { items, total, page, loading, errorText, load, goPage } = usePagedList<FavoriteItemData>(
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
  <div class="mx-auto max-w-[960px] space-y-3 p-4">
    <div class="flex items-baseline gap-3">
      <h1 class="text-lg">我的收藏</h1>
      <span v-if="total" class="muted text-xs">共 {{ total }} 件</span>
    </div>

    <p v-if="notice" class="notice text-success" data-testid="notice">{{ notice }}</p>
    <p v-if="actionError" class="text-error">{{ actionError }}</p>

    <p v-if="loading && !items.length" class="muted">加载中…</p>
    <p v-else-if="errorText" class="text-error">{{ errorText }}</p>

    <EmptyState
      v-else-if="!items.length"
      title="还没有收藏任何商品"
      description="在商品详情页点「收藏」，之后就能在这里找到。"
    >
      <RouterLink class="btn btn-primary btn-sm" to="/goods">去逛逛</RouterLink>
    </EmptyState>

    <template v-else>
      <div class="grid grid-cols-[repeat(auto-fill,minmax(200px,1fr))] gap-3">
        <FavoriteCard
          v-for="item in items"
          :key="item.goods_id"
          :item="item"
          :busy="busyId === item.goods_id"
          @remove="onRemove"
          @unavailable="onUnavailable"
        />
      </div>

      <Pagination
        :page="page"
        :page-size="PAGE_SIZE"
        :total="total"
        unit="件"
        :disabled="loading"
        @update:page="go"
      />
    </template>
  </div>
</template>