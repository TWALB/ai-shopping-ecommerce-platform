import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'

/**
 * 三端分离路由：
 * /login 统一登录页
 * /           商城（仅普通用户 role=0，含 /merchant/apply 商家入驻入口）
 * /merchant   商家工作台（仅商家 role=1）
 * /admin      管理后台（仅管理员 role=2）
 * 登录后按角色跳转各自端首页；越权访问提示无权限并回跳自己端
 */
const routes = [
  {
    path: '/login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录' }
  },
  // ===== 用户端：商城 =====
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    meta: { role: 0, title: '商城' },
    redirect: '/home',
    children: [
      { path: 'home', component: () => import('../views/Home.vue'), meta: { title: '首页' } },
      { path: 'products', component: () => import('../views/ProductList.vue'), meta: { title: '商品' } },
      { path: 'products/:id', component: () => import('../views/ProductDetail.vue'), meta: { title: '商品详情' } },
      { path: 'chat', component: () => import('../views/ChatView.vue'), meta: { title: '智能导购' } },
      { path: 'cart', component: () => import('../views/Cart.vue'), meta: { title: '购物车' } },
      { path: 'checkout', component: () => import('../views/Checkout.vue'), meta: { title: '结算' } },
      { path: 'orders', component: () => import('../views/OrderList.vue'), meta: { title: '我的订单' } },
      { path: 'orders/:id', component: () => import('../views/OrderDetail.vue'), meta: { title: '订单详情' } },
      { path: 'after-sales', component: () => import('../views/AfterSaleList.vue'), meta: { title: '我的售后' } },
      { path: 'merchant/apply', component: () => import('../views/MerchantApply.vue'), meta: { title: '商家入驻' } }
    ]
  },
  // ===== 商家端：商家工作台 =====
  {
    path: '/merchant',
    component: () => import('../layouts/MerchantLayout.vue'),
    meta: { role: 1, title: '商家中心' },
    children: [
      { path: '', component: () => import('../views/MerchantCenter.vue'), meta: { title: '商家中心' } }
    ]
  },
  // ===== 管理端：管理后台 =====
  {
    path: '/admin',
    component: () => import('../layouts/AdminLayout.vue'),
    meta: { role: 2, title: '管理后台' },
    children: [
      { path: '', component: () => import('../views/AdminDashboard.vue'), meta: { title: '管理后台' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/** 当前登录角色（本地 userInfo，缺失按普通用户处理） */
function currentRole() {
  try {
    return JSON.parse(localStorage.getItem('userInfo') || 'null')?.role ?? 0
  } catch (e) {
    return 0
  }
}

/** 各角色端首页 */
function homeOf(role) {
  if (role === 1) return '/merchant'
  if (role === 2) return '/admin'
  return '/home'
}

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  const role = currentRole()

  // 未登录 → 登录页
  if (to.path !== '/login' && !token) {
    return '/login'
  }
  // 已登录访问登录页 → 按角色回各自端首页
  if (to.path === '/login' && token) {
    return homeOf(role)
  }

  // 角色越权 → 提示无权限并回跳自己端
  const required = to.matched.find((r) => r.meta.role !== undefined)?.meta.role
  if (required !== undefined && role !== required) {
    ElMessage.warning('无权访问该页面')
    return homeOf(role)
  }
})

export default router
