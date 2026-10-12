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
onUnauthorized(() => {
  if (router.currentRoute.value.name !== 'login') {
    useToast().info('登录已超时，请重新登录')
    void router.push({ name: 'login', query: { reason: 'expired' } })
  }
})

app.mount('#app')
