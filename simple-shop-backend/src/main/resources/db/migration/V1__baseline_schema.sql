-- =============================================================================
-- simple-shop 阶段二 · 基线建表脚本
--
-- 由 db/draft/ 下的类别、商品、账号、意向草案合并而成。
-- 重建版本（架构改造 S2/S3）：库存拆到 simpleshop_goods_stock；
-- 交易中唯一性由 simpleshop_intention.trading_flag 唯一索引保证。
-- Flyway 只执行一次；已发布后不得修改，后续变更追加新的版本脚本。
--
-- 建表顺序：
--   category_l1 → category_l2 → goods → goods_stock → goods_image → goods_content_block
--   → goods_history → seller_account → buyer_account
--   → goods_queue_sequence → intention → intention_history
--
-- 约定：MySQL >= 8.0.16；utf8mb4 / utf8mb4_unicode_ci；时间列一律 UTC。
-- =============================================================================

-- =============================================================================
-- 00-category-tables.sql
-- =============================================================================

CREATE TABLE `simpleshop_category_l1` (
  `id`         varchar(50) NOT NULL COMMENT '一级类别 ID：C1 + UUID',
  `name`       varchar(50) NOT NULL COMMENT '类别名，<=50 字符，全局唯一',
  `create_at`  datetime    NOT NULL COMMENT '创建时间（UTC）',
  `update_at`  datetime    NOT NULL COMMENT '最后修改时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_l1_name` (`name`),
  CONSTRAINT `ck_category_l1_name` CHECK (`name` <> '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='一级类别表：只做分组，商品不能直接挂在一级类别';


-- -----------------------------------------------------------------------------
-- 2 / 2　二级类别
--   商品通过 goods.fk_category_id 引用本表。
--   (fk_l1_id, name) 唯一：同一父级下不重名。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_category_l2` (
  `id`         varchar(50) NOT NULL COMMENT '二级类别 ID：C2 + UUID',
  `fk_l1_id`   varchar(50) NOT NULL COMMENT '所属一级类别 ID',
  `name`       varchar(50) NOT NULL COMMENT '类别名，<=50 字符，同一父级下唯一',
  `create_at`  datetime    NOT NULL COMMENT '创建时间（UTC）',
  `update_at`  datetime    NOT NULL COMMENT '最后修改时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_l2_parent_name` (`fk_l1_id`, `name`),
  CONSTRAINT `ck_category_l2_name` CHECK (`name` <> ''),
  CONSTRAINT `fkey_category_l2_l1` FOREIGN KEY (`fk_l1_id`)
      REFERENCES `simpleshop_category_l1` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='二级类别表：商品必须挂到二级类别';


-- =============================================================================
-- 01-goods-tables.sql
-- =============================================================================

CREATE TABLE `simpleshop_goods` (
  `id`             varchar(50)  NOT NULL COMMENT '商品 ID：G + UUID',
  `name`           varchar(50)  NOT NULL COMMENT '名称，<=50 字符',
  `price`          decimal(8,2) NOT NULL COMMENT '价格，0 < price <= 999999.99',
  `fk_category_id` varchar(50)  NOT NULL COMMENT '所属二级类别 ID，引用 simpleshop_category_l2',
  `status`         varchar(20)  NOT NULL COMMENT 'ON_SALE / FROZEN / OFF_MANUAL / OFF_ZERO',
  `create_at`       datetime     NOT NULL COMMENT '发布时间（UTC）',
  `update_at`       datetime     NOT NULL COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  KEY `idx_goods_status_category_create` (`status`, `fk_category_id`, `create_at`),
  KEY `idx_goods_category` (`fk_category_id`),
  KEY `idx_goods_create_at` (`create_at`),
  CONSTRAINT `ck_goods_price` CHECK (`price` > 0),
  CONSTRAINT `ck_goods_status` CHECK (`status` IN ('ON_SALE', 'FROZEN', 'OFF_MANUAL', 'OFF_ZERO')),
  CONSTRAINT `fkey_goods_category` FOREIGN KEY (`fk_category_id`)
      REFERENCES `simpleshop_category_l2` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='商品表：只存展示属性与上下架语义；库存见 simpleshop_goods_stock';

-- -----------------------------------------------------------------------------
-- 2 / 5　商品库存表
--   与商品一对一，主键即商品 ID；独立行锁，库存变更不再更新商品行。
--   库存归零由应用触发下架；商品归档删除时随外键级联删除。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_goods_stock` (
  `fk_goods_id`  varchar(50) NOT NULL COMMENT '商品 ID，与商品一对一',
  `available`    int         NOT NULL COMMENT '可售库存，>= 0',
  `update_at`    datetime    NOT NULL COMMENT '最后变更时间（UTC）',
  PRIMARY KEY (`fk_goods_id`),
  CONSTRAINT `ck_goods_stock_available` CHECK (`available` >= 0),
  CONSTRAINT `fkey_goods_stock_goods` FOREIGN KEY (`fk_goods_id`)
      REFERENCES `simpleshop_goods` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='商品库存表：与商品一对一，独立行锁；库存归零由应用触发下架';


-- -----------------------------------------------------------------------------
-- 3 / 5　商品图片表（主图 + 图集）
--   只允许图片；商品图与描述媒体是不同限制，故不共用描述内容块表。
--   is_main 标记主图；main_flag 生成列 + 唯一索引保证每件商品至多一张主图。
--   MySQL 唯一索引允许多个 NULL，因此非主图行不会互相冲突。
--   图集最多 8 张、有图集必须有主图，由应用层校验。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_goods_image` (
  `id`           varchar(50)  NOT NULL COMMENT '图片 ID：GI + UUID',
  `fk_goods_id`   varchar(50)  NOT NULL COMMENT '所属商品 ID',
  `url`          varchar(512) NOT NULL COMMENT '相对 URL，服务端生成',
  `is_main`      tinyint      NOT NULL DEFAULT 0 COMMENT '是否主图：1 是，0 否',
  `sort_no`      int          NOT NULL COMMENT '排序号，从 1 开始，同一商品内唯一',
  `create_at`     datetime     NOT NULL COMMENT '创建时间（UTC）',
  `main_flag`    tinyint GENERATED ALWAYS AS (CASE WHEN `is_main` = 1 THEN 1 ELSE NULL END) STORED
      COMMENT '主图唯一性辅助列：主图为 1，非主图为 NULL',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_goods_image_sort` (`fk_goods_id`, `sort_no`),
  UNIQUE KEY `uk_goods_image_main` (`fk_goods_id`, `main_flag`),
  CONSTRAINT `ck_goods_image_is_main` CHECK (`is_main` IN (0, 1)),
  CONSTRAINT `ck_goods_image_sort_no` CHECK (`sort_no` > 0),
  CONSTRAINT `fkey_goods_image_goods` FOREIGN KEY (`fk_goods_id`)
      REFERENCES `simpleshop_goods` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='商品图片表：主图 + 图集，只允许图片';


-- -----------------------------------------------------------------------------
-- 4 / 5　商品描述内容块表
--   描述是 TEXT / IMAGE / VIDEO 的有序块列表，不存 HTML。
--   TEXT 块写 text_content、media_url 为空；IMAGE/VIDEO 块写 media_url、text_content 为空。
--   纯文本允许换行；整段文本总长度 1000 字符由应用层校验，单块列宽按 1000 预留。
--   描述媒体数量不限；图片 5MB、视频 20MB 等限制由应用配置控制。
--   sort_no 在同一商品内唯一，决定图文/视频的混排顺序。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_goods_content_block` (
  `id`          varchar(50)  NOT NULL COMMENT '块 ID：GB + UUID',
  `fk_goods_id`  varchar(50)  NOT NULL COMMENT '所属商品 ID',
  `block_type`   varchar(10)  NOT NULL COMMENT 'TEXT / IMAGE / VIDEO',
  `sort_no`      int          NOT NULL COMMENT '排序号，从 1 开始，同一商品内唯一',
  `text_content` varchar(1000) DEFAULT NULL COMMENT 'TEXT 块的纯文本，允许换行，单块 <=1000 字符',
  `media_url`    varchar(512) DEFAULT NULL COMMENT 'IMAGE / VIDEO 块的相对 URL',
  `create_at`     datetime     NOT NULL COMMENT '创建时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_goods_content_block_sort` (`fk_goods_id`, `sort_no`),
  CONSTRAINT `ck_goods_content_block_type` CHECK (`block_type` IN ('TEXT', 'IMAGE', 'VIDEO')),
  CONSTRAINT `ck_goods_content_block_sort_no` CHECK (`sort_no` > 0),
  CONSTRAINT `ck_goods_content_block_shape` CHECK (
      (`block_type` = 'TEXT' AND `text_content` IS NOT NULL AND `media_url` IS NULL)
   OR (`block_type` IN ('IMAGE', 'VIDEO') AND `text_content` IS NULL AND `media_url` IS NOT NULL)
  ),
  CONSTRAINT `fkey_goods_content_block_goods` FOREIGN KEY (`fk_goods_id`)
      REFERENCES `simpleshop_goods` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='商品描述内容块表：纯文本、图片、视频按 sort_no 混排';


-- -----------------------------------------------------------------------------
-- 5 / 5　历史商品表
--   沿用原商品 ID，永久保留；当前表归档后删除对应商品。
--   描述与图片均存 JSON 快照，历史查询不再依赖当前商品/图片/描述表。
--   媒体文件保留在原目录，不随归档搬迁；快照中存相对 URL。
--   description_snapshot 形如：
--     [{"type":"text","text":"..."},
--      {"type":"image","url":"/media/description/..."},
--      {"type":"video","url":"/media/description/..."}]
--   images_snapshot 形如：
--     [{"url":"/media/goods/...","is_main":true,"sort_no":1},
--      {"url":"/media/goods/...","is_main":false,"sort_no":2}]
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_goods_history` (
  `id`                  varchar(50)  NOT NULL COMMENT '沿用原商品 ID：G + UUID',
  `name`                varchar(50)  NOT NULL COMMENT '继承原值',
  `price`               decimal(8,2) NOT NULL COMMENT '继承原值',
  `stock`               int          NOT NULL COMMENT '归档时最终库存，成交售罄为 0',
  `category_l1_name`    varchar(50)  NOT NULL COMMENT '归档时一级类别名快照',
  `category_l2_name`    varchar(50)  NOT NULL COMMENT '归档时二级类别名快照',
  `result`              varchar(20)  NOT NULL COMMENT '归档结果：SOLD_OUT',
  `description_snapshot` JSON         NOT NULL COMMENT '描述内容块 JSON 快照',
  `images_snapshot`      JSON         NOT NULL COMMENT '商品图片 JSON 快照',
  `create_at`            datetime     NOT NULL COMMENT '发布时间（UTC），继承原值',
  `archived_at`          datetime     NOT NULL COMMENT '归档时间（UTC）',
  PRIMARY KEY (`id`),
  KEY `idx_goods_history_archived_at` (`archived_at`),
  KEY `idx_goods_history_create_at` (`create_at`),
  CONSTRAINT `ck_goods_history_price` CHECK (`price` > 0),
  CONSTRAINT `ck_goods_history_stock` CHECK (`stock` >= 0),
  CONSTRAINT `ck_goods_history_result` CHECK (`result` = 'SOLD_OUT'),
  CONSTRAINT `ck_goods_history_time_order` CHECK (`archived_at` >= `create_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='历史商品表：成交售罄归档，描述与图片存 JSON 快照';


-- =============================================================================
-- 02-account-tables.sql
-- =============================================================================

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


-- =============================================================================
-- 03-intention-tables.sql
-- =============================================================================

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
  `trading_flag`  tinyint GENERATED ALWAYS AS (CASE WHEN `status` = 'TRADING' THEN 1 ELSE NULL END) STORED
      COMMENT '交易中唯一性辅助列：TRADING 为 1，其余为 NULL',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_intention_goods_queue_order` (`fk_goods_id`, `queue_order`),
  UNIQUE KEY `uk_intention_trading` (`fk_goods_id`, `trading_flag`),
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
  COMMENT='当前意向表：引用买家账号，按商品独立排队；一商品至多一条 TRADING';


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
