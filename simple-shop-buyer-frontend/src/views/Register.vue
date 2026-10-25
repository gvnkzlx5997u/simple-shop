<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { describeError, useAuthStore } from '../stores/auth'

/*
 * 字段规则与后端 AccountRules 保持一致（交接文档没写，这里是照后端源码抄的）：
 *   用户名：非空、≤50 字、不含空格/换行/尖括号
 *   密码  ：≥8 字（后端另有 UTF-8 ≤72 字节的限制，正常使用不会碰到）
 *   电话  ：7~20 位、只能用数字和短横
 *   交易地点：可选，≤50 字，不含换行/尖括号
 *
 * ⚠️ 电话额外加了一条后端正则没有的约束：**数字至少 7 位**。
 * 后端的 `[0-9-]{7,20}` 允许 "-------" 这种全短横的值（实测能注册成功），
 * 而电话是商家的交易联系方式，全短横等于没有电话，属于脏数据。
 * 前端比后端严是安全的；根治仍需后端正则加一条。
 */
const USERNAME_MAX = 50
const PASSWORD_MIN = 8
const LOCATION_MAX = 50
const PHONE_MIN_DIGITS = 7
const PHONE_RE = /^[0-9-]{7,20}$/

const auth = useAuthStore()
const router = useRouter()

const username = ref('')
const password = ref('')
const phone = ref('')
const location = ref('')
const submitting = ref(false)
const errorText = ref('')

/** 返回空串表示通过，否则返回给用户看的具体原因。 */
function validate(): string {
  const u = username.value.trim()
  if (!u) return '请填用户名'
  if (u.length > USERNAME_MAX) return `用户名最多 ${USERNAME_MAX} 个字符`
  if (/[\s<>]/.test(u)) return '用户名不能包含空格、换行或尖括号'

  if (!password.value) return '请填密码'
  if (password.value.length < PASSWORD_MIN) return `密码至少 ${PASSWORD_MIN} 位`

  const p = phone.value.trim()
  if (!p) return '请填电话'
  if (!PHONE_RE.test(p)) return '电话请填 7~20 位，只能用数字和短横'
  if ((p.match(/[0-9]/g) ?? []).length < PHONE_MIN_DIGITS) {
    return `电话里至少要有 ${PHONE_MIN_DIGITS} 位数字`
  }

  const loc = location.value.trim()
  if (loc && (loc.length > LOCATION_MAX || /[<>]/.test(loc) || /[\r\n]/.test(loc))) {
    return `默认交易地点最多 ${LOCATION_MAX} 字，不能含换行或尖括号`
  }
  return ''
}

async function onSubmit() {
  const reason = validate()
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
  <div class="page">
    <div class="card form-card stack">
      <h1 class="title">买家注册</h1>

      <label class="field">
        <span>用户名</span>
        <input v-model="username" autocomplete="username" />
        <small class="hint">必填，最多 50 字，不能有空格</small>
      </label>

      <label class="field">
        <span>密码</span>
        <input v-model="password" type="password" autocomplete="new-password" />
        <small class="hint">必填，至少 8 位</small>
      </label>

      <label class="field">
        <span>电话</span>
        <input v-model="phone" inputmode="tel" />
        <small class="hint">必填，7~20 位，其中至少 7 位数字（如 13800138000）</small>
      </label>

      <label class="field">
        <span>默认交易地点</span>
        <input v-model="location" />
        <small class="hint">可不填，最多 50 字</small>
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

.hint {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-weak);
}
</style>
