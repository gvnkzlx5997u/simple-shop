# simple-shop 在线购物系统

> 阶段二开发已完成，部署已收口到本地构建与云端镜像推送。业务口径以 [阶段二文档](./simple-shop-doc/阶段二/阶段二目录.md) 为准，实施顺序见 [后端开发计划](./simple-shop-doc/阶段二/后端开发计划.md)。阶段一文档仅作历史参考。

## 项目定位

simple-shop 是一个单卖家的在线购物系统：卖家发布多件商品，买家注册登录后对在售商品提交购买意向，卖家按队列顺序线下交易并登记结果。系统不承载支付与物流。

阶段二相对阶段一的主要变化：

| 维度 | 阶段二 |
| --- | --- |
| 商品 | 多商品、库存、两级类别、主图与图集、文本/图片/视频混排描述 |
| 买家 | 注册账号，提交意向绑定账号；取消口令码 |
| 交易 | 按商品独立排队；取消交易流水表 |
| 下架 | 手动下架与库存清零下架可复售；只有成交售罄才归档 |
| 前端 | 前后端分离；卖家端与买家端各自独立工程 |
| 后端 | 按功能分包，统一 REST 接口，不再服务端渲染 |

## 仓库结构

```text
simple-shop/
├─ simple-shop-backend/            后端（Spring Boot，REST + 媒体文件）
├─ simple-shop-buyer-frontend/     买家端 SPA
├─ simple-shop-seller-frontend/    卖家端 SPA
├─ simple-shop-test-e2e/           Playwright 端到端测试（阶段二 7 spec / 57 条）
├─ deploy/                         本地构建部署（Docker Compose，阶段二口径）
├─ deploy-cloud/                   云端镜像部署（backend-1.0 / frontend-1.0 已推送）
└─ simple-shop-doc/                阶段一与阶段二文档
```

阶段二部署为三容器：nginx 同时托管买家 `/` 与卖家 `/seller/`，反代 `/api`、`/media` 到 backend。

## 当前进度

**后端（S0~S5 全部完成）**

- S0 清理与骨架 / S1 数据层（V1 基线 12 张表）/ S2 公共能力与账号 / S3 类别与商品 / S4 买家意向、按商品队列与交易裁决 / S5 历史与客户读模型：全部完成，`mvn clean verify` 212 个用例全绿。
- springdoc 已收口（v1.0.0），契约快照与前端任务见 [openapi.json](./simple-shop-doc/阶段二/openapi.json) 与 [前端交接文档](./simple-shop-doc/阶段二/前端交接文档.md)。
- 阶段二架构改造 S1~S6 已完成（商品/库存解耦、Service 接口化、交易所有权、契约类型化与字段级校验、副作用 afterCommit、Testcontainers 测试收口）。
- 阶段二期间的修复：库内时间双重时区折算（实体时间字段 `@JdbcTypeCode` 字面量绑定 + JVM 钉 UTC 兜底，订正记录见阶段一数据层设计说明书 §4.8 v1.3）；买家注册电话校验（数字至少 7 位）。
- 阶段一数据库不迁移；旧库备份在 `simple-shop-backend/db/legacy/`，`simple_shop` / `simple_shop_test` 由 Flyway 新建。

**卖家前端（阶段二改造完成，工程已更名 `simple-shop-seller-frontend`）**

- 契约层按 openapi 契约重写：`types.ts`（DTO 与错误码全集）+ 九个 API 模块；组件按 common / goods / intention 分层。
- 页面全部就位：商品列表（宫格 + 状态/类别/关键字筛选）、发布（媒体发布时统一上传 + 描述块行流编辑器）、商品详情（图集切换、改库存/改挂/上下架）、意向名单与交易裁决（进入交易/成功/失败裁决）、类别管理、客户管理（详情内嵌购买历史）、历史记录（归档快照 + 意向名单）；登录与改密沿用阶段一骨架。
- `npm run type-check` / `npm run build` / `npm test` 全绿；发布→意向→裁决→归档→历史/客户主流程已手工验证通过。

**买家前端（阶段二新建）**

- 工程 `simple-shop-buyer-frontend`：注册/登录、商品列表与详情、提交购买意向、我的历史；与卖家端共用同一套 REST 契约。
- 生产构建产物由 `deploy/frontend` 组合镜像托管在 `/`，开发端口 5174。

**部署（阶段二口径，2026-11-03 收口）**

- `deploy/docker-compose.yml`：三容器（mysql / backend / frontend），frontend 用 `deploy/frontend` 组合镜像同时托管买家 `/` 与卖家 `/seller/`，并反代 `/api`、`/media`；基础镜像统一 Docker Hub 官方源，后端 apt 层在基础镜像自带 curl 时跳过。
- `deploy-cloud/docker-compose.yml`：同拓扑的云端镜像版，使用 `lrhg173c/simple-shop:backend-1.0` 与 `:frontend-1.0`；镜像已于 2026-11-03 推送，目标机按 `pull + up` 验收。
- 本地构建：`cd deploy && docker compose up -d --build`；云端：`cd deploy-cloud && docker compose pull && docker compose up -d`。
- 后端镜像 `simple-shop-backend/Dockerfile` 已按阶段二收口（只提供 REST 与 `/media`，无 JSP）。
- ⚠️ 从阶段一升级：V1 基线已重建，不能复用旧 `simple-shop_mysql-data`；先备份并 `docker compose down -v`（或换项目名/卷名）再启动。

**E2E 验收（阶段二重写完成）**

- `simple-shop-test-e2e` 已重写为 7 spec / 57 条：登录鉴权、买家浏览与意向、卖家商品管理、交易裁决、接口契约、安全、客户与历史；全量跑约 23 秒，已在本地部署环境执行，详见该目录 README。
- 阶段一的 36 条旧用例已移入 `simple-shop-test-e2e/archive/`，**不要放回 `tests/`**，否则会被 `playwright test` 误跑。

## 文档

| 内容 | 位置 |
| --- | --- |
| 阶段二总览 | [阶段二目录](./simple-shop-doc/阶段二/阶段二目录.md) |
| 升级需求 | [升级需求A](./simple-shop-doc/阶段二/升级需求A.md) |
| 状态模型 | [状态模型设计](./simple-shop-doc/阶段二/状态模型设计.md) |
| 后端实施 | [后端开发计划](./simple-shop-doc/阶段二/后端开发计划.md) |
| 前端交接与 API 契约 | [前端交接文档](./simple-shop-doc/阶段二/前端交接文档.md)、[openapi.json](./simple-shop-doc/阶段二/openapi.json) |
| 后端构建与运行 | [simple-shop-backend/README.md](./simple-shop-backend/README.md) |
| 卖家前端说明 | [simple-shop-seller-frontend/README.md](./simple-shop-seller-frontend/README.md) |
| 阶段二部署手册（v2.0 定稿） | [安装与部署手册](./simple-shop-doc/阶段二/安装与部署手册.md) |
| 剩余事项交接 | [阶段二剩余事项交接](./simple-shop-doc/阶段二/阶段二剩余事项交接.md) |
| 阶段一文档（历史） | [阶段一 README](./simple-shop-doc/阶段一/阶段一目录.md) |

## 构建与验证

```bash
# 后端（含 212 个测试）
cd simple-shop-backend
mvn clean verify
pwsh -File scripts/check-comment-refs.ps1

# 卖家前端
cd simple-shop-seller-frontend
npm ci
npm run build    # 类型检查 + 生产构建
npm test         # 连通性测试（后端不可达时自动跳过）

# 买家前端
cd simple-shop-buyer-frontend
npm ci
npm run build
```
