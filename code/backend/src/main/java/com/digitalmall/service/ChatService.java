package com.digitalmall.service;

import com.digitalmall.vo.ChatMessageVO;
import com.digitalmall.vo.ChatSessionVO;

import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

/**
 * 大模型智能导购服务
 */
public interface ChatService {

    /** 创建会话 */
    ChatSessionVO createSession(String title);

    /** 会话列表（时间倒序） */
    List<ChatSessionVO> sessionPage();

    /** 删除会话及其消息 */
    void deleteSession(Long id);

    /** 会话历史消息 */
    List<ChatMessageVO> messagePage(Long sessionId);

    /** 导购快捷提问 */
    List<String> hotQuestions();

    /** 商品知识库向量化（full=true 全量重建），返回成功向量化数量 */
    int knowledgeSync(boolean full);

    /** 发送消息，SSE 流式返回：intent → products → answer（增量）→ done */
    void send(Long sessionId, String content, HttpServletResponse response);
}
