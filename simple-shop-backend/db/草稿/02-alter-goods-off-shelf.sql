-- =============================================================================
-- simple-shop 阶段三 · 02 修改商品表（草稿）
--
-- 状态：已并入 V3__stage3_goods_and_trade_schema.sql（定稿），本文件仅作设计过程留档，不再维护。
-- 依据：《阶段三状态模型设计》§3.1、§3.3、§9.2、§9.3。
--
-- 变化：
--   1. status 由四态收敛为两态：ON_SALE / OFF_SHELF；
--   2. 新增 off_shelf_reason（MANUAL / STOCK_ZERO / SOLD_OUT）与 off_shelf_at；
--   3. 状态与原因成对：在售时两者都为空，已下架时两者都非空；
--   4. 增加历史商品列表索引 (status, off_shelf_reason, off_shelf_at)。
--
-- 旧值归一（开发库可直接重建；保留数据的库按此近似映射）：
--   FROZEN     → ON_SALE            无预占模型下视为未成交，冻结机制已废除
--   OFF_MANUAL → OFF_SHELF + MANUAL
--   OFF_ZERO   → OFF_SHELF + STOCK_ZERO
--   off_shelf_at 只能取 update_at 近似（旧表没有下架时间）
--
-- 执行顺序不能颠倒：先删旧 CHECK、再归一数据、最后加新 CHECK；
-- 否则把 status 改成 OFF_SHELF 会被旧约束拒绝。
-- 版本要求：MySQL >= 8.0.16（DROP CHECK 可用，CHECK 真正生效）。
-- =============================================================================

ALTER TABLE `simpleshop_goods`
  ADD COLUMN `off_shelf_reason` varchar(20) DEFAULT NULL
      COMMENT '下架原因：MANUAL / STOCK_ZERO / SOLD_OUT；在售时为空' AFTER `status`,
  ADD COLUMN `off_shelf_at` datetime DEFAULT NULL
      COMMENT '下架时间（UTC）；在售时为空' AFTER `off_shelf_reason`;

ALTER TABLE `simpleshop_goods`
  DROP CHECK `ck_goods_status`;

UPDATE `simpleshop_goods`
SET `status` = 'ON_SALE'
WHERE `status` = 'FROZEN';

UPDATE `simpleshop_goods`
SET `status` = 'OFF_SHELF',
    `off_shelf_reason` = 'MANUAL',
    `off_shelf_at` = `update_at`
WHERE `status` = 'OFF_MANUAL';

UPDATE `simpleshop_goods`
SET `status` = 'OFF_SHELF',
    `off_shelf_reason` = 'STOCK_ZERO',
    `off_shelf_at` = `update_at`
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
  ADD KEY `idx_goods_status_reason_off_shelf` (`status`, `off_shelf_reason`, `off_shelf_at`),
  COMMENT = '商品表：在售 / 已下架两态，下架原因与时间见 off_shelf_*；库存见 simpleshop_goods_stock';
