#!/usr/bin/env node
/**
 * 首屏性能测量（LCP / FCP / TTFB / CLS / 长任务），跑在**构建产物**上。
 *
 * 为什么不用「DevTools 手动打点」：
 *   1. 手工开 CPU/网络限速经常在导航之后才生效，前几帧其实是满速跑的；
 *   2. 单次测量方差极大（同一页面 1.8s 与 2.6s 都正常），必须多次取中位数；
 *   3. LCP 会在首次交互/页面隐藏时才定稿，读早了拿到的是较早的候选值；
 *   4. `npm run preview` 默认不带 /api 代理，首页量到的可能是「接口 404」的画面。
 *
 * 这里用 CDP 在**导航前**打好限速，PerformanceObserver(buffered) 收指标，
 * 每个场景跑 N 次（默认 5）取中位数与 p75，并报出 LCP 元素是什么——
 * 「谁是最大元素」比一个孤立的秒数更能指导优化。
 *
 * 用法：
 *   npm run perf -- --url=http://localhost:4173/login --name=卖家登录
 *   npm run perf -- --url=http://localhost:4173/ --name=卖家首页 --auth=seller
 *   npm run perf -- --url=... --runs=7 --cpu=4 --net=slow4g --settle=1500
 *   npm run perf -- --url=... --warm            # 复用上下文（热缓存），对比冷启动
 *
 * 限速档位（--net）：slow4g（默认，150ms / 1.6Mbps / 750kbps）、fast3g、cable、none。
 * 也可以 --rtt=150 --down=1.6 --up=0.75 自定义（Mbps）。
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { chromium } from '@playwright/test';

// 配置与 Playwright 共用同一份 .env；这里不 import helpers/config.ts——.mjs 是原生 ESM，
// 直接引 .ts 会 ERR_MODULE_NOT_FOUND，读同一份文件更稳。
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const envFile = path.join(ROOT, '.env');
if (typeof process.loadEnvFile === 'function' && fs.existsSync(envFile)) {
  process.loadEnvFile(envFile);
}

const API_URL = process.env.API_URL || 'http://localhost:8080';
const SELLER_ACCOUNT = process.env.SELLER_ACCOUNT || 'seller';
const SELLER_PASSWORD = process.env.SELLER_PASSWORD || 'Abcd@1234';

const NET_PROFILES = {
  slow4g: { rtt: 150, down: 1.6, up: 0.75 },
  fast3g: { rtt: 562.5, down: 1.6, up: 0.75 },
  cable: { rtt: 40, down: 10, up: 5 },
  none: { rtt: 0, down: 0, up: 0 },
};

const SELLER_TOKEN_KEY = 'simple-shop.seller.token';
const SELLER_EXPIRE_KEY = 'simple-shop.seller.expire-at';

function parseArgs(argv) {
  const options = {
    url: '',
    name: '',
    runs: 5,
    cpu: 4,
    net: 'slow4g',
    rtt: undefined,
    down: undefined,
    up: undefined,
    settle: 1200,
    warm: false,
    auth: '',
    motion: 'auto',
    budgetLcp: undefined,
    help: false,
  };
  for (const arg of argv) {
    const [key, raw] = arg.split('=');
    const value = raw ?? '';
    switch (key) {
      case '--url': options.url = value; break;
      case '--name': options.name = value; break;
      case '--runs': options.runs = Number(value) || 5; break;
      case '--cpu': options.cpu = Number(value) || 1; break;
      case '--net': options.net = value; break;
      case '--rtt': options.rtt = Number(value); break;
      case '--down': options.down = Number(value); break;
      case '--up': options.up = Number(value); break;
      case '--settle': options.settle = Number(value) || 0; break;
      case '--warm': options.warm = true; break;
      case '--auth': options.auth = value; break;
      case '--motion': options.motion = value === 'reduce' ? 'reduce' : 'auto'; break;
      case '--budget-lcp': options.budgetLcp = Number(value); break;
      case '--help': options.help = true; break;
      default:
        if (arg.startsWith('--')) throw new Error(`未知参数：${arg}`);
    }
  }
  return options;
}

function usage() {
  console.log(`首屏性能测量（构建产物 + CDP 限速 + 多次取中位数）

  npm run perf -- --url=<页面地址> [--name=场景名] [--auth=seller]

  --url            要测的页面（必填，建议指向 npm run preview 的地址）
  --name           报告里的场景名，默认取 URL 路径
  --runs           运行次数，默认 5（取中位数与 p75）
  --cpu            CPU 降速倍数，默认 4（与 Lighthouse 移动端一致）
  --net            限速档位：slow4g | fast3g | cable | none，默认 slow4g
  --rtt/--down/--up  自定义限速（毫秒 / Mbps）
  --settle         页面 load 之后再等多久收口（毫秒），默认 1200
  --warm           热缓存模式（复用上下文），用来和冷启动对比
  --auth=seller    先调接口拿令牌并写入 localStorage，测需要登录的页面
  --motion         auto（默认，保留动效）| reduce（关掉动效）
  --budget-lcp     可选：LCP 预算（秒），超出时退出码为 1
`);
}

/** 在页面脚本之前注入：收 LCP/FCP/CLS/长任务，并记录 LCP 元素。 */
function collectMetrics() {
  const store = {
    lcp: 0,
    lcpTag: '',
    lcpDetail: '',
    fcp: 0,
    cls: 0,
    longTaskMs: 0,
    longTaskCount: 0,
  };
  window.__perf = store;

  performance.setResourceTimingBufferSize(500);

  new PerformanceObserver((list) => {
    for (const entry of list.getEntries()) {
      store.lcp = entry.startTime;
      const el = entry.element;
      store.lcpTag = el ? el.tagName : entry.url ? 'IMAGE(url)' : 'unknown';
      if (el) {
        const rect = el.getBoundingClientRect();
        store.lcpDetail = `${el.tagName.toLowerCase()} ${Math.round(rect.width)}×${Math.round(rect.height)} ${(el.textContent || '').trim().slice(0, 40)}`;
      } else {
        store.lcpDetail = String(entry.url || '').slice(0, 80);
      }
    }
  }).observe({ type: 'largest-contentful-paint', buffered: true });

  new PerformanceObserver((list) => {
    for (const entry of list.getEntries()) {
      if (entry.name === 'first-contentful-paint') store.fcp = entry.startTime;
    }
  }).observe({ type: 'paint', buffered: true });

  new PerformanceObserver((list) => {
    for (const entry of list.getEntries()) {
      if (!entry.hadRecentInput) store.cls += entry.value;
    }
  }).observe({ type: 'layout-shift', buffered: true });

  new PerformanceObserver((list) => {
    for (const entry of list.getEntries()) {
      store.longTaskCount += 1;
      // TBT 口径：超过 50ms 的部分累加
      store.longTaskMs += Math.max(0, entry.duration - 50);
    }
  }).observe({ type: 'longtask', buffered: true });
}

/** 读完指标后再算一遍资源量：传输字节数与请求数。 */
function readPageMetrics() {
  const nav = performance.getEntriesByType('navigation')[0];
  const resources = performance.getEntriesByType('resource');
  const transferBytes = resources.reduce((sum, item) => sum + (item.transferSize || 0), 0);
  return {
    ...window.__perf,
    ttfb: nav ? nav.responseStart : 0,
    domContentLoaded: nav ? nav.domContentLoadedEventEnd : 0,
    load: nav ? nav.loadEventEnd : 0,
    requests: resources.length + 1,
    transferBytes,
  };
}

async function sellerToken() {
  const response = await fetch(`${API_URL}/api/seller/auth/session`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ account: SELLER_ACCOUNT, password: SELLER_PASSWORD }),
  });
  const body = await response.json();
  if (body.code !== 0) throw new Error(`卖家登录失败：${body.message}`);
  return body.data;
}

async function applyThrottle(session, options) {
  const profile = NET_PROFILES[options.net] ?? NET_PROFILES.slow4g;
  const rtt = options.rtt ?? profile.rtt;
  const down = options.down ?? profile.down;
  const up = options.up ?? profile.up;

  await session.send('Network.enable');
  await session.send('Network.emulateNetworkConditions', {
    offline: false,
    latency: rtt,
    // CDP 用「字节/秒」，这里由 Mbps 换算
    downloadThroughput: down > 0 ? (down * 1024 * 1024) / 8 : -1,
    uploadThroughput: up > 0 ? (up * 1024 * 1024) / 8 : -1,
    connectionType: 'cellular4g',
  });
  await session.send('Emulation.setCPUThrottlingRate', { rate: options.cpu });
}

/**
 * 跑一次并返回指标。
 * sharedContext 非空时复用它（热缓存），否则每次新建上下文（冷启动，等价于首次访问）。
 */
async function runOnce(browser, options, token, sharedContext) {
  const context =
    sharedContext ??
    (await browser.newContext({
      viewport: { width: 1366, height: 768 },
      locale: 'zh-CN',
      timezoneId: 'Asia/Shanghai',
      // 整页淡入会把首帧做成透明，Chromium 就不报 FCP/LCP；reduce 用来复测「是不是动画问题」
      reducedMotion: options.motion === 'reduce' ? 'reduce' : 'no-preference',
    }));
  const page = await context.newPage();
  await page.addInitScript(collectMetrics);

  if (token) {
    // 卖家端令牌存 localStorage，登录页以外都要先塞进去，否则会跳登录、量错页面
    await page.addInitScript(
      ([key, value, expireKey, expire]) => {
        localStorage.setItem(key, value);
        localStorage.setItem(expireKey, expire);
      },
      [SELLER_TOKEN_KEY, token.token, SELLER_EXPIRE_KEY, token.expire_at],
    );
  }

  // 限速必须在导航之前挂上，否则首屏前几帧是满速跑的
  const session = await context.newCDPSession(page);
  await applyThrottle(session, options);

  await page.goto(options.url, { waitUntil: 'load', timeout: 60_000 });
  await page.waitForLoadState('networkidle', { timeout: 8_000 }).catch(() => undefined);
  if (options.settle > 0) await page.waitForTimeout(options.settle);

  const metrics = await page.evaluate(readPageMetrics);
  await page.close();
  if (!sharedContext) await context.close();
  return metrics;
}

function stats(values) {
  const sorted = [...values].sort((a, b) => a - b);
  const pick = (p) => sorted[Math.min(sorted.length - 1, Math.max(0, Math.floor(p * sorted.length) - 1))];
  const sum = sorted.reduce((acc, value) => acc + value, 0);
  return {
    min: sorted[0],
    median: pick(0.5),
    p75: pick(0.75),
    max: sorted[sorted.length - 1],
    mean: sum / sorted.length,
  };
}

const seconds = (ms) => `${(ms / 1000).toFixed(2)}s`;
const kb = (bytes) => `${(bytes / 1024).toFixed(0)} KB`;

function printReport(options, profile, runs) {
  const name = options.name || new URL(options.url).pathname;
  console.log(`\n=== ${name} ===`);
  console.log(`URL: ${options.url}`);
  console.log(
    `条件: CPU ${options.cpu}× · ${profile.down}Mbps ↓ / ${profile.up}Mbps ↑ / ${profile.rtt}ms RTT · ` +
      `${options.warm ? '热缓存' : '冷缓存'} · 动效 ${options.motion} · ${runs.length} 次`,
  );
  console.log('');
  console.log('   #    TTFB     FCP      LCP     CLS     TBT   请求    传输');
  runs.forEach((run, index) => {
    console.log(
      `  ${String(index + 1).padStart(2)}  ${seconds(run.ttfb).padStart(7)}  ${seconds(run.fcp).padStart(7)}  ` +
        `${seconds(run.lcp).padStart(7)}  ${run.cls.toFixed(3)}  ${String(Math.round(run.longTaskMs)).padStart(4)}  ` +
        `${String(run.requests).padStart(4)}  ${kb(run.transferBytes).padStart(7)}`,
    );
  });

  const lcp = stats(runs.map((run) => run.lcp));
  const fcp = stats(runs.map((run) => run.fcp));
  const ttfb = stats(runs.map((run) => run.ttfb));
  const cls = stats(runs.map((run) => run.cls));

  console.log('');
  console.log(
    `  中位数: TTFB ${seconds(ttfb.median)} · FCP ${seconds(fcp.median)} · LCP ${seconds(lcp.median)} · CLS ${cls.median.toFixed(3)}`,
  );
  console.log(
    `  p75   : TTFB ${seconds(ttfb.p75)} · FCP ${seconds(fcp.p75)} · LCP ${seconds(lcp.p75)} · CLS ${cls.p75.toFixed(3)}`,
  );
  console.log(`  波动  : LCP ${seconds(lcp.min)} ~ ${seconds(lcp.max)}（极差 ${seconds(lcp.max - lcp.min)}）`);
  console.log('');
  console.log(`  LCP 元素：${runs[runs.length - 1].lcpTag} — ${runs[runs.length - 1].lcpDetail}`);

  // FCP 为 0 说明首帧没有「内容」被画出来：常见原因是整页从 opacity:0 淡入
  // （合成器动画不产生新的 paint，Chromium 就不产出 FCP/LCP），或首屏是纯背景的空壳。
  if (runs.some((run) => run.fcp === 0 || run.lcp === 0)) {
    console.log('');
    console.log('  ⚠ 有运行没有拿到 FCP/LCP：检查首屏是否被整页淡入/白屏包住；');
    console.log('    可先用 --motion=reduce 复测（关掉动效）确认是动画问题还是真的没内容。');
  }

  if (options.budgetLcp) {
    const budget = options.budgetLcp * 1000;
    const ok = lcp.median <= budget;
    console.log('');
    console.log(`  预算：中位 LCP ≤ ${seconds(budget)} → ${ok ? '通过' : '超出'}（${seconds(lcp.median)}）`);
    return ok;
  }
  return true;
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help || !options.url) {
    usage();
    return options.help ? 0 : 1;
  }

  const token = options.auth === 'seller' ? await sellerToken() : null;
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const runs = [];
  let sharedContext = null;

  try {
    if (options.warm) {
      sharedContext = await browser.newContext({
        viewport: { width: 1366, height: 768 },
        reducedMotion: options.motion === 'reduce' ? 'reduce' : 'no-preference',
      });
      // 先走一遍把静态资源塞进上下文缓存，再开始计数
      await runOnce(browser, options, token, sharedContext);
    }

    for (let index = 0; index < options.runs; index += 1) {
      runs.push(await runOnce(browser, options, token, sharedContext));
      process.stdout.write(`  第 ${index + 1}/${options.runs} 次：LCP ${seconds(runs[index].lcp)}\n`);
    }
  } finally {
    if (sharedContext) await sharedContext.close();
    await browser.close();
  }

  const netProfile = NET_PROFILES[options.net] ?? NET_PROFILES.slow4g;
  const profile = {
    rtt: options.rtt ?? netProfile.rtt,
    down: options.down ?? netProfile.down,
    up: options.up ?? netProfile.up,
  };
  return printReport(options, profile, runs) ? 0 : 1;
}

main()
  .then((code) => {
    process.exitCode = code;
  })
  .catch((error) => {
    console.error(`[perf] ${error.message}`);
    process.exitCode = 1;
  });
