# simple-shop

在线购物系统的**后端**：数据库 DDL + JPA 实体与仓储（阶段二）+ **业务层 Service 与卖家端接口层 Controller（阶段三）**。

> **当前范围**：数据层、业务层（Service）、**卖家端 REST 接口 `I11-01` ~ `I11-16`（16 个，全部实现）**。
> **不含**：买家端页面（`B11-xx` 的 JSP，属下一轮；其 Service 已就绪）。
> **依据**：《数据库与数据层设计说明书》v1.2 ｜《需求规格说明书》v1.1 ｜
> 《后端业务层与卖家端接口层开发方案》（已拆分为 [docs/业务层与接口层/](docs/业务层与接口层/)，见该目录的 README）。
>
> **验证状态**：`mvn clean verify` → **BUILD SUCCESS，462 用例全绿**。
> ⚠️ **交接请先读 [《业务层与接口层 · 交接说明》](docs/业务层与接口层/交接说明.md)**——
> 环境核对、硬口径、容易踩的坑、已知限制与下一轮待办都在那里。

---

## 1. 技术基线

| 项 | 取值 |
| --- | --- |
| 构建工具 | Maven |
| Spring Boot | 3.5.16（3.5 线） |
| Java | 编译目标 **21**（构建 JDK 需 ≥ 21） |
| MySQL | ≥ 8.0.16（开发库 8.0.44） |
| 持久化 | Spring Data JPA + Hibernate 6.6.53.Final |
| Web | **Spring MVC（内嵌 Tomcat）**；打包为 **war**（为下一轮 JSP 准备） |
| 会话 | **进程内**（`SessionStore`，后端单容器；✅ 已定 Q-3） |
| schema 版本化 | Flyway 11.7.2 + 手写 SQL |
| 基础包名 | `com.simpleshop` |
| 连接池 | HikariCP（Spring Boot 默认） |

## 2. 构建与运行

> ⚠️ **请在工程根目录（`simple-shop/simple-shop-backend/`）执行 `mvn`**，原因见 §5。

```bash
mvn clean verify        # 编译 + 全部 462 个测试（约 75 秒）
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
│   ├── BuyerIntentionService、PasscodeService、SubmitRateLimiter   # 买家端（无 Controller）
│   └── ArchiveService、ImageStorageService、OperationLogService
├── web/                            # ★ 阶段三：卖家端接口层（5 个 Controller）
│   └── dto/ ApiResponse GlobalExceptionHandler SellerAuthInterceptor WebMvcConfig
└── persistence
    ├── entity/                     # 7 个实体
    ├── enums/                      # 6 个枚举
    ├── time/                       # DatabaseTimeProvider（唯一时间来源）
    └── repository/                 # 7 个仓储接口

src/main/resources
├── application.yml
├── logback-spring.xml              # 控制台 + 独立审计 appender（3 个月轮转）
└── db/migration/                   # V1__baseline_schema.sql、V2__seed_initial_data.sql

src/test
├── java/com/simpleshop/            # 24 个测试类 / 462 用例 + testing/ 测试设施
└── resources/                      # application-test.yml、csv/（15 张用例表）、images/
```

包结构详见《数据库与数据层设计说明书》§6.1。原设计的 `persistence/id` 包与自定义主键生成器
**已废弃、不再建立**（主键改为「前缀 + 标准 UUID」，在实体 `@PrePersist` 中赋值）。

### 4.1 业务层与接口层速览（阶段三交付）

| 项 | 内容 |
| --- | --- |
| 接口 | **`I11-01` ~ `I11-16` 共 16 个**：会话/账号 3（S3）、商品与图片 6（S4）、意向与交易 5（S6）、历史 2（S8） |
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

## 8. 更多细节

实现细节统一记录在文档里（**都是「代码里看不出为什么」的部分**）：

| 想了解 | 看 |
| --- | --- |
| **接手本工程**（环境核对、硬口径、坑、限制、下一轮待办） | **[《业务层与接口层 · 交接说明》](docs/业务层与接口层/交接说明.md)** |
| 接口契约、业务口径、测试策略（**设计文档 16 篇**） | [docs/业务层与接口层/设计文档/](docs/业务层与接口层/设计文档/README.md) |
| 每个阶段**实际怎么做的、踩了什么**（**实现过程记录 9 篇**） | [docs/业务层与接口层/实现过程记录/](docs/业务层与接口层/实现过程记录/README.md) |
| 表结构、字段口径、仓储方法表 | 《数据库与数据层设计说明书》v1.2 ｜ [docs/数据层/交接说明.md](docs/数据层/交接说明.md) |
| 上游需求（含 16 个接口的契约） | `../需求规格说明书/`（第 11 章为接口需求） |
