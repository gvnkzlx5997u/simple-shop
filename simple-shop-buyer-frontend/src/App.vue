<script setup lang="ts">
import { useRouter } from 'vue-router'

import AppIcon from './components/common/AppIcon.vue'
import { useTheme } from './composables/useTheme'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
const router = useRouter()
const { isDark, toggle } = useTheme()

async function onLogout() {
  await auth.logout()
  router.push({ name: 'goods-list' })
}
</script>

<template>
  <header class="sticky top-0 z-40 border-b border-base-300 bg-base-100">
    <!-- 桌面单行：品牌 + 导航(flex-1) + 账号(ml-auto);窄屏导航整行换到第二行，不做逐字折行。 -->
    <div class="mx-auto flex max-w-[960px] flex-wrap items-center gap-x-5 gap-y-1 px-4 py-2">
      <RouterLink class="whitespace-nowrap text-base font-semibold text-base-content" to="/goods">
        在线购物
      </RouterLink>

      <nav class="order-last flex w-full flex-wrap items-center gap-4 sm:order-none sm:w-auto sm:flex-1">
        <RouterLink class="nav-link" to="/goods">商品</RouterLink>
        <RouterLink v-if="auth.isLoggedIn" class="nav-link" to="/cart">购物车</RouterLink>
        <RouterLink v-if="auth.isLoggedIn" class="nav-link" to="/favorites">收藏</RouterLink>
        <RouterLink v-if="auth.isLoggedIn" class="nav-link" to="/orders">订单</RouterLink>
      </nav>

      <div class="ml-auto flex items-center gap-3">
        <button
          class="btn btn-ghost btn-sm btn-square"
          :aria-label="isDark ? '切换到浅色' : '切换到深色'"
          :title="isDark ? '切换到浅色' : '切换到深色'"
          @click="toggle"
        >
          <AppIcon :name="isDark ? 'sun' : 'moon'" />
        </button>

        <template v-if="auth.isLoggedIn">
          <span class="muted text-sm">{{ auth.username }}</span>
          <button class="btn btn-outline btn-sm" @click="onLogout">退出</button>
        </template>
        <template v-else>
          <RouterLink class="btn btn-ghost btn-sm" :to="{ name: 'login' }">登录</RouterLink>
          <RouterLink class="btn btn-primary btn-sm" :to="{ name: 'register' }">注册</RouterLink>
        </template>
      </div>
    </div>
  </header>

  <main>
    <RouterView />
  </main>
</template>

<style scoped>
/*
 * 导航默认不用主色：主色留给「当前页」和真正的主操作（加入购物车、去结算）。
 * 用纯语义变量而非 @apply：SFC scoped 是独立样式上下文，@apply 需要 @reference 才能解析工具类。
 */
.nav-link {
  color: color-mix(in oklab, var(--color-base-content) 60%, transparent);
  transition: color 150ms;
  white-space: nowrap;
}

.nav-link:hover {
  color: var(--color-base-content);
}

.nav-link.router-link-active {
  color: var(--color-primary);
  font-weight: 600;
}
</style>