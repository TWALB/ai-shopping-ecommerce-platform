<template>
  <el-container class="merchant-layout">
    <el-aside width="210px" class="aside">
      <div class="logo">🏪 商家工作台</div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item index="/merchant?tab=shop">店铺信息</el-menu-item>
        <el-menu-item index="/merchant?tab=products">商品管理</el-menu-item>
        <el-menu-item index="/merchant?tab=orders">订单发货</el-menu-item>
        <el-menu-item index="/merchant?tab=aftersales">售后处理</el-menu-item>
        <el-menu-item index="/merchant?tab=stats">经营统计</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="title">商家中心</span>
        <div class="user-area">
          <span class="user-name">{{ userStore.userInfo?.nickname || userStore.userInfo?.username }}</span>
          <el-button size="small" type="danger" plain @click="logout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getMe } from '../api/auth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => {
  const tab = route.query.tab || 'shop'
  return `/merchant?tab=${tab}`
})

onMounted(async () => {
  try {
    userStore.setUserInfo(await getMe())
  } catch (e) { /* 未登录已由守卫处理 */ }
})

function logout() {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.merchant-layout { min-height: 100vh; }
.aside { background: #1e293b; display: flex; flex-direction: column; }
.logo { color: #fff; font-weight: 700; font-size: 16px; padding: 18px 16px; white-space: nowrap; }
.menu { flex: 1; border-right: none; background: transparent; }
.menu :deep(.el-menu-item) { color: #cbd5e1; }
.menu :deep(.el-menu-item.is-active) { color: #fff; background: #334155; }
.menu :deep(.el-menu-item:hover) { background: #334155; color: #fff; }
.header { display: flex; align-items: center; justify-content: space-between; background: #fff; border-bottom: 1px solid #e2e8f0; }
.title { font-size: 18px; font-weight: 700; color: #0f172a; }
.user-area { display: flex; align-items: center; gap: 12px; }
.user-name { color: #475569; }
.main { background: #f1f5f9; }
</style>
