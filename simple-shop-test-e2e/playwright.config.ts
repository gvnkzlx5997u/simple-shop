import { defineConfig } from '@playwright/test';
import { BUYER_URL } from './helpers/config';

/**
 * Playwright 配置
 *
 * 关键取舍：
 *  - channel:'msedge'  → 直接驱动系统已装的 Edge，不下载 chromium（省 ~130MB，避开网络问题）
 *  - workers:1 / fullyParallel:false → 系统只有 1 个商品槽位（INV-01），用例之间有顺序依赖，必须串行
 *  - headless 默认 false → 验收时能看见浏览器自己点；加 --headless 可切
 *  - 失败自动截图/录像/trace → 万一挂了好定位，也是给老师看的证据
 */
export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  expect: { timeout: 10_000 },

  fullyParallel: false,
  workers: 1,
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
    headless: process.env.HEADLESS === '1',
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    viewport: { width: 1440, height: 900 },
    screenshot: 'only-on-failure',
    // video 需要额外的 ffmpeg 二进制；本机为免下载已跳过浏览器组件安装，
    // 截图 + trace 已足够定位问题，故关闭录像（要开就先跑 npx playwright install ffmpeg）
    video: 'off',
    trace: 'retain-on-failure',
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
  },

  projects: [
    {
      name: 'simple-shop',
    },
  ],
});
