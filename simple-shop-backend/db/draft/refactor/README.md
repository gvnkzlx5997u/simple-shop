# 后端架构改造 · 基线重建脚本

本目录保留基线重建的**校验与重置脚本**。重建后的 V1 已提升为正式迁移：

`simple-shop-backend/src/main/resources/db/migration/V1__baseline_schema.sql`

不再保留 V1 副本，避免两份基线漂移。

## 文件

| 文件 | 内容 | 用途 |
| --- | --- | --- |
| `verify.sql` | 重建后校验（表数、列、行数、不变量） | 每次重建后执行 |
| `dev_reset.sql` | 删除并重建开发库/测试库 | **仅开发环境** |

## 已执行记录（2026-10-30，S2）

1. 代码去掉 `Goods.stock`，新增 `inventory` 模块与 `StockPort`；
2. 执行 `dev_reset.sql` 重建 `simple_shop` 与 `simple_shop_test`；
3. 重建后的 V1 覆盖活动基线；
4. `mvn clean test`：197 个用例通过，Flyway + `ddl-auto=validate` 通过；
5. 开发库保持空库，首次启动由 Flyway 执行 V1/V2。

## 注意

- V1 修改后，旧库的 Flyway 校验和必然失配；`flyway repair` 只改校验和，表结构仍旧，**不能替代重建**。
- 生产环境不适用本方案；若将来已有生产数据，必须改回增量迁移。
- `FROZEN` 状态已确认保留，没有删除状态的脚本。
- 测试库名若不是 `simple_shop_test`（`TEST_DB_URL` 改过），请同步修改 `dev_reset.sql`。
