# AGENTS.md

> 面向接手 simple-shop 的 AI 协作者：先读本文件，再按任务阅读相关代码。
> 本文件只记录工程事实、协作约束和容易踩的坑，不代替项目文档。
> 日期：2026-11-08

## 1. 项目定位

- 课程项目，单卖家在线购物系统：买家注册登录后浏览在售商品，加入购物车或收藏，从购物车多件下单；卖家在后台确认、备货、发货并登记交易完成。系统不承载支付与物流。
- 订单取代购买意向，商品只有「在售 / 已下架」两态；不要沿用阶段二的意向排队、商品冻结与归档搬表。
- 项目按阶段迭代，新需求可能改变业务口径，先确认需求与状态模型，再改实现；不要全部沿用旧口径。
- 后端只提供 REST 与 `/media`。买家端与卖家端是两个独立 SPA。
- 当前阶段（阶段三）文档见 `simple-shop-doc/阶段三/目录.md`；历史阶段文档已失去时效性，仅作参考。

## 2. 仓库与技术栈

| 目录 | 职责 |
| --- | --- |
| `simple-shop-backend/` | Spring Boot 后端，REST + 媒体文件 |
| `simple-shop-buyer-frontend/` | 买家端 SPA |
| `simple-shop-seller-frontend/` | 卖家端 SPA |
| `simple-shop-test-e2e/` | Playwright 端到端测试 |
| `simple-shop-doc` | 项目文档 |
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
mvn clean verify                 # 默认 Testcontainers，需要 Docker
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

- **按功能分包**：`account`、`category`、`goods`、`inventory`、`order`、`cart`、`favorite`、`common`。功能包内按 `entity / repository / service / controller / dto` 组织；`service/` 放接口，`service/impl/` 放实现。阶段二的 `intention` 与 `history` 包已下线，不要恢复。
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
- **库存独立聚合**：商品只存展示属性，库存变更走 `inventory`；不要绕过库存服务直接改商品表库存字段。交易完成时按明细数量一次性扣减，任一明细不足则整单拒绝（全成或全败），不出现负库存；下单只校验、不预占。
- **状态不变量**：
  - 商品态：`ON_SALE`、`OFF_SHELF`；下架原因 `MANUAL`、`STOCK_ZERO`、`SOLD_OUT`。`ON_SALE` 时原因与下架时间必须为空，`OFF_SHELF` 时必须非空。
  - `OFF_SHELF + MANUAL` 是终态：禁止改库存、没有重新上架接口；类别仍可改挂（否则永久引用会阻塞类别删除）。只有 `STOCK_ZERO`、`SOLD_OUT` 在库存改回大于 0 时自动上架。
  - 订单态：`PENDING_CONFIRM`、`CONFIRMED`、`PREPARED`、`SHIPPED`、`COMPLETED`、`CANCELED`；整单流转，只能按序推进，不可跳级或回退。
  - 买家在进入 `SHIPPED` 前可取消，卖家在进入 `COMPLETED` 前可取消；取消是整单的，不影响库存。
  - 库存只在交易完成时扣减；完成时库存不足则拒绝完成，不部分完成、不自动取消、不扣成负数。
  - 存在未完成订单（`PENDING_CONFIRM` 至 `SHIPPED`）时，禁止手动下架与手动把库存改为 0；成交扣减到 0 不受此限。
  - 订单项、购物车行、收藏行没有状态；「历史商品」是 `OFF_SHELF` 的查询视图，不存在历史表与 `ARCHIVED`。
- **状态/迁移变更清单**：不要只改表或只改前端表现；涉及商品、订单状态时逐项检查：
  - 枚举定义与查询条件；
  - Flyway 新迁移（不改已发布脚本）；
  - 状态迁移与并发测试；
  - 买家/卖家两端的文案、筛选与按钮状态；
  - 状态与库存的一致性（在售无原因、已下架有原因；`MANUAL` 库存大于 0，另两类为 0）。
- **媒体限制**：商品图/描述图 5MB，描述视频 20MB，multipart 请求上限 25MB；nginx 的 `client_max_body_size 25m` 不能小于后端上限，否则会先收到 413 而不是业务错误码。
- **限流**：限流是保护措施，不是业务规则。总开关 `simple-shop.rate-limit.enabled` 默认开；注册、登录默认各 50 次/分钟，登录按「账号 + IP」计；提交订单只给 1000 次/分钟的兜底阈值；购物车、收藏不限流。E2E 与批量造数据在 yml 里整体关闭，不要靠调大阈值绕过。

## 5. 前端约定

- 两个前端工程**互不依赖**，不要互相 import；共享契约靠后端接口与各自 `api/types.ts` 保持一致。
- 字段名保持后端返回的 **snake_case**（`goods_id`、`page_size`、`create_at`），不要擅自转驼峰。
- 统一响应拆壳与错误处理集中在 `api/http.ts`；业务码映射为中文文案，界面不要直接展示后端 `message` 原文。
- 401 / 会话失效统一跳登录，不要在页面里各写一套。
- 商品是否可购买、可加购以后端返回的 `purchasable` 为准，不要在前端自行推断状态。
- 已下架商品对买家不可见：收藏里保留但不可进详情，购物车里保留但不可选中。
- 购物车数量与勾选只存 `localStorage`，不落库；下单请求自带 `goods_id + quantity`，后端不校验商品是否在购物车中。
- 订单详情买卖双方共用一套，按角色显示操作；订单列表只负责筛选与跳转。
- 卖家 SPA 生产构建 base 为 `/seller/`，开发期仍在根路径；不要用生产 URL 规则套开发地址。买家 SPA 固定在 `/`。
- 开发期 Vite 已把 `/api`、`/media` 代理到 `http://localhost:8080`；后端不在本机时用 `VITE_API_BASE_URL` 覆盖。
- 改页面时优先复用现有 `components/common`、`composables`、`stores`，不要为单个页面另起一套请求/状态实现。

## 6. 数据与迁移

- MySQL 8；开发库 `simple_shop`，测试库 `simple_shop_test`，E2E 库 `simple_shop_e2e`。三者不要混用，尤其不要拿开发库跑 E2E。
- Flyway 管理表结构：`V1__baseline_schema.sql` 是 12 张表的基线，`V2__seed_initial_data.sql` 写入种子数据。**已发布的迁移脚本不可修改**（改注释也会导致 checksum 失配）；新变更从 `V3` 开始新增。阶段三的 `V3` 建订单/购物车/收藏表、给商品表加下架原因与时间、删除意向与历史表；它只做三处旧状态值换算（`FROZEN→ON_SALE`、`OFF_MANUAL/OFF_ZERO→OFF_SHELF` 加原因），不迁移意向与历史记录。
- JPA 固定 `ddl-auto=validate`，不要改成 `update`/`create` 来“省事”；应用能启动本身就是实体与表结构一致的断言。
- 测试库由 Testcontainers 或 `-Plocal-mysql` 管理；迁移测试会创建随机临时库，不要手工复用。
- 数据库按阶段重建，旧数据卷不能跨阶段复用：升级必须先备份，再 `docker compose down -v`（或换项目名/卷名）。`down -v` 会删数据，执行前必须和人类确认。
- 媒体目录必须挂持久卷；商品下架不会搬迁或删除图片文件，卷丢失即永久丢失。

## 7. 测试与验收

- 后端提交前至少跑 `mvn clean verify`；默认路径需要 Docker。没有 Docker 时用 `mvn -Plocal-mysql clean verify`，并在交付说明里写清用了哪条路径。
- 架构规则由 ArchUnit 守门；改包结构、Controller 依赖或跨功能调用后，务必跑完整 verify，不要只跑单个测试类。
- **测试不断言审计日志与运行日志**：不用 logback appender 抓日志，也不断言日志条数或文案。日志是排查手段，不是契约，断言日志是过度测试；要验证副作用就断言业务状态、返回码与库内数据。
- 卖家前端改完跑 `npm run build`，涉及逻辑再跑 `npm test`；买家前端改完跑 `npm run build`。
- E2E 用 `workers:1` 串行，默认用系统 Edge；阶段三正在重写用例，spec 与条数以该目录实际为准。跑前按 E2E 目录的说明重建 `simple_shop_e2e`、启动三个服务并关闭限流。
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
- **文档时效**：当前口径只以当前阶段的文档为准；旧阶段文档只标注「已失去时效性」，不为每处差异逐段加注。
- **维护本文件**：架构、目录职责、常用命令、测试或部署口径变化时同步更新本文件；本文件只记录稳定约定，不写临时进度和变更日志。
