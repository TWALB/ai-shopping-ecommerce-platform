import request from './request'

// 商家模块接口（接口文档：十）
export const submitMerchantApply = (data) => request.post('/merchant/apply', data)
export const getMerchantApplyStatus = () => request.get('/merchant/apply/status')
export const getMyShop = () => request.get('/merchant/shop')
export const updateMyShop = (data) => request.put('/merchant/shop', data)
export const getMerchantProductPage = (params) => request.get('/merchant/product/page', { params })
export const addMerchantProduct = (data) => request.post('/merchant/product', data)
export const updateMerchantProduct = (id, data) => request.put(`/merchant/product/${id}`, data)
export const updateProductStatus = (id, status) => request.put(`/merchant/product/${id}/status`, null, { params: { status } })
export const getMerchantOrderPage = (params) => request.get('/merchant/order/page', { params })
export const deliverOrder = (id, logisticsNo, companyName) => request.post(`/merchant/order/${id}/deliver`, null, { params: { logisticsNo, companyName } })
export const getMerchantStats = () => request.get('/merchant/stats')
