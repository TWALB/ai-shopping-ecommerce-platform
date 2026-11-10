# 数码电商平台 · 上线前全检报告（代码审查 + 安全审计 + QA测试）

**日期**：2026-11-09
**场景**：上线前检查（Pre-Launch Check）
**参与成员**：🔍 产品评审员（代码审查）+ 🛡️ 安全官（OWASP+STRIDE 审计）+ ✅ QA与发布（测试分析+发布检查）

---

## 📌 TL;DR（执行摘要）

- 整体结论：🔴 **No-Go（当前状态不可验收/上线）**
- 阻塞项：**6 条无条件阻塞 + 3 条条件阻塞**（三方签字一致的最终口径，编号已冻结，零待裁定项）
  - **A 组｜演示路径阻塞（4 条，约 38 分钟纯代码，含 A1 拆分的 merchant1 密文 3 分钟）**：不修则验收人走不通/看不到，后面的问题无处验证
  - **B 组｜正确性阻塞（2 条，约 75 分钟纯代码）**：不修则系统行为是错的，答辩追问即暴露
  - **C 组｜安全条件阻塞（3 条，约 60 分钟）**：仅当非 localhost 部署时成立；一旦对外访问，**C1 排在 A 组之前**（账号接管优先级最高）
- 问题总数：**34 条**（critical 4 / high 11 / medium 12 / low 7），编号 QA-001~032 已冻结，三份报告口径完全一致、可互相引用（23 项安全发现、9 组 F↔QA 编号映射互通）
- ⚠️ C 组判定逻辑（QA 写明，避免执行者误判优先级）：A/B 是"不修就跑不通/行为就是错的"，**无论部署在哪都是阻塞**；C 组是"暴露面取决于部署形态"——**localhost 答辩与公网部署是同两行代码、两种风险**。且 C3（日志能读到码）+ C1（无需读码可直接爆破）= **双通道账号接管**，非 localhost 时 C1 最先修
- ⚠️ 工时口径（三方共同强调）：`35 + 75 = 110 分钟`是**纯代码修改**时间。本项目零自动化测试，B 组改的是交易主干，必须手工重走完整下单闭环，**另需 60-120 分钟回归，实际按 3-4 小时规划**——"改完不回归"正是这批 bug 的成因本身
- 下一步：清完 A+B（110 min 改码 + 60-120 min 回归，按 3~4 小时规划）→ **Go（限答辩/内网演示），附 2 条正确性欠账**；若对外部署，加清 C 组 3 项且 C1 最先修；**面向公网用户上线 = No-Go**，需关闭全部 critical+high（含 QA-005b/003/004/007）并补最小测试集（21 条用例约 2~3 人日）

---

## 🎯 核心结论卡片

| 项目 | 内容 |
|------|------|
| Go / No-Go | 🔴 **No-Go** → 清 A+B（110 min 改码 + 60-120 min 回归）→ **Go（限答辩/内网演示）**；对外部署加清 C 组（C1 最先修） |
| 严重度分布 | 🔴 6+3（条件）/ 🟠 high 11 / 🟡 medium 12 / 🟢 low 7（合计 34 条） |
| 关键行动项 | 8 条（见行动清单） |
| 总修复成本 | A ~38min + B ~75min + C ~60min（纯代码）+ 60-120min 手工回归，按 **3~4 小时**规划 |
| 构建门禁 | 后端 `mvn -o compile` ✅ / 前端 `npm run build` ✅（有 chunk 体积警告） |
| QA 健康分 | 54/100（测试有效覆盖率 ≈ 0%；最小测试集 21 条 ≈ 2~3 人日） |
| 安全底子 | 好于同类毕设：BCrypt、JWT+Redis 双校验单端登录、SQL 零注入点、订单/售后/地址归属校验齐全 |
| 建议负责人 | 翁宇（全栈）；答辩前至少预留 1.5 天修复窗口 |

---

## 1. 各成员核心结论

### 🔍 产品评审员（代码审查）
- **核心判断**：No-Go，阻塞分两层。A 组（演示路径，必现）：种子账号占位符、种子数据缺失、评价主路径崩溃、下单数量零校验；B 组（正确性）：售后退款闭环漏洞、状态机读后写并发。指出三方分歧的唯一根源是暴露模型不同（本机演示 vs 对外可访问），并完成了合并终稿。
- **关键建议**：「最该先修的不是技术含量最高的并发问题，而是 5 分钟就能修好的种子数据问题——它们决定验收人能不能看到东西。」亮点确认：SQL 注入零风险、越权防护扎实、库存预扣原子更新是交易代码最亮的一笔、LLM 降级链路完整。

### 🛡️ 安全官（OWASP Top 10 + STRIDE 审计）
- **核心判断**：No-Go，阻塞 4 条（数量校验、验证码爆破、口令走 URL、验证码落日志），严重 6 条。STRIDE 显示下单支付与售后退款两条链路**篡改维度均为高风险**，其余维度缓解充分。**最终已背书三方统一口径**，同意 F-006 从阻塞降级并入 C 组（采纳评审员的环境开关方案）。
- **关键建议**：验收清单按序执行；发现评审与 QA 均未覆盖的**存储型 Prompt 注入面**（商家可控商品字段拼入导购 Prompt）；B1 修复必须含**累计退款上限** `SUM(已退) + 本次 ≤ item.totalAmount`；确认 `application-local.yml` 的 LLM Key 从未进 Git 历史 ✅，但仍建议按已泄露处理并轮换（本机为非私人设备/多成员协作环境时必做）。

### ✅ QA与发布（测试分析 + 发布检查）
- **核心判断**：v5.3 终稿 34 条 issue（critical 4 / high 11 / medium 12 / low 7），健康分 54/100。结论措辞最终定为：**答辩/内网演示 = Go（清 A 组 38min + B 组 75min + 回归 60-120min 后）；面向公网用户上线 = No-Go**。弃用 "Conditional Go" 的理由：前置条件已封闭，B 层是**已知、已定位、有明确方案**的技术债而非未验证风险。**测试有效覆盖率约 0%**——交易金额、库存扣减、售后状态机、SSE 序列四类高风险逻辑零回归保护。
- **关键建议**：六节发布检查清单 + 19 条最小测试集（约 2~3 人日，作对外上线回归门禁）；**nginx 部署 SSE 必须关 `proxy_buffering`**；沉淀两条方法论——①回归清单必须显式包含"从列表进入"的每条带 query 深链（`?pay=1`/`?comment=1`，自测漏测根因）②三个会写出假用例的陷阱：断言 HTTP 状态码（项目统一 200+body.code）、`@SpringBootTest` 纯 Service 测越权（`@RequireRole` 是拦截器，service 层不触发）、MockMvc `getContentAsString()` 断言 SSE（直接写 response 分批 flush）。
- **本轮升级**：QA-005 拆 a/b——005b「部分退款不记账」从记账不准升为**运营数据失真**（4 条统计 SQL 全不判 `payment.status`，已全额退款订单永久计入 GMV/今日交易额/趋势/商家营业额；答辩被问"GMV 怎么算、退货算不算"当场暴露）；QA-029 UserContext 残留已钉死唯一触发路径（`JwtInterceptor:90` 角色不匹配 403，且该请求不触发 afterCompletion）并有 100% 确定性复现方案（MockMvc 同线程双请求 + 探针断言）。
- **正面回归基线（14 条入档）**：跨用户订单/售后/地址/购物车/会话访问均正确 403；商家跨店商品/订单/售后/投诉/评价回复均正确拒绝；公开接口白名单无误；新增 `payment.status 1→2` 置位、`restoreOrder` 依 `finishTime` 回退（均在 B1 改动半径内，第 13 条附"隐式状态推导"机理注脚：把状态机历史编码在业务时间戳上，长期解是加 `pre_aftersale_status` 显式字段）、QA-032 验收面（第 14 条）——明确标注"改动后不应退化"，为 B 组修复提供回归网。
- **A 组修复验收清单（QA 报告 G 节）**：每条含修改点/前置/验收步骤/通过判据/失败判据/耗时；含"先故意改回占位符亲见静默失败""先复现空态再修复""A4 必须直连接口断言 `body.code=400`、改完用 UI 正常下单验证未误伤"等防"看着好了其实没生效"设计。BCrypt 密文经 QA 用项目同版本 jar **独立实测复核通过**（非照抄评审结果）。
- **回归重点（v5.1 具体化）**：B1 改 `AfterSaleServiceImpl` → 重点回归"售后详情三方可见性"；B2 改 `cancel/confirm/deliver/merchantHandle` → 重点回归"商家跨店 403"（基线第 2/6/7/8 条）——这是"修好一个坏一个"最可能发生的位置。

---

## 2. 综合审查发现（三方对账合并终稿）

### 🔴 阻塞项清单

#### A 组｜演示路径阻塞（必现，合计约 2 小时）

| # | 条目 | 来源 | 位置 | 修复 |
|---|------|------|------|------|
| A1 | **种子账号**：admin 密码是字面量 `'此处替换为BCrypt密文'`，`BCrypt.matches()` 对非法格式**返回 false 不报错** → 现象只是"密码错误"。死锁链：admin 登不进 → 入驻无法审核 → `requireShop()` 全线 403 → 商家端不可用。**⚠️ patch 阶段新发现：`测试商品数据.sql:15` 的 merchant1 密码同样是中文占位符**（只修 admin 会在演示脚本第 7 步"商家发货"才炸） | 评审 / QA | `建表SQL:461`、`测试商品数据.sql:15` | 写入**实测验证过**的 BCrypt 密文（admin123 / merchant123，QA 用项目同版本 jar 独立复跑通过）；建议新增 `SeedPasswordInitializer` 启动自检从根上消灭静默失败；**不要改用 `BCrypt.checkpw()`**（无正则守卫，非法密文会抛异常变 500）（5 分钟） |
| A2 | **种子数据**：建表 SQL 商品 0 条，8 件商品全在 `测试商品数据.sql`，README 快速启动 5 步**没提** → 商城空 → 导购一律"没有匹配商品" → **核心特色看起来是坏的** | 评审 / QA | `README.md` | 快速启动补数据脚本步骤（5 分钟） |
| A3 | **评价主路径崩溃**：`onMounted` 未 `await load()` 就调 `openComment()` → TypeError。症状是**弹窗不出现、无任何提示**（页面正常渲染），观感等于"功能没做" | 评审 / QA-025 | `OrderDetail.vue:307-311` | 一行 `await` + 空值防御（10 分钟） |
| A4 | **下单数量无校验**：`quantity:-5` → 库存反增 + 订单金额为负；`0` → 零元购；`null` → stock 置 NULL 永久不可售。DTO 无 `@Valid/@Min`，购物车路径同病 | 评审 B2 / QA-001 / 安全 F-001 | `CreateOrderRequest.java`、`OrderServiceImpl:77-105`、`CartServiceImpl:38,67` | **四层防御**：① DTO 注解 `@NotNull @Min(1) @Max(999)`（含 `itemList` 嵌套 `@Valid`，漏了不生效）② Service 兜底 ③ `payAmount > 0` 断言（QA 补充：拦未来优惠券导致金额变负的唯一兜底位）④ 存量脏数据修复 SQL。**⚠️ patch 阶段实测坑**：`@RequestParam @Min` 在 Spring 6.1 抛 `HandlerMethodValidationException`，会被现有 GlobalExceptionHandler 兜底吞成 `code=500` 而非 400，肉眼难辨 → patch 选择 Service 层校验（1 小时） |

#### B 组｜正确性阻塞（答辩追问即暴露，合计约 5-6 小时）

| # | 条目 | 来源 | 说明 |
|---|------|------|------|
| B5 | **售后退款闭环漏洞**（三方各看到一半，拼起来才完整） | 评审 B3（完成后可重复申请）+ 安全 F-002（无累计退款上限）+ QA-009/014（状态 1 死状态导致 4 漏出校验集合） | **合并修法 5 步，缺一不可**：① `running = {0,1,3,4}` ② 每明细仅允许一条终态退款单（DB 唯一约束兜底）③ `SUM(该明细已退金额) + 本次 ≤ item.totalAmount` ④ 换货 `type=2` 跳过 `refundPayment` ⑤ 前端状态字典删掉 `1`。只做 ①② 挡不住多次部分退款累计超额；只改前端删 1 则漏洞原样保留（约 2 小时） |
| B6 | **状态机全"读后写"无并发保护**：`cancel/confirm/deliver/merchantHandle` 无 `WHERE status=?`。并发双取消 → 库存 +2×、`product.sales` 被 `GREATEST(...,0)` 永久归零 | 评审 B4 / QA-026 | 照抄 `PaymentServiceImpl.pay:47-56` 的条件更新写法即可（3-4 小时） |

#### C 组｜安全条件阻塞（约 60 分钟）

| # | 条目 | 来源 | 生效条件 |
|---|------|------|---------|
| C1 | **验证码无试错计数 → 可爆破接管账号**：`verifyCode` 失败不计数不消耗，6 位码 + 5 分钟 TTL + `forgot` 只需手机号即可重置任意账号密码 | 安全 F-003 | **非 localhost 访问时成立**；一旦对外，本条排在 A 组之前（账号接管优先级最高） |
| C2 | **口令走 URL query**：`changePassword`/`forgot` 用 `@RequestParam` 传新旧密码 → 进 access log、反代日志、浏览器地址栏 | 安全 F-005 | 同上；改 `@RequestBody` |
| C3 | **验证码明文落日志**：`AuthServiceImpl:194` `log.info` 打印验证码，无 profile 隔离 | 安全 F-006 | 同上；**最终方案（三方一致）**：改环境开关 `@Value("${app.sms.echo-code:false}")`，demo profile 开、生产默认关且缺失时启动 fail-fast——既保留答辩唯一可用的找回密码路径，又堵住带上线的风险 |

> **判定规则（写死，三方签字）**：只在**本机 localhost 答辩** → A+B 共 6 条为阻塞，C 组整体降 P1，但必须列入「外网部署前必改」清单；在**教室局域网/云服务器**访问 → C 组立即升为阻塞，C1 最先修。

#### ⚖️ F-006 裁定记录（已闭环）

安全官最初将验证码落日志列为阻塞；评审员异议：落日志是当前交付物找回密码的**唯一可用路径**（README 与前端均写明"未接短信网关，验证码看后端日志"），按阻塞处理会让作者在"删日志→功能不可用 / 留日志→违规"间二选一，不可执行。**最终三方一致采纳环境开关方案**，F-006 降级为 C3，该分歧已闭环、无遗留。

### 🟠 严重（P1，去重后主要项）

| # | 编号对照 | 问题 | 建议 |
|---|---------|------|------|
| 1 | S2 / QA-007 / F-004 | `GET /api/payment/query` 无归属校验（IDOR），可遍历 orderId 读他人支付流水 | 加 userId 归属校验 |
| 2 | F-007 / S15 / QA-020 | `application.yml` 明文 MySQL root/123456、JWT secret 默认值、useSSL=false | 全部改 `${ENV}` 占位符，缺失即启动失败 |
| 3 | F-011c / S16 | `application-local.yml` 含**真实可用 LLM Key 明文**（未进 Git ✅，但随交付目录存在） | 交付/打包前删除；本机若非私人设备则按已泄露处理并轮换（安全官升级口径） |
| 4 | F-011b | 商家入驻 `id_card` 身份证号**明文存储 + 回显**，手机号明文落认证日志 | 加密存储 + 展示脱敏 + 日志脱敏 |
| 5 | F-019 | JWT 拦截器 fail-open：Redis 故障时非法 Token 被当作"未登录"放行 → 把鉴权漏洞降级成"功能不可用"假象 | Redis 异常时 fail-closed 返回 503 |
| 6 | **退款不冲减 GMV**（评审员最终轮新发现） | `AdminStatsMapper:25` 与 `MerchantStatsMapper:21` 只按 `pay_time IS NOT NULL` 统计，不看 `payment.status` → **已退款订单永久计入平台总成交额和商家营业额**。答辩问"退货算不算进成交额"直接暴露 | 统计 SQL 加 `AND payment.status != 2`（约 30 分钟） |
| 7 | S1 / QA-010 | 无"未支付超时自动取消"，库存永久占用 | `@Scheduled` 扫单 + 复用 cancel 逻辑 |
| 8 | S3 / QA-012 | 商品详情/热销不校验 status（下架/草稿可访问）；评价摘要全量 `selectList` 在 Java 算均值（**独立性能子问题，勿并入漏修**） | status 过滤 + 聚合 SQL |
| 9 | S4 / QA-008 | 商家编辑商品删光 SKU 重建 → 购物车项静默消失、历史订单/售后 sku_id 悬空 | 改增量 upsert |
| 10 | S5 | `product.stock` 与 `sku.stock` 双轨库存不一致 | 统一以 SKU 为唯一库存源 |
| 11 | S6 | 导购 SSE 同步阻塞 Tomcat 线程（`blockLast()`+`Thread.sleep`），无超时/断开检测/限流 → 线程池耗尽 + LLM 费用失控 | SseEmitter/WebFlux 异步 + Redis 令牌桶限流 |
| 12 | S7 / QA-016 | 每次对话全表拉取商品 + Redis `KEYS` 全扫描（生产阻塞） | 本地缓存 + SCAN/ID 集合 |
| 13 | S8 / QA-013 | 前端 SSE 不判 `resp.ok`/content-type → token 过期时"导购思考中…"无限转圈无任何提示 | 读流前判状态，非 SSE 走 JSON 错误处理 |
| 14 | S9 / F-020 | 异常统一 HTTP 200（仅 body 带 code）→ 监控/网关/前端 error 分支全部失真；**公开环境将鉴权失败与成功混为一谈，属安全问题** | 映射真实 HTTP 状态码（保留 body 结构） |
| 15 | S10 / QA 覆盖结论 | **零自动化测试**（后端无 src/test，前端无 vitest） | 最小测试集：下单扣库存、并发取消、支付幂等、售后重复退款 4 条先行 |
| 16 | S11 / QA-011 | 下单/支付/评价无幂等键，全靠前端 loading 防重 | 客户端 requestId + Redis SETNX |
| 17 | S12 / QA-019 | UserController 6 个空壳接口 vs 接口文档 11 处"已实现"描述（含 409 语义）→ 按文档验收 100% 误判 | 实现或文档降级，二选一 |
| 18 | QA-003 | 购物车结算不过滤下架商品（直接购买路径却有校验） | CartMapper 补 `p.status=1` |
| 19 | QA-005b（独立子问题，勿并入 B5） | **部分退款后整单 `payment.status=2` 已退款**：单笔退款不应翻转整单支付状态 | 最省方案：`payment.status` 语义改为"存在退款记录"并保证所有查询不依赖该字段；彻底方案：拆 `payment_item` 表按明细记退款（约 45 分钟） |
| 20 | F-012 | **存储型 Prompt 注入**：商家可控的 `productName/tags/brief` 拼入导购「可推荐商品」段 → 恶意商家可影响导购输出（评审员确认此为自身漏报） | Prompt 拼装时对商家字段做转义/隔离标记 |

### 🟡 一般（P2，摘要）

- 前端 15 处 `await ElMessageBox.confirm` 无 try/catch（unhandled rejection）
- 401 拦截不清 Pinia store、无 redirect 回跳；无 404 兜底路由；`stores/user.js` JSON.parse 无保护
- `DEMO_AUTO_PUBLISH` 硬编码 `private static final`：想临时开自动展示评价要重新编译 → 改配置项（评审员第三轮新增）
- 一单多件：评价粒度混用（订单粒度 commented vs 明细粒度）；单明细售后使整单"售后中"阻断其他明细确认收货（QA-027）
- 售后/评价唯一性靠 select-then-insert，无 DB 唯一索引（QA-006）
- CORS 通配 + allowCredentials；MyBatis StdOutImpl 全量 SQL 入日志（PII 风险）
- `spring.jackson.date-format` 对 LocalDateTime 无效 → 前端到处 `.replace('T',' ')`
- 地址 update 丢默认标记且无事务；管理端无"最后一个管理员"保护
- 登录/下单接口无限流；找回密码可枚举已绑定手机号
- `via.placeholder.com` 外链图，断网演示全站白图（QA-024）
- **QA-029 `UserContext` ThreadLocal 残留隐患（可自我续命）**：`UserContext.set()` 在 `JwtInterceptor:79`、**位于角色校验（:90）之前**，故每次新的 403 都会先 set 再抛 → 残留被反复刷新、永不自动清理；持续打 403 接口即可让线程池持续变脏，**公开请求命中脏线程的概率随 403 流量上升**（已从"定时炸弹"升级为"混合流量下的越权概率问题"）。当前 service 只读自身 userId 尚不构成越权，定 medium 并含升级规则：一旦出现"公开接口 + 读 UserContext"组合（埋点/个性化推荐/浏览历史）立即升 high。修复：角色不匹配分支 set 前先判断，或 preHandle 抛点前统一 clear。已提供 MockMvc 同线程双请求 + 探针的 100% 确定性复现方案

### 🟢 建议（P3，摘要）

**横向设计建议（QA-032，三方互认，写进论文有价值）**：本项目授权体系存在**两处"默认放行"**——① `JwtInterceptor`：接口没有 `@RequireRole` 即为公开；② `CommentServiceImpl.managePage`（QA 首报、安全侧采纳为 F-022）：`CommentMapper.selectManageComments` 中 `shopId == null` 时不加店铺过滤、返回**全平台评价**。两者同一病根：**默认值是"放行/全量"，安全状态要靠人记得显式声明**。建议统一改为"**默认拒绝 + 显式 `@Public` 白名单**"（QA 逐个数过：公开接口共 11 条 = auth 4 + product 5 + hot-questions 1 + comment/product 1）。答辩被问"权限体系怎么设计的"时，从"靠每个接口记得加注解"升级为"默认拒绝 + 11 条显式公开接口"。建议排在 B 组之后、C 组之前，约 1 小时。注意：F-022 的失败方向是最坏的那种（全量数据），修复优先级应高于普通 🟢；另注意安全侧给的参考改法用了 `requireShop()`，但该方法在 `CommentServiceImpl` 中不存在，照贴会编译失败（QA 已给出可编译的 3 行判断改法）。

其余建议项：

- 订单号用 ThreadLocalRandom 可预测且可撞键；抽统一 `BizNoGenerator`（三处重复实现）
- 状态码魔法数字散落 20+ 处 → 枚举化；多处自建 ObjectMapper → 注入 Spring 单例
- echarts 未拆包（1.15MB chunk）；补 ESLint/Prettier/CI；加 traceId 透传便于答辩定位
- admin/admin123 文档口令部署即改；补安全响应头；商家售后处理写 operation_log

---

## ✅ 行动清单（按修复顺序）

| # | 行动 | 负责方 | 紧急度 | 期望完成 |
|---|------|--------|--------|---------|
| 1 | A1：建表 SQL 写入 admin123 的现成 BCrypt 密文 | 翁宇 | P0 | 第 1 小时（5 分钟） |
| 2 | A2：README 快速启动插入「执行 测试商品数据.sql」步骤 | 翁宇 | P0 | 第 1 小时（5 分钟） |
| 3 | A3：`OrderDetail.vue` onMounted 加 `await load()` + openComment 空值防御 | 翁宇 | P0 | 第 1 小时（10 分钟） |
| 4 | A4：CreateOrderRequest 加 `@NotNull @Min(1) @Max(999)` + Controller `@Valid` + CartService 两处下限校验 | 翁宇 | P0 | 第 1 天上午（1 小时） |
| 5 | B5：售后退款闭环 5 步合并修（running+4 / 终态唯一 / 累计上限 / 换货不退款 / 前端删状态 1） | 翁宇 | P0 | 第 1 天下午（2 小时） |
| 6 | B6：cancel/confirm/deliver/merchantHandle 改条件更新（抄 pay 写法） | 翁宇 | P0 | 第 1 天下午（3-4 小时） |
| 7 | C 组 + 同批安全项：C1 验证码锁定、C2 口令改 RequestBody、C3 验证码日志环境开关、S2 支付查询归属校验、删除 application-local.yml 并视环境轮换 LLM Key、P1-6 退款冲减 GMV | 翁宇 | P0（对外部署）/ P1（本机答辩） | 第 2 天上午 |
| 8 | 加分项：S9 异常 HTTP 语义、S8 SSE 错误处理、S1 超时关单、S10 最小测试集 4 条 | 翁宇 | P1 | 答辩前机动（约 1 天） |

---

## ⚠️ 回滚预案与发布要点（摘自 QA 发布检查清单）

- **回滚**：git tag 标记发布点 + `mysqldump` 全量备份；**项目无 Flyway/Liquibase，无备份即不可回滚**；Redis `login:token:*` 兼容旧 Token，需强制重登则 DEL 对应键
- **环境**：JDK17（pom 硬要求）、MySQL8（库名 `digital_mall` utf8mb4）、Redis 6379（**Windows 侧必须经 WSL 起，否则登录直接 503**）、Node18+
- **nginx 部署**：必须 `try_files $uri /index.html`；对 SSE 路径关 `proxy_buffering`、`proxy_read_timeout ≥ 120s`
- **配置**：`application-local.yml` 从 example 复制；无 LLM Key 时导购自动降级可用，但管理端「知识库向量化」会报 400 → 准备有 Key/无 Key 两套演示话术
- **演示数据**：手工准备完成订单/待审评价/待处理售后/待回复咨询各 1 条（空页面在答辩时最致命）；「管理端审核通过评价」为必演步骤
- **测试口径**：业务错误统一 HTTP 200 + body.code，自动化测试必须断言 `body.code`，断言 HTTP 状态码会 100% 假通过

---

## ⚠️ 待完善 / 已知局限

- 本次为静态深度分析 + 构建验证，未启动 MySQL/Redis 做交互式实测；所有复现路径为代码级推演（三方已交叉复核、QA v4 对账无事实分歧、编号冻结）
- QA 报告标注「UI 不可达」的条目需直接调 HTTP 接口二次验证
- 完整 P2/P3 清单与逐条定位见两位成员的原始报告文件
- 若需 A/B 组的可落地 patch（含现成 BCrypt 密文、README 改写、售后 5 步修法、状态机条件更新），可随时再启动团队产出
- **三方发现归属**（答辩被问"这些问题怎么没发现"时可用）：种子数据 B5/B6 由 QA 首报；GMV 不冲减由评审员最终轮新发现；存储型 Prompt 注入由安全官首报；三方各看到退款问题的一半，拼起来才完整——这正是多角色评审的价值

### 三方签字一致项（本次评审的共识存档）

1. **判定规则**：只本机 localhost 答辩 → A+B 共 6 条为阻塞，C 组整体降 P1；一旦对外访问 → C 组升为阻塞，C1 最先修
2. **B1 修复必须含累计退款上限**（安全官提出，三方确认）：`SUM(该明细已退金额) + 本次 ≤ item.totalAmount`
3. **工时口径**：`35 + 75 = 110 min` 是纯代码修改，必须另加 60-120 min 手工回归（QA 提出，评审员共鸣）

---

## 📚 成员产出索引

- **gstack-product-reviewer（产品评审员）**：完整代码审查报告 + 两轮结论更新 + 三方合并终稿（会话内产出）；**`deliverables/gstack/A组修复patch-2026-11-09.md`**（601 行：A1~A4 可直接照做的完整 patch，含两个实测 BCrypt 密文、SeedPasswordInitializer 代码、README 改写、六层数量校验、存量脏数据 SQL、附录 B 九行「改前复现/改后验证」对照表、附录 C 回归风险）
- **gstack-security-officer（安全官）**：`deliverables/gstack/安全审计报告.md`（F-001~F-023 共 23 项发现，OWASP Top 10 逐项结论 + STRIDE 双链路威胁表 + 三方编号映射附录，9 组 F↔QA 编号互通）
- **gstack-qa-lead（QA与发布）**：`deliverables/gstack/QA-测试报告与发布检查.md`（v5.3 终稿 977 行：34 条 issue + A/B/C 三层阻塞视图 + 发布检查清单 + 10 步演示脚本 + G 节 A 组修复验收清单（含 merchant1 补行与 `code=500` 两支失败判据）+ 21 条最小测试集 + 14 条正面回归基线）

---

## 🛠️ 实施记录（2026-11-09 18:20 补充）

**A 组 patch 已实际应用到仓库**（8 文件改动 + 1 新增类，108 insertions / 20 deletions），后端 `mvn -o compile` ✅、前端 `npm run build` ✅：

- A1：两个 SQL 文件写入实测密文（admin123 / merchant123）；新增 `config/SeedPasswordInitializer.java` 启动自检
- A2：README 快速启动整段改写（含商品数据步骤、演示账号表、排查顺序、重置数据库）
- A3：`OrderDetail.vue` `await load()` + 空值防御
- A4：`CreateOrderRequest`（含 itemList 嵌套 @Valid）+ `OrderController` @Valid + `OrderServiceImpl` 三层兜底 + `CartServiceImpl` 两处校验
- 刻意未做：存量脏数据 SQL 未在任何数据库执行（演示前需人工按 patch 附录 B 操作）；B 组/C 组/P1 文件均未触碰
- **下一步**：① 重建数据库（DROP DATABASE → 建表 SQL → 测试商品数据 SQL）② 按 QA 报告 G 节逐条手工验收 ③ 有余力再修 B 组两条正确性阻塞

---

> 本报告由软件工坊 AI 协作生成（gstack-product-reviewer / gstack-security-officer / gstack-qa-lead 三方独立审计、交叉核对、对账合并），关键决策请由工程负责人复核。
