package com.digitalmall.controller;

import com.digitalmall.common.PageResult;
import com.digitalmall.common.Result;
import com.digitalmall.security.RequireRole;
import com.digitalmall.service.AdminAiService;
import com.digitalmall.vo.AdminAiVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端 AI 模块：知识库（RAG）/ 智能导购与运营。
 * 全部只读聚合，仅管理员可访问；向量重建复用 ProductRetrievalService。
 */
@RestController
@RequestMapping("/api/admin/ai")
@RequireRole(RequireRole.ADMIN)
@RequiredArgsConstructor
public class AdminAiController {

    private final AdminAiService adminAiService;

    // ==================== AI 知识库（RAG） ====================

    /** 商品知识库列表（含切片数 / 向量同步状态） */
    @GetMapping("/knowledge/products")
    public Result<PageResult<AdminAiVO.KnowledgeProduct>> knowledgeProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean vectorized,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(adminAiService.knowledgeProducts(keyword, vectorized, pageNum, pageSize));
    }

    /** 重建向量：productId 为空则按 full 参数全量重建 */
    @PostMapping("/knowledge/sync")
    public Result<Map<String, Object>> syncKnowledge(@RequestParam(required = false) Long productId,
                                                     @RequestParam(defaultValue = "true") boolean full) {
        return Result.success(adminAiService.sync(productId, full));
    }

    /** 商品知识切片明细 */
    @GetMapping("/knowledge/chunks")
    public Result<List<AdminAiVO.Chunk>> chunks(@RequestParam(required = false) Long productId) {
        return Result.success(adminAiService.chunks(productId));
    }

    /** 切片概览（每个商品的切片数与文本长度） */
    @GetMapping("/knowledge/chunk-summary")
    public Result<List<Map<String, Object>>> chunkSummary() {
        return Result.success(adminAiService.chunkSummary());
    }

    /** Embedding 检索：与线上对话同一条链路，返回召回模式与耗时 */
    @GetMapping("/knowledge/search")
    public Result<Map<String, Object>> search(@RequestParam String query,
                                              @RequestParam(defaultValue = "5") int topK,
                                              @RequestParam(defaultValue = "true") boolean vector) {
        return Result.success(adminAiService.search(query, topK, vector));
    }

    // ==================== AI 智能导购与运营 ====================

    /** 导购会话（任务）分页 */
    @GetMapping("/session/page")
    public Result<PageResult<AdminAiVO.SessionRow>> sessionPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(adminAiService.sessionPage(keyword, pageNum, pageSize));
    }

    /** 会话明细 */
    @GetMapping("/session/{id}/messages")
    public Result<List<AdminAiVO.MessageRow>> sessionMessages(@PathVariable Long id) {
        return Result.success(adminAiService.sessionMessages(id));
    }

    /** Agent Run 运行记录（以助手回答为粒度） */
    @GetMapping("/run/page")
    public Result<PageResult<AdminAiVO.RunRow>> runPage(
            @RequestParam(required = false) String intent,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(adminAiService.runPage(intent, pageNum, pageSize));
    }

    /** Agent Step 执行步骤（按运行记录反推四步） */
    @GetMapping("/run/{id}/steps")
    public Result<Map<String, Object>> runSteps(@PathVariable Long id) {
        return Result.success(adminAiService.runSteps(id));
    }

    /** AI 推荐商品排行 */
    @GetMapping("/recommend/top")
    public Result<List<AdminAiVO.RecommendRow>> recommendTop(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "20") int limit) {
        return Result.success(adminAiService.recommendTop(days, limit));
    }

    /** AI 商品问答（提问 + 回答成对） */
    @GetMapping("/qa/page")
    public Result<PageResult<AdminAiVO.QaRow>> qaPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(adminAiService.qaPage(keyword, pageNum, pageSize));
    }

    /** AI 评价分析 */
    @GetMapping("/comment/analysis")
    public Result<AdminAiVO.CommentAnalysis> commentAnalysis(@RequestParam(defaultValue = "30") int days) {
        return Result.success(adminAiService.commentAnalysis(days));
    }

    /** AI 运营增长报告 */
    @GetMapping("/report/overview")
    public Result<AdminAiVO.Report> report(@RequestParam(defaultValue = "7") int days) {
        return Result.success(adminAiService.report(days));
    }
}
