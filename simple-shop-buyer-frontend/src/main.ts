import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from './App.vue'
import { onUnauthorized } from './api/http'
import router from './router'
import './assets/base.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)

// 交接文档要求：401 / code 10002 一律统一跳登录，不在各页面单独处理。
onUnauthorized(() => {
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }
})

app.mount('#app')
