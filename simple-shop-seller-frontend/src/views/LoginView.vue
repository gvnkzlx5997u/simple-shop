<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiError } from '../api/http'
import { ApiCode } from '../api/types'
import AppIcon from '../components/common/AppIcon.vue'
import { useTheme } from '../composables/useTheme'
import { useToast } from '../composables/useToast'
import { apiCodeMessage, M10 } from '../constants/messages'
import { useAuthStore } from '../stores/auth'
import { errorMessage } from '../utils/error'
import { validateAccount, validateLoginPassword } from '../validation'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const toast = useToast()
const { isDark, toggle: toggleTheme } = useTheme()

const account = ref('')
const password = ref('')
const errors = ref<{ account?: string; password?: string }>({})
const submitting = ref(false)

const sessionExpired = computed(() => route.query.reason === 'expired')
const themeLabel = computed(() => (isDark.value ? '切换到浅色' : '切换到深色'))

async function onSubmit(): Promise<void> {
  const accountResult = validateAccount(account.value)
  const passwordResult = validateLoginPassword(password.value)
  errors.value = {
    account: accountResult.ok ? undefined : apiCodeMessage(accountResult.code),
    password: passwordResult.ok ? undefined : apiCodeMessage(passwordResult.code),
  }
  if (!accountResult.ok || !passwordResult.ok) {
    return
  }
  submitting.value = true
  try {
    await auth.login(accountResult.value, passwordResult.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.push(redirect)
  } catch (error) {
    if (error instanceof ApiError && error.code === ApiCode.LOGIN_FAILED) {
      errors.value = { password: M10.loginFailed }
    } else {
      toast.error(errorMessage(error))
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="relative flex min-h-screen items-center justify-center p-6">
    <!-- 登录页没有侧栏，主题开关单独放右上角，否则未登录时没法切换主题。 -->
    <button
      type="button"
      class="btn btn-circle btn-ghost btn-sm absolute top-5 right-5"
      :aria-label="themeLabel"
      :title="themeLabel"
      @click="toggleTheme"
    >
      <AppIcon :name="isDark ? 'sun' : 'moon'" class="text-[16px]" />
    </button>

    <form
      class="card w-full max-w-sm border border-base-300 bg-base-100 shadow-sm"
      @submit.prevent="onSubmit"
    >
      <div class="card-body gap-5 p-6">
        <div class="flex items-center gap-2.5">
          <AppIcon name="shop" class="text-[28px] text-primary" />
          <div class="leading-tight">
            <p class="text-base font-semibold">SimpleShop</p>
            <p class="text-xs text-base-content/60">卖家后台</p>
          </div>
        </div>

        <p v-if="sessionExpired" class="alert alert-warning alert-soft text-sm">
          {{ M10.sessionExpired }}
        </p>

        <fieldset class="fieldset">
          <legend class="fieldset-legend text-xs">账号</legend>
          <input
            id="account"
            v-model="account"
            class="input w-full"
            type="text"
            autocomplete="username"
          />
          <p v-if="errors.account" class="mt-1 text-xs text-error">{{ errors.account }}</p>
        </fieldset>

        <fieldset class="fieldset">
          <legend class="fieldset-legend text-xs">密码</legend>
          <input
            id="password"
            v-model="password"
            class="input w-full"
            type="password"
            autocomplete="current-password"
          />
          <p v-if="errors.password" class="mt-1 text-xs text-error">{{ errors.password }}</p>
        </fieldset>

        <button type="submit" class="btn btn-primary mt-1 w-full" :disabled="submitting">
          {{ submitting ? '登录中…' : '登录' }}
        </button>
      </div>
    </form>
  </div>
</template>
