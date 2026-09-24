import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
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
      { path: 'admin', component: () => import('../views/AdminDashboard.vue'), meta: { title: '管理后台' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 登录守卫：未登录跳转登录页
router.beforeEach((to) => {
  if (to.path !== '/login' && !localStorage.getItem('token')) {
    return '/login'
  }
})

export default router
