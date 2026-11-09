package com.digitalmall.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.digitalmall.common.PageResult;
import com.digitalmall.entity.*;
import com.digitalmall.mapper.*;
import com.digitalmall.vo.AdminAiVO;
import com.digitalmall.vo.ProductBriefVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端「AI 知识库 / AI 智能导购与运营」服务。
 *
 * 设计原则：只读聚合现有数据，不新增表、不改线上链路。
 * - 知识库与切片：直接读 product / product_attr，向量状态查 Redis（product:embedding:{id}）
 * - 检索：复用 IntentService + ProductRetrievalService，与线上对话走同一条链路
 * - 任务 / 运行 / 问答 / 推荐：读 chat_session / chat_message
 * - 评价分析 / 运营报告：读 comment / order / chat_message
 *
 * 数据量为毕设规模（数十商品、数百消息），聚合在内存完成；若上生产应改为 SQL GROUP BY。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAiService {

    private static final Map<String, String> INTENT_LABELS = Map.of(
            "find_product", "找商品",
            "compare", "参数对比",
            "recommend", "选购建议",
            "chat", "闲聊/域外"
    );

    /** 评价关键词提取时的停用词（避免把"这个/非常"当特征） */
    private static final Set<String> STOP_WORDS = Set.of(
            "这个", "那个", "非常", "但是", "就是", "还是", "可以", "感觉", "觉得", "真的",
            "东西", "商品", "宝贝", "收到", "已经", "一下", "有点", "比较", "什么", "没有");

    private final ProductMapper productMapper;
    private final ProductAttrMapper productAttrMapper;
    private final CategoryMapper categoryMapper;
    private final BrandMapper brandMapper;
    private final SysUserMapper sysUserMapper;
    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final CommentMapper commentMapper;
    private final OrderMapper orderMapper;
    private final IntentService intentService;
    private final ProductRetrievalService retrievalService;

    // ==================== 一、商品知识库 ====================

    /** 商品知识库列表：切片数、向量状态、知识文本长度（支持按同步状态过滤） */
    public PageResult<AdminAiVO.KnowledgeProduct> knowledgeProducts(String keyword, Boolean vectorized,
                                                                   long pageNum, long pageSize) {
        List<Product> all = productMapper.selectList(Wrappers.<Product>lambdaQuery()
                .like(hasText(keyword), Product::getProductName, keyword)
                .orderByDesc(Product::getId));
        Map<Long, String> cats = categoryNames();
        Map<Long, String> brands = brandNames();
        Map<Long, List<ProductAttr>> attrs = attrsOf(all.stream().map(Product::getId).collect(Collectors.toList()));
        Set<Long> vectorizedIds = retrievalService.vectorizedIds();

        List<AdminAiVO.KnowledgeProduct> rows = new ArrayList<>();
        for (Product p : all) {
            boolean synced = vectorizedIds.contains(p.getId());
            if (vectorized != null && vectorized != synced) {
                continue;
            }
            String text = embedText(p, attrs.getOrDefault(p.getId(), List.of()));
            AdminAiVO.KnowledgeProduct vo = new AdminAiVO.KnowledgeProduct();
            vo.setId(p.getId());
            vo.setProductName(p.getProductName());
            vo.setCategoryName(cats.getOrDefault(p.getCategoryId(), ""));
            vo.setBrandName(brands.getOrDefault(p.getBrandId(), ""));
            vo.setPrice(p.getPrice());
            vo.setSales(p.getSales());
            vo.setStatus(p.getStatus());
            vo.setChunkCount(buildChunks(p, attrs.getOrDefault(p.getId(), List.of())).size());
            vo.setVectorized(synced);
            vo.setVectorDim(synced ? retrievalService.vectorDim(p.getId()) : null);
            vo.setTextLength(text.length());
            vo.setPreview(text.length() > 110 ? text.substring(0, 110) + "…" : text);
            rows.add(vo);
        }
        return slice(rows, pageNum, pageSize);
    }

    /** 单个 / 全量重建向量：productId 为空则全量 */
    public Map<String, Object> sync(Long productId, boolean full) {
        Map<String, Object> r = new LinkedHashMap<>();
        int count = productId == null ? retrievalService.syncKnowledge(full) : retrievalService.syncOne(productId);
        r.put("synced", count);
        r.put("total", retrievalService.vectorizedIds().size());
        r.put("vectorReady", retrievalService.vectorReady());
        return r;
    }

    // ==================== 二、商品知识切片 ====================

    /** 指定商品的切片明细；productId 为空时返回全部商品（截断到 200 条） */
    public List<AdminAiVO.Chunk> chunks(Long productId) {
        List<Product> products;
        if (productId == null) {
            products = productMapper.selectList(Wrappers.<Product>lambdaQuery().orderByDesc(Product::getSales));
        } else {
            Product one = productMapper.selectById(productId);
            products = one == null ? List.of() : List.of(one);
        }
        if (products.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ProductAttr>> attrs = attrsOf(products.stream().map(Product::getId).collect(Collectors.toList()));
        List<AdminAiVO.Chunk> list = new ArrayList<>();
        for (Product p : products) {
            list.addAll(buildChunks(p, attrs.getOrDefault(p.getId(), List.of())));
            if (list.size() >= 200) {
                break;
            }
        }
        return list.size() > 200 ? list.subList(0, 200) : list;
    }

    /** 切片概览：每个商品的切片数与文本长度 */
    public List<Map<String, Object>> chunkSummary() {
        List<Product> all = productMapper.selectList(Wrappers.<Product>lambdaQuery().orderByDesc(Product::getSales));
        Map<Long, List<ProductAttr>> attrs = attrsOf(all.stream().map(Product::getId).collect(Collectors.toList()));
        Set<Long> vecIds = retrievalService.vectorizedIds();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Product p : all) {
            List<AdminAiVO.Chunk> chunks = buildChunks(p, attrs.getOrDefault(p.getId(), List.of()));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", p.getId());
            m.put("productName", p.getProductName());
            m.put("chunkCount", chunks.size());
            m.put("textLength", chunks.stream().mapToInt(AdminAiVO.Chunk::getLength).sum());
            m.put("vectorized", vecIds.contains(p.getId()));
            m.put("types", chunks.stream().map(AdminAiVO.Chunk::getType).distinct().collect(Collectors.toList()));
            list.add(m);
        }
        return list;
    }

    // ==================== 三、Embedding 检索 ====================

    /** 用线上同一条链路检索：意图识别 → 槽位 → 向量/本地召回，返回模式与耗时 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> search(String query, int topK, boolean preferVector) {
        long start = System.nanoTime();
        Map<String, Object> intentResult = intentService.recognize(query);
        String intent = String.valueOf(intentResult.get("intent"));
        Map<String, Object> slots = (Map<String, Object>) intentResult.get("slots");
        boolean vectorReady = retrievalService.vectorReady();
        boolean useVector = preferVector && vectorReady;

        List<ProductBriefVO> products = retrievalService.retrieve(slots, query, intent, useVector);
        long ms = Math.max(1, (System.nanoTime() - start) / 1_000_000);

        List<AdminAiVO.SearchItem> items = new ArrayList<>();
        for (ProductBriefVO p : products) {
            AdminAiVO.SearchItem item = new AdminAiVO.SearchItem();
            item.setProductId(p.getId());
            item.setProductName(p.getProductName());
            item.setPrice(p.getPrice());
            item.setSales(p.getSales());
            item.setSimilarity(p.getSimilarity());
            items.add(item);
        }
        if (items.size() > topK) {
            items = items.subList(0, topK);
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("intent", intent);
        r.put("intentLabel", INTENT_LABELS.getOrDefault(intent, intent));
        r.put("slots", slots == null ? Map.of() : slots);
        r.put("mode", useVector ? "embedding" : "local");
        r.put("modeLabel", useVector ? "Embedding 向量召回" : "本地结构化召回");
        r.put("vectorReady", vectorReady);
        r.put("latencyMs", ms);
        r.put("items", items);
        r.put("note", useVector ? "与线上对话同一链路，TopN 固定为 5"
                : vectorReady ? "已关闭向量通路，本次走本地结构化匹配"
                : "未配置 api-key 或向量库为空，已自动降级为本地结构化匹配");
        return r;
    }

    // ==================== 四、AI 智能导购任务（会话） ====================

    public PageResult<AdminAiVO.SessionRow> sessionPage(String keyword, long pageNum, long pageSize) {
        Page<ChatSession> page = new Page<>(pageNum, pageSize);
        IPage<ChatSession> ip = sessionMapper.selectPage(page, Wrappers.<ChatSession>lambdaQuery()
                .like(hasText(keyword), ChatSession::getSessionTitle, keyword)
                .orderByDesc(ChatSession::getUpdatedTime)
                .orderByDesc(ChatSession::getId));
        List<ChatSession> sessions = ip.getRecords();
        List<Long> ids = sessions.stream().map(ChatSession::getId).collect(Collectors.toList());
        Map<Long, List<ChatMessage>> bySession = ids.isEmpty() ? Map.of()
                : messageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                        .in(ChatMessage::getSessionId, ids)
                        .orderByAsc(ChatMessage::getId))
                .stream().collect(Collectors.groupingBy(ChatMessage::getSessionId));
        Map<Long, SysUser> users = userMap(sessions.stream().map(ChatSession::getUserId).collect(Collectors.toList()));

        List<AdminAiVO.SessionRow> rows = new ArrayList<>();
        for (ChatSession s : sessions) {
            List<ChatMessage> msgs = bySession.getOrDefault(s.getId(), List.of());
            AdminAiVO.SessionRow vo = new AdminAiVO.SessionRow();
            vo.setId(s.getId());
            vo.setUserId(s.getUserId());
            SysUser u = users.get(s.getUserId());
            vo.setUsername(u == null ? "-" : u.getUsername());
            vo.setNickname(u == null ? "-" : u.getNickname());
            vo.setSessionTitle(s.getSessionTitle());
            vo.setMessageCount(msgs.size());
            vo.setQuestionCount((int) msgs.stream().filter(m -> m.getRole() != null && m.getRole() == 0).count());
            vo.setLastIntent(msgs.stream()
                    .filter(m -> m.getRole() != null && m.getRole() == 1 && m.getIntent() != null)
                    .reduce((a, b) -> b)
                    .map(ChatMessage::getIntent).orElse(null));
            vo.setCreatedTime(s.getCreatedTime());
            vo.setUpdatedTime(s.getUpdatedTime());
            rows.add(vo);
        }
        return PageResult.of(ip.getTotal(), pageNum, pageSize, rows);
    }

    /** 会话明细（含导购推荐的商品 ID） */
    public List<AdminAiVO.MessageRow> sessionMessages(Long sessionId) {
        return messageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getId))
                .stream().map(m -> {
                    AdminAiVO.MessageRow vo = new AdminAiVO.MessageRow();
                    vo.setId(m.getId());
                    vo.setRole(m.getRole());
                    vo.setContent(m.getContent());
                    vo.setIntent(m.getIntent());
                    vo.setProductIds(m.getProductIds());
                    vo.setTokenCount(m.getTokenCount());
                    vo.setCreatedTime(m.getCreatedTime());
                    return vo;
                }).collect(Collectors.toList());
    }

    // ==================== 五、Agent Run 运行记录 ====================

    /** 以「一条助手回答」为一次运行，回溯其对应的用户提问 */
    public PageResult<AdminAiVO.RunRow> runPage(String intent, long pageNum, long pageSize) {
        Page<ChatMessage> page = new Page<>(pageNum, pageSize);
        IPage<ChatMessage> ip = messageMapper.selectPage(page, Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getRole, 1)
                .eq(hasText(intent), ChatMessage::getIntent, intent)
                .orderByDesc(ChatMessage::getId));

        List<ChatMessage> runs = ip.getRecords();
        List<Long> sessionIds = runs.stream().map(ChatMessage::getSessionId).distinct().collect(Collectors.toList());
        Map<Long, List<ChatMessage>> usersBySession = sessionIds.isEmpty() ? Map.of()
                : messageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                        .in(ChatMessage::getSessionId, sessionIds)
                        .eq(ChatMessage::getRole, 0)
                        .orderByAsc(ChatMessage::getId))
                .stream().collect(Collectors.groupingBy(ChatMessage::getSessionId));
        Map<Long, SysUser> users = userMap(runs.stream().map(ChatMessage::getUserId).collect(Collectors.toList()));

        List<AdminAiVO.RunRow> rows = new ArrayList<>();
        for (ChatMessage run : runs) {
            AdminAiVO.RunRow vo = new AdminAiVO.RunRow();
            vo.setId(run.getId());
            vo.setSessionId(run.getSessionId());
            vo.setUserId(run.getUserId());
            SysUser u = users.get(run.getUserId());
            vo.setUsername(u == null ? "-" : u.getUsername());
            vo.setQuestion(previousQuestion(usersBySession.getOrDefault(run.getSessionId(), List.of()), run.getId()));
            vo.setIntent(run.getIntent());
            vo.setProductCount(splitIds(run.getProductIds()).size());
            vo.setAnswerLength(run.getContent() == null ? 0 : run.getContent().length());
            vo.setTokenCount(run.getTokenCount());
            vo.setCreatedTime(run.getCreatedTime());
            rows.add(vo);
        }
        return PageResult.of(ip.getTotal(), pageNum, pageSize, rows);
    }

    /** 运行步骤：由运行记录反推四步（提问 → 意图 → 召回 → 生成），steps 内每步含状态与摘要 */
    public Map<String, Object> runSteps(Long runId) {
        ChatMessage run = messageMapper.selectById(runId);
        if (run == null) {
            throw new IllegalStateException("运行记录不存在：" + runId);
        }
        List<ChatMessage> sameSession = messageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getSessionId, run.getSessionId())
                .le(ChatMessage::getId, runId)
                .orderByAsc(ChatMessage::getId));
        String question = previousQuestion(sameSession, runId);
        List<Long> productIds = splitIds(run.getProductIds());
        Map<Long, Product> productMap = productIds.isEmpty() ? Map.of()
                : productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(step(1, "用户提问", question == null ? "-" : question,
                question == null ? "未找到对应提问" : "已落库 chat_message(role=0)"));
        steps.add(step(2, "意图识别", INTENT_LABELS.getOrDefault(run.getIntent(), String.valueOf(run.getIntent())),
                run.getIntent() == null ? "未记录意图" : "intent=" + run.getIntent()));
        String recalled = productIds.isEmpty() ? "无召回商品"
                : productIds.stream().map(id -> {
                    Product p = productMap.get(id);
                    return p == null ? String.valueOf(id) : p.getProductName();
                }).collect(Collectors.joining("、"));
        steps.add(step(3, "商品召回", productIds.size() + " 件商品", recalled));
        steps.add(step(4, "回答生成", (run.getContent() == null ? 0 : run.getContent().length()) + " 字",
                run.getTokenCount() == null ? "token 未记录" : "token≈" + run.getTokenCount()));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("runId", runId);
        r.put("sessionId", run.getSessionId());
        r.put("intent", run.getIntent());
        r.put("createdTime", run.getCreatedTime());
        r.put("steps", steps);
        return r;
    }

    // ==================== 六、AI 推荐商品 ====================

    /** 被导购推荐次数聚合（近 N 天） */
    public List<AdminAiVO.RecommendRow> recommendTop(int days, int limit) {
        LocalDateTime since = LocalDateTime.now().minusDays(Math.max(days, 1));
        List<ChatMessage> runs = messageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getRole, 1)
                .isNotNull(ChatMessage::getProductIds)
                .ne(ChatMessage::getProductIds, "")
                .ge(ChatMessage::getCreatedTime, since));
        Map<Long, Integer> counter = new HashMap<>();
        Map<Long, LocalDateTime> lastTime = new HashMap<>();
        for (ChatMessage m : runs) {
            for (Long id : splitIds(m.getProductIds())) {
                counter.merge(id, 1, Integer::sum);
                lastTime.merge(id, m.getCreatedTime() == null ? LocalDateTime.now() : m.getCreatedTime(),
                        (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        if (counter.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> products = productMapper.selectBatchIds(counter.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        return counter.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(Math.max(limit, 1))
                .map(e -> {
                    AdminAiVO.RecommendRow vo = new AdminAiVO.RecommendRow();
                    vo.setProductId(e.getKey());
                    Product p = products.get(e.getKey());
                    vo.setProductName(p == null ? "（商品已删除）" : p.getProductName());
                    vo.setPrice(p == null ? BigDecimal.ZERO : p.getPrice());
                    vo.setSales(p == null ? 0 : p.getSales());
                    vo.setRecommendCount(e.getValue());
                    vo.setLastTime(lastTime.get(e.getKey()));
                    return vo;
                }).collect(Collectors.toList());
    }

    // ==================== 七、AI 商品问答 ====================

    /** 问答对（助手回答 + 其前一条用户提问），支持关键词过滤 */
    public PageResult<AdminAiVO.QaRow> qaPage(String keyword, long pageNum, long pageSize) {
        List<ChatMessage> answers = messageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getRole, 1)
                .orderByDesc(ChatMessage::getId)
                .last("limit 500"));
        List<Long> sessionIds = answers.stream().map(ChatMessage::getSessionId).distinct().collect(Collectors.toList());
        Map<Long, List<ChatMessage>> usersBySession = sessionIds.isEmpty() ? Map.of()
                : messageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                        .in(ChatMessage::getSessionId, sessionIds)
                        .eq(ChatMessage::getRole, 0)
                        .orderByAsc(ChatMessage::getId))
                .stream().collect(Collectors.groupingBy(ChatMessage::getSessionId));
        Map<Long, SysUser> users = userMap(answers.stream().map(ChatMessage::getUserId).collect(Collectors.toList()));

        List<AdminAiVO.QaRow> rows = new ArrayList<>();
        for (ChatMessage a : answers) {
            String q = previousQuestion(usersBySession.getOrDefault(a.getSessionId(), List.of()), a.getId());
            if (q == null) {
                continue;
            }
            if (hasText(keyword)
                    && !q.contains(keyword)
                    && (a.getContent() == null || !a.getContent().contains(keyword))) {
                continue;
            }
            AdminAiVO.QaRow vo = new AdminAiVO.QaRow();
            vo.setAnswerId(a.getId());
            vo.setSessionId(a.getSessionId());
            SysUser u = users.get(a.getUserId());
            vo.setUsername(u == null ? "-" : u.getUsername());
            vo.setQuestion(q);
            vo.setAnswer(a.getContent());
            vo.setIntent(a.getIntent());
            vo.setProductCount(splitIds(a.getProductIds()).size());
            vo.setCreatedTime(a.getCreatedTime());
            rows.add(vo);
        }
        return slice(rows, pageNum, pageSize);
    }

    // ==================== 八、AI 评价分析 ====================

    /** 评价分析：评分分布 + 差评关键词 + 最新差评（规则统计，不依赖大模型，离线可演示） */
    public AdminAiVO.CommentAnalysis commentAnalysis(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(Math.max(days, 1));
        List<Comment> comments = commentMapper.selectList(Wrappers.<Comment>lambdaQuery()
                .ne(Comment::getStatus, 2)
                .ge(Comment::getCreatedTime, since)
                .orderByDesc(Comment::getId));

        AdminAiVO.CommentAnalysis vo = new AdminAiVO.CommentAnalysis();
        vo.setTotal(comments.size());
        if (comments.isEmpty()) {
            vo.setAvgScore(0.0);
            vo.setGoodRate(0.0);
            vo.setNegativeCount(0);
            vo.setUnrepliedCount(0);
            vo.setDistribution(new ArrayList<>());
            vo.setKeywords(new ArrayList<>());
            vo.setNegatives(new ArrayList<>());
            return vo;
        }

        double avg = comments.stream()
                .filter(c -> c.getProductScore() != null)
                .mapToInt(Comment::getProductScore).average().orElse(0);
        long good = comments.stream().filter(c -> c.getProductScore() != null && c.getProductScore() >= 4).count();
        List<Comment> negatives = comments.stream()
                .filter(c -> c.getProductScore() != null && c.getProductScore() <= 2)
                .collect(Collectors.toList());

        vo.setAvgScore(round(avg));
        vo.setGoodRate(round(good * 100.0 / comments.size()));
        vo.setNegativeCount(negatives.size());
        vo.setUnrepliedCount((int) comments.stream().filter(c -> c.getReplyContent() == null || c.getReplyContent().isBlank()).count());

        List<Map<String, Object>> dist = new ArrayList<>();
        for (int score = 1; score <= 5; score++) {
            final int s = score;
            long count = comments.stream().filter(c -> c.getProductScore() != null && c.getProductScore() == s).count();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("score", score);
            m.put("count", count);
            m.put("rate", round(count * 100.0 / comments.size()) / 100.0);
            dist.add(m);
        }
        vo.setDistribution(dist);

        Map<String, Integer> wordCount = new HashMap<>();
        for (Comment c : negatives) {
            for (String w : tokenize(c.getContent())) {
                wordCount.merge(w, 1, Integer::sum);
            }
        }
        vo.setKeywords(wordCount.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(12)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("word", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                }).collect(Collectors.toList()));

        Map<Long, Product> products = negatives.isEmpty() ? Map.of()
                : productMapper.selectBatchIds(negatives.stream().map(Comment::getProductId)
                        .filter(Objects::nonNull).distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(Product::getId, p -> p));
        vo.setNegatives(negatives.stream().limit(10).map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            Product p = c.getProductId() == null ? null : products.get(c.getProductId());
            m.put("id", c.getId());
            m.put("productName", p == null ? "-" : p.getProductName());
            m.put("score", c.getProductScore());
            m.put("content", c.getContent());
            m.put("replied", c.getReplyContent() != null && !c.getReplyContent().isBlank());
            m.put("createdTime", c.getCreatedTime());
            return m;
        }).collect(Collectors.toList()));
        return vo;
    }

    // ==================== 九、AI 运营增长报告 ====================

    /** 运营概览：会话/提问/推荐/转化 + 每日提问趋势 + 意图分布 */
    public AdminAiVO.Report report(int days) {
        int span = Math.min(Math.max(days, 1), 90);
        LocalDateTime since = LocalDate.now().minusDays(span - 1L).atStartOfDay();

        List<ChatSession> sessions = sessionMapper.selectList(Wrappers.<ChatSession>lambdaQuery()
                .ge(ChatSession::getCreatedTime, since));
        List<ChatMessage> messages = messageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .ge(ChatMessage::getCreatedTime, since));
        List<ChatMessage> questions = messages.stream()
                .filter(m -> m.getRole() != null && m.getRole() == 0)
                .collect(Collectors.toList());
        List<ChatMessage> runs = messages.stream()
                .filter(m -> m.getRole() != null && m.getRole() == 1)
                .collect(Collectors.toList());

        Set<Long> chatUserIds = questions.stream().map(ChatMessage::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> orderUserIds = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                        .select(Order::getUserId))
                .stream().map(Order::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        long converted = chatUserIds.stream().filter(orderUserIds::contains).count();

        Set<Long> recommended = new HashSet<>();
        for (ChatMessage m : runs) {
            recommended.addAll(splitIds(m.getProductIds()));
        }

        AdminAiVO.Report vo = new AdminAiVO.Report();
        vo.setSessionCount(sessions.size());
        vo.setQuestionCount(questions.size());
        vo.setRunCount(runs.size());
        vo.setAvgTurns(round(sessions.isEmpty() ? 0 : questions.size() * 1.0 / sessions.size()));
        vo.setChatUsers(chatUserIds.size());
        vo.setOrderUsers(orderUserIds.size());
        vo.setConvertedUsers((int) converted);
        vo.setConversionRate(round(chatUserIds.isEmpty() ? 0 : converted * 100.0 / chatUserIds.size()));
        vo.setRecommendedProducts(recommended.size());
        vo.setKnowledgeProducts(productMapper.selectCount(null).intValue());
        vo.setVectorizedProducts(retrievalService.vectorizedIds().size());

        List<Map<String, Object>> trend = new ArrayList<>();
        Map<LocalDate, Long> byDay = questions.stream()
                .filter(q -> q.getCreatedTime() != null)
                .collect(Collectors.groupingBy(q -> q.getCreatedTime().toLocalDate(), Collectors.counting()));
        Map<LocalDate, Long> runsByDay = runs.stream()
                .filter(q -> q.getCreatedTime() != null)
                .collect(Collectors.groupingBy(q -> q.getCreatedTime().toLocalDate(), Collectors.counting()));
        for (int i = span - 1; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", d.toString());
            m.put("questions", byDay.getOrDefault(d, 0L));
            m.put("answers", runsByDay.getOrDefault(d, 0L));
            trend.add(m);
        }
        vo.setTrend(trend);

        Map<String, Long> intentCount = runs.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getIntent() == null ? "unknown" : m.getIntent(), Collectors.counting()));
        vo.setIntents(intentCount.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("intent", e.getKey());
                    m.put("label", INTENT_LABELS.getOrDefault(e.getKey(), e.getKey()));
                    m.put("count", e.getValue());
                    return m;
                }).collect(Collectors.toList()));
        return vo;
    }

    // ==================== 私有工具 ====================

    private Map<String, Object> step(int index, String name, String summary, String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("index", index);
        m.put("name", name);
        m.put("summary", summary);
        m.put("detail", detail);
        m.put("status", "success");
        return m;
    }

    /** 与向量化写入完全一致的文本拼装（字段口径同 ProductRetrievalService.buildProductText） */
    private String embedText(Product p, List<ProductAttr> attrs) {
        StringBuilder sb = new StringBuilder();
        sb.append(p.getProductName() == null ? "" : p.getProductName()).append(' ');
        if (p.getProductTitle() != null) sb.append(p.getProductTitle()).append(' ');
        if (p.getTags() != null) sb.append(p.getTags()).append(' ');
        if (p.getKeywords() != null) sb.append(p.getKeywords()).append(' ');
        if (p.getBrief() != null) sb.append(p.getBrief()).append(' ');
        sb.append("价格").append(p.getPrice()).append("元 ");
        for (ProductAttr a : attrs) {
            sb.append(a.getAttrName()).append(':').append(a.getAttrValue()).append(' ');
        }
        return sb.toString().trim();
    }

    /** 按字段切片：一个字段一个切片，便于前端解释「知识库里到底存了什么」 */
    private List<AdminAiVO.Chunk> buildChunks(Product p, List<ProductAttr> attrs) {
        List<AdminAiVO.Chunk> chunks = new ArrayList<>();
        addChunk(chunks, p, "商品名称", p.getProductName());
        addChunk(chunks, p, "营销标题", p.getProductTitle());
        addChunk(chunks, p, "关键词", p.getKeywords());
        addChunk(chunks, p, "标签", p.getTags());
        addChunk(chunks, p, "商品简介", p.getBrief());
        addChunk(chunks, p, "价格", p.getPrice() == null ? null : "价格：" + p.getPrice() + " 元");
        for (ProductAttr a : attrs) {
            addChunk(chunks, p, "参数·" + a.getAttrName(),
                    a.getAttrName() + "：" + (a.getAttrValue() == null ? "" : a.getAttrValue()));
        }
        return chunks;
    }

    private void addChunk(List<AdminAiVO.Chunk> chunks, Product p, String type, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        AdminAiVO.Chunk c = new AdminAiVO.Chunk();
        c.setProductId(p.getId());
        c.setProductName(p.getProductName());
        c.setType(type);
        c.setContent(content.trim());
        c.setLength(content.trim().length());
        chunks.add(c);
    }

    private Map<Long, List<ProductAttr>> attrsOf(List<Long> productIds) {
        List<Long> ids = productIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return productAttrMapper.selectList(Wrappers.<ProductAttr>lambdaQuery()
                        .in(ProductAttr::getProductId, ids)
                        .orderByAsc(ProductAttr::getSortOrder))
                .stream().collect(Collectors.groupingBy(ProductAttr::getProductId));
    }

    private Map<Long, SysUser> userMap(List<Long> userIds) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));
    }

    private Map<Long, String> categoryNames() {
        return categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, c -> c.getCategoryName() == null ? "" : c.getCategoryName()));
    }

    private Map<Long, String> brandNames() {
        return brandMapper.selectList(null).stream()
                .collect(Collectors.toMap(Brand::getId, b -> b.getBrandName() == null ? "" : b.getBrandName()));
    }

    /** 取该条助手回答之前最近的一条用户消息（同一会话内） */
    private String previousQuestion(List<ChatMessage> messages, Long beforeId) {
        String q = null;
        for (ChatMessage m : messages) {
            if (m.getId() >= beforeId) {
                break;
            }
            if (m.getRole() != null && m.getRole() == 0) {
                q = m.getContent();
            }
        }
        return q;
    }

    private List<Long> splitIds(String ids) {
        if (ids == null || ids.isBlank()) {
            return List.of();
        }
        List<Long> list = new ArrayList<>();
        for (String s : ids.split(",")) {
            try {
                list.add(Long.valueOf(s.trim()));
            } catch (NumberFormatException ignored) {
                // 非数字 ID 忽略
            }
        }
        return list;
    }

    /** 中文按 2-4 字滑窗过粗，这里用标点/空格切分并过滤停用词，足够做差评热词展示 */
    private List<String> tokenize(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<String> words = new ArrayList<>();
        for (String w : content.split("[\\s，。,.!?！？、；;：:\"'（）()【】\\[\\]]+")) {
            String t = w.trim();
            if (t.length() >= 2 && t.length() <= 10 && !STOP_WORDS.contains(t)) {
                words.add(t);
            }
        }
        return words;
    }

    private <T> PageResult<T> slice(List<T> all, long pageNum, long pageSize) {
        int size = (int) Math.max(pageSize, 1);
        int from = (int) Math.max((pageNum - 1) * size, 0);
        if (from >= all.size()) {
            return PageResult.of(all.size(), pageNum, pageSize, List.of());
        }
        int to = Math.min(from + size, all.size());
        return PageResult.of(all.size(), pageNum, pageSize, all.subList(from, to));
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
