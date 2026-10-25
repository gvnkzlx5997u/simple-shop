import { createPinia } from 'pinia'

/** 全局唯一的 Pinia 实例；路由守卫也要用它取 store。 */
export const pinia = createPinia()
