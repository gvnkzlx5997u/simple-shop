import { createApp } from 'vue'

import App from './App.vue'
import router from './router'
import './assets/base.css'
import { onUnauthorized } from './api/http'
import { useToast } from './composables/useToast'
import { pinia } from './stores'

const app = createApp(App)

app.use(pinia)
app.use(router)

// 会话失效（10002）统一回登录页；本地会话由 auth store 同步清空。
// 单飞标记:并发请求同时 401 时,只提示并跳转一次。
let redirectingToLogin = false
onUnauthorized(() => {
  const current = router.currentRoute.value
  if (redirectingToLogin || current.name === 'login') {
    return
  }
  redirectingToLogin = true
  useToast().info('登录已超时，请重新登录')
  void router.push({ name: 'login', query: { reason: 'expired' } }).finally(() => {
    redirectingToLogin = false
  })
})

app.mount('#app')
