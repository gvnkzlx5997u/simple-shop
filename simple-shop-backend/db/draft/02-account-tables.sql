-- =============================================================================
-- simple-shop 阶段二 · 账号建表脚本（半成品草案）
--
-- 状态：草案。未接入 Flyway，不创建数据库，也不会在启动时执行。
-- 范围：卖家账号、买家账号两张表。
-- 依赖：无前置外键依赖；后续意向表会引用 simpleshop_buyer_account(id)。
-- 合并顺序：00 类别 → 01 商品 → 02 账号 → 03 意向，最终合并为
--           src/main/resources/db/migration/V1__baseline_schema.sql。
--
-- 约定：
--   · 表名统一 simpleshop_ 前缀；主键为「前缀 + UUID」，varchar(50)。
--   · 时间列一律存 UTC。
--   · 密码只存 BCrypt 哈希，不存明文；列宽 100 足够容纳 60 字符的 BCrypt。
--   · MySQL >= 8.0.16（CHECK 约束真正生效）。
--
-- 本脚本落实的口径：
--   1. 卖家账号单账号；account 全局唯一；应用层保证只有一行。
--   2. 买家账号通过 username 唯一标识；phone 允许重复。
--   3. phone 的 7~20 位数字与 "-" 格式由应用层校验，数据库只限制非空与列宽。
--   4. 买家默认交易地点可空，最大 50 字符。
--   5. 买家没有改资料、改密码入口，因此本表不设 update_at；卖家改密更新时间。
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1 / 2　卖家账号
--   单账号。账号数据永久保留；改密时更新 password 与 update_at。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_seller_account` (
  `id`         varchar(50)  NOT NULL COMMENT '卖家账号 ID：SA + UUID',
  `account`    varchar(50)  NOT NULL COMMENT '登录账号，全局唯一',
  `password`   varchar(100) NOT NULL COMMENT '密码 BCrypt 哈希，禁止明文',
  `create_at`  datetime     NOT NULL COMMENT '创建时间（UTC）',
  `update_at`  datetime     NOT NULL COMMENT '最后修改时间（UTC），改密时更新',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seller_account` (`account`),
  CONSTRAINT `ck_seller_account_not_blank` CHECK (`account` <> ''),
  CONSTRAINT `ck_seller_password_not_blank` CHECK (`password` <> '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='卖家账号表：单账号，应用层保证只有一行';


-- -----------------------------------------------------------------------------
-- 2 / 2　买家账号
--   username 全局唯一；phone 不唯一，同一手机号可以有多个账号。
--   default_location 可空；phone 格式（7~20 位数字与 -）由应用层校验，不在库中限制。
--   create_at 建索引，供卖家客户列表按注册时间排序分页。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_buyer_account` (
  `id`                varchar(50)  NOT NULL COMMENT '买家账号 ID：BA + UUID',
  `username`          varchar(50)  NOT NULL COMMENT '登录用户名，全局唯一',
  `password`          varchar(100) NOT NULL COMMENT '密码 BCrypt 哈希，禁止明文',
  `phone`             varchar(20)  NOT NULL COMMENT '联系电话，7~20 位数字与 -，格式由应用层校验',
  `default_location`  varchar(50)  DEFAULT NULL COMMENT '默认交易地点，<=50 字符，可空',
  `create_at`         datetime     NOT NULL COMMENT '注册时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_buyer_username` (`username`),
  KEY `idx_buyer_create_at` (`create_at`),
  CONSTRAINT `ck_buyer_username_not_blank` CHECK (`username` <> ''),
  CONSTRAINT `ck_buyer_password_not_blank` CHECK (`password` <> ''),
  CONSTRAINT `ck_buyer_phone_not_blank` CHECK (`phone` <> ''),
  CONSTRAINT `ck_buyer_default_location_not_empty` CHECK (
      `default_location` IS NULL OR `default_location` <> '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='买家账号表：用户名唯一，手机号不唯一';
