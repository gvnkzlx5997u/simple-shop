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
  <div class="page">
    <h1 class="page__title">修改密码</h1>

    <form class="card password" @submit.prevent="onSubmit">
      <div class="field">
        <label class="field__label" for="old-password">原密码</label>
        <input
          id="old-password"
          v-model="oldPassword"
          class="input"
          type="password"
          autocomplete="current-password"
        />
        <p v-if="errors.old" class="field__error">{{ errors.old }}</p>
      </div>

      <div class="field">
        <label class="field__label" for="new-password">新密码（至少 8 位）</label>
        <input
          id="new-password"
          v-model="newPassword"
          class="input"
          type="password"
          autocomplete="new-password"
        />
        <p v-if="errors.next" class="field__error">{{ errors.next }}</p>
      </div>

      <div class="field">
        <label class="field__label" for="confirm-password">确认新密码</label>
        <input
          id="confirm-password"
          v-model="confirmPassword"
          class="input"
          type="password"
          autocomplete="new-password"
        />
        <p v-if="errors.confirm" class="field__error">{{ errors.confirm }}</p>
      </div>

      <button type="submit" class="btn btn--primary" :disabled="submitting">提交修改</button>
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

<style scoped>
.page__title {
  margin-bottom: 16px;
}

.password {
  max-width: 420px;
}
</style>
