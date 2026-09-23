# Digital Mall 前端脚手架（Vue3 + Vite + Element Plus + Pinia）

基于大模型导购的数码电商平台 · 前端工程

## 技术栈
- Vue 3.4 · Vite 5 · Vue Router 4 · Pinia 2 · Element Plus 2.7 · Axios

## 目录结构
```
frontend/
├── vite.config.js      开发代理：/api → http://localhost:8080
├── index.html
└── src/
    ├── main.js         入口（Element Plus / Pinia / Router）
    ├── App.vue
    ├── api/            axios 封装 + 各模块接口
    │   ├── request.js  JWT 注入、Result 解包、401 跳转
    │   ├── auth.js     认证接口
    │   ├── product.js  商品接口
    │   └── chat.js     导购接口（含 SSE 流式读取封装）
    ├── layouts/        MainLayout 主布局（导航/用户菜单）
    ├── router/         路由 + 登录守卫
    ├── stores/         Pinia 用户状态
    └── views/          登录/首页/商品列表/智能导购/管理后台
```

## 运行步骤
1. `npm install`
2. `npm run dev`（默认 http://localhost:5173）
3. 确保后端已在 8080 启动，页面登录即可联通

## 脚手架已实现
- ✅ axios 封装：自动带 Token、统一解包 Result、401 自动跳登录
- ✅ 登录/注册页（真实联通后端 /api/auth/*）
- ✅ 主布局 + 路由守卫
- ✅ 首页热门商品、商品列表检索（前端完整，等后端接口填充即可出数据）
- ✅ 智能导购聊天页：会话列表 + SSE 流式解析封装（`api/chat.js` 的 `sendChatStream`），后端 /chat/send 实现后即可用

## 开发顺序建议
商品详情 → 购物车 → 结算下单 → 我的订单 → 商家端 → 管理端 → 导购页完善
