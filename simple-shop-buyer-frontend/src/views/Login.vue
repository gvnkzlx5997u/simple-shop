<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { describeError, useAuthStore } from '../stores/auth'

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
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    router.replace(redirect || { name: 'goods-list' })
  } catch (err) {
    errorText.value = describeError(err)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="card form-card stack">
      <h1 class="title">买家登录</h1>

      <label class="field">
        <span>用户名</span>
        <input v-model="username" autocomplete="username" @keyup.enter="onSubmit" />
      </label>

      <label class="field">
        <span>密码</span>
        <input
          v-model="password"
          type="password"
          autocomplete="current-password"
          @keyup.enter="onSubmit"
        />
      </label>

      <p v-if="errorText" class="error">{{ errorText }}</p>

      <button class="primary" :disabled="submitting" @click="onSubmit">
        {{ submitting ? '登录中…' : '登录' }}
      </button>

      <p class="muted">
        还没有账号？<RouterLink :to="{ name: 'register' }">去注册</RouterLink>
      </p>
    </div>
  </div>
</template>

<style scoped>
.form-card {
  max-width: 360px;
  margin: 40px auto;
}

.title {
  font-size: 18px;
  margin: 0 0 8px;
}

.field {
  display: block;
}

.field span {
  display: block;
  margin-bottom: 4px;
  color: var(--color-text-weak);
}
</style>
