# simple-shop 后端

阶段二重构中的后端工程。当前完成 **S0 清理与骨架**、**S1 数据层**、**S2 公共能力与账号** 与 **S3 类别与商品**：V1/V2 数据库基线、实体/仓储、双角色会话与鉴权、买家注册/登录、卖家登录/改密、媒体上传、两级类别、商品发布/查询/库存/上下架/改挂和 springdoc 已落地。

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
  intention/   意向、队列序号 entity、repository、enums、队列端口实现
  history/     历史商品、历史意向 entity、repository、enums
src/main/resources/
  application.yml
  logback-spring.xml
  db/migration/  Flyway V1 基线、V2 种子
db/draft/      设计草案（保留作对照，不参与运行）
db/legacy/     阶段一脚本与数据库备份（不参与运行）
scripts/       注释编号门禁
```

`account`、`category`、`goods`、`intention`、`history` 五个功能包已建立 entity、repository、enums；S2 已补 `account` 的 service/controller/dto/support 与 `goods` 的媒体上传；S3 已补 `category` 的完整两级类别能力，以及 `goods` 的发布、查询、库存、上下架与改挂。controller 下按 seller/buyer 区分，公共查询放在 `controller/publicapi`。

## 构建与运行

在 `simple-shop-backend/` 目录执行：

```bash
mvn clean compile      # 仅编译，不需要数据库
mvn test               # 数据层 + API 测试，需要 MySQL；默认使用 simple_shop_test
mvn clean verify       # 编译 + 全部测试
mvn spring-boot:run    # 启动，需要 MySQL；默认使用 simple_shop
```

应用启动时由 Flyway 自动执行 `V1__baseline_schema.sql` 与 `V2__seed_initial_data.sql`，随后由 `ddl-auto=validate` 校验实体与表结构。

构建产物为 `target/simple-shop.war`，可用 `java -jar` 启动。

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
| PUT | `/api/seller/goods/{id}/category` | 改挂二级类别 |

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

## 数据库

- 阶段二使用全新基线，不迁移阶段一数据；阶段一旧库与旧测试库已删除，备份在 `db/legacy/backup.sql`、`db/legacy/test_db_backup.sql`。
- 活动迁移脚本是 `src/main/resources/db/migration/V1__baseline_schema.sql`（11 张表）与 `V2__seed_initial_data.sql`（卖家种子账号）。
- `db/draft/` 保留设计草案供对照，不参与运行；`db/legacy/` 的旧脚本不会被 Flyway 加载。
- 种子卖家：账号 `seller`，初始口令 `Abcd@1234`，首次登录后请修改。
- 测试库默认 `simple_shop_test`，可通过 `TEST_DB_URL` 覆盖；测试会自动建库并跑 Flyway。
- 已发布的迁移脚本不得修改，只能追加新版本。

## 代码约定

- 时间写入统一使用 `DatabaseTimeProvider`，库内一律 UTC。
- 会话区分买家与卖家角色，令牌不能互用。
- 商品图片只接受 JPG/PNG；描述媒体接受 JPG/PNG 和 MP4。文件名由服务端生成，数据库只存相对 URL，并做用途与目录穿越校验。
- 账号字段在 Service 层校验：用户名/账号 1~50 字符且不含空白/控制字符/尖括号；密码至少 8 字符且 UTF-8 不超过 72 字节；电话为 7~20 位数字与 `-`；默认交易地点可空、最多 50 字符。
- 类别名、商品名去首尾空白后 1~50 字符，拒绝控制字符、换行与尖括号；商品价格必须大于 0、不超过 999999.99 且最多两位小数；发布库存至少为 1。
- 商品图片与描述图片使用独立配置项；当前都限 5MB，但后续可以分别调整。
- 描述内容块按 `sort_no` 保存纯文本、图片、视频的混排顺序；纯文本整段 1000 字符上限由应用层校验，单个文本块在数据层也按 1000 字符预留。
- 商品媒体 URL 必须指向 `/media/goods/` 或 `/media/description/` 下已落盘的文件；发布时按用途、扩展名和文件存在性回验，拒绝外链和目录穿越。
- 注释只保留职责与非显然约束，不引用文档编号；可用 `pwsh -File scripts/check-comment-refs.ps1` 检查。
- 控制器只调用本功能 Service，跨功能读取走 Service 或只读端口。

## 当前状态

已完成：

- 删除买家 JSP 层、3 个 JSP Controller、4 个 JSP、买家静态资源与 JSP/JSTL 依赖。
- 删除口令码链路、交易流水、手动冻结、买家撤销/改资料相关代码与测试。
- 删除阶段一实体、仓储、枚举、业务 Service、卖家 Controller 与请求/响应 DTO（这些将按阶段二模型重写）。
- 旧迁移脚本移出活动目录；旧测试仅保留与业务模型无关的纯单元测试。
- 建立 `common` 包并完成角色化会话、双角色鉴权、图片/视频媒体存储、文本、审计、限流、错误码与健康检查。
- 增加类别、商品、账号与意向域 11 张表的阶段二基线，以及卖家种子账号。
- 建立 11 个实体、11 个仓储与 6 个枚举；完成 JSON 快照、主图唯一、队列序号唯一、状态/失败原因约束等数据层测试。
- S2 落地双角色会话与鉴权、买家注册/登录/退出、卖家登录/退出/改密、媒体上传、springdoc，以及账号字段的业务校验。
- S3 落地两级类别新增/重命名/删除与删除前置、商品发布/列表/详情/搜索、主图/图集、TEXT/IMAGE/VIDEO 描述块、库存调整、手动上下架、库存清零下架与改挂类别。
- 全部 141 个用例全绿（数据层 22 个、S2 接口与校验 23 个、S3 接口与字段规则 40 个、其余公共能力 56 个）；开发库与测试库均通过 Flyway 与 `ddl-auto=validate`。

下一步是 S4：买家意向、按商品队列、卖家交易裁决与库存扣减。
