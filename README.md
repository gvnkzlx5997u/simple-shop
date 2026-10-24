# simple-shop 在线购物系统

> 阶段二重构进行中。业务口径以 [阶段二文档](./simple-shop-doc/阶段二/README.md) 为准，实施顺序见 [后端开发计划](./simple-shop-doc/阶段二/后端开发计划.md)。阶段一文档仅作历史参考。

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
├─ simple-shop-backend/      后端（Spring Boot，REST + 媒体文件）
├─ simple-shop-frontend/     卖家端 SPA（阶段一形态，待改名为 seller-frontend 并重写业务页面）
├─ simple-shop-test-e2e/     Playwright 端到端测试（阶段一用例，待重写）
├─ deploy/                   本地构建部署（Docker Compose）
├─ deploy-cloud/             云端镜像部署
└─ simple-shop-doc/          阶段一与阶段二文档
```

买家端工程 `buyer-frontend` 尚未建立。

## 当前进度

- **S0 清理与骨架**：已完成阶段一业务代码、JSP、口令码、交易流水的清理，公共能力迁入 `common` 包。
- **S1 数据层**：已建立阶段二 V1 基线（11 张表）、V2 卖家种子、11 个实体、11 个仓储与 6 个枚举，数据层 22 个测试通过。
- **S2 公共能力与账号**：买家注册/登录/退出、卖家登录/退出/改密、双角色令牌隔离、媒体上传、springdoc 已完成。
- **S3 类别与商品**：两级类别 CRUD 与删除前置、商品发布/列表/详情/搜索、主图/图集、文本/图片/视频描述块、库存调整、手动上下架、库存清零下架与改挂类别已完成；全部 141 个测试通过，开发库与测试库均通过 `ddl-auto=validate`。
- 下一步 S4：买家意向、按商品队列与卖家交易裁决。
- 卖家前端、买家前端、E2E 与部署配置仍为阶段一形态，随对应阶段调整。
- 阶段一数据库不迁移；旧库与旧测试库已删除，备份在 `simple-shop-backend/db/legacy/`，新的 `simple_shop` / `simple_shop_test` 由 Flyway 新建。

## 文档

| 内容 | 位置 |
| --- | --- |
| 阶段二总览 | [阶段二 README](./simple-shop-doc/阶段二/README.md) |
| 升级需求 | [升级需求A](./simple-shop-doc/阶段二/升级需求A.md) |
| 状态模型 | [状态模型设计](./simple-shop-doc/阶段二/状态模型设计.md) |
| 后端实施 | [后端开发计划](./simple-shop-doc/阶段二/后端开发计划.md) |
| 后端构建与运行 | [simple-shop-backend/README.md](./simple-shop-backend/README.md) |
| 阶段一文档（历史） | [阶段一 README](./simple-shop-doc/阶段一/README.md) |

## 构建与验证

```bash
cd simple-shop-backend
mvn clean verify
pwsh -File scripts/check-comment-refs.ps1
```

前端与 E2E 命令将在各自工程调整后更新。
