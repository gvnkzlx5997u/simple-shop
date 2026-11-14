<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import type { RouteLocationNormalized } from 'vue-router'

import type { GoodsPublishRequest } from '../api/types'
import AppIcon from '../components/common/AppIcon.vue'
import ConfirmDialog from '../components/common/ConfirmDialog.vue'
import CategoryCascadeSelect from '../components/goods/CategoryCascadeSelect.vue'
import DescriptionBlockEditor from '../components/goods/DescriptionBlockEditor.vue'
import ImageFilePicker from '../components/goods/ImageFilePicker.vue'
import { countDescriptionText, createTextLine } from '../components/goods/descriptionEditor'
import type { DescriptionEditorLine, PickedImage } from '../components/goods/descriptionEditor'
import { usePublishFlow } from '../composables/usePublishFlow'
import { M10 } from '../constants/messages'
import { useToast } from '../composables/useToast'
import {
  DESCRIPTION_TOTAL_MAX_CHARS,
  GOODS_NAME_MAX_CHARS,
  validatePublishForm,
} from '../validation/goods'
import { countChars, hasAngleBracket } from '../validation/shared'

const router = useRouter()
const toast = useToast()
const { submitting, phase, uploadedCount, uploadTotal, attemptFailed, run } = usePublishFlow()

const name = ref('')
const price = ref('')
const stock = ref<number | ''>(1)
const categoryL1 = ref<string | null>(null)
const categoryL2 = ref<string | null>(null)
const mainImages = ref<PickedImage[]>([])
const galleryImages = ref<PickedImage[]>([])
const editorLines = ref<DescriptionEditorLine[]>([createTextLine()])

const errors = ref<{ name?: string; price?: string; stock?: string; category?: string }>({})
const confirmOpen = ref(false)

/** 校验通过后暂存的发布基干,确认弹窗后使用。 */
const pendingPayload = ref<GoodsPublishRequest | null>(null)

// ---- 离开保护：脏表单且未发布成功时拦截，防误丢内容 ----

const published = ref(false)
const leaveConfirmOpen = ref(false)
let leaveTarget: RouteLocationNormalized | null = null
let allowLeave = false

const isDirty = computed(
  () =>
    name.value.trim() !== '' ||
    price.value.trim() !== '' ||
    stock.value !== 1 ||
    categoryL1.value !== null ||
    mainImages.value.length > 0 ||
    galleryImages.value.length > 0 ||
    editorLines.value.some((line) => line.kind === 'media' || line.content.trim() !== ''),
)

onBeforeRouteLeave((to) => {
  if (published.value || !isDirty.value || allowLeave) {
    return true
  }
  leaveTarget = to
  leaveConfirmOpen.value = true
  return false
})

function confirmLeave(): void {
  leaveConfirmOpen.value = false
  allowLeave = true
  if (leaveTarget) {
    void router.push(leaveTarget).finally(() => {
      allowLeave = false
    })
  }
}

function onBeforeUnload(event: BeforeUnloadEvent): void {
  if (isDirty.value && !published.value) {
    event.preventDefault()
  }
}

onMounted(() => window.addEventListener('beforeunload', onBeforeUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', onBeforeUnload))

// ---- 表单展示与提交 ----

const nameCharCount = computed(() => countChars(name.value.trim()))
const nameOverLimit = computed(() => nameCharCount.value > GOODS_NAME_MAX_CHARS)

const progressText = computed(() => {
  if (phase.value === 'uploading') {
    return uploadTotal.value > 0
      ? `上传媒体 ${uploadedCount.value}/${uploadTotal.value}…`
      : '准备中…'
  }
  if (phase.value === 'publishing') {
    return '发布中…'
  }
  return ''
})

const submitLabel = computed(() => {
  if (submitting.value) {
    return progressText.value || '处理中…'
  }
  return attemptFailed.value ? '重试发布' : '发布商品'
})

function onSubmit(): void {
  const result = validatePublishForm({
    name: name.value,
    price: price.value,
    stock: stock.value,
    categoryL1: categoryL1.value,
    categoryL2: categoryL2.value,
  })
  errors.value = result.errors
  if (!result.values) {
    return
  }

  // 描述与图片的快速预检(结构性的最终校验在上传回填 URL 后进行)。
  if (countDescriptionText(editorLines.value) > DESCRIPTION_TOTAL_MAX_CHARS) {
    toast.error(M10.descriptionInvalid)
    return
  }
  if (editorLines.value.some((line) => line.kind === 'text' && hasAngleBracket(line.content))) {
    toast.error(M10.descriptionInvalid)
    return
  }
  if (galleryImages.value.length > 0 && mainImages.value.length === 0) {
    toast.error(M10.mainImageRequired)
    return
  }

  pendingPayload.value = {
    name: result.values.name,
    price: result.values.price,
    stock: result.values.stock,
    category_id: result.values.categoryId,
  }
  confirmOpen.value = true
}

async function doPublish(): Promise<void> {
  const payload = pendingPayload.value
  if (!payload) {
    return
  }
  confirmOpen.value = false
  const ok = await run(payload, {
    mainImages: mainImages.value,
    galleryImages: galleryImages.value,
    lines: editorLines.value,
  })
  if (ok) {
    published.value = true
    toast.success(M10.publishSuccess)
    await router.replace({ name: 'goods-list' })
  }
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <h1 class="text-lg font-semibold">发布商品</h1>
      <RouterLink to="/" class="btn btn-sm btn-outline">
        <AppIcon name="arrow-left" />
        返回列表
      </RouterLink>
    </header>

    <!-- novalidate：库存 input 的 min=1 会触发浏览器原生气泡，挡住我们自己的中文文案 -->
    <form
      class="panel flex max-w-[720px] flex-col gap-4"
      novalidate
      @submit.prevent="onSubmit"
    >
      <fieldset class="fieldset">
        <legend class="fieldset-legend text-xs">商品名称</legend>
        <input
          id="goods-name"
          v-model="name"
          class="input w-full"
          type="text"
          maxlength="60"
          :disabled="submitting"
        />
        <div class="mt-1 flex items-baseline gap-2">
          <p v-if="errors.name" class="m-0 text-xs text-error">{{ errors.name }}</p>
          <span
            class="ml-auto text-xs tabular-nums"
            :class="nameOverLimit ? 'font-semibold text-error' : 'text-base-content/60'"
          >
            {{ nameCharCount }}/{{ GOODS_NAME_MAX_CHARS }}
          </span>
        </div>
      </fieldset>

      <div class="grid gap-4 sm:grid-cols-2">
        <fieldset class="fieldset">
          <legend class="fieldset-legend text-xs">价格（元）</legend>
          <input
            id="goods-price"
            v-model="price"
            class="input w-full"
            type="text"
            inputmode="decimal"
            placeholder="0.00 ~ 999999.99"
            :disabled="submitting"
          />
          <p v-if="errors.price" class="mt-1 text-xs text-error">{{ errors.price }}</p>
        </fieldset>

        <fieldset class="fieldset">
          <legend class="fieldset-legend text-xs">库存（件）</legend>
          <input
            id="goods-stock"
            v-model.number="stock"
            class="input w-full"
            type="number"
            min="1"
            step="1"
            :disabled="submitting"
          />
          <p v-if="errors.stock" class="mt-1 text-xs text-error">{{ errors.stock }}</p>
        </fieldset>
      </div>

      <fieldset class="fieldset">
        <legend class="fieldset-legend text-xs">所属类别</legend>
        <CategoryCascadeSelect
          v-model:l1-id="categoryL1"
          v-model:l2-id="categoryL2"
          :disabled="submitting"
        />
        <p v-if="errors.category" class="mt-1 text-xs text-error">{{ errors.category }}</p>
      </fieldset>

      <ImageFilePicker
        v-model="mainImages"
        label="主图"
        :max="1"
        hint="JPG/PNG，≤5MB；可选，但有图集时必须有主图"
        :disabled="submitting"
      />

      <ImageFilePicker
        v-model="galleryImages"
        label="图集"
        :max="8"
        hint="JPG/PNG，≤5MB；最多 8 张"
        :disabled="submitting"
      />

      <fieldset class="fieldset">
        <legend class="fieldset-legend text-xs">商品描述</legend>
        <DescriptionBlockEditor v-model="editorLines" :disabled="submitting" />
      </fieldset>

      <div class="flex justify-end pt-1">
        <button type="submit" class="btn btn-primary" :disabled="submitting">
          {{ submitLabel }}
        </button>
      </div>

      <div v-if="submitting" class="flex flex-col gap-1.5" role="status">
        <progress
          class="progress progress-primary w-full"
          :value="phase === 'uploading' && uploadTotal > 0 ? uploadedCount : undefined"
          :max="uploadTotal || undefined"
        ></progress>
        <p class="m-0 text-xs text-base-content/60">{{ progressText }}</p>
      </div>
    </form>

    <ConfirmDialog
      :open="confirmOpen"
      :message="attemptFailed ? M10.confirmPublishRetry : M10.confirmPublish"
      :confirm-text="attemptFailed ? '确认重试' : '确认发布'"
      :loading="submitting"
      @confirm="doPublish"
      @cancel="confirmOpen = false"
    />

    <ConfirmDialog
      :open="leaveConfirmOpen"
      :message="M10.confirmLeavePublish"
      confirm-text="离开"
      cancel-text="留下"
      danger
      @confirm="confirmLeave"
      @cancel="leaveConfirmOpen = false"
    />
  </div>
</template>