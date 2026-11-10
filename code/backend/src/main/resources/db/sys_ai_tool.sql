-- AI 可调用工具注册表
-- 说明：后端启动时会自动执行同样的 CREATE TABLE IF NOT EXISTS 并播种 5 个内置工具
--      （见 AiToolService#init），本文件仅作为 DDL 存档，便于手工建库或论文附录引用。
-- 注意：表里只存「模型能看到什么工具、怎么描述、参数长什么样」，真实执行绑定在
--      AiToolInvoker 的 Java 方法上，管理端改不到，避免把内部接口暴露成可配置项。

CREATE TABLE IF NOT EXISTS `sys_ai_tool` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tool_code`     VARCHAR(64)  NOT NULL COMMENT '工具编码，同时是模型看到的 function name',
  `tool_name`     VARCHAR(64)  NOT NULL COMMENT '展示名称',
  `domain`        VARCHAR(16)  NOT NULL COMMENT 'product=商品域 biz=业务域',
  `description`   VARCHAR(500) DEFAULT NULL COMMENT '给模型看的用途说明',
  `params_schema` TEXT         COMMENT '入参 JSON Schema，调试台据此生成表单',
  `sample_params` TEXT         COMMENT '调试台一键填充的示例参数',
  `result_hint`   VARCHAR(255) DEFAULT NULL COMMENT '返回结构说明',
  `enabled`       TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
  `builtin`       TINYINT      NOT NULL DEFAULT 1 COMMENT '1=内置（有 Java 实现，不可删除）',
  `sort_no`       INT          DEFAULT 0 COMMENT '排序',
  `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tool_code` (`tool_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 可调用工具注册表';

-- 内置工具播种（重复执行安全）
INSERT IGNORE INTO `sys_ai_tool`
  (`tool_code`, `tool_name`, `domain`, `description`, `params_schema`, `sample_params`, `result_hint`, `enabled`, `builtin`, `sort_no`)
VALUES
('searchProduct', '商品检索', 'product',
 '按关键词、品类、预算召回商品，返回按相似度排序的候选列表。用户提到找某类商品时优先调用。',
 '{"type":"object","properties":{"keyword":{"type":"string","description":"核心需求词，如 拍照、续航、游戏"},"category":{"type":"string","description":"品类：手机/笔记本/平板/耳机/手表/相机/音箱/配件"},"budget":{"type":"number","description":"预算上限，单位元，0 表示不限"},"brand":{"type":"string","description":"品牌名，可空"},"topK":{"type":"integer","description":"返回条数，1-20，默认 5"}}}',
 '{"keyword":"拍照","category":"手机","budget":5000,"topK":5}',
 'count / items[{productId, productName, price, similarity}]', 1, 1, 1),

('getProductDetail', '商品详情', 'product',
 '按商品 ID 查询基础信息与全部参数属性，用于参数对比或回答「这款有什么配置」。',
 '{"type":"object","required":["productId"],"properties":{"productId":{"type":"integer","description":"商品 ID"}}}',
 '{"productId":1}',
 'productName / price / stock / attrs[{name, value}]', 1, 1, 2),

('getPrice', '价格与库存', 'product',
 '按商品 ID 查询实时价格、原价、库存与销量，用于「现在多少钱」「还有货吗」。',
 '{"type":"object","required":["productId"],"properties":{"productId":{"type":"integer","description":"商品 ID"}}}',
 '{"productId":1}',
 'price / originalPrice / stock / inStock / sales', 1, 1, 3),

('getOrderStatus', '订单状态', 'biz',
 '按订单号查询订单状态、金额与收货信息。用户问「我的订单到哪了」时调用，不要写进提示词。',
 '{"type":"object","required":["orderNo"],"properties":{"orderNo":{"type":"string","description":"订单号，形如 DM202601010001"}}}',
 '{"orderNo":"DM202601010001"}',
 'status / statusText / payAmount / 各时间节点', 1, 1, 4),

('getTrack', '物流轨迹', 'biz',
 '按订单号查询物流公司与签收轨迹，用于「快递到哪了」「什么时候能到」。',
 '{"type":"object","required":["orderNo"],"properties":{"orderNo":{"type":"string","description":"订单号，形如 DM202601010001"}}}',
 '{"orderNo":"DM202601010001"}',
 'companyName / logisticsNo / tracks[{trackTime, trackInfo}]', 1, 1, 5);
