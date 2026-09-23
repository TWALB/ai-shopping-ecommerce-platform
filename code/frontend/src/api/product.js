import request from './request'

// 商品模块接口（接口文档：三）
export const getProductPage = (params) => request.get('/product/page', { params })
export const getProductDetail = (id) => request.get(`/product/${id}`)
export const getCategoryTree = () => request.get('/product/category/tree')
export const getBrandList = () => request.get('/product/brand/list')
export const getHotProducts = () => request.get('/product/hot')
