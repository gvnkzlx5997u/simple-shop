<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '../stores/auth'
import { describeError } from '../utils/errorText'
import { validateRegister } from '../validation/register'

const auth = useAuthStore()
const router = useRouter()

const username = ref('')
const password = ref('')
const phone = ref('')
const location = ref('')
const submitting = ref(false)
const errorText = ref('')

async function onSubmit() {
  const reason = validateRegister({
    username: username.value,
    password: password.value,
    phone: phone.value,
    location: location.value,
  })
  if (reason) {
    errorText.value = reason
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
  <div class="mx-auto max-w-[960px] p-4">
    <div class="panel mx-auto mt-10 max-w-[360px] space-y-3">
      <h1 class="text-lg">买家注册</h1>

      <label class="block">
        <span class="mb-1 block text-base-content/60">用户名</span>
        <input v-model="username" class="input w-full" autocomplete="username" aria-label="用户名" />
        <small class="mt-1 block text-xs text-base-content/60">必填，最多 50 字，不能有空格</small>
      </label>

      <label class="block">
        <span class="mb-1 block text-base-content/60">密码</span>
        <input v-model="password" type="password" class="input w-full" autocomplete="new-password" aria-label="密码" />
        <small class="mt-1 block text-xs text-base-content/60">必填，至少 8 位</small>
      </label>

      <label class="block">
        <span class="mb-1 block text-base-content/60">电话</span>
        <input v-model="phone" class="input w-full" inputmode="tel" aria-label="电话" />
        <small class="mt-1 block text-xs text-base-content/60">
          必填，7~20 位，其中至少 7 位数字（如 13800138000）
        </small>
      </label>

      <label class="block">
        <span class="mb-1 block text-base-content/60">默认交易地点</span>
        <input v-model="location" class="input w-full" aria-label="默认交易地点" />
        <small class="mt-1 block text-xs text-base-content/60">可不填，最多 50 字</small>
      </label>

      <p v-if="errorText" class="text-error">{{ errorText }}</p>

      <button class="btn btn-primary w-full" :disabled="submitting" @click="onSubmit">
        {{ submitting ? '提交中…' : '注册并登录' }}
      </button>

      <p class="muted">
        已有账号？<RouterLink class="link link-primary" :to="{ name: 'login' }">去登录</RouterLink>
      </p>
    </div>
  </div>
</template>