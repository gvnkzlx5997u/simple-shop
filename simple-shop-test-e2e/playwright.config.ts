import { defineConfig } from '@playwright/test';

import { BUYER_URL } from './helpers/config';

/**
 * Playwright 配置（阶段三）
 *
 * 关键取舍：
 *  - channel:'msedge'：直接驱动系统已装的 Edge，不下载 Chromium（省 ~130MB，避开网络问题）
 *  - workers:1 / fullyParallel:false：卖家只有一个种子账号，多个用例并行会互相抢商品状态；
 *    等以后按商品隔离（每用例造自己的商品）再考虑并行
 *  - headless 默认 true：跑得快；想看着它点，用 HEADLESS=0
 *  - 失败保留 screenshot + trace，证据进 evidence/，报告进 report/
 *  - 只收 tests/ 下的 spec：archive/ 里的两代历史用例不参与执行
 */
export default defineConfig({
  testDir: './tests',
  testMatch: '**/*.spec.ts',
  timeout: 90_000,
  expect: { timeout: 10_000 },

  fullyParallel: false,
  // 始终单 worker：这套用例共用同一个种子卖家账号与同一份商品数据，并行会互相抢状态；
  // 同时低配机器上「一个 Edge + 两个 Vite + 一个 JVM」已经是常态占用，并发只会把内存打满。
  workers: 1,
  // 连续失败多半是环境问题（服务没起、库没重建），继续跑只会白开浏览器并堆一堆 trace。
  // 想跑完整个套件时用 --max-failures=0 关掉。
  maxFailures: 5,
  retries: 0,

  reporter: [
    ['list'],
    ['html', { outputFolder: 'report', open: 'never' }],
    ['json', { outputFile: 'report/result.json' }],
  ],

  outputDir: 'evidence',

  use: {
    baseURL: BUYER_URL,
    channel: 'msedge',
    headless: process.env.HEADLESS !== '0',
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    viewport: { width: 1440, height: 900 },
    screenshot: 'only-on-failure',
    // video 需要额外 ffmpeg 二进制（本机未下载浏览器组件），故关闭；截图 + trace 够定位
    video: 'off',
    trace: 'retain-on-failure',
    actionTimeout: 20_000,
    navigationTimeout: 30_000,
  },

  projects: [{ name: 'simple-shop-phase3' }],
});
