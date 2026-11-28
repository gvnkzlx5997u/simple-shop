# 部署（Docker Compose）

两套编排，同一套拓扑，区别只在镜像从哪来：

| 目录 | 用途 | 镜像来源 |
| --- | --- | --- |
| `deploy/` | 在目标机现场构建 | 用仓库内 Dockerfile 构建 backend 与 frontend |
| `deploy-cloud/` | 拉取已推送的版本化镜像 | `.env` 里的 `BACKEND_IMAGE` / `FRONTEND_IMAGE` |

## 拓扑

```
浏览器 :80
   │
   ▼
frontend(nginx) ── /            → 买家 SPA
   │            ├─ /seller/     → 卖家 SPA
   │            ├─ /api         → backend:8080
   │            └─ /media       → backend:8080
   ▼
backend(Spring Boot, 不映射宿主端口)
   ▼
mysql:8.0 (不映射宿主端口)
```

- **只有 frontend 暴露宿主 80 端口**；backend 与 mysql 只在内网 `shop-net` 里。
- MySQL 容器只负责建库与账号，建表与种子数据由后端启动时的 Flyway 完成。
- nginx 的 `client_max_body_size` 必须与后端 multipart 上限（25MB）对齐，小于后端时上传会先收到 413，而不是业务错误码。

## 卷（三个都不能省）

| 卷 | 挂载点 | 丢失后果 |
| --- | --- | --- |
| `mysql-data` | `/var/lib/mysql` | 丢全部业务数据 |
| `backend-images` | `/app/data/images` | 丢商品图/描述媒体/售后凭证，**且不可恢复**（下架不搬迁文件） |
| `backend-logs` | `/var/log/simple-shop` | 丢审计日志（保留 90 天） |

## 用法

```bash
# 本地构建
cd deploy
cp .env.example .env      # 填 MYSQL_ROOT_PASSWORD 与 DB_PASSWORD
docker compose up -d --build

# 云端镜像
cd deploy-cloud
cp .env.example .env      # 填口令，必要时改 BACKEND_IMAGE / FRONTEND_IMAGE
docker compose pull
docker compose up -d
```

前置：Docker 23+（构建用 BuildKit 的 `RUN --mount=type=cache`）、Compose v2，构建机能访问 Docker Hub 与 Maven 源。

- 口令与应用数据库账号全部来自 `.env`，**`.env` 已被 `.gitignore` 排除，不要提交**；生产环境必须改掉种子卖家 `seller` 的初始口令。
- 应用连库用最小权限账号 `simpleshop`，root 口令只给初始化与备份用。

## 升级与回滚

- **数据库按阶段重建**：旧数据卷不能跨阶段复用。先备份，再 `docker compose down -v`（或换项目名/卷名）后首次启动。**`down -v` 会删数据，执行前必须与人类确认。**
- 阶段四要求按 V4 重建：V4 给订单加的是 `NOT NULL` 无默认值的收货快照列，**订单表必须为空**才能执行；旧库会启动失败，不要关 Flyway 校验硬绕。
- 云端升级/回滚要**同时**改 `BACKEND_IMAGE` 与 `FRONTEND_IMAGE`，只换一个会出现前后端契约错版。

## 构建约定

- 基础镜像统一用 Docker Hub 官方源；不要再引入 `IMAGE_PREFIX` / `REGISTRY` 前缀机制。构建网络受限时配置 Docker 代理，而不是改镜像名前缀。
- 后端运行阶段的 apt 层是「基础镜像已带 curl 就跳过，缺失才安装」，不要改回无条件 `apt-get update`。
- 前端镜像由 `deploy/frontend/Dockerfile` 用仓库根上下文构建，同时产出买家 `/` 与卖家 `/seller/` 两个产物；两个前端工程各自的 `Dockerfile` 只用于独立预览。

## 单副本限制

会话、限流、订单编号都在后端进程内，**backend 不能多副本或走负载均衡**；容器重启会掉全部会话，这是已接受的设计，不要按「无状态服务」去改。
