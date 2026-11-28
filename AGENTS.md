# AGENTS.md

> 面向接手 simple-shop 的 AI 协作者：先读本文件确认硬约束与去处，再按任务读对应目录的 README 与当期文档。
> 本文件只写跨模块约定与「去哪里找」；业务规则在当期文档与各端 README，工程细节在各目录 README，阶段进度在根 README。
> 修改日期：2026-11-28

## 1. 项目与入口

- 课程项目，单卖家在线购物系统：买家浏览在售商品、加购物车或收藏、结算下单、支付、确认收货、申请售后；卖家维护商品与类别、履约发货、处理售后。不承载真实支付与物流。
- 后端只提供 REST 与 `/media`；买家端与卖家端是两个互不依赖的 SPA，共用一套接口。
- **阶段状态与完成进度登记在根 `README.md` 与各端 README，本文件不记录。**
- 当前口径以当期文档目录为准（现为 `simple-shop-doc/阶段四/`：目录、契约交接、openapi.json）；更早阶段的文档已失去时效性，只作追溯。

| 目录 | 职责 | 先读 |
| --- | --- | --- |
| `simple-shop-backend/` | Spring Boot，REST + 媒体 | `README.md`：构建与配置、业务口径、数据库、代码约定；接口清单见当期 `openapi.json` |
| `simple-shop-buyer-frontend/` | 买家 SPA（挂 `/`） | `README.md`：功能域、本地存储、订单与售后口径、样式约定；路由与接口见 `src/` |
| `simple-shop-seller-frontend/` | 卖家 SPA（挂 `/seller/`） | `README.md`：订单/售后/物流口径、发布流程、样式约定；路由与接口见 `src/` |
| `simple-shop-test-e2e/` | Playwright 端到端 | `README.md`：环境、跑法、用例清单与现状 |
| `simple-shop-doc/` | 分阶段文档 | `阶段四/目录.md` |
| `deploy/`、`deploy-cloud/` | 三容器 Compose（本地构建 / 云端镜像） | `deploy/README.md` |

## 2. 跨模块硬约束

下面几条改任何一端都要同时满足，其余细节见各 README。

- **契约变更三同步**：改接口或 DTO 时同批更新后端实现、当期的 `openapi.json` 与《契约交接》、两端 `api/types.ts`，以及受影响的接口测试。只改一端视为没做完。
- **业务码只增不改**：按域分段（1xxxx 账号、2xxxx 类别商品、5xxxx 通用、6xxxx 订单、7xxxx 购物车收藏、8xxxx 地址/售后/物流/支付），停用码位不复用（`3xxxx`、`4xxxx`、`10008`、`20020`）。
- **响应外壳**：`{code, message, data}`，`code=0` 成功；业务拒绝多为 HTTP 200 + 业务码，只有 401/400/404/500 用 HTTP 状态；参数校验错误带 `errors[]`。`message` 只给日志与排查，界面按码映射中文。
- **分页**：`page` 从 1 起，`page_size` 默认 10、上限 100，响应 `{items, page, page_size, total}`。
- **状态与按钮**：唯一依据是当期《契约交接》§4 的状态矩阵与后端实现，不要从状态名自行推断，也不要沿用旧阶段的流程表。
- **前端不推断业务状态**：能否购买/加购看后端 `purchasable`，购物车行是否可选看 `selectable`；字段名保持 snake_case，不擅自转驼峰。
- **两端互不依赖**：买家端与卖家端不互相 import，共享契约只靠后端接口与各自的 `api/types.ts`。
- **时间**：库内、接口、日志统一 UTC；实体时间字段保留 `@JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)`，连接串的 `connectionTimeZone=UTC` 不能删。
- **迁移只增不改**：已发布的 Flyway 脚本不可修改（改注释也会 checksum 失配）；`ddl-auto=validate` 不要改成 `update`/`create`，应用能启动本身就是实体与表结构一致的断言。
- **单副本**：会话、限流、单号都在进程内，backend 不能多副本；重启会掉会话，这是已接受的设计，不要按「无状态服务」去改。

## 3. 常用命令

| 目标 | 命令 | 说明 |
| --- | --- | --- |
| 后端 | `cd simple-shop-backend && mvn spring-boot:run` | 默认连本机 `simple_shop` |
| 买家端 | `cd simple-shop-buyer-frontend && npm ci && npm run dev` | `localhost:5174` |
| 卖家端 | `cd simple-shop-seller-frontend && npm ci && npm run dev` | `localhost:5173` |
| E2E | `cd simple-shop-test-e2e && npm ci && npx playwright test` | 需先起三个服务并连独立 E2E 库 |
| 本地部署 | `cd deploy && docker compose up -d --build` | 云端镜像见 `deploy-cloud/` |

构建、测试、环境变量的完整命令在各目录 README，不在这里重复。

## 4. 测试与验收

- **后端**：提交前跑一次后端测试，`mvn clean verify`（有 Docker）或 `mvn -Plocal-mysql clean verify`（无 Docker）**任选一条**，并在交付说明里写清用了哪条。不必为「保险」两条都跑。
- **不断言日志**：不用 appender 抓日志，也不断言条数或文案。日志是排查手段，不是契约；要验证副作用就断言业务状态、返回码与库内数据。
- **前端测试（单元 / E2E）一律不直连数据库**：造数走接口、断言看接口返回与页面。`simple-shop-test-e2e/scripts/db.js` 只用于重建 E2E 库与手工联调夹具，不被任何测试脚本引用。
- **前端**：改完跑 `npm run build`；涉及逻辑再跑 `npm run test:unit`。
- **E2E**：`workers: 1` 串行、默认系统 Edge；准备步骤、批次与用例清单见 `simple-shop-test-e2e/README.md`。
- 历史用例在 `archive/`，**不要移回 `tests/`**（会被 Playwright 误跑）；归档里的选择器与接口都已失效，只作追溯。
- 不要声称「测试通过」而没有实际执行；环境不具备时，说明哪些没跑、原因是什么。

## 5. 协作规则

- 除根 `README.md` 与一级目录下的 `README.md` 外，不新建 `README.md`；目录说明用 `目录.md`。
- 关键业务规则写注释说明「为什么」，简洁即可；注释里不引用文档，不写成长篇说明。
- 多人协作：不假设他人的 OS / 路径 / JDK / Node / Docker 版本一致；环境问题先与人类确认，不擅自改全局配置。
- 改动前看 `git status`：工作区可能已有他人未提交的改动，不要回滚、覆盖或重排，也不要改写历史。
- 不提交 `.env`、密钥、`target/`、`node_modules/`、`dist/` 等生成物。
- 破坏性操作（`docker compose down -v`、删库、批量删改文件、重写提交）先确认目标与影响。
- 不直接向 `main` 推送；分支与提交信息无强制约定。
- 文档时效：只以当前阶段文档为准，旧文档标注「已失去时效性」，不逐段加注。
- **维护本文件**：只增删跨模块硬约束与入口。业务规则写进对应目录 README 或当期文档，阶段进度写进 README，都不写在这里。
