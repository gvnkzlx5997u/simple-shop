-- 买家端 E2E 测试数据（阶段D）
--
-- 用法（注意必须指定 utf8mb4，否则中文报 ERROR 1366）：
--   mysql -uroot -p --default-character-set=utf8mb4 <库名> < simple-shop-d1-fixtures.sql
--
-- 幂等：重复执行只会把数据恢复成初始状态，不会报主键冲突。
-- 覆盖的商品 ID：GD-D1-ON / GD-D1-ON2 / GD-D1-ON3 / GD-D1-OFF / GD-FAV-01..15
-- 不含买家账号、订单、购物车、收藏——那些由测试脚本自己造并自己清。

SET time_zone = '+00:00';

-- ---------- 分类 ----------
INSERT INTO simpleshop_category_l1 (id, name, create_at, update_at)
VALUES ('CL1-D1', '数码', '2026-11-11 10:00:00', '2026-11-11 10:00:00')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO simpleshop_category_l2 (id, fk_l1_id, name, create_at, update_at)
VALUES ('CL2-D1', 'CL1-D1', '耳机', '2026-11-11 10:00:00', '2026-11-11 10:00:00')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- ---------- 4 件固定商品 ----------
INSERT INTO simpleshop_goods (id, name, price, fk_category_id, status, off_shelf_reason, off_shelf_at, create_at, update_at)
VALUES
  ('GD-D1-ON',  '测试耳机在售',   199.00, 'CL2-D1', 'ON_SALE',   NULL,     NULL, '2026-11-11 10:00:00', '2026-11-11 10:00:00'),
  ('GD-D1-ON2', '测试充电线缺货',  39.90, 'CL2-D1', 'ON_SALE',   NULL,     NULL, '2026-11-11 10:00:00', '2026-11-11 10:00:00'),
  ('GD-D1-ON3', '测试数据线在售',  15.50, 'CL2-D1', 'ON_SALE',   NULL,     NULL, '2026-11-11 10:00:00', '2026-11-11 10:00:00'),
  ('GD-D1-OFF', '测试耳机已下架', 299.00, 'CL2-D1', 'OFF_SHELF', 'MANUAL', '2026-11-11 10:00:00', '2026-11-11 10:00:00', '2026-11-11 10:00:00')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  price = VALUES(price),
  status = VALUES(status),
  off_shelf_reason = VALUES(off_shelf_reason),
  off_shelf_at = VALUES(off_shelf_at);

-- ---------- 15 件收藏测试商品（用于收藏列表分页） ----------
INSERT INTO simpleshop_goods (id, name, price, fk_category_id, status, off_shelf_reason, off_shelf_at, create_at, update_at)
SELECT CONCAT('GD-FAV-', LPAD(n, 2, '0')),
       CONCAT('收藏测试商品', n),
       10.00 + n,
       'CL2-D1',
       'ON_SALE',
       NULL,
       NULL,
       '2026-11-11 10:00:00',
       '2026-11-11 10:00:00'
FROM (SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
      UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
      UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14 UNION SELECT 15) t
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  price = VALUES(price),
  status = VALUES(status);

-- ---------- 库存 ----------
-- GD-D1-ON2 库存必须为 0：它是「在售但缺货」的唯一用例。
INSERT INTO simpleshop_goods_stock (fk_goods_id, available, update_at)
VALUES
  ('GD-D1-ON',  5, '2026-11-11 10:00:00'),
  ('GD-D1-ON2', 0, '2026-11-11 10:00:00'),
  ('GD-D1-ON3', 3, '2026-11-11 10:00:00'),
  ('GD-D1-OFF', 3, '2026-11-11 10:00:00')
ON DUPLICATE KEY UPDATE available = VALUES(available);

INSERT INTO simpleshop_goods_stock (fk_goods_id, available, update_at)
SELECT id, 5, '2026-11-11 10:00:00' FROM simpleshop_goods WHERE id LIKE 'GD-FAV-%'
ON DUPLICATE KEY UPDATE available = VALUES(available);

-- ---------- 核对 ----------
SELECT g.id, g.name, g.price, s.available, g.status
FROM simpleshop_goods g
JOIN simpleshop_goods_stock s ON s.fk_goods_id = g.id
WHERE g.id LIKE 'GD-%'
ORDER BY g.id;
