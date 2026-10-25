<script setup lang="ts">
import { useRouter } from 'vue-router'

import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
const router = useRouter()

async function onLogout() {
  await auth.logout()
  router.push({ name: 'goods-list' })
}
</script>

<template>
  <header class="site-header">
    <div class="header-inner">
      <RouterLink class="brand" to="/goods">在线购物</RouterLink>

      <nav class="nav">
        <RouterLink to="/goods">商品</RouterLink>
        <RouterLink v-if="auth.isLoggedIn" to="/history">我的历史</RouterLink>
      </nav>

      <div class="account">
        <template v-if="auth.isLoggedIn">
          <span class="muted">{{ auth.username }}</span>
          <button @click="onLogout">退出</button>
        </template>
        <template v-else>
          <RouterLink :to="{ name: 'login' }">登录</RouterLink>
          <RouterLink :to="{ name: 'register' }">注册</RouterLink>
        </template>
      </div>
    </div>
  </header>

  <main>
    <RouterView />
  </main>
</template>

<style scoped>
.site-header {
  background: #fff;
  border-bottom: 1px solid var(--color-border);
}

.header-inner {
  max-width: 960px;
  margin: 0 auto;
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 24px;
}

.brand {
  font-weight: 600;
  font-size: 16px;
  color: var(--color-text);
}

.nav {
  display: flex;
  gap: 16px;
  flex: 1;
}

.account {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
