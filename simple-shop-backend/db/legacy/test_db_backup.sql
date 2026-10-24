/*
 Navicat Premium Dump SQL

 Source Server         : test2
 Source Server Type    : MySQL
 Source Server Version : 80044 (8.0.44)
 Source Host           : localhost:3306
 Source Schema         : simple_shop_test

 Target Server Type    : MySQL
 Target Server Version : 80044 (8.0.44)
 File Encoding         : 65001

 Date: 23/10/2026 12:43:15
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for flyway_schema_history
-- ----------------------------
DROP TABLE IF EXISTS `flyway_schema_history`;
CREATE TABLE `flyway_schema_history`  (
  `installed_rank` int NOT NULL,
  `version` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `description` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `script` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `checksum` int NULL DEFAULT NULL,
  `installed_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`) USING BTREE,
  INDEX `flyway_schema_history_s_idx`(`success` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of flyway_schema_history
-- ----------------------------
INSERT INTO `flyway_schema_history` VALUES (1, '1', 'baseline schema', 'SQL', 'V1__baseline_schema.sql', -618310059, 'root', '2026-10-09 11:31:22', 410, 1);
INSERT INTO `flyway_schema_history` VALUES (2, '2', 'seed initial data', 'SQL', 'V2__seed_initial_data.sql', 1157441179, 'root', '2026-10-09 11:31:22', 16, 1);

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
INSERT INTO `simpleshop_goods` VALUES ('Gdfa15b45-c096-46ff-bd52-c084f2a4e8c8', 'E2E测试商品 2026-10-14 09:13:29', '由自动化验收测试创建，用于验证买家端与卖家端流程', NULL, 199.00, 'frozen', 'trade', '2026-10-14 09:13:29', '2026-10-14 09:13:30', '2026-10-14 09:13:30', NULL, NULL);

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
INSERT INTO `simpleshop_goods_history` VALUES ('G0ae04979-05fe-4595-a155-6694e6f6e45a', '界面发布商品W6SN5', '由自动化验收测试通过界面发布', NULL, 128.50, 'off_sale', NULL, '2026-10-14 09:13:27', '2026-10-14 09:13:28', NULL, '2026-10-14 09:13:28', 'offline');
INSERT INTO `simpleshop_goods_history` VALUES ('G63a30add-650e-4f09-8f17-ecab68dc29fc', 'E2E测试商品 2026-10-14 09:12:57', '由自动化验收测试创建，用于验证买家端与卖家端流程', NULL, 199.00, 'off_sale', NULL, '2026-10-14 09:12:57', '2026-10-14 09:13:23', '2026-10-14 09:13:23', '2026-10-14 09:13:23', 'sold');

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
INSERT INTO `simpleshop_intentions` VALUES ('I590ad1a3-1724-497f-9c7f-66f5e69596c9', 'Gdfa15b45-c096-46ff-bd52-c084f2a4e8c8', 7, '2026-10-14 09:13:29', '拦截测试乙', '13700002222', 'queued', NULL, NULL, 'K8QBTFSWYGW6');
INSERT INTO `simpleshop_intentions` VALUES ('I7f0f0d44-5b38-4182-9f4a-a85b3972cdd1', 'Gdfa15b45-c096-46ff-bd52-c084f2a4e8c8', 6, '2026-10-14 09:13:29', '拦截测试甲', '13700001111', 'trading', NULL, NULL, 'KYTJ9NNB5QYP');

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
INSERT INTO `simpleshop_intentions_history` VALUES ('I07c636cc-638c-4f4e-8248-10ee5f410ae8', 'G63a30add-650e-4f09-8f17-ecab68dc29fc', '2026-10-14 09:13:11', '剧本买家乙W6SN5', '13900002222', 'failed', 'voided', '自动化验收测试：模拟一次卖家作废');
INSERT INTO `simpleshop_intentions_history` VALUES ('I0ae34bbe-8295-40ab-85f4-2802c5ec2466', 'G63a30add-650e-4f09-8f17-ecab68dc29fc', '2026-10-14 09:13:11', '剧本买家丙W6SN5', '13900003333', 'succeeded', NULL, NULL);
INSERT INTO `simpleshop_intentions_history` VALUES ('I4b49bf2d-2702-4921-a835-020630a59b3d', 'G63a30add-650e-4f09-8f17-ecab68dc29fc', '2026-10-14 09:13:11', '剧本买家甲W6SN5', '13900001111', 'failed', 'sold_out', '自动化验收测试：模拟一次失败的交易');
INSERT INTO `simpleshop_intentions_history` VALUES ('Id42d412c-07b9-496d-8a06-13efd3dbbe08', 'G63a30add-650e-4f09-8f17-ecab68dc29fc', '2026-10-14 09:13:06', '自动测试李四', '13800001111', 'revoked', 'revoked', NULL);

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
INSERT INTO `simpleshop_queue_sequence` VALUES (1, 7);

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
INSERT INTO `simpleshop_trade_history` VALUES ('TH2d7fefe0-ae56-40c0-9c28-e428d0d73d8c', 'I0ae34bbe-8295-40ab-85f4-2802c5ec2466', '2026-10-14 09:13:23', '2026-10-14 09:13:23', 'sold', NULL, NULL, '2026-10-14 09:13:23');
INSERT INTO `simpleshop_trade_history` VALUES ('TH5c74ae3d-b9e2-4298-b0ae-58d3fd09cc11', 'I07c636cc-638c-4f4e-8248-10ee5f410ae8', '2026-10-14 09:13:22', '2026-10-14 09:13:22', 'failed', 'voided', '自动化验收测试：模拟一次卖家作废', '2026-10-14 09:13:22');
INSERT INTO `simpleshop_trade_history` VALUES ('THb5721fdf-8101-4cc4-8150-6d0d4702ba11', 'I4b49bf2d-2702-4921-a835-020630a59b3d', '2026-10-14 09:13:19', '2026-10-14 09:13:21', 'failed', 'requeued', '自动化验收测试：模拟一次失败的交易', '2026-10-14 09:13:21');

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
INSERT INTO `simpleshop_users` VALUES ('U0000000-0000-4000-8000-000000000001', 'seller', '$2a$10$H2DtwLwsf904/lMX1EtsCe40w6pXvXTkQlB6GVLvYfmA3nc9tGuLG', '2026-10-09 11:31:22', '2026-10-10 05:47:45');

SET FOREIGN_KEY_CHECKS = 1;
