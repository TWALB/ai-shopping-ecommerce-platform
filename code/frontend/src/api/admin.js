import request from './request'

// 管理员模块接口（接口文档：十一）
export const getAdminUserPage = (params) => request.get('/admin/user/page', { params })
export const setUserStatus = (id, status) => request.put(`/admin/user/${id}/status`, null, { params: { status } })
export const setUserRole = (id, role) => request.put(`/admin/user/${id}/role`, null, { params: { role } })
export const getAdminApplyPage = (params) => request.get('/admin/merchant-apply/page', { params })
export const auditApply = (id, agree, rejectReason) => request.put(`/admin/merchant-apply/${id}/audit`, null, { params: { agree, rejectReason } })
export const getAdminProductPage = (params) => request.get('/admin/product/page', { params })
export const setAdminProductStatus = (id, status) => request.put(`/admin/product/${id}/status`, null, { params: { status } })
export const getAdminOrderPage = (params) => request.get('/admin/order/page', { params })
export const getAdminAfterSalePage = (params) => request.get('/admin/after-sale/page', { params })
export const addNotice = (data) => request.post('/admin/notice', data)
export const getNoticePage = (params) => request.get('/admin/notice/page', { params })
export const deleteNotice = (id) => request.delete(`/admin/notice/${id}`)
export const getStatsOverview = () => request.get('/admin/stats/overview')
export const getStatsTrend = (days) => request.get('/admin/stats/trend', { params: { days } })
export const getOperationLogPage = (params) => request.get('/admin/operation-log/page', { params })
