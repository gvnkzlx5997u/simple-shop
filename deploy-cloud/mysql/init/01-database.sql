-- =============================================================================
-- simple-shop 数据库初始化（幂等）
-- 依据：安装与部署手册 v1.0 §5.1、§7
--   ⚠️ 只建库/授权/定排序规则，【不建表】：12 张业务表与种子数据由后端启动时的 Flyway 负责。
--   ⚠️ 本脚本只在 MySQL 数据目录为空（首次初始化）时执行一次。
--   MYSQL_DATABASE=simple_shop 与 MYSQL_USER 由 entrypoint 建库并授权；
--   这里只补 entrypoint 不做的一件事：库排序规则（幂等）。
--   ⚠️ 刻意【不写 GRANT】：账号名由 compose 的 DB_USERNAME 决定，写死会与之耦合。
-- =============================================================================

ALTER DATABASE `simple_shop` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
