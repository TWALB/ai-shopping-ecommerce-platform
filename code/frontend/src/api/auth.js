import request from './request'

// 认证模块接口（接口文档：一）
export const register = (data) => request.post('/auth/register', data)
export const login = (data) => request.post('/auth/login', data)
export const logout = () => request.post('/auth/logout')
export const getMe = () => request.get('/auth/me')
export const changePassword = (oldPassword, newPassword) =>
  request.put('/auth/password', null, { params: { oldPassword, newPassword } })
export const sendSmsCode = (phone, type) =>
  request.post('/auth/sms-code', null, { params: { phone, type } })
export const forgotPassword = (phone, code, newPassword) =>
  request.post('/auth/forgot', null, { params: { phone, code, newPassword } })
