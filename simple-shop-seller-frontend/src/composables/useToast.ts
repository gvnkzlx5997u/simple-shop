import { ref } from 'vue'

export type ToastTone = 'success' | 'error' | 'info'

export interface ToastItem {
  id: number
  message: string
  tone: ToastTone
}

// 模块级单例，任意组件调用 useToast() 共享同一组提示。
const items = ref<ToastItem[]>([])
let nextId = 1

/** 同文案去重窗口(毫秒)。 */
const DUPLICATE_WINDOW_MS = 1500
let lastMessage: string | null = null
let lastPushedAt = 0

function remove(id: number): void {
  items.value = items.value.filter((item) => item.id !== id)
}

function push(message: string, tone: ToastTone = 'info', duration = 3000): void {
  // 并发请求同时失败(如会话过期)时,多个调用点会推入同文案提示;短窗口内去重只保留一条。
  const now = Date.now()
  if (lastMessage === message && now - lastPushedAt < DUPLICATE_WINDOW_MS) {
    return
  }
  lastMessage = message
  lastPushedAt = now

  const id = nextId
  nextId += 1
  items.value = items.value.concat({ id, message, tone })
  if (duration > 0) {
    window.setTimeout(() => remove(id), duration)
  }
}

export function useToast() {
  return {
    items,
    remove,
    success: (message: string) => push(message, 'success'),
    error: (message: string) => push(message, 'error'),
    info: (message: string) => push(message, 'info'),
  }
}
