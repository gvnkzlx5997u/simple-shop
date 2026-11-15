// 保持无 import：避免 Vite 在配置打包阶段解析外部依赖。
// 买家端源码用相对路径（无 @ 别名），测试 likewise 用相对路径，故不配 alias。
export default {
  test: {
    environment: 'node',
    // forks 子进程启动时才读 TZ;threads 共享主进程 ICU，env.TZ 不生效。
    pool: 'forks',
    // 固定时区，让 formatTime 的 UTC→本地断言可复现（node 只在启动时读 TZ）。
    env: { TZ: 'UTC' },
    include: ['test/**/*.spec.ts'],
    setupFiles: ['test/setup.ts'],
  },
}