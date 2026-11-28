<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { fetchAftersale } from '../api/aftersale'
import { ApiError } from '../api/http'
import { ApiCode } from '../api/types'
import type { AftersaleDetailData } from '../api/types'
import EmptyState from '../components/common/EmptyState.vue'
import StatusTag from '../components/common/StatusTag.vue'
import GoodsGallery from '../components/goods/GoodsGallery.vue'
import { describeError } from '../utils/errorText'
import { formatMoney, formatTime } from '../utils/format'
import {
  AFTERSALE_STATUS_TONES,
  aftersaleResultText,
  aftersaleStatusText,
} from '../utils/aftersaleStatus'

const route = useRoute()
const aftersaleId = computed(() => String(route.params.id ?? ''))

const detail = ref<AftersaleDetailData | null>(null)
const loading = ref(false)
const errorText = ref('')
const notFound = ref(false)
const activeImage = ref('')

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  notFound.value = false
  try {
    const data = await fetchAftersale(aftersaleId.value)
    detail.value = data
    // 图片顺序沿用后端给的 sort_no，前端不重排。
    activeImage.value = data.image_urls[0] ?? ''
  } catch (err) {
    detail.value = null
    notFound.value =
      err instanceof ApiError &&
      (err.code === ApiCode.AFTERSALE_NOT_FOUND || err.httpStatus === 404)
    if (!notFound.value) errorText.value = describeError(err)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="mx-auto max-w-[960px] space-y-3 p-4">
    <div class="flex items-baseline justify-between">
      <h1 class="text-lg">售后详情</h1>
      <RouterLink to="/aftersales" class="link text-xs text-base-content/60">
        返回我的售后
      </RouterLink>
    </div>

    <p v-if="loading" class="muted">加载中…</p>

    <EmptyState
      v-else-if="notFound"
      title="售后单不存在"
      description="这条售后单可能不属于当前账号。"
    >
      <RouterLink class="btn btn-primary btn-sm" to="/aftersales">返回我的售后</RouterLink>
    </EmptyState>

    <div v-else-if="errorText" class="panel flex items-center gap-3">
      <p class="flex-1 text-error">{{ errorText }}</p>
      <button class="btn btn-outline btn-sm" @click="load">重试</button>
    </div>

    <template v-else-if="detail">
      <div class="panel">
        <div class="mb-3 flex flex-wrap items-center gap-2.5">
          <span class="text-[15px] font-semibold tracking-wide">{{ detail.aftersale_no }}</span>
          <StatusTag
            :label="aftersaleStatusText(detail.status)"
            :tone="AFTERSALE_STATUS_TONES[detail.status]"
          />
        </div>
        <div class="grid grid-cols-[88px_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
          <span class="muted">关联订单</span>
          <RouterLink
            class="link link-primary"
            :to="{ name: 'order-detail', params: { id: detail.order_id } }"
          >
            {{ detail.order_no }}
          </RouterLink>
          <span class="muted">商品</span>
          <span>{{ detail.goods_name }}</span>
          <span class="muted">申请时间</span>
          <span>{{ formatTime(detail.create_at) }}</span>
          <span class="muted">最近更新</span>
          <span>{{ formatTime(detail.update_at) }}</span>
          <template v-if="detail.result">
            <span class="muted">处理结果</span>
            <span>{{ aftersaleResultText(detail.result) }}</span>
          </template>
          <template v-if="detail.refund_amount != null">
            <span class="muted">退款金额</span>
            <span class="font-semibold text-error">¥{{ formatMoney(detail.refund_amount) }}</span>
            <span class="muted">退款时间</span>
            <span>{{ formatTime(detail.refunded_at) }}</span>
          </template>
        </div>
        <p v-if="detail.status === 'CANCELED'" class="muted mt-2 text-xs">
          已取消的售后单不产生退款，可以对同一件商品重新申请。
        </p>
        <p v-else-if="detail.status === 'PENDING'" class="muted mt-2 text-xs">
          等待卖家线下协商后登记结果，期间你不能撤销或修改。
        </p>
      </div>

      <div class="panel">
        <h2 class="mb-2.5 text-[15px]">{{ detail.title }}</h2>
        <p class="text-sm whitespace-pre-line">{{ detail.description }}</p>
      </div>

      <div v-if="detail.image_urls.length" class="panel">
        <h2 class="mb-2.5 text-[15px]">凭证图片（{{ detail.image_urls.length }} 张）</h2>
        <GoodsGallery
          :images="detail.image_urls"
          :active="activeImage"
          :alt="detail.title"
          @update:active="activeImage = $event"
        />
      </div>
    </template>
  </div>
</template>