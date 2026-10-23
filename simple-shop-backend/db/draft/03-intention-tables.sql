-- =============================================================================
-- simple-shop 阶段二 · 意向、队列序号与历史意向建表脚本（半成品草案）
--
-- 状态：草案。未接入 Flyway，不创建数据库，也不会在启动时执行。
-- 范围：按商品队列序号、当前意向、历史意向三张表。
-- 依赖：
--   · simpleshop_goods（01-goods-tables.sql）
--   · simpleshop_buyer_account（02-account-tables.sql）
--   · simpleshop_goods_history（01-goods-tables.sql）
-- 合并顺序：00 类别 → 01 商品 → 02 账号 → 03 意向。
--
-- 约定：
--   · 表名统一 simpleshop_ 前缀；主键为「前缀 + UUID」，varchar(50)。
--   · 时间列一律存 UTC。
--   · MySQL >= 8.0.16（CHECK 约束真正生效）。
--
-- 本脚本落实的口径：
--   1. 当前意向只引用买家账号 buyer_id，不再保存用户名/电话等交易联系字段。
--   2. 意向进入历史时，把买家用户名、电话、商品名作为字符串快照写入历史表。
--   3. 历史意向**不保存 queue_order**；队列序号随商品归档/删除而丢弃。
--   4. 队列序号按商品独立维护，每个商品一行，初值 0。
--   5. 意向状态四值：QUEUED / TRADING / SUCCEEDED / FAILED。
--   6. 失败原因两值：SOLD_OUT / SELLER_VOIDED；仅 FAILED 时非空。
--   7. 终态时间 finish_at：SUCCEEDED / FAILED 时非空，其余状态为空。
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1 / 3　商品队列序号
--   每个商品一行；发布商品时插入初值 0，提交意向时递增。
--   商品归档后随商品删除，因此历史意向不再保留 queue_order。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_goods_queue_sequence` (
  `fk_goods_id`    varchar(50) NOT NULL COMMENT '所属商品 ID，一商品一行',
  `current_value`  bigint      NOT NULL DEFAULT 0 COMMENT '该商品已分配的最大 queue_order',
  PRIMARY KEY (`fk_goods_id`),
  CONSTRAINT `ck_goods_queue_sequence_non_negative` CHECK (`current_value` >= 0),
  CONSTRAINT `fkey_goods_queue_sequence_goods` FOREIGN KEY (`fk_goods_id`)
      REFERENCES `simpleshop_goods` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='商品队列序号表：按商品独立递增，随商品删除';


-- -----------------------------------------------------------------------------
-- 2 / 3　当前意向
--   只引用 buyer_id，不保存买家用户名/电话；这些联系信息在归档时做字符串快照。
--   queue_order 在同一商品内唯一，从 1 开始；重新排队时刷新到该商品队尾。
--   fail_type 只在 FAILED 时非空；finish_at 只在终态非空。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_intention` (
  `id`           varchar(50) NOT NULL COMMENT '意向 ID：I + UUID',
  `fk_goods_id`   varchar(50) NOT NULL COMMENT '所属商品 ID',
  `fk_buyer_id`   varchar(50) NOT NULL COMMENT '买家账号 ID',
  `queue_order`   bigint      NOT NULL COMMENT '该商品内的队列序号，从 1 开始',
  `create_at`     datetime    NOT NULL COMMENT '提交时间（UTC），重新排队不变',
  `status`        varchar(20) NOT NULL COMMENT 'QUEUED / TRADING / SUCCEEDED / FAILED',
  `fail_type`     varchar(20) DEFAULT NULL COMMENT 'SOLD_OUT / SELLER_VOIDED；仅 FAILED 时非空',
  `finish_at`     datetime    DEFAULT NULL COMMENT '进入终态的时间（UTC）；非终态为空',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_intention_goods_queue_order` (`fk_goods_id`, `queue_order`),
  KEY `idx_intention_goods_status_order` (`fk_goods_id`, `status`, `queue_order`),
  KEY `idx_intention_buyer_status_create` (`fk_buyer_id`, `status`, `create_at`),
  CONSTRAINT `ck_intention_queue_order` CHECK (`queue_order` > 0),
  CONSTRAINT `ck_intention_status` CHECK (`status` IN ('QUEUED', 'TRADING', 'SUCCEEDED', 'FAILED')),
  CONSTRAINT `ck_intention_fail_type` CHECK (
      `fail_type` IS NULL OR `fail_type` IN ('SOLD_OUT', 'SELLER_VOIDED')),
  CONSTRAINT `ck_intention_terminal_state` CHECK (
      (`status` IN ('QUEUED', 'TRADING') AND `fail_type` IS NULL AND `finish_at` IS NULL)
   OR (`status` = 'SUCCEEDED' AND `fail_type` IS NULL AND `finish_at` IS NOT NULL)
   OR (`status` = 'FAILED' AND `fail_type` IS NOT NULL AND `finish_at` IS NOT NULL)
  ),
  CONSTRAINT `ck_intention_time_order` CHECK (
      `finish_at` IS NULL OR `finish_at` >= `create_at`),
  CONSTRAINT `fkey_intention_goods` FOREIGN KEY (`fk_goods_id`)
      REFERENCES `simpleshop_goods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fkey_intention_buyer` FOREIGN KEY (`fk_buyer_id`)
      REFERENCES `simpleshop_buyer_account` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='当前意向表：引用买家账号，按商品独立排队';


-- -----------------------------------------------------------------------------
-- 3 / 3　历史意向
--   随商品归档写入；沿用原意向 ID，引用历史商品。
--   买家用户名、电话、商品名均为字符串快照，不依赖当前账号/商品。
--   不保存 queue_order；历史只需要结果与时间。
--   result 只允许 SUCCEEDED / FAILED；fail_type 规则与当前表一致。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_intention_history` (
  `id`                   varchar(50)  NOT NULL COMMENT '沿用原意向 ID：I + UUID',
  `fk_goods_history_id`  varchar(50)  NOT NULL COMMENT '所属历史商品 ID（沿用原商品 ID）',
  `goods_name`           varchar(50)  NOT NULL COMMENT '归档时的商品名快照',
  `buyer_id`             varchar(50)  NOT NULL COMMENT '买家账号 ID',
  `buyer_username`       varchar(50)  NOT NULL COMMENT '归档时的买家用户名快照',
  `buyer_phone`          varchar(20)  NOT NULL COMMENT '归档时的买家电话快照',
  `create_at`            datetime     NOT NULL COMMENT '提交时间（UTC），继承原值',
  `result`               varchar(20)  NOT NULL COMMENT 'SUCCEEDED / FAILED',
  `fail_type`            varchar(20)  DEFAULT NULL COMMENT 'SOLD_OUT / SELLER_VOIDED；仅 FAILED 时非空',
  `finish_at`            datetime     NOT NULL COMMENT '进入终态的时间（UTC）',
  `archived_at`          datetime     NOT NULL COMMENT '归档时间（UTC）',
  PRIMARY KEY (`id`),
  KEY `idx_intention_history_buyer` (`buyer_id`, `create_at`),
  KEY `idx_intention_history_goods` (`fk_goods_history_id`, `create_at`),
  CONSTRAINT `ck_intention_history_result` CHECK (`result` IN ('SUCCEEDED', 'FAILED')),
  CONSTRAINT `ck_intention_history_fail_type` CHECK (
      `fail_type` IS NULL OR `fail_type` IN ('SOLD_OUT', 'SELLER_VOIDED')),
  CONSTRAINT `ck_intention_history_result_fail_type` CHECK (
      (`result` = 'SUCCEEDED' AND `fail_type` IS NULL)
   OR (`result` = 'FAILED' AND `fail_type` IS NOT NULL)
  ),
  CONSTRAINT `ck_intention_history_time_order` CHECK (
      `finish_at` >= `create_at` AND `archived_at` >= `finish_at`),
  CONSTRAINT `fkey_intention_history_goods` FOREIGN KEY (`fk_goods_history_id`)
      REFERENCES `simpleshop_goods_history` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='历史意向表：买家与商品信息为字符串快照，不保存队列序号';
