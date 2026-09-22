-- ============================================================================
-- 基于大模型导购的数码电商平台 · 数据库设计脚本（MySQL）
-- 数据库：digital_mall     字符集：utf8mb4     引擎：InnoDB
-- 适用：MySQL 5.7+ / 8.0
-- ----------------------------------------------------------------------------
-- 设计约定：
--   1. 主键统一为 BIGINT UNSIGNED AUTO_INCREMENT
--   2. 外键采用【逻辑外键】（只建普通索引、不建物理外键约束），
--      便于数据灵活管理与统计，表间关联关系见《数据库ER图》
--   3. 金额统一 DECIMAL(10,2)；状态统一 TINYINT；时间统一 DATETIME
--   4. created_time / updated_time 由数据库自动维护
--
-- 大模型导购存储方案（MySQL + Redis 混合）：
--   · 商品结构化数据 + 关键词/标签  → 本库（product.keywords / product.tags）
--   · 商品向量                        → Redis，key: product:embedding:{productId}
--   · 多轮对话历史                    → chat_session / chat_message 表
--   · 会话上下文临时缓存              → Redis，key: chat:context:{sessionId}，TTL 30分钟
--   · 热门商品缓存                    → Redis，key: product:hot（ZSet 按销量）
--   · 登录令牌 / 短信验证码           → Redis，key: login:token:{userId} / sms:code:{phone}
--
-- 说明：本脚本为幂等写法（建库/建表均 IF NOT EXISTS），可重复执行；
--       如需重建某张表，请先手动 DROP TABLE 该表。
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `digital_mall` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `digital_mall`;

-- ============================================================================
-- 一、用户与账号体系
-- ============================================================================

-- 1. 系统用户表：统一账号体系（普通用户 / 商家 / 管理员，role 区分）
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id`             BIGINT UNSIGNED AUTO_INCREMENT COMMENT '用户ID',
  `username`       VARCHAR(50)  NOT NULL COMMENT '登录账号',
  `password`       VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密）',
  `nickname`       VARCHAR(50)           COMMENT '昵称',
  `avatar`         VARCHAR(255)          COMMENT '头像URL',
  `phone`          VARCHAR(20)           COMMENT '手机号',
  `email`          VARCHAR(100)          COMMENT '邮箱',
  `gender`         TINYINT      NOT NULL DEFAULT 0 COMMENT '性别：0未知 1男 2女',
  `role`           TINYINT      NOT NULL DEFAULT 0 COMMENT '角色：0普通用户 1商家 2管理员',
  `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1正常',
  `last_login_time` DATETIME             COMMENT '最后登录时间',
  `last_login_ip`  VARCHAR(50)           COMMENT '最后登录IP',
  `created_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `updated_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_phone` (`phone`),
  KEY `idx_role_status` (`role`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表（统一账号体系）';

-- 2. 收货地址表
CREATE TABLE IF NOT EXISTS `user_address` (
  `id`             BIGINT UNSIGNED AUTO_INCREMENT COMMENT '地址ID',
  `user_id`        BIGINT UNSIGNED NOT NULL COMMENT '用户ID（→sys_user.id）',
  `receiver_name`  VARCHAR(50)  NOT NULL COMMENT '收货人姓名',
  `receiver_phone` VARCHAR(20)  NOT NULL COMMENT '收货人电话',
  `province`       VARCHAR(50)           COMMENT '省',
  `city`           VARCHAR(50)           COMMENT '市',
  `district`       VARCHAR(50)           COMMENT '区/县',
  `detail_address` VARCHAR(200) NOT NULL COMMENT '详细地址',
  `is_default`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认：0否 1是',
  `created_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货地址表';

-- 3. 商品收藏表
CREATE TABLE IF NOT EXISTS `user_favorite` (
  `id`           BIGINT UNSIGNED AUTO_INCREMENT COMMENT '收藏ID',
  `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '用户ID（→sys_user.id）',
  `product_id`   BIGINT UNSIGNED NOT NULL COMMENT '商品ID（→product.id）',
  `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品收藏表';

-- 4. 商家入驻申请表
CREATE TABLE IF NOT EXISTS `merchant_apply` (
  `id`               BIGINT UNSIGNED AUTO_INCREMENT COMMENT '申请ID',
  `user_id`          BIGINT UNSIGNED NOT NULL COMMENT '申请人用户ID（→sys_user.id）',
  `real_name`        VARCHAR(50)  NOT NULL COMMENT '真实姓名',
  `id_card`          VARCHAR(30)           COMMENT '身份证号',
  `contact_phone`    VARCHAR(20)  NOT NULL COMMENT '联系电话',
  `shop_name`        VARCHAR(50)  NOT NULL COMMENT '拟开设店铺名称',
  `shop_intro`       VARCHAR(500)          COMMENT '店铺简介',
  `qualification_url` VARCHAR(255)         COMMENT '资质材料文件URL',
  `status`           TINYINT      NOT NULL DEFAULT 0 COMMENT '审核状态：0待审核 1通过 2拒绝',
  `reject_reason`    VARCHAR(200)          COMMENT '拒绝原因',
  `audit_by`         BIGINT UNSIGNED       COMMENT '审核管理员ID（→sys_user.id）',
  `audit_time`       DATETIME              COMMENT '审核时间',
  `created_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `updated_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家入驻申请表';

-- 5. 店铺表（商家入驻审核通过后自动开通）
CREATE TABLE IF NOT EXISTS `shop` (
  `id`           BIGINT UNSIGNED AUTO_INCREMENT COMMENT '店铺ID',
  `merchant_id`  BIGINT UNSIGNED NOT NULL COMMENT '商家用户ID（→sys_user.id）',
  `shop_name`    VARCHAR(50)  NOT NULL COMMENT '店铺名称',
  `shop_logo`    VARCHAR(255)          COMMENT '店铺Logo',
  `shop_banner`  VARCHAR(255)          COMMENT '店铺横幅图',
  `shop_intro`   VARCHAR(500)          COMMENT '店铺简介',
  `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0冻结 1正常',
  `created_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_id` (`merchant_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺表';

-- ============================================================================
-- 二、商品中心
-- ============================================================================

-- 6. 商品分类表（支持多级：parent_id 自关联，0 为顶级分类）
CREATE TABLE IF NOT EXISTS `category` (
  `id`            BIGINT UNSIGNED AUTO_INCREMENT COMMENT '分类ID',
  `parent_id`     BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父分类ID（0为顶级）',
  `category_name` VARCHAR(50) NOT NULL COMMENT '分类名称',
  `icon`          VARCHAR(255)         COMMENT '分类图标URL',
  `sort_order`    INT         NOT NULL DEFAULT 0 COMMENT '排序号（越小越靠前）',
  `status`        TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：0停用 1启用',
  `created_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表（多级）';

-- 7. 品牌表
CREATE TABLE IF NOT EXISTS `brand` (
  `id`          BIGINT UNSIGNED AUTO_INCREMENT COMMENT '品牌ID',
  `brand_name`  VARCHAR(50) NOT NULL COMMENT '品牌名称',
  `brand_logo`  VARCHAR(255)         COMMENT '品牌Logo',
  `brand_desc`  VARCHAR(500)         COMMENT '品牌简介',
  `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：0停用 1启用',
  `created_time` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_brand_name` (`brand_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品牌表';

-- 8. 商品表
CREATE TABLE IF NOT EXISTS `product` (
  `id`             BIGINT UNSIGNED AUTO_INCREMENT COMMENT '商品ID',
  `shop_id`        BIGINT UNSIGNED NOT NULL COMMENT '所属店铺ID（→shop.id）',
  `category_id`    BIGINT UNSIGNED NOT NULL COMMENT '分类ID（→category.id）',
  `brand_id`       BIGINT UNSIGNED          COMMENT '品牌ID（→brand.id）',
  `product_name`   VARCHAR(200) NOT NULL COMMENT '商品名称',
  `product_title`  VARCHAR(300)          COMMENT '商品卖点标题',
  `main_image`     VARCHAR(255)          COMMENT '商品主图URL',
  `keywords`       VARCHAR(500)          COMMENT '检索关键词（逗号分隔，供关键词搜索与大模型向量化）',
  `tags`           VARCHAR(500)          COMMENT '标签（如：旗舰机、学生党、性价比）',
  `brief`          VARCHAR(500)          COMMENT '商品简介',
  `price`          DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '售价',
  `original_price` DECIMAL(10,2)          COMMENT '原价/划线价',
  `stock`          INT          NOT NULL DEFAULT 0 COMMENT '总库存',
  `sales`          INT          NOT NULL DEFAULT 0 COMMENT '销量',
  `view_count`     INT          NOT NULL DEFAULT 0 COMMENT '浏览量（经营统计用）',
  `status`         TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0草稿 1上架 2下架',
  `is_recommend`   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否推荐：0否 1是',
  `shelf_time`     DATETIME               COMMENT '上架时间',
  `created_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_shop_id` (`shop_id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_brand_id` (`brand_id`),
  KEY `idx_price` (`price`),
  KEY `idx_sales` (`sales`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- 9. 商品SKU表（规格维度的价格与库存：如 颜色/内存版本）
CREATE TABLE IF NOT EXISTS `product_sku` (
  `id`           BIGINT UNSIGNED AUTO_INCREMENT COMMENT 'SKU ID',
  `product_id`   BIGINT UNSIGNED NOT NULL COMMENT '商品ID（→product.id）',
  `sku_name`     VARCHAR(200) NOT NULL COMMENT '规格名（如：曜石黑/12GB+256GB）',
  `sku_image`    VARCHAR(255)          COMMENT '规格图片URL',
  `price`        DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT 'SKU售价',
  `stock`        INT          NOT NULL DEFAULT 0 COMMENT 'SKU库存',
  `sales`        INT          NOT NULL DEFAULT 0 COMMENT 'SKU销量',
  `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0停用 1启用',
  `created_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SKU表（规格/库存/价格）';

-- 10. 商品参数属性表（键值对，用于详情展示与参数筛选）
CREATE TABLE IF NOT EXISTS `product_attr` (
  `id`          BIGINT UNSIGNED AUTO_INCREMENT COMMENT '属性ID',
  `product_id`  BIGINT UNSIGNED NOT NULL COMMENT '商品ID（→product.id）',
  `attr_name`   VARCHAR(50)  NOT NULL COMMENT '参数名（如：屏幕/处理器/电池）',
  `attr_value`  VARCHAR(500) NOT NULL COMMENT '参数值',
  `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '排序号',
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品参数属性表（键值对）';

-- 11. 商品图片表（轮播图/详情图）
CREATE TABLE IF NOT EXISTS `product_image` (
  `id`          BIGINT UNSIGNED AUTO_INCREMENT COMMENT '图片ID',
  `product_id`  BIGINT UNSIGNED NOT NULL COMMENT '商品ID（→product.id）',
  `image_url`   VARCHAR(255) NOT NULL COMMENT '图片URL',
  `image_type`  TINYINT      NOT NULL DEFAULT 0 COMMENT '类型：0轮播图 1详情图',
  `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '排序号',
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品图片表';

-- ============================================================================
-- 三、交易中心
-- ============================================================================

-- 12. 购物车表
CREATE TABLE IF NOT EXISTS `cart` (
  `id`           BIGINT UNSIGNED AUTO_INCREMENT COMMENT '购物车项ID',
  `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '用户ID（→sys_user.id）',
  `sku_id`       BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID（→product_sku.id）',
  `quantity`     INT          NOT NULL DEFAULT 1 COMMENT '数量',
  `is_selected`  TINYINT      NOT NULL DEFAULT 1 COMMENT '是否勾选结算：0否 1是',
  `created_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_sku` (`user_id`, `sku_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';

-- 13. 订单表（order 为保留字，需反引号）
CREATE TABLE IF NOT EXISTS `order` (
  `id`               BIGINT UNSIGNED AUTO_INCREMENT COMMENT '订单ID',
  `order_no`         VARCHAR(32)  NOT NULL COMMENT '订单号',
  `user_id`          BIGINT UNSIGNED NOT NULL COMMENT '下单用户ID（→sys_user.id）',
  `shop_id`          BIGINT UNSIGNED NOT NULL COMMENT '店铺ID（→shop.id）',
  `total_amount`     DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '商品总金额',
  `freight_amount`   DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '运费',
  `pay_amount`       DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '实付金额',
  `receiver_name`    VARCHAR(50)  NOT NULL COMMENT '收货人姓名（下单快照）',
  `receiver_phone`   VARCHAR(20)  NOT NULL COMMENT '收货人电话（快照）',
  `receiver_address` VARCHAR(300) NOT NULL COMMENT '收货地址（快照）',
  `status`           TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0待付款 1待发货 2待收货 3已完成 4已取消 5售后中',
  `pay_type`         TINYINT               COMMENT '支付方式：0模拟支付',
  `pay_time`         DATETIME              COMMENT '支付时间',
  `delivery_time`    DATETIME              COMMENT '发货时间',
  `finish_time`      DATETIME              COMMENT '完成时间',
  `remark`           VARCHAR(500)          COMMENT '用户备注',
  `created_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `updated_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_shop_id` (`shop_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 14. 订单明细表（下单时冗余快照商品信息）
CREATE TABLE IF NOT EXISTS `order_item` (
  `id`            BIGINT UNSIGNED AUTO_INCREMENT COMMENT '明细ID',
  `order_id`      BIGINT UNSIGNED NOT NULL COMMENT '订单ID（→order.id）',
  `product_id`    BIGINT UNSIGNED NOT NULL COMMENT '商品ID（→product.id）',
  `sku_id`        BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID（→product_sku.id）',
  `product_name`  VARCHAR(200) NOT NULL COMMENT '商品名称（快照）',
  `sku_name`      VARCHAR(200)          COMMENT '规格名（快照）',
  `product_image` VARCHAR(255)          COMMENT '商品图（快照）',
  `price`         DECIMAL(10,2) NOT NULL COMMENT '成交单价（快照）',
  `quantity`      INT          NOT NULL COMMENT '购买数量',
  `total_amount`  DECIMAL(10,2) NOT NULL COMMENT '小计金额',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

-- 15. 支付记录表（模拟支付）
CREATE TABLE IF NOT EXISTS `payment` (
  `id`           BIGINT UNSIGNED AUTO_INCREMENT COMMENT '支付ID',
  `payment_no`   VARCHAR(32)  NOT NULL COMMENT '支付流水号',
  `order_id`     BIGINT UNSIGNED NOT NULL COMMENT '订单ID（→order.id）',
  `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '用户ID（→sys_user.id）',
  `amount`       DECIMAL(10,2) NOT NULL COMMENT '支付金额',
  `pay_type`     TINYINT      NOT NULL DEFAULT 0 COMMENT '支付方式：0模拟支付',
  `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0待支付 1已支付 2已退款',
  `pay_time`     DATETIME               COMMENT '支付时间',
  `created_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_no` (`payment_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付记录表（模拟支付）';

-- 16. 物流表
CREATE TABLE IF NOT EXISTS `logistics` (
  `id`           BIGINT UNSIGNED AUTO_INCREMENT COMMENT '物流ID',
  `order_id`     BIGINT UNSIGNED NOT NULL COMMENT '订单ID（→order.id）',
  `logistics_no` VARCHAR(50)  NOT NULL COMMENT '物流单号',
  `company_name` VARCHAR(50)  NOT NULL COMMENT '物流公司',
  `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0已揽收 1运输中 2已签收',
  `delivery_time` DATETIME              COMMENT '发货时间',
  `created_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  KEY `idx_logistics_no` (`logistics_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物流表';

-- 17. 物流轨迹表
CREATE TABLE IF NOT EXISTS `logistics_track` (
  `id`           BIGINT UNSIGNED AUTO_INCREMENT COMMENT '轨迹ID',
  `logistics_id` BIGINT UNSIGNED NOT NULL COMMENT '物流ID（→logistics.id）',
  `track_info`   VARCHAR(300) NOT NULL COMMENT '轨迹描述',
  `track_time`   DATETIME     NOT NULL COMMENT '轨迹时间',
  PRIMARY KEY (`id`),
  KEY `idx_logistics_id` (`logistics_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物流轨迹表';

-- 18. 售后申请表（退款/退货/换货）
CREATE TABLE IF NOT EXISTS `after_sale` (
  `id`             BIGINT UNSIGNED AUTO_INCREMENT COMMENT '售后ID',
  `after_sale_no`  VARCHAR(32)  NOT NULL COMMENT '售后单号',
  `order_id`       BIGINT UNSIGNED NOT NULL COMMENT '订单ID（→order.id）',
  `order_item_id`  BIGINT UNSIGNED NOT NULL COMMENT '订单明细ID（→order_item.id）',
  `user_id`        BIGINT UNSIGNED NOT NULL COMMENT '申请人用户ID（→sys_user.id）',
  `type`           TINYINT      NOT NULL DEFAULT 0 COMMENT '类型：0仅退款 1退货退款 2换货',
  `reason`         VARCHAR(300) NOT NULL COMMENT '申请原因',
  `amount`         DECIMAL(10,2) NOT NULL COMMENT '申请退款金额',
  `status`         TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0待商家处理 1商家同意 2商家拒绝 3平台介入 4已完成 5已撤销',
  `reject_reason`  VARCHAR(200)          COMMENT '拒绝原因',
  `handle_time`    DATETIME              COMMENT '处理时间',
  `handle_by`      BIGINT UNSIGNED       COMMENT '处理人ID（商家/管理员→sys_user.id）',
  `created_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `updated_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_after_sale_no` (`after_sale_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后申请表';

-- 19. 商品评价表（comment 用反引号包裹）
CREATE TABLE IF NOT EXISTS `comment` (
  `id`              BIGINT UNSIGNED AUTO_INCREMENT COMMENT '评价ID',
  `order_id`        BIGINT UNSIGNED NOT NULL COMMENT '订单ID（→order.id）',
  `order_item_id`   BIGINT UNSIGNED          COMMENT '订单明细ID（→order_item.id）',
  `product_id`      BIGINT UNSIGNED NOT NULL COMMENT '商品ID（→product.id）',
  `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '评价用户ID（→sys_user.id）',
  `product_score`   TINYINT      NOT NULL DEFAULT 5 COMMENT '商品评分（1-5）',
  `logistics_score` TINYINT      NOT NULL DEFAULT 5 COMMENT '物流评分（1-5）',
  `content`         VARCHAR(1000)          COMMENT '评价内容',
  `image_urls`      VARCHAR(1000)          COMMENT '评价图片（逗号分隔URL）',
  `is_anonymous`    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否匿名：0否 1是',
  `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0待审核 1已展示 2违规删除',
  `reply_content`   VARCHAR(500)           COMMENT '商家回复内容',
  `reply_time`      DATETIME               COMMENT '商家回复时间',
  `created_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
  `updated_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评价表';

-- ============================================================================
-- 四、大模型智能导购
-- ============================================================================

-- 20. 导购会话表（一个用户可有多轮会话）
CREATE TABLE IF NOT EXISTS `chat_session` (
  `id`            BIGINT UNSIGNED AUTO_INCREMENT COMMENT '会话ID',
  `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '用户ID（→sys_user.id）',
  `session_title` VARCHAR(100)          COMMENT '会话标题（可自动摘要生成）',
  `created_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大模型导购会话表';

-- 21. 导购消息表（多轮对话历史，支撑上下文管理）
CREATE TABLE IF NOT EXISTS `chat_message` (
  `id`           BIGINT UNSIGNED AUTO_INCREMENT COMMENT '消息ID',
  `session_id`   BIGINT UNSIGNED NOT NULL COMMENT '会话ID（→chat_session.id）',
  `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '用户ID（→sys_user.id）',
  `role`         TINYINT      NOT NULL DEFAULT 0 COMMENT '消息角色：0用户 1导购助手',
  `content`      TEXT         NOT NULL COMMENT '消息内容',
  `product_ids`  VARCHAR(500)          COMMENT '推荐关联商品ID（逗号分隔，前端渲染商品卡片）',
  `intent`       VARCHAR(50)           COMMENT '识别到的用户意图（找商品/参数对比/选购建议/个性化推荐）',
  `token_count`  INT                   COMMENT 'Token消耗数（统计与计费用）',
  `created_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导购对话消息表';

-- ============================================================================
-- 五、运营管理
-- ============================================================================

-- 22. 系统公告表（活动通知/商品上新/平台规则）
CREATE TABLE IF NOT EXISTS `notice` (
  `id`            BIGINT UNSIGNED AUTO_INCREMENT COMMENT '公告ID',
  `title`         VARCHAR(100) NOT NULL COMMENT '公告标题',
  `content`       TEXT         NOT NULL COMMENT '公告内容',
  `type`          TINYINT      NOT NULL DEFAULT 0 COMMENT '类型：0活动 1商品上新 2平台规则',
  `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0草稿 1已发布',
  `publish_time`  DATETIME               COMMENT '发布时间',
  `publisher_id`  BIGINT UNSIGNED        COMMENT '发布人ID（→sys_user.id 管理员）',
  `created_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统公告表';

-- 23. 客户咨询/投诉表（商家端客户管理）
CREATE TABLE IF NOT EXISTS `complaint` (
  `id`            BIGINT UNSIGNED AUTO_INCREMENT COMMENT 'ID',
  `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '发起用户ID（→sys_user.id）',
  `shop_id`       BIGINT UNSIGNED NOT NULL COMMENT '店铺ID（→shop.id）',
  `order_id`      BIGINT UNSIGNED          COMMENT '关联订单ID（→order.id，可空）',
  `type`          TINYINT      NOT NULL DEFAULT 0 COMMENT '类型：0咨询 1投诉',
  `content`       VARCHAR(1000) NOT NULL COMMENT '内容',
  `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0待处理 1已回复 2已关闭',
  `reply_content` VARCHAR(1000)           COMMENT '回复内容',
  `reply_time`    DATETIME                COMMENT '回复时间',
  `created_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_shop_id` (`shop_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户咨询/投诉表';

-- 24. 操作日志表（管理员/商家关键操作留痕，权限管控审计用）
CREATE TABLE IF NOT EXISTS `operation_log` (
  `id`           BIGINT UNSIGNED AUTO_INCREMENT COMMENT '日志ID',
  `user_id`      BIGINT UNSIGNED          COMMENT '操作人ID（→sys_user.id，可空=未登录）',
  `module`       VARCHAR(50)              COMMENT '操作模块（商品/订单/用户/审核等）',
  `action`       VARCHAR(100)             COMMENT '操作内容',
  `ip`           VARCHAR(50)              COMMENT '操作IP',
  `created_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- ============================================================================
-- 六、初始数据（示例数据，可按需修改）
-- ============================================================================

-- 初始管理员账号：admin / admin123（注意：password 字段必须存 BCrypt 密文，
-- 请使用 Spring Security 的 BCryptPasswordEncoder 生成后替换下面占位符）
INSERT INTO `sys_user` (`username`, `password`, `nickname`, `role`, `status`) VALUES
('admin', '此处替换为BCrypt密文', '平台管理员', 2, 1);

-- 顶级商品分类（数码电商）
INSERT INTO `category` (`parent_id`, `category_name`, `sort_order`, `status`) VALUES
(0, '手机通讯', 1, 1),
(0, '笔记本电脑', 2, 1),
(0, '平板电脑', 3, 1),
(0, '智能穿戴', 4, 1),
(0, '音频耳机', 5, 1),
(0, '相机摄像', 6, 1),
(0, '电脑配件', 7, 1),
(0, '智能家居', 8, 1);

-- 初始品牌
INSERT INTO `brand` (`brand_name`, `status`) VALUES
('Apple', 1), ('华为', 1), ('小米', 1), ('荣耀', 1),
('OPPO', 1), ('vivo', 1), ('三星', 1), ('索尼', 1), ('联想', 1);

-- ============================================================================
-- 附：Redis 侧键设计（不建表，供大模型导购模块参考）
--   product:embedding:{productId}   → 商品向量（JSON浮点数组，Embedding接口生成）
--   chat:context:{sessionId}        → 最近N轮对话上下文（TTL 30分钟）
--   product:hot                     → 热门商品ZSet（member=productId, score=sales）
--   login:token:{userId}            → 登录令牌（TTL随JWT有效期）
--   sms:code:{phone}                → 短信验证码（TTL 5分钟）
-- ============================================================================
