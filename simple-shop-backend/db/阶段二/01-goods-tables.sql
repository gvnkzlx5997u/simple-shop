-- =============================================================================
-- simple-shop 阶段二 · 商品域建表脚本（半成品草案）
--
-- 状态：草案。未接入 Flyway，不创建数据库，也不会在启动时执行。
-- 范围：商品表、商品图片表、描述内容块表、历史商品表。
-- 类别表见 00-category-tables.sql；合并时按 00 → 01 顺序建表，
-- 商品表的 fk_category_id 才能引用二级类别。
-- 账号表见 02-account-tables.sql，意向/队列表见 03-intention-tables.sql；
-- 全部合并后再形成 src/main/resources/db/migration/V1__baseline_schema.sql。
--
-- 约定：
--   · 表名统一 simpleshop_ 前缀；主键为「前缀 + UUID」，varchar(50)。
--   · 时间列一律存 UTC；DDL 不做时区转换。
--   · MySQL >= 8.0.16（CHECK 约束真正生效）。
--   · 字符集 utf8mb4 / utf8mb4_unicode_ci。
--
-- 本脚本落实的口径：
--   1. 商品描述是「纯文本 + 图片 + 视频」的有序内容块，不是 HTML。
--   2. 纯文本允许换行；整段描述文本共 1000 字符的上限由应用层校验，
--      单个 TEXT 块的列宽也按 1000 预留。
--   3. 商品图片（主图/图集）与描述媒体是两套不同的限制：
--      商品图片只允许图片；描述媒体允许图片和视频。
--      当前商品图片 5MB、描述图片 5MB、描述视频 20MB，均由应用配置控制，
--      不写死在 DDL 中，后续可分别调整。
--   4. 商品图片最多 1 张主图 + 8 张图集；主图唯一与 sort_no 唯一由本脚本保证，
--      张数上限由应用层校验。
--   5. 描述中的图片/视频数量不设上限。
--   6. 归档时商品描述与商品图片均存 JSON 快照；媒体文件留在原目录，不搬迁。
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1 / 4　商品表
--   只存当前商品；已归档商品不在本表。
--   fk_category_id 引用 00-category-tables.sql 的二级类别表。
--   status 取值：ON_SALE / FROZEN / OFF_MANUAL / OFF_ZERO。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_goods` (
  `id`             varchar(50)  NOT NULL COMMENT '商品 ID：G + UUID',
  `name`           varchar(50)  NOT NULL COMMENT '名称，<=50 字符',
  `price`          decimal(8,2) NOT NULL COMMENT '价格，0 < price <= 999999.99',
  `stock`          int          NOT NULL COMMENT '库存，>=0；成交时扣减',
  `fk_category_id` varchar(50)  NOT NULL COMMENT '所属二级类别 ID，引用 simpleshop_category_l2',
  `status`         varchar(20)  NOT NULL COMMENT 'ON_SALE / FROZEN / OFF_MANUAL / OFF_ZERO',
  `create_at`       datetime     NOT NULL COMMENT '发布时间（UTC）',
  `update_at`       datetime     NOT NULL COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  KEY `idx_goods_status_category_create` (`status`, `fk_category_id`, `create_at`),
  KEY `idx_goods_category` (`fk_category_id`),
  KEY `idx_goods_create_at` (`create_at`),
  CONSTRAINT `ck_goods_price` CHECK (`price` > 0),
  CONSTRAINT `ck_goods_stock` CHECK (`stock` >= 0),
  CONSTRAINT `ck_goods_status` CHECK (`status` IN ('ON_SALE', 'FROZEN', 'OFF_MANUAL', 'OFF_ZERO')),
  CONSTRAINT `fkey_goods_category` FOREIGN KEY (`fk_category_id`)
      REFERENCES `simpleshop_category_l2` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='商品表：当前在售/交易中/下架商品，不含已归档商品';


-- -----------------------------------------------------------------------------
-- 2 / 4　商品图片表（主图 + 图集）
--   只允许图片；商品图与描述媒体是不同限制，故不共用描述内容块表。
--   is_main 标记主图；main_flag 生成列 + 唯一索引保证每件商品至多一张主图。
--   MySQL 唯一索引允许多个 NULL，因此非主图行不会互相冲突。
--   图集最多 8 张、有图集必须有主图，由应用层校验。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_goods_image` (
  `id`           varchar(50)  NOT NULL COMMENT '图片 ID：GI + UUID',
  `fk_goods_id`   varchar(50)  NOT NULL COMMENT '所属商品 ID',
  `url`          varchar(512) NOT NULL COMMENT '相对 URL，服务端生成',
  `is_main`      tinyint      NOT NULL DEFAULT 0 COMMENT '是否主图：1 是，0 否',
  `sort_no`      int          NOT NULL COMMENT '排序号，从 1 开始，同一商品内唯一',
  `create_at`     datetime     NOT NULL COMMENT '创建时间（UTC）',
  `main_flag`    tinyint GENERATED ALWAYS AS (CASE WHEN `is_main` = 1 THEN 1 ELSE NULL END) STORED
      COMMENT '主图唯一性辅助列：主图为 1，非主图为 NULL',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_goods_image_sort` (`fk_goods_id`, `sort_no`),
  UNIQUE KEY `uk_goods_image_main` (`fk_goods_id`, `main_flag`),
  CONSTRAINT `ck_goods_image_is_main` CHECK (`is_main` IN (0, 1)),
  CONSTRAINT `ck_goods_image_sort_no` CHECK (`sort_no` > 0),
  CONSTRAINT `fkey_goods_image_goods` FOREIGN KEY (`fk_goods_id`)
      REFERENCES `simpleshop_goods` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='商品图片表：主图 + 图集，只允许图片';


-- -----------------------------------------------------------------------------
-- 3 / 4　商品描述内容块表
--   描述是 TEXT / IMAGE / VIDEO 的有序块列表，不存 HTML。
--   TEXT 块写 text_content、media_url 为空；IMAGE/VIDEO 块写 media_url、text_content 为空。
--   纯文本允许换行；整段文本总长度 1000 字符由应用层校验，单块列宽按 1000 预留。
--   描述媒体数量不限；图片 5MB、视频 20MB 等限制由应用配置控制。
--   sort_no 在同一商品内唯一，决定图文/视频的混排顺序。
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_goods_content_block` (
  `id`          varchar(50)  NOT NULL COMMENT '块 ID：GB + UUID',
  `fk_goods_id`  varchar(50)  NOT NULL COMMENT '所属商品 ID',
  `block_type`   varchar(10)  NOT NULL COMMENT 'TEXT / IMAGE / VIDEO',
  `sort_no`      int          NOT NULL COMMENT '排序号，从 1 开始，同一商品内唯一',
  `text_content` varchar(1000) DEFAULT NULL COMMENT 'TEXT 块的纯文本，允许换行，单块 <=1000 字符',
  `media_url`    varchar(512) DEFAULT NULL COMMENT 'IMAGE / VIDEO 块的相对 URL',
  `create_at`     datetime     NOT NULL COMMENT '创建时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_goods_content_block_sort` (`fk_goods_id`, `sort_no`),
  CONSTRAINT `ck_goods_content_block_type` CHECK (`block_type` IN ('TEXT', 'IMAGE', 'VIDEO')),
  CONSTRAINT `ck_goods_content_block_sort_no` CHECK (`sort_no` > 0),
  CONSTRAINT `ck_goods_content_block_shape` CHECK (
      (`block_type` = 'TEXT' AND `text_content` IS NOT NULL AND `media_url` IS NULL)
   OR (`block_type` IN ('IMAGE', 'VIDEO') AND `text_content` IS NULL AND `media_url` IS NOT NULL)
  ),
  CONSTRAINT `fkey_goods_content_block_goods` FOREIGN KEY (`fk_goods_id`)
      REFERENCES `simpleshop_goods` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='商品描述内容块表：纯文本、图片、视频按 sort_no 混排';


-- -----------------------------------------------------------------------------
-- 4 / 4　历史商品表
--   沿用原商品 ID，永久保留；当前表归档后删除对应商品。
--   描述与图片均存 JSON 快照，历史查询不再依赖当前商品/图片/描述表。
--   媒体文件保留在原目录，不随归档搬迁；快照中存相对 URL。
--   description_snapshot 形如：
--     [{"type":"text","text":"..."},
--      {"type":"image","url":"/media/description/..."},
--      {"type":"video","url":"/media/description/..."}]
--   images_snapshot 形如：
--     [{"url":"/media/goods/...","is_main":true,"sort_no":1},
--      {"url":"/media/goods/...","is_main":false,"sort_no":2}]
-- -----------------------------------------------------------------------------
CREATE TABLE `simpleshop_goods_history` (
  `id`                  varchar(50)  NOT NULL COMMENT '沿用原商品 ID：G + UUID',
  `name`                varchar(50)  NOT NULL COMMENT '继承原值',
  `price`               decimal(8,2) NOT NULL COMMENT '继承原值',
  `stock`               int          NOT NULL COMMENT '归档时最终库存，成交售罄为 0',
  `category_l1_name`    varchar(50)  NOT NULL COMMENT '归档时一级类别名快照',
  `category_l2_name`    varchar(50)  NOT NULL COMMENT '归档时二级类别名快照',
  `result`              varchar(20)  NOT NULL COMMENT '归档结果：SOLD_OUT',
  `description_snapshot` JSON         NOT NULL COMMENT '描述内容块 JSON 快照',
  `images_snapshot`      JSON         NOT NULL COMMENT '商品图片 JSON 快照',
  `create_at`            datetime     NOT NULL COMMENT '发布时间（UTC），继承原值',
  `archived_at`          datetime     NOT NULL COMMENT '归档时间（UTC）',
  PRIMARY KEY (`id`),
  KEY `idx_goods_history_archived_at` (`archived_at`),
  KEY `idx_goods_history_create_at` (`create_at`),
  CONSTRAINT `ck_goods_history_price` CHECK (`price` > 0),
  CONSTRAINT `ck_goods_history_stock` CHECK (`stock` >= 0),
  CONSTRAINT `ck_goods_history_result` CHECK (`result` = 'SOLD_OUT'),
  CONSTRAINT `ck_goods_history_time_order` CHECK (`archived_at` >= `create_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='历史商品表：成交售罄归档，描述与图片存 JSON 快照';
