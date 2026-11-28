<script setup lang="ts">
import { ref } from 'vue'

import type { AddressData } from '../../api/types'
import { useAddressStore } from '../../stores/address'
import { describeError } from '../../utils/errorText'
import { validateAddress } from '../../validation/address'

/** address 为 null 表示新建。弹窗每次打开都重新挂载，所以初值直接取 props 即可。 */
const props = defineProps<{ address: AddressData | null }>()
const emit = defineEmits<{ saved: [created: boolean]; cancel: [] }>()

const addresses = useAddressStore()

const form = ref({
  receiver_name: props.address?.receiver_name ?? '',
  receiver_phone: props.address?.receiver_phone ?? '',
  detail_address: props.address?.detail_address ?? '',
})
const formError = ref('')
const saving = ref(false)

async function save(): Promise<void> {
  if (saving.value) return
  const reason = validateAddress(form.value)
  if (reason) {
    formError.value = reason
    return
  }
  saving.value = true
  formError.value = ''
  const payload = {
    receiver_name: form.value.receiver_name.trim(),
    receiver_phone: form.value.receiver_phone.trim(),
    detail_address: form.value.detail_address.trim(),
  }
  try {
    if (props.address) {
      await addresses.update(props.address.id, payload)
      emit('saved', false)
    } else {
      await addresses.create(payload)
      emit('saved', true)
    }
  } catch (err) {
    // 失败留在弹窗里，填过的内容不丢。
    formError.value = describeError(err)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="space-y-3" data-testid="address-form">
    <label class="block">
      <span class="mb-1 block text-base-content/60">收货人</span>
      <input v-model="form.receiver_name" class="input w-full" autocomplete="name" />
      <small class="mt-1 block text-xs text-base-content/60">必填，最多 50 字</small>
    </label>

    <label class="block">
      <span class="mb-1 block text-base-content/60">手机号</span>
      <input v-model="form.receiver_phone" class="input w-full" inputmode="tel" autocomplete="tel" />
      <small class="mt-1 block text-xs text-base-content/60">
        必填，7~20 位，其中至少 7 位数字（如 13800138000）
      </small>
    </label>

    <label class="block">
      <span class="mb-1 block text-base-content/60">详细地址</span>
      <input v-model="form.detail_address" class="input w-full" autocomplete="street-address" />
      <small class="mt-1 block text-xs text-base-content/60">
        必填，一行写完，最多 200 字；不拆省市区
      </small>
    </label>

    <p class="muted text-xs">默认地址在列表里点「设为默认」，表单里不设置。</p>

    <p v-if="formError" class="text-error">{{ formError }}</p>

    <div class="modal-action">
      <button class="btn btn-ghost" :disabled="saving" @click="emit('cancel')">取消</button>
      <button
        class="btn btn-primary"
        data-testid="address-save"
        :disabled="saving"
        @click="save"
      >
        {{ saving ? '保存中…' : '保存' }}
      </button>
    </div>
  </div>
</template>