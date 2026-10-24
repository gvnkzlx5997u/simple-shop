# 阶段一迁移脚本归档

本目录保存阶段一的 Flyway 基线脚本，仅作历史参考，**不在运行期加载**。

- 阶段二使用全新的数据库与新的 `V1` 基线，阶段一数据不迁移。
- 旧的 `V1__baseline_schema.sql`、`V2__seed_initial_data.sql` 已从
  `src/main/resources/db/migration` 移出，避免被 Flyway 自动执行。
- 阶段二的新基线脚本将在数据层阶段加入 `src/main/resources/db/migration`。
