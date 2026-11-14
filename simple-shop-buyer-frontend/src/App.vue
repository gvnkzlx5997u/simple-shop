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
        <RouterLink v-if="auth.isLoggedIn" to="/cart">购物车</RouterLink>
        <RouterLink v-if="auth.isLoggedIn" to="/favorites">收藏</RouterLink>
        <RouterLink v-if="auth.isLoggedIn" to="/orders">订单</RouterLink>
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
  background: var(--color-surface);
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

/*
 * 导航默认不用主色：主色留给「当前页」和真正的主操作（加入购物车、去结算）。
 * 四个链接全铺主色会让主色失去强调作用，也看不出当前在哪一页。
 */
.nav a {
  color: var(--color-text-weak);
}

.nav a:hover {
  color: var(--color-text);
}

.nav a.router-link-active {
  color: var(--color-primary);
  font-weight: 600;
}

.account {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
