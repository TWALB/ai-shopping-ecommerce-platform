<template>
  <el-container class="layout">
    <el-header class="header">
      <div class="logo">🛒 数码电商平台</div>
      <el-menu mode="horizontal" :default-active="$route.path" router class="menu" :ellipsis="false">
        <el-menu-item index="/home">首页</el-menu-item>
        <el-menu-item index="/products">商品</el-menu-item>
        <el-menu-item index="/chat">智能导购</el-menu-item>
        <el-menu-item index="/admin" v-if="userStore.userInfo?.role === 2">管理后台</el-menu-item>
      </el-menu>
      <div class="user-area">
        <el-dropdown @command="handleCommand">
          <span class="user-name">
            {{ userStore.userInfo?.nickname || userStore.userInfo?.username || '未登录' }}
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>
    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getMe } from '../api/auth'

const router = useRouter()
const userStore = useUserStore()

onMounted(async () => {
  if (!userStore.userInfo) {
    try {
      userStore.setUserInfo(await getMe())
    } catch (e) { /* 未登录已由守卫处理 */ }
  }
})

function handleCommand(command) {
  if (command === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout { min-height: 100vh; }
.header { display: flex; align-items: center; background: #fff; border-bottom: 1px solid #e5e7eb; }
.logo { font-size: 18px; font-weight: 700; color: #1e40af; margin-right: 24px; white-space: nowrap; }
.menu { flex: 1; border-bottom: none; }
.user-area { margin-left: auto; }
.user-name { cursor: pointer; color: #374151; display: flex; align-items: center; gap: 4px; }
.main { background: #f5f7fa; }
</style>
