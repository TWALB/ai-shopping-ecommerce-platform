# Digital Mall 后端脚手架（Spring Boot 3 + MyBatis-Plus + Redis + JWT）

基于大模型导购的数码电商平台 · 后端工程

## 技术栈
- JDK 17+（本机 Java 20 已验证）· Spring Boot 3.2.5 · MyBatis-Plus 3.5.5
- MySQL（digital_mall 库，见《数码电商平台-数据库建表SQL.sql》）· Redis
- JWT（jjwt 0.12）· BCrypt 密码加密

## 目录结构
```
backend/
└── src/main/java/com/digitalmall/
    ├── DigitalMallApplication.java  启动类
    ├── common/     统一返回 Result、分页 PageResult、业务异常、全局异常处理
    ├── config/     Web配置（拦截器/CORS）、MyBatis-Plus分页插件
    ├── security/   JWT工具、登录拦截器、@RequireRole注解、用户上下文
    ├── controller/ 11个模块Controller（认证已实现，其余为待开发骨架）
    ├── dto/        请求对象
    ├── entity/     实体（sys_user 示例）
    ├── mapper/     MyBatis-Plus Mapper
    ├── service/    业务接口与实现
    └── vo/         返回对象
```

## 运行步骤
1. 启动 MySQL：执行建表脚本《数码电商平台-数据库建表SQL.sql》（注意替换管理员密码为 BCrypt 密文）
2. 启动 Redis
3. 修改 `src/main/resources/application.yml` 中的数据库账号密码
4. 启动：`mvn spring-boot:run`（或用 IDEA 打开运行）
5. 验证：`POST http://localhost:8080/api/auth/register` → `POST /api/auth/login` 获取 Token

## 脚手架已实现 vs 待开发
- ✅ 基础设施：统一返回、全局异常、JWT 登录鉴权、角色权限注解、CORS、分页插件
- ✅ 认证闭环：注册 / 登录 / 当前用户（真实可用，联通数据库与 Redis）
- ⏳ 业务模块：Controller 骨架已按《数码电商平台-后端接口文档.html》建好签名与 TODO，按文档逐个填充即可

## 开发顺序建议
认证(已完成) → 商品 → 购物车 → 订单 → 支付 → 评价 → 售后 → 商家 → 管理员 → **大模型导购**（核心，SSE 流式 + 向量检索，最后攻坚）
