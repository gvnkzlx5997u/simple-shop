-- =============================================================================
-- simple-shop 阶段二 · 商品类别建表脚本（半成品草案）
--
-- 状态：草案。未接入 Flyway，不创建数据库，也不会在启动时执行。
-- 范围：一级类别、二级类别两张表。
-- 执行顺序：本脚本必须在 01-goods-tables.sql 之前执行，
--           因为商品表的外键会引用二级类别。
--
-- 约定：
--   · 表名统一 simpleshop_ 前缀；主键为「前缀 + UUID」，varchar(50)。
--   · 时间列一律存 UTC。
--   · MySQL >= 8.0.16（CHECK 约束真正生效）。
--
-- 口径：
--   1. 只有两级，一级只做分组，商品必须挂到二级类别。
--   2. 同一父级下类别名不重复；一级类别名全局不重复。
--   3. 一级类别下仍有二级类别时，外键 RESTRICT 阻止删除。
--   4. 二级类别被商品引用时，外键 RESTRICT 阻止删除；应用层需先提示改挂商品。
--   5. 类别重命名只改 name 和 update_at；历史商品保存名称快照，不受影响。
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1 / 2　一级类别
-- -----------------------------------------------------------------------------
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
