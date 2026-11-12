<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import AppIcon from '../components/common/AppIcon.vue'
import OrderList from '../components/order/OrderList.vue'

/**
 * 订单列表宿主页。
 *
 * 「订单管理 / 按客户 / 按商品」是同一个列表的三种筛选入口，不是三个页面：
 * 入口差异全部落在 query 上（buyer_id / goods_id），订单详情一律按 order_id 进。
 */
const route = useRoute()
const router = useRouter()

function queryValue(key: string): string {
  const value = route.query[key]
  return typeof value === 'string' ? value : ''
}

const buyerId = computed(() => queryValue('buyer_id') || null)
const goodsId = computed(() => queryValue('goods_id') || null)

const entryHint = computed(() => {
  if (buyerId.value) {
    return '已按客户筛选'
  }
  if (goodsId.value) {
    return '已按商品筛选'
  }
  return ''
})

function clearEntryFilter(): void {
  void router.push({ name: 'orders' })
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <header class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 class="text-lg font-semibold">订单管理</h1>
        <p class="text-xs text-base-content/60">
          按状态查看订单；订单只能按序推进，完成时会按明细数量扣减库存。
        </p>
      </div>
      <!--
        这里用 button 而不是 RouterLink：vue-router 会给激活链接自动加 aria-current="page"，
        daisyUI 把 aria-current 当作「当前项」给按钮铺底色，btn-ghost 会看起来像个灰块。
      -->
      <button
        v-if="entryHint"
        type="button"
        class="btn btn-sm btn-ghost text-base-content/70"
        title="清除筛选，查看全部订单"
        @click="clearEntryFilter"
      >
        <AppIcon name="close" class="text-[13px]" />
        {{ entryHint }}
      </button>
    </header>

    <OrderList :buyer-id="buyerId" :goods-id="goodsId" />
  </div>
</template>
