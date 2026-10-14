/**
 * 全局配置 —— 两个地址 + 卖家账号
 * 想改地址/账号，只改这里（或设同名环境变量），不用动用例。
 */

/** 买家端（后端直出 JSP） */
export const BUYER_URL = process.env.BUYER_URL || 'http://localhost:8080';

/** 卖家端（vite dev server）
 *  ⚠️ 必须写 localhost：dev server 只监听 IPv6 [::1]，写 127.0.0.1 连不上 */
export const SELLER_URL = process.env.SELLER_URL || 'http://localhost:5173';

/** 卖家端 REST 接口在与买家端同一个后端（/api/seller/**） */
export const API_URL = process.env.API_URL || BUYER_URL;

/** 卖家账号（种子数据；口令只存在于本地，不进版本库） */
export const SELLER_ACCOUNT = process.env.SELLER_ACCOUNT || 'seller';
/**
 * 卖家口令。**不写默认值**——避免真实口令进版本库（DEC-DB-12③）。
 * 由启动脚本（local-config.bat，已 git-ignore）注入；
 * 若直接跑 npx playwright test，需自行 set SELLER_PASSWORD。
 */
export const SELLER_PASSWORD =
  process.env.SELLER_PASSWORD ||
  (() => {
    throw new Error(
      '[E2E] 环境变量 SELLER_PASSWORD 未设置。\n' +
      '      请通过 2-跑验收测试.bat 启动（它读 local-config.bat），\n' +
      '      或先复制 local-config.example.bat 为 local-config.bat 并填好口令。'
    );
  })();

/** 口令码格式（C-16） */
export const TOKEN_RE = /^[A-Z0-9]{12}$/;
