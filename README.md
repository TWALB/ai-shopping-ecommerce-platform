# 基于大模型导购的数码电商平台 · 项目仓库

毕业设计：基于大模型导购的数码电商平台设计与实现（翁宇 · 厦门工学院）

## 目录结构
```
大模型导购的数码电商平台/
├── README.md                          本文件（项目总览）
│
├── 开题报告-翁宇.docx                  开题报告原件
├── 开题报告内容.txt                    开题报告纯文本（检索用）
│
├── 数码电商平台-数据库建表SQL.sql       MySQL 建表脚本（24张表+初始数据）
├── 数码电商平台-数据库ER图.html         数据库 ER 图（浏览器打开）
├── 数码电商平台-后端接口文档.html       后端 REST API 文档（81个接口，可搜索）
├── 测试商品数据.sql                     8件测试商品（含SKU/参数/图片）
│
├── 验证-建表SQL.py / 验证-ER图HTML.py / 验证-接口文档.py / 验证-测试数据SQL.py   产物校验脚本
├── scripts/eval-report.py             读取评测结果 JSON，生成 deliverables/导购检索评测报告.md
│
└── code/                              前后端工程代码
    ├── backend/       Spring Boot 3 + MyBatis-Plus + Redis + JWT
    │                   ├ 认证闭环 ✓ 商品模块 ✓ 交易闭环（购物车/订单/支付/地址）✓
    │                   ├ 其余为按接口文档建好的骨架（README 见 code/backend/README.md）
    │                   └ src/main/resources/eval/retrieval-eval.json  60 条带标注的检索评测集
    └── frontend/      Vue3 + Vite + Element Plus + Pinia
                        ├ 登录/注册/首页/商品/详情/导购聊天/购物车/结算/订单 已就绪（README 见 code/frontend/README.md）
                        ├ 三端分离：商城(MainLayout，仅用户) / 商家工作台(MerchantLayout) / 管理后台(AdminLayout)
                        ├ 登录后按角色自动跳转各自端首页；越权访问提示无权限并回跳
```

## 技术栈
- 前端：Vue 3 · Vite · Element Plus · Pinia · Axios
- 后端：Spring Boot 3.2 · Java 17+ · MyBatis-Plus · MySQL · Redis · JWT
- 核心特色：大模型智能导购（LLM + 商品知识库向量检索 + SSE 流式对话）

## 快速启动
1. MySQL 执行 `数码电商平台-数据库建表SQL.sql`
   （建表 24 张 + 分类/品牌/管理员账号；admin 密码已内置可用的 BCrypt 密文）
2. **MySQL 执行 `测试商品数据.sql`**
   （8 件商品 + 1 个店铺 + 商家账号 merchant1）

   > ⚠️ **这一步不能跳过**：建表 SQL 里没有任何商品数据。不执行则首页/商品列表为空，
   > 智能导购会一律回答"抱歉，暂时没有完全匹配的商品"——核心功能看起来像坏了。
   > ⚠️ 该脚本**只能执行一次**（含唯一键约束，重复执行会报 Duplicate entry）。
   > 需要重跑请先 `DROP DATABASE digital_mall;` 重建，见「重置数据库」一节。

3. 启动 Redis
4. 后端配置：
   `cd code/backend/src/main/resources && Copy-Item application-local.yml.example application-local.yml`
   在生成的文件里填入自己的大模型 Key（**不填也能启动**，导购自动走本地检索降级模式）
5. 后端启动：`cd code/backend && mvn spring-boot:run`（端口 8080）
6. 前端：`cd code/frontend && npm install && npm run dev`（端口 5173）
7. 浏览器打开 http://localhost:5173

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
- 启用真实大模型：在 `application-local.yml` 填入 `llm.base-url`、`llm.api-key`、`llm.model`、`llm.embedding-model`（或设置同名环境变量）后重启后端，管理员在「管理端 → AI 知识库（RAG） → 商品知识库」点「全量重建向量」执行向量化（Redis 键 `product:embedding:{id}`，TTL 7 天）
- 召回策略：**先硬约束过滤、再语义排序**。对比类问题优先精确匹配用户点名的机型/品牌；找商品类先按「品类 → 预算上限（超 20% 剔除）」过滤候选集，再按「关键词同义词 → 品牌 → 销量微调」打分；向量通路在排序前应用同一套硬约束。过滤后为空即如实告知「没有符合条件的商品」，不凑数

## 管理端 AI 模块（知识库 RAG / 智能导购与运营）

管理后台左侧菜单按三组组织：平台运营、AI 知识库（RAG）、AI 智能导购与运营。AI 两组共 10 个页面，全部读取现有表与 Redis 向量索引，**不新增数据库表**。

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

- 后端入口：`AdminAiController`（`/api/admin/ai/**`，`@RequireRole(ADMIN)`）+ `AdminAiService`
- 检索页复用线上链路（`IntentService.recognize` → `ProductRetrievalService.retrieve`），因此「页面上看到的召回结果」与商城端用户看到的一致
- 向量未同步或未配置 Key 时，页面会显式标注「已降级为本地结构化召回」，不隐藏降级行为
- 冒烟脚本：`python scripts/verify-admin-ai.py`（登录后依次调用 10 个接口并打印关键字段）

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
