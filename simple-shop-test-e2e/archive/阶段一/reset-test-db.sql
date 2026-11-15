-- ============================================================
-- 复位测试库（simple_shop_test）
-- ⚠️ 只清测试库，绝不碰正式库 simple_shop
-- 保留 simpleshop_users（卖家账号是 Flyway 种子数据）；
-- 表结构不动，只清业务数据 ⇒ 后端不用重启。
-- ============================================================
SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE simpleshop_intentions;
TRUNCATE TABLE simpleshop_intentions_history;
TRUNCATE TABLE simpleshop_goods;
TRUNCATE TABLE simpleshop_goods_history;
TRUNCATE TABLE simpleshop_trade_history;

SET FOREIGN_KEY_CHECKS = 1;

-- ⚠️ 序号表【不能 TRUNCATE】：id=1 那一行是 Flyway V2 种子数据，
--    缺了它提交意向会抛 IllegalStateException → HTTP 500
--    （"queue sequence row (id=1) is missing; V2__seed_initial_data.sql must have inserted it"）。
--    正确做法是把计数重置为 0（下一个意向仍然是第 1 位）。
INSERT INTO simpleshop_queue_sequence (id, current_value)
VALUES (1, 0)
ON DUPLICATE KEY UPDATE current_value = 0;
