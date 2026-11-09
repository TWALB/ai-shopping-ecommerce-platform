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
        <el-menu :default-active="activeMenu" :default-openeds="['ops', 'kb', 'ai']" router class="menu">
          <el-sub-menu index="ops">
            <template #title>
              <el-icon><DataAnalysis /></el-icon>
              <span>平台运营</span>
            </template>
            <el-menu-item index="/admin?tab=stats">
              <el-icon><Odometer /></el-icon><span>数据总览</span>
            </el-menu-item>
            <el-menu-item index="/admin?tab=users">
              <el-icon><User /></el-icon><span>用户管理</span>
            </el-menu-item>
            <el-menu-item index="/admin?tab=applies">
              <el-icon><Shop /></el-icon><span>入驻审核</span>
            </el-menu-item>
            <el-menu-item index="/admin?tab=products">
              <el-icon><Goods /></el-icon><span>商品管理</span>
            </el-menu-item>
            <el-menu-item index="/admin?tab=orders">
              <el-icon><Tickets /></el-icon><span>订单管理</span>
            </el-menu-item>
            <el-menu-item index="/admin?tab=aftersales">
              <el-icon><Service /></el-icon><span>售后管理</span>
            </el-menu-item>
            <el-menu-item index="/admin?tab=notices">
              <el-icon><Bell /></el-icon><span>公告管理</span>
            </el-menu-item>
            <el-menu-item index="/admin?tab=comments">
              <el-icon><ChatLineSquare /></el-icon><span>评价审核</span>
            </el-menu-item>
            <el-menu-item index="/admin?tab=logs">
              <el-icon><Document /></el-icon><span>操作日志</span>
            </el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="kb">
            <template #title>
              <el-icon><Reading /></el-icon>
              <span>AI 知识库（RAG）</span>
            </template>
            <el-menu-item index="/admin/ai/knowledge/products">
              <el-icon><Collection /></el-icon><span>商品知识库</span>
            </el-menu-item>
            <el-menu-item index="/admin/ai/knowledge/chunks">
              <el-icon><Files /></el-icon><span>商品知识切片</span>
            </el-menu-item>
            <el-menu-item index="/admin/ai/knowledge/search">
              <el-icon><Search /></el-icon><span>Embedding 检索</span>
            </el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="ai">
            <template #title>
              <el-icon><MagicStick /></el-icon>
              <span>AI 智能导购与运营</span>
            </template>
            <el-menu-item index="/admin/ai/guide/tasks">
              <el-icon><ChatDotRound /></el-icon><span>AI 智能导购任务</span>
            </el-menu-item>
            <el-menu-item index="/admin/ai/guide/runs">
              <el-icon><VideoPlay /></el-icon><span>Agent Run 运行记录</span>
            </el-menu-item>
            <el-menu-item index="/admin/ai/guide/steps">
              <el-icon><SetUp /></el-icon><span>Agent Step 执行步骤</span>
            </el-menu-item>
            <el-menu-item index="/admin/ai/guide/recommend">
              <el-icon><Star /></el-icon><span>AI 推荐商品</span>
            </el-menu-item>
            <el-menu-item index="/admin/ai/qa">
              <el-icon><ChatLineSquare /></el-icon><span>AI 商品问答</span>
            </el-menu-item>
            <el-menu-item index="/admin/ai/comment">
              <el-icon><DataLine /></el-icon><span>AI 评价分析</span>
            </el-menu-item>
            <el-menu-item index="/admin/ai/report">
              <el-icon><TrendCharts /></el-icon><span>AI 运营增长报告</span>
            </el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="cfg">
            <template #title>
              <el-icon><Setting /></el-icon>
              <span>AI 配置中心</span>
            </template>
            <el-menu-item index="/admin/ai/config">
              <el-icon><EditPen /></el-icon><span>提示词与参数</span>
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="crumb">
          <span class="root">管理后台</span>
          <el-icon class="sep"><ArrowRight /></el-icon>
          <span class="cur">{{ pageTitle }}</span>
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
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getMe, logout as logoutApi } from '../api/auth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const TAB_TITLES = {
  stats: '数据总览',
  users: '用户管理',
  applies: '入驻审核',
  products: '商品管理',
  orders: '订单管理',
  aftersales: '售后管理',
  notices: '公告管理',
  comments: '评价审核',
  logs: '操作日志'
}

const activeMenu = computed(() => {
  if (route.path.startsWith('/admin/ai')) {
    return route.path
  }
  return `/admin?tab=${route.query.tab || 'stats'}`
})

const pageTitle = computed(() => {
  if (route.path.startsWith('/admin/ai')) {
    return route.meta.title || 'AI 模块'
  }
  return TAB_TITLES[route.query.tab || 'stats'] || '数据总览'
})

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
