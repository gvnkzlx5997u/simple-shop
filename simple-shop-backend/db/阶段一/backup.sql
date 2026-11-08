/*
 Navicat Premium Dump SQL

 Source Server         : test2
 Source Server Type    : MySQL
 Source Server Version : 80044 (8.0.44)
 Source Host           : localhost:3306
 Source Schema         : simple_shop

 Target Server Type    : MySQL
 Target Server Version : 80044 (8.0.44)
 File Encoding         : 65001

 Date: 23/10/2026 12:41:50
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for flyway_schema_history
-- ----------------------------
DROP TABLE IF EXISTS `flyway_schema_history`;
CREATE TABLE `flyway_schema_history`  (
  `installed_rank` int NOT NULL,
  `version` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `description` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `script` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `checksum` int NULL DEFAULT NULL,
  `installed_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`) USING BTREE,
  INDEX `flyway_schema_history_s_idx`(`success` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of flyway_schema_history
-- ----------------------------
INSERT INTO `flyway_schema_history` VALUES (1, '1', 'baseline schema', 'SQL', 'V1__baseline_schema.sql', -618310059, 'root', '2026-10-09 06:13:25', 390, 1);
INSERT INTO `flyway_schema_history` VALUES (2, '2', 'seed initial data', 'SQL', 'V2__seed_initial_data.sql', 1157441179, 'root', '2026-10-09 06:13:25', 13, 1);

-- ----------------------------
-- Table structure for simpleshop_goods
-- ----------------------------
DROP TABLE IF EXISTS `simpleshop_goods`;
CREATE TABLE `simpleshop_goods`  (
  `id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商品 ID：G + 标准 UUID',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '名称，<=50 字符',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '描述，<=500 字符，纯文本',
  `pic_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '图片相对路径，服务端生成',
  `price` decimal(8, 2) NOT NULL COMMENT '价格，0 < price <= 999999.99',
  `status` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'on_sale / frozen（本表不落 off_sale）',
  `freeze_by` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'manual / trade；on_sale 时为空',
  `create_at` datetime NOT NULL COMMENT '发布时间（UTC）',
  `update_at` datetime NOT NULL COMMENT '更新时间（UTC）',
  `trade_start` datetime NULL DEFAULT NULL COMMENT '交易开始时间（UTC），只记录不展示',
  `trade_end` datetime NULL DEFAULT NULL COMMENT '交易结束时间（UTC），归档时必填',
  `result` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'sold / offline；本表恒为 NULL（不写入）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_goods_status`(`status` ASC) USING BTREE,
  INDEX `idx_goods_create_at`(`create_at` ASC) USING BTREE,
  CONSTRAINT `ck_goods_time_order` CHECK ((`trade_start` is null) or (`trade_end` is null) or (`trade_end` >= `trade_start`))
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商品表：仅存当前商品，至多 1 行' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of simpleshop_goods
-- ----------------------------

-- ----------------------------
-- Table structure for simpleshop_goods_history
-- ----------------------------
DROP TABLE IF EXISTS `simpleshop_goods_history`;
CREATE TABLE `simpleshop_goods_history`  (
  `id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '沿用原商品 ID，前缀仍为 G',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '继承原值',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '继承原值',
  `pic_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '继承原值；图片文件不搬迁',
  `price` decimal(8, 2) NOT NULL COMMENT '继承原值',
  `status` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '归档时恒写 off_sale',
  `freeze_by` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '归档时恒清空',
  `create_at` datetime NOT NULL COMMENT '发布时间（UTC），继承原值',
  `update_at` datetime NOT NULL COMMENT '归档时间（UTC）',
  `trade_start` datetime NULL DEFAULT NULL COMMENT '交易开始时间（UTC），手动下架时为空',
  `trade_end` datetime NOT NULL COMMENT '交易结束时间（UTC），历史列表展示此字段',
  `result` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'sold / offline',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_goods_history_trade_end`(`trade_end` ASC) USING BTREE,
  INDEX `idx_goods_history_create_at`(`create_at` ASC) USING BTREE,
  CONSTRAINT `ck_goods_history_time_order` CHECK ((`trade_end` >= `create_at`) and ((`trade_start` is null) or (`trade_end` >= `trade_start`)))
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '历史商品表：已下架商品归档，永久保留' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of simpleshop_goods_history
-- ----------------------------
INSERT INTO `simpleshop_goods_history` VALUES ('G050301eb-1b9b-4a6c-83d4-a594e84d8447', '测试商品2', 'asdfasdf', '/images/2026/10/b1ba98f4-1c81-48f1-a49c-28d6c92b0b04.jpg', 2.10, 'off_sale', NULL, '2026-10-12 06:19:13', '2026-10-12 06:20:14', '2026-10-12 06:20:01', '2026-10-12 06:20:14', 'offline');
INSERT INTO `simpleshop_goods_history` VALUES ('G88a5be2d-ecbb-4bd5-bfad-d4a75b790a26', '测试商品3', 'dsafasf', NULL, 1.00, 'off_sale', NULL, '2026-10-12 06:45:27', '2026-10-12 06:45:46', NULL, '2026-10-12 06:45:46', 'offline');
INSERT INTO `simpleshop_goods_history` VALUES ('Gb4c884d3-45f9-4bc8-9372-4743cb7557a3', '测试商品1', 'asdfsdaflkczv\n撒旦法23dasf\n前rjl1\n阿斯蒂芬', NULL, 1.00, 'off_sale', NULL, '2026-10-12 06:10:27', '2026-10-12 06:12:28', '2026-10-12 06:12:26', '2026-10-12 06:12:28', 'sold');

-- ----------------------------
-- Table structure for simpleshop_intentions
-- ----------------------------
DROP TABLE IF EXISTS `simpleshop_intentions`;
CREATE TABLE `simpleshop_intentions`  (
  `id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '意向编号：I + 标准 UUID',
  `fk_good_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属商品 ID',
  `queue_order` int NOT NULL COMMENT '排序序号，全库唯一，重排队时刷新',
  `create_at` datetime NOT NULL COMMENT '原始提交时间（UTC），重排队不变',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '买家姓名，<=50 字符',
  `tel` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '联系电话，7~20 位数字与 -',
  `status` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'queued/trading/succeeded/failed/revoked',
  `fail_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'sold_out/revoked/voided/requeued',
  `fail_reason` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '失败备注，<=300 字符',
  `token` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '口令码，12 位大写字母+数字，可反查',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_intention_queue_order`(`queue_order` ASC) USING BTREE,
  UNIQUE INDEX `uk_intention_token`(`token` ASC) USING BTREE,
  INDEX `idx_intention_goods_status_order`(`fk_good_id` ASC, `status` ASC, `queue_order` ASC) USING BTREE,
  INDEX `idx_intention_create_at`(`create_at` ASC) USING BTREE,
  INDEX `idx_intention_status`(`status` ASC) USING BTREE,
  CONSTRAINT `fkey_intentions_fk_good_id` FOREIGN KEY (`fk_good_id`) REFERENCES `simpleshop_goods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '购买意向表：仅存当前商品的意向队列' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of simpleshop_intentions
-- ----------------------------

-- ----------------------------
-- Table structure for simpleshop_intentions_history
-- ----------------------------
DROP TABLE IF EXISTS `simpleshop_intentions_history`;
CREATE TABLE `simpleshop_intentions_history`  (
  `id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '沿用原意向 ID，前缀仍为 I',
  `fk_goods_history_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属历史商品 ID',
  `create_at` datetime NOT NULL COMMENT '原始提交时间（UTC），历史名单排序键',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '继承原值',
  `tel` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '继承原值',
  `status` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '归档时必为终态',
  `fail_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '继承原值',
  `fail_reason` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '继承原值',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_intention_history_goods`(`fk_goods_history_id` ASC, `create_at` ASC) USING BTREE,
  INDEX `idx_intention_history_status`(`status` ASC) USING BTREE,
  CONSTRAINT `fkey_intention_history_fk_goods_history_id` FOREIGN KEY (`fk_goods_history_id`) REFERENCES `simpleshop_goods_history` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '历史意向表：随商品归档的意向记录（不含 queue_order / token），永久保留' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of simpleshop_intentions_history
-- ----------------------------
INSERT INTO `simpleshop_intentions_history` VALUES ('I46c20b76-45cc-4c25-b9ef-92d2b5102378', 'Gb4c884d3-45f9-4bc8-9372-4743cb7557a3', '2026-10-12 06:11:06', 'sadf', '11111111111', 'succeeded', NULL, NULL);
INSERT INTO `simpleshop_intentions_history` VALUES ('I939ba9c8-d04f-40f9-be15-00b87be48429', 'G050301eb-1b9b-4a6c-83d4-a594e84d8447', '2026-10-12 06:19:44', '123', '1234567890', 'failed', 'voided', NULL);

-- ----------------------------
-- Table structure for simpleshop_queue_sequence
-- ----------------------------
DROP TABLE IF EXISTS `simpleshop_queue_sequence`;
CREATE TABLE `simpleshop_queue_sequence`  (
  `id` tinyint NOT NULL COMMENT '固定为 1，单行表',
  `current_value` bigint NOT NULL DEFAULT 0 COMMENT '已分配的最大 queue_order',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '队列序号辅助表：为 queue_order 提供全库单调自增序号' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of simpleshop_queue_sequence
-- ----------------------------
INSERT INTO `simpleshop_queue_sequence` VALUES (1, 3);

-- ----------------------------
-- Table structure for simpleshop_trade_history
-- ----------------------------
DROP TABLE IF EXISTS `simpleshop_trade_history`;
CREATE TABLE `simpleshop_trade_history`  (
  `id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流水 ID：TH + 标准 UUID',
  `fk_intention_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '意向编号（逻辑关联，不建物理外键）',
  `trade_start` datetime NOT NULL COMMENT '该次交易开始时间（UTC）',
  `trade_end` datetime NOT NULL COMMENT '该次交易结束时间（UTC）',
  `result` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'sold / failed',
  `fail_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'failed 时必填（应用层保证）',
  `fail_reason` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '失败备注，<=300 字符',
  `create_at` datetime NOT NULL COMMENT '流水追加写入时间（UTC）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_trade_history_intention`(`fk_intention_id` ASC, `trade_start` ASC) USING BTREE,
  INDEX `idx_trade_history_create_at`(`create_at` ASC) USING BTREE,
  CONSTRAINT `ck_trade_history_time_order` CHECK (`trade_end` >= `trade_start`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '意向交易流水表：只追加，不更新不删除' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of simpleshop_trade_history
-- ----------------------------
INSERT INTO `simpleshop_trade_history` VALUES ('TH5c843175-f667-4292-a76b-bef8fd15586b', 'I46c20b76-45cc-4c25-b9ef-92d2b5102378', '2026-10-12 06:11:56', '2026-10-12 06:12:15', 'failed', 'requeued', 'asdfkjsdafl', '2026-10-12 06:12:15');
INSERT INTO `simpleshop_trade_history` VALUES ('TH7d44dfd7-6012-40a4-9838-1e6cf897d3fa', 'I939ba9c8-d04f-40f9-be15-00b87be48429', '2026-10-12 06:20:01', '2026-10-12 06:20:09', 'failed', 'voided', NULL, '2026-10-12 06:20:09');
INSERT INTO `simpleshop_trade_history` VALUES ('TH7db65723-220f-43e1-a170-1203f67af6af', 'I46c20b76-45cc-4c25-b9ef-92d2b5102378', '2026-10-12 06:12:26', '2026-10-12 06:12:28', 'sold', NULL, NULL, '2026-10-12 06:12:28');

-- ----------------------------
-- Table structure for simpleshop_users
-- ----------------------------
DROP TABLE IF EXISTS `simpleshop_users`;
CREATE TABLE `simpleshop_users`  (
  `id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '账号 ID：U + 标准 UUID',
  `account` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录账号，全局唯一',
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '不可逆哈希，禁止明文',
  `create_at` datetime NOT NULL COMMENT '创建时间（UTC）',
  `update_at` datetime NOT NULL COMMENT '密码最后修改时间（UTC）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_account`(`account` ASC) USING BTREE,
  CONSTRAINT `ck_user_password_not_blank` CHECK (`password` <> _utf8mb4'')
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '卖家账号表：单账号，账号数据永久保留' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of simpleshop_users
-- ----------------------------
INSERT INTO `simpleshop_users` VALUES ('U0000000-0000-4000-8000-000000000001', 'seller', '$2a$10$OHzqM5qU3fCLG/ixnx/EReXS6NHnT37EpZyavH3Zkff2T4YjVZ3R6', '2026-10-09 06:13:25', '2026-10-09 06:13:25');

SET FOREIGN_KEY_CHECKS = 1;
