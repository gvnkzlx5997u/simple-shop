# simple-shop 后端

> 阶段三后端工程：商品两态、订单、购物车与收藏。本文只描述代码结构、对外接口与运行方式；
> 进度、待办与遗留项记录在 [`simple-shop-doc/阶段三/后端开发计划.md`](../simple-shop-doc/阶段三/后端开发计划.md)。

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
  account/     买家/卖家账号、卖家客户查询
  category/    一级/二级类别
  goods/       商品、图片、描述块、状态与下架原因
  inventory/   商品库存（独立聚合，商品只存展示属性）
src/main/resources/
  application.yml
  logback-spring.xml
  db/migration/  V1 基线、V2 种子、V3 阶段三结构
db/草稿/     阶段三迁移与建表草稿（不参与运行）
db/阶段一/   阶段一基线、种子与数据库备份（历史归档）
db/阶段二/   阶段二建表脚本（历史归档）
db/阶段三/   阶段三交付归档目录
```

功能包内 `service/` 放接口、`service/impl/` 放实现；跨功能只允许依赖对方的 `api/` 端口，
Controller 与测试注入接口。controller 下按 `seller` / `buyer` / `publicapi` 分目录。

订单、购物车、收藏三个功能包尚未建立，按 B 阶段落地，packaging 与分层沿用 `goods` 的方式。

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
- 应用启动时由 Flyway 依次执行 `V1__baseline_schema.sql`、`V2__seed_initial_data.sql`、
  `V3__stage3_goods_and_trade_schema.sql`，随后由 `ddl-auto=validate` 校验实体与表结构。

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
| `RATE_LIMIT_ENABLED` | `true` | 保护性限流总开关；开发与测试环境设为 `false`，不要靠调大阈值绕过 |
| `RATE_LIMIT_REGISTER` | 50 | 注册每分钟上限，按客户端地址计 |
| `RATE_LIMIT_LOGIN` | 50 | 登录每分钟上限，按「账号 + 客户端地址」计 |
| `RATE_LIMIT_ORDER` | 1000 | 提交订单每分钟上限，按客户端地址计；只是兜底保护，不是业务规则 |

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
| GET | `/api/public/goods/{id}` | 商品详情；只返回在售商品，已下架返回 404 |
| POST | `/api/buyer/auth/register` | 买家注册：username、password、phone、default_location |
| POST | `/api/buyer/auth/session` | 买家登录，返回 token 与 expire_at |
| DELETE | `/api/buyer/auth/session` | 买家退出，幂等 |
| POST | `/api/buyer/orders` | 提交订单：items[{goods_id, quantity}]，金额由服务端算 |
| GET | `/api/buyer/orders` | 我的订单；status、page、page_size，按下单时间倒序 |
| GET | `/api/buyer/orders/{id}` | 订单详情；只回订单头，不含订单项 |
| GET | `/api/buyer/orders/{id}/items` | 订单项；按订单 ID 查，不分页 |
| POST | `/api/buyer/orders/{id}/cancel` | 取消自己的订单；已发货后不可取消 |
| POST | `/api/seller/auth/session` | 卖家登录 |
| DELETE | `/api/seller/auth/session` | 卖家退出，幂等 |
| PUT | `/api/seller/auth/password` | 卖家改密，成功后全部卖家会话失效 |
| POST | `/api/seller/media` | 卖家上传媒体，multipart 字段 file + purpose |
| GET | `/api/seller/categories` | 卖家类别树 |
| POST | `/api/seller/categories/l1` | 新增一级类别 |
| PUT/DELETE | `/api/seller/categories/l1/{id}` | 重命名/删除一级类别 |
| POST | `/api/seller/categories/l2` | 新增二级类别：l1_id、name |
| PUT/DELETE | `/api/seller/categories/l2/{id}` | 重命名/删除二级类别 |
| GET | `/api/seller/goods` | 卖家商品列表；status、off_shelf_reason、category_id、keyword、page、page_size |
| POST | `/api/seller/goods` | 发布商品；含类别、库存、描述块、主图/图集 URL |
| GET | `/api/seller/goods/{id}` | 商品详情，包含全部状态与下架原因 |
| PUT | `/api/seller/goods/{id}/stock` | 修改库存；在售改 0 触发库存耗尽下架，补货自动上架 |
| POST | `/api/seller/goods/{id}/off-shelf` | 手动下架；终态，没有重新上架接口 |
| PUT | `/api/seller/goods/{id}/category` | 改挂二级类别，所有状态均可 |
| GET | `/api/seller/orders` | 订单列表；status、buyer_id、goods_id、page、page_size 可组合 |
| GET | `/api/seller/orders/{id}` | 订单详情；只回订单头，不含订单项 |
| GET | `/api/seller/orders/{id}/items` | 订单项；按订单 ID 查，不分页 |
| POST | `/api/seller/orders/{id}/confirm` | 确认订单：待确认 → 已确认 |
| POST | `/api/seller/orders/{id}/prepare` | 备货完成：已确认 → 备货完成，不校验库存 |
| POST | `/api/seller/orders/{id}/ship` | 开始发货：备货完成 → 已发货 |
| POST | `/api/seller/orders/{id}/complete` | 交易完成：按明细数量扣库存，任一不足则整单拒绝 |
| POST | `/api/seller/orders/{id}/cancel` | 取消订单：交易完成前可取消 |
| GET | `/api/seller/customers` | 注册客户列表；按注册时间倒序分页 |
| GET | `/api/seller/customers/{id}` | 客户详情；不含购买历史，历史订单用 `/api/seller/orders?buyer_id=` 查 |

- 鉴权：`Authorization: Bearer <token>`；买家 token 与卖家 token 不能互用；`/api/public/**` 无需令牌。
- 媒体上传 `purpose` 取 `GOODS_IMAGE` 或 `DESCRIPTION_MEDIA`；发布商品时绑定返回的 URL。
- 接口文档：`/v3/api-docs`、`/swagger-ui/index.html`；生产用 `SPRINGDOC_ENABLED=false` 关闭。
- 购物车、收藏的接口尚未实现。

### 类别与商品口径

- 类别只有两级；商品必须挂二级类别，一级只做分组。一级类别删除前必须没有二级子类，二级类别删除前必须没有当前商品引用。
- 商品发布后名称、价格、描述、图片不可修改；库存可调、类别可改挂。
- 主图可选；图集最多 8 张，且必须先有主图；主图 + 图集合计最多 9 张。
- 描述是 TEXT / IMAGE / VIDEO 的有序内容块，不存 HTML；文本总长 1000 字符，单块同样最多 1000。
- 商品只有 `ON_SALE`、`OFF_SHELF` 两态；已下架必须带原因 `MANUAL` / `STOCK_ZERO` / `SOLD_OUT` 与下架时间，在售时两者都为空。
- 在售改库存为 0 → 自动下架（`STOCK_ZERO`）；库存耗尽下架的商品改回大于 0 → 自动上架并清空原因。手动下架是终态：库存只读，类别仍可改挂。
- 存在未完成订单（待确认 / 已确认 / 备货完成 / 已发货）时，禁止手动下架，也禁止手动把库存改成 0（20020）；改成其他正数不受限。成交扣减到 0 不在此限。
- 卖家列表按 `status` + `off_shelf_reason` 筛选；原因只能与 `OFF_SHELF` 组合，否则按参数错误拒绝。
- 买家列表与详情只放行 `ON_SALE`；`purchasable` 以后端返回为准，等价于「在售且有库存」。

### 订单口径

- 六态：`PENDING_CONFIRM` → `CONFIRMED` → `PREPARED` → `SHIPPED` → `COMPLETED`，另有终态 `CANCELED`；整单流转，只能按序前进一格，不可跳级或回退。
- 买家在进入「已发货」前可取消；卖家在「交易完成」前可取消（含已发货）。取消不影响库存，取消人与时间只记操作日志。
- 下单只校验商品在售与库存，不锁库存行、不预占；交易完成时按明细数量一次性扣减，任一不足则整单拒绝、订单停在已发货，补货后可重试。
- 下单只收明细与数量，同一订单内重复商品按参数错误（400 / 50002）拒绝，不做数量合并。
- 列表与详情只回订单头，**不含订单项**；明细走 `/orders/{id}/items`，列表分页、明细不分页，避免一层返回里出现两层分页。
- 买家只能读自己的订单，他人订单按不存在（60001）处理；卖家可读全部订单。
- 「订单管理」「按客户」「按商品」三个入口在后端只是同一个列表的筛选条件（`status` / `buyer_id` / `goods_id` 可组合），进入详情一律按订单主键查；按商品筛选经订单项反查订单 ID。

### 订单错误码

| 码 | 常量 | HTTP | 含义 |
| --- | --- | --- | --- |
| 60001 | `ORDER_NOT_FOUND` | 404 | 订单不存在；买家操作他人订单同样按不存在处理 |
| 60002 | `ORDER_STATUS_INVALID` | 200 | 状态不允许该操作：跳级、重复推进、终态推进 |
| 60003 | `ORDER_NOT_CANCELABLE` | 200 | 当前状态不可取消：买家在已发货后、双方在交易完成后 |
| 60004 | `ORDER_STOCK_SHORTAGE` | 200 | 交易完成时库存不足，整单拒绝、订单停在已发货 |
| 20011 | `GOODS_NOT_FOUND` | 404 | 下单时商品不存在 |
| 20019 | `GOODS_NOT_ON_SALE` | 200 | 下单时商品已下架 |
| 20015 | `STOCK_INVALID` | 200 | 下单数量超过库存 |
| 20020 | `GOODS_HAS_UNFINISHED_ORDER` | 200 | 有未完成订单时手动下架或手动把库存改成 0 |
| 50002 | `PARAM_INVALID` | 400 | 明细为空、同一订单内重复商品、状态参数非法等 |

码表以 `ErrorCode` 枚举为准。重复取消按成功返回只是为了防抖，不是可依赖的业务契约；重复推进会返回 60002。

### 购物车与收藏

按 B2 实现，本节待补。

### 客户口径

- 客户列表与详情只读：字段仅限用户名、电话、默认交易地点与注册时间，不提供搜索、统计或导出。
- 购买历史不在客户详情里内嵌：卖家订单列表支持 `buyer_id` 筛选，直接按订单粒度查该客户的订单即可。

## 数据库

- 开发库 `simple_shop`、测试库 `simple_shop_test`、E2E 库 `simple_shop_e2e` 互相隔离，不要混用。
- 活动迁移脚本：`V1__baseline_schema.sql`（12 张表基线）、`V2__seed_initial_data.sql`（卖家种子）、
  `V3__stage3_goods_and_trade_schema.sql`（删意向与归档四表、商品加下架原因与时间、建订单/订单项/购物车/收藏）。
- 迁移后仍是 12 张表；`V3` 只做结构变更与旧状态值换算，不迁移意向与历史记录。
- 数据库按阶段重建：升级前先备份，再删库或删数据卷；`V3` 可以用于阶段二存量库的原地升级（商品与类别数据保留）。
- `db/草稿/` 保留阶段三设计草稿供对照；`db/阶段一`、`db/阶段二` 是历史归档，Flyway 不会加载。
- 种子卖家：账号 `seller`，初始口令 `Abcd@1234`，首次登录后请修改。
- 测试库默认 `simple_shop_test`，可通过 `TEST_DB_URL` 覆盖；测试启动时自动跑 Flyway 并校验实体。
- 已发布的迁移脚本不得修改，只能追加新版本。

## 代码约定

- 时间写入统一使用 `DatabaseTimeProvider`，库内一律 UTC；实体时间字段保留 `@JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)`。
- 会话区分买家与卖家角色，令牌不能互用。
- 商品图片只接受 JPG/PNG；描述媒体接受 JPG/PNG 和 MP4。文件名由服务端生成，数据库只存相对 URL，并做用途与目录穿越校验。
- 请求 DTO 在边界做存在性、长度与上下界校验（`@Valid` + Bean Validation），失败返回 HTTP 400 + 50002，并附 `errors: [{field, message}]`；字段名使用请求体 JSON 键（如 `category_id`、`description[0].type`）。
- 账号字段的语义校验仍在 Service 层：用户名/账号 1~50 字符且不含空白/控制字符/尖括号；密码至少 8 字符且 UTF-8 不超过 72 字节；电话为 7~20 位数字与 `-`；默认交易地点可空、最多 50 字符。
- 类别名、商品名去首尾空白后 1~50 字符，拒绝控制字符、换行与尖括号；商品价格必须大于 0、不超过 999999.99 且最多两位小数；发布库存至少为 1。
- 商品图片与描述图片使用独立配置项；当前都限 5MB，但后续可以分别调整。
- 描述内容块按 `sort_no` 保存纯文本、图片、视频的混排顺序；纯文本整段 1000 字符上限由应用层校验，单个文本块在数据层也按 1000 字符预留。
- 商品媒体 URL 必须指向 `/media/goods/` 或 `/media/description/` 下已落盘的文件；发布时按用途、扩展名和文件存在性回验，拒绝外链和目录穿越。
- 商品状态与下架原因只经实体的 `offShelf(reason)` / `backOnSale()` 变化，不提供单独写状态或原因的方法。
- 注释只保留职责与非显然约束，写清「为什么」，不引用文档编号；这是一条约定，不设机器门禁。
- 测试不断言审计日志与运行日志：日志是排查手段，不是契约，抓日志文本、数条数都会让测试随文案变脆。测试只断言业务状态、返回码与库内数据；提交后副作用需要验证时，断言业务结果而不是日志。
- 控制器只调用本功能 Service，跨功能读取走 Service 或只读端口。
- 事务内不可回滚的副作用（审计日志、改密后的会话撤销）统一经 `AfterCommit` 推迟到提交成功之后；事务回滚时不会执行。
- 限流是保护措施，不是业务规则：注册与登录按「账号 + 地址」或地址计数，下单只给兜底阈值，购物车与收藏不限流。
