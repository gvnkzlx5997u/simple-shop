<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import AppIcon from '../components/common/AppIcon.vue'
import type { IconName } from '../components/common/AppIcon.vue'
import { useTheme } from '../composables/useTheme'
import { useAuthStore } from '../stores/auth'

interface NavItem {
  to: string
  label: string
  icon: IconName
  /** 归属该入口的路径前缀：商品详情、发布页也算「商品列表」这个域。 */
  matches: string[]
}

const NAV: NavItem[] = [
  { to: '/', label: '商品列表', icon: 'product', matches: ['/', '/goods', '/publish'] },
  { to: '/orders', label: '订单管理', icon: 'list', matches: ['/orders'] },
  { to: '/categories', label: '类别管理', icon: 'file', matches: ['/categories'] },
  { to: '/customers', label: '客户管理', icon: 'user', matches: ['/customers'] },
  { to: '/password', label: '修改密码', icon: 'key', matches: ['/password'] },
]

const SIDEBAR_KEY = 'simple-shop.seller.sidebar'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { isDark, toggle: toggleTheme } = useTheme()

function readCollapsed(): boolean {
  try {
    return localStorage.getItem(SIDEBAR_KEY) === 'collapsed'
  } catch {
    return false
  }
}

const collapsed = ref(readCollapsed())

watch(collapsed, (value) => {
  try {
    localStorage.setItem(SIDEBAR_KEY, value ? 'collapsed' : 'expanded')
  } catch {
    // 隐私模式下写不进去，只在本次会话生效
  }
})

/** 收起后文字不可见，按钮与图标的可读名称统一走 aria-label 与 tooltip。 */
const themeLabel = computed(() => (isDark.value ? '切换到浅色' : '切换到深色'))

function isActive(item: NavItem): boolean {
  return item.matches.some((target) =>
    target === '/' ? route.path === '/' : route.path === target || route.path.startsWith(target + '/'),
  )
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
  <div class="flex min-h-screen">
    <aside
      class="sticky top-0 flex h-screen shrink-0 flex-col border-r border-base-300 bg-base-100 py-4 transition-[width,padding] duration-200"
      :class="collapsed ? 'w-16 px-2' : 'w-[200px] px-3'"
    >
      <div class="mb-6 flex items-center gap-2" :class="collapsed ? 'justify-center' : 'px-1'">
        <AppIcon name="shop" class="text-[22px] text-primary" />
        <div v-if="!collapsed" class="min-w-0 leading-tight">
          <p class="truncate text-sm font-semibold">SimpleShop</p>
          <p class="truncate text-xs text-base-content/60">卖家后台</p>
        </div>
      </div>

      <ul class="menu menu-sm w-full gap-1 p-0">
        <li v-for="item in NAV" :key="item.to">
          <RouterLink
            :to="item.to"
            class="gap-2.5"
            :class="[
              isActive(item) ? 'menu-active font-semibold' : '',
              collapsed ? 'justify-center' : '',
            ]"
            :title="collapsed ? item.label : undefined"
            :aria-current="isActive(item) ? 'page' : undefined"
          >
            <AppIcon :name="item.icon" class="text-[18px]" />
            <span v-if="!collapsed" class="truncate">{{ item.label }}</span>
          </RouterLink>
        </li>
      </ul>

      <div class="mt-auto flex flex-col gap-1 pt-6" :class="collapsed ? 'items-center' : ''">
        <button
          type="button"
          class="btn btn-ghost btn-sm font-normal"
          :class="collapsed ? 'btn-square' : 'justify-start'"
          :title="collapsed ? themeLabel : undefined"
          :aria-label="themeLabel"
          @click="toggleTheme"
        >
          <AppIcon :name="isDark ? 'sun' : 'moon'" class="text-[16px]" />
          <span v-if="!collapsed" class="truncate">{{ themeLabel }}</span>
        </button>

        <button
          type="button"
          class="btn btn-ghost btn-sm font-normal"
          :class="collapsed ? 'btn-square' : 'justify-start'"
          :title="collapsed ? '退出登录' : undefined"
          aria-label="退出登录"
          @click="onLogout"
        >
          <AppIcon name="sign-out" class="text-[16px]" />
          <span v-if="!collapsed" class="truncate">退出登录</span>
        </button>

        <div class="divider my-0.5"></div>

        <button
          type="button"
          class="btn btn-ghost btn-sm font-normal"
          :class="collapsed ? 'btn-square' : 'justify-start'"
          :title="collapsed ? '展开侧栏' : undefined"
          :aria-label="collapsed ? '展开侧栏' : '收起侧栏'"
          :aria-expanded="!collapsed"
          @click="collapsed = !collapsed"
        >
          <AppIcon :name="collapsed ? 'arrow-right' : 'arrow-left'" class="text-[16px]" />
          <span v-if="!collapsed" class="truncate">收起侧栏</span>
        </button>
      </div>
    </aside>

    <main class="min-w-0 flex-1 p-6">
      <RouterView v-slot="{ Component }">
        <Transition name="route" mode="out-in">
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>
  </div>
</template>
