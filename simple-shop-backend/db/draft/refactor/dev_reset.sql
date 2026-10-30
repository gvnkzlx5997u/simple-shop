-- =============================================================================
-- 开发环境库重置（草案）
--
-- ！！ 会删除 simple_shop 与 simple_shop_test 的全部数据 ！！
-- 仅用于 V1 基线重建：旧库的 Flyway 校验和与新 V1 不一致，必须重建。
--
-- 执行后启动后端，Flyway 会自动执行新的 V1/V2。
-- 若测试库名不是 simple_shop_test（TEST_DB_URL 改过），请同步修改。
-- =============================================================================

DROP DATABASE IF EXISTS `simple_shop`;
CREATE DATABASE `simple_shop`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

DROP DATABASE IF EXISTS `simple_shop_test`;
CREATE DATABASE `simple_shop_test`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
