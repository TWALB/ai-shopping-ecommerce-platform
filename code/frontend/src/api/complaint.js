import request from './request'

// 咨询/投诉模块（客户管理）
export const createComplaint = (data) => request.post('/complaint', data)
export const getMyComplaintPage = (params) => request.get('/complaint/page', { params })
export const getMerchantComplaintPage = (params) => request.get('/complaint/merchant/page', { params })
export const replyComplaint = (id, content) =>
  request.put(`/complaint/${id}/reply`, null, { params: { content } })
