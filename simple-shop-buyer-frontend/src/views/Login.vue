<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '../stores/auth'
import { describeError } from '../utils/errorText'
import { safeRedirect } from '../utils/redirect'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const username = ref('')
const password = ref('')
const submitting = ref(false)
const errorText = ref('')

async function onSubmit() {
  // 分开提示：只缺一项时告诉用户缺的是哪一项，不要笼统说"都要填"。
  const u = username.value.trim()
  if (!u && !password.value) {
    errorText.value = '请输入用户名和密码'
    return
  }
  if (!u) {
    errorText.value = '请输入用户名'
    return
  }
  if (!password.value) {
    errorText.value = '请输入密码'
    return
  }
  submitting.value = true
  errorText.value = ''
  try {
    await auth.login({ username: u, password: password.value })
    router.replace(safeRedirect(route.query.redirect) || { name: 'goods-list' })
  } catch (err) {
    errorText.value = describeError(err)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="mx-auto max-w-[960px] p-4">
    <div class="panel mx-auto mt-10 max-w-[360px] space-y-3">
      <h1 class="text-lg">买家登录</h1>

      <label class="block">
        <span class="mb-1 block text-base-content/60">用户名</span>
        <input
          v-model="username"
          class="input w-full"
          autocomplete="username"
          @keyup.enter="onSubmit"
        />
      </label>

      <label class="block">
        <span class="mb-1 block text-base-content/60">密码</span>
        <input
          v-model="password"
          type="password"
          class="input w-full"
          autocomplete="current-password"
          @keyup.enter="onSubmit"
        />
      </label>

      <p v-if="errorText" class="text-error">{{ errorText }}</p>

      <button class="btn btn-primary w-full" :disabled="submitting" @click="onSubmit">
        {{ submitting ? '登录中…' : '登录' }}
      </button>

      <p class="muted">
        还没有账号？<RouterLink class="link link-primary" :to="{ name: 'register' }">去注册</RouterLink>
      </p>
    </div>
  </div>
</template>