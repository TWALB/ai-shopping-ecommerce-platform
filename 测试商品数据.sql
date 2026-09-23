-- ============================================================================
-- 测试商品数据（商品模块演示用）
-- 前提：先执行《数码电商平台-数据库建表SQL.sql》完成建库建表与基础数据
-- 说明：
--   1. 通过 SET @变量 + LAST_INSERT_ID() 关联商品与SKU/参数/图片，可重复追加
--   2. 商家账号密码为占位符：请用 BCryptPasswordEncoder 对 "merchant123" 加密后替换，
--      或直接复用你已注册账号的 id 修改 shop.merchant_id
--   3. 商品图片使用占位图 URL，可替换为真实商品图片
-- ============================================================================

USE `digital_mall`;

-- ---------------------------- 商家与店铺 ----------------------------
-- 商家账号：merchant1 / merchant123（密码需替换为 BCrypt 密文）
INSERT INTO `sys_user` (`username`, `password`, `nickname`, `phone`, `role`, `status`) VALUES
('merchant1', '此处替换为BCrypt密文', '数码优选商家', '13900000001', 1, 1);

-- 店铺（merchant_id 关联上面新插入的用户）
SET @merchantId = (SELECT id FROM `sys_user` WHERE username = 'merchant1' LIMIT 1);
INSERT INTO `shop` (`merchant_id`, `shop_name`, `shop_intro`, `status`) VALUES
(@merchantId, '数码优选旗舰店', '正品保障，假一赔十，全国联保', 1);

SET @shopId = LAST_INSERT_ID();

-- ---------------------------- 分类与品牌变量 ----------------------------
SET @c_phone    = (SELECT id FROM `category` WHERE category_name = '手机通讯' LIMIT 1);
SET @c_laptop   = (SELECT id FROM `category` WHERE category_name = '笔记本电脑' LIMIT 1);
SET @c_pad      = (SELECT id FROM `category` WHERE category_name = '平板电脑' LIMIT 1);
SET @c_audio    = (SELECT id FROM `category` WHERE category_name = '音频耳机' LIMIT 1);
SET @b_apple    = (SELECT id FROM `brand` WHERE brand_name = 'Apple' LIMIT 1);
SET @b_huawei   = (SELECT id FROM `brand` WHERE brand_name = '华为' LIMIT 1);
SET @b_xiaomi   = (SELECT id FROM `brand` WHERE brand_name = '小米' LIMIT 1);
SET @b_lenovo   = (SELECT id FROM `brand` WHERE brand_name = '联想' LIMIT 1);
SET @b_sony     = (SELECT id FROM `brand` WHERE brand_name = '索尼' LIMIT 1);

-- ---------------------------- 商品1：iPhone 17 Pro ----------------------------
INSERT INTO `product` (`shop_id`, `category_id`, `brand_id`, `product_name`, `product_title`, `main_image`,
  `keywords`, `tags`, `brief`, `price`, `original_price`, `stock`, `sales`, `view_count`, `status`, `is_recommend`, `shelf_time`)
VALUES (@shopId, @c_phone, @b_apple, 'iPhone 17 Pro', '钛金属机身 · A19 Pro 芯片 · 长焦三摄',
  'https://via.placeholder.com/400x400?text=iPhone+17+Pro',
  'iphone,苹果,旗舰,5g,拍照,手机', '旗舰机,拍照', '苹果最新旗舰，钛金属设计，影像系统全面升级', 8999.00, 9999.00, 100, 3200, 15800, 1, 1, NOW());
SET @p1 = LAST_INSERT_ID();
INSERT INTO `product_sku` (`product_id`, `sku_name`, `price`, `stock`, `sales`, `status`) VALUES
(@p1, '原色钛金属/256GB', 8999.00, 60, 2000, 1),
(@p1, '原色钛金属/512GB', 10999.00, 40, 1200, 1);
INSERT INTO `product_attr` (`product_id`, `attr_name`, `attr_value`, `sort_order`) VALUES
(@p1, '屏幕', '6.3英寸 OLED 120Hz', 1),
(@p1, '处理器', 'A19 Pro', 2),
(@p1, '电池', '3500mAh 有线/无线快充', 3),
(@p1, '摄像头', '4800万像素三摄', 4);
INSERT INTO `product_image` (`product_id`, `image_url`, `image_type`, `sort_order`) VALUES
(@p1, 'https://via.placeholder.com/800x800?text=iPhone+17+Pro+1', 0, 1),
(@p1, 'https://via.placeholder.com/800x800?text=iPhone+17+Pro+2', 0, 2);

-- ---------------------------- 商品2：华为 Mate 70 Pro ----------------------------
INSERT INTO `product` (`shop_id`, `category_id`, `brand_id`, `product_name`, `product_title`, `main_image`,
  `keywords`, `tags`, `brief`, `price`, `original_price`, `stock`, `sales`, `view_count`, `status`, `is_recommend`, `shelf_time`)
VALUES (@shopId, @c_phone, @b_huawei, '华为 Mate 70 Pro', '鸿蒙生态 · 卫星通信 · 麒麟旗舰芯片',
  'https://via.placeholder.com/400x400?text=Huawei+Mate+70+Pro',
  '华为,mate,旗舰,卫星通信,手机', '旗舰机,商务', '华为新一代旗舰，通信能力天花板', 6999.00, 7299.00, 150, 2800, 13200, 1, 1, NOW());
SET @p2 = LAST_INSERT_ID();
INSERT INTO `product_sku` (`product_id`, `sku_name`, `price`, `stock`, `sales`, `status`) VALUES
(@p2, '曜石黑/12GB+512GB', 6999.00, 90, 1800, 1),
(@p2, '雪域白/12GB+512GB', 6999.00, 60, 1000, 1);
INSERT INTO `product_attr` (`product_id`, `attr_name`, `attr_value`, `sort_order`) VALUES
(@p2, '屏幕', '6.8英寸 OLED 120Hz', 1),
(@p2, '处理器', '麒麟 9100', 2),
(@p2, '电池', '5000mAh 100W快充', 3),
(@p2, '特色', '卫星通话 / 红外遥控', 4);
INSERT INTO `product_image` (`product_id`, `image_url`, `image_type`, `sort_order`) VALUES
(@p2, 'https://via.placeholder.com/800x800?text=Huawei+Mate+70+Pro', 0, 1);

-- ---------------------------- 商品3：小米 15 ----------------------------
INSERT INTO `product` (`shop_id`, `category_id`, `brand_id`, `product_name`, `product_title`, `main_image`,
  `keywords`, `tags`, `brief`, `price`, `original_price`, `stock`, `sales`, `view_count`, `status`, `is_recommend`, `shelf_time`)
VALUES (@shopId, @c_phone, @b_xiaomi, '小米 15', '小屏旗舰 · 徕卡影像 · 骁龙8至尊版',
  'https://via.placeholder.com/400x400?text=Xiaomi+15',
  '小米,15,徕卡,小屏,旗舰,手机', '性价比,旗舰机', '年度小屏旗舰，徕卡光学镜头', 2999.00, 3299.00, 200, 5210, 22100, 1, 1, NOW());
SET @p3 = LAST_INSERT_ID();
INSERT INTO `product_sku` (`product_id`, `sku_name`, `price`, `stock`, `sales`, `status`) VALUES
(@p3, '黑色/12GB+256GB', 2999.00, 120, 3000, 1),
(@p3, '白色/16GB+512GB', 3499.00, 80, 2210, 1);
INSERT INTO `product_attr` (`product_id`, `attr_name`, `attr_value`, `sort_order`) VALUES
(@p3, '屏幕', '6.36英寸 1.5K 120Hz', 1),
(@p3, '处理器', '骁龙 8 至尊版', 2),
(@p3, '摄像头', '徕卡三摄 5000万像素', 3),
(@p3, '电池', '5400mAh 90W快充', 4);
INSERT INTO `product_image` (`product_id`, `image_url`, `image_type`, `sort_order`) VALUES
(@p3, 'https://via.placeholder.com/800x800?text=Xiaomi+15', 0, 1);

-- ---------------------------- 商品4：Redmi Note 14（学生党性价比） ----------------------------
INSERT INTO `product` (`shop_id`, `category_id`, `brand_id`, `product_name`, `product_title`, `main_image`,
  `keywords`, `tags`, `brief`, `price`, `original_price`, `stock`, `sales`, `view_count`, `status`, `is_recommend`, `shelf_time`)
VALUES (@shopId, @c_phone, @b_xiaomi, 'Redmi Note 14', '千元机皇 · 大电池长续航',
  'https://via.placeholder.com/400x400?text=Redmi+Note+14',
  '红米,note,千元机,长续航,学生,手机', '学生党,性价比', '千元档位续航神机，学生党首选', 1299.00, 1499.00, 300, 8000, 35000, 1, 0, NOW());
SET @p4 = LAST_INSERT_ID();
INSERT INTO `product_sku` (`product_id`, `sku_name`, `price`, `stock`, `sales`, `status`) VALUES
(@p4, '星岩黑/8GB+256GB', 1299.00, 180, 5000, 1),
(@p4, '浅海蓝/8GB+256GB', 1299.00, 120, 3000, 1);
INSERT INTO `product_attr` (`product_id`, `attr_name`, `attr_value`, `sort_order`) VALUES
(@p4, '屏幕', '6.67英寸 120Hz', 1),
(@p4, '电池', '5500mAh 45W快充', 2),
(@p4, '摄像头', '1.08亿像素主摄', 3);
INSERT INTO `product_image` (`product_id`, `image_url`, `image_type`, `sort_order`) VALUES
(@p4, 'https://via.placeholder.com/800x800?text=Redmi+Note+14', 0, 1);

-- ---------------------------- 商品5：MacBook Air 13 ----------------------------
INSERT INTO `product` (`shop_id`, `category_id`, `brand_id`, `product_name`, `product_title`, `main_image`,
  `keywords`, `tags`, `brief`, `price`, `original_price`, `stock`, `sales`, `view_count`, `status`, `is_recommend`, `shelf_time`)
VALUES (@shopId, @c_laptop, @b_apple, 'MacBook Air 13', 'M4芯片 · 无风扇设计 · 全天续航',
  'https://via.placeholder.com/400x400?text=MacBook+Air',
  '苹果,macbook,air,轻薄本,笔记本,学生', '轻薄本,学生党', '轻薄便携，性能与续航兼备', 7999.00, 8499.00, 80, 1500, 9800, 1, 1, NOW());
SET @p5 = LAST_INSERT_ID();
INSERT INTO `product_sku` (`product_id`, `sku_name`, `price`, `stock`, `sales`, `status`) VALUES
(@p5, '午夜色/16GB+256GB', 7999.00, 50, 900, 1),
(@p5, '星光色/16GB+512GB', 9499.00, 30, 600, 1);
INSERT INTO `product_attr` (`product_id`, `attr_name`, `attr_value`, `sort_order`) VALUES
(@p5, '屏幕', '13.6英寸 Liquid 视网膜屏', 1),
(@p5, '芯片', 'Apple M4', 2),
(@p5, '内存', '16GB 统一内存', 3),
(@p5, '重量', '1.24kg', 4);
INSERT INTO `product_image` (`product_id`, `image_url`, `image_type`, `sort_order`) VALUES
(@p5, 'https://via.placeholder.com/800x800?text=MacBook+Air', 0, 1);

-- ---------------------------- 商品6：联想 拯救者 Y7000P ----------------------------
INSERT INTO `product` (`shop_id`, `category_id`, `brand_id`, `product_name`, `product_title`, `main_image`,
  `keywords`, `tags`, `brief`, `price`, `original_price`, `stock`, `sales`, `view_count`, `status`, `is_recommend`, `shelf_time`)
VALUES (@shopId, @c_laptop, @b_lenovo, '联想 拯救者 Y7000P', '游戏本 · RTX5060显卡 · 电竞屏',
  'https://via.placeholder.com/400x400?text=Lenovo+Y7000P',
  '联想,拯救者,游戏本,电竞,笔记本', '游戏本,高性能', '主流价位游戏本，畅玩3A大作', 7499.00, 7999.00, 90, 2100, 14500, 1, 0, NOW());
SET @p6 = LAST_INSERT_ID();
INSERT INTO `product_sku` (`product_id`, `sku_name`, `price`, `stock`, `sales`, `status`) VALUES
(@p6, '幻影黑/16GB+1TB', 7499.00, 60, 1500, 1),
(@p6, '幻影黑/32GB+1TB', 8299.00, 30, 600, 1);
INSERT INTO `product_attr` (`product_id`, `attr_name`, `attr_value`, `sort_order`) VALUES
(@p6, '屏幕', '16英寸 2.5K 165Hz', 1),
(@p6, '显卡', 'RTX 5060 8GB', 2),
(@p6, '处理器', '酷睿 i7-14700HX', 3),
(@p6, '散热', '霜刃Pro散热系统', 4);
INSERT INTO `product_image` (`product_id`, `image_url`, `image_type`, `sort_order`) VALUES
(@p6, 'https://via.placeholder.com/800x800?text=Lenovo+Y7000P', 0, 1);

-- ---------------------------- 商品7：iPad Air 11 ----------------------------
INSERT INTO `product` (`shop_id`, `category_id`, `brand_id`, `product_name`, `product_title`, `main_image`,
  `keywords`, `tags`, `brief`, `price`, `original_price`, `stock`, `sales`, `view_count`, `status`, `is_recommend`, `shelf_time`)
VALUES (@shopId, @c_pad, @b_apple, 'iPad Air 11', 'M3芯片 · 支持Apple Pencil Pro',
  'https://via.placeholder.com/400x400?text=iPad+Air',
  '苹果,ipad,air,平板,学习', '平板,学习办公', '学习办公利器，适配手写笔', 4799.00, 5199.00, 120, 1800, 11200, 1, 0, NOW());
SET @p7 = LAST_INSERT_ID();
INSERT INTO `product_sku` (`product_id`, `sku_name`, `price`, `stock`, `sales`, `status`) VALUES
(@p7, '蓝色/128GB', 4799.00, 70, 1100, 1),
(@p7, '蓝色/256GB', 5599.00, 50, 700, 1);
INSERT INTO `product_attr` (`product_id`, `attr_name`, `attr_value`, `sort_order`) VALUES
(@p7, '屏幕', '11英寸 Liquid 视网膜屏', 1),
(@p7, '芯片', 'Apple M3', 2),
(@p7, '配件支持', 'Apple Pencil Pro / 妙控键盘', 3);
INSERT INTO `product_image` (`product_id`, `image_url`, `image_type`, `sort_order`) VALUES
(@p7, 'https://via.placeholder.com/800x800?text=iPad+Air', 0, 1);

-- ---------------------------- 商品8：索尼 WH-1000XM6 ----------------------------
INSERT INTO `product` (`shop_id`, `category_id`, `brand_id`, `product_name`, `product_title`, `main_image`,
  `keywords`, `tags`, `brief`, `price`, `original_price`, `stock`, `sales`, `view_count`, `status`, `is_recommend`, `shelf_time`)
VALUES (@shopId, @c_audio, @b_sony, '索尼 WH-1000XM6', '旗舰降噪 · 30小时续航 · 高解析音质',
  'https://via.placeholder.com/400x400?text=Sony+WH-1000XM6',
  '索尼,降噪,耳机,头戴,蓝牙', '降噪耳机,通勤', '行业标杆降噪，通勤差旅神器', 2499.00, 2699.00, 150, 3200, 18600, 1, 1, NOW());
SET @p8 = LAST_INSERT_ID();
INSERT INTO `product_sku` (`product_id`, `sku_name`, `price`, `stock`, `sales`, `status`) VALUES
(@p8, '黑色', 2499.00, 90, 2000, 1),
(@p8, '银灰色', 2499.00, 60, 1200, 1);
INSERT INTO `product_attr` (`product_id`, `attr_name`, `attr_value`, `sort_order`) VALUES
(@p8, '降噪', 'HD降噪处理器 QN2e', 1),
(@p8, '续航', '30小时（开降噪）', 2),
(@p8, '连接', '蓝牙5.3 / 多点连接', 3),
(@p8, '重量', '250g', 4);
INSERT INTO `product_image` (`product_id`, `image_url`, `image_type`, `sort_order`) VALUES
(@p8, 'https://via.placeholder.com/800x800?text=Sony+WH-1000XM6', 0, 1);

-- 完成：导入后访问 http://localhost:8080/api/product/page 即可看到8件测试商品
