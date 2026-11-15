import { beforeEach } from 'vitest'

/**
 * 内存版 localStorage。node 环境没有 DOM，而 cartStorage / auth / token 都直接读写
 * localStorage;token.ts 在模块加载时就捕获它，所以必须在测试导入源码之前装好（setupFiles 先于测试文件执行）。
 */
const store = new Map<string, string>()

const stub = {
  get length() {
    return store.size
  },
  clear: () => store.clear(),
  getItem: (k: string) => (store.has(k) ? store.get(k)! : null),
  key: (i: number) => Array.from(store.keys())[i] ?? null,
  removeItem: (k: string) => {
    store.delete(k)
  },
  setItem: (k: string, v: string) => {
    store.set(k, String(v))
  },
} as unknown as Storage

globalThis.localStorage = stub

beforeEach(() => {
  stub.clear()
})