<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiError } from '../api/http'
import { publishGoods, uploadImage } from '../api/product'
import { ApiCode } from '../api/types'
import type { PublishGoodsRequest } from '../api/types'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import { M10 } from '../constants/messages'
import { useToast } from '../composables/useToast'
import { errorMessage } from '../utils/error'
import { validateGoodsDescription, validateGoodsName, validatePrice } from '../validation'

const MAX_IMAGE_BYTES = 5 * 1024 * 1024

const router = useRouter()
const toast = useToast()

const name = ref('')
const description = ref('')
const price = ref('')
const selectedFile = ref<File | null>(null)
const previewUrl = ref<string | null>(null)
const submitting = ref(false)
const confirmOpen = ref(false)
const pending = ref<PublishGoodsRequest | null>(null)
const errors = ref<{ name?: string; description?: string; price?: string; image?: string }>({})

/** 选图只做本地初步校验与预览；真正的上传在点击「发布」时进行。 */
function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }
  errors.value = { ...errors.value, image: undefined }

  const lower = file.name.toLowerCase()
  const looksImage =
    file.type === 'image/jpeg' ||
    file.type === 'image/png' ||
    lower.endsWith('.jpg') ||
    lower.endsWith('.jpeg') ||
    lower.endsWith('.png')
  if (!looksImage || file.size > MAX_IMAGE_BYTES) {
    errors.value = { ...errors.value, image: M10.imageInvalid }
    return
  }

  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
  selectedFile.value = file
  previewUrl.value = URL.createObjectURL(file)
}

onBeforeUnmount(() => {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
})

function onSubmit(): void {
  const nameResult = validateGoodsName(name.value)
  const descriptionResult = validateGoodsDescription(description.value)
  const priceResult = validatePrice(price.value)

  errors.value = {
    image: errors.value.image,
    name: nameResult.ok
      ? undefined
      : nameResult.code === ApiCode.GOODS_NAME_INVALID
        ? M10.nameInvalid
        : M10.nameRequired,
    description: descriptionResult.ok ? undefined : M10.descriptionInvalid,
    price: priceResult.ok
      ? undefined
      : priceResult.code === ApiCode.PRICE_OUT_OF_RANGE
        ? M10.priceInvalid
        : M10.priceRequired,
  }
  if (!nameResult.ok || !descriptionResult.ok || !priceResult.ok) {
    return
  }

  pending.value = {
    name: nameResult.value,
    description: descriptionResult.value,
    pic_url: null,
    price: priceResult.value,
  }
  confirmOpen.value = true
}

async function onConfirmPublish(): Promise<void> {
  if (!pending.value) {
    return
  }
  submitting.value = true
  try {
    // 有图则先上传拿到 pic_url，再带着它发布（两步接口）。
    let picUrl: string | null = null
    if (selectedFile.value) {
      const data = await uploadImage(selectedFile.value)
      picUrl = data.pic_url
    }
    await publishGoods({ ...pending.value, pic_url: picUrl })
    confirmOpen.value = false
    toast.success('发布成功')
    await router.push({ name: 'home' })
  } catch (caught) {
    confirmOpen.value = false
    const code = caught instanceof ApiError ? caught.code : 0
    if (code === ApiCode.GOODS_ALREADY_EXISTS) {
      toast.error(M10.goodsAlreadyExists)
    } else if (code === ApiCode.GOODS_NAME_INVALID) {
      errors.value = { ...errors.value, name: M10.nameInvalid }
    } else if (code === ApiCode.GOODS_DESCRIPTION_INVALID) {
      errors.value = { ...errors.value, description: M10.descriptionInvalid }
    } else if (code === ApiCode.IMAGE_INVALID) {
      errors.value = { ...errors.value, image: M10.imageInvalid }
    } else if (code === ApiCode.PRICE_OUT_OF_RANGE) {
      errors.value = { ...errors.value, price: M10.priceInvalid }
    } else {
      toast.error(errorMessage(caught))
    }
  } finally {
    submitting.value = false
  }
}

function onCancel(): void {
  void router.push({ name: 'home' })
}
</script>

<template>
  <div class="page">
    <h1 class="page__title">发布商品</h1>

    <form class="card publish" @submit.prevent="onSubmit">
      <div class="field">
        <label class="field__label" for="goods-name">名称</label>
        <input id="goods-name" v-model="name" class="input" type="text" maxlength="80" />
        <p v-if="errors.name" class="field__error">{{ errors.name }}</p>
      </div>

      <div class="field">
        <label class="field__label" for="goods-description">描述（可选）</label>
        <textarea id="goods-description" v-model="description" class="textarea"></textarea>
        <p v-if="errors.description" class="field__error">{{ errors.description }}</p>
      </div>

      <div class="field">
        <label class="field__label" for="goods-image">图片（可选，JPG／PNG，≤5MB）</label>
        <input
          id="goods-image"
          type="file"
          accept=".jpg,.jpeg,.png,image/jpeg,image/png"
          @change="onFileChange"
        />
        <p class="muted">图片将在点击「发布」后上传。</p>
        <p v-if="errors.image" class="field__error">{{ errors.image }}</p>
        <img v-if="previewUrl" class="publish__preview" :src="previewUrl" alt="图片预览" />
      </div>

      <div class="field">
        <label class="field__label" for="goods-price">价格（元）</label>
        <input
          id="goods-price"
          v-model="price"
          class="input"
          type="text"
          inputmode="decimal"
          placeholder="0.00"
        />
        <p v-if="errors.price" class="field__error">{{ errors.price }}</p>
      </div>

      <div class="publish__actions">
        <button type="button" class="btn" :disabled="submitting" @click="onCancel">取消</button>
        <button type="submit" class="btn btn--primary" :disabled="submitting">发布</button>
      </div>
    </form>

    <ConfirmDialog
      :open="confirmOpen"
      :message="M10.confirmPublish"
      :loading="submitting"
      @confirm="onConfirmPublish"
      @cancel="confirmOpen = false"
    />
  </div>
</template>

<style scoped>
.page__title {
  margin-bottom: 16px;
}

.publish {
  max-width: 640px;
}

.publish__preview {
  display: block;
  max-width: 200px;
  max-height: 200px;
  margin-top: 10px;
  border: 1px solid var(--border);
  border-radius: 6px;
}

.publish__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}
</style>
