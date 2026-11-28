<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import {
  cancelSellerAftersale,
  completeSellerAftersale,
  getSellerAftersale,
} from '../api/aftersale'
import type { AftersaleDetailData, AftersaleResult } from '../api/types'
import AppIcon from '../components/common/AppIcon.vue'
import ConfirmDialog from '../components/common/ConfirmDialog.vue'
import StatusTag from '../components/common/StatusTag.vue'
import { sellerAftersaleActions } from '../components/aftersale/aftersaleActions'
import {
  AFTERSALE_RESULT_LABELS,
  AFTERSALE_RESULT_OPTIONS,
  AFTERSALE_RESULT_TONES,
  AFTERSALE_STATUS_LABELS,
  AFTERSALE_STATUS_TONES,
} from '../constants/labels'
import { M10 } from '../constants/messages'
import { useRequestSequence } from '../composables/useRequestSequence'
import { useToast } from '../composables/useToast'
import { errorMessage } from '../utils/error'
import { formatDateTime, formatPrice } from '../utils/format'

/**
 * 售后详情（卖家）。
 *
 * 处理方式只有两条路：登记结果并完成，或取消（同拒绝）。两者都是终态，
 * 完成后不能改结果，取消后买家可重新申请、生成新单号。
 * 退款金额由后端按订单项快照算，页面只展示，不提供输入框。
 */
const route = useRoute()
const toast = useToast()

const aftersaleId = computed(() => (typeof route.params.id === 'string' ? route.params.id : ''))

const detail = ref<AftersaleDetailData | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const missing = ref(false)

const seq = useRequestSequence()

async function load(): Promise<void> {
  const token = seq.begin()
  loading.value = true
  error.value = null
  missing.value = false
  try {
    const data = await getSellerAftersale(aftersaleId.value)
    if (!seq.isCurrent(token)) {
      return
    }
    detail.value = data
  } catch (caught) {
    if (!seq.isCurrent(token)) {
      return
    }
    detail.value = null
    error.value = errorMessage(caught)
    // 81001 对应 HTTP 404：重试没有意义，只给返回列表的出口。
    missing.value = true
  } finally {
    if (seq.isCurrent(token)) {
      loading.value = false
    }
  }
}

onMounted(load)
watch(aftersaleId, load)

const actions = computed(() => (detail.value ? sellerAftersaleActions(detail.value.status) : []))
const canComplete = computed(() => actions.value.some((action) => action.key === 'complete'))
const canCancel = computed(() => actions.value.some((action) => action.key === 'cancel'))

const busy = ref(false)

// ---- 完成：先选处理结果，再确认 ----

const resultOpen = ref(false)
/**
 * 不预选任何一项：完成是终态、退款不可撤销，默认选中「退货并退款」等于
 * 让人点两下就退钱。必须自己选一项才能提交（未选时按钮禁用）。
 */
const selectedResult = ref<AftersaleResult | ''>('')
const selectedOption = computed(
  () => AFTERSALE_RESULT_OPTIONS.find((option) => option.value === selectedResult.value) ?? null,
)

function openResultDialog(): void {
  if (busy.value) {
    return
  }
  selectedResult.value = ''
  resultOpen.value = true
}

function closeResultDialog(): void {
  if (busy.value) {
    return
  }
  resultOpen.value = false
}

async function submitComplete(): Promise<void> {
  if (busy.value || selectedResult.value === '') {
    return
  }
  const result = selectedResult.value
  busy.value = true
  try {
    await completeSellerAftersale(aftersaleId.value, result)
    toast.success(M10.aftersaleCompleted)
    resultOpen.value = false
    await load()
  } catch (caught) {
    toast.error(errorMessage(caught))
    resultOpen.value = false
    // 被拒时状态可能已被改（例如另一张售后单刚退过款），重拉一次再决定按钮。
    await load()
  } finally {
    busy.value = false
  }
}

// ---- 取消（同拒绝）：二次确认 ----

const cancelOpen = ref(false)

function openCancelDialog(): void {
  if (busy.value) {
    return
  }
  cancelOpen.value = true
}

async function submitCancel(): Promise<void> {
  if (busy.value) {
    return
  }
  busy.value = true
  try {
    await cancelSellerAftersale(aftersaleId.value)
    toast.success(M10.aftersaleCanceled)
    cancelOpen.value = false
    await load()
  } catch (caught) {
    toast.error(errorMessage(caught))
    cancelOpen.value = false
    await load()
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <h1 class="text-lg font-semibold">售后详情</h1>
      <RouterLink to="/aftersales" class="btn btn-sm btn-outline">
        <AppIcon name="arrow-left" />
        返回售后列表
      </RouterLink>
    </header>

    <div v-if="error" role="alert" class="alert alert-error alert-soft text-sm">
      <AppIcon name="forbidden" />
      <span>{{ error }}</span>
      <div class="ml-auto flex gap-2">
        <RouterLink v-if="missing" to="/aftersales" class="btn btn-xs btn-outline">
          返回列表
        </RouterLink>
        <button v-else type="button" class="btn btn-xs btn-outline" @click="load">重试</button>
      </div>
    </div>
    <div v-else-if="loading && !detail" class="panel text-sm text-base-content/60">
      售后单加载中…
    </div>

    <template v-else-if="detail">
      <div class="panel flex flex-col gap-4">
        <div class="flex flex-wrap items-center gap-3">
          <h2 class="font-mono text-base font-semibold">{{ detail.aftersale_no }}</h2>
          <StatusTag
            :label="AFTERSALE_STATUS_LABELS[detail.status]"
            :tone="AFTERSALE_STATUS_TONES[detail.status]"
          />
          <StatusTag
            v-if="detail.result"
            :label="AFTERSALE_RESULT_LABELS[detail.result]"
            :tone="AFTERSALE_RESULT_TONES[detail.result]"
          />
        </div>

        <dl class="grid grid-cols-1 gap-x-8 gap-y-3 text-sm sm:grid-cols-2 lg:grid-cols-3">
          <div class="flex flex-col gap-0.5">
            <dt class="text-xs text-base-content/60">订单号</dt>
            <dd class="m-0">
              <RouterLink
                :to="{ name: 'order-detail', params: { id: detail.order_id } }"
                class="link link-hover font-mono"
              >
                {{ detail.order_no }}
              </RouterLink>
            </dd>
          </div>
          <div class="flex min-w-0 flex-col gap-0.5">
            <dt class="text-xs text-base-content/60">商品</dt>
            <dd class="m-0 truncate" :title="detail.goods_name">{{ detail.goods_name }}</dd>
          </div>
          <div class="flex min-w-0 flex-col gap-0.5">
            <dt class="text-xs text-base-content/60">标题</dt>
            <dd class="m-0 truncate" :title="detail.title">{{ detail.title }}</dd>
          </div>
          <div class="flex flex-col gap-0.5">
            <dt class="text-xs text-base-content/60">申请时间</dt>
            <dd class="m-0">{{ formatDateTime(detail.create_at) }}</dd>
          </div>
          <div class="flex flex-col gap-0.5">
            <dt class="text-xs text-base-content/60">最后更新</dt>
            <dd class="m-0">{{ formatDateTime(detail.update_at) }}</dd>
          </div>
          <div v-if="detail.refund_amount" class="flex flex-col gap-0.5">
            <dt class="text-xs text-base-content/60">退款金额</dt>
            <dd class="m-0 font-semibold text-warning">
              {{ formatPrice(detail.refund_amount) }}
              <span v-if="detail.refunded_at" class="ml-1 text-xs font-normal text-base-content/60">
                于 {{ formatDateTime(detail.refunded_at) }}
              </span>
            </dd>
          </div>
        </dl>

        <div v-if="detail.status === 'COMPLETED'" class="rounded-box bg-base-200 px-3 py-2 text-xs">
          <p class="m-0">
            {{
              detail.result === 'EXCHANGE'
                ? '换货不产生退款，该订单项之后仍可再申请售后。'
                : '退款已登记，同一订单项只能退款一次。'
            }}
          </p>
        </div>
        <div v-else-if="detail.status === 'CANCELED'" class="rounded-box bg-base-200 px-3 py-2 text-xs">
          <p class="m-0">已取消（同拒绝）：买家可以重新申请，重新申请会生成新的售后单号。</p>
        </div>
        <div v-else class="rounded-box bg-base-200 px-3 py-2 text-xs">
          <p class="m-0">
            等待你登记处理结果。退款金额由后端按「订单项单价 × 数量」自动算，不需要你填写。
          </p>
        </div>

        <div v-if="canComplete || canCancel" class="flex flex-wrap items-center justify-end gap-2 border-t border-base-300 pt-4">
          <button
            v-if="canCancel"
            type="button"
            class="btn btn-sm btn-outline btn-error"
            :disabled="busy"
            data-testid="aftersale-cancel"
            @click="openCancelDialog"
          >
            取消 / 拒绝
          </button>
          <button
            v-if="canComplete"
            type="button"
            class="btn btn-sm btn-primary"
            :disabled="busy"
            data-testid="aftersale-complete"
            @click="openResultDialog"
          >
            处理并完成
          </button>
        </div>
      </div>

      <section class="panel flex flex-col gap-2">
        <h2 class="text-sm font-semibold">买家描述</h2>
        <p v-if="detail.description" class="m-0 whitespace-pre-line text-sm leading-relaxed">
          {{ detail.description }}
        </p>
        <p v-else class="m-0 text-sm text-base-content/60">买家没有填写描述</p>
      </section>

      <section class="panel flex flex-col gap-3">
        <h2 class="text-sm font-semibold">凭证图片</h2>
        <p v-if="detail.image_urls.length === 0" class="m-0 text-sm text-base-content/60">
          {{ M10.aftersaleImagesEmpty }}
        </p>
        <div v-else class="flex flex-wrap gap-3">
          <a
            v-for="url in detail.image_urls"
            :key="url"
            :href="url"
            target="_blank"
            rel="noopener"
            class="block rounded-box border border-base-300 p-0 leading-none"
            title="点击查看大图"
          >
            <img
              :src="url"
              alt="售后凭证"
              class="size-28 rounded-box object-cover"
              loading="lazy"
            />
          </a>
        </div>
      </section>
    </template>

    <!-- 选处理结果：三种结果的差别（动不动钱、占不占退款额度）直接写在选项下面 -->
    <Teleport to="body">
      <div
        v-if="resultOpen"
        class="modal modal-open"
        role="dialog"
        aria-modal="true"
        aria-label="选择处理结果"
        data-testid="aftersale-result-dialog"
        @click.self="closeResultDialog"
      >
        <div class="modal-box max-w-[460px] shadow-xl">
          <h3 class="text-base font-semibold">{{ M10.aftersaleResultTitle }}</h3>
          <div class="mt-4 flex flex-col gap-2">
            <label
              v-for="option in AFTERSALE_RESULT_OPTIONS"
              :key="option.value"
              class="flex cursor-pointer flex-col gap-1 rounded-box border border-base-300 px-3 py-2"
              :class="selectedResult === option.value ? 'border-primary bg-primary/5' : ''"
            >
              <span class="flex items-center gap-2">
                <input
                  v-model="selectedResult"
                  class="radio radio-sm radio-primary"
                  type="radio"
                  name="aftersale-result"
                  :value="option.value"
                  :disabled="busy"
                />
                <span class="text-sm font-medium">{{ option.label }}</span>
              </span>
              <span class="pl-6 text-xs text-base-content/60">{{ option.hint }}</span>
            </label>
          </div>
          <p v-if="!selectedOption" class="mt-3 mb-0 text-xs text-warning">
            {{ M10.aftersaleResultRequired }}
          </p>
          <div class="modal-action">
            <button
              type="button"
              class="btn btn-sm btn-outline"
              :disabled="busy"
              @click="closeResultDialog"
            >
              取消
            </button>
            <button
              type="button"
              class="btn btn-sm btn-primary"
              :disabled="busy || !selectedOption"
              data-testid="aftersale-result-submit"
              @click="submitComplete"
            >
              {{ busy ? '处理中…' : '确认完成' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <ConfirmDialog
      :open="cancelOpen"
      title="取消售后"
      :message="M10.confirmCancelAftersale"
      confirm-text="取消售后"
      cancel-text="再想想"
      danger
      :loading="busy"
      @confirm="submitCancel"
      @cancel="cancelOpen = false"
    />
  </div>
</template>
