-- =============================================================================
-- simple-shop 阶段三 · 01 删除（草稿）
--
-- 状态：设计草稿，不参与运行期加载；定稿后并入 V3 迁移脚本（V3__*.sql）。
-- 依据：《阶段三状态模型设计》§3.8、§9.4；团队决策 D5（数据库可重建，不做存量迁移）。
--
-- 删除四张表：
--   simpleshop_intention            购买意向（被订单取代）
--   simpleshop_intention_history    历史意向（随商品归档机制一并下线）
--   simpleshop_goods_history        历史商品（商品不再搬表，历史商品改为查询视图）
--   simpleshop_goods_queue_sequence 按商品队列序号（无队列即无序号）
--
-- 顺序必须「先子表、后父表」：
--   intention_history → goods_history；intention / goods_queue_sequence → goods
--
-- 注意：
--   · 已发布的 V1/V2 不得修改；本脚本只是 V3 的第一段。
--   · 意向与历史记录不迁移为订单（D5）；需要留档时先整库备份。
--   · 商品、库存、图片、描述、类别、两个账号表都不动。
--   · MySQL 的 DDL 不能回滚：三份草稿应在同一个 V3 中顺序执行，失败后按重建处理。
-- =============================================================================

DROP TABLE IF EXISTS `simpleshop_intention_history`;
DROP TABLE IF EXISTS `simpleshop_goods_history`;
DROP TABLE IF EXISTS `simpleshop_intention`;
DROP TABLE IF EXISTS `simpleshop_goods_queue_sequence`;
