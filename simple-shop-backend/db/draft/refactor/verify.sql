-- =============================================================================
-- 基线重建后的校验（草案）
-- 在目标库执行；1~3 为结构检查，4~10 为数据不变量，结果必须与注释一致。
-- =============================================================================

-- 1. 业务表数量：应为 12
SELECT COUNT(*) AS `table_count`
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_type = 'BASE TABLE'
  AND table_name LIKE 'simpleshop\_%';

-- 2. 商品表不应再有 stock 列：应为 0
SELECT COUNT(*) AS `goods_stock_column`
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'simpleshop_goods'
  AND column_name = 'stock';

-- 3. 意向表应有 trading_flag 与唯一索引：应为 2 行
SELECT index_name, non_unique, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'simpleshop_intention'
  AND index_name = 'uk_intention_trading'
ORDER BY seq_in_index;

-- 4. 库存行数必须等于商品行数
SELECT (SELECT COUNT(*) FROM `simpleshop_goods`)       AS `goods_count`,
       (SELECT COUNT(*) FROM `simpleshop_goods_stock`) AS `stock_count`;

-- 5. 缺失库存行（必须为空）
SELECT g.`id`
FROM `simpleshop_goods` g
LEFT JOIN `simpleshop_goods_stock` s ON s.`fk_goods_id` = g.`id`
WHERE s.`fk_goods_id` IS NULL;

-- 6. 孤儿库存行（必须为空）
SELECT s.`fk_goods_id`
FROM `simpleshop_goods_stock` s
LEFT JOIN `simpleshop_goods` g ON g.`id` = s.`fk_goods_id`
WHERE g.`id` IS NULL;

-- 7. 负库存（必须为空）
SELECT * FROM `simpleshop_goods_stock` WHERE `available` < 0;

-- 8. 不变量：ON_SALE 必然有库存（必须为空）
SELECT g.`id`, s.`available`
FROM `simpleshop_goods` g
JOIN `simpleshop_goods_stock` s ON s.`fk_goods_id` = g.`id`
WHERE g.`status` = 'ON_SALE' AND s.`available` = 0;

-- 9. 不变量：FROZEN 与 TRADING 一一对应（必须为空）
SELECT g.`id`, g.`status`
FROM `simpleshop_goods` g
LEFT JOIN `simpleshop_intention` i
       ON i.`fk_goods_id` = g.`id` AND i.`status` = 'TRADING'
WHERE (g.`status` = 'FROZEN') <> (i.`id` IS NOT NULL);

-- 10. 重复 TRADING（唯一索引应已阻止；必须为空）
SELECT `fk_goods_id`, COUNT(*) AS `trading_count`
FROM `simpleshop_intention`
WHERE `status` = 'TRADING'
GROUP BY `fk_goods_id`
HAVING COUNT(*) > 1;
