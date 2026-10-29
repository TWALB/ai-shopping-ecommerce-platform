import request from './request'

// 售后模块接口（接口文档：八）
export const applyAfterSale = (data) => request.post('/after-sale', data)
export const getMyAfterSalePage = (params) => request.get('/after-sale/page', { params })
export const getAfterSaleDetail = (id) => request.get(`/after-sale/${id}`)
export const cancelAfterSale = (id) => request.post(`/after-sale/${id}/cancel`)
export const getMerchantAfterSalePage = (params) => request.get('/merchant/after-sale/page', { params })
export const handleAfterSale = (id, params) => request.put(`/after-sale/${id}/handle`, null, { params })
export const adminHandleAfterSale = (id, params) => request.put(`/after-sale/${id}/admin`, null, { params })
