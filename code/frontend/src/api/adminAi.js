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
