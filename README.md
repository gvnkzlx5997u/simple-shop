# simple-shop 在线购物系统

> 本文件是项目的**简介与仓库结构说明**。业务需求、部署与构建细节分列在下列文档中，本文只做索引与「为什么这么定」的登记。

---

## 一、项目简介

simple-shop 是一个**极简单商品**的在线购物系统，形态由三条硬约束共同定义：

| 约束 | 内容 |
| --- | --- |
| 单商品 | 同一时间只经营**一件**商品；卖掉一件，再发布下一件 |
| 单卖家 | 系统**只有一个卖家账号**，不做多账号与权限分级 |
| 线下交易 | 线上只负责**撮合与排队**，钱货两清在线下完成，系统不承载支付与物流 |

**一句话定位**：卖家在线发布商品，买家在线留下联系方式排队，卖家线下成交后回到系统登记结果。

**系统解决的核心问题**：让「一件商品被多个买家想要」这件事有明确的先后秩序，并让买卖双方在系统外联系时都有可靠依据。

系统由**三端**组成：

| 端 | 形态 | 代码位置 |
| --- | --- | --- |
| **买家端** | 服务端渲染 **JSP**（首页即当前商品，URL 不含商品 ID）；买家不注册、不登录，凭证是**口令码** | `simple-shop-backend/src/main/webapp/WEB-INF/views/` |
| **卖家端** | **Vue 3 + Vite** 单页应用（发布商品、冻结/解冻、下架、进入交易、标记交易结果、历史查询） | `simple-shop-frontend/` |
| **后端** | **Spring Boot 3.5 / Java 21 / MySQL 8 / Flyway**，打包为**可执行 war**，同时承载买家端 JSP 与卖家端 REST | `simple-shop-backend/` |

---

## 二、文档地图

| 想了解 | 看 |
| --- | --- |
| 业务需求（`FR` / `BR` / `INV` / `NFR` / `AC`、页面与接口契约） | 《需求规格说明书》 |
| **部署与运维**（容器拓扑、镜像、启动、初始化、备份、改密、升级回滚、故障排查） | [安装与部署手册.md](安装与部署手册.md) |
| 本地构建与运行命令 | [构建过程手册.md](构建过程手册.md) |
| 后端实现（数据层 / 业务层 / 接口层 / 买家端 JSP）与后端偏离取舍 | [simple-shop-backend/README.md](simple-shop-backend/README.md) ｜ `simple-shop-backend/docs/` |
| 卖家前端实现与前端偏离取舍 | [simple-shop-frontend/README.md](simple-shop-frontend/README.md) |
| 端到端自动验收（Playwright 驱动 Edge，36 条） | [simple-shop-test-e2e/验收测试说明.md](simple-shop-test-e2e/验收测试说明.md) |

---

## 三、仓库结构

```
simple-shop/
├─ README.md                       ← 本文件（项目简介与仓库结构）
├─ 安装与部署手册.md                ← 部署/运维主文档（含容器化决策 D-1 ~ D-13）
├─ 构建过程手册.md                  ← 本地构建/运行命令速查
├─ simple-shop-backend/            ← 后端 + 买家端 JSP（Spring Boot，可执行 war）
│  ├─ src/main/java/com/simpleshop/   数据层 / 业务层 / 接口层（卖家 REST + 买家端 Controller）
│  ├─ src/main/webapp/WEB-INF/views/  买家端 4 个 JSP（放 WEB-INF 下，浏览器不能直接访问）
│  ├─ src/main/resources/db/migration/ Flyway 迁移脚本 V1 / V2
│  ├─ docs/                           数据层、业务层与接口层的设计文档与交接说明
│  ├─ Dockerfile                      后端镜像（多阶段：Maven 构建 war + JRE21 运行）
│  └─ README.md                       后端说明与「已知偏离、决定与取舍」
├─ simple-shop-frontend/           ← 卖家端 SPA（Vue 3 + Vite + Pinia）
│  ├─ src/                            页面、组件、接口层、校验、状态
│  ├─ Dockerfile / nginx.conf         前端镜像（Vite 构建 + nginx 托管与反向代理）
│  └─ README.md                       前端说明与「已知偏离与限制」
├─ deploy/                         ← 部署选项甲：**本地构建**
│  ├─ docker-compose.yml              compose 在宿主机 build 后端 / 前端镜像
│  ├─ .env.example                    提交；`.env` 存真实口令、不入库
│  ├─ .gitignore
│  └─ mysql/init/01-database.sql      只建库（表由 Flyway 建）
├─ deploy-cloud/                   ← 部署选项乙：**云端镜像**
│  ├─ docker-compose.yml              直接 pull Docker Hub 预构建镜像
│  ├─ .env.example / .gitignore
│  └─ mysql/init/01-database.sql      与甲完全相同
└─ simple-shop-test-e2e/           ← Playwright 端到端验收（36 条，跑独立测试库）
   ├─ tests/                          买家端 10 条 + 卖家端 19 条 + 安全 7 条
   ├─ 1-启动测试环境.bat / 2-跑验收测试.bat / 3-查看测试报告.bat
   └─ 验收测试说明.md
```

> `deploy/` 与 `deploy-cloud/` 的**服务、网络、卷、健康检查、端口与 URL 规划完全一致**，只有 backend / frontend 两段是 `build:` 还是 `image:`；
> 两者 compose 项目名都是 `simple-shop`，因此**同一台宿主机上二选一，不要同时运行**（端口 80 与卷名都会冲突）。

---

## 四、容器化部署：决定与取舍

> 本章登记「为什么这么定」与已知取舍；**操作步骤见 [安装与部署手册.md](安装与部署手册.md)**。

### 4.1 部署形态（已定）

- **三容器 + 三命名卷 + 一个自定义 bridge 网络**：`mysql`（健康后）→ `backend` → `frontend`（nginx）。
- **只有 nginx 暴露宿主端口 80**；`backend`（内部 8080）与 `mysql`（内部 3306）**不映射宿主端口**，排障走 `docker compose exec`。
- 资源名以 `simple-shop` 为 compose 项目名解析：网络 `simple-shop_shop-net`；卷 `simple-shop_mysql-data`、`simple-shop_backend-images`、`simple-shop_backend-logs`。
- **两个等价的部署选项**：
  - **甲 本地构建**（`deploy/`）：在目标机上构建 backend / frontend 镜像，首次或源码有改动时用；构建期可用镜像源加速。
  - **乙 云端镜像**（`deploy-cloud/`）：直接拉取 Docker Hub 的 `lrhg173c/simple-shop:backend` / `:frontend`，目标机无需构建工具链、便于快速复现同一份镜像。

### 4.2 URL 与路由约定（部署引入）

| 访问路径 | 由谁处理 |
| --- | --- |
| `/`、`/query`、`/intent/**` | 买家端 JSP（后端；**保持根 context-path**，刻意不设 `SERVER_SERVLET_CONTEXT_PATH`） |
| `/api/seller/**` | 卖家端 REST 接口（`Authorization: Bearer`） |
| `/images/**`、`/buyer/**` | 后端静态资源（商品图片、买家端 CSS 与占位图） |
| `/seller/**` | 卖家端 SPA（nginx 静态托管；构建时 `--base=/seller/`） |

- 买家端与卖家 SPA 都想要根路径 `/`，本部署按**方案乙**划分：**买家在 `/`，卖家在 `/seller/`**。卖家入口不是根路径，演示与联调时注意。
- 后端**不改 context-path**，因此容器内路径与仓库文档、接口文档逐字一致。
- nginx 只有两条规则：`/seller/` 静态 + `try_files` 回退；其余全部 `proxy_pass` 到 `backend:8080`。

### 4.3 数据与持久化

- **MySQL 容器只建库**（`utf8mb4 / utf8mb4_unicode_ci`）；建表与种子数据由后端启动时的 **Flyway** 自动完成，不手工建表。
- 应用账号 `simpleshop` 为**最小权限**（仅 `simple_shop` 库）；root 口令只用于初始化与运维/备份，**不注入应用**。
- 三个卷都必须持久化：`mysql-data`（丢失即数据库全失）、`backend-images`（归档不搬迁图片文件，丢失即永久性数据丢失）、`backend-logs`（审计日志保留 3 个月）。
- 真实口令只写在 `deploy/.env`（或 `deploy-cloud/.env`，均已 gitignore），仓库只提交 `.env.example`。

### 4.4 镜像与镜像源

- **基础镜像源是「测试机环境约定」，不是项目硬性依赖**：Maven 走腾讯云、npm 走 npmmirror、Docker 基础镜像走轩辕免费版（仅 docker.io、仅 pull），记录日期 2026-10-13；失效时按手册 §3.2 回退官方源。
- 本地构建版把镜像前缀抽成 `IMAGE_PREFIX`（`${VAR-default}` 语义，留空即回退官方源）。
- **云端镜像版删去了该前缀**：backend / frontend 不再在目标机构建，构建参数 `REGISTRY` 无作用；且这两个镜像位于 Docker Hub **用户仓库**，而轩辕免费版只代理 `docker.io/library/` 官方镜像，前缀对它们无效。但其 `mysql:8.0` 仍需目标机能访问 Docker Hub。

### 4.5 已知限制（部署视角）

| 项 | 说明 |
| --- | --- |
| 进程内会话 | 后端**单副本**，不能多副本/负载均衡，容器重启会丢会话（已接受） |
| MySQL 单点 | 无主从、无高可用；月度可用率目标需额外运维措施 |
| 仅 HTTP | HTTPS / 域名不在本期范围 |
| 只构建 `linux/amd64` | 多架构不在本期范围 |
| 构建依赖外网 | 「本地构建」选项需要能访问 Maven / npm / Docker 镜像源 |
| 云端镜像为可变标签 | `:backend` / `:frontend` 会被重新推送覆盖；生产建议改为版本化标签以便回滚 |
