import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// 开发期把 /api 与 /media 代理到后端（同源，无 CORS）。
// 部署时由 nginx 反代，详见交接文档 §2 与 §5。
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5174,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/media': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
