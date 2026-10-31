# simple-shop 后端

阶段二重构中的后端工程。当前完成 **S0 清理与骨架**、**S1 数据层**、**S2 公共能力与账号**、**S3 类别与商品**、**S4 意向与交易** 与 **S5 归档与历史**：V1/V2 数据库基线、实体/仓储、双角色会话与鉴权、买家注册/登录、卖家登录/改密、媒体上传、两级类别、商品发布/查询/库存/上下架/改挂、买家提交意向、按商品队列、卖家交易裁决、成交售罄归档、买家历史 union、卖家历史查询与客户管理已落地。

业务口径以 [阶段二文档](../simple-shop-doc/阶段二/README.md) 为准，实施顺序见 [后端开发计划](../simple-shop-doc/阶段二/后端开发计划.md)。

## 技术基线

| 项 | 取值 |
| --- | --- |
| 构建 | Maven，可执行 war |
| Java | 编译目标 21，构建 JDK ≥ 21 |
| Spring Boot | 3.5.16 |
| 持久化 | Spring Data JPA + Hibernate，MySQL 8.0.16+ |
| 迁移 | Flyway + 手写 SQL |
| 会话 | 进程内 token，买家/卖家按角色隔离 |
| 前端 | 前后端分离，后端只提供 REST 与媒体文件 |

## 目录结构

```text
src/main/java/com/simpleshop/
  SimpleShopApplication.java
  common/
    api/       统一响应、分页、错误码、异常处理、健康检查
    auth/      角色、会话、鉴权拦截器、当前用户、密码哈希
    config/    应用配置、Jackson、Web MVC
    media/     图片/视频识别与媒体落盘
    text/      文本规则、UTC 时间格式化
    audit/     关键操作日志
    time/      UTC 时间来源
    security/  限流
    id/        主键生成
  account/     账号 entity、repository、service、controller、dto、support
  category/    一级/二级类别 entity、repository、service、controller、dto
  goods/       商品、图片、描述块 entity、repository、enums、support、service、controller、dto
  inventory/   商品库存 entity、repository、service（独立聚合，商品只存展示属性）
  intention/   意向、队列序号 entity、repository、enums、队列端口、service、controller、dto
  history/     历史商品、历史意向 entity、repository、enums、service（归档写入与卖家历史查询）
src/main/resources/
  application.yml
  logback-spring.xml
  db/migration/  Flyway V1 基线、V2 种子
db/draft/      设计草案（保留作对照，不参与运行）
db/legacy/     阶段一脚本与数据库备份（不参与运行）
scripts/       注释编号门禁
```

功能包内 `service/` 放接口、`service/impl/` 放实现；跨功能只允许依赖对方的 `api/` 端口，Controller 与测试注入接口。

`account`、`category`、`goods`、`intention`、`history` 五个功能包已建立 entity、repository、enums；S2 已补 `account` 的 service/controller/dto/support 与 `goods` 的媒体上传；S3 已补 `category` 的完整两级类别能力，以及 `goods` 的发布、查询、库存、上下架与改挂；S4 已补 `intention` 的提交/队列/裁决/买家历史与 `history` 的归档写入。controller 下按 seller/buyer 区分，公共查询放在 `controller/publicapi`。

## 构建与运行

在 `simple-shop-backend/` 目录执行：

```bash
mvn clean compile              # 仅编译，不需要数据库或 Docker
mvn test                       # 默认用 Testcontainers 启动一次性 MySQL；需要 Docker
mvn -Plocal-mysql test         # 改用本机 simple_shop_test；不需要 Docker
mvn clean verify               # 默认同上，含迁移测试与 ArchUnit
mvn -Plocal-mysql clean verify # 本机 MySQL 兜底路径
mvn spring-boot:run            # 启动，需要 MySQL；默认使用 simple_shop
```

- 默认测试镜像为 `mysql:8.0`，与部署使用的 MySQL 8.0 保持同一 tag；容器只存在于测试 JVM。
  首次运行需要拉取 `mysql:8.0` 与 `testcontainers/ryuk`，之后走本机 Docker 镜像缓存。
- `-Plocal-mysql` 使用 `TEST_DB_URL`（默认 `jdbc:mysql://localhost:3306/simple_shop_test`）；
  迁移测试会在本机创建随机命名临时库并删除；账号取 `TEST_DB_USERNAME`/`TEST_DB_PASSWORD`，
  缺省回退 `DB_USERNAME`/`DB_PASSWORD`（root/123456）。
- Testcontainers/JUnit/ArchUnit 都是 `test` scope，不会进入 `target/simple-shop.war`，生产部署无需 Docker。
- 应用启动时由 Flyway 自动执行 `V1__baseline_schema.sql` 与 `V2__seed_initial_data.sql`，随后由 `ddl-auto=validate` 校验实体与表结构。

构建产物为 `target/simple-shop.war`，可用 `java -jar` 启动。
容器部署由仓库根目录的 [`deploy/docker-compose.yml`](../deploy/docker-compose.yml) 编排；
本工程 [`Dockerfile`](./Dockerfile) 负责构建只含 REST 与 `/media` 的后端镜像。

## 配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_URL` | 本机 `simple_shop` | JDBC 连接串，已带 UTC 与 utf8mb4 参数 |
| `DB_USERNAME` | `root` | 数据库账号 |
| `DB_PASSWORD` | `123456` | 数据库口令 |
| `MEDIA_DIR` | `./data/media` | 媒体根目录；兼容阶段一的 `IMAGE_DIR`，容器部署时必须挂持久卷 |
| `AUDIT_LOG_PATH` | `logs` | 审计日志目录，保留 90 天 |
| `SPRINGDOC_ENABLED` | `true` | 是否开放 `/v3/api-docs` 与 Swagger UI；生产建议设为 `false` |

媒体限制在 `application.yml` 的 `simple-shop.media` 下配置：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `goods-image-max-size` | 5MB | 商品主图/图集，只允许 JPG/PNG |
| `description-image-max-size` | 5MB | 描述内容块中的图片，与商品图片是独立限制 |
| `description-video-max-size` | 20MB | 描述内容块中的视频，当前只接受 MP4 |

Spring 的 multipart 上限已调到 20MB/25MB，图片的 5MB 由 Service 按用途单独校验。

媒体 URL 形状：

- 商品图片：`/media/goods/uuuu/MM/<uuid>.jpg|png`
- 描述媒体：`/media/description/uuuu/MM/<uuid>.jpg|png|mp4`

数据库只存相对 URL；服务端回验时会同时检查用途目录、扩展名和文件是否存在。

## API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/public/health` | 健康检查 |
| GET | `/api/public/categories` | 两级类别树，无需登录 |
| GET | `/api/public/goods` | 在售商品列表/搜索；keyword、category_id、page、page_size |
| GET | `/api/public/goods/{id}` | 商品详情；在售、交易中可见，下架返回 404 |
| POST | `/api/buyer/auth/register` | 买家注册：username、password、phone、default_location |
| POST | `/api/buyer/auth/session` | 买家登录，返回 token 与 expire_at |
| DELETE | `/api/buyer/auth/session` | 买家退出，幂等 |
| POST | `/api/seller/auth/session` | 卖家登录 |
| DELETE | `/api/seller/auth/session` | 卖家退出，幂等 |
| PUT | `/api/seller/auth/password` | 卖家改密，成功后全部卖家会话失效 |
| POST | `/api/seller/media` | 卖家上传媒体，multipart 字段 file + purpose |
| GET | `/api/seller/categories` | 卖家类别树 |
| POST | `/api/seller/categories/l1` | 新增一级类别 |
| PUT/DELETE | `/api/seller/categories/l1/{id}` | 重命名/删除一级类别 |
| POST | `/api/seller/categories/l2` | 新增二级类别：l1_id、name |
| PUT/DELETE | `/api/seller/categories/l2/{id}` | 重命名/删除二级类别 |
| GET | `/api/seller/goods` | 卖家商品列表；status、category_id、keyword、page、page_size |
| POST | `/api/seller/goods` | 发布商品；含类别、库存、描述块、主图/图集 URL |
| GET | `/api/seller/goods/{id}` | 商品详情，包含全部当前状态 |
| PUT | `/api/seller/goods/{id}/stock` | 修改库存 |
| POST | `/api/seller/goods/{id}/off-shelf` | 手动下架 |
| POST | `/api/seller/goods/{id}/re-list` | 重新上架，body 必须带 `{"confirm":true}` |
| POST | `/api/buyer/intentions` | 提交购买意向，body 仅 `goods_id`；回执不含排队位次 |
| GET | `/api/buyer/history` | 我的历史；仅终态意向，按下单时间倒序分页 |
| GET | `/api/seller/goods/{id}/intentions` | 意向名单；按队列序号升序分页，可按状态筛选 |
| POST | `/api/seller/intentions/{id}/trade` | 选定队首进入交易；商品在售、无其他交易中意向、且必须是队首 |
| POST | `/api/seller/intentions/{id}/success` | 标记交易成功；扣库存，扣减后为 0 时商品与全部意向同事务归档 |
| POST | `/api/seller/intentions/{id}/failure` | 标记交易失败；disposal 取 `VOIDED`（作废）或 `REQUEUED`（重排至队尾） |
| PUT | `/api/seller/goods/{id}/category` | 改挂二级类别 |
| GET | `/api/seller/history/goods` | 历史商品列表；按归档时间倒序分页 |
| GET | `/api/seller/history/goods/{id}` | 历史商品详情；含快照与全部历史意向 |
| GET | `/api/seller/customers` | 注册客户列表；按注册时间倒序分页 |
| GET | `/api/seller/customers/{id}` | 客户详情 + 购买历史；分页作用于历史并集 |

- 鉴权：`Authorization: Bearer <token>`；买家 token 与卖家 token 不能互用；`/api/public/**` 无需令牌。
- 媒体上传 `purpose` 取 `GOODS_IMAGE` 或 `DESCRIPTION_MEDIA`。S2 的上传与商品解耦，S3 发布商品时绑定返回的 URL。
- 接口文档：`/v3/api-docs`、`/swagger-ui/index.html`；生产用 `SPRINGDOC_ENABLED=false` 关闭。

### 类别与商品口径

- 类别只有两级；商品必须挂二级类别，一级只做分组。一级类别删除前必须没有二级子类，二级类别删除前必须没有当前商品引用。
- 商品发布后名称、价格、描述、图片不可修改；库存可调、类别可改挂，交易中两者都禁止。
- 主图可选；图集最多 8 张，且必须先有主图；主图 + 图集合计最多 9 张。
- 描述是 TEXT / IMAGE / VIDEO 的有序内容块，不存 HTML；文本总长 1000 字符，单块同样最多 1000。
- 商品状态为 `ON_SALE`、`FROZEN`、`OFF_MANUAL`、`OFF_ZERO`；在售改库存为 0 会触发 `OFF_ZERO`，清零下架改大于 0 自动回到在售。
- 手动下架和库存清零下架都要求没有排队中/交易中意向；手动下架后重新上架必须显式确认。

### 意向与交易口径

- 每个商品独立排队；队列序号从 1 递增、同商品内唯一、只增不回收，重新排队刷新至队尾且下单时间不变；重新上架不重置序号。
- 提交意向要求商品在售，不扣库存、不去重（同一买家可重复提交）；非终态意向（排队中/交易中）达 `simple-shop.queue.max-size`（默认 1000）后拒绝新提交。
- 进入交易只能选队首；同一商品同时至多一笔交易；商品进入交易中（`FROZEN`）后不收新意向、禁止改库存/改挂/下架。
- 标记成功扣库存 1 件：扣减后大于 0 商品回在售、其余排队保留；等于 0 时商品与全部意向同事务归档——其余排队中意向自动置失败（商品已售出），类别名、买家用户名/电话、描述与图片以快照写入历史表，当前表删除商品行与意向行，队列序号行随外键级联删除。
- 标记失败须裁决：作废（意向置失败·卖家作废）或重新排队；商品回在售，库存不变。
- 买家历史是当前表已终态意向与历史意向表的并集，分页在并集之后；只含自己的终态记录，字段仅商品名、下单时间、结果、交易时间。
- 商品行是同一商品写操作的聚合锁；加锁顺序固定为商品行 → 队列序号行 → 意向行，禁止反向。

### 历史与客户口径

- 历史只读：只有成交售罄会归档（写入历史表并删除当前行），归档内容为快照（类别名、买家用户名/电话、描述与图片 JSON），历史不引用当前类别、账号与商品。
- 卖家历史商品列表/详情来自历史表；详情内含该商品全部历史意向，电话完整展示不脱敏。
- 客户列表与详情只读：字段仅限用户名、电话、默认交易地点与注册时间，不提供搜索、统计或导出。
- 客户购买历史 = 当前表已终态意向 ∪ 历史意向表，分页在并集之后；由 intention 实现、account 经端口调用，account 不反向依赖意向功能。

## 数据库

- 阶段二使用全新基线，不迁移阶段一数据；阶段一旧库与旧测试库已删除，备份在 `db/legacy/backup.sql`、`db/legacy/test_db_backup.sql`。
- 活动迁移脚本是 `src/main/resources/db/migration/V1__baseline_schema.sql`（12 张表）与 `V2__seed_initial_data.sql`（卖家种子账号）。
- `db/draft/` 保留设计草案供对照，不参与运行；`db/legacy/` 的旧脚本不会被 Flyway 加载。
- 种子卖家：账号 `seller`，初始口令 `Abcd@1234`，首次登录后请修改。
- 测试库默认 `simple_shop_test`，可通过 `TEST_DB_URL` 覆盖；测试会自动建库并跑 Flyway。
- 已发布的迁移脚本不得修改，只能追加新版本。

## 代码约定

- 时间写入统一使用 `DatabaseTimeProvider`，库内一律 UTC。
- 会话区分买家与卖家角色，令牌不能互用。
- 商品图片只接受 JPG/PNG；描述媒体接受 JPG/PNG 和 MP4。文件名由服务端生成，数据库只存相对 URL，并做用途与目录穿越校验。
- 请求 DTO 在边界做存在性、长度与上下界校验（`@Valid` + Bean Validation），失败返回 HTTP 400 + 50002，并附 `errors: [{field, message}]`；字段名使用请求体 JSON 键（如 `category_id`、`description[0].type`）。
- 账号字段的语义校验仍在 Service 层：用户名/账号 1~50 字符且不含空白/控制字符/尖括号；密码至少 8 字符且 UTF-8 不超过 72 字节；电话为 7~20 位数字与 `-`；默认交易地点可空、最多 50 字符。
- 类别名、商品名去首尾空白后 1~50 字符，拒绝控制字符、换行与尖括号；商品价格必须大于 0、不超过 999999.99 且最多两位小数；发布库存至少为 1。
- 商品图片与描述图片使用独立配置项；当前都限 5MB，但后续可以分别调整。
- 描述内容块按 `sort_no` 保存纯文本、图片、视频的混排顺序；纯文本整段 1000 字符上限由应用层校验，单个文本块在数据层也按 1000 字符预留。
- 商品媒体 URL 必须指向 `/media/goods/` 或 `/media/description/` 下已落盘的文件；发布时按用途、扩展名和文件存在性回验，拒绝外链和目录穿越。
- 注释只保留职责与非显然约束，不引用文档编号；可用 `pwsh -File scripts/check-comment-refs.ps1` 检查。
- 控制器只调用本功能 Service，跨功能读取走 Service 或只读端口。
- 事务内不可回滚的副作用（审计日志、改密后的会话撤销）统一经 `AfterCommit` 推迟到提交成功之后；事务回滚时不会执行。

## 当前状态

已完成：

- 删除买家 JSP 层、3 个 JSP Controller、4 个 JSP、买家静态资源与 JSP/JSTL 依赖。
- 删除口令码链路、交易流水、手动冻结、买家撤销/改资料相关代码与测试。
- 删除阶段一实体、仓储、枚举、业务 Service、卖家 Controller 与请求/响应 DTO（这些将按阶段二模型重写）。
- 旧迁移脚本移出活动目录；旧测试仅保留与业务模型无关的纯单元测试。
- 建立 `common` 包并完成角色化会话、双角色鉴权、图片/视频媒体存储、文本、审计、限流、错误码与健康检查。
- 增加类别、商品、库存、账号与意向域 12 张表的阶段二基线，以及卖家种子账号。
- 建立 12 个实体、12 个仓储与 6 个枚举；完成 JSON 快照、主图唯一、队列序号唯一、状态/失败原因约束等数据层测试。
- S2 落地双角色会话与鉴权、买家注册/登录/退出、卖家登录/退出/改密、媒体上传、springdoc，以及账号字段的业务校验。
- S3 落地两级类别新增/重命名/删除与删除前置、商品发布/列表/详情/搜索、主图/图集、TEXT/IMAGE/VIDEO 描述块、库存调整、手动上下架、库存清零下架与改挂类别。
- S4 落地买家提交意向（限流/队列上限/重复提交）、卖家意向名单（分页/状态筛选）、进入交易（队首/唯一交易守卫）、标记成功（库存扣减、售罄同事务归档）、标记失败（作废/重排队）、买家历史 union，以及并发不变量测试（序号唯一、唯一交易、不超卖、归档一致性）。
- S5 落地卖家历史商品列表/详情（快照解析、历史意向名单）与客户管理（注册客户列表、客户详情 + 购买历史并集，经跨包端口读取）。
- 全部 212 个用例全绿（含架构改造新增的 6 个包依赖用例与库存拆表用例）；开发库与测试库均通过 Flyway 与 `ddl-auto=validate`。S4～S6 期间数据库零变更，V1/V2 未动。
- 架构改造 S1 完成：13 个 Service 接口化、`api` 端口（商品查询/队列活跃度/归档/类别引用）、history 与 intention 解环、队列序号惰性创建、买家历史读模型迁入 history、ArchUnit 包依赖门禁（S3 后为 6 条）。
- 架构改造 S2 完成：V1 基线重建，库存拆到 `simpleshop_goods_stock` 与 `inventory` 模块；商品实体不再有 `stock`，发布/改库存/成交扣减都走 `StockPort`；交易中唯一性由 `trading_flag` 唯一索引兜底。
- 架构改造 S3 完成：新增 `GoodsLifecyclePort`，intention 不再操作商品实体；`Intention.goods` 改为 `goodsId` 列；删除 `GoodsTradeSupport`；ArchUnit 增加 intention/goods 双向边界规则。
- 架构改造 S4 完成：`TradeDisposal`、`TradeGoodsStatus` 与意向状态/失败原因改为 Java 枚举；请求体加 `@Valid` 与字段约束；参数错误返回字段级 `errors`；文本长度用 `@CodePointLength` 按码点计数；OpenAPI 快照已重新导出（30 个路径 / 59 个 schema）。
- 架构改造 S5 完成：`AfterCommit` 统一推迟不可回滚副作用；审计日志改为提交后写入，回滚不留假记录；改密后的会话撤销移到提交后，事务回滚不再误踢会话。
- 架构改造 S6 完成：默认用 Testcontainers `mysql:8.0` 跑全量测试，`-Plocal-mysql` 回退本机库；新增空库 V1→V2 迁移 + Hibernate validate 测试；WAR 不含任何测试依赖。

下一步：最终联调、部署验收与演示准备。
