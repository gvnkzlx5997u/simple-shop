# simple-shop

> **阶段二提示**：本文档描述阶段一后端；设计/过程文档已迁移至 [simple-shop-doc](../simple-shop-doc/阶段一/后端文档/README.md)。阶段二将按功能分包重写，以 [后端开发计划](../simple-shop-doc/阶段二/后端开发计划.md) 为准。

在线购物系统的**后端**：数据库 DDL + JPA 实体与仓储（阶段二）+ **业务层 Service 与卖家端接口层 Controller（阶段三）** + **买家端 JSP 页面（任务 4）**。

> **当前范围**：数据层、业务层（Service）、**卖家端 REST 接口 `I11-01` ~ `I11-16`（16 个）**、
> **买家端 JSP 页面 `B11-01` ~ `B11-07`（4 个页面，服务端渲染）**——三部分均已实现。
> 买家端**不出 REST 契约**（`C-05`、`A-02`），页面路由与实现口径见 §4.2，偏离与取舍见 §8。
> **依据**：《数据库与数据层设计说明书》v1.2 ｜《需求规格说明书》v1.1 ｜
> 《后端业务层与卖家端接口层开发方案》（已拆分为 [后端文档/业务层与接口层/](../simple-shop-doc/阶段一/后端文档/业务层与接口层/)，见该目录的 README）。
>
> **验证状态**：`mvn clean verify` → **BUILD SUCCESS，484 用例全绿**（462 原有 + 22 买家端 JSP）。
> ⚠️ **交接请先读 [《业务层与接口层 · 交接说明》](../simple-shop-doc/阶段一/后端文档/业务层与接口层/交接说明.md)**——
> 环境核对、硬口径、容易踩的坑、已知限制都在那里（其 §11 的「买家端 JSP 五项」已由本轮的 §4.2 落实）。

---

## 1. 技术基线

| 项 | 取值 |
| --- | --- |
| 构建工具 | Maven |
| Spring Boot | 3.5.16（3.5 线） |
| Java | 编译目标 **21**（构建 JDK 需 ≥ 21） |
| MySQL | ≥ 8.0.16（开发库 8.0.44） |
| 持久化 | Spring Data JPA + Hibernate 6.6.53.Final |
| Web | **Spring MVC（内嵌 Tomcat）+ JSP / JSTL**；打包为**可执行 war**（`java -jar`） |
| 会话 | **进程内**（`SessionStore`，后端单容器；✅ 已定 Q-3） |
| schema 版本化 | Flyway 11.7.2 + 手写 SQL |
| 基础包名 | `com.simpleshop` |
| 连接池 | HikariCP（Spring Boot 默认） |

## 2. 构建与运行

> ⚠️ **请在工程根目录（`simple-shop/simple-shop-backend/`）执行 `mvn`**，原因见 §5。

```bash
mvn clean verify        # 编译 + 全部 484 个测试（约 75 秒）
mvn clean compile       # 仅编译
mvn spring-boot:run     # 启动 web 应用（默认 8080 端口）
mvn clean package       # 产出 target/simple-shop.war
java -jar target/simple-shop.war   # 以 war 方式启动（真实端口验证用这条）
mvn test                # 仅跑测试
```

**运行期环境变量**（容器化时用它覆盖，详见《交接说明》§1.2）：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | 本机 `simple_shop` / `root` / `123456` | 连接串须保留 `connectionTimeZone=UTC` |
| `IMAGE_DIR` | `./data/images` | 商品图片目录，**容器化须挂持久卷** |
| `-DAUDIT_LOG_PATH`（**系统属性**，非环境变量） | `logs` | 审计日志目录 |

**JDK**：编译目标为 Java 21，构建 JDK **不得低于 21**（pom 中的 enforcer 规则会拦截并提示）。
更高的 JDK 也可构建（已在 JDK 21 与 24 上实测：24 构建出的字节码仍为 Java 21，全部测试通过），
因此不要求本机必须恰好装 JDK 21。

**控制台乱码**（中文 Windows 常见）时可加：

```powershell
$env:MAVEN_OPTS='-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8'
```

## 3. 数据库

```sql
CREATE DATABASE IF NOT EXISTS simple_shop
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

连接配置在 `src/main/resources/application.yml`，三项均可用环境变量覆盖（默认值面向本机开发）：

| 变量 | 默认值 |
| --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/simple_shop?...&createDatabaseIfNotExist=true` |
| `DB_USERNAME` | `root` |
| `DB_PASSWORD` | `123456` |

> 连接串带 `createDatabaseIfNotExist=true` 是为了本机开箱即用；
> **正式环境请预先建库并交 DBA 管控**，并通过环境变量注入账号口令，不要沿用默认值。

### 3.1 库表版本化

表由 Flyway 迁移脚本建立，`ddl-auto` 固定为 `validate`（核对实体与库结构一致）：

| 脚本 | 内容 |
| --- | --- |
| `V1__baseline_schema.sql` | 7 张表 + 索引 + 外键 + 物理性 CHECK |
| `V2__seed_initial_data.sql` | 队列序号行 + 卖家账号（幂等） |

> **⚠️ 已发布的迁移脚本不得修改**（会导致 Flyway 校验和失配、启动直接报错）。
> 后续每次变更新增 `V3`、`V4`…

## 4. 代码结构

```
src/main/java/com/simpleshop
├── SimpleShopApplication.java
├── config/                         # AppProperties、JacksonConfig
├── security/                       # PasswordHasher、PasswordHashTool
├── session/                        # SessionStore（进程内会话）
├── service/                        # ★ 阶段三：业务层
│   ├── dto/ support/ exception/    #   响应 DTO / 校验器 / ErrorCode
│   ├── SellerAuthService、SellerGoodsService、SellerIntentionService、SellerHistoryService
│   ├── BuyerIntentionService、PasscodeService、SubmitRateLimiter   # 买家端 Service（Controller 见 §4.2）
│   └── ArchiveService、ImageStorageService、OperationLogService
├── web/                            # ★ 接口层：卖家端 5 个 Controller + 买家端 3 个
│   ├── BuyerHomeController、BuyerIntentController、BuyerQueryController   # 买家端 JSP（§4.2）
│   └── dto/ ApiResponse GlobalExceptionHandler SellerAuthInterceptor WebMvcConfig
└── persistence
    ├── entity/                     # 7 个实体
    ├── enums/                      # 6 个枚举
    ├── time/                       # DatabaseTimeProvider（唯一时间来源）
    └── repository/                 # 7 个仓储接口

src/main/resources
├── application.yml                 # 含 spring.mvc.view：/WEB-INF/views/ + .jsp
├── logback-spring.xml              # 控制台 + 独立审计 appender（3 个月轮转）
├── static/buyer/                   # 买家端静态资源：buyer.css、product.svg
└── db/migration/                   # V1__baseline_schema.sql、V2__seed_initial_data.sql

src/main/webapp/WEB-INF/views/      # ★ 买家端 JSP（放 WEB-INF 下 ⇒ 浏览器不能直接访问）
├── index.jsp                       # P10-01 当前商品页（GET /）
├── intent-form.jsp                 # P10-02 提交意向表单（GET /intent/new、POST /intent）
├── intent-success.jsp              # P10-03 提交成功页（无独立 URL，仅提交成功后在同一请求内渲染）
└── query.jsp                       # P10-04 口令码查询与操作（GET|POST /query、/query/update、/query/revoke）

src/test
├── java/com/simpleshop/            # 25 个测试类 / 484 用例 + testing/ 测试设施
└── resources/                      # application-test.yml、csv/（15 张用例表）、images/
```

包结构详见《数据库与数据层设计说明书》§6.1。原设计的 `persistence/id` 包与自定义主键生成器
**已废弃、不再建立**（主键改为「前缀 + 标准 UUID」，在实体 `@PrePersist` 中赋值）。

### 4.1 业务层与接口层速览

| 项 | 内容 |
| --- | --- |
| 接口 | **`I11-01` ~ `I11-16` 共 16 个**：会话/账号 3（S3）、商品与图片 6（S4）、意向与交易 5（S6）、历史 2（S8） |
| 买家端页面 | **`B11-01` ~ `B11-07` 共 7 个端点 / 4 个 JSP**：`@Controller` 服务端渲染，**无鉴权、无 REST 契约**，见 §4.2 |
| 统一响应 | `{code, message, data}`；**业务规则拒绝返回 HTTP 200 + 业务码**（仅 4 个例外：`10002`→401、`20011`→404、`50000`→500、`50002`→400） |
| 错误码 | `ErrorCode` 是唯一来源，域归属 `1xxxx` 认证 / `2xxxx` 商品 / `3xxxx` 意向 / `4xxxx` 口令码 / `5xxxx` 通用；**不得增删编号** |
| 鉴权 | `Authorization: Bearer <token>`，拦截器只覆盖 `/api/seller/**`（登录端点排除）；**买家端不需要鉴权** |
| 事务与加锁 | 一个业务动作一个事务；卖家写 `@Transactional(timeout=5)`；**统一加锁顺序 ①序号表 → ②商品行 → ③意向行** |
| 归档 | 手动下架与标记成功都会在**同一事务内**把商品与全部意向搬进历史表并硬删当前表 |
| 审计日志 | 独立 appender（`com.simpleshop.audit`），UTC 时间戳、保留 90 天；**不记口令码与密码** |
| 性能 | 查询 P95 ≤ 150ms、典型写 P95 ≤ 59ms（预算 500ms/1s）；⚠️ **归档满队列（1000 条）未达标（1.7~2.8s）**，见《交接说明》§7.1 |

**⚠️ 三条最容易踩的**（详见《交接说明》§4~§6）：DTO 注解只判「有没有」；
`I11-15` 与 `I11-10` 的排序口径**相反**；`trade_start`/`create_at`/`trade_end` 都是**秒精度**，
同一秒内的先后**不确定**——**不要写断言顺序的用例**。

### 4.2 买家端 JSP 页面（本轮交付）

买家端是**服务端渲染**（`C-05`、`A-02`）：**不出 REST 契约**，凭证是**口令码**而非会话（`O-01`、`BR-32`），
因此**不需要鉴权**——`WebMvcConfig` 的拦截器只覆盖 `/api/seller/**`。3 个 `@Controller` 返回**逻辑视图名**，
由 `application.yml` 的 `spring.mvc.view`（`/WEB-INF/views/` + `.jsp`）解析到 4 个 JSP；

| 端点 | 编号 | Controller | 视图 | 页面需求 |
| --- | --- | --- | --- | --- |
| `GET /` | `B11-01` | `BuyerHomeController` | `index.jsp` | `P10-01` 当前商品页（含「无商品」空态与「已冻结」态） |
| `GET /intent/new` | `B11-02` | `BuyerIntentController` | `intent-form.jsp` | `P10-02` 提交意向表单 |
| `POST /intent` | `B11-03` | `BuyerIntentController` | 成功 → `intent-success.jsp`；被拒 → 回表单 | `P10-03` 成功页（口令码仅展示一次） |
| `GET` / `POST /query` | `B11-04` / `B11-05` | `BuyerQueryController` | `query.jsp` | `P10-04` 查询（`NotFound` / `Expired` / `Active` 排队中 / `Active` 交易中） |
| `POST /query/update` | `B11-06` | `BuyerQueryController` | `query.jsp` | 改姓名 / 电话（`FR-16`） |
| `POST /query/revoke` | `B11-07` | `BuyerQueryController` | `query.jsp` | 撤销意向（`FR-17`） |

**四条实现口径**（逐条理由见代码注释与 §8）：

1. **JSP 放 `WEB-INF/` 下**：浏览器**不能直接访问**，必须经 Controller 渲染——这正是 `B11-03` 口径②
   「提交成功页不得通过 URL 直接访问」（`K5-02`、`BR-24`）成立的前提；实测 `GET /intent/success` → **404**。
2. **成功页在同一个 POST 请求内渲染**（既不 `redirect` 也不 `forward:`）：口令码只经**请求属性**传递，
   不进 URL、不进 session；代价是刷新会**重发 POST**——这是表单应用的既有语义，也正好使口令码不再展示。
3. **响应的 record 摊平成 `LinkedHashMap` 再交视图**：JSP 的 EL 只认 JavaBeans，读不到 record 的访问器
   （直接写 `${goods.status}` 会 `PropertyNotFoundException`），故 Controller **逐字段显式**搬进 `Map`，
   JSP 里照旧写 `${goods.name}`（EL 对 `Map` 的属性访问就是 key 查找）。**不用反射式拷贝**，保证
   「页面能看到哪些字段」是被明确挑选出来的（同设计说明书 §4.6.4）。
4. **文案唯一权威是 `BuyerPrompt`**：JSP 不写死中文提示；商品文本与买家输入一律走 `<c:out>` /
   `fn:escapeXml`——输出转义是输入拒绝之外的第二层，**两层不可互补**。

> 买家端**未纳入**卖家端鉴权拦截器、`/images/**` **公开可访问**（买家看商品图所必需，`FR-12`）——
> 这两处是**有意为之**，不是遗漏。若哪天有人把 `/**` 加进拦截器，买家端会整体 401，首页直接打不开。

## 5. ⚠️ Maven 本地仓库在项目内

本机 `~/.m2/repository` **不可写**，Maven 无法在其中落盘依赖。因此 `.mvn/maven.config` 把本地仓库
重定向到项目内：

```
-Dmaven.repo.local=.m2/repository
```

- 依赖会下载到 `simple-shop/simple-shop-backend/.m2/repository`（约 100 MB，已在 `.gitignore` 中排除）。
- **该路径相对当前工作目录解析**，所以必须在工程根目录执行 `mvn`；
  在别处用 `mvn -f <path>/pom.xml` 调用会把依赖下到**调用目录**。
- 换到 `~/.m2` 可写的机器时，删除 `.mvn/maven.config` 即可改回全局仓库。

## 6. 核心约定

### 6.1 时间

- 库内所有 `datetime` 一律为 **UTC**，由 `DatabaseTimeProvider` 统一提供。
- **禁止**直接使用 `LocalDateTime.now()` / `new Date()` / `System.currentTimeMillis()` 入库。
- 实体时间字段必须标注 `@JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)`，否则会**静默偏 8 小时**。
- UTC+8 展示转换**不在数据层**，由应用层/前端完成。

### 6.2 校验责任

**数据库只管「结构对不对」（非空、唯一、外键、时间不倒流），「值对不对」全部归应用层。**
枚举取值、数值区间、格式（口令码/电话）、文本规则、长度上限、跨表不变量（`INV-*`）、队列上限、
状态迁移合法性，均由 Service 拦截并给出提示文案。

### 6.3 主键与归档

- 主键 `<前缀> + 标准 UUID`，列宽 `varchar(50)`；各实体在 `@PrePersist` 中 **`if (id == null)`** 赋值。
- 该 `if` 是硬约束：归档时历史行须**沿用原 ID**，由调用方显式赋原值。

### 6.4 加锁

加锁方法一律以 `...ForUpdate` 结尾，**必须在事务内调用**。
统一加锁顺序：**① 序号表行 → ② 商品行 → ③ 意向行**（防死锁）。
凡改变商品状态、或改变「是否有意向处于 `trading`」的事务，必须首先锁商品行。

### 6.5 与业务层的关系（阶段三之后）

数据层的**校验责任边界不变**：数据库只管结构（非空、唯一、外键、时间不倒流），「值对不对」全归 Service。
⚠️ 业务层**没有改动任何数据层交付物**（实体、仓储、V1/V2 迁移脚本），
唯一一处配置改动是新增 `hibernate.jdbc.batch_size: 50`（性能实测逼出来的，见《交接说明》§7.1）。

## 7. 初始账号与改密

| 项 | 值 |
| --- | --- |
| 登录账号 | `seller` |
| 初始口令 | `Abcd@1234` |
| 库中存储 | BCrypt 哈希（脚本中无明文） |

口令不以明文入库，改密需先用 `PasswordHashTool` 生成哈希，再执行 `UPDATE ... SET password = '<哈希>', update_at = UTC_TIMESTAMP()`。
**完整步骤（含「库已上线时新增 V3 脚本而非改 V2」）见《交接说明》§9.2。**

> ⚠️ 初始口令是约定的固定值：它能满足「不存明文」，但**不提供保密性**。
> 首次登录后请立即改密，上线前换成只有运维知道的口令。

## 8. 已知偏离、决定与取舍

> 这里登记**上游文档未规定、或与上游写法不同**的后端口径与已有取舍；部署相关的取舍见根目录 [README.md](../README.md)。
> 逐条细节在《业务层与接口层 · 交接说明》与设计文档里，下表只做索引。

### 8.1 上游未规定 / 与上游不同的口径

| # | 事项 | 本项目的口径 | 依据 / 详见 |
| --- | --- | --- | --- |
| 1 | 业务规则拒绝的 HTTP 状态 | 默认 **HTTP 200 + 业务码**；仅 4 个例外（`10002`→401、`20011`→404、`50000`→500、`50002`→400） | `§11.3.4`，交接 §4.1 |
| 2 | `message` 的用途 | **ASCII 英文，仅供日志与排查**；界面文案由前端 / `BuyerPrompt` 按 `code` 映射第 10 章文案表 | 已定 Q-13，交接 §4.2 |
| 3 | 请求 DTO 上的校验注解 | **只判「有没有」**（`@NotBlank` 等）；有专属错误码的字段不得用 `@Size`/`@Pattern`，否则专属码永远不可达 | 交接 §4.3 |
| 4 | 契约未规定的三处分支 | 媒体类型协商失败 → 400 + `50002`；HTTP 方法不支持 → 405；Controller 方法参数约束不通过 → 400 + `50002` | 交接 §4.4 |
| 5 | `price` 的类型 | 全程**字符串**（入参 `String` → `BigDecimal` 校验 → 出参 `toPlainString()`），不做浮点 | `11-F`，交接 §4.5 |
| 6 | JSON 命名 | `snake_case` 靠**逐个 `@JsonProperty`**，不配全局命名策略（会连带影响实体） | 交接 §4.6 |
| 7 | 三个秒精度排序键 | `trade_start` / `create_at` / `trade_end` 为**秒**精度，同秒先后不确定 ⇒ 界面不声称严格时序、用例不断言顺序 | 交接 §6 |
| 8 | 买家端成功页的转交方式 | 不用 `redirect` / `forward:`，在同一个 POST 请求内渲染成功视图；口令码仅经请求属性 | `K5-02`、`BR-24`，§4.2 |
| 9 | record 响应与 JSP EL | Controller 逐字段摊平成 `Map` 再交视图（record 访问器不符合 JavaBeans 规范） | §4.2 |
| 10 | 买家端 `contactByToken` | 为 `P10-04` 预填/回显新增**只读**方法；不给 `PasscodeQueryResult.Active` 加字段（最小暴露），Controller 也不直连 Repository | 任务 4 实现记录 |

### 8.2 有意保留的限制（已接受）

| 项 | 说明 |
| --- | --- |
| 会话在进程内 | 后端**单容器**；**不可多副本 / 负载均衡**，容器重启丢会话（`Q-3`、`9-O`）。改多副本须换共享存储并复核 `C-04` |
| 超出 `max-swallow-size`（10MB）的上传 | 仍会**断连**——有意不用 `-1`，不给攻击者白送资源消耗；5MB 上限内的拒绝仍正常返回 `20009` |
| `publishGoods` 的 `INV-01` 并发竞态 | **已接受**（`DEC-DB-06`）：单管理员、无注册入口；将来引入多管理员须回头重评 |
| 限流 | **固定窗口、计数在进程内**（`Q-8` 最小版本）；多副本时阈值会变成 `10 × 副本数` |
| 上传文件名 | **不使用原文件名**，服务端生成 UUID 文件名（防枚举、防路径穿越） |
| 审计日志 | 只有基本审计，**无测试保证**（用户明确降低要求）；可用「真实端口实测 + 全量日志统计 + 调用点走查」复核 |

---

## 9. 更多细节

实现细节统一记录在文档里（**都是「代码里看不出为什么」的部分**）：

| 想了解 | 看 |
| --- | --- |
| **接手本工程**（环境核对、硬口径、坑、限制） | **[《业务层与接口层 · 交接说明》](../simple-shop-doc/阶段一/后端文档/业务层与接口层/交接说明.md)** |
| 接口契约、业务口径、测试策略（**设计文档 16 篇**） | [后端文档/业务层与接口层/设计文档/](../simple-shop-doc/阶段一/后端文档/业务层与接口层/设计文档/README.md) |
| 每个阶段**实际怎么做的、踩了什么**（**实现过程记录 9 篇**） | [后端文档/业务层与接口层/实现过程记录/](../simple-shop-doc/阶段一/后端文档/业务层与接口层/实现过程记录/README.md) |
| 表结构、字段口径、仓储方法表 | 《数据库与数据层设计说明书》v1.2 ｜ [后端文档/数据层/交接说明.md](../simple-shop-doc/阶段一/后端文档/数据层/交接说明.md) |
