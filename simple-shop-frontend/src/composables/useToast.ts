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

function remove(id: number): void {
  items.value = items.value.filter((item) => item.id !== id)
}

function push(message: string, tone: ToastTone = 'info', duration = 3000): void {
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
