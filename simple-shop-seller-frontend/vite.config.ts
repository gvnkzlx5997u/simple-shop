import { fileURLToPath, URL } from 'node:url'

import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'
import vueDevTools from 'vite-plugin-vue-devtools'

// 开发与预览都要把接口与媒体转发到本地后端，否则 `npm run preview` 里的页面
// 拿到的是 404，测出来的首屏指标（LCP）是在「接口报错」的画面上量的，没有意义。
const proxy = {
  '/api': { target: 'http://localhost:8080', changeOrigin: true },
  '/media': { target: 'http://localhost:8080', changeOrigin: true },
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    // Tailwind v4 走 Vite 插件接入，不需要 postcss 配置与 tailwind.config.js。
    tailwindcss(),
    vue(),
    // vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: { proxy },
  // 性能测量必须在构建产物上做；preview 默认不带代理，这里显式复用同一份配置。
  preview: { proxy },
})
