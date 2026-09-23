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
│
├── 验证-建表SQL.py / 验证-ER图HTML.py / 验证-接口文档.py   产物校验脚本
│
└── code/                              前后端工程代码
    ├── backend/       Spring Boot 3 + MyBatis-Plus + Redis + JWT
    │                   ├ 认证闭环已实现，其余模块为按接口文档建好的骨架（README 见 code/backend/README.md）
    └── frontend/      Vue3 + Vite + Element Plus + Pinia
                        ├ 登录/注册/首页/商品列表/导购聊天页已就绪（README 见 code/frontend/README.md）
```

## 技术栈
- 前端：Vue 3 · Vite · Element Plus · Pinia · Axios
- 后端：Spring Boot 3.2 · Java 17+ · MyBatis-Plus · MySQL · Redis · JWT
- 核心特色：大模型智能导购（LLM + 商品知识库向量检索 + SSE 流式对话）

## 快速启动
1. MySQL 执行 `数码电商平台-数据库建表SQL.sql`（把管理员密码替换为 BCrypt 密文）
2. 启动 Redis
3. 后端：`cd code/backend && mvn spring-boot:run`（8080）
4. 前端：`cd code/frontend && npm install && npm run dev`（5173）
5. 浏览器打开 http://localhost:5173

## 开发路线
数据库（完成）→ 接口文档（完成）→ 脚手架（完成）→
①商品模块 → ②购物车/订单/支付 → ③评价/售后 → ④商家端 → ⑤管理端 → ⑥大模型导购（核心，最后攻坚）
