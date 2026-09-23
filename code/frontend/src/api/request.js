import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

/**
 * axios 实例：统一 /api 前缀、JWT 注入、响应解包、错误提示
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 60000
})

// 请求拦截：自动携带 Token
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：解包 Result{code,message,data}，非 200 统一提示
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      if (res.code === 401) {
        localStorage.removeItem('token')
        router.push('/login')
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res.data
  },
  (error) => {
    ElMessage.error(error.response?.data?.message || '网络错误')
    return Promise.reject(error)
  }
)

export default request
