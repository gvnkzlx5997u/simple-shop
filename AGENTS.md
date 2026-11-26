# AGENTS.md

> 面向接手 simple-shop 的 AI 协作者：先读本文件，再按任务阅读相关代码。
> 本文件只记录工程事实、协作约束和容易踩的坑，不代替项目文档。
> 修改日期：2026-11-15

## 1. 项目定位

- 课程项目，单卖家在线购物系统：买家注册登录后浏览在售商品，加入购物车或收藏，从购物车多件下单；卖家在后台确认、备货、发货并登记交易完成。系统不承载支付与物流。
- 订单取代购买意向，商品只有「在售 / 已下架」两态；不要沿用阶段二的意向排队、商品冻结与归档搬表。
- 项目按阶段迭代，新需求可能改变业务口径，先确认需求与状态模型，再改实现；不要全部沿用旧口径。
- 后端只提供 REST 与 `/media`。买家端与卖家端是两个独立 SPA。
- **阶段状态：阶段四（挑战需求 C）后端已交付，前端与 E2E 待改造。** 数据层（V4）、订单七态主链、地址、售后、横切与测试全部完成，契约冻结在 `simple-shop-doc/阶段四/openapi.json` 与《契约交接》。阶段四文档见 `simple-shop-doc/阶段四/目录.md`；阶段一、阶段二、阶段三文档已失去时效性，仅作参考。

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
- **前端**：Vue 3 + TypeScript + Vite + Pinia + Vue Router + Axios；卖家开发端口 5173，买家开发端口 5174。
- **样式栈两端统一**：Tailwind CSS 4 + daisyUI 5（深/浅两套自定义主题 `shop-light`/`shop-dark`，token 两端同值）。卖家端取 `-sm` 后台密度，买家端用默认尺寸；主题存储键各端独立（`simple-shop.seller.theme` / `simple-shop.buyer.theme`）。
- **构建环境**：Node 24（Docker 统一用 `node:24-bookworm-slim`；卖家 `engines` 允许 `^22.18.0 || >=24.12.0`）；Docker 23+（BuildKit 的 `RUN --mount=type=cache`）。
- **测试**：JUnit 5、Testcontainers（`mysql:8.0`）、ArchUnit；两端前端 Vitest 单元测试；Playwright E2E（默认系统 Edge，不下载 Chromium）。**前端测试一律不直连数据库**（见 §7）。
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
npm run test:unit                # 单元测试（test/ 目录，无需后端）
npm run test:api                 # 接口连通性（需后端，不可达自动跳过）

# 买家前端
cd simple-shop-buyer-frontend
npm ci
npm run build                    # 内含 vue-tsc 类型检查（test/ 也纳入）
npm run test:unit                # 单元测试（test/ 目录，无需后端）

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
npm run clean          # 清上一轮的 report/evidence/test-results（只删生成物）
npm run typecheck      # Playwright 只转译不查类型，类型错误靠这一步拦
npx playwright test
```

## 4. 后端约定

- **按功能分包**：`account`、`category`、`goods`、`inventory`、`order`、`cart`、`favorite`、`address`、`payment`、`aftersale`、`common`。功能包内按 `entity / repository / service / controller / dto` 组织；`service/` 放接口，`service/impl/` 放实现。阶段二的 `intention` 与 `history` 包已下线，不要恢复。
- **三个新包的依赖方向是固定的**：`order` 可以用 `address.api`、`payment.api`、`goods.api`；`payment` 是叶子，谁都不认识（接真实渠道也不许反过来 import 订单）；`aftersale` 只能用 `order.api` 与 `order.enums`（状态枚举跨包共享），不能碰订单的实体、仓储、服务与 DTO。反向依赖由 ArchUnit 拦截，不要用"临时 import"绕。
- **依赖方向由 ArchUnit 守门**：`common` 保持隔离；Controller 不直接碰 Repository；跨功能只依赖对方的 `api/` 端口；功能包之间不允许循环依赖。不要用“先写通再重构”的方式绕过这些规则。
- **Controller 分层**：卖家、买家、公共查询分别放在 `controller/seller`、`controller/buyer`、`controller/publicapi`；不要把所有接口塞进一个 Controller。
- **接口边界**：Entity 不直接暴露给前端；请求/响应使用 DTO。响应外壳统一为 `{code, message, data}`，`code=0` 表示成功；业务拒绝通常是 HTTP 200 + 业务码，只有 401、400、404、500 使用 HTTP 状态码。参数校验错误可能带 `errors` 数组。业务码按域分段：1xxxx 账号、2xxxx 类别与商品、5xxxx 通用、6xxxx 订单、7xxxx 购物车与收藏、8xxxx 阶段四（800xx 地址、810xx 售后、820xx 物流、830xx 支付）；3xxxx、4xxxx 与 10008、20020 已停用，不得复用。
- **订单查询边界**：列表与详情只回订单头，订单项按订单 ID 单独查询（避免一层返回里两层分页）；售后列表同理，按 `order_id` 单独查，不内嵌进订单详情。买家只能读自己的订单，他人订单按不存在处理；客户购买历史不在客户详情内嵌，改查卖家订单列表并按买家筛选。
- **两个派生字段的算法**：订单详情的 `refunded_amount` = 取消退款（支付流水）+ 售后退款（售后单，只算已完成且结果为退款类）；`aftersale_count` 是售后条数（含待处理与换货）。两者都在查询时算，任何表都不存这个总数。
- **分页**：请求 `page` 从 1 开始，`page_size` 默认 10、上限 100；响应为 `{items, page, page_size, total}`。
- **契约/错误码变更**：新增业务码要登记并同步两端，不能复用旧码，也不能只改一端；改接口或 DTO 时同批更新：
  - 后端 DTO 与实现；
  - OpenAPI 快照（`simple-shop-doc/阶段四/openapi.json`）与《阶段四契约交接》；
  - 买家、卖家两端的 `api/types.ts`；
  - 受影响的接口测试。
- **鉴权**：自定义拦截器 + BCrypt 校验，没有引入 Spring Security 全栈；令牌放在进程内，买家与卖家令牌不能互用。默认会话 30 分钟，backend 重启会丢会话。
- **时间**：库内、接口、日志统一 UTC。实体时间字段必须保留 `@JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)`；不要用依赖 JVM 默认时区的 `now()`。连接串中的 `connectionTimeZone=UTC` 不能删。
- **库存独立聚合**：商品只存展示属性，库存变更走 `inventory`；不要绕过库存服务直接改商品表库存字段。成交（买家确认收货）时按明细数量一次性扣减，任一明细不足则整单拒绝（全成或全败），不出现负库存；下单只校验、不预占，支付与取消都不改库存。
- **状态不变量**：
  - 商品态：`ON_SALE`、`OFF_SHELF`；下架原因 `MANUAL`、`STOCK_ZERO`、`SOLD_OUT`。`ON_SALE` 时原因与下架时间必须为空，`OFF_SHELF` 时必须非空。
  - `OFF_SHELF + MANUAL` 是终态：禁止改库存、没有重新上架接口；类别仍可改挂（否则永久引用会阻塞类别删除）。只有 `STOCK_ZERO`、`SOLD_OUT` 在库存改回大于 0 时自动上架。
  - 订单态：`PENDING_PAYMENT`、`PAID`、`CONFIRMED`、`PREPARED`、`SHIPPED`、`COMPLETED`、`CANCELED`；整单流转，只能按序推进，不可跳级或回退。
  - 买家在进入 `SHIPPED` 前可取消，卖家在进入 `COMPLETED` 前可取消；取消是整单的，不改变库存。已支付订单取消要原路退款，退款流水属支付包。
  - 交易完成只能由买家确认收货推进；卖家没有「完成订单」的动作与端点。
  - 库存只在成交（买家确认收货）时扣减：在售商品按明细数量扣，任一不足整单拒绝；已下架商品成交不扣库存。
  - 已删除「有未完成订单禁止手动下架 / 改 0」：卖家随时可以下架或调整库存，成交扣减到 0 仍会自动下架。
  - 订单项、购物车行、收藏行没有状态；「历史商品」是 `OFF_SHELF` 的查询视图，不存在历史表与 `ARCHIVED`。
  - 售后态：`PENDING` → `COMPLETED` / `CANCELED`；完成必须带结果，「拒绝」与「取消」是同一个动作，没有独立的已拒绝状态。
  - 售后额度按订单项：同一订单项最多一条「已完成 + 退款类结果」的售后单（数据库唯一索引兜底），完成并退款前必须锁订单项行再判额度；换货不占额度。
  - 售后退款只落售后单，不写支付流水、不改库存、不上架——否则订单详情的已退款会重复计数。
  - 地址：每个买家最多一条默认（唯一索引兜底），切换默认要「先清旧的、刷库、再置新的」；删默认后由剩余最新一条补位。
  - 支付：`payment_channel` 与 `paid_at` 同生同灭，失败时两者都为空；`pay_deadline` 只在待支付订单上出现。
- **状态/迁移变更清单**：不要只改表或只改前端表现；涉及商品、订单状态时逐项检查：
  - 枚举定义与查询条件；
  - Flyway 新迁移（不改已发布脚本）；
  - 状态迁移与并发测试；
  - 买家/卖家两端的文案、筛选与按钮状态；
  - 状态与库存的一致性（在售无原因、已下架有原因；`MANUAL` 库存大于 0，另两类为 0）。
- **媒体限制**：商品图/描述图/售后凭证图各 5MB，描述视频 20MB，multipart 请求上限 25MB；nginx 的 `client_max_body_size 25m` 不能小于后端上限，否则会先收到 413 而不是业务错误码。目录由 `MediaPurpose` 决定（`goods` / `description` / `aftersale`），新增用途只改枚举——存储服务的 URL 回验正则按枚举拼装，不要再写死目录名。买家上传入口只开放 `AFTERSALE_IMAGE`，其他用途 20027。
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
- **订单七态**：`PENDING_PAYMENT`、`PAID`、`CONFIRMED`、`PREPARED`、`SHIPPED`、`COMPLETED`、`CANCELED`。按钮显隐以《阶段四契约交接》§4 的状态矩阵为准，不要从状态名自行推断；**卖家端没有「完成订单」**，成交只由买家确认收货推进。
- **倒计时用 `pay_deadline`**：只在待支付订单上有值，前端不要按 24 小时自己算（时限是后端配置）。
- **售后入口用 `order_item_id`**：同一商品可能出现在多个订单里，用 `goods_id` 定位会指错行。
- **8xxxx 错误码**按段映射文案：800xx 地址、810xx 售后、820xx 物流、830xx 支付；未识别的码统一走「操作失败，请稍后重试」，不要把后端 `message` 直接展示给用户。
- 卖家 SPA 生产构建 base 为 `/seller/`，开发期仍在根路径；不要用生产 URL 规则套开发地址。买家 SPA 固定在 `/`。
- 开发期 Vite 已把 `/api`、`/media` 代理到 `http://localhost:8080`；后端不在本机时用 `VITE_API_BASE_URL` 覆盖。
- 改页面时优先复用现有 `components/common`、`composables`、`stores`，不要为单个页面另起一套请求/状态实现。
- **两端样式栈**：Tailwind CSS 4 + daisyUI 5。唯一 CSS 入口是 `src/assets/base.css`（`@import "tailwindcss"` + `@plugin "daisyui"` + 两套主题 + 少量 `@layer components` 项目复合类）。写样式优先用 daisyUI 组件类，其次 Tailwind 工具类；确有全局复用的复合类再放进 `@layer components`，不要写未分层的组件样式（未分层 CSS 会压过工具类，之后只能靠 `!important` 打架）。SFC `<style scoped>` 是独立样式上下文，里面用 `@apply` 需 `@reference` 主 CSS，否则报 unknown utility；scoped 里优先纯语义变量或干脆全工具类。
- **不要与 daisyUI 重名**：`.btn`、`.card`、`.input`、`.textarea`、`.select`、`.table`、`.badge`、`.menu`、`.modal`、`.toast`、`.alert` 归 daisyUI 所有。项目里不要再定义同名类，也不要给这些元素补裸写覆盖；项目自有容器统一用 `.panel`。
- **要改 daisyUI 组件自身的外观，用 `@utility`**：daisyUI 的组件类落在 utilities 层的子层里，`@layer components` 盖不住（会被静默忽略）。全局调整用 `@utility btn { font-size: ... }` 这种形式；`@layer components` 只放项目自己的复合类。
- **颜色只用语义色**：`base-100/200/300`、`base-content`、`primary`、`success`、`warning`、`error`、`info` 及对应 `-content`。模板与 scoped 样式里不写死色值（`#fff`、`rgba(...)`、`gray-500`），否则深色主题必然失效；弱化文字用 `text-base-content/60`。
- **叠加层用实色 `--color-surface-solid`**：浅色的 `--color-base-100` 是半透明（面板叠在暖色底上才是暖白），但**盖在内容之上**的界面必须不透明，否则背后文字会透上来——原生 `<select>` 的弹出列表、`option`、`modal-box`、以后的 `dropdown-content`/`toast` 都用 `var(--color-surface-solid)`。新增浮层时先想这一条，不要直接用 `bg-base-100`。`--color-base-100` 与 `--color-surface-solid` 都在 `base.css` 的两套主题里定义，改色时一起改。
- **两套主题**：`data-theme="shop-light" | "shop-dark"` 挂在 `<html>`。切换与持久化只走 `composables/useTheme`，页面内不要直接改 `document.documentElement.dataset.theme`；首屏主题由 `index.html` 内联脚本先定，避免闪白。存储键各端独立（卖家 `simple-shop.seller.theme`、买家 `simple-shop.buyer.theme`）。
- **图标**：统一走 `components/common/AppIcon.vue`（内联 `<svg>` + `currentColor`），不要在模板里用 `<img src="*.svg">` 直插图标——`assets/` 里的图标是固定黑色填充，深色主题下会糊成黑块。新增图标把 path 的 `d` 加进 `PATHS` 即可。
- **后台密度（仅卖家）**：数据密集型界面默认取 `-sm` 一档（`btn-sm`、`input-sm`、`select-sm`、`table-sm`），用默认尺寸会把一屏信息量挤掉。**买家端是 storefront，用默认尺寸**，不照搬 `-sm`，也不做卖家那条补偿字号的 `@utility`。
- **买家端差异**：与卖家共用样式底座与公共件契约（StatusTag/Pagination/EmptyState/AppIcon），但保留内联二次确认（不用 modal）、内联 notice/alert（不用全局 toast）、960px 居中顶栏布局；表单用包裹式 `<label>`（或 `for`/`id`）保证 `getByLabel` 可访问名，不照搬卖家 fieldset/legend（legend 不给 input 起名）。

## 6. 数据与迁移

- MySQL 8；开发库 `simple_shop`，测试库 `simple_shop_test`，E2E 库 `simple_shop_e2e`。三者不要混用，尤其不要拿开发库跑 E2E。
- Flyway 管理表结构：`V1__baseline_schema.sql` 是 12 张表的基线，`V2__seed_initial_data.sql` 写入种子数据。**已发布的迁移脚本不可修改**（改注释也会导致 checksum 失配）；新变更从 `V3` 开始新增。阶段三的 `V3` 建订单/购物车/收藏表、给商品表加下架原因与时间、删除意向与历史表；它只做三处旧状态值换算（`FROZEN→ON_SALE`、`OFF_MANUAL/OFF_ZERO→OFF_SHELF` 加原因），不迁移意向与历史记录。
- 阶段三交付后 `V3` 归档在 `db/阶段三/`，与 `src/main/resources/db/migration` 中的执行版本保持一致；改动迁移只能新增 V4，不能回头改 V3。
- JPA 固定 `ddl-auto=validate`，不要改成 `update`/`create` 来“省事”；应用能启动本身就是实体与表结构一致的断言。
- 测试库由 Testcontainers 或 `-Plocal-mysql` 管理；迁移测试会创建随机临时库，不要手工复用。
- 数据库按阶段重建，旧数据卷不能跨阶段复用：升级必须先备份，再 `docker compose down -v`（或换项目名/卷名）。`down -v` 会删数据，执行前必须和人类确认。
- 媒体目录必须挂持久卷；商品下架不会搬迁或删除图片文件，卷丢失即永久丢失。

## 7. 测试与验收

- 后端提交前至少跑 `mvn clean verify`；默认路径需要 Docker。没有 Docker 时用 `mvn -Plocal-mysql clean verify`，并在交付说明里写清用了哪条路径。
- 架构规则由 ArchUnit 守门；改包结构、Controller 依赖或跨功能调用后，务必跑完整 verify，不要只跑单个测试类。
- **测试不断言审计日志与运行日志**：不用 logback appender 抓日志，也不断言日志条数或文案。日志是排查手段，不是契约，断言日志是过度测试；要验证副作用就断言业务状态、返回码与库内数据。
- **前端测试（单元 / E2E）一律不直连数据库**：造数走接口、断言走接口返回与页面；库内数据与 schema 事实（如购物车表无数量/勾选列）只属后端测试。`simple-shop-test-e2e/scripts/db.js` 仅用于重建 E2E 库与手工联调夹具，不被任何测试脚本引用；不要给前端测试开「连库对账」的口子。
- 两端前端改完跑 `npm run build`，涉及逻辑再跑 `npm run test:unit`（买家 `test/` 已纳入 tsconfig 类型检查）。
- E2E 用 `workers:1` 串行，默认用系统 Edge；阶段三收口后为 **10 spec / 90 条**（含买家 02b/02c 与跨端 08）。跑前按 E2E 目录的说明重建 `simple_shop_e2e`、启动三个服务并关闭限流。
- 历史用例在 `archive/阶段一/` 与 `archive/阶段二/`（对照关系见 `archive/目录.md`），**不要移回 `tests/`**，否则会被 Playwright 误跑；归档里的选择器与接口都已失效，只能作追溯用。
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
