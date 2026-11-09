<template>
  <el-container class="admin-layout">
    <el-aside width="228px" class="aside">
      <div class="logo">
        <div class="logo-mark">DM</div>
        <div class="logo-text">
          <b>数智商城</b>
          <span>管理后台</span>
        </div>
      </div>

      <el-scrollbar class="menu-scroll">
        <el-menu :default-active="activeMenu" :default-openeds="openeds" router class="menu"
                 @open="onOpen" @close="onClose">
          <el-sub-menu v-for="group in adminMenu" :key="group.key" :index="group.key">
            <template #title>
              <el-icon><component :is="group.icon" /></el-icon>
              <span>{{ group.title }}</span>
            </template>
            <el-menu-item v-for="item in group.children" :key="item.path" :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="crumb">
          <template v-for="(seg, i) in chain" :key="i">
            <el-icon v-if="i > 0" class="sep"><ArrowRight /></el-icon>
            <span :class="i === chain.length - 1 ? 'cur' : 'root'">{{ seg }}</span>
          </template>
        </div>
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
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getMe, logout as logoutApi } from '../api/auth'
import { adminMenu, DEFAULT_OPENED, resolveMenuPath, resolveChain } from './menuConfig'

const OPEN_KEY = 'admin_menu_opened'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 展开态持久化：刷新后保持上次的样子，侧栏不会每次全展开 */
const openeds = ref(readOpened())

function readOpened() {
  try {
    const saved = JSON.parse(localStorage.getItem(OPEN_KEY) || 'null')
    return Array.isArray(saved) ? saved : [...DEFAULT_OPENED]
  } catch (e) {
    return [...DEFAULT_OPENED]
  }
}

function persistOpened(list) {
  openeds.value = list
  localStorage.setItem(OPEN_KEY, JSON.stringify(list))
}

function onOpen(index) {
  if (!openeds.value.includes(index)) persistOpened([...openeds.value, index])
}

function onClose(index) {
  persistOpened(openeds.value.filter((k) => k !== index))
}

const activeMenu = computed(() => resolveMenuPath(route.path, route.query))

const chain = computed(() => resolveChain(activeMenu.value))

onMounted(async () => {
  try {
    userStore.setUserInfo(await getMe())
  } catch (e) { /* 未登录已由守卫处理 */ }
})

async function logout() {
  try { await logoutApi() } catch (e) { /* 后端不可用时也允许本地退出 */ }
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.admin-layout { min-height: 100vh; }

.aside {
  background: #fff;
  border-right: 1px solid #e8ecf2;
  display: flex;
  flex-direction: column;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 18px;
  border-bottom: 1px solid #f1f5f9;
}
.logo-mark {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: #2563eb;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  letter-spacing: 0.5px;
}
.logo-text { display: flex; flex-direction: column; line-height: 1.25; }
.logo-text b { font-size: 14px; color: #0f172a; font-weight: 600; }
.logo-text span { font-size: 11px; color: #94a3b8; }

.menu-scroll { flex: 1; }
.menu { border-right: none; padding: 6px 8px 20px; }

.menu :deep(.el-sub-menu__title) {
  height: 42px;
  line-height: 42px;
  font-size: 13px;
  color: #0f172a;
  font-weight: 500;
  border-radius: 8px;
}
.menu :deep(.el-sub-menu__title:hover) { background: #f8fafc; }
.menu :deep(.el-sub-menu .el-menu-item) {
  height: 40px;
  line-height: 40px;
  min-width: 0;
  font-size: 13px;
  color: #475569;
  border-radius: 8px;
  padding-left: 46px !important;
}
.menu :deep(.el-menu-item .el-icon) { font-size: 15px; margin-right: 8px; }
.menu :deep(.el-menu-item:hover) { background: #f8fafc; color: #0f172a; }
.menu :deep(.el-menu-item.is-active) {
  background: #eef4ff;
  color: #2563eb;
  font-weight: 500;
  box-shadow: inset 2px 0 0 #2563eb;
}
.menu :deep(.el-sub-menu.is-active > .el-sub-menu__title) { color: #2563eb; }

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e8ecf2;
  height: 56px;
}
.crumb { display: flex; align-items: center; gap: 6px; font-size: 13px; }
.crumb .root { color: #94a3b8; }
.crumb .sep { font-size: 11px; color: #cbd5e1; }
.crumb .cur { color: #0f172a; font-weight: 500; }

.user-area { display: flex; align-items: center; gap: 12px; }
.user-name { color: #475569; font-size: 13px; }
.main { background: #f6f8fb; padding: 16px; }
</style>
