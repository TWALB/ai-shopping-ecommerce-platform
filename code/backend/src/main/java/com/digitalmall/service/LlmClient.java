package com.digitalmall.service;

import com.digitalmall.config.LlmProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 大模型客户端（OpenAI 兼容接口）
 * - chatStream：流式对话（/chat/completions，stream=true）
 * - chat：非流式对话（意图识别等结构化输出）
 * - embed：文本向量（/embeddings）
 * - test：连通性自检（管理端「AI 模型配置」页用）
 *
 * 参数全部从 AiConfigService 读取，回落 application.yml：
 * 管理端改完密钥/模型后，下一次调用即生效，不需要重启。
 * WebClient 按 (baseUrl + apiKey) 签名缓存，签名变化时重建，避免每次请求都新建连接池。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmClient {

    private final LlmProperties props;
    private final AiConfigService config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile WebClient webClient;
    private volatile String signature;

    // ==================== 生效参数（库优先，yml 兜底） ====================

    public String baseUrl() {
        return config.value(AiConfigService.MODEL_BASE_URL, props.getBaseUrl());
    }

    public String apiKey() {
        return config.value(AiConfigService.MODEL_API_KEY, props.getApiKey());
    }

    public String model() {
        return config.value(AiConfigService.MODEL_NAME, props.getModel());
    }

    public String embeddingModel() {
        return config.value(AiConfigService.MODEL_EMBEDDING, props.getEmbeddingModel());
    }

    public double temperature() {
        double fallback = props.getTemperature() == null ? 0.3 : props.getTemperature();
        return config.doubleValue(AiConfigService.MODEL_TEMPERATURE, fallback, 0, 2);
    }

    /** 密钥是否来自数据库（否则来自 yml / 环境变量） */
    public boolean apiKeyFromDb() {
        String v = config.get(AiConfigService.MODEL_API_KEY);
        return v != null && !v.isBlank();
    }

    public boolean available() {
        String key = apiKey();
        return key != null && !key.isBlank() && !"your-api-key".equalsIgnoreCase(key.trim());
    }

    private WebClient client() {
        String url = baseUrl();
        String key = apiKey();
        String sig = url + "|" + key;
        WebClient c = webClient;
        if (c == null || !sig.equals(signature)) {
            synchronized (this) {
                if (webClient == null || !sig.equals(signature)) {
                    webClient = WebClient.builder()
                            .baseUrl(url)
                            .defaultHeader("Authorization", "Bearer " + key)
                            .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                            .build();
                    signature = sig;
                    log.info("LLM 客户端已重建：baseUrl={}，model={}", url, model());
                }
                c = webClient;
            }
        }
        return c;
    }

    /** 流式对话：按增量文本返回，失败抛异常由上层降级 */
    public Flux<String> chatStream(List<Map<String, String>> messages) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model());
        body.put("messages", messages);
        body.put("stream", true);
        body.put("temperature", temperature());
        return client().post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .timeout(Duration.ofSeconds(90))
                .map(ServerSentEvent::data)
                .filter(Objects::nonNull)
                .map(this::extractDelta)
                .filter(s -> s != null && !s.isEmpty());
    }

    /** 非流式对话（用于意图识别等），返回 content 原文 */
    public String chat(List<Map<String, String>> messages) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model());
        body.put("messages", messages);
        body.put("stream", false);
        body.put("temperature", temperature());
        String resp = client().post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(60));
        try {
            JsonNode root = objectMapper.readTree(resp);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                return choices.get(0).path("message").path("content").asText("");
            }
        } catch (Exception e) {
            log.warn("解析 LLM 非流式响应失败: {}", e.getMessage());
        }
        return "";
    }

    /** 文本向量化，失败返回 null */
    public float[] embed(String text) {
        if (!available() || text == null || text.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", embeddingModel(),
                    "input", text
            );
            String resp = client().post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(60));
            JsonNode root = objectMapper.readTree(resp);
            JsonNode data = root.path("data");
            if (data.isArray() && !data.isEmpty()) {
                JsonNode emb = data.get(0).path("embedding");
                float[] vec = new float[emb.size()];
                for (int i = 0; i < vec.length; i++) {
                    vec[i] = (float) emb.get(i).asDouble();
                }
                return vec;
            }
        } catch (Exception e) {
            log.warn("Embedding 调用失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 连通性自检：真实发一次 1 轮对话 + 一次向量化。
     * 只用于管理端「测试连接」按钮，不参与线上链路。
     */
    public Map<String, Object> test() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("baseUrl", baseUrl());
        out.put("model", model());
        out.put("embeddingModel", embeddingModel());
        out.put("apiKeyMasked", AiConfigService.mask(apiKey()));
        out.put("apiKeyFromDb", apiKeyFromDb());
        out.put("available", available());
        if (!available()) {
            out.put("ok", false);
            out.put("error", "未配置可用的 API Key（数据库与 yml 均为空或仍是占位符）");
            return out;
        }

        long t0 = System.currentTimeMillis();
        String reply = "";
        try {
            reply = chat(List.of(Map.of("role", "user", "content", "只回复两个字：可用")));
        } catch (Exception e) {
            out.put("ok", false);
            out.put("chatOk", false);
            out.put("chatMs", System.currentTimeMillis() - t0);
            out.put("error", "对话接口调用失败：" + rootMessage(e));
            return out;
        }
        long chatMs = System.currentTimeMillis() - t0;
        boolean chatOk = reply != null && !reply.isBlank();
        out.put("chatOk", chatOk);
        out.put("chatMs", chatMs);
        out.put("chatSample", reply == null ? "" : reply.trim());

        long t1 = System.currentTimeMillis();
        float[] vec = embed("连通性测试");
        long embedMs = System.currentTimeMillis() - t1;
        out.put("embedOk", vec != null);
        out.put("embedMs", embedMs);
        out.put("embedDim", vec == null ? 0 : vec.length);
        out.put("ok", chatOk && vec != null);
        if (!chatOk) {
            out.put("error", "对话接口返回空内容，请检查模型名是否可用");
        } else if (vec == null) {
            out.put("error", "向量接口不可用，请检查向量模型名与账户权限");
        }
        return out;
    }

    private String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    /** 从流式 chunk 中提取增量文本 */
    private String extractDelta(String data) {
        if (data == null || "[DONE]".equals(data.trim())) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode delta = choices.get(0).path("delta");
                if (delta.has("content")) {
                    return delta.path("content").asText("");
                }
            }
        } catch (Exception e) {
            // 非 JSON 帧忽略
        }
        return null;
    }
}
