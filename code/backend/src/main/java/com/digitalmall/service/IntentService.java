package com.digitalmall.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 意图识别：优先调用 LLM 输出结构化 JSON，失败或未配置时降级为规则匹配
 * 返回 {intent, slots:{category, budget, keyword, brand}}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentService {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<String> CATEGORIES = List.of(
            "手机", "笔记本", "平板", "耳机", "手表", "相机", "音箱", "配件", "显示器", "键盘", "鼠标", "充电器");
    private static final List<String> KEYWORDS = List.of(
            "拍照", "影像", "续航", "快充", "性能", "游戏", "轻薄", "便携", "降噪", "音质", "屏幕", "高刷",
            "学生", "办公", "性价比", "旗舰", "长续航", "大存储", "折叠");

    /** 识别意图与槽位 */
    public Map<String, Object> recognize(String content) {
        if (llmClient.available()) {
            try {
                Map<String, Object> result = recognizeByLlm(content);
                if (result != null) {
                    return result;
                }
            } catch (Exception e) {
                log.warn("LLM 意图识别失败，降级规则匹配: {}", e.getMessage());
            }
        }
        return recognizeByRule(content);
    }

    private Map<String, Object> recognizeByLlm(String content) throws Exception {
        String system = "你是电商导购意图识别器。只输出 JSON，不要解释。格式："
                + "{\"intent\":\"find_product|compare|recommend|chat\",\"slots\":{\"category\":\"\",\"budget\":0,\"keyword\":\"\",\"brand\":\"\"}}。"
                + "find_product=找商品；compare=参数对比；recommend=选购建议；chat=闲聊。"
                + "category 从 手机/笔记本/平板/耳机/手表/相机/音箱/配件 中选，没有填空字符串；budget 为数字元，没有填 0；keyword 提炼核心需求（如 拍照/续航/游戏/轻薄），没有填空字符串。";
        String answer = llmClient.chat(List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", content)
        ));
        if (answer == null || answer.isBlank()) {
            return null;
        }
        String json = answer.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        JsonNode root = objectMapper.readTree(json);
        String intent = root.path("intent").asText("find_product");
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("category", root.path("slots").path("category").asText(""));
        slots.put("budget", root.path("slots").path("budget").asDouble(0));
        slots.put("keyword", root.path("slots").path("keyword").asText(""));
        slots.put("brand", root.path("slots").path("brand").asText(""));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("intent", normalizeIntent(intent));
        result.put("slots", slots);
        return result;
    }

    /** 规则降级：关键词分类 + 预算/品类/品牌抽取 */
    public Map<String, Object> recognizeByRule(String content) {
        String text = content == null ? "" : content;
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("category", matchCategory(text));
        slots.put("budget", extractBudget(text));
        slots.put("keyword", matchKeyword(text));
        slots.put("brand", "");

        String intent;
        if (text.contains("对比") || text.contains("比较") || text.contains("pk") || text.contains("哪个好")
                || text.contains("怎么选") || text.contains("vs")) {
            intent = "compare";
        } else if (text.contains("推荐") || text.contains("建议") || text.contains("选哪个") || text.contains("买什么")) {
            intent = "recommend";
        } else if (!"".equals(slots.get("category")) || (Double) slots.get("budget") > 0
                || !"".equals(slots.get("keyword"))) {
            intent = "find_product";
        } else {
            intent = "chat";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("intent", intent);
        result.put("slots", slots);
        return result;
    }

    private String normalizeIntent(String intent) {
        return switch (intent == null ? "" : intent.trim()) {
            case "find_product", "compare", "recommend", "chat" -> intent.trim();
            default -> "find_product";
        };
    }

    private String matchCategory(String text) {
        for (String c : CATEGORIES) {
            if (text.contains(c)) {
                return c;
            }
        }
        return "";
    }

    private String matchKeyword(String text) {
        for (String k : KEYWORDS) {
            if (text.contains(k)) {
                return k;
            }
        }
        return "";
    }

    private double extractBudget(String text) {
        Matcher m = Pattern.compile("(\\d{2,6})\\s*(元|块|人民币|以内|以下|左右)?").matcher(text);
        Matcher m2 = Pattern.compile("预算\\s*(\\d{2,6})").matcher(text);
        if (m2.find()) {
            return Double.parseDouble(m2.group(1));
        }
        while (m.find()) {
            String unit = m.group(2);
            String num = m.group(1);
            if (unit != null && !unit.isBlank()) {
                return Double.parseDouble(num);
            }
            // 裸数字且上下文有预算/价位/千元等语义时采用
            String around = text.substring(Math.max(0, m.start() - 6), Math.min(text.length(), m.end() + 6));
            if (around.contains("预算") || around.contains("价") || around.contains("价位")
                    || around.contains("左右") || around.contains("以下") || around.contains("以内")) {
                return Double.parseDouble(num);
            }
        }
        return 0;
    }
}
