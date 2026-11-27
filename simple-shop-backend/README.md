# simple-shop 后端

> 阶段三后端工程：商品两态、订单、购物车与收藏。本文只描述**当前代码**的结构、对外接口与运行方式。
>
> **当前状态：阶段四（挑战需求 C）后端已交付。** 覆盖 `V4` 迁移、七态订单、支付（Mock）、收货地址、物流、售后、取消与超时退款；接口契约以 [openapi.json](../simple-shop-doc/阶段四/openapi.json) 与[契约交接](../simple-shop-doc/阶段四/契约交接.md) 为准。
> 阶段四的范围、表结构与任务拆分见 [`simple-shop-doc/阶段四/目录.md`](../simple-shop-doc/阶段四/目录.md)，后端任务计划是仓库根目录的临时文档（不入库）。

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

订单、购物车、收藏三个功能包已按 B 阶段落地，packaging 与分层沿用 `goods` 的方式。

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
  `V3__stage3_goods_and_trade_schema.sql`、`V4__stage4_payment_delivery_aftersale_schema.sql`，
  随后由 `ddl-auto=validate` 校验实体与表结构。
- `mvn clean verify` 当前 **314 条用例**：单元（枚举与规则）、数据层（迁移、约束、生成列）、
  接口（买卖两端全链路）、并发（重复支付、过期取消、并发收货扣减、并发退款额度）、架构（7 条依赖规则）。
  `mvn clean verify` 与 `mvn -Plocal-mysql clean verify` 两条路径各跑通过一次。

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
| `RATE_LIMIT_ORDER` | 1000 | 提交订单每分钟上限，按买家计；只是兜底保护，不是业务规则 |

媒体限制在 `application.yml` 的 `simple-shop.media` 下配置：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `goods-image-max-size` | 5MB | 商品主图/图集，只允许 JPG/PNG |
| `description-image-max-size` | 5MB | 描述内容块中的图片，与商品图片是独立限制 |
| `description-video-max-size` | 20MB | 描述内容块中的视频，当前只接受 MP4 |

Spring 的 multipart 上限已调到 20MB/25MB，图片的 5MB 由 Service 按用途单独校验。

买家侧的上传入口只开放售后凭证图，用途白名单写在服务里，不靠前端自觉。

媒体 URL 形状：

- 商品图片：`/media/goods/uuuu/MM/<uuid>.jpg|png`
- 描述媒体：`/media/description/uuuu/MM/<uuid>.jpg|png|mp4`
- 售后凭证图：`/media/aftersale/uuuu/MM/<uuid>.jpg|png`

数据库只存相对 URL；服务端回验时会同时检查用途目录、扩展名和文件是否存在。

## API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/public/health` | 健康检查 |
| GET | `/api/public/categories` | 两级类别树，无需登录 |
| GET | `/api/public/goods` | 在售商品列表/搜索；keyword、category_id、page、page_size |
| GET | `/api/public/goods/{id}` | 商品详情；只返回在售商品，已下架返回 404 |
| POST | `/api/buyer/auth/register` | 买家注册：username、password、phone |
| POST | `/api/buyer/auth/session` | 买家登录，返回 token 与 expire_at |
| DELETE | `/api/buyer/auth/session` | 买家退出，幂等 |
| GET | `/api/buyer/addresses` | 地址列表；默认在前，不分页（上限 10 条） |
| POST | `/api/buyer/addresses` | 新建地址：receiver_name、receiver_phone、detail_address；第一条自动默认 |
| PUT | `/api/buyer/addresses/{id}` | 编辑地址；不改默认标记 |
| DELETE | `/api/buyer/addresses/{id}` | 删除地址；删的是默认时由最新一条补位 |
| POST | `/api/buyer/addresses/{id}/default` | 设为默认；原默认自动取消 |
| POST | `/api/buyer/aftersales` | 申请售后：order_item_id、title、description、image_urls（≤9） |
| GET | `/api/buyer/aftersales` | 我的售后；status、order_id、page、page_size，按申请时间倒序 |
| GET | `/api/buyer/aftersales/{id}` | 售后详情；只能看自己的，否则 81001 |
| POST | `/api/buyer/media` | 买家上传售后凭证图：multipart 字段 `file`，purpose 只接受 `AFTERSALE_IMAGE` |
| POST | `/api/buyer/orders` | 提交订单：address_id、items[{goods_id, quantity}]，金额由服务端算 |
| GET | `/api/buyer/orders` | 我的订单；status、page、page_size，按下单时间倒序 |
| GET | `/api/buyer/orders/{id}` | 订单详情；只回订单头，不含订单项 |
| GET | `/api/buyer/orders/{id}/items` | 订单项；按订单 ID 查，不分页 |
| POST | `/api/buyer/orders/{id}/pay` | 支付订单：body `payment_channel`（BANK_CARD / CREDIT_CARD / ALIPAY / WECHAT）；失败返回 83003，订单仍待支付可重试 |
| POST | `/api/buyer/orders/{id}/confirm-receipt` | 确认收货：已发货 → 交易完成；在售商品扣库存，已下架商品不扣 |
| POST | `/api/buyer/orders/{id}/cancel` | 取消自己的订单；已发货后不可取消 |
| POST | `/api/buyer/cart` | 加入购物车：goods_id；重复加购只留一行，商品不在售返回 70001 |
| GET | `/api/buyer/cart` | 我的购物车；不分页，已下架行保留并标 selectable=false |
| DELETE | `/api/buyer/cart/{goodsId}` | 移除一件；行不存在也返回成功 |
| POST | `/api/buyer/cart/move-to-favorites` | 批量转收藏：goods_ids；含不可选商品整批拒绝并返回 70003 |
| POST | `/api/buyer/favorites` | 收藏：goods_id；重复收藏只留一行，商品不在售返回 70002 |
| GET | `/api/buyer/favorites` | 我的收藏；page、page_size，按收藏时间倒序 |
| GET | `/api/buyer/favorites/{goodsId}` | 当前买家是否收藏了这件商品，返回 favorited |
| DELETE | `/api/buyer/favorites/{goodsId}` | 取消收藏；不要求商品在售，行不存在也返回成功 |
| POST | `/api/seller/auth/session` | 卖家登录 |
| DELETE | `/api/seller/auth/session` | 卖家退出，幂等 |
| PUT | `/api/seller/auth/password` | 卖家改密，成功后全部卖家会话失效 |
| POST | `/api/seller/media` | 卖家上传媒体：multipart 字段 `file`，查询参数 `purpose` |
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
| POST | `/api/seller/orders/{id}/ship` | 开始发货：备货完成 → 已发货；请求体 `carrier`、`tracking_no`、`logistics_remark` |
| PUT | `/api/seller/orders/{id}/logistics` | 修改物流：发货后改承运商/单号/备注，状态不变；已取消订单 82002 |
| POST | `/api/seller/orders/{id}/cancel` | 取消订单：交易完成前可取消 |
| GET | `/api/seller/customers` | 注册客户列表；按注册时间倒序分页 |
| GET | `/api/seller/customers/{id}` | 客户详情；不含购买历史，历史订单用 `/api/seller/orders?buyer_id=` 查 |

- 鉴权：`Authorization: Bearer <token>`；买家 token 与卖家 token 不能互用；`/api/public/**` 无需令牌。
- 媒体上传 `purpose` 取 `GOODS_IMAGE` 或 `DESCRIPTION_MEDIA`；发布商品时绑定返回的 URL。
- 接口文档：`/v3/api-docs`、`/swagger-ui/index.html`；生产用 `SPRINGDOC_ENABLED=false` 关闭。
- 阶段三交付快照：`../simple-shop-doc/阶段三/openapi.json`，契约口径与边界见同目录《契约交接》；改接口后需重新生成快照。

### 类别与商品口径

- 类别只有两级；商品必须挂二级类别，一级只做分组。一级类别删除前必须没有二级子类，二级类别删除前必须没有当前商品引用。
- 商品发布后名称、价格、描述、图片不可修改；库存可调、类别可改挂。
- 主图可选；图集最多 8 张，且必须先有主图；主图 + 图集合计最多 9 张。
- 描述是 TEXT / IMAGE / VIDEO 的有序内容块，不存 HTML；文本总长 1000 字符，单块同样最多 1000。
- 商品只有 `ON_SALE`、`OFF_SHELF` 两态；已下架必须带原因 `MANUAL` / `STOCK_ZERO` / `SOLD_OUT` 与下架时间，在售时两者都为空。
- 在售改库存为 0 → 自动下架（`STOCK_ZERO`）；库存耗尽下架的商品改回大于 0 → 自动上架并清空原因。手动下架是终态：库存只读，类别仍可改挂。
- 没有「有未完成订单就不许下架」的限制：库存不预占、成交才扣减，卖家随时可以下架或调整库存；成交扣减到 0 仍自动下架（`SOLD_OUT`）。
- 卖家列表按 `status` + `off_shelf_reason` 筛选；原因只能与 `OFF_SHELF` 组合，否则按参数错误拒绝。
- 买家列表与详情只放行 `ON_SALE`；`purchasable` 以后端返回为准，等价于「在售且有库存」。

### 地址口径

- 三个字段：收货人、手机号、一行详细地址；不做省市区、邮编，也不允许换行。电话沿用账号口径（7~20 位数字与 `-`）。
- 每个买家最多 10 条地址（80002），第一条创建即默认；之后只能通过「设为默认」切换，编辑不动默认标记。
- 同一买家最多一条默认由数据库唯一索引（生成列 `default_key`）兜底；设默认时先清旧默认再置新的，两次 flush 保证不撞索引。
- 删除默认地址后，由剩余里最新创建的一条补位；全部删光则没有默认。
- 订单只存下单时的地址快照，地址之后怎么改、删都不影响历史订单。
- 归属按令牌主体判定，他人地址一律按不存在（80001）返回；字段不合法返回 80003，下单未选或地址已删返回 80004。

### 售后口径（买家侧）

- 只有**交易完成**的订单可以按订单项申请售后；同一订单项可以多次申请（换货之后仍可再申请），是否受理由卖家线下判断，申请阶段不拦。
- 申请只填标题、描述与图片。售后类型（退货退款 / 仅退款 / 换货）由卖家处理时登记，买家申请时不选。
- 图片先走 `POST /api/buyer/media`（purpose 只接受 `AFTERSALE_IMAGE`，单张 5MB、JPG/PNG），申请时只传返回的 URL；外链与跨用途地址都会被媒体服务拒绝。
- 单号形如 `AS202611260001`；列表按申请时间倒序分页，可按 `status`、`order_id` 筛选；详情只能看自己的。
- 卖家登记结果时三选一：`RETURN_AND_REFUND` / `REFUND_ONLY` / `EXCHANGE`。退款类结果由后端按订单项「单价 × 数量」算金额，不接受前端传值；换货不产生退款。
- **同一订单项只能退款一次**：完成并退款前锁订单项行再判额度，第二次返回 81004。换货不占额度，换货后仍可再申请退款。
- 完成是终态：已完成不能再取消或改结果（81002）；取消只对未处理的单有效，重复取消按成功返回。取消后可以重新申请，生成新单号。
- 订单详情的 `refunded_amount` = 取消退款（支付流水）+ 售后退款（售后单，只算「已完成 + 退款类结果」）；`aftersale_count` 是售后条数，含待处理与换货。售后列表不内嵌在订单详情里，按 `order_id` 单独查。
- 错误码：81001 售后单不存在、81002 状态不允许、81003 订单项不可申请（不存在/非本人/订单未完成）、81004 该订单项已退款、81005 结果缺失或非法、81006 标题或描述非法、81007 图片超过 9 张。

### 订单口径

- 七态：`PENDING_PAYMENT` → `PAID` → `CONFIRMED` → `PREPARED` → `SHIPPED` → `COMPLETED`，另有终态 `CANCELED`；整单流转，只能按序前进一格，不可跳级或回退。
- 交易完成只由买家确认收货推进，卖家没有「完成订单」接口；成交时在售商品按明细数量扣减，任一不足整单拒绝、订单停在已发货，补货后可重试；已下架商品成交不扣库存。
- 买家在进入「已发货」前可取消；卖家在「交易完成」前可取消（含已发货）。取消不改变库存，取消人与时间只记操作日志。
- 已支付订单取消即全额原路退款，写一条 `ORDER_CANCEL` 退款流水；待支付取消不退款。重复取消返回成功但不会重复退。
- 下单起 24 小时未支付由定时任务自动取消（启动即扫、按批处理、每笔独立事务），不改库存、不退款。
- 发货后卖家可以改承运商、单号与备注，订单状态不变。这是高频修正动作，**不写审计**；只有已发货且未取消的订单允许。
- 下单必须选一个已存在的收货地址，订单存收货人/手机号/地址快照；下单只校验商品在售与库存，不锁库存行、不预占。
- 支付走 Mock 网关（`simple-shop.payment`）：四个渠道都成功，失败由 `mock.fail-channels` 注入给测试用；支付失败不写 `payment_channel` / `paid_at`，订单停在待支付。支付时限 24 小时，由 `order.payment-timeout` 配置。
- 下单只收明细与数量，同一订单内重复商品按参数错误（400 / 50002）拒绝，不做数量合并。
- 列表与详情只回订单头，**不含订单项**；明细走 `/orders/{id}/items`，列表分页、明细不分页，避免一层返回里出现两层分页。
- 订单头统一返回收货快照（收货人/手机号/详细地址）、支付（渠道、支付时间）、物流（承运商、多行单号、备注）与退款汇总：`refunded_amount` = 取消退款 + 售后退款，`aftersale_count` 为售后条数（售后列表按 `order_id` 单独查）。
- `pay_deadline` 只在待支付订单上有值，前端据此倒数，不要自己按 24 小时算；订单项带 `order_item_id`，售后按订单项而不是商品定位。
- 买家只能读自己的订单，他人订单按不存在（60001）处理；卖家可读全部订单。
- 「订单管理」「按客户」「按商品」三个入口在后端只是同一个列表的筛选条件（`status` / `buyer_id` / `goods_id` 可组合），进入详情一律按订单主键查；按商品筛选经订单项反查订单 ID。

### 订单错误码

| 码 | 常量 | HTTP | 含义 |
| --- | --- | --- | --- |
| 60001 | `ORDER_NOT_FOUND` | 404 | 订单不存在；买家操作他人订单同样按不存在处理 |
| 60002 | `ORDER_STATUS_INVALID` | 200 | 状态不允许该操作：跳级、重复推进、终态推进 |
| 60003 | `ORDER_NOT_CANCELABLE` | 200 | 当前状态不可取消：买家在已发货后、双方在交易完成后 |
| 60004 | `ORDER_STOCK_SHORTAGE` | 200 | 买家确认收货时库存不足，整单拒绝、订单停在已发货 |
| 83001 | `PAYMENT_CHANNEL_INVALID` | 200 | 支付渠道不在四个枚举内 |
| 83002 | `PAYMENT_NOT_ALLOWED` | 200 | 订单非待支付，或已过支付时限 |
| 83003 | `PAYMENT_FAILED` | 200 | 渠道返回失败，订单仍待支付、可重试 |
| 83004 | `REFUND_FAILED` | 200 | 退款失败（Mock 基本不触发，留给真实渠道） |
| 20011 | `GOODS_NOT_FOUND` | 404 | 下单时商品不存在 |
| 20019 | `GOODS_NOT_ON_SALE` | 200 | 下单时商品已下架 |
| 20015 | `STOCK_INVALID` | 200 | 下单数量超过库存 |
| 50002 | `PARAM_INVALID` | 400 | 明细为空、同一订单内重复商品、状态参数非法等 |

码表以 `ErrorCode` 枚举为准。重复取消按成功返回只是为了防抖，不是可依赖的业务契约；重复推进会返回 60002。

### 购物车与收藏

- 购物车行只存「买家 + 商品 + 加购时间」；数量与勾选由前端 localStorage 维护，不落库、也不产生接口。
- 「买家 + 商品」由唯一键保证唯一：重复加购不新增行，也不刷新加购时间。移除与取消收藏幂等，行不存在同样返回成功。
- 是否可选在读取时由商品状态派生，不落库：`selectable` 与 `detail_available` 都等价于「商品在售」。**库存不足仍可选中**，缺货由结算时校验拦截。已下架行保留展示并带 `off_shelf_reason`，复售自动恢复，不需要同步。
- 购物车列表不分页：前端要算总价与导航角标，翻页会漏行。收藏列表分页，默认 10 条，上限见分页配置。
- 转收藏整批成功或整批拒绝：入参里有一件不可选就整单拒绝、一行不动（70003）；只转本购物车真实存在的行，`moved_count` 恒等于真正转过去的件数。写收藏与删购物车行在同一事务。
- 详情页的「收藏 / 已收藏」用 `GET /api/buyer/favorites/{goodsId}` 查，不要靠翻收藏列表判断——列表分页，超过一页就会漏。
- 购物车与收藏不限流，与订单不同。

### 购物车与收藏错误码

| 码 | 常量 | HTTP | 含义 |
| --- | --- | --- | --- |
| 70001 | `CART_GOODS_NOT_SELECTABLE` | 200 | 加入购物车时商品不在售 |
| 70002 | `FAVORITE_GOODS_NOT_SELECTABLE` | 200 | 收藏时商品不在售 |
| 70003 | `CART_TO_FAVORITE_REJECTED` | 200 | 转收藏时入参含不可选商品，整批拒绝；message 带被拒商品 ID |

不设「行不存在」错误码：移除购物车行与取消收藏都按幂等处理，删不到也返回成功。

### 客户口径

- 客户列表与详情只读：字段仅限用户名、电话、默认交易地点与注册时间，不提供搜索、统计或导出。
- 购买历史不在客户详情里内嵌：卖家订单列表支持 `buyer_id` 筛选，直接按订单粒度查该客户的订单即可。

## 数据库

- 开发库 `simple_shop`、测试库 `simple_shop_test`、E2E 库 `simple_shop_e2e` 互相隔离，不要混用。
- 活动迁移脚本：`V1__baseline_schema.sql`（12 张表基线）、`V2__seed_initial_data.sql`（卖家种子）、
  `V3__stage3_goods_and_trade_schema.sql`（删意向与归档四表、商品加下架原因与时间、建订单/订单项/购物车/收藏）、
  `V4__stage4_payment_delivery_aftersale_schema.sql`（订单加收货/支付/物流字段并转七态、删买家默认交易地点、建地址与售后四表）。
- 迁移后共 16 张表；`V3` 只做结构变更与旧状态值换算，`V4` 不做任何数据回填。
- 数据库按阶段重建：升级前先备份，再删库或删数据卷；`V3` 可以用于阶段二存量库的原地升级（商品与类别数据保留）。
- `db/草稿/` 保留阶段三设计草稿供对照；`db/阶段三/` 是阶段三交付归档（V3 定稿）；`db/阶段一`、`db/阶段二` 是历史归档，Flyway 不会加载。
- `V4` 要求订单表为空：开发库与 E2E 库按重建流程执行（备份 → 删库 → 启动由 Flyway 跑到 V4），旧订单不迁移；`V1`–`V3` 不改。
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
