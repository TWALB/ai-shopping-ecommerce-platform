package com.digitalmall.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.digitalmall.config.LlmProperties;
import com.digitalmall.entity.SysAiConfig;
import com.digitalmall.mapper.SysAiConfigMapper;
import com.digitalmall.vo.AiConfigVO;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 配置中心：模型参数 + 提示词模板 + 运行参数，统一配置表。
 *
 * 设计要点：
 * - 提示词是运营资产，不是代码常量：改文案不该走「改代码 → 重编译 → 重启」；
 * - 保存即生效：写库后同步刷新本地缓存，下一个请求就读到新值（单实例部署无需额外通知）；
 * - 可回滚到默认：每行都存 default_value，一键恢复，避免改坏后无法复原；
 * - 密钥只写不读：api_key 在列表接口里一律掩码，且默认不落库，留空即回落到 application.yml；
 * - 表不存在时降级：建表/播种失败只告警，读取自动回落到代码内置默认值，保证服务能起。
 */
@Slf4j
@Service
public class AiConfigService {

    /** 导购回答的系统提示词 */
    public static final String CHAT_PROMPT = "chat.system_prompt";
    /** 意图识别的系统提示词 */
    public static final String INTENT_PROMPT = "intent.system_prompt";
    /** 每个商品注入给模型的参数条数上限 */
    public static final String ATTR_LIMIT = "chat.attr_limit";

    /** 模型接入参数（改完即热切换，不需要重启） */
    public static final String MODEL_BASE_URL = "model.base_url";
    public static final String MODEL_API_KEY = "model.api_key";
    public static final String MODEL_NAME = "model.name";
    public static final String MODEL_EMBEDDING = "model.embedding";
    public static final String MODEL_TEMPERATURE = "model.temperature";

    /** 场景：模型接入 / 导购回答 / 意图识别 / 运行参数 */
    public static final String SCENE_MODEL = "model";
    public static final String SCENE_CHAT = "chat";
    public static final String SCENE_INTENT = "intent";
    public static final String SCENE_PARAM = "param";

    /** 默认导购提示词：与迁移前硬编码一致，仅把「召回件数」抽成占位符以便演示模板能力 */
    public static final String DEFAULT_CHAT_PROMPT = """
            你是数码电商平台的智能导购助手。规则：
            1. 只能推荐下方【可推荐商品】中给出的商品，不得编造不存在的商品或参数；
            2. 结合用户预算与需求说明推荐理由，参数对比时用简洁表格（本次召回 {{productCount}} 件候选，只能使用这些商品）；
            3. 回答用中文、分点、简洁，不要输出与导购无关的内容；
            4. 若没有合适商品，如实告知并建议调整预算或需求。""";

    /** 默认意图识别提示词：与迁移前硬编码完全一致 */
    public static final String DEFAULT_INTENT_PROMPT =
            "你是电商导购意图识别器。只输出 JSON，不要解释。格式："
                    + "{\"intent\":\"find_product|compare|recommend|chat\",\"slots\":{\"category\":\"\",\"budget\":0,\"keyword\":\"\",\"brand\":\"\"}}。"
                    + "find_product=找商品；compare=参数对比；recommend=选购建议；chat=闲聊。"
                    + "category 从 手机/笔记本/平板/耳机/手表/相机/音箱/配件 中选，没有填空字符串；budget 为数字元，没有填 0；keyword 提炼核心需求（如 拍照/续航/游戏/轻薄），没有填空字符串。";

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS `sys_ai_config` (
              `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
              `config_key` VARCHAR(64) NOT NULL COMMENT '配置键',
              `config_name` VARCHAR(64) NOT NULL COMMENT '展示名称',
              `config_value` TEXT NOT NULL COMMENT '当前值',
              `default_value` TEXT NOT NULL COMMENT '内置默认值',
              `description` VARCHAR(255) DEFAULT NULL COMMENT '说明与可用变量',
              `version` INT NOT NULL DEFAULT 1 COMMENT '保存后自增',
              `updated_by` BIGINT DEFAULT NULL COMMENT '最后修改人',
              `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
              `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (`id`),
              UNIQUE KEY `uk_config_key` (`config_key`)
            ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 提示词与运行参数配置'""";

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{\\s*(\\w+)\\s*}}");

    /**
     * 一项配置的定义。
     * scene 决定在管理端哪个页面出现；secret 表示只写不读（列表里打码）；
     * allowEmpty 用于「留空即回落 yml」的密钥类配置。
     */
    private record Def(String key, String name, String value, String description,
                       String scene, boolean secret, boolean allowEmpty) {
    }

    private final SysAiConfigMapper configMapper;
    private final JdbcTemplate jdbcTemplate;
    private final List<Def> defs;

    /** key -> 已生效的值；保存/恢复时同步刷新，读路径零 DB 往返 */
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(String value, int version, LocalDateTime updateTime) {
    }

    public AiConfigService(SysAiConfigMapper configMapper, JdbcTemplate jdbcTemplate, LlmProperties props) {
        this.configMapper = configMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.defs = buildDefs(props);
    }

    /** 模型参数的默认值取自 application.yml，保证「没配过」时行为与迁移前完全一致 */
    private List<Def> buildDefs(LlmProperties props) {
        return List.of(
                new Def(MODEL_BASE_URL, "接口地址 Base URL", props.getBaseUrl(),
                        "OpenAI 兼容接口根地址，末尾不要带 /chat/completions", SCENE_MODEL, false, false),
                new Def(MODEL_API_KEY, "API Key", "",
                        "留空表示沿用 application.yml / 环境变量 LLM_API_KEY。写入后仅展示掩码，不再回显明文",
                        SCENE_MODEL, true, true),
                new Def(MODEL_NAME, "对话模型", props.getModel(),
                        "用于导购回答与意图识别的模型名", SCENE_MODEL, false, false),
                new Def(MODEL_EMBEDDING, "向量模型", props.getEmbeddingModel(),
                        "更换后向量维度可能变化，需重新全量重建向量库", SCENE_MODEL, false, false),
                new Def(MODEL_TEMPERATURE, "温度 temperature", String.valueOf(props.getTemperature() == null ? 0.3 : props.getTemperature()),
                        "0-2，越小越稳定，导购建议 0.2-0.5", SCENE_MODEL, false, false),
                new Def(CHAT_PROMPT, "导购回答系统提示词", DEFAULT_CHAT_PROMPT,
                        "决定模型怎么说话。可用变量：{{productCount}} 本次召回件数、{{intentLabel}} 意图中文名",
                        SCENE_CHAT, false, false),
                new Def(INTENT_PROMPT, "意图识别系统提示词", DEFAULT_INTENT_PROMPT,
                        "决定槽位怎么抽：意图枚举、品类候选、预算与关键词口径。修改后立即影响新提问的识别结果",
                        SCENE_INTENT, false, false),
                new Def(ATTR_LIMIT, "参数注入条数", "6",
                        "每个商品注入给模型的参数条数上限（1-12）。太小模型无参数可比，太大挤占上下文",
                        SCENE_PARAM, false, false)
        );
    }

    @PostConstruct
    public void init() {
        try {
            jdbcTemplate.execute(DDL);
            seedDefaults();
            log.info("AI 配置中心就绪，共 {} 项配置", defs.size());
        } catch (Exception e) {
            log.warn("AI 配置表初始化失败，将使用内置默认提示词: {}", e.getMessage());
        }
    }

    // ==================== 读 ====================

    /** 取配置当前值；表/行缺失时回落到代码默认值，保证导购功能不因配置故障不可用 */
    public String get(String key) {
        Cached cached = cache.get(key);
        if (cached != null) {
            return cached.value();
        }
        SysAiConfig row = selectByKey(key);
        if (row != null) {
            cache.put(key, new Cached(row.getConfigValue(), versionOf(row), row.getUpdateTime()));
            return row.getConfigValue();
        }
        Def def = defOf(key);
        return def == null ? null : def.value();
    }

    /** 取非空值，空白时用 fallback（密钥留空 → 回落 yml 就靠这个） */
    public String value(String key, String fallback) {
        String v = get(key);
        return v == null || v.isBlank() ? fallback : v.trim();
    }

    /** 渲染占位符：只做字符串替换，不引入模板引擎，避免运营改动带来执行风险 */
    public String render(String key, Map<String, String> vars) {
        String template = get(key);
        if (template == null || vars == null || vars.isEmpty()) {
            return template;
        }
        String out = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    /** 取整数型参数并夹在区间内，非法值回落默认 */
    public int intValue(String key, int fallback, int min, int max) {
        try {
            int v = Integer.parseInt(String.valueOf(get(key)).trim());
            return Math.max(min, Math.min(v, max));
        } catch (Exception e) {
            return fallback;
        }
    }

    /** 取小数型参数，非法值回落默认 */
    public double doubleValue(String key, double fallback, double min, double max) {
        try {
            double v = Double.parseDouble(String.valueOf(get(key)).trim());
            return Math.max(min, Math.min(v, max));
        } catch (Exception e) {
            return fallback;
        }
    }

    /** 全部配置（密钥已掩码），供管理端列表 */
    public List<AiConfigVO> list() {
        List<AiConfigVO> out = new ArrayList<>();
        for (Def def : defs) {
            out.add(toVo(def));
        }
        return out;
    }

    /** 按场景取配置，Prompt 模板页按场景分 tab 就靠它 */
    public List<AiConfigVO> listByScene(String scene) {
        List<AiConfigVO> out = new ArrayList<>();
        for (Def def : defs) {
            if (scene == null || scene.isBlank() || scene.equals(def.scene())) {
                out.add(toVo(def));
            }
        }
        return out;
    }

    /** 用示例变量渲染一次，供管理端「预览」使用（不调用模型，不产生费用） */
    public Map<String, Object> preview(String key, Map<String, String> vars) {
        Def def = defOf(key);
        if (def == null) {
            throw new IllegalArgumentException("未知配置项：" + key);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("key", key);
        out.put("variables", extractVars(get(key)));
        out.put("rendered", render(key, vars));
        return out;
    }

    // ==================== 写 ====================

    public AiConfigVO save(String key, String value, Long operator) {
        Def def = defOf(key);
        if (def == null) {
            throw new IllegalArgumentException("未知配置项：" + key);
        }
        // 前端把掩码原样提交回来 = 没打算改密钥，直接视为无变更，避免把 **** 写进库
        if (def.secret() && value != null && value.contains("****")) {
            return toVo(def);
        }
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isBlank() && !def.allowEmpty()) {
            throw new IllegalArgumentException("配置内容不能为空");
        }
        validate(key, trimmed);

        SysAiConfig row = selectByKey(key);
        if (row == null) {
            row = new SysAiConfig();
            row.setConfigKey(key);
            row.setConfigName(def.name());
            row.setConfigValue(trimmed);
            row.setDefaultValue(def.value());
            row.setDescription(def.description());
            row.setVersion(1);
            row.setUpdatedBy(operator);
            row.setCreateTime(LocalDateTime.now());
            row.setUpdateTime(LocalDateTime.now());
            configMapper.insert(row);
        } else {
            row.setConfigValue(trimmed);
            row.setVersion(versionOf(row) + 1);
            row.setUpdatedBy(operator);
            row.setUpdateTime(LocalDateTime.now());
            configMapper.updateById(row);
        }
        cache.put(key, new Cached(trimmed, versionOf(row), row.getUpdateTime()));
        log.info("AI 配置已更新：{}（v{}，操作人 {}）", key, versionOf(row), operator);
        return toVo(def);
    }

    /** 恢复内置默认值（同样走保存路径，版本号继续自增，可追溯） */
    public AiConfigVO reset(String key, Long operator) {
        Def def = defOf(key);
        if (def == null) {
            throw new IllegalArgumentException("未知配置项：" + key);
        }
        SysAiConfig row = selectByKey(key);
        String fallback = row == null || row.getDefaultValue() == null ? def.value() : row.getDefaultValue();
        return save(key, fallback, operator);
    }

    /** 暴露配置定义，供模型配置页拼「哪些项可改」 */
    public List<Map<String, Object>> schema(String scene) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Def def : defs) {
            if (scene != null && !scene.isBlank() && !scene.equals(def.scene())) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", def.key());
            m.put("name", def.name());
            m.put("scene", def.scene());
            m.put("secret", def.secret());
            m.put("description", def.description());
            out.add(m);
        }
        return out;
    }

    // ==================== 内部 ====================

    private void validate(String key, String v) {
        if (ATTR_LIMIT.equals(key)) {
            int n = parseInt(key, v);
            if (n < 1 || n > 12) {
                throw new IllegalArgumentException("参数注入条数需在 1-12 之间");
            }
        }
        if (MODEL_TEMPERATURE.equals(key)) {
            double d;
            try {
                d = Double.parseDouble(v);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("温度必须是数字");
            }
            if (d < 0 || d > 2) {
                throw new IllegalArgumentException("温度需在 0-2 之间");
            }
        }
        if (MODEL_BASE_URL.equals(key) && !v.startsWith("http")) {
            throw new IllegalArgumentException("Base URL 需以 http:// 或 https:// 开头");
        }
        if (MODEL_NAME.equals(key) && v.contains(" ")) {
            throw new IllegalArgumentException("模型名不能包含空格");
        }
    }

    private int parseInt(String key, String v) {
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key.equals(ATTR_LIMIT) ? "参数注入条数必须是数字" : "必须是数字");
        }
    }

    private void seedDefaults() {
        for (Def def : defs) {
            Long count = configMapper.selectCount(
                    Wrappers.<SysAiConfig>lambdaQuery().eq(SysAiConfig::getConfigKey, def.key()));
            if (count != null && count > 0) {
                continue;
            }
            SysAiConfig row = new SysAiConfig();
            row.setConfigKey(def.key());
            row.setConfigName(def.name());
            row.setConfigValue(def.value());
            row.setDefaultValue(def.value());
            row.setDescription(def.description());
            row.setVersion(1);
            row.setCreateTime(LocalDateTime.now());
            row.setUpdateTime(LocalDateTime.now());
            configMapper.insert(row);
        }
    }

    private SysAiConfig selectByKey(String key) {
        try {
            return configMapper.selectOne(
                    Wrappers.<SysAiConfig>lambdaQuery().eq(SysAiConfig::getConfigKey, key).last("limit 1"));
        } catch (Exception e) {
            log.debug("读取 AI 配置失败（表可能不存在）: {}", e.getMessage());
            return null;
        }
    }

    private AiConfigVO toVo(Def def) {
        SysAiConfig row = selectByKey(def.key());
        String raw = row == null ? def.value() : row.getConfigValue();
        AiConfigVO vo = new AiConfigVO();
        vo.setKey(def.key());
        vo.setName(def.name());
        vo.setScene(def.scene());
        vo.setSecret(def.secret());
        vo.setDescription(def.description());
        vo.setValue(def.secret() ? mask(raw) : raw);
        vo.setDefaultValue(def.secret() ? mask(row == null ? def.value() : row.getDefaultValue()) : (row == null ? def.value() : row.getDefaultValue()));
        vo.setConfigured(row != null && row.getConfigValue() != null && !row.getConfigValue().isBlank());
        vo.setVersion(row == null ? 0 : versionOf(row));
        vo.setUpdateTime(row == null ? null : row.getUpdateTime());
        vo.setUpdatedBy(row == null ? null : row.getUpdatedBy());
        vo.setVariables(extractVars(raw));
        return vo;
    }

    /** 密钥掩码：保留首 4 位与末 4 位，中间固定 4 个星号，长度不足以掩码时全掩 */
    public static String mask(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        if (raw.length() <= 8) {
            return "****";
        }
        return raw.substring(0, 4) + "****" + raw.substring(raw.length() - 4);
    }

    private Def defOf(String key) {
        return defs.stream().filter(d -> d.key().equals(key)).findFirst().orElse(null);
    }

    private List<String> extractVars(String template) {
        List<String> vars = new ArrayList<>();
        if (template == null) {
            return vars;
        }
        Matcher m = VAR_PATTERN.matcher(template);
        while (m.find()) {
            String name = m.group(1);
            if (!vars.contains(name)) {
                vars.add(name);
            }
        }
        return vars;
    }

    private int versionOf(SysAiConfig row) {
        return row.getVersion() == null ? 1 : row.getVersion();
    }
}
