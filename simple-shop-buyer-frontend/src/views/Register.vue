<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { describeError, useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()

const username = ref('')
const password = ref('')
const phone = ref('')
const location = ref('')
const submitting = ref(false)
const errorText = ref('')

async function onSubmit() {
  if (!username.value.trim() || !password.value || !phone.value.trim()) {
    errorText.value = '用户名、密码、电话都要填'
    return
  }
  submitting.value = true
  errorText.value = ''
  try {
    // 注册成功后后端不返回令牌，这里再登录一次（交接文档 4.2）。
    await auth.registerAndLogin({
      username: username.value.trim(),
      password: password.value,
      phone: phone.value.trim(),
      default_location: location.value.trim() || undefined,
    })
    router.replace({ name: 'goods-list' })
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
      <h1 class="title">买家注册</h1>

      <label class="field">
        <span>用户名</span>
        <input v-model="username" autocomplete="username" />
      </label>

      <label class="field">
        <span>密码</span>
        <input v-model="password" type="password" autocomplete="new-password" />
      </label>

      <label class="field">
        <span>电话</span>
        <input v-model="phone" inputmode="tel" />
      </label>

      <label class="field">
        <span>默认交易地点（可不填）</span>
        <input v-model="location" />
      </label>

      <p v-if="errorText" class="error">{{ errorText }}</p>

      <button class="primary" :disabled="submitting" @click="onSubmit">
        {{ submitting ? '提交中…' : '注册并登录' }}
      </button>

      <p class="muted">
        已有账号？<RouterLink :to="{ name: 'login' }">去登录</RouterLink>
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
