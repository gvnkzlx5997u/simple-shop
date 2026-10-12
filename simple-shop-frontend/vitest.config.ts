// 保持无 import：避免 Vite 在配置打包阶段解析外部依赖。
export default {
  resolve: {
    alias: {
      '@': process.cwd() + '/src',
    },
  },
  test: {
    environment: 'node',
    pool: 'threads',
    include: ['src/**/__tests__/**/*.spec.ts'],
  },
}
