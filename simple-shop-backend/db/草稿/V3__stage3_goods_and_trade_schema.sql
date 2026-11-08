-- =============================================================================
-- simple-shop 阶段三 · 结构迁移（V3 定稿）
--
-- 三处结构变更，必须按本文顺序执行：先删表，再改商品表，最后建交易表。
--
--   1. 删除意向与归档四张表：意向、历史意向、历史商品、商品队列序号；
--   2. 商品表两态化：status 收敛为 ON_SALE / OFF_SHELF，新增下架原因与下架时间；
--   3. 新增交易四张表：订单、订单项、购物车行、收藏行。
--
-- 迁移后表数量仍是 12 张。已发布的 V1 / V2 不得修改，本脚本只做追加。
--
-- 为什么保留旧状态值的换算语句：
--   阶段二的库里有 FROZEN / OFF_MANUAL / OFF_ZERO 三种旧值，不先换算就会被新
--   CHECK 拒绝，迁移在建约束这一步直接失败。三条 UPDATE 只做枚举值换算，
--   不迁移任何业务数据：意向与历史记录随表删除，不转成订单（无预占模型下
--   冻结视为未成交，因此 FROZEN → ON_SALE）。
--
-- 金额精度：商品价是 decimal(8,2)，订单项快照沿用同一精度；订单总额按
--   Σ(单价 × 数量) 由服务端算出后落 decimal(12,2)。全链路统一两位小数，
--   不做四位小数，避免金额比较与展示出现两套舍入口径。
--
-- 订单号：varchar(32)。够放「前缀 + 日期 + 序列 + 并发后缀」，具体生成格式
--   与撞号重试在订单模块实现时定，这里只保证列宽与唯一约束先行到位。
--
-- 订单项唯一键 (fk_order_id, fk_goods_id)：同一订单内同一商品只允许一行。
--   服务端对重复明细做合并（数量相加）或拒绝都必须服从该键，数据库不允许出现
--   两行同商品，避免「同一订单两条明细各自通过校验」绕过库存判断。
-- =============================================================================


-- =============================================================================
-- 1 / 3　删除意向与归档
--
--   顺序必须「先子表、后父表」：
--     intention_history → goods_history；intention / goods_queue_sequence → goods
--   商品、库存、图片、描述、类别、两个账号表都不动。
-- =============================================================================

DROP TABLE `simpleshop_intention_history`;
DROP TABLE `simpleshop_goods_history`;
DROP TABLE `simpleshop_intention`;
DROP TABLE `simpleshop_goods_queue_sequence`;


-- =============================================================================
-- 2 / 3　商品表两态化
--
--   状态与原因成对：在售时原因与下架时间都为空，已下架时都非空。
--   执行顺序不能颠倒：先删旧 CHECK、再换算数据、最后加新 CHECK，
--   否则写入 OFF_SHELF 会被旧约束拒绝。
-- =============================================================================

ALTER TABLE `simpleshop_goods`
  ADD COLUMN `off_shelf_reason` varchar(20) DEFAULT NULL
      COMMENT '下架原因：MANUAL / STOCK_ZERO / SOLD_OUT；在售时为空' AFTER `status`,
  ADD COLUMN `off_shelf_at` datetime DEFAULT NULL
      COMMENT '下架时间（UTC）；在售时为空' AFTER `off_shelf_reason`;

-- 旧 CHECK 只认四个旧状态值，必须先删才能写入 OFF_SHELF。
ALTER TABLE `simpleshop_goods`
  DROP CHECK `ck_goods_status`;

-- 无预占模型下「冻结」不再表示成交进行中，视为未成交的在售商品。
UPDATE `simpleshop_goods`
SET `status` = 'ON_SALE'
WHERE `status` = 'FROZEN';

-- 旧表没有下架时间，只能取 update_at 近似；与 create_at 取较大值，
-- 保证不违反下面 ck_goods_off_shelf_time（旧表没有 update_at >= create_at 的约束）。
UPDATE `simpleshop_goods`
SET `status` = 'OFF_SHELF',
    `off_shelf_reason` = 'MANUAL',
    `off_shelf_at` = GREATEST(`update_at`, `create_at`)
WHERE `status` = 'OFF_MANUAL';

UPDATE `simpleshop_goods`
SET `status` = 'OFF_SHELF',
    `off_shelf_reason` = 'STOCK_ZERO',
    `off_shelf_at` = GREATEST(`update_at`, `create_at`)
WHERE `status` = 'OFF_ZERO';

ALTER TABLE `simpleshop_goods`
  ADD CONSTRAINT `ck_goods_status` CHECK (`status` IN ('ON_SALE', 'OFF_SHELF')),
  ADD CONSTRAINT `ck_goods_off_shelf` CHECK (
      (`status` = 'ON_SALE' AND `off_shelf_reason` IS NULL AND `off_shelf_at` IS NULL)
   OR (`status` = 'OFF_SHELF'
       AND `off_shelf_reason` IN ('MANUAL', 'STOCK_ZERO', 'SOLD_OUT')
       AND `off_shelf_at` IS NOT NULL)
  ),
  ADD CONSTRAINT `ck_goods_off_shelf_time` CHECK (
      `off_shelf_at` IS NULL OR `off_shelf_at` >= `create_at`),
  -- 卖家「历史商品」列表：按状态与原因筛选，按下架时间倒序。
  -- 原有 idx_goods_status_category_create 的第二列是类别，无法承担该排序。
  ADD KEY `idx_goods_status_reason_off_shelf`
      (`status`, `off_shelf_reason`, `off_shelf_at`),
  COMMENT = '商品表：在售 / 已下架两态，下架原因与时间见 off_shelf_*；库存见 simpleshop_goods_stock';


-- =============================================================================
-- 3 / 3　新增交易四张表
--
--   订单项、购物车行、收藏行都没有状态列：订单项的有效性由整单状态派生，
--   购物车与收藏行的有效性由商品状态在读取时派生，落状态就会出现两套真相。
--   下单快照只存进订单项（商品名、单价）；商品行永不物理删除，goods_id 可长期引用。
--   外键一律 RESTRICT，避免商品或账号被误删导致订单、购物车、收藏引用悬空。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 3.1 / 4　订单头
--   整单流转，状态只能按序前进；COMPLETED 与 CANCELED 是终态。
--   不存地址、备注、取消人与取消时间、状态时间线：这些要么不属于最小集，
--   要么只在操作日志里记。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_order` (
  `id`           varchar(50)   NOT NULL COMMENT '订单 ID：O + UUID',
  `order_no`     varchar(32)   NOT NULL COMMENT '订单号：人类可读，全局唯一，创建后不变',
  `fk_buyer_id`  varchar(50)   NOT NULL COMMENT '下单买家账号 ID；用户名与电话展示时从账号读',
  `total_amount` decimal(12,2) NOT NULL COMMENT '订单总额 = Σ(下单单价 × 数量)，服务端计算',
  `status`       varchar(20)   NOT NULL COMMENT 'PENDING_CONFIRM / CONFIRMED / PREPARED / SHIPPED / COMPLETED / CANCELED',
  `create_at`    datetime      NOT NULL COMMENT '下单时间（UTC），列表默认排序键',
  `update_at`    datetime      NOT NULL COMMENT '最后状态变更时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_order_buyer_create` (`fk_buyer_id`, `create_at`),
  KEY `idx_order_status_create` (`status`, `create_at`),
  KEY `idx_order_create_at` (`create_at`),
  CONSTRAINT `ck_order_no_not_blank` CHECK (`order_no` <> ''),
  CONSTRAINT `ck_order_total_amount` CHECK (`total_amount` > 0),
  CONSTRAINT `ck_order_status` CHECK (`status` IN (
      'PENDING_CONFIRM', 'CONFIRMED', 'PREPARED', 'SHIPPED', 'COMPLETED', 'CANCELED')),
  CONSTRAINT `ck_order_time_order` CHECK (`update_at` >= `create_at`),
  CONSTRAINT `fkey_order_buyer` FOREIGN KEY (`fk_buyer_id`)
      REFERENCES `simpleshop_buyer_account` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='订单表：整单流转，状态只能按序前进，不可跳过或回退';

-- -----------------------------------------------------------------------------
-- 3.2 / 4　订单项
--   没有 status 列：整单计算状态，明细只承载商品引用与下单快照，不存在部分完成。
--   uk_order_item_order_goods：同一订单内同一商品只允许一行，重复明细由服务端
--   合并或拒绝，唯一键兜底。
--   idx_order_item_goods 同时服务「商品的订单记录」与「是否存在未完成订单」。
--   订单详情按订单取明细走 uk_order_item_order_goods 的左前缀，不再单建索引。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_order_item` (
  `id`          varchar(50)  NOT NULL COMMENT '订单项 ID：OI + UUID',
  `fk_order_id` varchar(50)  NOT NULL COMMENT '所属订单 ID',
  `fk_goods_id` varchar(50)  NOT NULL COMMENT '商品 ID；商品不删除，引用长期有效',
  `goods_name`  varchar(50)  NOT NULL COMMENT '下单时的商品名快照，防止改名污染历史',
  `unit_price`  decimal(8,2) NOT NULL COMMENT '下单时的单价快照，与商品价同精度',
  `quantity`    int          NOT NULL COMMENT '购买数量，>= 1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_item_order_goods` (`fk_order_id`, `fk_goods_id`),
  KEY `idx_order_item_goods` (`fk_goods_id`, `fk_order_id`),
  CONSTRAINT `ck_order_item_quantity` CHECK (`quantity` > 0),
  CONSTRAINT `ck_order_item_unit_price` CHECK (`unit_price` > 0),
  CONSTRAINT `fkey_order_item_order` FOREIGN KEY (`fk_order_id`)
      REFERENCES `simpleshop_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fkey_order_item_goods` FOREIGN KEY (`fk_goods_id`)
      REFERENCES `simpleshop_goods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='订单项表：只存商品引用与下单快照，没有独立状态';

-- -----------------------------------------------------------------------------
-- 3.3 / 4　购物车行
--   只存「买家 + 商品 + 加购时间」。数量与勾选由前端 localStorage 维护，
--   后端不得把它们当可信数据落库；下单请求自带数量并重新校验。
--   商品下架后行保留，展示时按商品状态派生「失效」，不在行上落状态。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_cart_item` (
  `id`          varchar(50) NOT NULL COMMENT '购物车行 ID：CI + UUID',
  `fk_buyer_id` varchar(50) NOT NULL COMMENT '买家账号 ID',
  `fk_goods_id` varchar(50) NOT NULL COMMENT '商品 ID',
  `create_at`   datetime    NOT NULL COMMENT '加购时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cart_item_buyer_goods` (`fk_buyer_id`, `fk_goods_id`),
  KEY `idx_cart_item_buyer_create` (`fk_buyer_id`, `create_at`),
  KEY `idx_cart_item_goods` (`fk_goods_id`),
  CONSTRAINT `fkey_cart_item_buyer` FOREIGN KEY (`fk_buyer_id`)
      REFERENCES `simpleshop_buyer_account` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fkey_cart_item_goods` FOREIGN KEY (`fk_goods_id`)
      REFERENCES `simpleshop_goods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='购物车表：只存买家与商品，数量与勾选在前端，不落库';

-- -----------------------------------------------------------------------------
-- 3.4 / 4　收藏
--   只存「买家 + 商品 + 收藏时间」，不做商品快照；商品下架后行保留，
--   展示时派生失效，复售时无需任何同步。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_favorite` (
  `id`          varchar(50) NOT NULL COMMENT '收藏记录 ID：FV + UUID',
  `fk_buyer_id` varchar(50) NOT NULL COMMENT '买家账号 ID',
  `fk_goods_id` varchar(50) NOT NULL COMMENT '商品 ID',
  `create_at`   datetime    NOT NULL COMMENT '收藏时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorite_buyer_goods` (`fk_buyer_id`, `fk_goods_id`),
  KEY `idx_favorite_buyer_create` (`fk_buyer_id`, `create_at`),
  KEY `idx_favorite_goods` (`fk_goods_id`),
  CONSTRAINT `fkey_favorite_buyer` FOREIGN KEY (`fk_buyer_id`)
      REFERENCES `simpleshop_buyer_account` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fkey_favorite_goods` FOREIGN KEY (`fk_goods_id`)
      REFERENCES `simpleshop_goods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='收藏表：只存买家、商品与收藏时间，不做商品快照';
