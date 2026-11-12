<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiError } from '../api/http'
import { ApiCode } from '../api/types'
import ConfirmDialog from '../components/common/ConfirmDialog.vue'
import { apiCodeMessage, M10 } from '../constants/messages'
import { useToast } from '../composables/useToast'
import { useAuthStore } from '../stores/auth'
import { errorMessage } from '../utils/error'
import { validateNewPassword, validateOldPassword } from '../validation'

const router = useRouter()
const auth = useAuthStore()
const toast = useToast()

const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const errors = ref<{ old?: string; next?: string; confirm?: string }>({})
const confirmOpen = ref(false)
const submitting = ref(false)

function onSubmit(): void {
  const oldResult = validateOldPassword(oldPassword.value)
  const newResult = validateNewPassword(newPassword.value)
  errors.value = {
    old: oldResult.ok
      ? undefined
      : oldResult.code === ApiCode.OLD_PASSWORD_MISMATCH
        ? M10.oldPasswordMismatch
        : '请填写原密码',
    next: newResult.ok
      ? undefined
      : newResult.code === ApiCode.PASSWORD_INVALID
        ? M10.newPasswordTooShort
        : '请填写新密码',
    confirm: newPassword.value === confirmPassword.value ? undefined : M10.passwordMismatch,
  }
  if (!oldResult.ok || !newResult.ok || errors.value.confirm) {
    return
  }
  confirmOpen.value = true
}

async function onConfirmChange(): Promise<void> {
  submitting.value = true
  try {
    await auth.changePassword(oldPassword.value, newPassword.value)
    confirmOpen.value = false
    toast.success(M10.changePasswordSuccess)
    await router.push({ name: 'login' })
  } catch (caught) {
    confirmOpen.value = false
    const code = caught instanceof ApiError ? caught.code : 0
    if (code === ApiCode.OLD_PASSWORD_MISMATCH) {
      errors.value = { ...errors.value, old: M10.oldPasswordMismatch }
    } else if (code === ApiCode.PASSWORD_INVALID) {
      errors.value = { ...errors.value, next: M10.newPasswordTooShort }
    } else {
      toast.error(errorMessage(caught))
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <header>
      <h1 class="text-lg font-semibold">修改密码</h1>
      <p class="text-xs text-base-content/60">修改成功后当前会话会失效，需要重新登录。</p>
    </header>

    <form class="panel flex max-w-[420px] flex-col gap-4" @submit.prevent="onSubmit">
      <fieldset class="fieldset">
        <legend class="fieldset-legend text-xs">原密码</legend>
        <input
          id="old-password"
          v-model="oldPassword"
          class="input w-full"
          type="password"
          autocomplete="current-password"
        />
        <p v-if="errors.old" class="mt-1 text-xs text-error">{{ errors.old }}</p>
      </fieldset>

      <fieldset class="fieldset">
        <legend class="fieldset-legend text-xs">新密码（至少 8 位）</legend>
        <input
          id="new-password"
          v-model="newPassword"
          class="input w-full"
          type="password"
          autocomplete="new-password"
        />
        <p v-if="errors.next" class="mt-1 text-xs text-error">{{ errors.next }}</p>
      </fieldset>

      <fieldset class="fieldset">
        <legend class="fieldset-legend text-xs">确认新密码</legend>
        <input
          id="confirm-password"
          v-model="confirmPassword"
          class="input w-full"
          type="password"
          autocomplete="new-password"
        />
        <p v-if="errors.confirm" class="mt-1 text-xs text-error">{{ errors.confirm }}</p>
      </fieldset>

      <button type="submit" class="btn btn-primary mt-1 w-full" :disabled="submitting">
        提交修改
      </button>
    </form>

    <ConfirmDialog
      :open="confirmOpen"
      :message="M10.confirmChangePassword"
      :loading="submitting"
      @confirm="onConfirmChange"
      @cancel="confirmOpen = false"
    />
  </div>
</template>
