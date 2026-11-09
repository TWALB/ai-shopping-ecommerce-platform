package com.digitalmall.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.digitalmall.entity.SysAiTool;
import com.digitalmall.mapper.SysAiToolMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Function Tool 注册表。
 *
 * 职责边界：
 * - 这里只管「模型能看到哪些工具、每个工具怎么描述、参数长什么样、是否启用」；
 * - 真实执行在 {@link AiToolInvoker}，管理端改不了执行目标，避免把内网接口暴露成可配置项；
 * - 本轮不接入对话主链路：注册表 + 调试台可独立验证，接线属于下一步（需重跑检索评测）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiToolService {

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS `sys_ai_tool` (
              `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
              `tool_code` VARCHAR(64) NOT NULL COMMENT '工具编码，同时是模型看到的 function name',
              `tool_name` VARCHAR(64) NOT NULL COMMENT '展示名称',
              `domain` VARCHAR(16) NOT NULL COMMENT 'product=商品域 biz=业务域',
              `description` VARCHAR(500) DEFAULT NULL COMMENT '给模型看的用途说明',
              `params_schema` TEXT COMMENT '入参 JSON Schema',
              `sample_params` TEXT COMMENT '调试台示例参数',
              `result_hint` VARCHAR(255) DEFAULT NULL COMMENT '返回结构说明',
              `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
              `builtin` TINYINT NOT NULL DEFAULT 1 COMMENT '1=内置，不可删除',
              `sort_no` INT DEFAULT 0 COMMENT '排序',
              `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
              `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (`id`),
              UNIQUE KEY `uk_tool_code` (`tool_code`)
            ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 可调用工具注册表'""";

    private static final String SCHEMA_SEARCH = """
            {"type":"object","properties":{
              "keyword":{"type":"string","description":"核心需求词，如 拍照、续航、游戏"},
              "category":{"type":"string","description":"品类：手机/笔记本/平板/耳机/手表/相机/音箱/配件"},
              "budget":{"type":"number","description":"预算上限，单位元，0 表示不限"},
              "brand":{"type":"string","description":"品牌名，可空"},
              "topK":{"type":"integer","description":"返回条数，1-20，默认 5"}
            }}""";

    private static final String SCHEMA_PRODUCT_ID = """
            {"type":"object","required":["productId"],"properties":{
              "productId":{"type":"integer","description":"商品 ID"}
            }}""";

    private static final String SCHEMA_ORDER_NO = """
            {"type":"object","required":["orderNo"],"properties":{
              "orderNo":{"type":"string","description":"订单号，形如 DM202601010001"}
            }}""";

    private record Seed(String code, String name, String domain, String description,
                        String schema, String sample, String hint, int sort) {
    }

    private static final List<Seed> SEEDS = List.of(
            new Seed("searchProduct", "商品检索", "product",
                    "按关键词、品类、预算召回商品，返回按相似度排序的候选列表。用户提到找某类商品时优先调用。",
                    SCHEMA_SEARCH, "{\"keyword\":\"拍照\",\"category\":\"手机\",\"budget\":5000,\"topK\":5}",
                    "count / items[{productId, productName, price, similarity}]", 1),
            new Seed("getProductDetail", "商品详情", "product",
                    "按商品 ID 查询基础信息与全部参数属性，用于参数对比或回答「这款有什么配置」。",
                    SCHEMA_PRODUCT_ID, "{\"productId\":1}",
                    "productName / price / stock / attrs[{name, value}]", 2),
            new Seed("getPrice", "价格与库存", "product",
                    "按商品 ID 查询实时价格、原价、库存与销量，用于「现在多少钱」「还有货吗」。",
                    SCHEMA_PRODUCT_ID, "{\"productId\":1}",
                    "price / originalPrice / stock / inStock / sales", 3),
            new Seed("getOrderStatus", "订单状态", "biz",
                    "按订单号查询订单状态、金额与收货信息。用户问「我的订单到哪了」时调用，不要写进提示词。",
                    SCHEMA_ORDER_NO, "{\"orderNo\":\"DM202601010001\"}",
                    "status / statusText / payAmount / 各时间节点", 4),
            new Seed("getTrack", "物流轨迹", "biz",
                    "按订单号查询物流公司与签收轨迹，用于「快递到哪了」「什么时候能到」。",
                    SCHEMA_ORDER_NO, "{\"orderNo\":\"DM202601010001\"}",
                    "companyName / logisticsNo / tracks[{trackTime, trackInfo}]", 5)
    );

    private final SysAiToolMapper toolMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AiToolInvoker invoker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        try {
            jdbcTemplate.execute(DDL);
            seedBuiltins();
            log.info("AI 工具注册表就绪，共 {} 个内置工具", SEEDS.size());
        } catch (Exception e) {
            log.warn("AI 工具表初始化失败，功能工具页将不可用: {}", e.getMessage());
        }
    }

    // ==================== 查询 ====================

    public List<SysAiTool> list(String domain, String keyword) {
        var q = Wrappers.<SysAiTool>lambdaQuery();
        if (domain != null && !domain.isBlank()) {
            q.eq(SysAiTool::getDomain, domain);
        }
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(SysAiTool::getToolCode, keyword)
                    .or().like(SysAiTool::getToolName, keyword)
                    .or().like(SysAiTool::getDescription, keyword));
        }
        q.orderByAsc(SysAiTool::getSortNo).orderByAsc(SysAiTool::getId);
        return toolMapper.selectList(q);
    }

    public SysAiTool get(Long id) {
        return toolMapper.selectById(id);
    }

    public SysAiTool byCode(String code) {
        return toolMapper.selectOne(
                Wrappers.<SysAiTool>lambdaQuery().eq(SysAiTool::getToolCode, code).last("limit 1"));
    }

    // ==================== 写 ====================

    public SysAiTool create(SysAiTool tool) {
        validate(tool, true);
        if (byCode(tool.getToolCode()) != null) {
            throw new IllegalArgumentException("工具编码已存在：" + tool.getToolCode());
        }
        tool.setId(null);
        tool.setBuiltin(0);
        if (tool.getEnabled() == null) {
            tool.setEnabled(1);
        }
        if (tool.getSortNo() == null) {
            tool.setSortNo(99);
        }
        tool.setCreateTime(LocalDateTime.now());
        tool.setUpdateTime(LocalDateTime.now());
        toolMapper.insert(tool);
        log.info("新增 AI 工具：{}（{}）", tool.getToolCode(), tool.getToolName());
        return tool;
    }

    /** 只允许改元数据，tool_code 与 builtin 不可变，避免把内置实现「改名换姓」后失效 */
    public SysAiTool update(Long id, SysAiTool patch) {
        SysAiTool db = toolMapper.selectById(id);
        if (db == null) {
            throw new IllegalArgumentException("工具不存在：" + id);
        }
        if (patch.getToolName() != null) {
            db.setToolName(patch.getToolName().trim());
        }
        if (patch.getDescription() != null) {
            db.setDescription(patch.getDescription().trim());
        }
        if (patch.getParamsSchema() != null) {
            checkJson(patch.getParamsSchema(), "参数 Schema");
            db.setParamsSchema(patch.getParamsSchema());
        }
        if (patch.getSampleParams() != null) {
            checkJson(patch.getSampleParams(), "示例参数");
            db.setSampleParams(patch.getSampleParams());
        }
        if (patch.getResultHint() != null) {
            db.setResultHint(patch.getResultHint());
        }
        if (patch.getEnabled() != null) {
            db.setEnabled(patch.getEnabled());
        }
        if (patch.getSortNo() != null) {
            db.setSortNo(patch.getSortNo());
        }
        db.setUpdateTime(LocalDateTime.now());
        toolMapper.updateById(db);
        return db;
    }

    public SysAiTool toggle(Long id, boolean enabled) {
        SysAiTool db = toolMapper.selectById(id);
        if (db == null) {
            throw new IllegalArgumentException("工具不存在：" + id);
        }
        db.setEnabled(enabled ? 1 : 0);
        db.setUpdateTime(LocalDateTime.now());
        toolMapper.updateById(db);
        return db;
    }

    public void delete(Long id) {
        SysAiTool db = toolMapper.selectById(id);
        if (db == null) {
            return;
        }
        if (Integer.valueOf(1).equals(db.getBuiltin())) {
            throw new IllegalArgumentException("内置工具有代码实现，不能删除；如需下线请停用");
        }
        toolMapper.deleteById(id);
    }

    /** 补齐缺失的内置工具（误删了自定义工具不影响，内置的靠这个恢复） */
    @Transactional
    public int restoreBuiltins() {
        return seedBuiltins();
    }

    // ==================== 调试 ====================

    /**
     * 执行一次工具调用并返回可审计的调用记录。
     * 不经过模型：这是「工具本身对不对」的验证，不是「模型会不会选」的验证。
     */
    public Map<String, Object> debug(String code, Map<String, Object> params) {
        SysAiTool tool = byCode(code);
        if (tool == null) {
            throw new IllegalArgumentException("工具不存在：" + code);
        }
        if (!invoker.supported(code)) {
            throw new IllegalArgumentException("工具「" + tool.getToolName() + "」只有元数据，没有执行实现");
        }

        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("traceId", traceId);
        out.put("toolCode", code);
        out.put("toolName", tool.getToolName());
        out.put("domain", tool.getDomain());
        out.put("enabled", tool.getEnabled());
        out.put("request", params == null ? Map.of() : params);

        long t0 = System.currentTimeMillis();
        try {
            Map<String, Object> resp = invoker.invoke(code, params);
            out.put("success", true);
            out.put("response", resp);
        } catch (Exception e) {
            out.put("success", false);
            out.put("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            log.info("工具调试失败 {}（{}）：{}", code, traceId, e.getMessage());
        }
        out.put("costMs", System.currentTimeMillis() - t0);
        return out;
    }

    /** 生成 OpenAI tools 数组：接主链路时直接塞进请求体，这里先用来看「模型会看到什么」 */
    public List<Map<String, Object>> openAiTools(String domain) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SysAiTool t : list(domain, null)) {
            if (!Integer.valueOf(1).equals(t.getEnabled())) {
                continue;
            }
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", t.getToolCode());
            fn.put("description", t.getDescription());
            fn.put("parameters", parseJson(t.getParamsSchema()));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "function");
            item.put("function", fn);
            out.add(item);
        }
        return out;
    }

    // ==================== 内部 ====================

    private int seedBuiltins() {
        int added = 0;
        for (Seed s : SEEDS) {
            if (byCode(s.code()) != null) {
                continue;
            }
            SysAiTool t = new SysAiTool();
            t.setToolCode(s.code());
            t.setToolName(s.name());
            t.setDomain(s.domain());
            t.setDescription(s.description());
            t.setParamsSchema(s.schema());
            t.setSampleParams(s.sample());
            t.setResultHint(s.hint());
            t.setEnabled(1);
            t.setBuiltin(1);
            t.setSortNo(s.sort());
            t.setCreateTime(LocalDateTime.now());
            t.setUpdateTime(LocalDateTime.now());
            toolMapper.insert(t);
            added++;
        }
        return added;
    }

    private void validate(SysAiTool tool, boolean creating) {
        if (tool.getToolCode() == null || tool.getToolCode().isBlank()) {
            throw new IllegalArgumentException("工具编码不能为空");
        }
        String code = tool.getToolCode().trim();
        if (!code.matches("^[a-zA-Z][a-zA-Z0-9_]{2,40}$")) {
            throw new IllegalArgumentException("工具编码需以字母开头，仅含字母/数字/下划线，长度 3-40");
        }
        if (tool.getToolName() == null || tool.getToolName().isBlank()) {
            throw new IllegalArgumentException("工具名称不能为空");
        }
        String domain = tool.getDomain();
        if (!"product".equals(domain) && !"biz".equals(domain)) {
            throw new IllegalArgumentException("业务域只能是 product 或 biz");
        }
        if (tool.getParamsSchema() == null || tool.getParamsSchema().isBlank()) {
            throw new IllegalArgumentException("参数 Schema 不能为空");
        }
        checkJson(tool.getParamsSchema(), "参数 Schema");
        if (tool.getSampleParams() != null && !tool.getSampleParams().isBlank()) {
            checkJson(tool.getSampleParams(), "示例参数");
        }
        if (creating) {
            tool.setToolCode(code);
        }
    }

    private void checkJson(String text, String label) {
        try {
            objectMapper.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException(label + " 不是合法 JSON：" + e.getMessage());
        }
    }

    private Object parseJson(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            return Map.of("type", "object");
        }
    }
}
