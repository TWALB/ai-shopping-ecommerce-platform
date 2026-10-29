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
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 大模型客户端（OpenAI 兼容接口）
 * - chatStream：流式对话（/chat/completions，stream=true）
 * - chat：非流式对话（意图识别等结构化输出）
 * - embed：文本向量（/embeddings）
 * 未配置可用 api-key 时 available() 返回 false，由上层降级
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmClient {

    private final LlmProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile WebClient webClient;

    public boolean available() {
        return props.available();
    }

    private WebClient client() {
        if (webClient == null) {
            synchronized (this) {
                if (webClient == null) {
                    webClient = WebClient.builder()
                            .baseUrl(props.getBaseUrl())
                            .defaultHeader("Authorization", "Bearer " + props.getApiKey())
                            .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                            .build();
                }
            }
        }
        return webClient;
    }

    /** 流式对话：按增量文本返回，失败抛异常由上层降级 */
    public Flux<String> chatStream(List<Map<String, String>> messages) {
        Map<String, Object> body = Map.of(
                "model", props.getModel(),
                "messages", messages,
                "stream", true,
                "temperature", props.getTemperature() == null ? 0.3 : props.getTemperature()
        );
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
        Map<String, Object> body = Map.of(
                "model", props.getModel(),
                "messages", messages,
                "stream", false,
                "temperature", props.getTemperature() == null ? 0.3 : props.getTemperature()
        );
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
                    "model", props.getEmbeddingModel(),
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
