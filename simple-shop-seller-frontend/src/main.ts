import { createApp } from 'vue'

import App from './App.vue'
import router from './router'
import './assets/base.css'
import { onUnauthorized } from './api/http'
import { useToast } from './composables/useToast'
import { M10 } from './constants/messages'
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
  useToast().info(M10.sessionExpired)
  void router.push({ name: 'login', query: { reason: 'expired' } }).finally(() => {
    redirectingToLogin = false
  })
})

// 等首次导航解析完再挂载：
//   - 首屏直接渲染目标页面，<Transition> 看到的是「首次渲染」而不是「进场」，
//     不会再从 opacity:0 淡入——否则整页首帧是透明的，Chromium 不产出 FCP/LCP
//     （实测：默认动效偏好下只有 first-paint，LCP 永远量不到），用户也会多看到一段空白；
//   - 顺带保证首次渲染时 router.currentRoute 已就绪，页面里读 route 不会拿到空值。
void router.isReady().then(() => app.mount('#app'))
