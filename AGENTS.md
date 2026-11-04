# AGENTS.md

> 面向接手 simple-shop 的 AI 协作者：先读本文件，再按任务阅读相关代码。
> 本文件只记录工程事实、协作约束和容易踩的坑，不代替项目文档。
> 日期：2026-11-04

## 1. 项目定位

- 课程项目，单卖家在线购物系统：买家注册登录后浏览在售商品并提交购买意向，卖家按商品队列线下交易并登记结果；系统不承载支付与物流。
- 项目按阶段迭代，新需求可能改变业务口径，先确认需求与状态模型，再改实现；不要全部沿用旧口径。
- 后端只提供 REST 与 `/media`。买家端与卖家端是两个独立 SPA。

## 2. 仓库与技术栈

| 目录 | 职责 |
| --- | --- |
| `simple-shop-backend/` | Spring Boot 后端，REST + 媒体文件 |
| `simple-shop-buyer-frontend/` | 买家端 SPA |
| `simple-shop-seller-frontend/` | 卖家端 SPA |
| `simple-shop-test-e2e/` | Playwright 端到端测试 |
| `simple-shop-doc`       |      项目文档       |
| `deploy/` | 本地构建的三容器 Compose 部署 |
| `deploy-cloud/` | 使用云端镜像的三容器 Compose 部署 |

- **后端**：Java 21、Spring Boot 3.5.16、Maven（可执行 war）、Spring Data JPA/Hibernate、MySQL 8、Flyway、springdoc；运行端口 8080。
- **前端**：Vue 3 + TypeScript + Vite + Pinia + Vue Router + Axios；卖家开发端口 5173，买家开发端口 5174。两端均使用原生 CSS，不引入 UI 组件库。
- **构建环境**：Node 24（Docker 统一用 `node:24-bookworm-slim`；卖家 `engines` 允许 `^22.18.0 || >=24.12.0`）；Docker 23+（BuildKit 的 `RUN --mount=type=cache`）。
- **测试**：JUnit 5、Testcontainers（`mysql:8.0`）、ArchUnit、Vitest、Playwright（默认系统 Edge，不下载 Chromium）。
- **部署**：Docker Compose，三容器 mysql / backend / frontend；只有 nginx 暴露宿主 80 端口。

## 3. 常用命令

```bash
# 后端
cd simple-shop-backend
mvn clean verify                 # 212 个用例，默认 Testcontainers，需要 Docker
mvn -Plocal-mysql clean verify   # 无 Docker 时连本机 simple_shop_test
mvn spring-boot:run              # 启动，默认连本机 simple_shop

# 卖家前端
cd simple-shop-seller-frontend
npm ci
npm run build                    # 类型检查 + 生产构建
npm test                         # Vitest

# 买家前端
cd simple-shop-buyer-frontend
npm ci
npm run build                    # 内含 vue-tsc 类型检查

# 本地容器部署
cd deploy
docker compose up -d --build

# 云端镜像部署
cd deploy-cloud
docker compose pull
docker compose up -d
```

E2E 需要后端、买家 SPA、卖家 SPA 三个服务先启动，并连接**独立 E2E 库**，不能连开发库。命令：

```bash
cd simple-shop-test-e2e
npm ci
npx playwright test
```

## 4. 后端约定

- **按功能分包**：`account`、`category`、`goods`、`inventory`、`intention`、`history`、`common`。功能包内按 `entity / repository / service / controller / dto` 组织；`service/` 放接口，`service/impl/` 放实现。
- **依赖方向由 ArchUnit 守门**：`common` 保持隔离；Controller 不直接碰 Repository；跨功能只依赖对方的 `api/` 端口；功能包之间不允许循环依赖。不要用“先写通再重构”的方式绕过这些规则。
- **Controller 分层**：卖家、买家、公共查询分别放在 `controller/seller`、`controller/buyer`、`controller/publicapi`；不要把所有接口塞进一个 Controller。
- **接口边界**：Entity 不直接暴露给前端；请求/响应使用 DTO。响应外壳统一为 `{code, message, data}`，`code=0` 表示成功；业务拒绝通常是 HTTP 200 + 业务码，只有 401、400、404、500 使用 HTTP 状态码。参数校验错误可能带 `errors` 数组。
- **分页**：请求 `page` 从 1 开始，`page_size` 默认 10、上限 100；响应为 `{items, page, page_size, total}`。
- **契约/错误码变更**：新增业务码要登记并同步两端，不能复用旧码，也不能只改一端；改接口或 DTO 时同批更新：
  - 后端 DTO 与实现；
  - OpenAPI 契约；
  - 买家、卖家两端的 `api/types.ts`；
  - 受影响的接口测试。
- **鉴权**：自定义拦截器 + BCrypt 校验，没有引入 Spring Security 全栈；令牌放在进程内，买家与卖家令牌不能互用。默认会话 30 分钟，backend 重启会丢会话。
- **时间**：库内、接口、日志统一 UTC。实体时间字段必须保留 `@JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)`；不要用依赖 JVM 默认时区的 `now()`。连接串中的 `connectionTimeZone=UTC` 不能删。
- **库存独立聚合**：商品只存展示属性，库存变更走 `inventory`；不要绕过库存服务直接改商品表库存字段。
- **状态不变量**：
  - 当前商品态：`ON_SALE`、`FROZEN`、`OFF_MANUAL`、`OFF_ZERO`；`ARCHIVED` 只存在于历史表，不要塞回当前商品表。
  - 意向态：`QUEUED`、`TRADING`、`SUCCEEDED`、`FAILED`；失败原因 `SOLD_OUT`、`SELLER_VOIDED`。
  - 按商品独立排队，队列上限 1000；库存清零下架可复售，只有成交售罄才归档；归档不搬迁媒体文件。
- **状态/迁移变更清单**：不要只改当前表或只改前端表现；涉及商品、意向、归档状态时逐项检查：
  - 枚举定义与当前表的查询条件；
  - Flyway 新迁移（不改已发布脚本）；
  - 历史表映射；
  - 状态迁移测试；
  - 买家/卖家两端的文案、筛选与按钮状态。
- **媒体限制**：商品图/描述图 5MB，描述视频 20MB，multipart 请求上限 25MB；nginx 的 `client_max_body_size 25m` 不能小于后端上限，否则会先收到 413 而不是业务错误码。
- **限流**：注册、登录、提交意向默认各 10 次/分钟，可用 `RATE_LIMIT_*` 环境变量覆盖；批量造数据或跑 E2E 时注意 429。

## 5. 前端约定

- 两个前端工程**互不依赖**，不要互相 import；共享契约靠后端接口与各自 `api/types.ts` 保持一致。
- 字段名保持后端返回的 **snake_case**（`goods_id`、`page_size`、`create_at`），不要擅自转驼峰。
- 统一响应拆壳与错误处理集中在 `api/http.ts`；业务码映射为中文文案，界面不要直接展示后端 `message` 原文。
- 401 / 会话失效统一跳登录，不要在页面里各写一套。
- 商品是否可购买以后端返回的 `purchasable` 为准，不要在前端自行推断“交易中/已下架”。
- 卖家 SPA 生产构建 base 为 `/seller/`，开发期仍在根路径；不要用生产 URL 规则套开发地址。买家 SPA 固定在 `/`。
- 开发期 Vite 已把 `/api`、`/media` 代理到 `http://localhost:8080`；后端不在本机时用 `VITE_API_BASE_URL` 覆盖。
- 改页面时优先复用现有 `components/common`、`composables`、`stores`，不要为单个页面另起一套请求/状态实现。

## 6. 数据与迁移

- MySQL 8；开发库 `simple_shop`，测试库 `simple_shop_test`，E2E 库 `simple_shop_e2e`。三者不要混用，尤其不要拿开发库跑 E2E。
- Flyway 管理表结构：`V1__baseline_schema.sql` 是 12 张表的基线，`V2__seed_initial_data.sql` 写入种子数据。**已发布的迁移脚本不可修改**（改注释也会导致 checksum 失配）；新变更从 `V3` 开始新增。
- JPA 固定 `ddl-auto=validate`，不要改成 `update`/`create` 来“省事”；应用能启动本身就是实体与表结构一致的断言。
- 测试库由 Testcontainers 或 `-Plocal-mysql` 管理；迁移测试会创建随机临时库，不要手工复用。
- 阶段一的 `simple-shop_mysql-data` 卷与阶段二基线不兼容：升级必须先备份，再 `docker compose down -v`（或换项目名/卷名）。`down -v` 会删数据，执行前必须和人类确认。
- 媒体目录必须挂持久卷；归档不会搬迁图片文件，卷丢失即永久丢失。

## 7. 测试与验收

- 后端提交前至少跑 `mvn clean verify`；默认路径需要 Docker。没有 Docker 时用 `mvn -Plocal-mysql clean verify`，并在交付说明里写清用了哪条路径。
- 架构规则由 ArchUnit 守门；改包结构、Controller 依赖或跨功能调用后，务必跑完整 verify，不要只跑单个测试类。
- 卖家前端改完跑 `npm run build`，涉及逻辑再跑 `npm test`；买家前端改完跑 `npm run build`。
- E2E 当前 7 个 spec / 57 条，`workers:1` 串行；默认用系统 Edge。跑前按 E2E 目录的说明重建 `simple_shop_e2e`、启动三个服务并放宽限流。
- 阶段一的旧 E2E 用例在 `archive/`，**不要移回 `tests/`**，否则会被 Playwright 误跑。
- 不要声称“测试通过”而没有实际执行；环境不具备时，说明哪些没跑、原因是什么。

## 8. 部署与运行

- 拓扑：mysql / backend / frontend 三容器；nginx 同时托管买家 `/` 与卖家 `/seller/`，反代 `/api`、`/media` 到 `backend:8080`；backend 与 mysql 不映射宿主端口。
- 卷：`mysql-data`、`backend-images`、`backend-logs`；`backend-images` 与 `backend-logs` 不能省。
- 两套编排：`deploy/` 在目标机现场构建，`deploy-cloud/` 拉取版本化镜像。云端升级/回滚要**同时**改 `BACKEND_IMAGE` 与 `FRONTEND_IMAGE`，不要只换一个。
- 基础镜像统一 Docker Hub 官方源；不要再引入 `IMAGE_PREFIX` / `REGISTRY` 前缀机制。构建网络受限时配置 Docker 代理，不要改镜像名前缀。
- 后端运行阶段的 apt 层是“基础镜像已带 curl 就跳过，缺失才安装”；不要改回无条件 `apt-get update`。
- 口令与 `.env` 不提交；生产环境必须修改种子账号的初始口令。
- 单副本限制：会话在进程内，backend 不能多副本/负载均衡；容器重启会掉会话，这是已接受的设计，不要按“无状态服务”去改。

## 9. 协作规则

- 除项目根目录 `README.md` 与一级目录下的 `README.md` 外，不要创建其他 `README.md`；确需目录说明时改用 `目录.md`。
- 复杂语句、关键业务规则要写注释，简洁说明“为什么”；不要在注释里引用文档，也不要把注释写成长篇项目文档。
- 这是多人协作项目，不能假设所有人的 OS、路径、JDK/Node/Docker 版本完全一致；环境问题先与人类确认，不要擅自改全局配置。
- 改动前先看 `git status`：工作区/暂存区可能已有他人或人类未提交的修改，不要回滚、覆盖、重排这些改动，也不要改写历史。
- 不提交 `.env`、密钥、`target/`、`node_modules/`、`dist/` 等生成物；提交前确认没有把本机配置带进去。
- 破坏性操作（`docker compose down -v`、删库、批量重命名/删除文件、重写提交）必须先确认目标与影响，再执行。
- **提交/推送**：不允许直接向 `main` 推送；除此之外，分支命名与提交信息没有强制约定。
- **维护本文件**：架构、目录职责、常用命令、测试或部署口径变化时同步更新本文件；本文件只记录稳定约定，不写临时进度和变更日志。
