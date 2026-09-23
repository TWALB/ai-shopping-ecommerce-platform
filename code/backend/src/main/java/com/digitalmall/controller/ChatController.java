package com.digitalmall.controller;

import com.digitalmall.common.Result;
import com.digitalmall.security.RequireRole;
import org.springframework.web.bind.annotation.*;

/**
 * 大模型智能导购模块（接口文档：九、大模型导购）— 系统核心特色
 * 开发重点：/chat/send 采用 SSE（text/event-stream）流式返回
 *   流程：意图识别 → 商品向量检索(Redis) → 组装Prompt → LLM流式生成
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    /** 发送消息，SSE 流式返回（核心接口）
     *  返回 text/event-stream，事件：intent → products → answer → done
     *  注意：SSE 接口建议直接用 HttpServletResponse 写流，不走统一 Result 包装 */
    @PostMapping(value = "/send", produces = "text/event-stream;charset=UTF-8")
    @RequireRole
    public void send(@RequestParam Long sessionId, @RequestParam String content,
                     jakarta.servlet.http.HttpServletResponse response) {
        // TODO 开发阶段实现流式输出：
        // 1) 调 LLM 意图识别（找商品/参数对比/选购建议/闲聊），抽取槽位
        // 2) 生成 query 向量，Redis 余弦相似度召回 TopN 商品
        // 3) 组装 Prompt（注入召回商品结构化信息 + 系统提示词）
        // 4) 调 LLM 流式生成，逐段写 SSE 事件
        // 5) 落库 chat_message（intent / product_ids / token_count）
    }

    /** 创建会话 */
    @PostMapping("/session")
    @RequireRole
    public Result<Void> createSession(@RequestParam(required = false) String title) {
        // TODO
        return Result.success();
    }

    /** 会话列表 */
    @GetMapping("/session/page")
    @RequireRole
    public Result<Void> sessionPage() {
        // TODO
        return Result.success();
    }

    /** 删除会话 */
    @DeleteMapping("/session/{id}")
    @RequireRole
    public Result<Void> deleteSession(@PathVariable Long id) {
        // TODO
        return Result.success();
    }

    /** 会话历史消息 */
    @GetMapping("/message/page")
    @RequireRole
    public Result<Void> messagePage(@RequestParam Long sessionId) {
        // TODO
        return Result.success();
    }

    /** 商品知识库向量化（管理员：初始化/全量重建） */
    @PostMapping("/knowledge/sync")
    @RequireRole(RequireRole.ADMIN)
    public Result<Void> knowledgeSync(@RequestParam(defaultValue = "false") Boolean full) {
        // TODO 遍历上架商品 → Embedding → 写 Redis product:embedding:{id}
        return Result.success();
    }

    /** 导购快捷提问 */
    @GetMapping("/hot-questions")
    public Result<Void> hotQuestions() {
        // TODO 返回预置问题数组
        return Result.success();
    }
}
