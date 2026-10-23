-- =============================================================================
-- simple-shop 数据层 · 基线建表脚本
-- Flyway 版本化脚本：V1__baseline_schema.sql
--
-- 依据：《数据库与数据层设计说明书》v1.2 第 4 章（表结构设计）、附录 A（DDL 汇总与执行顺序）、
--       §9.3（字符集与排序规则）、§10.3（校验职责划分 / DEC-DB-14）
-- 配套：《需求规格说明书》v1.1 §9.3.1（表清单）、§9.9（枚举字典）
--
-- 前置条件：数据库 simple_shop 已存在（utf8mb4 / utf8mb4_unicode_ci）
--           与 §9.3、附录 A「0. 库」一致：
--   CREATE DATABASE IF NOT EXISTS `simple_shop`
--     DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
--           Flyway 在既有库内执行，故本脚本不重复建库。
--
-- 版本要求：MySQL >= 8.0.16（本脚本使用 CHECK 约束，8.0.16 起才真正生效；本机 8.0.44）
--
-- 执行顺序（按外键依赖，= 附录 A）：
--   1. simpleshop_users              （无外键依赖）              §4.6
--   2. simpleshop_goods              （无外键依赖）              §4.1
--   3. simpleshop_intentions         （FK → goods）              §4.2
--   4. simpleshop_goods_history      （无外键依赖）              §4.3
--   5. simpleshop_intentions_history （FK → goods_history）      §4.4
--   6. simpleshop_trade_history      （刻意无外键，9-N）          §4.5
--   7. simpleshop_queue_sequence     （辅助表，无关联）           §4.7
--
-- ⚠️ 本脚本只保留「纯物理性」CHECK（ck_*_time_order、ck_user_password_not_blank）。
--    枚举取值、格式正则、价格区间类 CHECK 已按 DEC-DB-14 全部删除（清单见 §10.3.2），
--    值校验一律归应用层——数据库只管「结构对不对」，不管「值对不对」（§10.3.4）。
--
-- ⚠️ 已发布脚本不得修改（§9.1）：后续每次变更新增 V2、V3… 版本文件。
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1 / 7　simpleshop_users（卖家账号表）　　　　　　§4.6　·　字典 §9.8
--   单账号、无外键依赖，故最先建表。
--   实体类名为 ShopUser（User 是 SQL 保留字），@Table(name = "simpleshop_users")。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_users` (
  `id`         varchar(50)  NOT NULL COMMENT '账号 ID：U + 标准 UUID',
  `account`    varchar(50)  NOT NULL COMMENT '登录账号，全局唯一',
  `password`   varchar(100) NOT NULL COMMENT '不可逆哈希，禁止明文',
  `create_at`  datetime     NOT NULL COMMENT '创建时间（UTC）',
  `update_at`  datetime     NOT NULL COMMENT '密码最后修改时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_account` (`account`),
  -- 「禁止明文」的最低物理保障：哈希不可能为空串（保留的纯物理性 check）
  CONSTRAINT `ck_user_password_not_blank` CHECK (`password` <> '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='卖家账号表：单账号，账号数据永久保留';


-- -----------------------------------------------------------------------------
-- 2 / 7　simpleshop_goods（商品表）　　　　　　　　§4.1　·　字典 §9.4.1
--   记录上限 ≤ 1 行（INV-01）。该上限无法用纯 DDL 表达，由应用层保障（DEC-DB-06、§8.5）。
--   status 仅出现 on_sale / frozen；result 在本表恒为 NULL（DEC-DB-11、§10.6）。
--   trade_end **不建索引**：本表 trade_end 无查询需求（「按 trade_end 倒序分页」只发生在
--   历史商品表，见 I11-15 / §4.3.2），且本表至多 1 行。索引清单见 §4.1.2。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_goods` (
  `id`          varchar(50)   NOT NULL COMMENT '商品 ID：G + 标准 UUID',
  `name`        varchar(50)   NOT NULL COMMENT '名称，<=50 字符',
  `description` varchar(500)  DEFAULT NULL COMMENT '描述，<=500 字符，纯文本',
  `pic_url`     varchar(512)  DEFAULT NULL COMMENT '图片相对路径，服务端生成',
  `price`       decimal(8,2)  NOT NULL COMMENT '价格，0 < price <= 999999.99',
  `status`      varchar(10)   NOT NULL COMMENT 'on_sale / frozen（本表不落 off_sale）',
  `freeze_by`   varchar(10)   DEFAULT NULL COMMENT 'manual / trade；on_sale 时为空',
  `create_at`   datetime      NOT NULL COMMENT '发布时间（UTC）',
  `update_at`   datetime      NOT NULL COMMENT '更新时间（UTC）',
  `trade_start` datetime      DEFAULT NULL COMMENT '交易开始时间（UTC），只记录不展示',
  `trade_end`   datetime      DEFAULT NULL COMMENT '交易结束时间（UTC），归档时必填',
  `result`      varchar(10)   DEFAULT NULL COMMENT 'sold / offline；本表恒为 NULL（不写入）',
  PRIMARY KEY (`id`),
  KEY `idx_goods_status` (`status`),
  KEY `idx_goods_create_at` (`create_at`),
  -- 纯物理性不变量：时间不倒流（保留）
  CONSTRAINT `ck_goods_time_order` CHECK (
      `trade_start` IS NULL OR `trade_end` IS NULL OR `trade_end` >= `trade_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='商品表：仅存当前商品，至多 1 行';


-- -----------------------------------------------------------------------------
-- 3 / 7　simpleshop_intentions（购买意向表）　　　　§4.2　·　字典 §9.5.1
--   fk_good_id 列宽必须与被引用主键完全一致（均 varchar(50)），否则建不了外键。
--   queue_order 全库唯一（由 simpleshop_queue_sequence 分配）；token 全局唯一。
--   INV-02（同一商品至多 1 条 trading）无「部分唯一索引」可用，由商品行锁保障（§8.2）。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_intentions` (
  `id`          varchar(50)  NOT NULL COMMENT '意向编号：I + 标准 UUID',
  `fk_good_id`  varchar(50)  NOT NULL COMMENT '所属商品 ID',
  `queue_order` int          NOT NULL COMMENT '排序序号，全库唯一，重排队时刷新',
  `create_at`   datetime     NOT NULL COMMENT '原始提交时间（UTC），重排队不变',
  `name`        varchar(50)  NOT NULL COMMENT '买家姓名，<=50 字符',
  `tel`         varchar(20)  NOT NULL COMMENT '联系电话，7~20 位数字与 -',
  `status`      varchar(10)  NOT NULL COMMENT 'queued/trading/succeeded/failed/revoked',
  `fail_type`   varchar(10)  DEFAULT NULL COMMENT 'sold_out/revoked/voided/requeued',
  `fail_reason` varchar(300) DEFAULT NULL COMMENT '失败备注，<=300 字符',
  `token`       varchar(20)  NOT NULL COMMENT '口令码，12 位大写字母+数字，可反查',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_intention_queue_order` (`queue_order`),
  UNIQUE KEY `uk_intention_token` (`token`),
  KEY `idx_intention_goods_status_order` (`fk_good_id`, `status`, `queue_order`),
  KEY `idx_intention_create_at` (`create_at`),
  KEY `idx_intention_status` (`status`),
  CONSTRAINT `fkey_intentions_fk_good_id` FOREIGN KEY (`fk_good_id`)
      REFERENCES `simpleshop_goods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='购买意向表：仅存当前商品的意向队列';


-- -----------------------------------------------------------------------------
-- 4 / 7　simpleshop_goods_history（历史商品表）　　§4.3　·　字典 §9.6.1
--   与商品表 1:1 同名同类型，差异仅两处：status 恒为 off_sale、freeze_by 恒为 NULL。
--   与当前表的三处差异：trade_end / result 为 NOT NULL，trade_end 建索引（I11-15 倒序分页）。
--   不使用 DESC 索引：单列升序索引即可支撑倒序扫描（DEC-29）。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_goods_history` (
  `id`          varchar(50)  NOT NULL COMMENT '沿用原商品 ID，前缀仍为 G',
  `name`        varchar(50)  NOT NULL COMMENT '继承原值',
  `description` varchar(500) DEFAULT NULL COMMENT '继承原值',
  `pic_url`     varchar(512) DEFAULT NULL COMMENT '继承原值；图片文件不搬迁',
  `price`       decimal(8,2) NOT NULL COMMENT '继承原值',
  `status`      varchar(10)  NOT NULL COMMENT '归档时恒写 off_sale',
  `freeze_by`   varchar(10)  DEFAULT NULL COMMENT '归档时恒清空',
  `create_at`   datetime     NOT NULL COMMENT '发布时间（UTC），继承原值',
  `update_at`   datetime     NOT NULL COMMENT '归档时间（UTC）',
  `trade_start` datetime     DEFAULT NULL COMMENT '交易开始时间（UTC），手动下架时为空',
  `trade_end`   datetime     NOT NULL COMMENT '交易结束时间（UTC），历史列表展示此字段',
  `result`      varchar(10)  NOT NULL COMMENT 'sold / offline',
  PRIMARY KEY (`id`),
  KEY `idx_goods_history_trade_end` (`trade_end`),
  KEY `idx_goods_history_create_at` (`create_at`),
  -- 纯物理性不变量：归档时间不早于发布时间，也不早于交易开始时间（保留）
  CONSTRAINT `ck_goods_history_time_order` CHECK (
      `trade_end` >= `create_at`
   AND (`trade_start` IS NULL OR `trade_end` >= `trade_start`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='历史商品表：已下架商品归档，永久保留';


-- -----------------------------------------------------------------------------
-- 5 / 7　simpleshop_intentions_history（历史意向表）　§4.4　·　字典 §9.6.2
--   ⚠️ 与购买意向表不是 1:1 复制：按 DEC-DB-05 本表**不含 queue_order、token** 两列。
--   差异共三处：① 外键列改名 fk_good_id -> fk_goods_history_id（值不变）；
--               ② 删除 queue_order；③ 删除 token。
--   归档口径：id/create_at/name/tel/status/fail_type/fail_reason 逐列复制，
--             fk_good_id -> fk_goods_history_id，queue_order 与 token 不复制。
--   归档时 status 必为终态（INV-05），该约束已按 DEC-DB-14 下沉应用层前置校验。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_intentions_history` (
  `id`                  varchar(50)  NOT NULL COMMENT '沿用原意向 ID，前缀仍为 I',
  `fk_goods_history_id` varchar(50)  NOT NULL COMMENT '所属历史商品 ID',
  `create_at`           datetime     NOT NULL COMMENT '原始提交时间（UTC），历史名单排序键',
  `name`                varchar(50)  NOT NULL COMMENT '继承原值',
  `tel`                 varchar(20)  NOT NULL COMMENT '继承原值',
  `status`              varchar(10)  NOT NULL COMMENT '归档时必为终态',
  `fail_type`           varchar(10)  DEFAULT NULL COMMENT '继承原值',
  `fail_reason`         varchar(300) DEFAULT NULL COMMENT '继承原值',
  PRIMARY KEY (`id`),
  KEY `idx_intention_history_goods` (`fk_goods_history_id`, `create_at`),
  KEY `idx_intention_history_status` (`status`),
  CONSTRAINT `fkey_intention_history_fk_goods_history_id` FOREIGN KEY (`fk_goods_history_id`)
      REFERENCES `simpleshop_goods_history` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='历史意向表：随商品归档的意向记录（不含 queue_order / token），永久保留';


-- -----------------------------------------------------------------------------
-- 6 / 7　simpleshop_trade_history（意向交易流水表）　§4.5　·　字典 §9.7
--   ⚠️ fk_intention_id 刻意**不建物理外键**（已决 9-N）：归档会移动意向行，
--      物理外键在归档事务内会瞬时失配，且 ON DELETE 语义会误删流水。
--      列宽仍取 varchar(50)，与被关联的意向主键一致。
--   本表只追加，不更新不删除（§10.4）；由仓储层不提供 delete/update 派生方法落实。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_trade_history` (
  `id`              varchar(50)  NOT NULL COMMENT '流水 ID：TH + 标准 UUID',
  `fk_intention_id` varchar(50)  NOT NULL COMMENT '意向编号（逻辑关联，不建物理外键）',
  `trade_start`     datetime     NOT NULL COMMENT '该次交易开始时间（UTC）',
  `trade_end`       datetime     NOT NULL COMMENT '该次交易结束时间（UTC）',
  `result`          varchar(10)  NOT NULL COMMENT 'sold / failed',
  `fail_type`       varchar(10)  DEFAULT NULL COMMENT 'failed 时必填（应用层保证）',
  `fail_reason`     varchar(300) DEFAULT NULL COMMENT '失败备注，<=300 字符',
  `create_at`       datetime     NOT NULL COMMENT '流水追加写入时间（UTC）',
  PRIMARY KEY (`id`),
  KEY `idx_trade_history_intention` (`fk_intention_id`, `trade_start`),
  KEY `idx_trade_history_create_at` (`create_at`),
  -- 纯物理性不变量：一次交易的时间不倒流（保留）
  CONSTRAINT `ck_trade_history_time_order` CHECK (`trade_end` >= `trade_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='意向交易流水表：只追加，不更新不删除';


-- -----------------------------------------------------------------------------
-- 7 / 7　simpleshop_queue_sequence（队列序号表 / 辅助表）　§4.7 · DEC-DB-03
--   为 queue_order 提供全库单调自增的序号来源；不承载业务数据、不被任何业务表引用。
--   单行表：id 固定为 1。current_value 只增不减。
--   ⚠️ 备份范围须包含本表（OPEN-02）：否则恢复后序号从 0 重来，会与既有
--      queue_order 冲突（触发 uk_intention_queue_order 唯一索引报错，属可发现的失败）。
--   本表初始行（id=1, current_value=0）由任务 6 的初始化脚本负责，用
--   INSERT ... ON DUPLICATE KEY UPDATE 保证幂等。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_queue_sequence` (
  `id`            tinyint NOT NULL COMMENT '固定为 1，单行表',
  `current_value` bigint  NOT NULL DEFAULT 0 COMMENT '已分配的最大 queue_order',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='队列序号辅助表：为 queue_order 提供全库单调自增序号';
