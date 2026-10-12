/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 后端 API 前缀；默认 /api（开发期由 Vite 代理转发）。 */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
