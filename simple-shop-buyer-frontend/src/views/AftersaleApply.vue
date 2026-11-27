<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { applyAftersale } from '../api/aftersale'
import { ApiError } from '../api/http'
import { uploadAftersaleImage } from '../api/media'
import { fetchOrder, fetchOrderItems } from '../api/order'
import { ApiCode } from '../api/types'
import type { OrderData, OrderItemData } from '../api/types'
import EmptyState from '../components/common/EmptyState.vue'
import { describeError } from '../utils/errorText'
import { formatMoney } from '../utils/format'
import { canApplyAftersale } from '../utils/orderStatus'
import {
  MAX_AFTERSALE_IMAGES,
  validateAftersale,
  validateAftersaleImage,
} from '../validation/aftersale'

const route = useRoute()
const router = useRouter()
const orderId = computed(() => String(route.query.order_id ?? ''))
const orderItemId = computed(() => String(route.query.order_item_id ?? ''))

const order = ref<OrderData | null>(null)
const item = ref<OrderItemData | null>(null)
const loading = ref(false)
const loadError = ref('')
const notApplicable = ref(false)

const title = ref('')
const description = ref('')
/** 只存后端返回的 URL：外链与商品图目录的地址都会被拒。 */
const images = ref<string[]>([])
const uploading = ref(false)
const uploadError = ref('')
const formError = ref('')
const submitting = ref(false)

const full = computed(() => images.value.length >= MAX_AFTERSALE_IMAGES)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  notApplicable.value = false
  try {
    const [head, detail] = await Promise.all([
      fetchOrder(orderId.value),
      fetchOrderItems(orderId.value),
    ])
    order.value = head
    // 用 order_item_id 定位，不用 goods_id：同一商品可能出现在多张订单里。
    item.value = detail.items.find((i) => i.order_item_id === orderItemId.value) ?? null
    notApplicable.value = !item.value || !canApplyAftersale(head.status)
  } catch (err) {
    order.value = null
    item.value = null
    if (err instanceof ApiError && (err.code === ApiCode.ORDER_NOT_FOUND || err.httpStatus === 404)) {
      notApplicable.value = true
    } else {
      loadError.value = describeError(err)
    }
  } finally {
    loading.value = false
  }
}

async function onPick(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  // 清空以便同一张图删掉后还能再选。
  input.value = ''
  if (!files.length) return
  uploadError.value = ''
  uploading.value = true
  try {
    for (const file of files) {
      if (full.value) {
        uploadError.value = `最多上传 ${MAX_AFTERSALE_IMAGES} 张图片`
        break
      }
      const reason = validateAftersaleImage(file)
      if (reason) {
        uploadError.value = reason
        continue
      }
      try {
        const stored = await uploadAftersaleImage(file)
        images.value = [...images.value, stored.url]
      } catch (err) {
        // 单张失败不影响其它张，已传成功的保留。
        uploadError.value = describeError(err)
      }
    }
  } finally {
    uploading.value = false
  }
}

function removeImage(index: number): void {
  images.value = images.value.filter((_, i) => i !== index)
  uploadError.value = ''
}

async function submit(): Promise<void> {
  if (!item.value || submitting.value || uploading.value) return
  const reason = validateAftersale({ title: title.value, description: description.value })
  if (reason) {
    formError.value = reason
    return
  }
  submitting.value = true
  formError.value = ''
  try {
    const applied = await applyAftersale({
      order_item_id: item.value.order_item_id,
      title: title.value.trim(),
      description: description.value.trim(),
      image_urls: images.value,
    })
    void router.replace({ name: 'aftersale-detail', params: { id: applied.aftersale_id } })
  } catch (err) {
    formError.value = describeError(err)
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="mx-auto max-w-[960px] space-y-3 p-4">
    <div class="flex items-baseline justify-between">
      <h1 class="text-lg">申请售后</h1>
      <RouterLink
        :to="{ name: 'order-detail', params: { id: orderId } }"
        class="link text-xs text-base-content/60"
      >
        返回订单详情
      </RouterLink>
    </div>

    <p v-if="loading" class="muted">加载中…</p>

    <div v-else-if="loadError" class="panel flex items-center gap-3">
      <p class="flex-1 text-error">{{ loadError }}</p>
      <button class="btn btn-outline btn-sm" @click="load">重试</button>
    </div>

    <EmptyState
      v-else-if="notApplicable"
      title="这件商品当前不可申请售后"
      description="只有交易完成的订单可以按订单项申请售后。"
    >
      <RouterLink
        class="btn btn-primary btn-sm"
        :to="{ name: 'order-detail', params: { id: orderId } }"
      >
        回订单详情
      </RouterLink>
    </EmptyState>

    <template v-else-if="order && item">
      <div class="panel">
        <h2 class="mb-2.5 text-[15px]">售后商品</h2>
        <div class="grid grid-cols-[88px_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
          <span class="muted">订单号</span>
          <span>{{ order.order_no }}</span>
          <span class="muted">商品</span>
          <span>{{ item.goods_name }}</span>
          <span class="muted">单价 × 数量</span>
          <span>¥{{ formatMoney(item.unit_price) }} × {{ item.quantity }}</span>
          <span class="muted">本行金额</span>
          <span class="font-semibold text-error">¥{{ formatMoney(item.amount) }}</span>
        </div>
        <p class="muted mt-2 text-xs">
          按整行金额退款，不拆数量。退货退款 / 仅退款 / 换货由卖家线下协商后登记，申请时不用选。
        </p>
      </div>

      <div class="panel space-y-3">
        <label class="block">
          <span class="mb-1 block text-base-content/60">标题</span>
          <input v-model="title" class="input w-full" />
          <small class="mt-1 block text-xs text-base-content/60">必填，一行写完，最多 100 字</small>
        </label>

        <label class="block">
          <span class="mb-1 block text-base-content/60">描述</span>
          <textarea v-model="description" class="textarea w-full" rows="5"></textarea>
          <small class="mt-1 block text-xs text-base-content/60">必填，最多 1000 字，可以分行写</small>
        </label>

        <div>
          <span class="mb-1 block text-base-content/60">凭证图片</span>
          <input
            type="file"
            class="file-input w-full"
            accept="image/jpeg,image/png"
            multiple
            :disabled="uploading || full"
            data-testid="aftersale-upload"
            @change="onPick"
          />
          <small class="mt-1 block text-xs text-base-content/60">
            可不传；JPG 或 PNG，单张不超过 5MB，最多 {{ MAX_AFTERSALE_IMAGES }} 张（已传
            {{ images.length }} 张）
          </small>

          <p v-if="uploading" class="muted mt-1 text-xs">上传中…</p>
          <p v-if="uploadError" class="mt-1 text-error">{{ uploadError }}</p>

          <div v-if="images.length" class="mt-2 flex flex-wrap gap-2">
            <div v-for="(url, index) in images" :key="url" class="relative">
              <img
                :src="url"
                :alt="'凭证图 ' + (index + 1)"
                class="size-20 rounded-field border border-base-300 object-cover"
                data-testid="aftersale-image"
              />
              <button
                class="btn btn-ghost btn-xs absolute -top-2 -right-2 rounded-full bg-base-100"
                :aria-label="'删除第 ' + (index + 1) + ' 张'"
                @click="removeImage(index)"
              >
                ✕
              </button>
            </div>
          </div>
        </div>

        <p v-if="formError" class="text-error">{{ formError }}</p>

        <button
          class="btn btn-primary"
          data-testid="aftersale-submit"
          :disabled="submitting || uploading"
          @click="submit"
        >
          {{ submitting ? '提交中…' : '提交申请' }}
        </button>
        <p class="muted text-xs">提交后由卖家处理，你不能撤销或改状态；被取消后可以再申请。</p>
      </div>
    </template>
  </div>
</template>