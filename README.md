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
│
└── code/                              前后端工程代码
    ├── backend/       Spring Boot 3 + MyBatis-Plus + Redis + JWT
    │                   ├ 认证闭环 ✓ 商品模块 ✓ 交易闭环（购物车/订单/支付/地址）✓
    │                   ├ 其余为按接口文档建好的骨架（README 见 code/backend/README.md）
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
1. MySQL 执行 `数码电商平台-数据库建表SQL.sql`（把管理员密码替换为 BCrypt 密文）
2. 启动 Redis
3. 后端配置：`cd code/backend/src/main/resources && Copy-Item application-local.yml.example application-local.yml`，在生成的文件里填入自己的大模型 Key（不填也能启动，导购自动走本地检索降级模式）
4. 后端启动：`cd code/backend && mvn spring-boot:run`（8080）
5. 前端：`cd code/frontend && npm install && npm run dev`（5173）
6. 浏览器打开 http://localhost:5173

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
- 启用真实大模型：在 `application-local.yml` 填入 `llm.base-url`、`llm.api-key`、`llm.model`、`llm.embedding-model`（或设置同名环境变量）后重启后端，管理员在「管理端 → 商品管理 → 重建导购知识库」执行向量化（Redis 键 `product:embedding:{id}`，TTL 7 天）
- 召回策略：对比类问题优先精确匹配用户点名的机型/品牌；找商品类按「品类 → 预算上限 → 关键词同义词 → 品牌 → 销量微调」排序，超出预算 20% 以上直接剔除

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
