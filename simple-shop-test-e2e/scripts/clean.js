#!/usr/bin/env node
/**
 * 清理测试产生的缓存与证据文件。
 *
 * 为什么需要：一轮失败会留下 trace.zip + 截图，几十上百 MB 很常见；
 * Playwright 的 HTML 报告与 JSON 结果也会越攒越多。低配机器上这些文件既占盘，
 * 又会让「打开报告 / 再跑一轮」变慢。
 *
 * 只删生成物，不碰源码与配置：
 *   report/               HTML + JSON 报告
 *   evidence/             失败截图与 trace
 *   test-results/         Playwright 默认输出目录
 *   playwright-report/    默认 HTML 报告目录（若改过输出位置）
 *   logs/
 *
 * 用法：npm run clean
 * 注意：不会删 .env、node_modules、archive；也不会碰后端的 data/media。
 */
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '..');
const TARGETS = ['report', 'evidence', 'test-results', 'playwright-report', 'logs'];

let removed = 0;
let freed = 0;

function dirSize(dir) {
  let total = 0;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      total += dirSize(full);
    } else if (entry.isFile()) {
      total += fs.statSync(full).size;
    }
  }
  return total;
}

function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KB', 'MB', 'GB'];
  let value = bytes / 1024;
  let index = 0;
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index += 1;
  }
  return `${value.toFixed(1)} ${units[index]}`;
}

for (const name of TARGETS) {
  const target = path.join(ROOT, name);
  if (!fs.existsSync(target)) {
    continue;
  }
  const size = fs.statSync(target).isDirectory() ? dirSize(target) : 0;
  fs.rmSync(target, { recursive: true, force: true });
  removed += 1;
  freed += size;
  console.log(`[clean] 已删除 ${name}/（${formatBytes(size)}）`);
}

if (removed === 0) {
  console.log('[clean] 没有需要清理的缓存目录');
} else {
  console.log(`[clean] 共清理 ${removed} 个目录，释放 ${formatBytes(freed)}`);
}
