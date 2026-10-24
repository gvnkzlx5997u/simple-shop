-- =============================================================================
-- simple-shop 阶段二 · 初始化数据
--
-- 只写入卖家单账号。买家通过注册产生；商品、类别、意向不预置。
-- 队列序号随商品创建，不再有全局初始行。
--
-- 密码只写入 BCrypt 哈希，不写入明文。初始口令见 README，首次登录后请修改。
-- 时间列使用 UTC_TIMESTAMP()，与库内 UTC 口径一致。
-- =============================================================================

INSERT INTO `simpleshop_seller_account` (`id`, `account`, `password`, `create_at`, `update_at`)
VALUES (
  'SA0000000-0000-4000-8000-000000000001',
  'seller',
  '$2a$10$OHzqM5qU3fCLG/ixnx/EReXS6NHnT37EpZyavH3Zkff2T4YjVZ3R6',
  UTC_TIMESTAMP(),
  UTC_TIMESTAMP()
)
ON DUPLICATE KEY UPDATE `id` = `id`;
