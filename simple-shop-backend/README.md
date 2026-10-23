# simple-shop 后端

阶段二重构中的后端工程。当前完成 **S0 清理与骨架**：阶段一业务主干、JSP 层、口令码与交易流水已移除，可复用的公共能力已迁入 `common` 包，工程可编译、单元测试可运行。

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
| 前端 | 前后端分离，后端只提供 REST 与图片 |

## 目录结构

```text
src/main/java/com/simpleshop/
  SimpleShopApplication.java
  common/
    api/       统一响应、分页、错误码、异常处理、健康检查
    auth/      角色、会话、鉴权拦截器、当前用户、密码哈希
    config/    应用配置、Jackson、Web MVC
    media/     图片类型识别与图片落盘
    text/      文本规则、UTC 时间格式化
    audit/     关键操作日志
    time/      UTC 时间来源
    security/  限流
src/main/resources/
  application.yml
  logback-spring.xml
db/legacy/     阶段一迁移脚本归档（不参与运行）
scripts/       注释编号门禁
```

阶段二的业务包 `account`、`category`、`goods`、`intention`、`history` 会随后续阶段建立；每个功能包内再分 entity、repository、service、controller、dto，controller 下按 seller/buyer 区分。

## 构建与运行

在 `simple-shop-backend/` 目录执行：

```bash
mvn clean verify       # 编译 + 单元测试
mvn clean compile      # 仅编译
mvn spring-boot:run    # 启动，需要可用的 MySQL
```

启动前需要设置数据库连接；默认值指向本机 `simple_shop` 库。当前尚未加入阶段二数据库基线，S1 完成前应用无法通过 `ddl-auto=validate` 校验。

构建产物为 `target/simple-shop.war`，可用 `java -jar` 启动。

## 配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_URL` | 本机 `simple_shop` | JDBC 连接串，已带 UTC 与 utf8mb4 参数 |
| `DB_USERNAME` | `root` | 数据库账号 |
| `DB_PASSWORD` | `123456` | 数据库口令 |
| `IMAGE_DIR` | `./data/images` | 图片根目录，容器部署时必须挂持久卷 |
| `AUDIT_LOG_PATH` | `logs` | 审计日志目录，保留 90 天 |

其余配置项见 `src/main/resources/application.yml`，会绑定到 `com.simpleshop.common.config.AppProperties`。

## 数据库

- 阶段二使用全新基线，不迁移阶段一数据。
- 阶段一脚本已移到 `db/legacy/`，不会被 Flyway 加载。
- 新基线加在 `src/main/resources/db/migration/`；已发布的脚本不得修改，只能追加。

## 代码约定

- 时间写入统一使用 `DatabaseTimeProvider`，库内一律 UTC。
- 会话区分买家与卖家角色，令牌不能互用。
- 图片只接受 JPG/PNG 且不超过 5MB，文件名由服务端生成并做目录穿越校验。
- 注释只保留职责与非显然约束，不引用文档编号；可用 `pwsh -File scripts/check-comment-refs.ps1` 检查。
- 控制器只调用本功能 Service，跨功能读取走 Service 或只读端口。

## 当前状态

已完成：

- 删除买家 JSP 层、3 个 JSP Controller、4 个 JSP、买家静态资源与 JSP/JSTL 依赖。
- 删除口令码链路、交易流水、手动冻结、买家撤销/改资料相关代码与测试。
- 删除阶段一实体、仓储、枚举、业务 Service、卖家 Controller 与请求/响应 DTO（这些将按阶段二模型重写）。
- 旧迁移脚本移出活动目录；旧测试仅保留与业务模型无关的纯单元测试。
- 建立 `common` 包并完成角色化会话、双角色鉴权、图片、文本、审计、限流、错误码与健康检查。

下一步是 S1：阶段二数据库基线、实体、仓储、枚举与数据层测试。
