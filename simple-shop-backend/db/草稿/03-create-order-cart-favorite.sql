-- =============================================================================
-- simple-shop 阶段三 · 03 新增（草稿）
--
-- 状态：设计草稿；定稿后并入 V3。
-- 依据：《阶段三状态模型设计》§4、§5、§6、§9.2、§9.3。
--
-- 新建四张表（按外键依赖排序）：
--   simpleshop_order      订单（整单状态；引用买家账号）
--   simpleshop_order_item 订单项（引用订单与商品；没有状态）
--   simpleshop_cart_item  购物车行（引用买家与商品；不存数量与勾选）
--   simpleshop_favorite   收藏行（引用买家与商品；不做快照）
--
-- 设计口径：
--   · 订单项没有状态：没有部分取消、部分发货、部分完成，所有状态判断只读订单头。
--   · 下单快照存进订单项（商品名、单价）；商品行永不物理删除，goods_id 可长期引用。
--   · 订单总额由服务端按 Σ(单价 × 数量) 计算后落库，不接受客户端传入。
--   · 购物车行按「买家 + 商品」唯一；数量与勾选在前端 localStorage，不落库。
--   · 订单与订单项不做物理删除；购物车行、收藏行可由买家移除。
--   · 外键一律 RESTRICT，避免商品或账号被误删导致历史引用悬空。
--
-- 约定：主键 varchar(50)「前缀 + UUID」；时间列 UTC；utf8mb4 / utf8mb4_unicode_ci。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1 / 4　订单头
--   状态只能按序前进；取消与完成都是终态。
--   buyer 侧列表按 create_at 倒序；卖家订单管理按 status + create_at 筛选。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_order` (
  `id`           varchar(50)   NOT NULL COMMENT '订单 ID：O + UUID',
  `order_no`     varchar(32)   NOT NULL COMMENT '订单号：人类可读，全局唯一，创建后不变',
  `fk_buyer_id`  varchar(50)   NOT NULL COMMENT '下单买家账号 ID',
  `total_amount` decimal(12,2) NOT NULL COMMENT '订单总额 = Σ(下单单价 × 数量)，服务端计算',
  `status`       varchar(20)   NOT NULL COMMENT 'PENDING_CONFIRM / CONFIRMED / PREPARED / SHIPPED / COMPLETED / CANCELED',
  `create_at`    datetime      NOT NULL COMMENT '下单时间（UTC）',
  `update_at`    datetime      NOT NULL COMMENT '最后状态变更时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_order_buyer_create` (`fk_buyer_id`, `create_at`),
  KEY `idx_order_status_create` (`status`, `create_at`),
  CONSTRAINT `ck_order_no_not_blank` CHECK (`order_no` <> ''),
  CONSTRAINT `ck_order_total_amount` CHECK (`total_amount` > 0),
  CONSTRAINT `ck_order_status` CHECK (`status` IN (
      'PENDING_CONFIRM', 'CONFIRMED', 'PREPARED', 'SHIPPED', 'COMPLETED', 'CANCELED')),
  CONSTRAINT `fkey_order_buyer` FOREIGN KEY (`fk_buyer_id`)
      REFERENCES `simpleshop_buyer_account` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='订单表：整单流转，状态只能按序前进，不可跳过或回退';


-- -----------------------------------------------------------------------------
-- 2 / 4　订单项
--   没有 status 列：整单计算状态，明细只承载商品引用与下单快照。
--   uk_order_item_order_goods：同一订单内同一商品只允许一行；下单时对重复明细
--   做合并或拒绝（开发阶段定），该唯一键把结论固化在库里，避免出现两行同商品。
--   idx_order_item_goods 同时服务「商品订单记录」与「是否有未完成订单」检查。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_order_item` (
  `id`          varchar(50)  NOT NULL COMMENT '订单项 ID：OI + UUID',
  `fk_order_id` varchar(50)  NOT NULL COMMENT '所属订单 ID',
  `fk_goods_id` varchar(50)  NOT NULL COMMENT '商品 ID；商品不删除，引用长期有效',
  `goods_name`  varchar(50)  NOT NULL COMMENT '下单时的商品名快照',
  `unit_price`  decimal(8,2) NOT NULL COMMENT '下单时的单价快照',
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
-- 3 / 4　购物车行
--   只存「买家 + 商品 + 加购时间」；数量、勾选由前端维护（D3、F1）。
--   商品下架后行保留，展示时按商品状态派生「失效」，不在这里落状态。
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
-- 4 / 4　收藏
--   只存「买家 + 商品 + 收藏时间」，不做商品快照；商品下架后保留，展示时派生失效。
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
