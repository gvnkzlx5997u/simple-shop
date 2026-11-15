#!/usr/bin/env node
/**
 * 重建 E2E 独立库（跨平台版本，替代原来只认 Windows + 写死 mysql.exe 路径的 .bat）。
 *
 * 用法：
 *   npm run db:reset                              # 重建 simple_shop_e2e
 *   npm run db:probe                              # 只探活并打印目标库，不删库
 *   node scripts/reset-e2e-db.js --db=simple_shop_xxx
 *   node scripts/reset-e2e-db.js --dry-run        # 等价于 db:probe
 *
 * 口令等配置来自项目根的 `.env`（模板 `.env.example`），不在命令行里传：
 * 命令行参数会进 shell 历史与进程列表，而且换机器就要重新记一遍 export。
 * 临时覆盖仍可用真实环境变量（优先级高于 .env）。
 *
 * 只 DROP/CREATE 目标库，表结构与种子数据由后端启动时的 Flyway 建；
 * 开发库 simple_shop 与测试库 simple_shop_test 在 db.js 里受保护，传进来会直接拒绝。
 */
const db = require('./db');

function parseArgs(argv) {
  const options = { database: undefined, dryRun: false, help: false };
  for (const arg of argv) {
    if (arg === '--dry-run' || arg === '-n') {
      options.dryRun = true;
    } else if (arg === '--help' || arg === '-h') {
      options.help = true;
    } else if (arg.startsWith('--db=')) {
      options.database = arg.slice('--db='.length);
    } else {
      throw new Error(`未知参数：${arg}（只支持 --db=库名 / --dry-run / --help）`);
    }
  }
  return options;
}

function usage() {
  console.log(`重建 E2E 独立库

  node scripts/reset-e2e-db.js [--db=库名] [--dry-run]

配置来自项目根的 .env（模板 .env.example；真实环境变量优先）
  E2E_DB_HOST      主机，默认 127.0.0.1
  E2E_DB_PORT      端口，默认 3306
  E2E_DB_USER      账号，默认 root
  E2E_DB_PASSWORD  口令（留空表示空口令）
  E2E_DB_NAME      库名，默认 simple_shop_e2e
`);
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    usage();
    return 0;
  }

  const overrides = options.database ? { database: options.database } : {};
  const target = db.describe(db.resolve(overrides));
  console.log(`[E2E] 目标库：${target}`);

  if (options.dryRun) {
    const health = await db.probe({ ...overrides, database: null });
    if (!health.ok) {
      console.error(`[E2E] 连不上 MySQL：${health.reason}`);
      return 1;
    }
    console.log(`[E2E] 连接正常（MySQL ${health.version}），--dry-run 未改动任何库`);
    return 0;
  }

  const name = db.resolve(overrides).database;
  const { target: done } = await db.recreateDatabase(name, overrides);
  console.log(`[E2E] 已重建 ${done}（utf8mb4 / utf8mb4_unicode_ci）`);
  console.log('[E2E] 下一步：启动后端，Flyway 会自动建表并写入种子数据');
  return 0;
}

main()
  .then((code) => {
    process.exitCode = code;
  })
  .catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  })
  .finally(() => db.close());
