import request from './request'

// 评价模块接口（接口文档：七）
export const submitComment = (data) => request.post('/comment', data)
export const getProductComments = (productId, params) => request.get(`/comment/product/${productId}`, { params })
export const getCommentPage = (params) => request.get('/comment/page', { params })
export const replyComment = (id, replyContent) => request.put(`/comment/${id}/reply`, null, { params: { replyContent } })
export const auditComment = (id, status) => request.put(`/comment/${id}/status`, null, { params: { status } })
