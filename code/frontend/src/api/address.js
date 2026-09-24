import request from './request'

// 收货地址接口（接口文档：二 用户模块）
export const getAddressList = () => request.get('/user/address/list')
export const addAddress = (data) => request.post('/user/address', data)
export const updateAddress = (id, data) => request.put(`/user/address/${id}`, data)
export const deleteAddress = (id) => request.delete(`/user/address/${id}`)
export const setDefaultAddress = (id) => request.put(`/user/address/${id}/default`)
