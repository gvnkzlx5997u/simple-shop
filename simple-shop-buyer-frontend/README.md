# simple-shop-buyer-frontend

在线购物系统 · 买家端前端（阶段二，前后端分离）。

对应后端：`simple-shop-backend`（Spring Boot，REST + 媒体文件，不输出 HTML）。
接口契约以 `simple-shop-doc/阶段二/前端交接文档.md` 与后端 Swagger（`/swagger-ui/index.html`）为准。

## 技术栈

Vue 3 + Vite + TypeScript + Pinia + Vue Router + Axios

## 页面与接口

买家端共 5 个页面、6 个接口（详见交接文档 §4.2）。

| 页面 | 路由 | 接口 |
| --- | --- | --- |
| 商品列表 / 搜索 | `/goods` | `GET /api/public/goods`（keyword、category_id、page、page_size） |
| — | — | `GET /api/public/categories`（两级类别树） |
| 商品详情 | `/goods/:id` | `GET /api/public/goods/{id}` |
| 注册 | `/register` | `POST /api/buyer/auth/register` |
| 登录 / 退出 | `/login` | `POST` / `DELETE /api/buyer/auth/session` |
| 我的历史 | `/history` | `GET /api/buyer/history` |
| 提交购买意向 | 详情页按钮 | `POST /api/buyer/intentions`（body 仅 `goods_id`） |

## 目录结构

```
src/
├── api/
│   ├── types.ts      接口契约类型（字段保持后端 snake_case）与业务码
│   ├── token.ts      令牌本地持久化（key: simple-shop.buyer.token）
│   ├── http.ts       axios 封装：统一响应解包、ApiError、401 回调
│   └── buyer.ts      6 个接口的调用封装
├── stores/auth.ts    登录态、业务码 → 中文文案映射
├── router/index.ts   路由与登录守卫（仅 /history 需要登录）
├── views/            GoodsList / GoodsDetail / Login / Register / History
├── assets/base.css   全局基础样式
├── App.vue           顶栏（商品 / 我的历史 / 登录退出）
└── main.ts           入口；注册 401 统一跳登录
```

## 本地开发

### 1. 先启动后端

```bash
cd ../simple-shop-backend
mvn spring-boot:run
```

需要本机 MySQL。开发库默认 `simple_shop`，连接信息见后端 `application.yml`
（`DB_URL` / `DB_USERNAME` / `DB_PASSWORD` 均可用环境变量覆盖）。

后端起来后：

- 接口文档：<http://localhost:8080/swagger-ui/index.html>
- 健康检查：<http://localhost:8080/api/public/health>

### 2. 启动前端

```bash
npm install      # 首次
npm run dev      # http://localhost:5174
```

`vite.config.ts` 已把 `/api` 与 `/media` 代理到 `http://localhost:8080`，
所以开发期同源、无 CORS。要换后端地址：

```bash
VITE_API_BASE_URL=http://其它地址:8080/api npm run dev
```

### 3. 构建

```bash
npm run build        # 输出到 dist/
npm run type-check   # 只做类型检查
```

## 实现约定

- **后端响应统一为 `{code, message, data}`**，`code=0` 为成功。业务拒绝仍走 HTTP 200，只有会话失效(401)、参数错误(400)、资源不存在(404)、服务端错误(500) 用 HTTP 状态码。
- **界面不展示后端 `message` 原文**，一律按业务码映射成中文（`stores/auth.ts` 的 `describeError`）。
- **字段名保持后端返回的 snake_case**（`goods_id`、`page_size`、`create_at`），不做驼峰转换。
- **401 / code 10002 统一跳登录**，在各页面不单独处理（`main.ts` 注册回调）。
- **交易中的商品**：页面可打开，但「我要购买」禁用并提示，判断依据是后端返回的 `purchasable`，不在前端自行推断状态。
- **提交意向后不展示排队位次**（后端回执不含位次）。

## 买家端不做的事

以下在需求里明确不存在，前端也没有留下入口：

改资料、改密码、找回密码、撤销意向、查看排队位次、购物车、支付、收货地址、客服。

## 已知限制

- **移动端适配：按组长确认暂缓**（不在挑战需求 A 范围内）。当前仅做基础响应式，演示与验收在桌面浏览器进行。
- 历史页需要「已有交易结果」的记录才会显示内容；排队中、交易中的意向不出现在历史里（依据 `升级需求A-状态模型设计`「意向四态」一节：买家只能看到自己的终态）。
- 商品列表每页 12 条（列表为 3 行 × 4 列）；后端 `page_size` 上限 100，超限返回 400。
