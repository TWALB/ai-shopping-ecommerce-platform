package com.digitalmall.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.digitalmall.entity.Product;
import com.digitalmall.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 「AI 模型配置」的业务层。
 *
 * 只做三件事：
 * 1. 汇总当前生效参数（密钥掩码）与向量库状态，供页面展示；
 * 2. 保存模型参数并给出风险提示（换向量模型 → 维度可能变化，需重建）；
 * 3. 连通性自检，把「对话能不能通」和「向量能不能用」分开报，避免一个失败掩盖另一个。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelService {

    private final AiConfigService config;
    private final LlmClient llmClient;
    private final ProductRetrievalService retrievalService;
    private final ProductMapper productMapper;

    /** 当前生效参数 + 向量库状态 */
    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("baseUrl", llmClient.baseUrl());
        out.put("model", llmClient.model());
        out.put("embeddingModel", llmClient.embeddingModel());
        out.put("temperature", llmClient.temperature());
        out.put("apiKeyMasked", AiConfigService.mask(llmClient.apiKey()));
        out.put("apiKeyFromDb", llmClient.apiKeyFromDb());
        out.put("available", llmClient.available());
        out.put("vector", vectorStatus());
        out.put("params", config.listByScene(AiConfigService.SCENE_MODEL));

        List<String> hints = new ArrayList<>();
        if (!llmClient.available()) {
            hints.add("未配置可用 API Key，导购当前降级为本地检索 + 模板回答");
        }
        if (!Boolean.TRUE.equals(((Map<?, ?>) out.get("vector")).get("ready"))) {
            hints.add("向量通路未就绪，语义召回不可用（仍可走本地检索）");
        }
        out.put("hints", hints);
        return out;
    }

    /**
     * 保存模型参数。只接受 model 场景下已声明的配置键，避免管理端越权写入提示词。
     * 空字符串表示「不修改」，密钥传空同理（要清除请用恢复默认）。
     */
    public Map<String, Object> save(Map<String, String> body, Long operator) {
        List<String> warnings = new ArrayList<>();
        String oldEmbedding = llmClient.embeddingModel();

        saveOne(body, AiConfigService.MODEL_BASE_URL, operator);
        saveOne(body, AiConfigService.MODEL_NAME, operator);
        saveOne(body, AiConfigService.MODEL_EMBEDDING, operator);
        saveOne(body, AiConfigService.MODEL_TEMPERATURE, operator);
        // 密钥单独处理：空值代表沿用 yml，不做覆盖
        String apiKey = body.get(AiConfigService.MODEL_API_KEY);
        if (apiKey != null && !apiKey.isBlank()) {
            config.save(AiConfigService.MODEL_API_KEY, apiKey, operator);
        }

        String newEmbedding = llmClient.embeddingModel();
        if (!oldEmbedding.equals(newEmbedding)) {
            warnings.add("已切换向量模型（" + oldEmbedding + " → " + newEmbedding
                    + "），请到「商品知识库」执行全量重建，否则旧向量维度不匹配会导致语义召回失效");
        }
        Map<String, Object> out = status();
        out.put("warnings", warnings);
        return out;
    }

    private void saveOne(Map<String, String> body, String key, Long operator) {
        String v = body.get(key);
        if (v == null || v.isBlank()) {
            return;
        }
        config.save(key, v, operator);
    }

    /** 连通性自检：真实调用一次对话 + 一次向量化 */
    public Map<String, Object> test() {
        Map<String, Object> result = llmClient.test();
        Map<String, Object> vector = vectorStatus();
        result.put("vector", vector);
        // 维度守卫：探针维度与存量向量维度不一致时，说明换了模型但没重建。
        // 注意用 intValue 比较：>127 的整数不在 Integer 缓存里，直接 != 比的是引用，会误报不一致
        Object dim = vector.get("dim");
        Object probe = result.get("embedDim");
        boolean mismatch = dim instanceof Integer d && d > 0
                && probe instanceof Integer p && p > 0
                && d.intValue() != p.intValue();
        result.put("dimMismatch", mismatch);
        if (mismatch) {
            result.put("error", "向量维度不一致：存量 " + dim + " 维，当前模型 " + probe + " 维，请全量重建向量库");
        }
        return result;
    }

    /** 向量库状态：是否就绪、维度、已向量化条数、商品总数 */
    public Map<String, Object> vectorStatus() {
        Map<String, Object> v = new LinkedHashMap<>();
        boolean ready = retrievalService.vectorReady();
        Set<Long> ids = retrievalService.vectorizedIds();
        Long total = productMapper.selectCount(Wrappers.<Product>lambdaQuery());
        v.put("ready", ready);
        v.put("vectorized", ids == null ? 0 : ids.size());
        v.put("total", total == null ? 0 : total);
        int dim = 0;
        if (ids != null && !ids.isEmpty()) {
            dim = retrievalService.vectorDim(ids.iterator().next());
        }
        v.put("dim", dim);
        return v;
    }
}
