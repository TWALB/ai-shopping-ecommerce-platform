package com.digitalmall.controller;

import com.digitalmall.common.Result;
import com.digitalmall.entity.SysAiTool;
import com.digitalmall.security.RequireRole;
import com.digitalmall.security.UserContext;
import com.digitalmall.service.AiConfigService;
import com.digitalmall.service.AiModelService;
import com.digitalmall.service.AiToolService;
import com.digitalmall.vo.AiConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端「AI 基础配置」：模型配置 / Prompt 模板 / Function Tool / 工具调试。
 * 与 AdminAiController（知识库 + 运行观测）共用 /api/admin/ai 前缀，路径不重叠。
 */
@RestController
@RequestMapping("/api/admin/ai")
@RequireRole(RequireRole.ADMIN)
@RequiredArgsConstructor
public class AdminAiBaseController {

    private final AiModelService aiModelService;
    private final AiConfigService aiConfigService;
    private final AiToolService aiToolService;

    // ==================== AI 模型配置 ====================

    /** 当前生效参数（密钥掩码）、向量库状态与风险提示 */
    @GetMapping("/model")
    public Result<Map<String, Object>> modelStatus() {
        return Result.success(aiModelService.status());
    }

    /** 保存模型参数：写库即生效，无需重启 */
    @PutMapping("/model")
    public Result<Map<String, Object>> saveModel(@RequestBody Map<String, String> body) {
        return Result.success(aiModelService.save(body, UserContext.getUserId()));
    }

    /** 连通性自检：真实发一次对话 + 一次向量化，返回耗时与维度 */
    @PostMapping("/model/test")
    public Result<Map<String, Object>> testModel() {
        return Result.success(aiModelService.test());
    }

    // ==================== Prompt 模板 ====================

    /** 模板列表，按场景分组（chat / intent / param），不含模型参数 */
    @GetMapping("/prompt")
    public Result<Map<String, List<AiConfigVO>>> promptList() {
        Map<String, List<AiConfigVO>> out = new LinkedHashMap<>();
        out.put(AiConfigService.SCENE_CHAT, aiConfigService.listByScene(AiConfigService.SCENE_CHAT));
        out.put(AiConfigService.SCENE_INTENT, aiConfigService.listByScene(AiConfigService.SCENE_INTENT));
        out.put(AiConfigService.SCENE_PARAM, aiConfigService.listByScene(AiConfigService.SCENE_PARAM));
        return Result.success(out);
    }

    /** 保存模板：只接受非 model 场景，防止从这里把密钥改掉 */
    @PutMapping("/prompt")
    public Result<AiConfigVO> savePrompt(@RequestBody Map<String, String> body) {
        String key = body.get("key");
        assertNotModel(key);
        return Result.success(aiConfigService.save(key, body.get("value"), UserContext.getUserId()));
    }

    /** 恢复模板默认值 */
    @PostMapping("/prompt/reset")
    public Result<AiConfigVO> resetPrompt(@RequestParam String key) {
        assertNotModel(key);
        return Result.success(aiConfigService.reset(key, UserContext.getUserId()));
    }

    private void assertNotModel(String key) {
        boolean isModel = aiConfigService.listByScene(AiConfigService.SCENE_MODEL).stream()
                .anyMatch(v -> v.getKey().equals(key));
        if (isModel) {
            throw new IllegalArgumentException("模型接入参数请到「AI 模型配置」页修改");
        }
    }

    // ==================== Function Tool ====================

    /** 工具列表（可按业务域过滤） */
    @GetMapping("/tools")
    public Result<List<SysAiTool>> tools(@RequestParam(required = false) String domain,
                                         @RequestParam(required = false) String keyword) {
        return Result.success(aiToolService.list(domain, keyword));
    }

    /** 新增工具（仅元数据，无执行实现时调试会明确报错） */
    @PostMapping("/tools")
    public Result<SysAiTool> createTool(@RequestBody SysAiTool tool) {
        return Result.success(aiToolService.create(tool));
    }

    /** 修改工具元数据：编码与内置标记不可变 */
    @PutMapping("/tools/{id}")
    public Result<SysAiTool> updateTool(@PathVariable Long id, @RequestBody SysAiTool patch) {
        return Result.success(aiToolService.update(id, patch));
    }

    /** 启停工具 */
    @PostMapping("/tools/{id}/toggle")
    public Result<SysAiTool> toggleTool(@PathVariable Long id, @RequestParam boolean enabled) {
        return Result.success(aiToolService.toggle(id, enabled));
    }

    /** 删除自定义工具；内置工具会被拒绝 */
    @DeleteMapping("/tools/{id}")
    public Result<Void> deleteTool(@PathVariable Long id) {
        aiToolService.delete(id);
        return Result.success(null);
    }

    /** 补齐缺失的内置工具 */
    @PostMapping("/tools/restore-builtin")
    public Result<Map<String, Object>> restoreBuiltin() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("restored", aiToolService.restoreBuiltins());
        return Result.success(out);
    }

    /** 工具调试：直接执行，不经过模型，返回请求/响应/耗时/调用号 */
    @PostMapping("/tools/{code}/debug")
    public Result<Map<String, Object>> debugTool(@PathVariable String code,
                                                 @RequestBody(required = false) Map<String, Object> params) {
        return Result.success(aiToolService.debug(code, params));
    }

    /** 模型实际会看到的 tools 数组（OpenAI 兼容格式），便于核对描述与参数 */
    @GetMapping("/tools/openai")
    public Result<List<Map<String, Object>>> openaiTools(@RequestParam(required = false) String domain) {
        return Result.success(aiToolService.openAiTools(domain));
    }
}
