# QA 报告：基于大模型导购的数码电商平台

| Field | Value |
|-------|-------|
| **Date** | 2026-11-09 |
| **Tier** | Standard（静态深度分析 + 构建验证；未启动 MySQL/Redis 做交互式实测） |
| **Scope** | 全量业务模块①②③④⑤⑥⑦⑧：认证/商品/购物车/订单/支付/售后/评价/导购SSE/咨询投诉/管理端 |
| **Branch** | 当前工作区 HEAD `4aed888`（工作树干净） |
| **Framework** | 后端 Spring Boot 3.2.5 + MyBatis-Plus 3.5.5 + JWT + Redis；前端 Vue3 + Vite5 + Element Plus + Pinia |
| **代码规模** | 后端 141 个 Java 文件 / 前端 33 个源文件（16 视图 + 3 布局 + 11 API 模块） |
| **构建验证** | `mvn -o compile` ✅ 通过（EXIT=0）；`npm run build` ✅ 通过（16.32s，含 chunk>500kB 警告） |
| **自动化测试** | ❌ 后端 0 用例（无 `src/test`）｜❌ 前端 0 用例（无 test runner / 无 test 脚本） |

## 健康分：54/100

> v5 修订（2026-11-09）：按代码审查建议将 QA-005 拆为 005a（调用点 1 行，随 B1 改）/ 005b（记账模型 + schema + 4 条统计 SQL），并补录"退款后 GMV 不减"影响面；QA-029 触发面收窄至唯一路径并给出确定性复现；工时口径补"不含手工回归"。问题 32 → **33**。
> **v5.1（三方口径终版）**：新增 **C 组条件阻塞 3 项**（C1 QA-030 / C2 QA-031 / C3 QA-017），与代码审查、安全审计统一为「**6 无条件 + 3 条件**」表述；QA-017 定级 medium → **high**（与 QA-030 构成组合拳）；QA-001 修复建议补第四层 `payAmount > 0` 断言。
> **v5.2（最终收口）**：编码审查 **F-022** 为 **QA-032**（授权"缺省即放行"，与 QA-029 合并为横向建议「**默认拒绝 + 显式 `@Public`**」，修复口径取"抛 403"而非"返回空列表"）；回归基线补第 14 条，第 13 条补机理注脚（`finishTime` 隐式状态推导）；用例 #20 断言层修正为"启动日志/退出码"，新增 #21（评价管理默认拒绝）。问题 33 → **34**（low 6 → 7），**健康分不变（54）**——新增项为 🟢 设计债，不改变发布判断。
> v4：补录 QA-028~031，拆分 QA-002a/002b（27 → 32）。v2：补录 QA-025/026/027（24 → 27）。

| Category | Score | 依据 |
|----------|-------|------|
| Functional | 45 | 3 个阻塞级 + 11 个严重级（含 1 条主路径必现前端崩溃、1 类并发读后写竞态、2 条账号安全缺口） |
| Console/Errors | 68 | 有统一 `GlobalExceptionHandler`；但存在可触发 500 的入参路径 + 1 处前端必崩 + `UserContext` 线程残留隐患 |
| Links/Nav | 85 | 三端路由与角色守卫完备，无死链；`/profile`、`/favorite` 为后端空壳接口（前端未接入） |
| Visual | 80 | 组件库统一、构建通过；未做多视口实测，`via.placeholder.com` 外链图为离线风险 |
| UX | 68 | SSE 无超时/无失败提示、"评价过看不到"缺提示、批量评价入口聚合误导 |
| Performance | 62 | 商品详情**全量加载评价算均值**（无分页无聚合）；`AdminDashboard` chunk 1.15MB；`redisTemplate.keys()` 全键扫描；`toVOList` 存在 N+1 |
| Accessibility | 55 | 未做 a11y 审查：图片无 `alt`、`el-rate`/图标按钮无语义标签、键盘可达性未验证 |

## Top 3 必修（按执行顺序，验收路径优先）

1. **QA-002a + QA-002b 初始化双缺口**：admin 密码占位符（登录死锁链）+ 商品数据脚本未纳入启动流程（首页空、导购空转）—— 不修则验收人登录不了、看不到商品，其余缺陷根本走不到
2. **QA-025 订单列表「去评价」主路径必崩**（TypeError，评价功能在主入口完全不可用）
3. **QA-001 负/零数量下单 → 库存反增、订单金额为负**（交易数据完整性）

---

## 问题清单（按 qa issue-taxonomy 分类）

> severity：critical 🔴 / high 🟠 / medium 🟡 / low 🟢；category：functional / ux / content / performance / console / visual / accessibility

### 🔴 QA-001 购物车与下单数量缺下界校验：负数量反增库存、订单金额为负

| Field | Value |
|---|---|
| Severity | **critical** |
| Category | functional（金额/库存数据完整性） |
| 位置 | `CartController.java:31,38`（`@RequestParam Integer quantity` 无校验）；`CartServiceImpl.java:38-75`；`OrderServiceImpl.java:102-109`（`stock = stock - {0}` + `ge(stock, qty)`）；`dto/CreateOrderRequest.java`（无 `@NotNull/@Min`，Controller 无 `@Valid`）；前端 `Cart.vue:33` 仅 UI 层 `:min="1"` |

**期望 vs 实际**
- 期望：数量 <1 时返回 `400 参数非法`，不得产生任何库存/金额变更。
- 实际：
  - `PUT /api/cart/{id}/quantity?quantity=-3` → 通过 `quantity > stock` 校验（-3 > stock 为假）→ 购物车落库 `quantity=-3`。
  - `POST /api/order` body `{"fromCart":false,"addressId":X,"itemList":[{"skuId":Y,"quantity":-3}]}` → `ge(stock,-3)` 恒真 → 执行 `stock = stock - (-3)` **库存 +3**，`total_amount = price × (-3)` **订单金额为负**，随后可支付出负数支付流水。
  - `quantity=null`（漏传）→ `stock >= null` 恒假 → 返回「商品「xx」库存不足」误导性错误（非 400 参数错误）。

**复现路径（API 级，UI 不可达）**
1. 普通用户登录拿 Token。
2. `PUT /api/cart/1/quantity?quantity=-3` → 看购物车 `quantity=-3`。
3. 或直接 `POST /api/order`（`fromCart=false`，`quantity=-3`）→ 查 `product_sku.stock` 增加、`order.pay_amount` 为负。

**建议**：`CreateOrderRequest.OrderItemLine.quantity` 加 `@NotNull @Min(1) @Max(999)`，`OrderController.create` 加 `@Valid`；`CartServiceImpl.add/updateQuantity` 与两处 Controller `@RequestParam @Min(1)` 双端校验；`createOneOrder` 循环前再兜一次 `quantity<=0 → 400`；**并在生成订单头后断言 `payAmount > 0`，否则抛 400**（第四层兜底，防任何未来的组合路径算出非正金额——安全审计 F-001 补充项）。

---

### 🔴 QA-002a 全新环境 admin 账号为 BCrypt 占位符 → 登录死锁链（原 QA-002 拆分①）

| Field | Value |
|---|---|
| Severity | **critical**（评审/答辩换机或重建库时，管理端与商家端完全无法登录） |
| Category | content / release |
| 位置 | `数码电商平台-数据库建表SQL.sql:461`（`('admin','此处替换为BCrypt密文',...)`）；`测试商品数据.sql:15`（`merchant1` 同样为占位符）；`README.md:36`（仅一句"把管理员密码替换为 BCrypt 密文"，未提供生成工具，且"快速启动"5 步**未包含** `测试商品数据.sql`，无商品数据来源） |

**期望 vs 实际**
- 期望：按 README 五步即可得到可登录的 `admin` / `merchant1` 与一批商品数据。
- 实际：
  - `BCryptPasswordEncoder.matches(任意密码, "此处替换为BCrypt密文")` 恒 false → 登录返回 `400 用户名或密码错误`，管理后台/商家工作台不可进入。
  - 仓库内无 BCrypt 生成脚本（`验证-测试数据SQL.py` 只用 sqlparse 校验 SQL 文本结构，不产出密文）。
  - 脚本注释声称"可重复追加"，但 `sys_user.uk_username`、`shop.uk_merchant_id`、`brand.uk_brand_name`、`cart.uk_user_sku` 均为唯一键且无 `DELETE/TRUNCATE` 前置 → 二次执行报 `Duplicate entry`，`category` 会重复插入导致商品挂到重复分类下。

**复现路径**：新机器 → 只执行建表 SQL → 尝试用 `admin` + 任意密码登录 → 400。重复执行 `测试商品数据.sql` → Duplicate entry 报错中断。

**失效链（比"登录失败"严重得多，评估后置顶为最高优先级）**

```
admin 密码占位符（无法登录）
  └─ 管理端「商家入驻审核」不可操作
      └─ merchant1 无法被审核通过 → shop 表无对应记录
          └─ MerchantServiceImpl.requireShop():258-264 全线 403「请先完成商家入驻」
              └─ 商家工作台（商品/订单/发货/售后/咨询）全部不可用
```
且 `BCryptPasswordEncoder.matches()` 对非法密文格式**返回 false 而非抛异常**（仅打一条 warning）→ 现象是"用户名或密码错误"，排查成本极高，极易被误判为"密码记错了"。

**商品数据缺失会让核心特色空转**：

### 🔴 QA-002b 种子商品数据脚本未纳入启动流程（原 QA-002 拆分②）

| Field | Value |
|---|---|
| Severity | **critical**（不修则首页空、导购空转，验收看不到任何商品） |
| Category | content / release |
| 位置 | `数码电商平台-数据库建表SQL.sql`（INSERT 仅 `sys_user / category / brand`，**product 表 0 条**）；`测试商品数据.sql`（8 件商品 + shop + 商家账号）；`README.md:36-40`（快速启动 5 步**未包含**该脚本） |

> 与 QA-002a 是两件独立的事，**必须分开修**：只修账号 → 登进去了但商城是空的；只补商品 → 首页有货但管理员/商家进不去。代码审查侧对应 B6。

**不执行该脚本的后果**（8 件商品、shop、商家账号全在 `测试商品数据.sql` 里）→
- 首页/商品列表全空；
- 导购 `ProductRetrievalService.onSaleProducts()` 返回空 → `buildLocalAnswer` 一律回"抱歉，暂时没有完全匹配的商品"→ **答辩最核心的大模型导购看起来像坏了**；
- `chat/knowledge/sync` 返回 0。
而 README 仅在**目录结构第 16 行**提过该脚本，**快速启动 5 步里完全没有**（第 1 步只跑建表 SQL、第 2 步直接启动 Redis）。

**建议（含已实测验证的现成密文）**：

① **直接内置密文**（QA 侧已独立实测验证，见下），替换 `建表SQL:461`：
```sql
-- 初始管理员账号：admin / admin123（BCrypt，spring-security-crypto 6.2.4 实测通过）
INSERT INTO `sys_user` (`username`, `password`, `nickname`, `role`, `status`) VALUES
('admin', '$2a$10$Z6bTJn/2LQq.WZERf0aM/uy9drgbMlqDNdkOqsW7LHLU9UB7Hddx6', '平台管理员', 2, 1);
```
**QA 侧独立实测结果**（用 `~/.m2` 中项目同版本 `spring-security-crypto-6.2.4.jar` 编译执行）：
| 断言 | 结果 |
|---|---|
| 密文长度 | **60**（`VARCHAR(100)` 装得下 ✅） |
| `matches("admin123", hash)` | **true** ✅ |
| `matches("admin124", hash)` | false ✅ |
| `matches(任意密码, "此处替换为BCrypt密文")` | **false 且不抛异常**，日志仅 `警告: Encoded password does not look like BCrypt` ✅ 印证"静默失败伪装成密码错误"的机制 |

② **加启动自检，从根上消灭静默失败**（`config/AdminPasswordInitializer implements ApplicationRunner`）：启动时查 role=2 的账号，若 `password` 不以 `$2a$/$2b$/$2y$` 开头 → 自动重置为 `admin123` 并打醒目 WARN。这样即使操作者忘了替换占位符，也只会看到"密码被重置"的告警，不会卡在"密码错误"。
③ **⚠️ 不要改用 `BCrypt.checkpw()`**：`BCryptPasswordEncoder.matches()` 内部有 `BCRYPT_PATTERN` 正则守卫，非法密文 → warning + `false`；而直接调 `BCrypt.checkpw()` 对非法 salt 会抛 `IllegalArgumentException: Invalid salt`，把"静默失败"变成 500。
④ README 快速启动补「执行 测试商品数据.sql」一步（见 QA-002b）并给演示账号表；种子段改 `INSERT ... ON DUPLICATE KEY UPDATE` 或按外键逆序 `DELETE FROM`。

---

### 🔴 QA-025 订单列表「去评价」主路径必崩（`onMounted` 未 await，读 null.items）

| Field | Value |
|---|---|
| Severity | **critical**（主路径 100% 复现，评价入口在订单列表完全不可用） |
| Category | console / functional |
| 位置 | `views/OrderDetail.vue:307-311`（`onMounted(() => { load(); if (route.query.comment === '1') openComment() })` —— `load()` 未 await）＋ `:274-281`（`openComment()` 直接 `detail.value.items.forEach(...)`）＋ `OrderList.vue:31`（跳转 `/orders/${o.id}?comment=1`） |

**期望 vs 实际**
- 期望：从订单列表点「去评价」→ 详情页加载完成后弹出评价弹窗。
- 实际：`onMounted` 同步执行，此时 `detail.value === null`，`openComment()` 抛 `TypeError: Cannot read properties of null (reading 'items')`（未捕获，控制台红字）→ **弹窗不出现**，用户点完「去评价」只是停留在普通详情页，无任何提示。`load()` 的 Promise 后续仍会 resolve，故页面本身能渲染，缺陷表现为"点了没反应"，极易被误判为"评价功能没做"。

**复现路径**：登录 → 我的订单 → 已完成订单点「去评价」→ 观察控制台 `TypeError`、无弹窗。
**对照路径**：手动进订单详情 → 点「评价商品」按钮 → **正常**（此时 detail 已加载）。→ 说明缺陷只在列表跳转这一条主路径，测试必须两条路径都覆盖。

**建议**：`onMounted(async () => { await load(); if (route.query.pay === '1') openPay(); if (route.query.comment === '1') openComment() })`；`openComment` 首行加 `if (!detail.value) return` 防御。

**漏测根因分析（为什么作者自测没发现）**：`?pay=1` 这条**不崩**——`openPay()` 只置 `payDialog = true`，模板里的 `detail.payAmount` 位于 `v-if="detail"` 内，不会在 null 上取属性。所以"从订单列表去支付"是通的，恰好掩盖了"从订单列表去评价"的坏；而"详情页内点评价按钮"也正常。**两条自测路径全绿，唯一坏的那条（列表→评价）被绕开了**。→ 回归清单必须显式包含"从列表进入"的每一条带 query 的深链（`?pay=1` / `?comment=1`），这是本项目最容易漏测的面。

---

### 🟠 QA-003 购物车结算未过滤已下架商品与失效 SKU

| Field | Value |
|---|---|
| Severity | high |
| Category | functional |
| 位置 | `mapper/CartMapper.java:24-31`（`selectSelectedVOList` JOIN product 但**无** `p.status = 1` 条件，也无 `s.status = 1`）；对比 `OrderServiceImpl.buildLineFromSku:278` 对直接购买路径有状态校验 |

**期望 vs 实际**：期望下架商品无法结算（应提示"商品已下架"并从勾选中剔除）；实际 `fromCart=true` 时下架商品照常下单成功、照常扣库存、销量累加，商品已下架仍在产生订单。

**复现**：商家把商品 A 下架 → 用户购物车里 A 仍勾选 → 去结算 → 下单成功。

**建议**：`selectSelectedVOList` 增加 `AND p.status = 1 AND s.status = 1`；或在 `create()` 里对 `lines` 做状态过滤并对被过滤项返回明确提示（"以下商品已下架，请重新勾选"），保留勾选状态避免用户困惑。

---

### 🟠 QA-004 多商品订单「去评价」以订单粒度聚合，明细评价入口会永久消失

| Field | Value |
|---|---|
| Severity | high |
| Category | functional / ux |
| 位置 | `mapper/OrderMapper.java:20`（`(SELECT COUNT(*) FROM comment c WHERE c.order_id = o.id) AS commented`）；`OrderList.vue:31`（`v-if="o.status === 3 && !o.commented"`）；`OrderDetail.vue:283-305`（批量提交，`for` 循环内 `await` 抛错即中断剩余明细） |

**期望 vs 实际**
- 期望：每个订单明细独立判断是否已评价，未评价的明细始终有评价入口。
- 实际：只要该订单下有 1 条评价，`commented > 0` → 订单列表「去评价」按钮消失；若批量提交时第 2 个明细因"该商品已评价过"报错，循环中断，第 3 个及之后明细**永久失去评价入口**（商品详情也无法评价，只能看到订单级按钮）。

**复现**：同一店铺下单一笔含 2 个 SKU 的订单 → 支付 → 商家发货 → 确认收货 → 评价弹窗提交（两个都填）→ 若其中一个此前已通过其他路径评价过，则第二个报错中断 → 返回订单列表，无「去评价」按钮，剩余明细无法再评价。

**建议**：`OrderVO` 增 `itemCount` 与 `commentedCount`（或用 `未评价明细数 > 0` 判断，SQL 改为 `COUNT(DISTINCT c.order_item_id)` 与 `order_item` 数量比较）；订单详情为每个明细行提供独立「评价」按钮 + 已评价 `el-tag`；批量提交改为 `Promise.allSettled` 并汇总成功/失败。

---

### 🟠 QA-005a 换货（`type=2`）同意后仍被标记「已退款」（原 QA-005 拆分①｜随 P0-B 的 B1 同批改）

| Field | Value |
|---|---|
| Severity | high |
| Category | functional（资金一致性） |
| 位置 | `AfterSaleServiceImpl.java:198-207`（`merchantHandle` 的 agree 分支**无条件**调 `refundPayment(order.getId())`，未判断 `type`）；`:247-257`（`adminHandle` 同） |

**期望 vs 实际**
- 期望：换货不涉及退款（`apply` 已把 `amount` 强制置 0），同意换货不应改动支付状态。
- 实际：换货同意 → 订单回到「已完成」+ `payment.status` 置 2（已退款）→ 订单详情/对账显示"已退款"但钱从未退。

**复现**：已支付订单 → 申请「换货」→ 商家端同意 → 查 `payment.status` 由 1 变 2。

**建议（5 分钟，1 行）**：`if (as.getType() != null && as.getType() != 2) refundPayment(...)`。**必须随 B1 同批改**——改 `running` 集合与状态字典时人就在 `merchantHandle` 这个函数里，顺手改；单列成待办反而容易在后续迭代被忘掉。

---

### 🟠 QA-005b 部分退款不记账：整笔 payment 被标记已退款，且退款后 GMV 不减（原 QA-005 拆分②｜独立，含 schema 变更）

| Field | Value |
|---|---|
| Severity | high |
| Category | functional（运营数据失真） |
| 位置 | `AfterSaleServiceImpl.refundPayment:370-379`（只按 `orderId` 取最后一笔 payment 置 `status=2`，**不看退了多少、退的是哪几件**）；`AdminStatsMapper:25/29/34`（`totalAmount` / `todayStats` / `trend`）；`MerchantStatsMapper:21/25`（`sumAmount` / `sumTodayAmount`）；`payment` 表**无 `refund_amount` / `refund_time` 字段** |

**三处数据失真（已逐条核实 SQL）**

1. **「订单粒度 vs 明细粒度」混用第二处**：`apply` 支持单明细售后（`order_item_id` 粒度），但 `refundPayment(orderId)` 把**整笔支付流水**标记为已退款。用户退了 3 件里的 1 件 → 系统显示"整单已退款"。第一处同类混用是 `OrderMapper:20` 的 `commented` 计数（见 QA-004）。
2. **退款后 GMV 永久不减**（影响管理端总览 + 商家统计两个看板）：4 条统计 SQL 全部只判断 `pay_time IS NOT NULL`，**从不 join/判断 `payment.status`** ——
   ```sql
   AdminStatsMapper:25   SELECT IFNULL(SUM(pay_amount),0) FROM `order` WHERE pay_time IS NOT NULL
   AdminStatsMapper:29   ... todayStats：WHERE pay_time IS NOT NULL AND pay_time >= #{today}
   AdminStatsMapper:34   ... trend：DATE_FORMAT(pay_time) 分组求和
   MerchantStatsMapper:21 SELECT IFNULL(SUM(o.pay_amount),0) ... AND o.pay_time IS NOT NULL
   ```
   → 已全额退款的订单**永久计入**平台总成交额、今日交易额、近 N 日趋势、商家累计/今日营业额。**答辩若被问"你们 GMV 怎么算、退货算不算"，这条会当场暴露**。
3. **无退款金额字段**：`payment` 仅 `amount / pay_type / status / pay_time`，无法表达"部分退款 ¥100 / 已退 ¥100 / 待退 ¥900"，也无法对账。

**建议（45 分钟，含 schema）**：① `payment` 增 `refund_amount DECIMAL(10,2) DEFAULT 0` + `refund_time DATETIME`，`status` 增加 `3 部分退款`；② `refundPayment` 改签名 `(orderId, refundAmount)`：换货直接 return、`refundAmount >= payAmount` 置 2、否则累加 `refund_amount` 置 3；③ 统计 SQL 改为 `SUM(pay_amount) - SUM(IFNULL(payment.refund_amount,0))`（或 `JOIN payment p ON p.order_id=o.id AND p.status=1` 只算未退款）；④ 售后同意时再加一道「累计已退 ≤ 实付」校验（配合 QA-009 的循环退款修复）。

> 与 QA-005a 分开的理由（代码审查交叉确认）：005a 是**调用点 1 行**、改的是 B1 同一函数、5 分钟；005b 是**记账模型缺失**、要动表结构和 4 条统计 SQL、45 分钟。合成的 30 分钟估算只够做 005a，会让人以为都修完了。

---

### 🟠 QA-006 售后/评价唯一性仅靠 select-then-insert，数据库无唯一索引

| Field | Value |
|---|---|
| Severity | high |
| Category | functional（并发/重复提交） |
| 位置 | `AfterSaleServiceImpl.java:66-71`（先 count 再 insert）；`CommentServiceImpl.java:82-87`（同）;建表 SQL `after_sale`（仅 `uk_after_sale_no`）、`comment`（仅普通索引，`order_item_id` 可空） |

**期望 vs 实际**：期望重复申请/重复评价在库层被拒绝（幂等 409）；实际两个并发请求（双击提交、双开标签页、弱网重试）可同时穿过 count 校验，各自插入成功 → 同一明细 2 条进行中售后 → 可被商家各同意一次，产生重复退款动作；同一明细 2 条评价 → 商品评分被重复计入。

**复现**：用两个并发请求（相同 Token 同时发 `POST /api/after-sale`，或 `POST /api/comment`）→ 两条 200，库内 2 条记录。

**建议**：`ALTER TABLE after_sale ADD UNIQUE KEY uk_item_running (order_item_id, status)`（或加 `active_flag` 生成列做部分唯一索引）；`ALTER TABLE comment ADD UNIQUE KEY uk_order_item (order_item_id)`；Service 捕获 `DuplicateKeyException` → 转 `409 该商品已有进行中的售后申请 / 该商品已评价过`。

---

### 🟠 QA-007 支付结果查询存在越权（无订单归属校验）

| Field | Value |
|---|---|
| Severity | high |
| Category | functional（越权） |
| 位置 | `PaymentServiceImpl.java:77-90`（`query(orderId)` 直接按 `orderId` 查，无 `userId` 比对）；`PaymentController.java:28-31` |

**期望 vs 实际**：期望仅订单本人可查支付流水；实际任意登录用户传他人 `orderId` 即可读到 `paymentNo / status / payTime`（支付流水号属敏感单据信息，可用于社工或对账混淆）。

**复现**：用户 A 记下自己的订单 id → 用户 B 登录后 `GET /api/payment/query?orderId=A的订单id` → 返回 A 的支付流水。

**建议**：`query` 内先 `orderMapper.selectById` 并校验 `order.getUserId().equals(uid())`，否则 403；或直接复用 `checkOwnOrder`。

---

### 🟠 QA-008 商家编辑商品会删除并重建 SKU，旧 SKU 引用全部失效

| Field | Value |
|---|---|
| Severity | high |
| Category | functional（数据完整性） |
| 位置 | `MerchantServiceImpl.java:163-173`（`updateProduct` 先 `DELETE FROM product_sku WHERE product_id=?` 再重建） |

**期望 vs 实际**
- 期望：编辑商品（改名/改价/加库存）不应使已有购物车、订单明细、售后单的 SKU 关联失效。
- 实际：重建后 SKU 主键全变 →
  - 购物车项指向已删除 `sku_id`，`selectCartVOList` 的 INNER JOIN 直接**丢弃该行** → 用户看到购物车"商品凭空消失"、勾选结算金额变少；
  - 直接购买链接/收藏页带旧 `skuId` → `buildLineFromSku` 抛 `404 商品规格不存在`；
  - `order_item.sku_id` / `after_sale.order_item_id` 悬空，售后详情无法关联商品。

**复现**：用户把商品加入购物车 → 商家在商品编辑里改一次价格并保存 → 用户刷新购物车 → 该商品行消失。

**建议**：改为按 `skuId` 增量 upsert（有 id 则 update、无 id 则 insert、缺失的置 `status=0` 软删而非物理删）；物理删除前校验是否被 `cart/order_item/after_sale` 引用。

---

### 🟠 QA-009 已完成的售后可对同一明细反复申请退款

| Field | Value |
|---|---|
| Severity | high |
| Category | functional（资金风险） |
| 位置 | `AfterSaleServiceImpl.java:66-71`（只拦 `status in (0,1,3)`，`4 已完成` 与 `5 已撤销` 放行）；`refundPayment` 仅靠 `payment.status==1` 兜底 |

**期望 vs 实际**：期望同一订单明细仅能完成一次退款类售后（换货除外）；实际「已完成（4）」后可立即再提交一次退款申请并再次被商家同意 → 订单/支付状态被再次改写（`payment.status` 已是 2 故不重复打款，但售后单、订单状态、操作日志全部错乱，且若该明细此前是换货，第二次退款会真的把钱退掉，等价于"换货后又退款"）。

**复现**：订单已完成 → 申请「仅退款」→ 商家同意（status=4，payment→2）→ 再次申请「仅退款」→ 未报 409，商家可再次同意。
**更狠的变体（安全审计交叉确认，注意累计口径）**：先申请**部分退款**（如 ¥100/¥1000）→ 商家同意 → 订单回到「已完成」→ 再申请**全额退款** ¥1000 → 仍可提交并被同意 → **累计退款 ¥1100 > 实付 ¥1000**。根因同上：`running` 集合不含终态 4，且没有任何"累计已退金额 ≤ 实付金额"的校验（`amount` 只与该**明细**实付比较，不与该明细**历史已退**累计比较）。

**建议**：`running` 条件改为 `status in (0,1,3,4)`（换货允许再申请时按 `type` 区分）；或引入 `after_sale_seq` 与"每条明细最多 1 次退款 + 1 次换货"的规则，并在同意时校验 `payment.status` 若已非 1 则拒绝。

> **⚠️ 与 QA-014 同源，必须合并修（代码审查交叉核对结论）**：本条的成因正是「`merchantHandle` 同意走 `0→4` 跳过状态 1」+「进行中集合只查 `{0,1,3}` 不含终态 4」。因此
> **只删前端状态 1 的展示项 → 漏洞仍在；只把 4 加进 running 集合 → UI 仍展示永不出现的死状态。**
> 一次性口径：`running = {0,1,3,4}`（+ 对 `order_item_id` 加终态唯一约束）＋ 前端字典删掉状态 1 ＋ 类注释改为「仅 0 可撤销，商家同意即 4 终态」＋ 换货（`type=2`）在同意分支中跳过 `refundPayment`（见 QA-005a）。

---

### 🟠 QA-026 订单状态变更全是"读后写"，并发双请求导致库存双倍回补/重复发货

| Field | Value |
|---|---|
| Severity | high |
| Category | functional（并发竞态，数据完整性） |
| 位置 | `OrderServiceImpl.cancel:220-239`（读 `status==0` → 回补库存 → `updateById`）、`confirm:241-250`、`MerchantServiceImpl.deliver:198-239`、`AfterSaleServiceImpl.merchantHandle:177-217`；对照 `PaymentServiceImpl.pay:47-56` **已正确使用** `LambdaUpdateWrapper.eq(status,0)` 乐观更新 |

**期望 vs 实际**
- 期望：状态流转幂等，第二次请求返回 400/409 且不产生副作用（与 `pay` 的实现口径一致）。
- 实际：`cancel` 为「查 → 校验 → 回补库存/销量 → 无条件 `updateById`」，两次并发 `POST /api/order/{id}/cancel` 都可穿过 `status==0` 校验 → **库存回补 2×quantity、销量扣减 2×**，`product.sales` 出现负数（`GREATEST(...,0)` 只防负值不防多扣）。`deliver` 并发会产生两条 `logistics`（虽有 `uk_order_id` 兜底成 500）、`merchantHandle` 并发会对同一售后单重复退款标记。

**复现**：对一个 `status=0` 订单并发 2 次 `POST /api/order/{id}/cancel`（k6/JMeter 双请求，或浏览器双击，窗口窄但成立）→ 查 `product_sku.stock` 增加 2×quantity。

**建议**：统一改为条件更新——`update ... set status=4 where id=? and status=0`，`affected==0` 即抛「订单状态已变更，请刷新」；`confirm`/`deliver`/`merchantHandle` 同改。回归时必须对 cancel / confirm / deliver / pay / 售后处理 **每个接口各做一次并发双请求**。

---

### 🟡 QA-027 单一明细申请售后会使整单进入「售后中」，其他明细的确认收货被阻断

| Field | Value |
|---|---|
| Severity | medium |
| Category | functional / ux |
| 位置 | `AfterSaleServiceImpl.apply:106-108`（任一明细申请即 `order.setStatus(5)`）；`OrderServiceImpl.confirm:242-246`（仅 `status==2` 可确认收货）；`OrderDetail.vue:12`（按钮 `v-if="detail.status === 2"`） |

**期望 vs 实际**：期望售后以「订单明细」为粒度，部分退款不影响同一订单其他商品的正常收货流程；实际对 1 件商品申请售后后整单 `status→5`，「确认收货」按钮消失、`confirm` 接口返回「仅待收货订单可确认收货」，用户只能先撤销售后（`restoreOrder` 才能回到 2）才能继续收货。

**建议**：短期在 UI 上明示"该订单售后处理中，其他商品暂不可确认收货"；中期将售后中标记下沉到 `order_item` 维度（或 `order.status` 保留 2/3，另设 `after_sale_flag`），仅当全部明细售后中时才阻断收货。

---

### 🟡 QA-028 商品详情全量加载评价算均值（无分页、无聚合）

| Field | Value |
|---|---|
| Severity | medium |
| Category | performance |
| 位置 | `ProductServiceImpl.java:111-121`（`commentMapper.selectList(status=1)` 拉全量后在 Java 端 `mapToInt().average()`） |

**期望 vs 实际**：期望用 `SELECT COUNT(*), AVG(product_score), AVG(logistics_score) FROM comment WHERE product_id=? AND status=1` 一条聚合查询；实际把该商品**所有已展示评价整行加载到内存**再算均值——爆款商品 500+ 条评价时，每次打开详情页都拖全表、DTO 反序列化开销随评价数线性增长，且 `CommentSummaryVO` 只需要 3 个数字。

> 与 QA-012 分离：QA-012 是 status 过滤缺失（正确性），本条是查询方式低效（性能），**修一处不会顺带修好另一处**（代码审查侧 S3 的第二子项）。

**建议**：`CommentMapper` 加 `@Select("SELECT COUNT(*) total, AVG(product_score) avgP, AVG(logistics_score) avgL ...")` 返回 `CommentSummaryVO`；列表接口 `productComments` 本来就分页，摘要不该走 list。

---

### 🟡 QA-029 `UserContext` 在 preHandle 抛异常时不被清理，线程复用可能残留上一个用户

| Field | Value |
|---|---|
| Severity | medium（当前公开接口未读 uid，暂不产生越权，但是高危隐患） |
| Category | functional（身份混淆隐患） |
| 位置 | `JwtInterceptor.java:79`（`UserContext.set(...)`）→ `:82-91`（角色校验可能抛 403）→ `:100-104`（清理只写在 `afterCompletion`）；Spring 对 **preHandle 抛异常的拦截器不会回调其 `afterCompletion`** |

**触发面已被钉死为唯一路径（代码审查逐行核对）**：`UserContext.set()` 在 `:79`，而它**之前**的所有抛点都在 set 之前——

| 行 | 抛点 | 是否留下残留 |
|---|---|---|
| `:53` | 无 Authorization → 401 | ❌ set 之前 |
| `:65` | Redis 不可用 → 503 | ❌ set 之前 |
| `:68` | Redis token 不匹配 → 401 | ❌ set 之前 |
| `:74/:77` | 账号不存在 / 被禁用 → 401 | ❌ set 之前 |
| **`:90`** | **角色不匹配 → 403** | ✅ **唯一残留路径** |

因此不是"任意 preHandle 异常"，而是精确的「**持有效 Token 但角色不匹配的 403**」。这一点对修复有用：不需要全量防御，只需覆盖该路径（当然 `preHandle` 首行无条件 `clear()` 是最省事的一刀）。

**残留存活窗口：不是"一个请求"，而是「取决于 403 流量」（代码审查进一步推演，已采纳）**
- 该 403 请求**自身**不触发 `afterCompletion`（`interceptorIndex` 停在 `-1`）→ 残留跨过该请求；
- **更关键**：`UserContext.set()` 在角色校验**之前**（`:79`），所以**每一次新的 403 请求都会重新 set 一次再抛** → 攻击者持续打 403 接口（例如普通用户反复调 `/api/admin/**`）时，该线程的残留**永不清理且被反复刷新**；
- 并发下**多条 Tomcat 线程同时变脏**，各自残留不同用户 ID；公开接口请求落到任意脏线程 → handler 读到别人的 uid。

→ 所以窗口 = **"取决于 403 流量"**：403 打得越多、脏线程越多、公开请求命中脏线程的概率越高。这把 QA-029 从"加一行日志才炸的定时炸弹"提升为**"混合流量下的越权概率问题"**。
**定级说明**：仍保持 medium——因为当前**没有任何公开接口读取 uid**，属"不可利用但可达"。**升级规则**：一旦出现"公开接口 + 读 `UserContext`"的组合（埋点、个性化推荐、浏览历史），立即升为 high。

**确定性复现方案（MockMvc 在测试线程内同步执行，残留 100% 可复现）**：① 请求 1：用户 A（role=0）打 `/api/admin/user/page` → 断言 403；② 请求 2（同一测试线程）：先注册一个 `@TestConfiguration` 探针 Controller，读取 `UserContext.getUserId()` 并返回；③ 断言探针读到的是 **A 的 ID**（而非 null）→ 复现成功；修复后同一用例断言应为 **null**。→ 建议直接作为该修复的验收用例，比手工点页面可靠。

**建议**：`preHandle` 第一行无条件 `UserContext.clear()`；或用 `try/finally` 包住全部逻辑；或改用 `RequestContextHolder`/`@RequestScope` bean 替代 ThreadLocal。

> **→ 与 QA-032 同根因，建议合并表述**：本条的"`@RequireRole` 缺省即公开"与 QA-032 的"`shopId == null` 缺省即全量"是**同一个设计病根——默认值是"放行"**。两处单列都是小问题，合并后是一条可写进答辩的授权设计结论：「授权一律默认拒绝 + 显式 `@Public`」。详见 QA-032。

---

### 🟠 QA-030 短信验证码无试错计数，6 位码可在线爆破（安全审计交叉确认）

| Field | Value |
|---|---|
| Severity | high |
| Category | functional（账号接管风险） |
| 位置 | `AuthServiceImpl.verifyCode:223-236`（仅校验值，**无失败计数/锁定**）；`sendSmsCode` 的 60 秒限流只限制「发送」不限制「校验」；`forgotPassword:143-160` 仅凭「手机号 + 验证码」即重置密码 |

**期望 vs 实际**：期望同一 `phone+type` 连续错误 5 次即锁定（或验证码作废并强制重发）；实际可对同一验证码无限次提交，5 分钟有效期内可尝试 10⁶ 个组合 → 配合 `forgot` 流程等于**任意手机号账号可被接管**（只要有 5 分钟时间窗与并发能力）。`verifyCode` 只在**成功时**删除 key，失败不计数。

> 本条由安全审计独立发现（其报告第 3 项），QA 侧登记为**验收测试项**：必须写"连续 6 次错误验证码 → 第 6 次应返回锁定提示"的负向用例；整改由安全报告主导。

**建议**：`sms:fail:{type}:{phone}` 计数，≥5 次 `del` 掉验证码 key 并加 `sms:lock` 60s；成功时清零；同时对 `forgot` 加图形/滑块或按 IP 限流。

---

### 🟡 QA-031 口令经 URL query 传输（`PUT /api/auth/password`、`POST /api/auth/forgot`）

| Field | Value |
|---|---|
| Severity | medium |
| Category | functional（凭据泄露面） |
| 位置 | `AuthController.java:52-54`（`@RequestParam String oldPassword, String newPassword`）、`:60-63`（`@RequestParam String phone, code, newPassword`） |

**期望 vs 实际**：期望口令走请求体（JSON body）；实际放在 query string → 会进入 Nginx/Tomcat **access log**、浏览器历史、Referer，且 `request.js` 的 axios 拦截器日志、抓包/代理工具均可见明文口令。前端 `Login.vue:139-153` 与 `AuthController` 配套调用时就是这样传的。

> 安全审计第 5 项独立发现；QA 侧登记为测试项（验收时检查 access log 是否含明文口令即可证实）。

**建议**：改 `@RequestBody ChangePasswordRequest{oldPassword,newPassword}` / `ForgotRequest{phone,code,newPassword}`，前端 `request.put('/auth/password', {oldPassword, newPassword})`；顺带把 `sms-code` 之外的敏感参数全部从 `@RequestParam` 迁到 body。

---

### 🟡 QA-010 未支付订单无超时自动关闭

| Field | Value |
|---|---|
| Severity | medium |
| Category | functional |
| 位置 | 全仓库无定时任务/`@Scheduled`；`OrderServiceImpl.cancel` 仅用户手工触发 |

**期望 vs 实际**：期望下单 30 分钟后未支付自动取消并回补库存；实际订单永久停在 `status=0`，库存被长期占用，商品可售库存逐步"蒸发"（演示时反复下单不支付会导致"库存不足"误判）。

**建议**：加 `@Scheduled` 扫描 `status=0 AND created_time < now-30min` → 复用 `cancel` 逻辑（含库存/销量回补）；或至少在发布检查清单里写明"演示前清理未支付订单"。

---

### 🟡 QA-011 下单无幂等控制，重复提交会产生多笔订单

| Field | Value |
|---|---|
| Severity | medium |
| Category | functional |
| 位置 | `OrderServiceImpl.create`（无幂等 Token / 无唯一业务键）；前端 `Checkout.vue:146` 仅 `submitting` 局部去抖，`request.js` 超时 60s |

**期望 vs 实际**：期望重复提交（双击、超时重试、刷新重发）只生成一笔订单；实际 `fromCart=true` 路径因下单后清空勾选项而"意外幂等"，`fromCart=false`（立即购买）路径每次调用都新建订单并再次扣库存。

**建议**：前端提交前生成 `idempotencyKey`（UUID 存 sessionStorage）随 body 上送，后端用 Redis `SETNX order:req:{userId}:{key}` 幂等表拦截；或对 `(userId, skuId, 数量, 5 分钟内)` 做重单提示。

---

### 🟡 QA-012 热门商品缓存不校验上下架、不主动失效

| Field | Value |
|---|---|
| Severity | medium |
| Category | functional / performance |
| 位置 | `ProductServiceImpl.java:165-191`（`hot()`：命中 ZSet 后 `selectBatchIds(ids)` 无 `status` 过滤；缓存只增不删，销量变化也不刷新） |

**期望 vs 实际**：期望首页热销只含上架商品且随销量更新；实际下架商品在被缓存后仍长期出现在首页热销位，点击进入详情页可正常打开（`detail` 也不校验 `status`，草稿/下架商品对任意人可访问）→ 用户可下单（叠加 QA-003）。

**建议**：`selectBatchIds` 后过滤 `status==1`；下架/上架时 `zset.remove(key, id)`；或改用 `TTL 300s` 的短缓存 + 命中后按 `sales` 重排。

---

### 🟡 QA-013 导购 SSE 前端无状态码/超时/中断处理，失败时界面"空白气泡"

| Field | Value |
|---|---|
| Severity | medium |
| Category | ux / console |
| 位置 | `api/chat.js:17-50`（`fetch` 未检查 `resp.ok`，无 `AbortController`、无超时）；`ChatView.vue:114-129`（先 push 空气泡，仅 `catch` 时 `pop`；`onDone` 未触发则气泡永久空白）；后端 `ChatServiceImpl.send` 无心跳 ping，`Thread.sleep(25)` 逐片推送占用请求线程 |

**期望 vs 实际**：期望 Token 过期（401）、后端 500、LLM 90s 超时（`LlmClient` 已设 `.timeout(90s)`）时给出明确错误并清理气泡；实际 `resp.body` 读到的是 JSON 错误体，按 SSE 帧解析后无 `data:` 行 → 事件全空 → 气泡留白，用户无任何反馈（`finally` 只关 loading）。

**建议**：`if (!resp.ok) { const j = await resp.json().catch(()=>null); throw new Error(j?.message||'导购服务异常') }`；`AbortController` + 前端 100s 超时；后端每 10s 写 `event: ping` 保活；错误时后端在流内补 `event: error` 事件。

---

### 🟡 QA-014 售后 `status=1` 为死状态；注释/实现/README 三方描述不一致

| Field | Value |
|---|---|
| Severity | medium |
| Category | content |
| 位置 | `AfterSaleServiceImpl.java:30`（类注释："0/1/3 可撤销为 5"）vs 实现 `cancel` 仅允许 `status==0`（`:139`）vs `README.md:64`（仅 0 可撤销）；`merchantHandle` 同意直接 `0→4`，`status=1` 永远写不进去，但前端/管理端下拉框仍暴露「商家同意」选项 |

**期望 vs 实际**：期望状态机文档、代码、UI 三者一致；实际存在不可达状态 `1` 与过时注释，答辩演示"撤销已进入平台介入的售后单"时会被质疑（实现返回 `400 仅待商家处理的售后单可撤销`）。

**建议**：统一为「仅 `0` 可撤销」并同步修正类注释；删除前端 `1 商家同意` 展示项或改为"同意即完成"的文案；若确需 `0→1→4` 两段式，则补齐 `1` 的写入与撤销支持。

> **本条与 QA-009 是同一根因的两面，必须一并修**（详见 QA-009 末尾的统一口径）。代码级铁证：`AfterSaleServiceImpl` 中 `setStatus` 只写入 `0/2/3/4/5`（`:103 / :142 / :162 / :199 / :212 / :237`），**状态 1 从不被赋值**，仅存在于前端展示字典与文档描述中。

---

### 🟡 QA-015 评价提交即待审核（status=0），商品详情不可见，前端无任何提示

| Field | Value |
|---|---|
| Severity | medium |
| Category | ux |
| 位置 | `CommentServiceImpl.java:41`（`DEMO_AUTO_PUBLISH=false`）、`:102`；`ProductServiceImpl.detail:111-113`（仅 `status=1` 计入摘要与列表）；`OrderDetail.vue:298`（仅提示"评价成功"） |

**期望 vs 实际**：期望提交后提示"评价已提交，审核通过后展示"并告知去哪看进度；实际提示"评价成功！"，用户回商品详情页**看不到自己的评价**（评分/条数都不变），误判为功能失效——这是答辩现场最容易被追问的"看起来坏了"的点。

**建议**：文案改为「评价已提交，待平台审核后展示」；商品详情增加"我的待审核评价"入口或订单详情展示评价状态；演示脚本必须包含「管理端 → 评价审核 → 通过」这一步。
另：`DEMO_AUTO_PUBLISH` 是 `private static final` **硬编码**，切 `true` 需重新编译，答辩现场无法临时切换 → 建议改为 `@Value("${app.comment.auto-publish:false}")`，默认 `false`（守正式流程），答辩前用 `application-local.yml` 或环境变量临时开启，自带兜底能力。

---

### 🟡 QA-016 分页参数无上限 + Redis 全键扫描 + N+1 查询

| Field | Value |
|---|---|
| Severity | medium |
| Category | performance |
| 位置 | 各 Controller `@RequestParam(defaultValue="10") long pageSize`（`OrderController:37`、`ProductQuery.pageSize`、`AdminController` 等，均无 `@Max`）；`ProductRetrievalService.java:117,150`（`redisTemplate.keys(pattern)`）；`ProductServiceImpl.toVOList:239`（按 shopId 批量查，但 `detail` 内多次单查） |

**期望 vs 实际**：期望 `pageSize ≤ 100`；实际 `pageSize=100000` 会一次性拉全表（管理端用户列表/操作日志尤甚）→ 前端渲染卡死、连接池占满；`KEYS product:embedding:*` 在向量库增长后阻塞 Redis 单线程（生产禁止）。

**建议**：统一加 `@Max(100)` 或服务层 `Math.min(pageSize, 100)`；`KEYS` 改 `SCAN` 或维护 `product:embedding:index` Set。

---

### 🟠 QA-017 短信验证码写入应用日志（C3｜部署前必须关闭，需开关化）

| Field | Value |
|---|---|
| Severity | **high**（三方对齐后由 medium 上调：与 QA-030 构成"读日志即接管账号"组合拳） |
| Category | functional / 安全（详见安全审计 F-006） |
| 位置 | `AuthServiceImpl.java:188-195`（`log.info("【模拟短信】...验证码：{}")`），**无 profile 开关，任何环境都打** |
| 阻塞层 | **C3 条件阻塞**——在"非 localhost 访问"场景下升为阻塞 |

**期望 vs 实际**：期望生产环境走短信网关；实际当前无环境开关，任何 profile 都把 6 位验证码打进日志文件 → 能读日志即可重置任意账号密码（`forgot` 仅需手机号 + 验证码）。

**组合风险（三方共识）**：C3（日志里能读到码）+ C1（无需读码，可直接爆破 5 分钟窗口）= 双通道账号接管。单条看是中危，**组合利用链是上线阻塞级**——这是定级上调的唯一依据。

**建议（开关化，而非简单删日志）**：① 新增配置 `app.sms.echo-code`（默认 `false`）；② `local` / `demo` profile 可置 `true` 便于答辩演示；③ **非 local profile 且 `echo-code=true` 时启动 fail-fast 直接失败**（杜绝"演示代码被带上线"）；④ 发送逻辑抽 `SmsSender` 接口，`MockSmsSender`（local）/ `ProdSmsSender`（真实网关），生产 profile 缺实现则启动失败。

---

### 🟡 QA-018 前端多处静默 catch，后端不可用时表现为"空白/无数据"

| Field | Value |
|---|---|
| Severity | medium |
| Category | console / ux |
| 位置 | `ChatView.vue:79,100`（`catch (e) { /* 后端未实现 */ }`）、`:147`；`OrderDetail.vue:180`；`AfterSaleList.vue` 同类 |

**期望 vs 实际**：期望接口失败给出可操作提示；实际 `catch` 空实现 → 会话/消息/售后列表静默变空，用户以为数据丢了；注释"后端未实现"已过时（后端已实现），会误导后续维护。

**建议**：统一走 `request.js` 的 `ElMessage.error`，`catch` 内至少 `messages.value = []` + 提示；清理过时注释。

---

### 🟢 QA-019 `UserController` 存在空壳接口（个人资料、收藏夹）

| Field | Value |
|---|---|
| Severity | low |
| Category | functional |
| 位置 | `UserController.java:25-91`：`profile` / `updateProfile` / `favoriteList` / `addFavorite` / `removeFavorite` / `checkFavorite` 全部 `Result<Void>` 空实现（无 Service 层） |

**实际**：接口返回 `200 {code:200, data:null}`——看着成功其实什么都没做。前端当前未接入（`grep favorite` 无引用），故不阻断运行，但**验收风险很高**：`数码电商平台-后端接口文档.html` 中"收藏"出现 **11 处**，且含「收藏返回 409」这类**冲突语义描述**（对应建表 SQL 里确实存在的 `uk_user_product`）→ 文档是按"已实现且带唯一约束"写的，**按文档验收的人 100% 会判定收藏/个人资料已完工**。前端若后续接入，`checkFavorite` 恒返回 null 会让收藏按钮永远显示"未收藏"。

**建议**：删除或标注 `@Deprecated / 未实现`，避免答辩时被追问"收藏功能呢"。

---

### 🟢 QA-020 配置项明文：DB 密码与 JWT secret 硬编码在已提交的 `application.yml`

| Field | Value |
|---|---|
| Severity | low（安全侧已覆盖，QA 侧登记） |
| 位置 | `application.yml`：`username: root` / `password: 123456` / `jwt.secret: digital-mall-secret-key-please-change-in-production-0123456789` |

**说明**：LLM Key 已正确外置到 gitignore 的 `application-local.yml`（且 `git log --all -- application-local.yml` 无历史记录 ✅ 未泄漏）。DB/Redis/JWT 仍为明文本地默认值，本地/演示可接受，上线须环境变量注入。

---

### 🟢 QA-021 CORS 允许任意来源且携带凭证

`WebConfig.java:28-35`：`allowedOriginPatterns("*") + allowCredentials(true)`。演示可接受；上线需白名单化，否则配合 JWT 放 localStorage 的组合更易被横向利用。

### 🟢 QA-022 前端产物体积

`AdminDashboard` 1,152 kB / `index` 1,211 kB（gzip 386/389 kB）。echarts 全量引入未按需。建议 `manualChunks` 拆分 echarts、`build.chunkSizeWarningLimit` 之外再做路由级懒加载（当前视图已懒加载，问题在 echarts 与 element-plus 全量）。首屏 390 kB gzip 在校园网可接受，非阻塞。

### 🟢 QA-023 评价能力缺口：无逐条状态、无图片上传、无追评

后端 `SubmitCommentRequest.imageUrls` 已支持，前端评价弹窗未提供上传入口（`image_urls` 永远为空）；商品详情评价列表无"已评价/待审核"区分。属功能完整性建议，非缺陷。

### 🟢 QA-024 外链占位图，离线演示风险

`Cart.vue / Checkout.vue / OrderDetail.vue` 用 `https://via.placeholder.com/80` 兜底，`测试商品数据.sql` 商品图亦为占位 URL。答辩教室断网时全站图片空白，建议改本地 `public/placeholder.png`。

---

### 🟢 QA-032 授权判定两处"缺省即放行"：`@RequireRole` 不标即公开、评价管理的 `shopId == null` 即全量（与 QA-029 同根因，合并为横向建议）

| Field | Value |
|---|---|
| Severity | **low**（但**修复优先级高于普通 🟢**，见"定级说明"） |
| Category | security / design（fail-open 授权） |
| 位置 | ① `security/JwtInterceptor.java:82-91`（只有标了 `@RequireRole` 的接口才校验，**未标注 = 公开**）；② `CommentServiceImpl.managePage:114-128` + `CommentMapper.selectManageComments:37`（`<if test='shopId != null'> AND p.shop_id = #{shopId} </if>`） |
| 交叉编号 | 安全审计 **F-022**（fail-open 角色判断）；归口 QA 侧 |
| 阻塞层 | **无**——正常链路不可达（精度说明见下） |

**代码证据（逐行核对，与安全审计口径一致）**

```java
// CommentServiceImpl.managePage:115-124
Integer role = UserContext.get() == null ? null : UserContext.get().role();
Long shopId = null;                       // ← 缺省值 = 不过滤店铺
if (role != null && role == 1) {          // 只有商家才收敛
    Shop shop = shopMapper.selectOne(...);
    if (shop == null) throw new BusinessException(403, "请先完成商家入驻");  // 已正确 fail-closed
    shopId = shop.getId();
}
commentMapper.selectManageComments(page, shopId, status, keyword);  // shopId=null → 全平台
```

`selectManageComments` 的 SQL 里 `shopId` 条件包在 `<if test='shopId != null'>` 内 → **`shopId == null` 等于"不加店铺过滤"，返回全平台评价**。三种取值下的行为：

| role | shopId | 实际效果 | 是否预期 |
|---|---|---|---|
| 1（商家） | 本店 ID | 仅本店 | ✅ |
| 1（商家）但无店 | — | **403「请先完成商家入驻」** | ✅ 已 fail-closed |
| 2（管理员） | `null` | 全平台 | ✅ 设计如此 |
| **`null` / 其他值（如未来 `role=3` 客服）** | **`null`** | **全平台（静默）** | ❌ **fail-open** |

**期望 vs 实际**：期望"拿不到授权上下文 → 拒绝"；实际"拿不到 → 放行，且放行到范围最大的那个视图"。一旦 `role` 出现第三类取值（新增角色、`UserContext` 异常、反序列化空值），商家端会**静默**看到全平台评价，无任何报错、无任何日志。

**定级说明（为什么是 🟢，但优先级高于普通 🟢）**：`@RequireRole({MERCHANT, ADMIN})` 在拦截器层已保证 `role ∈ {1,2}`，故 `role ∉ {1,2}` 在**正常链路不可达**，不构成现网可利用缺陷 → 定 low 正确。但它的**失败方向是最坏的一类**：越权拿到**全量数据**且**静默无痕**，与 QA-020/021 这种"配置不理想"不同性质 → 修复优先级应排在普通 🟢 之前。

**合并后的横向建议（本报告最终口径）**

> QA-029（`UserContext` 残留）+ QA-032（`managePage` 缺省全量）+ `@RequireRole` 缺省公开，**三条同一个病根：默认值是"放行"，安全状态需要每个接口显式声明。**
> - `JwtInterceptor`：**没标 `@RequireRole` = 公开**
> - `managePage`：**`shopId == null` = 全量**
>
> 统一改为**默认拒绝 + 显式 `@Public`**：拦截器侧"未标注 `@RequireRole` 一律 403，公开接口显式标 `@Public`"——白名单从"记得加注解"收敛到一处可审计的注解；`managePage` 侧"先确定授权上下文再查询"。

> 这条合并的额外价值（答辩可用）：若被问"你的权限体系怎么设计的"，当前答案是"**靠每个接口记得加注解**"；改造后可以答"**默认拒绝 + 显式声明，白名单只有 11 条公开接口**（4 条认证 + 5 条商品查询 + 热门问题 + 商品评价列表，即基线第 11 条）"。前者是习惯，后者是设计——一条能审计的白名单，比"每个接口都记得加注解"结实得多。

**修复参考（安全审计给定口径：抛 403，不返回空列表）**

```java
// CommentServiceImpl.managePage 开头
if (role == null || (role != 1 && role != 2)) throw new BusinessException(403, "无权访问");
if (role == 1) shopId = requireShop().getId();   // 需先补该方法，见下方落地提醒
```

> ⚠️ **落地提醒（QA 侧核对代码时发现）**：安全审计给的参考片段用了 `requireShop()`，但该方法当前只存在于 `AfterSaleServiceImpl:381` / `ComplaintServiceImpl:39` / `MerchantServiceImpl`，**`CommentServiceImpl` 里没有**。直接照贴会编译不过——所以本条的改动量口径是"3 行判断 +（可选）抽 6 行私有方法"，不是"3 行"。

**为什么选"抛 403"而不是"返回空列表"**（两条理由，供评审/答辩时用）
1. **语义正确性**：`role==1 但 shop==null` 是**异常状态**（商家无店），属"权限上下文不成立"，不是"本店没有评价"。返回空列表会把配置/数据异常**伪装成正常空态**，商家与排查者都被误导；抛 403 与 `managePage:120-122` 内联的既有行为、以及 `AfterSaleServiceImpl` / `ComplaintServiceImpl` 的 `requireShop()` 完全一致，错误模式统一。
2. **fail-closed 的本意**：拿不到授权上下文时默认动作必须是"**拒绝且可观测**"。空列表是静默放行（虽放行的只是空数据），只有异常上抛才会进 `GlobalExceptionHandler` 的统一错误码，日志/监控才看得见。

**顺带全局扫**：同类 `if (role != null && ...)` 的写法建议全仓扫一遍（`grep -rn "role != null" src/`），把"role 为空 → 跳过校验"的分支统一改为"role 为空 → 拒绝"。

**验收（对应测试集 #21）**：role=2 → 全量；role=1 且有店 → 仅本店；role=1 且无店 → 403 可读提示（**不是空列表**）；role 取非法值 → **403（不是全量）**。

---

## 现有测试覆盖评估

| 维度 | 现状 | 评价 |
|---|---|---|
| 后端单元测试 | **`code/backend/src/test` 目录不存在**，141 个 Java 类 0 用例 | ❌ `pom.xml` 已引入 `spring-boot-starter-test`（test scope）但从未使用；`mvn test` 无测试执行 |
| 后端集成测试 | 无 `@SpringBootTest`、无 MockMvc、无 Testcontainers | ❌ |
| 前端单元测试 | 无 vitest/jest 依赖，`package.json` 无 `test` 脚本，无 `__tests__` | ❌ |
| 前端 E2E | 无 Playwright/Cypress | ❌ |
| 仓库自带脚本 | `验证-测试数据SQL.py`（sqlparse 校验字符数/括号/语句块）、`提取-交易模块接口.py` | ⚠️ 仅校验 SQL 文本结构，**无业务断言、不连库、不覆盖任何业务分支** |
| 手工验证痕迹 | `run-backend.log`、`verify-backend.err.log`、`build-check.log` 等本地日志（已 gitignore） | ⚠️ 一次性人工验证，不可回归 |
| 构建可用性 | 后端 `mvn -o compile` ✅、前端 `npm run build` ✅ | ✅ 可作为发布门禁的"编译关" |

**结论：有效自动化测试覆盖率 ≈ 0%（0/141 类、0/16 页面）**。当前所有质量保证依赖"人工点一遍"，对交易金额、库存扣减、售后状态机、SSE 事件序列这四类高风险逻辑无任何回归保护——任何一次改动都可能静默破坏主流程。

**⚠️ 断言口径（写给后续执行测试的人）**：本项目业务错误统一返回 **HTTP 200 + body `{code, message}`**（`GlobalExceptionHandler` 对所有 `BusinessException` 都返回 200，只有路径不存在才是真 404）。因此**断言必须看 `body.code`，不能看 HTTP 状态码**，否则"应该 400/403/409"的负向用例会整批假通过——这是最容易埋进假绿灯的坑。

**Mock/数据准备提示**：后端所有 Service 均直接依赖 Mapper 接口，无 Repository 抽象，`@MockBean` 需要 mock 10+ 个 Mapper（`OrderServiceImpl` 依赖 10 个），建议关键用例走 H2/测试库集成测试而非纯单测；`UserContext` 是 ThreadLocal，单测需在 `@BeforeEach` 手动 `UserContext.set(...)`、`@AfterEach` `clear()`，否则所有用例变 401 且看不出原因。

**三个会写出"假用例"的陷阱（写测试前必读）**

| # | 陷阱 | 后果 | 正确做法 |
|---|---|---|---|
| 1 | 断言 HTTP 状态码 | 业务错误统一返回 **200 + body.code**，负向用例全绿假通过 | 断言 `body.code` |
| 2 | 用 `@SpringBootTest` 纯 Service 测试验越权 | **`@RequireRole` 是 `HandlerInterceptor`，service 层根本不触发** → "看起来测了越权其实没测" | 越权用例必须走 `MockMvc` 全栈 |
| 3 | 用 MockMvc 的 `andReturn().getResponse().getContentAsString()` 断言 SSE | `ChatService.send` 直接写 `HttpServletResponse` 且流式分批 flush，聚合内容可能为空/不完整 | 直接用 `MockHttpServletResponse` 拿 `getContentAsString()`，并断言 `event:` 行顺序（`intent→products→answer→done`） |

**已确认的正面回归基线（14 条，改动后不应退化，安全审计已验证）**

> ⚠️ **B1 / B2 的改动半径重点回归第 2 / 6 / 7 / 8 条**：B1 动 `AfterSaleServiceImpl`（`running` 集合、状态字典、`refundPayment` 调用点）→ 直接相邻 **第 2 条「售后详情三方可见性」**；B2 动 `cancel/confirm/deliver/merchantHandle` 的状态条件更新 → 直接相邻 **第 6/7/8 条「商家跨店 403」**。这两处是"修好一个坏一个"最可能发生的位置。

1. 订单：他人订单 detail / cancel / confirm / track → 403
2. 售后：他人售后单 detail / cancel → 403；跨店商家 handle → 403
3. 地址：使用他人 `addressId` 下单 → 403
4. 购物车：他人 cartId 改数量/勾选/删除 → 403
5. 导购会话：他人 sessionId 发消息/看历史/删除 → 403
6. 商家：跨店商品编辑/上下架 → 403
7. 商家：跨店订单发货 → 403
8. 商家：跨店售后处理 / 咨询回复 → 403
9. 商家：跨店评价回复 → 403（`CommentServiceImpl.reply` 的 EXISTS 子查询）
10. 管理端：所有 `/api/admin/**` 非 role=2 → 403
11. 未标注 `@RequireRole` 的接口**共 11 条映射**：`/api/auth/{register,login,forgot,sms-code}`（4）、`/api/product/{page,{id},category/tree,brand/list,hot}`（5）、`/api/chat/hot-questions`（1）、`/api/comment/product/{id}`（1）—— 全部为只读或认证类，**无越权泄露面**；这也正是 QA-032 建议改为"默认拒绝 + `@Public` 白名单"时要显式标注的那 11 条
12. **售后同意 → `payment.status` 由 1 置 2**（`refundPayment` 改动半径内，安全审计提醒）
13. **`restoreOrder` 依 `finishTime` 是否为空回退到 2 或 3**（撤销/拒绝后订单状态不错乱，`refundPayment` 同函数域改动半径内）
14. **评价管理三条视图正确**：role=2 → 全平台；role=1 且有店 → 仅本店；role=1 且无店 → 403「请先完成商家入驻」（`managePage` 改动半径内，与 QA-032 同批验证）

> **第 13 条的机理注脚（安全审计补充，已采纳）**：`restoreOrder` 用 `finishTime != null` 反推"售前是待收货还是已完成"，本质是把**状态机迁移历史编码在一个业务时间戳字段上**——典型的**隐式状态推导**。任何改动 `finishTime` 写入时机的迭代（自动确认收货、超时关单、批量补数据）都会**静默污染**这个判断，且不会有任何报错。长期解是订单表加 `pre_aftersale_status` 显式字段（申请售后时快照原状态）；短期靠这条基线兜住——**改动 `finishTime` / `OrderServiceImpl.confirm` / 任何定时任务时必须手工走一遍第 13 条**。

**最小可行测试集建议（按 ROI 排序，共 21 个用例，2~3 人日）**

| # | 用例 | 类型 | 断言要点 |
|---|---|---|---|
| 1 | 超库存下单 | 单元（Mock Mapper） | `ge(stock,qty)` 未命中 → 400「库存不足」，无订单落库 |
| 2 | 同 SKU 并发下单 10 线程 | 并发（H2/测试库） | 成功笔数 ≤ 初始库存，`stock` 不出现负数 |
| 3 | 数量为 0 / 负数 / null | 参数化单元 | 全部 400，库存与金额不变（覆盖 QA-001） |
| 4 | 空购物车结算 | 单元 | 400「请先勾选要结算的商品」 |
| 5 | 售后状态机 6 条合法流转 | 单元 | 0→4、0→2→3→4、0→5、非法流转 400 |
| 6 | 退款金额上限 | 单元 | `amount > item.totalAmount` → 400 |
| 7 | 售后重复申请 | 并发/顺序 | 第二次 409（覆盖 QA-006） |
| 8 | 换货同意 | 单元 | `payment.status` 保持 1（覆盖 QA-005a） |
| 9 | 支付乐观锁 | 单元 | 两次 `pay` 第二次「订单状态不允许支付」，只 1 条 payment |
| 10 | 单端互踢 | 集成（Redis mock） | 旧 Token 请求 → 401；改密后旧 Token 失效 |
| 11 | SSE 事件序列 | MockMvc | 依次 `intent`→`products`→`answer`→`done`，字段齐全，无 Key 时走本地模板 |
| 12 | 越权 | MockMvc | 他人 `orderId` 的 detail/pay/query 全部 403（覆盖 QA-007） |
| 13 | 取消/确认并发双请求 | 并发 | 第二次 400「状态已变更」，库存只回补 1 次（覆盖 QA-026） |
| 14 | 列表→去评价主路径 | 手工/E2E（唯一必须手测项） | 详情页无 TypeError，弹窗正常打开（覆盖 QA-025；手动进详情点按钮是**正常**的，两条路径都要跑） |
| 15 | 验证码试错锁定 | MockMvc | 连续 6 次错误验证码 → 第 6 次返回锁定/验证码已失效（覆盖 QA-030） |
| 16 | 售后累计超退 | 集成/H2 | 部分退款 ¥100 → 同意 → 再申请 ¥1000（实付 ¥1000）→ 应拒绝（覆盖 QA-009 变体） |
| 17 | 评价摘要走聚合 | 单元 + SQL 计数 | 商品详情只发 1 条聚合查询，不再 `selectList` 全量评价（覆盖 QA-028） |
| 18 | 退款后 GMV 应减少 | 集成/H2 | 全额退款后管理端 `totalAmount` 与商家 `sumAmount` 均减少该笔（覆盖 QA-005b） |
| 19 | UserContext 残留 | MockMvc（同线程连续两请求） | 探针 Controller 读到的 uid 为 null（覆盖 QA-029） |
| 20 | 日志开关 fail-fast | 启动测试 | `SPRING_PROFILES_ACTIVE=prod` + `app.sms.echo-code=true` → 应用**启动失败**（覆盖 QA-017/C3）。**断言点放在启动日志/退出码，不在 HTTP 层**——服务根本不该起来，写 HTTP 断言等于没测 |
| 21 | 评价管理默认拒绝 | MockMvc | role=2 → 全平台；role=1 有店 → 仅本店；role=1 无店 → 403；**role 取非法值 → 403 而非全量**（覆盖 QA-032/F-022；`@RequireRole` 是拦截器，必须走 MockMvc 全栈） |

---

## 发布检查清单（Ship Checklist）

### A. 环境依赖

| 项 | 要求 | 校验命令 | 状态 |
|---|---|---|---|
| JDK | **17**（`pom` 明确 `java.version=17`，Spring Boot 3.2.5） | `java -version` | ⬜ |
| Maven | 3.8+，且本地仓库已含依赖（离线可用） | `mvn -o -q compile` | ✅ 已验证通过 |
| MySQL | 8.0+，库名 **`digital_mall`**，`utf8mb4`，时区 Asia/Shanghai | `mysql -e "show variables like 'character_set_database'"` | ⬜ |
| Redis | 6/7，`localhost:6379`，db0。**本机在 WSL 内**，须先起 | `netstat -ano \| findstr :6379` | ⬜ |
| Node | 18+ / npm 9+ | `node -v` | ⬜ |
| 浏览器 | Chrome/Edge 最新（SSE + `ReadableStream` 依赖） | — | ⬜ |
| 网络 | LLM Key 需外网；无 Key 时可离线（降级模式） | — | ⬜ |

### B. 配置项

| 项 | 文件 | 动作 | 风险 |
|---|---|---|---|
| 激活 profile | `application.yml` | 默认 `local`，可被 `SPRING_PROFILES_ACTIVE` 覆盖 | — |
| LLM Key | `application-local.yml` | 从 `.example` 复制并填 `llm.api-key/base-url/model` | 🟠 **缺失时管理端「知识库向量化」按钮会报 400**（`IllegalStateException`→400），导购自动降级为本地检索（`/chat/send` 仍可用）→ **演示必须有 A/B 两套话术** |
| DB 连接 | `application.yml:13-16` | `root/123456` 为本地默认，换机需改 | 🟡 |
| JWT secret | `application.yml:46` | 上线前更换 | 🟡 |
| 跨域 | `WebConfig` | 前端走 Vite 代理，同源，无需改 | — |

### C. 数据初始化（严格按序，**当前脚本需人工补密文**）

> ⚠️ 本节 5 步 = P0-A 的 A1（QA-002a）+ A2（QA-002b）。**步骤 2 与步骤 3 缺一不可**：只补账号 → 商城是空的；只补商品 → 管理端/商家端进不去。

| 步骤 | 命令/文件 | 校验点 | 状态 |
|---|---|---|---|
| 1 | `mysql -u root -p < 数码电商平台-数据库建表SQL.sql` | 24 张表创建成功；`admin` 账号密码**仍为占位符**（见 QA-002a） | ⬜ 需先替换 BCrypt |
| 2 | 生成并替换 `admin` / `merchant1` 的 BCrypt 密文（建议仓库直接内置 `admin123` 的现成密文） | `SELECT username,role,status FROM sys_user`；用 admin 登录管理端成功 | ⬜ **无现成工具** |
| 3 | `mysql ... < 测试商品数据.sql`（见 QA-002b） | `SELECT COUNT(*) FROM product` > 0 且 `shop` 有 1 条；确认 `uk_brand_name` 未冲突 | ⬜ 脚本不可重复执行 |
| 4 | （有 Key 时）管理端「大模型导购 → 知识库向量化」全量同步 | `redis-cli KEYS 'product:embedding:*'` 数量 = 上架商品数 | ⬜ 无 Key 时跳过 |
| 5 | 手工造演示数据：1 笔已完成订单 + 1 条待审核评价 + 1 条待处理售后 + 1 条待回复咨询 | 三端各页面均有可展示数据（空页面在答辩时很致命） | ⬜ |

### D. 构建与部署产物

| 项 | 命令 | 产物 | 校验 |
|---|---|---|---|
| 后端 | `cd code/backend && mvn clean package -DskipTests` | `target/digital-mall-backend-1.0.0.jar` | `java -jar` 起得来、`/api/product/page` 200 |
| 前端 | `cd code/frontend && npm ci && npm run build` | `dist/`（`index.html` + `assets/*`） | ✅ 本地已验证可构建 |
| 静态托管 | nginx / `vite preview` | — | **必须**：`history` 模式需 `try_files $uri /index.html`；`/api` 反代 8080 |
| SSE 代理 | nginx | — | **必须** `proxy_buffering off; proxy_read_timeout ≥ 120s;`（后端已设 `X-Accel-Buffering: no`，但 nginx 侧仍需关 buffer） |
| 端口 | 8080 / 5173（开发） | — | 防火墙/占用检查 `netstat -ano \| findstr :8080` |

### E. 回滚预案

| 层 | 动作 |
|---|---|
| 代码 | `git tag release-YYYYMMDD` + 保留上一版 `jar` 与 `dist` 目录（带版本号命名，如 `dist-20261109/`），nginx 软链切换 |
| 数据库 | 发布前 `mysqldump -u root -p digital_mall > backup_YYYYMMDD.sql`；**本项目无 Flyway/Liquibase，无迁移版本管理**，任何表结构变更须手工记录 ALTER 脚本 → 无备份不可回滚 |
| 缓存 | 登录态在 Redis：`login:token:*`；回滚后旧 Token 仍有效（TTL 24h），如需强制全员重登：`redis-cli DEL login:token:*` |
| 演示兜底 | 准备"演示专用快照库"（含全套已完成数据），出故障时 `mysql < demo_snapshot.sql` 30 秒重建现场 |

### F. 演示预演脚本（建议按此顺序走一遍，10 步，含所有高风险点）

1. 登录页注册新用户 → 自动登录（验证 register + 单端登录）
2. 首页热销 / 商品列表搜索「手机」→ 筛选价格 3000-5000 → 排序（验证 QA-012 缓存是否含下架商品）
3. 商品详情 → 选 SKU → 加购物车 → 改数量（**不要用 API 试负数，UI 已限制**）
4. 智能导购：问「5000以内的旗舰手机推荐」→ 观察流式输出与商品卡片（验证 SSE 四事件；无 Key 时说明降级）
5. 购物车勾选 → 结算 → 新增地址 → 提交订单（验证按店铺分单）
6. 订单详情 → 模拟支付 → 支付成功
7. 商家工作台 → 待发货 → 填写物流 → 发货 → 用户确认收货
8. 用户评价（双评分）→ **切管理端审核通过** → 回商品详情确认评价与评分已出现（QA-015 关键点）。⚠️ 入口注意：从订单列表点「去评价」当前必崩（QA-025），未修复前演示改走「订单详情 → 评价商品」按钮
9. 申请售后（仅退款）→ 商家拒绝 → 用户申请平台介入 → 管理员判定退款 → 校验 `payment.status=2`、订单回到已完成（售后全链路 + QA-005）
10. 商品详情「咨询商家」→ 商家回复 → 用户查看（QA-018/投诉链路）

---

## G. A 组修复验收清单（配套代码审查侧 patch，逐条可执行）

> 用法：每改完一条，照"验收步骤"走一遍；"通过判据"全部满足才算关闭。**A 组 4 条必须按 A1→A2→A3→A4 顺序验**——A1/A2 不过，后面的用例没有可用的数据环境。

### A1｜QA-002a 种子管理员账号

| 项 | 内容 |
|---|---|
| 修改点 | `数码电商平台-数据库建表SQL.sql:461` 换为已实测密文；建议同时加 `config/AdminPasswordInitializer` 启动自检 |
| 前置 | 空库 或 已执行建表 SQL 但 admin 密码仍是占位符 |
| 验收步骤 | ① `SELECT password FROM sys_user WHERE username='admin'` → 应显示 `$2a$10$...`（60 字符）<br>② 启动后端，浏览器登录 `admin / admin123` → 进入管理后台<br>③ 故意把 DB 里密码改回 `此处替换为BCrypt密文` → 重启（未加自检时）登录 → 应看到"用户名或密码错误"且后端日志有 `警告: Encoded password does not look like BCrypt`（**这一步是为了确认静默失败机制存在，不是要求它存在**）<br>④ 若已加自检：重启后日志应出现"已自动重置为 admin123"，直接登录成功 |
| 通过判据 | ① 密文以 `$2a$` 开头且长度 60；② `admin/admin123` 登录成功并能打开"用户管理"页；③ 管理端**能审核商家入驻**（点开审批弹窗不报错） |
| 失败判据 | 登录返回 400「用户名或密码错误」；或密码字段仍是中文；或管理端页面 403 |
| 参考耗时 | 5 min |

### A1-2｜merchant1 种子商家账号（**同一缺口的第二处，必须同批修**）

> 补录来源：代码审查侧在 patch 附录 B 中额外发现并经 QA 侧独立实测确认——`测试商品数据.sql:16` 的 `merchant1` **也是字面量占位符**（文件注释约定明文 `merchant123`）。这是**第二个登录死锁**，且藏在"用户按 README 已经执行过"的那个脚本里。

| 项 | 内容 |
|---|---|
| 修改点 | `测试商品数据.sql:16` 换为已实测密文；建议与 A1-1 共用同一个 `AdminPasswordInitializer` 自检（把"种子账号密码校验"做成列表） |
| 实测密文 | `$2a$10$2NM4YBNe.CAIpncdYppEF.HQ5wLi3P8qmPMxtYFEKcrHVbCnRqTQG`（QA 侧独立实测：`length=60`、`matches("merchant123")=true`、`matches("merchant124")=false`、占位符 → `false` 且仅告警） |
| 为何不能只验 admin | admin 登录成功**不能证明** merchant1 可用。演示脚本第 7 步（发货）、第 10 步（咨询回复）**全要商家登录**——只验 admin 的最坏结果是答辩走到第 7 步才发现商家端进不去 |
| 验收步骤 | ① `SELECT password FROM sys_user WHERE username='merchant1'` → `$2a$` 开头、60 字符<br>② `merchant1 / merchant123` 登录 → 进入商家工作台 `/merchant`<br>③ 依次打开 **商品 / 订单 / 售后 / 咨询** 四个页签 |
| 通过判据 | ② 登录成功；③ **四个页签全部能打开**，不出现 403「请先完成商家入驻」 |
| 失败判据 | 登录返回 400「用户名或密码错误」→ 密文未换；或**登录成功但页签 403** → 说明 `shop` 绑定/`merchant_id` 关联也断了，是另一条线（查 `shop.merchant_id` 是否等于该用户 id） |
| 参考耗时 | 3 min |

### A2｜QA-002b 种子商品数据

| 项 | 内容 |
|---|---|
| 修改点 | README「快速启动」补第 2 步并加"不能跳过"警示；账号表 |
| 验收步骤 | ① **先不复现跳过**：全新库只执行建表 SQL → 启动 → 打开首页 → 记下现象（应为空列表）<br>② 打开智能导购问「5000以内的旗舰手机推荐」→ 记下回答（应为"抱歉，暂时没有完全匹配的商品…"）<br>③ 执行 `测试商品数据.sql` → `SELECT COUNT(*) FROM product` 应 > 0<br>④ 刷新首页 → 有商品；重问导购 → 有推荐商品卡片 |
| 通过判据 | ④ 首页出现 ≥ 8 件商品；导购返回商品卡片且 `event: products` 非空数组；商家账号可按 README 账号表登录 |
| 失败判据 | 二次执行 `测试商品数据.sql` 报 `Duplicate entry`（说明幂等改造未做，可接受但需在 README 注明"仅执行一次"）；或商品挂到重复分类下 |
| 参考耗时 | 5 min |

### A3｜QA-025 评价主路径崩溃

| 项 | 内容 |
|---|---|
| 修改点 | `OrderDetail.vue:307-311` `onMounted` 改 `async` + `await load()`；`openComment` 首行加 `if (!detail.value?.items?.length) return` |
| 前置 | A1/A2 已完成；存在一笔 `status=3 已完成` 且未评价的订单 |
| 验收步骤 | ① 打开浏览器控制台（F12，勾 Console）<br>② 我的订单 → 点该订单的**「去评价」**按钮（**必须从列表点，不能先进详情页**）→ 观察<br>③ 关掉弹窗 → 再手动进订单详情 → 点「评价商品」按钮 → 观察<br>④ 填双评分 + 文字 → 提交 → 看提示 |
| 通过判据 | ② 评价弹窗**正常出现**，控制台**无** `TypeError: Cannot read properties of null`；③ 弹窗同样正常（两条路径都必须通）；④ 提交后提示出现，无 500 |
| 失败判据 | ② 无弹窗 / 控制台有 TypeError → 未修好；或 ③ 坏而 ② 好 → 改错了路径 |
| 参考耗时 | 5 min |

### A4｜QA-001 数量下界校验

| 项 | 内容 |
|---|---|
| 修改点 | DTO `@NotNull/@Min(1)/@Max(999)` + 嵌套 `@Valid`；Controller `@Valid`；`buildLineFromSku` 与 `createOneOrder` 兜底；`CartServiceImpl.add/updateQuantity` 兜底；**第四层** `payAmount > 0` 断言；存量脏数据修复 SQL |
| 验收步骤 | ① **直接调接口**（Postman/curl，带普通用户 Token）：<br>`POST /api/order` body `{"addressId":<有效>,"fromCart":false,"itemList":[{"skuId":<有效>,"quantity":-5}]}`<br>② 同上 `quantity: 0`<br>③ 同上 `quantity: null`（或整个字段不传）<br>④ 同上 `quantity: 5000`<br>⑤ `PUT /api/cart/{id}/quantity?quantity=-3`<br>⑥ **每次请求前后各查一次** `SELECT stock FROM product_sku WHERE id=<skuId>` 与 `SELECT COUNT(*) FROM order WHERE user_id=<uid>` |
| 通过判据 | ①~⑤ 全部返回 `body.code=400`（**注意：不是看 HTTP 状态码**），message 为参数类提示（"购买数量至少为 1"等，**不应是"库存不足"**）；⑥ 库存与订单数**完全不变** |
| 失败判据 | **① 按现象分四支判因（代码审查侧补充，原判据漏了后两支）**：<br>· `code=200` 且 `pay_amount` 为负 → **四层校验一层都没生效**<br>· `code=400` + 「库存不足」→ 命中了 `ge(stock,null)` 分支，DTO 校验没生效<br>· **`code=500` + 「服务器异常，请稍后重试」→ `quantity` 为 null 后 `quantity <= 0` 自动拆箱 **NPE**，说明 Service 兜底没落地**<br>· **`code=500`（购物车接口）→ 把 `@Min(1)` 加在了 `@RequestParam` 上，Spring 6.1 抛 `HandlerMethodValidationException`，被 `Exception.class` 兜底吞成 500**<br>② 库存发生变化；③ 任一请求 `code=200` |
| 参考耗时 | 20 min |

> **为什么要单列 500 这两支**：`GlobalExceptionHandler` 的有效 handler 只有 `BusinessException / MethodArgumentNotValidException / NoResourceFoundException` + `Exception.class` 兜底。所以"本该 400 却返回 500"是**校验改错了位置**的确定性信号。尤其第四支——"把 `@Min` 加在 Controller 参数上更干净"是**最像优化的错误改法**，一改就把 400 变 500，而 UI 上照样弹"有错误提示"，肉眼分辨不出。
>
> **已知现状（不要求修）**：购物车接口 `?quantity=` **整个不传**会返回 500（`MissingServletRequestParameterException` 落兜底）——这是**改造前就存在**的行为，验收时不要当成本次回归。

**购物车侧 curl（已核 `CartController` 签名：`@RequireRole` 在类上、默认 `{0,1,2}`，普通用户 Token 即可；头必须是 `Authorization: Bearer <token>`，`JwtInterceptor:51-52` 硬校验 `Bearer ` 前缀）**

```bash
T=<UI 登录后从 localStorage 取的 token>

# 下界：应 code=400
curl -s -X PUT  "http://localhost:8080/api/cart/1/quantity?quantity=-3" -H "Authorization: Bearer $T"
curl -s -X PUT  "http://localhost:8080/api/cart/1/quantity?quantity=0"  -H "Authorization: Bearer $T"
curl -s -X POST "http://localhost:8080/api/cart?skuId=1&quantity=0"     -H "Authorization: Bearer $T"

# 正向对照：必须 code=200（防"校验加多了误伤正常路径"）
curl -s -X POST "http://localhost:8080/api/cart?skuId=1&quantity=2"     -H "Authorization: Bearer $T"
curl -s -X PUT  "http://localhost:8080/api/cart/1/quantity?quantity=2"  -H "Authorization: Bearer $T"

# 前后各查一次
# SELECT id, quantity FROM cart WHERE id=1;
# SELECT stock FROM product_sku WHERE id=1;
```

> 上界 `quantity=1000` **不列为必过项**：`@Max(999)` 是本次修复的取舍，不是原始需求；实现若只做下界校验、返回 200 也算合格——**按实际实现判断**，避免把"设计选择"当成"缺陷"。

> **A4 的隐性验收点**：改完后用 UI 正常下单一次（数量 1、2、5）必须仍然成功——校验加多了导致正常下单被拦，是最常见的回归事故。这一条也要走。

### A 组整体回归（改完 4 条后，一次性走一遍）

按 F 节 10 步演示脚本完整走一遍。重点确认两条**容易被 A 组改动波及**的路径：
1. **注册新用户 → 加购物车 → 下单 → 支付**（A4 改了 DTO 与 Service 校验，正常路径不能误伤）
2. **商家入驻申请 → admin 审核通过 → 商家登录**（A1 改了 admin 密码，入驻审核是 A1 的验收关键路径）

---

## 结论

### Ship Readiness

| Metric | Value |
|---|---|
| Health score | **54 / 100** |
| Issues found | **34**（critical 4 / high 11 / medium 12 / low 7） |
| 阻塞视图 | **6 无条件**（A 组 4 + B 组 2）+ **3 条件**（C 组，非 localhost 时升为阻塞） |
| 自动化测试 | 0 用例，有效覆盖 ≈ 0% |
| 构建门禁 | 后端 ✅ / 前端 ✅ |
| 已知妥协（文档已声明） | 模拟支付、验证码打日志、评价需审核、Redis 强依赖登录态、无 Key 降级 |

> 口径说明：上表 severity 是**缺陷严重度**（taxonomy 定义），下节"阻塞项"是**发布阻断视图**——两者不等价。例如 QA-009 / QA-026 的 severity 是 high，但因其可破坏资金/库存数据且修复成本低，仍纳入发布阻断清单（B5 / B6）。

### 统一阻塞清单：分两层（QA 视角 + 代码审查视角，三方对账后定稿）

> 分支分歧的根源不是事实，是**判定刀**不同：QA 侧按「能否完成验收演示」切，代码审查侧按「系统行为是否错误」切。两者都对，混在一起会导致"先修哪个"无法决策。故分两层，事实不变、编号可双向引用。

**P0-A｜路径阻塞**（不修则验收人看不到、走不通；全部必现，各 ≤ 20 min，**合计约 35 min**）

| 序 | QA 编号 | 审查编号 | 内容 | 判定依据 |
|---|---|---|---|---|
| A1 | **QA-002a** | B5 | admin 密码为字面量占位符 → 登录死锁链（管理端不可用 → 商家入驻无法审核 → 商家工作台全线 403） | 进不去 |
| A2 | **QA-002b** | B6 | `测试商品数据.sql` 未纳入启动流程 → 首页空 → 导购一律"抱歉没有匹配商品" | 看不到 |
| A3 | **QA-025** | B1 | 列表→「去评价」必崩，评价模块在主入口不可达 | 走不通 |
| A4 | **QA-001** | B2 | 负/零数量下单 → 库存反增、金额为负（UI 不可达，但接口可达，验收抽查即暴露） | 行为错且必现 |

**P0-B｜正确性阻塞**（不修则系统行为是错的；演示不易触发，但**答辩追问即暴露**；合计约 75 min）

| 序 | QA 编号 | 审查编号 | 内容 | 工时 |
|---|---|---|---|---|
| B1 | **QA-009 + QA-014 + QA-005a**（同源合并修） | B3 | 售后可循环退款（含"部分退款 + 全额退款 → 累计超实付"变体）+ 状态 1 死状态 + 换货误标已退款 | 30 min |
| B2 | **QA-026** | B4 | 订单状态流转"读后写" → 并发双请求致库存双倍回补、`sales` 永久归零 | 45 min |

**C 组｜条件阻塞**（在"非 localhost 访问"场景下升为阻塞；答辩本地演示可暂缓，**C1 最先修**；三方对齐后为 3 项）

| 序 | QA 编号 | 安全编号 | 内容 | 为何是"条件" |
|---|---|---|---|---|
| C1 | **QA-030** | F-003 | 验证码无试错计数，6 位码可在线爆破（5 分钟窗口 × 10⁶ 组合） | 本地演示风险低；一旦可从外网访问即等于账号可被接管 |
| C2 | **QA-031** | F-005 | 口令经 URL query 传输（`/auth/password`、`/auth/forgot`） | 会进 access log / 浏览器历史；本地无日志采集时暴露面小 |
| C3 | **QA-017** | F-006 | 验证码打印到应用日志（无 profile 开关） | 与 C1 构成"读日志即接管"组合拳；**修复=开关化 `app.sms.echo-code` + 非 local 开启时启动 fail-fast** |

> C 组与 A/B 的区别：A/B 是"不修就跑不通/行为就是错的"，无论部署在哪都是阻塞；C 组是"**暴露面取决于部署形态**"——localhost 答辩与公网部署是同两行代码、两种风险。三方统一表述为「**6 无条件 + 3 条件**」。

**建议执行顺序**：A1 → A2 → A3 → A4（35 min，换来"能演示的系统"）→ B1 → B2（75 min，换来"经得起追问的系统"）→ **C1 → C3 → C2（仅当要出校园网/公网）**。**A+B 代码修改约 110 分钟。**

> ⏱ **工时口径（重要）**：上表 110 min **仅为代码修改时间**，**不含回归验证**。本项目零自动化测试，每改一处都必须手工重走一遍黄金路径（登录→商品→加购→下单→支付→发货→收货→评价→售后），B1/B2 改的是交易主干，回归面覆盖整个下单闭环 → **另需 60~120 min 手工回归**。实际投入按 **3~4 小时** 规划。
> 这不是保守估计——**恰恰是"零测试 + 只测 happy path"这个工程习惯催生了本报告这批缺陷**（`?comment=1` 深链漏到今天就是这个原因）。

其余 high（QA-003 / QA-004 / QA-005b / QA-007 / QA-030）不属"阻塞答辩"，但**阻塞上线**，见下节。

**非阻塞但建议同批做的一处横向改造（答辩价值 > 风险价值）**：**QA-029 + QA-032** 是同一个病根——「**缺省即放行**」：`JwtInterceptor` 不标 `@RequireRole` 就等于公开，`managePage` 的 `shopId == null` 就等于全平台。当前都不可利用（`@RequireRole` 把 role 收敛在 {1,2}；公开接口不读 uid），所以不阻塞；但被打包成一条「**授权默认拒绝 + 显式 `@Public` 白名单**」后，它回答的是答辩必问题"你的权限体系怎么设计的"——从"靠每个接口记得加注解"变成"默认拒绝 + 11 条显式公开接口"。改动量约 1 小时（`JwtInterceptor` 改为默认拒绝 + 新增 `@Public` 标 11 条公开接口映射 + `managePage` 3 行判断，若抽 `requireShop()` 再加 6 行），**建议排在 B 组之后、C 组之前**——它和 C 组同属"出网前必须正确"的安全基本面，且此时改不会与 A/B 的改动抢文件。

### 结论：**Go（限答辩 / 内网演示），附 2 条正确性欠账；面向公网用户上线 = No-Go**

> 措辞说明（代码审查建议，采纳）：此前用"Conditional Go"，但该词在评审语境里通常指"还有**未验证**的前置条件"。此处前置条件已封闭——P0-A 4 条改完即可完整演示，P0-B 2 条是**已知、已定位、有明确修复方案**的技术债，不是未知风险。故答辩场景直接判 **Go**，并把 B 层显式记为"欠账"，避免读报告的人误判成熟度。

**必须先关闭（无条件阻塞）**：见上节 P0-A（4 条，约 35 min）+ P0-B（3 项合一，约 75 min）。执行顺序 A1→A2→A3→A4→B1→B2，**代码修改约 110 分钟 + 手工回归 60~120 分钟**。

**条件阻塞（C 组，一旦"非 localhost 访问"即升为阻塞）**：C1 QA-030（验证码可爆破）→ C3 QA-017（验证码入日志开关化）→ C2 QA-031（口令走 URL）。C1 最先修——它与 C3 是双通道账号接管。

**上线前建议关闭（不阻塞答辩，阻塞"公网真实用户"）**：**QA-005b（退款记账 + GMV 不减）** / QA-003（下架商品可结算）/ QA-004（评价粒度）/ QA-007（支付查询越权）。QA-006 / QA-008 可延到下个迭代，但需在"已知问题"里写明（QA-006 的唯一索引部分已在 B1 中顺带解决）。

**可接受发布（记录为已知问题）：** QA-010 ~ QA-024、**QA-032**（授权"缺省即放行"，1 小时可清，建议顺手做）。其中 **QA-015（评价需审核才能看到）必须在演示脚本中显式演示"管理端审核"这一步**，否则现场效果等同功能失效。

**若仅用于课程答辩/内网演示（Go）**：关闭 A 组 + B 组（约 110 min 代码 + 60~120 min 手工回归）后即可交付演示，且必须按"F. 演示预演脚本"完整预演一次；**若面向公网用户上线（No-Go）**：需先关闭 C 组 3 项（C1→C3→C2），再关闭 QA-005b（记账/GMV）、QA-003、QA-004、QA-007 等全部 critical + high，并补齐最小测试集第 1/2/9/12/13/15/16/18 条作为回归门禁。

---

*报告生成方式：qa skill Standard tier 静态深度分析（代码走查 + 业务逻辑推演 + 构建验证）。未启动 MySQL/Redis 进行交互式实测——所有"复现路径"均为代码级推演，标注为 UI 不可达的条目需直接调用 HTTP 接口验证。*
