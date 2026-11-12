import { computed, ref } from 'vue'

export type ThemeName = 'shop-light' | 'shop-dark'

/** 深浅主题的本地持久化键；与 index.html 内联脚本共用。 */
const STORAGE_KEY = 'simple-shop.seller.theme'
const DARK_QUERY = '(prefers-color-scheme: dark)'

function safeStorage(): Storage | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

function readStored(): ThemeName | null {
  const raw = safeStorage()?.getItem(STORAGE_KEY)
  return raw === 'shop-light' || raw === 'shop-dark' ? raw : null
}

function systemTheme(): ThemeName {
  const query = window.matchMedia?.(DARK_QUERY)
  return query?.matches ? 'shop-dark' : 'shop-light'
}

/**
 * 初值优先取 index.html 已经写好的 data-theme；没取到再退到本地偏好，最后跟系统。
 * 这样首屏不闪，Vue 挂载后也不会把主题改回去。
 */
function initialTheme(): ThemeName {
  const current = document.documentElement.dataset.theme
  if (current === 'shop-light' || current === 'shop-dark') {
    return current
  }
  return readStored() ?? systemTheme()
}

// 模块级单例，与 useToast 同风格：任意组件调用 useTheme() 共享同一份状态。
const theme = ref<ThemeName>(initialTheme())

function apply(next: ThemeName): void {
  theme.value = next
  document.documentElement.dataset.theme = next
  try {
    safeStorage()?.setItem(STORAGE_KEY, next)
  } catch {
    // 隐私模式下写不进去，只在本次会话生效。
  }
}

// 用户没手动选过时跟随系统切换；手动选过就尊重用户的选择。
window.matchMedia?.(DARK_QUERY).addEventListener?.('change', () => {
  if (readStored() === null) {
    theme.value = systemTheme()
    document.documentElement.dataset.theme = theme.value
  }
})

export function useTheme() {
  return {
    theme,
    isDark: computed(() => theme.value === 'shop-dark'),
    setTheme: apply,
    toggle: () => apply(theme.value === 'shop-dark' ? 'shop-light' : 'shop-dark'),
  }
}
