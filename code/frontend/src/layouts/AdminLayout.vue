<template>
  <el-container class="admin-layout">
    <el-aside width="210px" class="aside">
      <div class="logo">🛡️ 管理后台</div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item index="/admin?tab=stats">数据总览</el-menu-item>
        <el-menu-item index="/admin?tab=users">用户管理</el-menu-item>
        <el-menu-item index="/admin?tab=applies">入驻审核</el-menu-item>
        <el-menu-item index="/admin?tab=products">商品管理</el-menu-item>
        <el-menu-item index="/admin?tab=orders">订单管理</el-menu-item>
        <el-menu-item index="/admin?tab=aftersales">售后管理</el-menu-item>
        <el-menu-item index="/admin?tab=notices">公告管理</el-menu-item>
        <el-menu-item index="/admin?tab=comments">评价审核</el-menu-item>
        <el-menu-item index="/admin?tab=logs">操作日志</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="title">管理后台</span>
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
  const tab = route.query.tab || 'stats'
  return `/admin?tab=${tab}`
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
.admin-layout { min-height: 100vh; }
.aside { background: #0f172a; display: flex; flex-direction: column; }
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
