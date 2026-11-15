/**
 * 全局配置：三个地址 + 卖家账号。
 *
 * 来源：项目根的 `.env`（模板 `.env.example`），缺项时用下面的默认值。
 * 优先级「真实环境变量 > .env > 默认值」——换环境只改 .env，不用在终端 export。
 *
 * 为什么要可配置：
 *  - 本地开发是「后端 8080 + 买家 5174 + 卖家 5173」三个地址；
 *  - 用 deploy/ 的 Compose 跑时，是 nginx 一个入口（买家 `/`、卖家 `/seller/`）；
 *  - 两种形态只改配置，不改用例。
 */
import { existsSync } from 'node:fs';
import { resolve } from 'node:path';

// 与 scripts/db.js 读同一个文件；playwright.config.ts 会先加载本模块，
// 所以整套用例在启动时就拿到了 .env 里的地址与账号。
const envFile = resolve(__dirname, '..', '.env');
if (typeof process.loadEnvFile === 'function' && existsSync(envFile)) {
  process.loadEnvFile(envFile);
}

/** 后端 REST 地址（浏览器请求经 Vite 代理到同一个后端） */
export const API_URL = process.env.API_URL || 'http://localhost:8080';

/** 买家 SPA。⚠️ 用 localhost，不要写 127.0.0.1（Vite dev 只监听 [::1]） */
export const BUYER_URL = process.env.BUYER_URL || 'http://localhost:5174';

/** 卖家 SPA。生产挂在 /seller/，本地开发是根路径 */
export const SELLER_URL = process.env.SELLER_URL || 'http://localhost:5173';

/** 种子卖家账号（V2 迁移写入） */
export const SELLER_ACCOUNT = process.env.SELLER_ACCOUNT || 'seller';

/**
 * 种子卖家口令。
 *
 * 这是 `V2__seed_initial_data.sql` 写入的公开种子口令，已在
 * `simple-shop-doc/阶段二/阶段二剩余事项交接.md` 明文写出，不属于机密。
 * 指向其他环境时用环境变量覆盖；**真实口令不要写进本文件**。
 */
export const SELLER_PASSWORD = process.env.SELLER_PASSWORD || 'Abcd@1234';

/** 本次运行的数据后缀：所有账号/商品都带它，避免历史残留互相干扰 */
export const RUN_ID = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
