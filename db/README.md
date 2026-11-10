# 数据库脚本

## 执行顺序

新建环境按编号执行，两步都不能省：

| 顺序 | 文件 | 内容 | 必须执行 |
|---|---|---|---|
| 1 | `数码电商平台-数据库建表SQL.sql` | 24 张业务表 + 2 张 AI 表 + 分类/品牌/账号 | 是 |
| 2 | `测试商品数据.sql` | 8 件商品 + 1 个店铺 + SKU/参数/图片 | **是** |
| — | `sys_ai_config.sql` | AI 配置表 DDL 存档（已并入主建表脚本） | 否，应用启动自动建表 |
| — | `sys_ai_tool.sql` | AI 工具注册表 DDL + 5 个内置工具（已并入主建表脚本） | 否，应用启动自动建表并播种 |

建表脚本只创建空表，**不含任何商品数据**。跳过第 2 步会让首页空白、智能导购对任何问题都回"没有符合条件的商品"，看起来像核心功能坏了。

## 执行方式

```bash
mysql -u root -p < 数码电商平台-数据库建表SQL.sql
mysql -u root -p digital_mall < 测试商品数据.sql
```

或用 Navicat / DataGrip 打开文件整段执行。字符集必须是 `utf8mb4`，否则商品名里的 emoji 与部分符号会插入失败。

## 校验

```bash
python ../scripts/验证-测试数据SQL.py
```

预期结果：`product` 8 条、`product_sku` 覆盖全部商品、`product_attr` 每条商品都有参数、`shop` 1 条、`sys_user` 含 `admin` 与 `merchant1`。

## 重置

```sql
DROP DATABASE IF EXISTS `digital_mall`;
```

然后重新执行第 1、2 步。

> **不要**对已有数据的库重复执行 `测试商品数据.sql`。脚本里的商品编码、用户名都有唯一键约束，重复执行会报 `Duplicate entry`，需要先清库。

## 常见报错

| 报错 | 原因 | 处理 |
|---|---|---|
| `Duplicate entry ... for key 'uk_product_code'` | 重复执行了测试数据脚本 | 先 `DROP DATABASE` 重建 |
| `Data too long for column 'nickname'` | Windows 下 `mysql.exe` 默认字符集是 gbk，把 UTF-8 文件里的中文按 GBK 解析，字节数膨胀 | 两个脚本已在 `USE` 后加了 `SET NAMES utf8mb4;`，正常不会再出现；若手工改过脚本，导入时加 `--default-character-set=utf8mb4` |
| 中文变问号 / 乱码 | 连接字符集不是 utf8mb4 | 导入与查询都用 `--default-character-set=utf8mb4`，或连接串加 `?characterEncoding=utf8mb4` |
| 登录提示用户名或密码错误 | `sys_user.password` 是占位符而非 BCrypt 密文 | 重新执行建表脚本，或依赖启动自检 `SeedPasswordInitializer` |
| `Table 'digital_mall.sys_ai_config' doesn't exist` | 手工建库且跳过了 DDL 存档 | 执行 `sys_ai_config.sql` / `sys_ai_tool.sql`，或直接启动后端让它自建 |

## 关于两个 DDL 存档

`sys_ai_config.sql` 与 `sys_ai_tool.sql` 的**权威副本**在后端资源目录：

- `code/backend/src/main/resources/db/sys_ai_config.sql`
- `code/backend/src/main/resources/db/sys_ai_tool.sql`

`AiConfigService` / `AiToolService` 启动时执行同样的 `CREATE TABLE IF NOT EXISTS` 并播种默认值，因此正常部署不需要手工执行。放到这里是为了：换环境纯手工建库时有据可依；论文附录引用 DDL 时代码与文档对得上。**改表结构时两处同步改**。
