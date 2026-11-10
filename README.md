# 基于大模型导购的数码电商平台 · 项目仓库

毕业设计：基于大模型导购的数码电商平台设计与实现（翁宇 · 厦门工学院）

## 目录结构
```
大模型导购的数码电商平台/
├── README.md                          本文件（项目总览 + 从零启动清单）
│
├── docs/                               设计与文档
│   ├── 数码电商平台-数据库ER图.html       数据库 ER 图（浏览器直接打开）
│   ├── 数码电商平台-后端接口文档.html     后端 REST API 文档（81 个接口，可搜索）
│   └── _交易模块接口提取.txt             接口文档的纯文本抽取（便于检索与引用）
│
├── db/                                 MySQL 脚本（按 README 里的顺序执行）
│   ├── README.md                       执行顺序、重置方法、常见报错
│   ├── 数码电商平台-数据库建表SQL.sql     24 业务表 + 2 张 AI 表 + 分类/品牌/账号
│   ├── 测试商品数据.sql                  8 件测试商品（含 SKU/参数/图片）
│   ├── sys_ai_config.sql               AI 配置表 DDL 存档（启动自建，手工建库才需要）
│   └── sys_ai_tool.sql                 AI 工具注册表 DDL + 5 个内置工具（同上）
│
├── deliverables/                       交付物
│   ├── 导购检索评测报告.md               60 条标注集上的三通路对照（含修复前后）
│   └── qa/                             阶段性 QA 归档（测试报告、安全审计、上线检查、修复前快照）
│
├── scripts/                            工具脚本
│   ├── eval-report.py                  读评测 JSON → 生成 deliverables 报告
│   ├── verify-admin-ai.py              管理端 AI 接口冒烟
│   ├── 验证-测试数据SQL.py              校验测试商品数据的完整性
│   └── archive/                        已停用的历史脚本（保留备查）
│
└── code/                               前后端工程代码
    ├── backend/       Spring Boot 3 + MyBatis-Plus + Redis + JWT
    │                   ├ 认证闭环 ✓ 商品模块 ✓ 交易闭环（购物车/订单/支付/地址）✓
    │                   ├ AI 导购：意图识别 → 硬约束召回 → SSE 流式生成
    │                   ├ AI 运营端：配置热更新（sys_ai_config）+ 工具注册表（sys_ai_tool）
    │                   ├ 其余为按接口文档建好的骨架（README 见 code/backend/README.md）
    │                   └ src/main/resources/eval/retrieval-eval.json  60 条带标注的检索评测集
    └── frontend/      Vue3 + Vite + Element Plus + Pinia
                        ├ 登录/注册/首页/商品/详情/导购聊天/购物车/结算/订单 已就绪（README 见 code/frontend/README.md）
                        ├ 三端分离：商城(MainLayout，仅用户) / 商家工作台(MerchantLayout) / 管理后台(AdminLayout)
                        └ 登录后按角色自动跳转各自端首页；越权访问提示无权限并回跳
```

> 根目录只保留 `README.md`、四个资料目录（`docs` / `db` / `deliverables` / `scripts`）与 `code`。
> 新增文档放 `docs/`、SQL 放 `db/`、一次性脚本放 `scripts/archive/`，根目录不再堆文件。

## 技术栈
- 前端：Vue 3 · Vite · Element Plus · Pinia · Axios
- 后端：Spring Boot 3.2 · Java 17+ · MyBatis-Plus · MySQL · Redis · JWT
- 核心特色：大模型智能导购（LLM + 商品知识库向量检索 + SSE 流式对话）

## 从零启动清单

按顺序走完这 7 步即可跑起来。每步都写了「怎么确认成功」，失败时不用猜。

| # | 做什么 | 怎么确认成功 |
|---|---|---|
| 1 | 建库建表：MySQL 执行 `db/数码电商平台-数据库建表SQL.sql` | `USE digital_mall; SHOW TABLES;` 有 26 张表（24 业务 + 2 AI） |
| 2 | **导入商品数据**：执行 `db/测试商品数据.sql` | `SELECT COUNT(*) FROM product;` 返回 8 |
| 3 | 启动 Redis | 日志无 `RedisConnectionFailureException`；登录能拿到 token |
| 4 | 配大模型 Key：`cd code/backend/src/main/resources && cp application-local.yml.example application-local.yml`，填入 `llm.api-key` | 不填也能启动，导购自动降级为本地检索 |
| 5 | 启后端：`cd code/backend && mvn spring-boot:run` | 控制台出现 `Tomcat started on port 8080`；`GET /api/products` 有数据 |
| 6 | 启前端：`cd code/frontend && npm install && npm run dev` | 控制台给出 `http://localhost:5173` |
| 7 | 浏览器打开 http://localhost:5173 | 首页能看到 8 件商品 |

> ⚠️ **第 2 步不能跳过**：建表 SQL 里没有任何商品数据。不执行则首页/商品列表为空，
> 智能导购会一律回答"抱歉，暂时没有完全匹配的商品"——核心功能看起来像坏了。
> ⚠️ 该脚本**只能执行一次**（含唯一键约束，重复执行会报 Duplicate entry）。
> 需要重跑请先 `DROP DATABASE digital_mall;` 重建，见「重置数据库」一节。
> ⚠️ AI 的两张表（`sys_ai_config` / `sys_ai_tool`）**不用手工建**，应用启动时自动建表并播种；
> `db/` 下的同名 SQL 只是 DDL 存档，供论文附录或纯手工建库使用。

### 演示账号

| 角色 | 账号 | 密码 | 说明 |
|---|---|---|---|
| 管理员 | `admin` | `admin123` | 管理后台 `/admin` |
| 商家 | `merchant1` | `merchant123` | 商家工作台 `/merchant` |
| 普通用户 | 自行注册 | — | 商城 `/` |

> **登录提示"用户名或密码错误"时的排查顺序**
> 1. 确认 `SELECT username, password FROM sys_user WHERE username IN ('admin','merchant1');`
>    的 password 是 `$2a$` 开头、长度 60 的密文，而不是中文占位符；
> 2. 若仍是占位符：重新执行建表/测试数据 SQL，或依赖启动自检
>    `SeedPasswordInitializer`（会在启动日志打印"已自动重置为 admin123"）；
> 3. 确认 Redis 已启动（登录态写 Redis，Redis 不可用会返回 503）。

### 重置数据库（演示前准备干净环境）

```sql
DROP DATABASE IF EXISTS `digital_mall`;
```
然后重新按第 1、2 步执行。**不要**直接重复执行 `测试商品数据.sql`。

## 开发路线
数据库（完成）→ 接口文档（完成）→ 脚手架（完成）→
①商品模块（完成）→ ②购物车/订单/支付（完成）→ ③评价（完成）→ ④商家端（完成）→ ⑤管理端（完成）→ ⑥大模型导购（完成）→ ⑦售后模块（完成）→ ⑧咨询/投诉（完成）

## 评价模块说明（③）

- 入口：订单列表「去评价」只在「已完成且未评价」时出现；已提交评价的订单显示「已评价」标记（订单列表接口返回 `commented` 计数）
- 双评分：一次提交商品评分 + 物流评分（1-5 星），可填文字与图片、可匿名
- 审核：提交后状态为「待审核」，管理端「评价审核」点「通过展示」后才在商品详情公开；「违规删除」可下线评价
- 展示：商品详情「商品评价」区显示评价条数、商品均分、物流均分与评价列表（仅 `status=1`）

## 大模型导购说明（⑥）

- 链路：意图识别（find_product / compare / recommend / chat + 槽位）→ 商品召回（Top5）→ 组装 Prompt → 流式生成 → 落库
- 接口：`POST /api/chat/send`（SSE，事件序列 `intent` → `products` → `answer`（delta 增量）→ `done`）；会话与历史：`/api/chat/session`、`/api/chat/session/page`、`/api/chat/message/page`
- 双模式：未提供真实 Key（`application.yml` 中保持占位符、`application-local.yml` 未配置）时自动降级为「规则意图识别 + 本地结构化召回 + 模板分段推送」，无网络也能完整演示；填入真实 Key（豆包方舟或任意 OpenAI 兼容端点）后切换为「Embedding 向量召回 + LLM 流式生成」
- 启用真实大模型：两种方式二选一——① 在 `application-local.yml` 填 `llm.*`（或设同名环境变量）后重启；② 直接在「管理端 → AI 基础配置 → AI 模型配置」填 Key 并保存，**无需重启**（WebClient 按签名重建）。随后到「AI 知识库（RAG） → 商品知识库」点「全量重建向量」执行向量化（Redis 键 `product:embedding:{id}`，TTL 7 天）
- 召回策略：**先硬约束过滤、再语义排序**。对比类问题优先精确匹配用户点名的机型/品牌；找商品类先按「品类 → 预算上限（超 20% 剔除）」过滤候选集，再按「关键词同义词 → 品牌 → 销量微调」打分；向量通路在排序前应用同一套硬约束。过滤后为空即如实告知「没有符合条件的商品」，不凑数

## 管理端 AI 模块（知识库 RAG / 智能导购与运营 / 基础配置）

管理后台左侧菜单按四组组织，前两组回答「AI 现在看到了什么」，第三组回答「AI 用起来怎么样」，第四组才是「AI 怎么被配置」：

| 分组 | 定位 | 页面数 |
|---|---|---|
| 平台运营 | 与 AI 无关的常规后台 | 9 |
| AI 知识库（RAG） | 向量库与切片的可观测性 | 3 |
| AI 智能导购与运营 | 会话、召回、问答、增长分析 | 7 |
| AI 基础配置 | 模型、提示词、工具（改这里会改变 AI 行为） | 5 |

**为什么把「配置」单独抽一组**：前三组是只读观测，改坏了不影响线上；第四组每次保存都会改变导购行为，混在业务菜单里容易被误点。分开之后，「看数据」和「改行为」在视觉上就是两件事。

| 页面 | 路由 | 数据来源 | 能回答的问题 |
|---|---|---|---|
| 商品知识库 | `/admin/ai/knowledge/products` | product / product_attr / Redis | 哪些商品已建向量、知识文本多长、单条重建 |
| 商品知识切片 | `/admin/ai/knowledge/chunks` | 同上（按字段切分） | 模型到底"看得见"哪些字段 |
| Embedding 检索 | `/admin/ai/knowledge/search` | IntentService + ProductRetrievalService | 同一句话会召回什么、走向量还是本地通路 |
| AI 智能导购任务 | `/admin/ai/guide/tasks` | chat_session / chat_message | 谁在问、问了几轮、最近一次意图 |
| Agent Run 运行记录 | `/admin/ai/guide/runs` | chat_message（role=1） | 哪次回答召回少、回答短、token 高 |
| Agent Step 执行步骤 | `/admin/ai/guide/steps` | 同上的运行记录反推 | 一次运行的四步回放（提问→意图→召回→生成） |
| AI 推荐商品 | `/admin/ai/guide/recommend` | chat_message.product_ids 聚合 | 推荐多但销量低的商品 |
| AI 商品问答 | `/admin/ai/qa` | 提问 + 紧随其后的回答 | 高频问题与回答缺陷 |
| AI 评价分析 | `/admin/ai/comment` | comment（规则统计） | 评分分布、差评关键词、未回复数 |
| AI 运营增长报告 | `/admin/ai/report` | chat_session / chat_message / order | 会话数、转化率、每日趋势、意图分布 |
| AI 模型配置 | `/admin/ai/model` | sys_ai_config（`model.*`） | 现在用哪个模型、Key 是否有效、向量维度对不对得上 |
| Prompt 模板 | `/admin/ai/prompt` | sys_ai_config（chat/intent） | 提示词当前版本、默认值、可用变量 |
| Function Tool | `/admin/ai/tool` | sys_ai_tool | 模型能调用哪些工具、参数长什么样、是否启用 |
| 商品工具调试 | `/admin/ai/tool/debug/product` | 调用真实检索服务 | 给定参数会返回什么商品 |
| 业务工具调试 | `/admin/ai/tool/debug/biz` | 调用真实订单/物流 | 订单状态、物流轨迹对不对 |

- 后端入口：`AdminAiController`（观测类，`/api/admin/ai/**`）+ `AdminAiBaseController`（配置类，`/api/admin/ai/model|prompt|tools`），均标注 `@RequireRole(ADMIN)`
- 检索页复用线上链路（`IntentService.recognize` → `ProductRetrievalService.retrieve`），因此「页面上看到的召回结果」与商城端用户看到的一致
- 向量未同步或未配置 Key 时，页面会显式标注「已降级为本地结构化召回」，不隐藏降级行为
- 菜单配置化：`frontend/src/layouts/menuConfig.js` 一处定义，侧栏、选中态、面包屑、展开态（localStorage `admin_menu_opened`）都从这份数组推导，加页面只改数据
- 冒烟脚本：`python scripts/verify-admin-ai.py`（登录后依次调用观测类接口并打印关键字段）

## AI 基础配置（模型 / 提示词 / 工具）

这三项的共同前提是：**它们是运营资产，不是代码常量**。改完保存，下一个请求就生效，不重编译、不重启。

### 1. 模型配置（`model.*`）

| 配置键 | 作用 | 备注 |
|---|---|---|
| `model.base_url` | OpenAI 兼容端点 | 换供应商只改这里 |
| `model.api_key` | 密钥 | **只写不读**：接口返回 `sk-w****b-2Q`，提交回掩码视为未修改 |
| `model.name` | 对话模型名 | |
| `model.embedding_model` | 向量模型名 | 换它会导致存量向量维度失配，页面会告警 |
| `model.temperature` | 采样温度 | 导购建议 0.2–0.5 |

关键实现是**热切换**：`LlmClient` 按 `baseUrl|apiKey` 计算签名缓存 `WebClient`，签名变了就重建，所以保存后下一次对话直接用新模型。第一版把 `WebClient` 在构造时创建一次，改库只改了配置文件、实际请求还走旧端点——这类"看起来保存成功"的坑最难查，所以单独加了「连通性自检」按钮：真发一次对话 + 一次向量化，把耗时、返回维度、是否与存量向量维度一致都打出来。

### 2. Prompt 模板

| 配置键 | 作用 | 可用变量 |
|---|---|---|
| `chat.system_prompt` | 导购回答的系统提示词（怎么说） | `{{productCount}}` 本次召回件数、`{{intentLabel}}` 意图中文名 |
| `intent.system_prompt` | 意图识别的系统提示词（怎么抽槽位） | — |

设计要点：

- **保存即生效**：写库后同步刷新本地缓存，读路径零 DB 往返；配置缺失或读库异常时自动回落到代码内置默认值，导购功能不因配置故障中断
- **可回滚**：每行都存 `default_value`，管理端「恢复默认」一键复原，`version` 每次保存自增，可追溯改了几版
- **占位符只做字符串替换**：不引入模板引擎，避免运营改动带来执行风险；预览接口用示例变量渲染，不调用模型、不产生费用
- **参数注入**：`ChatServiceImpl` 组装上下文时批量查询 `product_attr`（一次 IN 查询后按商品分组），每件商品注入前 N 条参数（`chat.attr_limit`，1–12）。此前上下文只有名称/价格/销量，而提示词要求「参数对比用表格」，模型拿不到参数只能编造——这是「参考对比必翻车」的根因，现已修复
- **密钥隔离**：`prompt` 接口会拦掉 `model.*` 场景，防止有人从模板页改密钥；`model` 接口则不返回明文

### 3. Function Tool（工具注册表）

`sys_ai_tool` 存的是「模型能看到什么」：`tool_code`（同时是 function name）、`description`（直接影响模型选不选它）、`params_schema`（JSON Schema，调试台据此自动生成表单）、`enabled`。真实执行绑定在 `AiToolInvoker` 的 Java 方法上，**管理端改不到执行目标**——否则等于把内部接口做成可配置项。

内置 5 个工具：

| tool_code | 域 | 实际读取 |
|---|---|---|
| `searchProduct` | product | `ProductRetrievalService`（与线上导购同一套召回） |
| `getProductDetail` | product | product + product_attr |
| `getPrice` | product | SKU 价格、库存、销量 |
| `getOrderStatus` | biz | order（状态、金额、各时间节点） |
| `getTrack` | biz | logistics + logistics_track |

**边界要说清楚**：本轮只做到「注册表 + 调试台可跑通」，**没有接进对话主链路**。接进去需要改流式 `tool_calls` 的增量参数拼接与多轮回环（把 `role=tool` 的结果再发一次），会动到导购核心链路，且必须重跑那套 60 条评测确认无回退。页面上已标注此状态，避免误以为已接通。

<details>
<summary>为什么不接（技术取舍，答辩可直接引用）</summary>

- SSE 下的工具回环要维护「一次请求内多轮模型调用」的中间状态，前端还要能渲染「正在调用 xx 工具」的临时态，复杂度陡增；
- 现有链路的意图识别已经把商品类需求结构化，`searchProduct` 的结果可以直接由后端注入上下文，比让模型自己决定调工具**更稳定、更可测**；
- 一旦接入，评测口径要从「槽位 → 召回」改成「工具选择 → 参数 → 结果」，60 条标注集需要重标，时间成本大于收益。

</details>


## 导购检索评测（可复现）

核心创新点需要数据支撑，因此单独提供了一套带标注的离线评测，回答「相比普通关键词搜索，智能导购好在哪里」。

- 评测集：`code/backend/src/main/resources/eval/retrieval-eval.json`，60 条标注样本，每条含标准意图、标准槽位、可接受商品集合、样本类型（找货 / 应弃答 / 开放式 / 域外）
- 执行方式（需要 MySQL 已导入建表 SQL 与测试商品数据）：
  ```bash
  cd code/backend
  mvn -Dtest=RetrievalEvalIT test                      # 本地结构化通路 → target/retrieval-eval-report.json
  mvn -Dtest=RetrievalEvalIT -Deval.vector=true test   # 向量通路 → target/retrieval-eval-report-vector.json
  python scripts/eval-report.py                        # 产出 deliverables/导购检索评测报告.md
  ```
  该测试类命名为 `*IT`，不会被 `mvn test` 自动拉起，也不占用 8080，可与正在运行的后端共存
- 三条对照通路（同数据集、同语料、同一套排序代码，只改变槽位来源）：
  - `B0` 关键词基线：中文二元组 + 英文词重叠计数，等价于 `LIKE '%词%'` 排序
  - `B1` 本文方案（Oracle）：喂入标注槽位，隔离衡量检索本身的质量
  - `B2` 本文方案（端到端）：喂入规则意图识别出的槽位，反映线上真实链路
- 指标：意图准确率（4 类 + 检索/非检索二分类）、品类/预算槽位准确率、Hit@5、Recall@5、Precision@5、MRR、预算越界率、跨品类错配率、弃答准确率、平均耗时
- 管理端接口：`GET /api/admin/chat/eval?vector=false`（仅管理员），返回同一份报告 JSON，便于后续在「管理后台」做可视化
- 设计约束：默认关闭向量通路（`allowVector=false`），保证未配 Key / 未同步向量时也能复现同一份结果；向量通路需显式加 `-Deval.vector=true`，且应在固定模型与已同步向量的环境下执行
- 实测结论（8 件商品语料，60 条标注）：B1 标注槽位下 Precision@5 **61.3%（本地）/ 68.7%（向量）**，预算越界率 **0%**，错品类率 **0%**，弃答准确率 **87.5%**；对照基线 B0 的 Precision 为 43.5%、越界率 43.3%、弃答 12.5%。端到端（B2）仍受槽位识别精度限制（预算槽位 81.5%、品类槽位 74.6%），是当前主要瓶颈
- 评测同时用于回归：修复「命中不足就放开约束」的兜底后，弃答准确率由 0% 提升到 87.5%，失败样本从 8 条降到 1 条

## 售后模块说明（⑦）

- 状态机：`0` 待商家处理 →（商家同意）`4` 已完成；→（商家拒绝）`2` 商家拒绝 →（用户申请介入）`3` 平台介入 →（平台判定）`4` 已完成；用户在 `0` 状态可撤销 → `5` 已撤销
- 申请规则：仅「待收货 / 已完成」订单可申请；同一明细存在进行中售后单时返回 409；申请后订单 `status→5`（售后中），撤销 / 拒绝 / 完成后按 `finishTime` 自动回退为 `2` 或 `3`
- 退款：商家同意或平台判定完成时 `payment.status→2`（已退款），并补齐订单 `finishTime`；换货（type=2）金额固定 0，退款金额不得超过该明细实付
- 平台介入：商家拒绝后买家可在「我的售后」发起 `POST /api/after-sale/{id}/appeal`（仅本人、仅 `status=2` 可申请，否则 400/403）；管理员判定完成时可填写平台意见，后端以「拒绝原因｜平台意见：xxx」追加到 `reject_reason`，详情页同时展示商家拒绝理由与平台意见
- 接口：`POST /api/after-sale`、`GET /api/after-sale/page`、`GET /api/after-sale/{id}`（返回 3 节点处理进度）、`POST /api/after-sale/{id}/cancel`、`POST /api/after-sale/{id}/appeal`（买家申请平台介入）、`PUT /api/after-sale/{id}/handle`（商家）、`PUT /api/after-sale/{id}/admin`（平台 3 介入 / 4 完成）、`GET /merchant/after-sale/page`（商家列表）、`GET /admin/after-sale/page`
- 权限：详情仅本人 / 本店商家 / 管理员可见；商家处理与商家列表按店铺过滤，跨店返回 403
- 前端：订单详情「申请售后」弹窗 +「售后记录」卡片、`/after-sales` 我的售后（进度时间轴、撤销 / 申请介入）、商家端「售后处理」页签（同意 / 拒绝并填写拒绝原因）、管理端「售后管理」页签（介入处理 / 完成退款并填写平台意见）

## 咨询/投诉模块说明（客户管理）

- 定位：对应 ER 图与建表 SQL 中的 `complaint` 表（商家端客户管理），状态机 `0` 待处理 → `1` 已回复
- 提交：商品详情页店铺栏「咨询商家」→ 弹窗选择「咨询 / 投诉」并填写内容（≤1000 字），可选关联本店订单；后端校验店铺存在、类型 ∈ {0,1}、订单归属本人且属于该店铺
- 查看：用户端顶栏下拉「我的咨询」（`/complaints`）按状态筛选，详情弹窗展示商家回复与回复时间
- 回复：商家端「客户咨询」页签列表（类型 / 客户 / 内容与回复 / 状态），点「回复」填写内容后 `status→1` 并记录 `reply_time`；仅本店、仅待处理可回复，跨店 403
- 接口：`POST /api/complaint`、`GET /api/complaint/page`（我的）、`GET /api/complaint/merchant/page`（本店）、`PUT /api/complaint/{id}/reply`
- 列表用户名兜底：`IFNULL(NULLIF(nickname,''), IFNULL(NULLIF(username,''), '用户'))`，兼容昵称为空串的账号

## 认证与会话安全

- 登录态双校验：JWT 负责身份签名，Redis `login:token:{userId}` 保存当前有效 Token（TTL = `jwt.expire-hours`）；拦截器每次请求比对两者，退出登录 / 修改密码 / 重置密码都会删除该 Key，旧 Token 立即失效
- 单端登录：同一账号再次登录会覆盖 Redis 中的 Token，旧设备被顶下线
- 退出登录：用户端、商家端、管理端三个入口都会先调 `POST /api/auth/logout` 再清本地 Token
- 修改密码：`PUT /api/auth/password`，校验原密码 + 新密码长度 6-20 + 禁止与原密码相同，成功后强制重新登录（用户端右上角「修改密码」弹窗）
- 忘记密码：`POST /api/auth/sms-code` 生成 6 位验证码写入 Redis（5 分钟有效，同手机号 + 同场景 60 秒限一次）；`POST /api/auth/forgot` 校验并消费验证码后重置密码（登录页「忘记密码？」入口）
- 验证码在开发环境只打印后端日志（搜索「模拟短信」），生产环境须接入短信网关，不得写入日志或响应体
- Redis 是登录态的一部分：Redis 不可用时登录与鉴权返回 503「登录状态校验失败」，需先启动 Redis
- 本机 Redis 在 WSL 内（Windows 侧无原生 Redis），启动方式：`Start-Process wsl.exe -ArgumentList @('-d','Ubuntu','-u','root','--','bash','-lc','redis-server --daemonize yes; sleep 86400') -WindowStyle Hidden`，约 5 秒后 `netstat -ano | Select-String ':6379' | Select-String LISTENING` 有输出即为就绪

## 配置与密钥管理

仓库会公开托管，密钥不进版本库，按以下约定维护：

| 文件 | 是否提交 | 作用 |
|---|---|---|
| `code/backend/src/main/resources/application.yml` | 提交 | 只保留默认值与占位符，敏感项写成 `${环境变量:默认值}` |
| `code/backend/src/main/resources/application-local.yml` | **不提交**（已被 `.gitignore` 忽略） | 本机真实配置：大模型 `base-url` / `api-key` / 模型名 |
| `code/backend/src/main/resources/application-local.yml.example` | 提交 | 模板，新环境复制后填 Key |

- 敏感项：`llm.base-url`、`llm.api-key`、`llm.model`、`llm.embedding-model` 统一用 `${LLM_BASE_URL:...}`、`${LLM_API_KEY:...}`、`${LLM_MODEL:...}`、`${LLM_EMBEDDING_MODEL:...}` 读取
- 本地默认激活 `local` profile（`spring.profiles.active: ${SPRING_PROFILES_ACTIVE:local}`）；若没有 `application-local.yml`，启动不报错，导购自动降级为本地检索模式
- 取值优先级：环境变量 > `application-local.yml` > `application.yml` 默认值
- 构建产物 `code/backend/target`、`code/frontend/dist` 已从 Git 索引移除并加入忽略——否则 `target/classes/application.yml` 这类配置副本会把密钥带进仓库
- 提交前自查：`git diff --cached | Select-String "sk-"`，有命中说明密钥已进暂存区，先 `git restore --staged <文件>` 再提交
- 换 Key 只改 `application-local.yml`，不要改 `application.yml`；若 Key 曾进入过任何已推送的提交，应立即在模型平台控制台吊销并重新签发
