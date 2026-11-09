package com.digitalmall.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 管理端「AI 知识库 / AI 智能导购与运营」视图对象。
 * 全部为只读展示，数据来源为现有表（product / product_attr / chat_session / chat_message / comment / order）
 * 与 Redis 向量索引，不额外建表。
 */
public class AdminAiVO {

    /** 商品知识库列表行 */
    @Data
    public static class KnowledgeProduct {
        private Long id;
        private String productName;
        private String categoryName;
        private String brandName;
        private BigDecimal price;
        private Integer sales;
        private Integer status;
        /** 切片数量（按字段切分） */
        private Integer chunkCount;
        /** 是否已在 Redis 建立向量 */
        private Boolean vectorized;
        /** 向量维度，未向量化时为 null */
        private Integer vectorDim;
        /** 参与向量化的文本长度（字符） */
        private Integer textLength;
        /** 知识文本预览 */
        private String preview;
    }

    /** 知识切片 */
    @Data
    public static class Chunk {
        private Long productId;
        private String productName;
        private String type;
        private String content;
        private Integer length;
    }

    /** 检索结果项 */
    @Data
    public static class SearchItem {
        private Long productId;
        private String productName;
        private BigDecimal price;
        private Integer sales;
        private Double similarity;
    }

    /** 导购会话（任务）行 */
    @Data
    public static class SessionRow {
        private Long id;
        private Long userId;
        private String username;
        private String nickname;
        private String sessionTitle;
        private Integer messageCount;
        private Integer questionCount;
        private String lastIntent;
        private LocalDateTime createdTime;
        private LocalDateTime updatedTime;
    }

    /** 会话内消息 */
    @Data
    public static class MessageRow {
        private Long id;
        private Integer role;
        private String content;
        private String intent;
        private String productIds;
        private Integer tokenCount;
        private LocalDateTime createdTime;
    }

    /** 一次导购运行（以一条助手回答为粒度） */
    @Data
    public static class RunRow {
        private Long id;
        private Long sessionId;
        private Long userId;
        private String username;
        private String question;
        private String intent;
        private Integer productCount;
        private Integer answerLength;
        private Integer tokenCount;
        private LocalDateTime createdTime;
    }

    /** 推荐商品聚合行 */
    @Data
    public static class RecommendRow {
        private Long productId;
        private String productName;
        private BigDecimal price;
        private Integer sales;
        private Integer recommendCount;
        private LocalDateTime lastTime;
    }

    /** 问答对 */
    @Data
    public static class QaRow {
        private Long answerId;
        private Long sessionId;
        private String username;
        private String question;
        private String answer;
        private String intent;
        private Integer productCount;
        private LocalDateTime createdTime;
    }

    /** 评价分析 */
    @Data
    public static class CommentAnalysis {
        private Integer total;
        private Double avgScore;
        private Double goodRate;
        private Integer negativeCount;
        private Integer unrepliedCount;
        private List<Map<String, Object>> distribution;
        private List<Map<String, Object>> keywords;
        private List<Map<String, Object>> negatives;
    }

    /** 运营增长报告 */
    @Data
    public static class Report {
        private Integer sessionCount;
        private Integer questionCount;
        private Integer runCount;
        private Double avgTurns;
        private Integer chatUsers;
        private Integer orderUsers;
        private Integer convertedUsers;
        private Double conversionRate;
        private Integer recommendedProducts;
        private Integer knowledgeProducts;
        private Integer vectorizedProducts;
        private List<Map<String, Object>> trend;
        private List<Map<String, Object>> intents;
    }
}
