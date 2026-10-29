package com.digitalmall.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.digitalmall.common.BusinessException;
import com.digitalmall.entity.ChatMessage;
import com.digitalmall.entity.ChatSession;
import com.digitalmall.entity.Product;
import com.digitalmall.mapper.ChatMessageMapper;
import com.digitalmall.mapper.ChatSessionMapper;
import com.digitalmall.mapper.ProductMapper;
import com.digitalmall.security.UserContext;
import com.digitalmall.service.ChatService;
import com.digitalmall.service.IntentService;
import com.digitalmall.service.LlmClient;
import com.digitalmall.service.ProductRetrievalService;
import com.digitalmall.vo.ChatDoneVO;
import com.digitalmall.vo.ChatMessageVO;
import com.digitalmall.vo.ChatSessionVO;
import com.digitalmall.vo.ProductBriefVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 大模型导购服务实现
 * 链路：意图识别 → 商品召回（向量/本地）→ 组装 Prompt → LLM 流式 / 本地模板 → 落库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final ProductMapper productMapper;
    private final IntentService intentService;
    private final ProductRetrievalService retrievalService;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Long uid() {
        return UserContext.getUserId();
    }

    // ==================== 会话管理 ====================

    @Override
    public ChatSessionVO createSession(String title) {
        ChatSession session = new ChatSession();
        session.setUserId(uid());
        session.setSessionTitle(title == null || title.isBlank() ? "新会话" : title);
        sessionMapper.insert(session);
        return toSessionVO(session);
    }

    @Override
    public List<ChatSessionVO> sessionPage() {
        List<ChatSession> list = sessionMapper.selectList(Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getUserId, uid())
                .orderByDesc(ChatSession::getUpdatedTime)
                .orderByDesc(ChatSession::getId));
        return list.stream().map(this::toSessionVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long id) {
        ChatSession session = requireSession(id);
        messageMapper.delete(Wrappers.<ChatMessage>lambdaQuery().eq(ChatMessage::getSessionId, session.getId()));
        sessionMapper.deleteById(session.getId());
    }

    @Override
    public List<ChatMessageVO> messagePage(Long sessionId) {
        ChatSession session = requireSession(sessionId);
        List<ChatMessage> list = messageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getSessionId, session.getId())
                .orderByAsc(ChatMessage::getId));
        return list.stream().map(this::toMessageVO).collect(Collectors.toList());
    }

    @Override
    public List<String> hotQuestions() {
        return List.of(
                "5000以内的旗舰手机推荐",
                "iPhone 和华为 怎么选",
                "适合学生党的笔记本",
                "拍照好的手机 3000左右",
                "降噪耳机推荐 预算1000"
        );
    }

    @Override
    public int knowledgeSync(boolean full) {
        return retrievalService.syncKnowledge(full);
    }

    // ==================== SSE 流式对话 ====================

    @Override
    public void send(Long sessionId, String content, HttpServletResponse response) {
        ChatSession session = requireSession(sessionId);
        if (content == null || content.isBlank()) {
            throw new BusinessException(400, "消息内容不能为空");
        }
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");

        PrintWriter writer;
        try {
            writer = response.getWriter();
        } catch (IOException e) {
            throw new BusinessException(500, "SSE 输出流初始化失败");
        }

        String text = content.trim();
        // 1. 用户消息落库
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(session.getId());
        userMsg.setUserId(uid());
        userMsg.setRole(0);
        userMsg.setContent(text);
        messageMapper.insert(userMsg);

        // 2. 意图识别
        Map<String, Object> intentResult = intentService.recognize(text);
        String intent = String.valueOf(intentResult.get("intent"));
        @SuppressWarnings("unchecked")
        Map<String, Object> slots = (Map<String, Object>) intentResult.get("slots");
        writeEvent(writer, "intent", Map.of("intent", intent, "slots", slots == null ? Map.of() : slots));

        // 3. 商品召回
        List<ProductBriefVO> products;
        try {
            products = retrievalService.retrieve(slots, text, intent);
        } catch (Exception e) {
            log.warn("商品召回失败: {}", e.getMessage());
            products = Collections.emptyList();
        }
        writeEvent(writer, "products", products);

        // 4. 生成回答（LLM 流式 / 本地模板降级）
        StringBuilder fullAnswer = new StringBuilder();
        boolean llmOk = false;
        if (llmClient.available()) {
            try {
                List<Map<String, String>> messages = buildLlmMessages(session.getId(), userMsg.getId(), text, products, slots, intent);
                llmClient.chatStream(messages)
                        .doOnNext(chunk -> {
                            fullAnswer.append(chunk);
                            writeEvent(writer, "answer", Map.of("content", chunk, "delta", true));
                        })
                        .blockLast();
                llmOk = fullAnswer.length() > 0;
            } catch (Exception e) {
                log.warn("LLM 流式生成失败，降级本地回答: {}", e.getMessage());
                fullAnswer.setLength(0);
            }
        }
        if (!llmOk) {
            String local = buildLocalAnswer(intent, slots, products, text);
            for (String piece : splitForStream(local)) {
                fullAnswer.append(piece);
                writeEvent(writer, "answer", Map.of("content", piece, "delta", true));
                try {
                    Thread.sleep(25);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        // 5. 助手消息落库
        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setSessionId(session.getId());
        aiMsg.setUserId(uid());
        aiMsg.setRole(1);
        aiMsg.setContent(fullAnswer.toString());
        aiMsg.setIntent(intent);
        if (!products.isEmpty()) {
            aiMsg.setProductIds(products.stream().map(p -> String.valueOf(p.getId()))
                    .collect(Collectors.joining(",")));
        }
        aiMsg.setTokenCount(estimateTokens(text, fullAnswer.toString()));
        messageMapper.insert(aiMsg);

        // 6. 完成事件
        ChatDoneVO done = new ChatDoneVO();
        done.setSessionId(session.getId());
        done.setMessageId(aiMsg.getId());
        done.setIntent(intent);
        done.setTokenCount(aiMsg.getTokenCount());
        writeEvent(writer, "done", done);
        writer.flush();

        // 7. 更新会话（标题取首条用户消息 + 时间）
        if (session.getSessionTitle() == null || "新会话".equals(session.getSessionTitle())) {
            session.setSessionTitle(text.length() > 20 ? text.substring(0, 20) : text);
        }
        session.setUpdatedTime(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    // ==================== 私有方法 ====================

    /** SSE 事件写出（异常时静默，避免中断主流程） */
    private void writeEvent(PrintWriter writer, String event, Object data) {
        try {
            writer.write("event: " + event + "\n");
            writer.write("data: " + objectMapper.writeValueAsString(data) + "\n\n");
            writer.flush();
        } catch (Exception e) {
            log.debug("SSE 写出失败（客户端可能已断开）: {}", e.getMessage());
        }
    }

    /** 组装真实 LLM 的对话消息（系统提示 + 近期上下文 + 商品知识） */
    private List<Map<String, String>> buildLlmMessages(Long sessionId, Long excludeMsgId, String text,
                                                      List<ProductBriefVO> products,
                                                      Map<String, Object> slots, String intent) {
        String system = "你是数码电商平台的智能导购助手。规则：\n"
                + "1. 只能推荐下方【可推荐商品】中给出的商品，不得编造不存在的商品或参数；\n"
                + "2. 结合用户预算与需求说明推荐理由，参数对比时用简洁表格；\n"
                + "3. 回答用中文、分点、简洁，不要输出与导购无关的内容；\n"
                + "4. 若没有合适商品，如实告知并建议调整预算或需求。";

        StringBuilder knowledge = new StringBuilder("【可推荐商品】\n");
        if (products.isEmpty()) {
            knowledge.append("（无匹配商品）\n");
        } else {
            for (int i = 0; i < products.size(); i++) {
                ProductBriefVO p = products.get(i);
                knowledge.append(i + 1).append(". ").append(p.getProductName())
                        .append("，价格 ").append(p.getPrice()).append(" 元");
                if (p.getSales() != null) {
                    knowledge.append("，销量 ").append(p.getSales());
                }
                knowledge.append('\n');
            }
        }
        knowledge.append("\n用户需求：").append(text);
        if (slots != null && !slots.isEmpty()) {
            knowledge.append("\n（意图：").append(intent);
            if (slots.get("category") != null && !"".equals(slots.get("category")))
                knowledge.append("，品类：").append(slots.get("category"));
            if (slots.get("budget") instanceof Number n && n.doubleValue() > 0)
                knowledge.append("，预算：").append(n.intValue()).append("元");
            if (slots.get("keyword") != null && !"".equals(slots.get("keyword")))
                knowledge.append("，关注点：").append(slots.get("keyword"));
            knowledge.append("）");
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        // 近期上下文（最多 6 条，排除刚落库的当前用户消息）
        List<ChatMessage> recent = messageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getSessionId, sessionId)
                .lt(ChatMessage::getId, excludeMsgId)
                .orderByDesc(ChatMessage::getId)
                .last("limit 6"));
        Collections.reverse(recent);
        for (ChatMessage m : recent) {
            if (m.getRole() != null && m.getContent() != null) {
                messages.add(Map.of("role", m.getRole() == 0 ? "user" : "assistant", "content", m.getContent()));
            }
        }
        messages.add(Map.of("role", "user", "content", knowledge.toString()));
        return messages;
    }

    /** 本地模板回答（无 LLM 时的演示模式） */
    private String buildLocalAnswer(String intent, Map<String, Object> slots,
                                    List<ProductBriefVO> products, String text) {
        if (products.isEmpty()) {
            return "抱歉，暂时没有完全匹配的商品。可以试试放宽预算或换个品类，例如「5000以内的旗舰手机推荐」。";
        }
        Map<Long, Product> detailMap = productMapper.selectBatchIds(
                        products.stream().map(ProductBriefVO::getId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(Product::getId, p -> p));

        StringBuilder sb = new StringBuilder();
        String budgetDesc = slots != null && slots.get("budget") instanceof Number n && n.doubleValue() > 0
                ? "预算 " + n.intValue() + " 元以内" : "";
        String kwDesc = slots != null && slots.get("keyword") != null && !"".equals(slots.get("keyword"))
                ? "关注「" + slots.get("keyword") + "」" : "";

        if ("compare".equals(intent)) {
            sb.append("为你对比以下商品：\n");
            for (int i = 0; i < products.size(); i++) {
                ProductBriefVO p = products.get(i);
                Product d = detailMap.get(p.getId());
                sb.append(i + 1).append(". ").append(p.getProductName())
                        .append(" ｜ 价格 ¥").append(p.getPrice())
                        .append(" ｜ 销量 ").append(p.getSales() == null ? "—" : p.getSales())
                        .append(" ｜ 特点 ").append(d == null || d.getTags() == null ? "—" : d.getTags())
                        .append('\n');
            }
            sb.append("建议结合预算与实际体验需求选择；如需更细致的参数对比，可以告诉我具体机型。");
        } else {
            sb.append("根据你的需求");
            if (!budgetDesc.isEmpty() || !kwDesc.isEmpty()) {
                sb.append("（").append(budgetDesc);
                if (!budgetDesc.isEmpty() && !kwDesc.isEmpty()) sb.append("，");
                sb.append(kwDesc).append("）");
            }
            sb.append("，为你筛选出这些商品：\n");
            for (int i = 0; i < products.size(); i++) {
                ProductBriefVO p = products.get(i);
                Product d = detailMap.get(p.getId());
                sb.append(i + 1).append(". ").append(p.getProductName())
                        .append("（¥").append(p.getPrice()).append("）");
                if (d != null && d.getTags() != null && !d.getTags().isBlank()) {
                    sb.append("——").append(d.getTags());
                } else if (d != null && d.getBrief() != null && !d.getBrief().isBlank()) {
                    sb.append("——").append(d.getBrief().length() > 40 ? d.getBrief().substring(0, 40) + "…" : d.getBrief());
                }
                sb.append('\n');
            }
            sb.append("综合价格与销量，建议优先考虑「").append(products.get(0).getProductName())
                    .append("」；想对比参数或换价位，可以继续问我。");
        }
        return sb.toString();
    }

    /** 将整段文本切成流式片段（按标点/换行） */
    private List<String> splitForStream(String text) {
        List<String> pieces = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (char c : text.toCharArray()) {
            cur.append(c);
            if (c == '\n' || c == '；' || c == '。' || cur.length() >= 60) {
                pieces.add(cur.toString());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) {
            pieces.add(cur.toString());
        }
        return pieces;
    }

    /** Token 估算（中文按字计，粗略 2 字符 ≈ 1 token） */
    private int estimateTokens(String question, String answer) {
        int len = (question == null ? 0 : question.length()) + (answer == null ? 0 : answer.length());
        return Math.max(1, len / 2);
    }

    /** 会话归属校验 */
    private ChatSession requireSession(Long id) {
        ChatSession session = sessionMapper.selectById(id);
        if (session == null) {
            throw new BusinessException(404, "会话不存在");
        }
        if (!session.getUserId().equals(uid())) {
            throw new BusinessException(403, "无权访问该会话");
        }
        return session;
    }

    private ChatSessionVO toSessionVO(ChatSession s) {
        ChatSessionVO vo = new ChatSessionVO();
        vo.setId(s.getId());
        vo.setSessionTitle(s.getSessionTitle());
        vo.setCreatedTime(s.getCreatedTime());
        vo.setUpdatedTime(s.getUpdatedTime());
        return vo;
    }

    private ChatMessageVO toMessageVO(ChatMessage m) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(m.getId());
        vo.setRole(m.getRole());
        vo.setContent(m.getContent());
        vo.setIntent(m.getIntent());
        vo.setCreatedTime(m.getCreatedTime());
        List<Long> ids = new ArrayList<>();
        if (m.getProductIds() != null && !m.getProductIds().isBlank()) {
            for (String s : m.getProductIds().split(",")) {
                try {
                    ids.add(Long.valueOf(s.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        vo.setProductIds(ids);
        if (!ids.isEmpty()) {
            List<Product> products = productMapper.selectBatchIds(ids);
            vo.setProducts(products.stream().map(p -> {
                ProductBriefVO b = new ProductBriefVO();
                b.setId(p.getId());
                b.setProductName(p.getProductName());
                b.setPrice(p.getPrice());
                b.setMainImage(p.getMainImage());
                b.setSales(p.getSales());
                return b;
            }).collect(Collectors.toList()));
        }
        return vo;
    }
}
