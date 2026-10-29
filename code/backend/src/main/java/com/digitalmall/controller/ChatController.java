package com.digitalmall.controller;

import com.digitalmall.common.BusinessException;
import com.digitalmall.common.Result;
import com.digitalmall.security.RequireRole;
import com.digitalmall.service.ChatService;
import com.digitalmall.vo.ChatMessageVO;
import com.digitalmall.vo.ChatSessionVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 大模型智能导购模块（接口文档：九、大模型导购）— 系统核心特色
 * /chat/send 采用 SSE（text/event-stream）流式返回：intent → products → answer → done
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** 发送消息，SSE 流式返回（核心接口） */
    @PostMapping(value = "/send", produces = "text/event-stream;charset=UTF-8")
    @RequireRole
    public void send(@RequestParam Long sessionId, @RequestParam String content,
                     HttpServletResponse response) {
        chatService.send(sessionId, content, response);
    }

    /** 创建会话 */
    @PostMapping("/session")
    @RequireRole
    public Result<ChatSessionVO> createSession(@RequestParam(required = false) String title) {
        return Result.success(chatService.createSession(title));
    }

    /** 会话列表 */
    @GetMapping("/session/page")
    @RequireRole
    public Result<List<ChatSessionVO>> sessionPage() {
        return Result.success(chatService.sessionPage());
    }

    /** 删除会话 */
    @DeleteMapping("/session/{id}")
    @RequireRole
    public Result<Void> deleteSession(@PathVariable Long id) {
        chatService.deleteSession(id);
        return Result.success();
    }

    /** 会话历史消息 */
    @GetMapping("/message/page")
    @RequireRole
    public Result<List<ChatMessageVO>> messagePage(@RequestParam Long sessionId) {
        return Result.success(chatService.messagePage(sessionId));
    }

    /** 商品知识库向量化（管理员：初始化/全量重建） */
    @PostMapping("/knowledge/sync")
    @RequireRole(RequireRole.ADMIN)
    public Result<Integer> knowledgeSync(@RequestParam(defaultValue = "false") Boolean full) {
        try {
            return Result.success(chatService.knowledgeSync(Boolean.TRUE.equals(full)));
        } catch (IllegalStateException e) {
            throw new BusinessException(400, e.getMessage());
        }
    }

    /** 导购快捷提问 */
    @GetMapping("/hot-questions")
    public Result<List<String>> hotQuestions() {
        return Result.success(chatService.hotQuestions());
    }
}
