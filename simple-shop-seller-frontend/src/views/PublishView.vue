<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import { publishGoods } from '../api/goods'
import { uploadMedia } from '../api/media'
import { ApiCode } from '../api/types'
import type { GoodsPublishRequest, MediaPurpose } from '../api/types'
import ConfirmDialog from '../components/common/ConfirmDialog.vue'
import CategoryCascadeSelect from '../components/goods/CategoryCascadeSelect.vue'
import DescriptionBlockEditor from '../components/goods/DescriptionBlockEditor.vue'
import ImageFilePicker from '../components/goods/ImageFilePicker.vue'
import {
  countDescriptionText,
  createTextLine,
  pendingMediaLines,
  toDescriptionBlocks,
} from '../components/goods/descriptionEditor'
import type { DescriptionEditorLine, PickedImage } from '../components/goods/descriptionEditor'
import { apiCodeMessage, M10 } from '../constants/messages'
import { useToast } from '../composables/useToast'
import { errorMessage } from '../utils/error'
import {
  validateDescriptionBlocks,
  validateGoodsName,
  validateImageSelection,
  validatePrice,
  validatePublishStock,
} from '../validation'
import { DESCRIPTION_TOTAL_MAX_CHARS } from '../validation/goods'
import { hasAngleBracket } from '../validation/shared'

const router = useRouter()
const toast = useToast()

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
const submitting = ref(false)
const progressText = ref('')

/** 校验通过后暂存的发布基干,确认弹窗后使用。 */
const pendingPayload = ref<GoodsPublishRequest | null>(null)

const submitLabel = computed(() =>
  submitting.value ? progressText.value || '处理中…' : '发布商品',
)

function onSubmit(): void {
  const nameResult = validateGoodsName(name.value)
  const priceResult = validatePrice(price.value)
  const stockResult = validatePublishStock(stock.value)

  errors.value = {
    name: nameResult.ok
      ? undefined
      : nameResult.code === ApiCode.PARAM_INVALID
        ? M10.nameRequired
        : apiCodeMessage(nameResult.code),
    price: priceResult.ok
      ? undefined
      : priceResult.code === ApiCode.PARAM_INVALID
        ? M10.priceRequired
        : apiCodeMessage(priceResult.code),
    stock: stockResult.ok ? undefined : apiCodeMessage(stockResult.code),
    category: categoryL1.value
      ? categoryL2.value
        ? undefined
        : M10.categoryL2Required
      : M10.categoryL1Required,
  }
  if (!nameResult.ok || !priceResult.ok || !stockResult.ok || !categoryL2.value) {
    return
  }

  // 描述与图片的快速预检(结构性的最终校验在上传回填 URL 后进行)。
  if (countDescriptionText(editorLines.value) > DESCRIPTION_TOTAL_MAX_CHARS) {
    toast.error(M10.descriptionInvalid)
    return
  }
  const textHasBracket = editorLines.value.some(
    (line) => line.kind === 'text' && hasAngleBracket(line.content),
  )
  if (textHasBracket) {
    toast.error(M10.descriptionInvalid)
    return
  }
  if (galleryImages.value.length > 0 && mainImages.value.length === 0) {
    toast.error(M10.mainImageRequired)
    return
  }

  pendingPayload.value = {
    name: nameResult.value,
    price: Number(priceResult.value),
    stock: stockResult.value,
    category_id: categoryL2.value,
  }
  confirmOpen.value = true
}

async function doPublish(): Promise<void> {
  const payload = pendingPayload.value
  if (!payload) {
    return
  }
  confirmOpen.value = false
  submitting.value = true
  try {
    // 1) 统一上传:主图 → 图集 → 描述媒体(按行序)。串行上传便于进度提示;
    //    失败即中止,已成功者的 url 已缓存,重试只传剩余。
    const uploads: { purpose: MediaPurpose; run: () => Promise<void> }[] = []
    for (const item of mainImages.value) {
      if (!item.url) {
        uploads.push({
          purpose: 'GOODS_IMAGE',
          run: async () => {
            item.url = (await uploadMedia(item.file, 'GOODS_IMAGE')).url
          },
        })
      }
    }
    for (const item of galleryImages.value) {
      if (!item.url) {
        uploads.push({
          purpose: 'GOODS_IMAGE',
          run: async () => {
            item.url = (await uploadMedia(item.file, 'GOODS_IMAGE')).url
          },
        })
      }
    }
    for (const line of pendingMediaLines(editorLines.value)) {
      uploads.push({
        purpose: 'DESCRIPTION_MEDIA',
        run: async () => {
          line.url = (await uploadMedia(line.file, 'DESCRIPTION_MEDIA')).url
        },
      })
    }

    for (let i = 0; i < uploads.length; i += 1) {
      progressText.value = `上传媒体 ${i + 1}/${uploads.length}…`
      const task = uploads[i]
      if (task) {
        await task.run()
      }
    }

    // 2) 终检:此刻所有 URL 已回填,结构与前缀校验完整可做。
    const mainUrl = mainImages.value[0]?.url ?? null
    const galleryUrls = galleryImages.value
      .map((item) => item.url)
      .filter((url): url is string => url != null)
    const imageCheck = validateImageSelection(mainUrl, galleryUrls)
    if (!imageCheck.ok) {
      toast.error(apiCodeMessage(imageCheck.code))
      return
    }
    const blocks = toDescriptionBlocks(editorLines.value)
    const descCheck = validateDescriptionBlocks(blocks)
    if (!descCheck.ok) {
      toast.error(apiCodeMessage(descCheck.code))
      return
    }

    // 3) 发布
    progressText.value = '发布中…'
    await publishGoods({
      ...payload,
      description: blocks,
      main_image: mainUrl,
      gallery_images: galleryUrls,
    })
    toast.success(M10.publishSuccess)
    await router.replace({ name: 'goods-list' })
  } catch (caught) {
    toast.error(errorMessage(caught))
  } finally {
    submitting.value = false
    progressText.value = ''
  }
}
</script>

<template>
  <div class="page">
    <div class="page__header">
      <h1 class="page__title">发布商品</h1>
      <RouterLink to="/" class="btn">返回列表</RouterLink>
    </div>

    <form class="card publish-form" @submit.prevent="onSubmit">
      <div class="field">
        <label class="field__label" for="goods-name">商品名称</label>
        <input
          id="goods-name"
          v-model="name"
          class="input"
          type="text"
          maxlength="60"
          :disabled="submitting"
        />
        <p v-if="errors.name" class="field__error">{{ errors.name }}</p>
      </div>

      <div class="publish-form__row">
        <div class="field">
          <label class="field__label" for="goods-price">价格(元)</label>
          <input
            id="goods-price"
            v-model="price"
            class="input"
            type="text"
            inputmode="decimal"
            placeholder="0.00 ~ 999999.99"
            :disabled="submitting"
          />
          <p v-if="errors.price" class="field__error">{{ errors.price }}</p>
        </div>
        <div class="field">
          <label class="field__label" for="goods-stock">库存(件)</label>
          <input
            id="goods-stock"
            v-model.number="stock"
            class="input"
            type="number"
            min="1"
            step="1"
            :disabled="submitting"
          />
          <p v-if="errors.stock" class="field__error">{{ errors.stock }}</p>
        </div>
      </div>

      <div class="field">
        <span class="field__label">所属类别</span>
        <CategoryCascadeSelect
          v-model:l1-id="categoryL1"
          v-model:l2-id="categoryL2"
          :disabled="submitting"
        />
        <p v-if="errors.category" class="field__error">{{ errors.category }}</p>
      </div>

      <div class="field">
        <ImageFilePicker
          v-model="mainImages"
          label="主图"
          :max="1"
          hint="JPG/PNG,≤5MB;可选,但有图集时必须有主图"
          :disabled="submitting"
        />
      </div>

      <div class="field">
        <ImageFilePicker
          v-model="galleryImages"
          label="图集"
          :max="8"
          hint="JPG/PNG,≤5MB;最多 8 张"
          :disabled="submitting"
        />
      </div>

      <div class="field">
        <span class="field__label">商品描述</span>
        <DescriptionBlockEditor v-model="editorLines" />
      </div>

      <div class="publish-form__actions">
        <button type="submit" class="btn btn--primary" :disabled="submitting">
          {{ submitLabel }}
        </button>
      </div>
    </form>

    <ConfirmDialog
      :open="confirmOpen"
      :message="M10.confirmPublish"
      confirm-text="确认发布"
      :loading="submitting"
      @confirm="doPublish"
      @cancel="confirmOpen = false"
    />
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

.publish-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 720px;
}

.publish-form__row {
  display: flex;
  gap: 16px;
}

.publish-form__row .field {
  flex: 1;
}

.publish-form__actions {
  display: flex;
  justify-content: flex-end;
}
</style>
