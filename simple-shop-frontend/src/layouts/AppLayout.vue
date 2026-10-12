<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

function isActive(target: string): boolean {
  if (target === '/') {
    return route.path === '/'
  }
  return route.path === target || route.path.startsWith(target + '/')
}

async function onLogout(): Promise<void> {
  try {
    await auth.logout()
  } catch {
    // 退出登录失败也要回到登录页（本地会话已清空）
  }
  await router.push({ name: 'login' })
}
</script>

<template>
  <div class="layout">
    <aside class="layout__sidebar">
      <div class="layout__brand">SimpleShop<br />卖家后台</div>
      <nav class="layout__nav">
        <RouterLink to="/" class="layout__link" :class="{ 'layout__link--active': isActive('/') }">
          当前商品
        </RouterLink>
        <RouterLink
          to="/history"
          class="layout__link"
          :class="{ 'layout__link--active': isActive('/history') }"
        >
          历史商品
        </RouterLink>
        <RouterLink
          to="/password"
          class="layout__link"
          :class="{ 'layout__link--active': isActive('/password') }"
        >
          修改密码
        </RouterLink>
      </nav>
      <button type="button" class="btn layout__logout" @click="onLogout">退出登录</button>
    </aside>
    <main class="layout__main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
}

.layout__sidebar {
  display: flex;
  flex-direction: column;
  flex: 0 0 200px;
  padding: 20px 16px;
  border-right: 1px solid var(--border);
  background: var(--surface);
}

.layout__brand {
  margin-bottom: 24px;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.4;
}

.layout__nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.layout__link {
  padding: 8px 12px;
  border-radius: 6px;
  color: var(--text);
}

.layout__link:hover {
  background: #f3f4f6;
}

.layout__link--active {
  background: #eff6ff;
  color: var(--primary);
  font-weight: 600;
}

.layout__logout {
  margin-top: auto;
}

.layout__main {
  flex: 1;
  min-width: 0;
  padding: 24px;
}
</style>
