import request from './request'

// 订单模块接口（接口文档：五）
export const createOrder = (data) => request.post('/order', data)
export const getOrderPage = (params) => request.get('/order/page', { params })
export const getOrderDetail = (id) => request.get(`/order/${id}`)
export const cancelOrder = (id) => request.post(`/order/${id}/cancel`)
export const confirmOrder = (id) => request.post(`/order/${id}/confirm`)
export const getOrderTrack = (id) => request.get(`/order/${id}/track`)

// 支付模块接口（接口文档：六 模拟支付）
export const payOrder = (orderId) => request.post('/payment', null, { params: { orderId } })
export const queryPayment = (orderId) => request.get('/payment/query', { params: { orderId } })
