-- =============================================================================
-- simple-shop 阶段四 · 结构迁移（V4）
--
-- 四组变更，按本文顺序执行：
--   1. 买家账号删「默认交易地点」：地址改由地址簿维护，旧值不迁移；
--   2. 订单表加地址快照、支付、物流字段，状态由 6 态扩为 7 态；
--   3. 新增收货地址表；
--   4. 新增售后单、售后图片、退款流水三张表。
--
-- 迁移后共 16 张表。已发布的 V1 / V2 / V3 不得修改，本脚本只做追加。
--
-- 前置条件：订单表为空。本阶段沿用「按阶段重建数据库」的惯例，旧订单与旧会话
--   一律不保留、不做数据换算。若库中还留着历史订单，第 2 节的 ADD COLUMN NOT NULL
--   会直接失败（Invalid use of NULL value）——这是提醒先走重建流程，不要改成可空列
--   来绕过节流：订单没有收货人就没有履约对象，空值不是合法状态。
--
-- 落库时机：必须与阶段四的实体改造同批执行。本脚本删掉了 buyer_account.default_location，
--   而阶段三的 BuyerAccount 仍映射该列，单独执行 V4 会让应用启动时 ddl-auto=validate 直接失败。
--   「先改实体再落库」或「同一提交里一起上」，不要只跑脚本。
--
-- 为什么退款金额不进订单表：取消退款是整单的，归支付包的退款流水；售后退款是
--   单个订单项的，归售后单。订单详情展示的「已退款」是两处聚合出来的结果，
--   订单表再存一份就会变成第三处真相。
-- =============================================================================


-- =============================================================================
-- 1 / 4　买家账号：删除「默认交易地点」
--
--   必须先删 CHECK 再删列：约束引用了该列，直接 DROP COLUMN 会被拒绝。
--   phone 的格式约束仍在应用层，库内只保证非空。
-- =============================================================================

ALTER TABLE `simpleshop_buyer_account`
  DROP CHECK `ck_buyer_default_location_not_empty`;

ALTER TABLE `simpleshop_buyer_account`
  DROP COLUMN `default_location`,
  COMMENT = '买家账号表：用户名唯一，手机号不唯一；收货地址见 simpleshop_address';


-- =============================================================================
-- 2 / 4　订单表：地址快照、支付信息、物流信息、七态
--
--   地址存快照而不是引用地址簿：地址簿可改可删，订单必须留住下单当时那一份。
--   支付渠道与支付时间成对出现，要么都空（未支付），要么都有值。
--   物流三项只在发货时写入；单号用多行文本装多个单号，不拆表、不做节点。
--   状态收敛为七态：PENDING_PAYMENT 承接下单，PAID 承接付款，PENDING_CONFIRM 退场。
-- =============================================================================

ALTER TABLE `simpleshop_order`
  ADD COLUMN `receiver_name`    varchar(50)  NOT NULL
      COMMENT '收货人快照，下单时写入' AFTER `fk_buyer_id`,
  ADD COLUMN `receiver_phone`   varchar(20)  NOT NULL
      COMMENT '收货手机号快照，下单时写入' AFTER `receiver_name`,
  ADD COLUMN `receiver_address` varchar(200) NOT NULL
      COMMENT '收货详细地址快照（一行），下单时写入' AFTER `receiver_phone`,
  ADD COLUMN `payment_channel`  varchar(20)  DEFAULT NULL
      COMMENT '支付渠道：BANK_CARD / CREDIT_CARD / ALIPAY / WECHAT；未支付为空' AFTER `total_amount`,
  ADD COLUMN `paid_at`          datetime     DEFAULT NULL
      COMMENT '支付成功时间（UTC）；未支付为空' AFTER `payment_channel`,
  ADD COLUMN `carrier`          varchar(50)  DEFAULT NULL
      COMMENT '承运商名称，前端固定清单，后端只存字符串' AFTER `status`,
  ADD COLUMN `tracking_no`      varchar(500) DEFAULT NULL
      COMMENT '物流单号，多行文本，可含多个单号' AFTER `carrier`,
  ADD COLUMN `logistics_remark` varchar(500) DEFAULT NULL
      COMMENT '物流备注，特殊状态或补充说明' AFTER `tracking_no`;

-- 旧 CHECK 只认六态，必须先删才能写入 PENDING_PAYMENT。
ALTER TABLE `simpleshop_order`
  DROP CHECK `ck_order_status`;

ALTER TABLE `simpleshop_order`
  MODIFY COLUMN `status` varchar(20) NOT NULL
      COMMENT 'PENDING_PAYMENT / PAID / CONFIRMED / PREPARED / SHIPPED / COMPLETED / CANCELED';

ALTER TABLE `simpleshop_order`
  ADD CONSTRAINT `ck_order_status` CHECK (`status` IN (
      'PENDING_PAYMENT', 'PAID', 'CONFIRMED', 'PREPARED', 'SHIPPED', 'COMPLETED', 'CANCELED')),
  -- 支付渠道与支付时间同生同灭：只写一半会让「是否已支付」出现两种判断口径。
  ADD CONSTRAINT `ck_order_paid_pair` CHECK (
      (`payment_channel` IS NULL AND `paid_at` IS NULL)
   OR (`payment_channel` IS NOT NULL AND `paid_at` IS NOT NULL)),
  -- 发货之后必须有承运商与单号，否则买家查不到物流。
  ADD CONSTRAINT `ck_order_shipped_logistics` CHECK (
      `status` NOT IN ('SHIPPED', 'COMPLETED')
   OR (`carrier` IS NOT NULL AND `tracking_no` IS NOT NULL)),
  ADD CONSTRAINT `ck_order_receiver_complete` CHECK (
      `receiver_name` <> '' AND `receiver_phone` <> '' AND `receiver_address` <> ''),
  COMMENT = '订单表：七态整单流转；地址为下单快照，支付与物流字段随状态写入；不存退款金额';


-- =============================================================================
-- 3 / 4　收货地址
--
--   字段只有三项：收货人、手机号、一行详细地址。不做省市区与邮编。
--   default_key 是生成列：默认地址记 1、非默认记 NULL。唯一索引
--   (fk_buyer_id, default_key) 因此允许同一买家有多条非默认地址、但最多一条默认，
--   「同一买家只有一个默认」由数据库兜底，不必只靠服务层在事务里保证。
--   idx_address_buyer_create 供「删除默认后取最近创建的一条补位」使用。
-- =============================================================================

CREATE TABLE `simpleshop_address` (
  `id`              varchar(50)  NOT NULL COMMENT '地址 ID：AD + UUID',
  `fk_buyer_id`     varchar(50)  NOT NULL COMMENT '所属买家账号 ID',
  `receiver_name`   varchar(50)  NOT NULL COMMENT '收货人，<=50 字符',
  `receiver_phone`  varchar(20)  NOT NULL COMMENT '收货手机号，7~20 位数字与 -，格式由应用层校验',
  `detail_address`  varchar(200) NOT NULL COMMENT '详细地址，一行，<=200 字符',
  `is_default`      tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否默认地址：1 是、0 否',
  `default_key`     tinyint      GENERATED ALWAYS AS (IF(`is_default` = 1, 1, NULL)) STORED
      COMMENT '默认地址标记：默认记 1，非默认记 NULL；与买家 ID 组成唯一键',
  `create_at`       datetime     NOT NULL COMMENT '创建时间（UTC）',
  `update_at`       datetime     NOT NULL COMMENT '最后修改时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_address_buyer_default` (`fk_buyer_id`, `default_key`),
  KEY `idx_address_buyer_create` (`fk_buyer_id`, `create_at`),
  CONSTRAINT `ck_address_is_default` CHECK (`is_default` IN (0, 1)),
  CONSTRAINT `ck_address_receiver_name` CHECK (`receiver_name` <> ''),
  CONSTRAINT `ck_address_receiver_phone` CHECK (`receiver_phone` <> ''),
  CONSTRAINT `ck_address_detail` CHECK (`detail_address` <> ''),
  CONSTRAINT `ck_address_time_order` CHECK (`update_at` >= `create_at`),
  CONSTRAINT `fkey_address_buyer` FOREIGN KEY (`fk_buyer_id`)
      REFERENCES `simpleshop_buyer_account` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='收货地址表：每买家最多一条默认地址，由唯一索引保证';


-- =============================================================================
-- 4 / 4　售后单、售后图片、退款流水
--
--   4.1 售后单：一张单对应一个订单项（不是商品），整行全退，不记退货数量。
--       三态 PENDING → COMPLETED / CANCELED；取消与拒绝同义，只有卖家能改。
--       result 只在 COMPLETED 时有值；退款类结果必须带金额与退款时间。
--       refund_key 是生成列：退款类结果记 1、其余记 NULL。唯一索引
--       (fk_order_item_id, refund_key) 保证「同一订单项最多一条已产生退款的售后单」，
--       换货与已取消不占额度，因此不阻挡后续再申请。
--       库只约束「已产生退款」这一条；同一订单项能不能同时挂着多条待处理的售后单，
--       需求没有规定，当前不拦，由服务层在提交申请时自行决定。
--       fk_buyer_id 冗余自订单：买家「我的售后」直接按买家分页，不必每次 join 订单。
--
--   4.2 售后图片：只存 URL 与顺序，上限 9 张由应用层校验；不做图片内容副本。
--
--   4.3 退款流水：只记取消已支付订单的整单退款。售后退款记在售后单里，不进本表，
--       否则订单详情的「已退款」会把同一笔钱算两遍。
--       uk_payment_refund_order_reason 是取消退款幂等的最后一道保险：同一订单
--       同一原因只退一次，重复提交取消时撞键即视为已退。
--       注：支付渠道不上库级 CHECK——渠道是可增的业务配置，加 CHECK 会把新增渠道
--       变成一次迁移；渠道合法性由支付包在入口处校验。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 4.1 / 3　售后单
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_aftersale` (
  `id`               varchar(50)   NOT NULL COMMENT '售后单 ID：AS + UUID',
  `aftersale_no`     varchar(32)   NOT NULL COMMENT '售后单号：AS + yyyyMMdd(UTC) + 4 位每日序列，唯一',
  `fk_order_id`      varchar(50)   NOT NULL COMMENT '所属订单 ID',
  `fk_order_item_id` varchar(50)   NOT NULL COMMENT '对应的订单项 ID；一张单只对应一个订单项',
  `fk_buyer_id`      varchar(50)   NOT NULL COMMENT '申请买家账号 ID，冗余自订单供买家列表分页',
  `title`            varchar(100)  NOT NULL COMMENT '申请标题，<=100 字符',
  `description`      varchar(1000) NOT NULL COMMENT '问题描述，<=1000 字符',
  `status`           varchar(20)   NOT NULL COMMENT 'PENDING / COMPLETED / CANCELED',
  `result`           varchar(30)   DEFAULT NULL
      COMMENT '处理结果：RETURN_AND_REFUND / REFUND_ONLY / EXCHANGE；未完成时为空',
  `refund_amount`    decimal(12,2) DEFAULT NULL COMMENT '退款金额 = 订单项单价 × 数量，服务端计算',
  `refunded_at`      datetime      DEFAULT NULL COMMENT '退款时间（UTC）',
  `refund_key`       tinyint       GENERATED ALWAYS AS (
      IF(`result` IN ('RETURN_AND_REFUND', 'REFUND_ONLY'), 1, NULL)) STORED
      COMMENT '已产生退款标记：退款类结果记 1，其余记 NULL；与订单项 ID 组成唯一键',
  `create_at`        datetime      NOT NULL COMMENT '申请时间（UTC）',
  `update_at`        datetime      NOT NULL COMMENT '最后处理时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aftersale_no` (`aftersale_no`),
  UNIQUE KEY `uk_aftersale_item_refund` (`fk_order_item_id`, `refund_key`),
  KEY `idx_aftersale_buyer_create` (`fk_buyer_id`, `create_at`),
  KEY `idx_aftersale_status_create` (`status`, `create_at`),
  KEY `idx_aftersale_order` (`fk_order_id`),
  CONSTRAINT `ck_aftersale_no_not_blank` CHECK (`aftersale_no` <> ''),
  CONSTRAINT `ck_aftersale_title` CHECK (`title` <> ''),
  CONSTRAINT `ck_aftersale_description` CHECK (`description` <> ''),
  CONSTRAINT `ck_aftersale_status` CHECK (`status` IN ('PENDING', 'COMPLETED', 'CANCELED')),
  CONSTRAINT `ck_aftersale_result` CHECK (
      `result` IS NULL
   OR `result` IN ('RETURN_AND_REFUND', 'REFUND_ONLY', 'EXCHANGE')),
  -- 完成必须带结果；未完成的单不允许残留结果与退款字段。
  CONSTRAINT `ck_aftersale_completion` CHECK (
      (`status` = 'COMPLETED' AND `result` IS NOT NULL)
   OR (`status` <> 'COMPLETED'
       AND `result` IS NULL AND `refund_amount` IS NULL AND `refunded_at` IS NULL)),
  -- 退款类结果必须落金额与时间；换货一分钱不退。
  -- 金额判断必须写成 IS NOT NULL AND > 0：只写 refund_amount > 0 时，金额为 NULL
  -- 会让整个表达式变成 NULL，而 MySQL 的 CHECK 对 NULL 视为通过，这里会漏掉「结果
  -- 是仅退款、金额却没填」这种最该拦住的情况。
  CONSTRAINT `ck_aftersale_refund_pair` CHECK (
      (`result` IN ('RETURN_AND_REFUND', 'REFUND_ONLY')
       AND `refund_amount` IS NOT NULL AND `refund_amount` > 0
       AND `refunded_at` IS NOT NULL)
   OR ((`result` IS NULL OR `result` = 'EXCHANGE')
       AND `refund_amount` IS NULL AND `refunded_at` IS NULL)),
  CONSTRAINT `ck_aftersale_time_order` CHECK (`update_at` >= `create_at`),
  CONSTRAINT `ck_aftersale_refunded_after_create` CHECK (
      `refunded_at` IS NULL OR `refunded_at` >= `create_at`),
  CONSTRAINT `fkey_aftersale_order` FOREIGN KEY (`fk_order_id`)
      REFERENCES `simpleshop_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fkey_aftersale_order_item` FOREIGN KEY (`fk_order_item_id`)
      REFERENCES `simpleshop_order_item` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fkey_aftersale_buyer` FOREIGN KEY (`fk_buyer_id`)
      REFERENCES `simpleshop_buyer_account` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='售后单表：三态，整行全退；退款类结果每个订单项最多一条';

-- -----------------------------------------------------------------------------
-- 4.2 / 3　售后图片
--   只存 URL 与排序，不复制文件；上限 9 张由应用层校验，库内只保证顺序唯一。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_aftersale_image` (
  `id`              varchar(50)  NOT NULL COMMENT '售后图片 ID：AI + UUID',
  `fk_aftersale_id` varchar(50)  NOT NULL COMMENT '所属售后单 ID',
  `url`             varchar(255) NOT NULL COMMENT '媒体相对 URL，指向 /media/aftersale/ 下已落盘的文件',
  `sort_no`         int          NOT NULL COMMENT '展示顺序，从 1 开始',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aftersale_image_sort` (`fk_aftersale_id`, `sort_no`),
  CONSTRAINT `ck_aftersale_image_sort` CHECK (`sort_no` > 0),
  CONSTRAINT `ck_aftersale_image_url` CHECK (`url` <> ''),
  CONSTRAINT `fkey_aftersale_image_aftersale` FOREIGN KEY (`fk_aftersale_id`)
      REFERENCES `simpleshop_aftersale` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='售后图片表：只存 URL 与顺序，不复制文件';

-- -----------------------------------------------------------------------------
-- 4.3 / 3　退款流水
--   order_no 是快照：支付包不读订单表，账要能独立看懂。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_payment_refund` (
  `id`          varchar(50)   NOT NULL COMMENT '退款流水 ID：RF + UUID',
  `fk_order_id` varchar(50)   NOT NULL COMMENT '订单 ID',
  `order_no`    varchar(32)   NOT NULL COMMENT '订单号快照，便于对账时不回查订单表',
  `channel`     varchar(20)   NOT NULL COMMENT '退款渠道，取订单原支付渠道',
  `amount`      decimal(12,2) NOT NULL COMMENT '退款金额：取消退款为订单总额',
  `reason`      varchar(20)   NOT NULL DEFAULT 'ORDER_CANCEL'
      COMMENT '退款原因：本期只有 ORDER_CANCEL，预留售后退款以外的场景',
  `refunded_at` datetime      NOT NULL COMMENT '退款时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_refund_order_reason` (`fk_order_id`, `reason`),
  CONSTRAINT `ck_payment_refund_order_no` CHECK (`order_no` <> ''),
  CONSTRAINT `ck_payment_refund_amount` CHECK (`amount` > 0),
  CONSTRAINT `ck_payment_refund_reason` CHECK (`reason` <> ''),
  CONSTRAINT `fkey_payment_refund_order` FOREIGN KEY (`fk_order_id`)
      REFERENCES `simpleshop_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='退款流水表：取消已支付订单的整单退款；售后退款记在售后单';
