import request from './request'

// 管理端 AI 模块接口（知识库 / 智能导购与运营）
export const getKnowledgeProducts = (params) => request.get('/admin/ai/knowledge/products', { params })
export const syncAiKnowledge = (params) => request.post('/admin/ai/knowledge/sync', null, { params })
export const getAiChunks = (params) => request.get('/admin/ai/knowledge/chunks', { params })
export const getChunkSummary = () => request.get('/admin/ai/knowledge/chunk-summary')
export const aiKnowledgeSearch = (params) => request.get('/admin/ai/knowledge/search', { params })

export const getAiSessionPage = (params) => request.get('/admin/ai/session/page', { params })
export const getAiSessionMessages = (id) => request.get(`/admin/ai/session/${id}/messages`)
export const getAiRunPage = (params) => request.get('/admin/ai/run/page', { params })
export const getAiRunSteps = (id) => request.get(`/admin/ai/run/${id}/steps`)
export const getAiRecommendTop = (params) => request.get('/admin/ai/recommend/top', { params })
export const getAiQaPage = (params) => request.get('/admin/ai/qa/page', { params })
export const getAiCommentAnalysis = (params) => request.get('/admin/ai/comment/analysis', { params })
export const getAiReport = (params) => request.get('/admin/ai/report/overview', { params })

// AI 配置中心（提示词与运行参数）
export const getAiConfig = () => request.get('/admin/ai/config')
export const saveAiConfig = (data) => request.put('/admin/ai/config', data)
export const resetAiConfig = (key) => request.post('/admin/ai/config/reset', null, { params: { key } })
export const previewAiConfig = (params) => request.get('/admin/ai/config/preview', { params })

// ===== AI 模型配置（基础配置） =====
export const getAiModel = () => request.get('/admin/ai/model')
export const saveAiModel = (data) => request.put('/admin/ai/model', data)
export const testAiModel = () => request.post('/admin/ai/model/test')

// ===== Prompt 模板（按场景分组） =====
export const getAiPrompts = () => request.get('/admin/ai/prompt')
export const saveAiPrompt = (data) => request.put('/admin/ai/prompt', data)
export const resetAiPrompt = (key) => request.post('/admin/ai/prompt/reset', null, { params: { key } })

// ===== Function Tool 注册表与调试 =====
export const getAiTools = (params) => request.get('/admin/ai/tools', { params })
export const createAiTool = (data) => request.post('/admin/ai/tools', data)
export const updateAiTool = (id, data) => request.put(`/admin/ai/tools/${id}`, data)
export const toggleAiTool = (id, enabled) => request.post(`/admin/ai/tools/${id}/toggle`, null, { params: { enabled } })
export const deleteAiTool = (id) => request.delete(`/admin/ai/tools/${id}`)
export const restoreAiTools = () => request.post('/admin/ai/tools/restore-builtin')
export const debugAiTool = (code, params) => request.post(`/admin/ai/tools/${code}/debug`, params)
export const getAiOpenaiTools = (params) => request.get('/admin/ai/tools/openai', { params })
