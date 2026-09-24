import request from './request'

// 购物车模块接口（接口文档：四）
export const getCartList = () => request.get('/cart/list')
export const addToCart = (skuId, quantity) => request.post('/cart', null, { params: { skuId, quantity } })
export const updateCartQuantity = (id, quantity) => request.put(`/cart/${id}/quantity`, null, { params: { quantity } })
export const updateCartSelected = (id, isSelected) => request.put(`/cart/${id}/selected`, null, { params: { isSelected } })
export const deleteCartItem = (id) => request.delete(`/cart/${id}`)
export const clearSelectedCart = () => request.post('/cart/clear-selected')
