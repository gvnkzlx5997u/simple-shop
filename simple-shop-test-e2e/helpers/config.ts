/**
 * 全局配置：三个地址 + 卖家账号。
 *
 * 为什么全走环境变量：
 *  - 本地开发是「后端 8080 + 买家 5174 + 卖家 5173」三个地址；
 *  - 将来用 deploy/ 的 Compose 跑时，是 nginx 一个入口（买家 `/`、卖家 `/seller/`）；
 *  - 两种形态只改环境变量，不改用例。
 */

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
