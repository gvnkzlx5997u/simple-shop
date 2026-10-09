# simple-shop

在线购物系统的**后端数据层**：数据库 DDL、JPA 实体与仓储接口。

> **范围**：数据库表结构、JPA `entity`、Spring Data JPA `repository`。
> **不含**：Service、Controller、DTO、页面（`spring.main.web-application-type=none`，启动后不占端口）。
> **依据**：《数据库与数据层设计说明书》v1.2 ｜《需求规格说明书》v1.1 ｜《开发方决策记录》。

---

## 1. 技术基线

| 项 | 取值 |
| --- | --- |
| 构建工具 | Maven |
| Spring Boot | 3.5.16（3.5 线） |
| Java | 编译目标 **21**（构建 JDK 需 ≥ 21） |
| MySQL | ≥ 8.0.16（开发库 8.0.44） |
| 持久化 | Spring Data JPA + Hibernate 6.6.53.Final |
| schema 版本化 | Flyway 11.7.2 + 手写 SQL |
| 基础包名 | `com.simpleshop` |
| 连接池 | HikariCP（Spring Boot 默认） |

## 2. 构建与运行

> ⚠️ **请在工程根目录（`simple-shop/simple-shop-backend/`）执行 `mvn`**，原因见 §5。

```bash
mvn clean verify        # 编译 + 全部测试
mvn clean compile       # 仅编译
mvn spring-boot:run     # 启动（无 web 层：连库 → Flyway 迁移 → 结构校验后正常退出）
mvn test                # 仅跑测试
```

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
├── security/                       # PasswordHasher、PasswordHashTool
└── persistence
    ├── entity/                     # 7 个实体
    ├── enums/                      # 6 个枚举
    ├── time/                       # DatabaseTimeProvider（唯一时间来源）
    └── repository/                 # 7 个仓储接口

src/main/resources
├── application.yml
└── db/migration/                   # V1__baseline_schema.sql、V2__seed_initial_data.sql

src/test
├── java/com/simpleshop/            # 测试基类 + 3 个测试类
└── resources/application-test.yml  # 测试库配置（simple_shop_test）
```

包结构详见《数据库与数据层设计说明书》§6.1。原设计的 `persistence/id` 包与自定义主键生成器
**已废弃、不再建立**（主键改为「前缀 + 标准 UUID」，在实体 `@PrePersist` 中赋值）。

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

实现细节（实体映射要点、加锁契约、归档口径、测试注意事项、本机环境特殊化处理核对表、
以及设计说明书的 4 处实现期订正）统一记录在：

- **[《交接说明》](./docs/数据层/交接说明.md)** —— 环境核对、实现要点、测试口径、设计文档订正
- **《数据库与数据层设计说明书》v1.2** —— 表结构、字段口径、仓储方法表的权威依据
