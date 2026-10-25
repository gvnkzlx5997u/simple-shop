<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiError } from '../api/http'
import { ApiCode } from '../api/types'
import { useToast } from '../composables/useToast'
import { apiCodeMessage, M10 } from '../constants/messages'
import { useAuthStore } from '../stores/auth'
import { errorMessage } from '../utils/error'
import { validateAccount, validateLoginPassword } from '../validation'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const toast = useToast()

const account = ref('')
const password = ref('')
const errors = ref<{ account?: string; password?: string }>({})
const submitting = ref(false)

const sessionExpired = computed(() => route.query.reason === 'expired')

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
  <div class="login">
    <form class="login__card card" @submit.prevent="onSubmit">
      <h1 class="login__title">SimpleShop 卖家后台</h1>
      <p v-if="sessionExpired" class="login__notice">{{ M10.sessionExpired }}</p>

      <div class="field">
        <label class="field__label" for="account">账号</label>
        <input id="account" v-model="account" class="input" type="text" autocomplete="username" />
        <p v-if="errors.account" class="field__error">{{ errors.account }}</p>
      </div>

      <div class="field">
        <label class="field__label" for="password">密码</label>
        <input
          id="password"
          v-model="password"
          class="input"
          type="password"
          autocomplete="current-password"
        />
        <p v-if="errors.password" class="field__error">{{ errors.password }}</p>
      </div>

      <button type="submit" class="btn btn--primary login__submit" :disabled="submitting">
        {{ submitting ? '登录中…' : '登录' }}
      </button>
    </form>
  </div>
</template>

<style scoped>
.login {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
}

.login__card {
  width: 100%;
  max-width: 360px;
}

.login__title {
  margin-bottom: 20px;
  font-size: 18px;
  text-align: center;
}

.login__notice {
  margin: 0 0 16px;
  padding: 8px 12px;
  border: 1px solid #fde68a;
  border-radius: 6px;
  background: #fffbeb;
  color: var(--warning);
  font-size: 13px;
}

.login__submit {
  width: 100%;
}
</style>
