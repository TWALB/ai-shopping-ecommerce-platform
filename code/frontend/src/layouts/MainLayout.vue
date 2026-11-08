<template>
  <el-container class="layout">
    <el-header class="header">
      <div class="logo">🛒 数码电商平台</div>
      <el-menu mode="horizontal" :default-active="$route.path" router class="menu" :ellipsis="false">
        <el-menu-item index="/home">首页</el-menu-item>
        <el-menu-item index="/products">商品</el-menu-item>
        <el-menu-item index="/chat">智能导购</el-menu-item>
        <el-menu-item index="/cart">购物车</el-menu-item>
        <el-menu-item index="/orders">我的订单</el-menu-item>
        <el-menu-item index="/after-sales">我的售后</el-menu-item>
        <el-menu-item index="/merchant/apply">商家入驻</el-menu-item>
      </el-menu>
      <div class="user-area">
        <el-dropdown @command="handleCommand">
          <span class="user-name">
            {{ userStore.userInfo?.nickname || userStore.userInfo?.username || '未登录' }}
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>
    <el-main class="main">
      <router-view />
    </el-main>

    <!-- 修改密码 -->
    <el-dialog v-model="pwdDialog" title="修改密码" width="420px">
      <el-form label-width="96px">
        <el-form-item label="原密码" required>
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="当前登录密码" />
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="6-20 位" />
        </el-form-item>
        <el-form-item label="确认新密码" required>
          <el-input v-model="pwdForm.confirm" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
      </el-form>
      <div class="pwd-tip">修改成功后当前登录会失效，需要重新登录。</div>
      <template #footer>
        <el-button @click="pwdDialog = false">取消</el-button>
        <el-button type="primary" :loading="pwdLoading" @click="submitPassword">确认修改</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'
import { getMe, logout as logoutApi, changePassword } from '../api/auth'

const router = useRouter()
const userStore = useUserStore()

const pwdDialog = ref(false)
const pwdLoading = ref(false)
const pwdForm = ref({ oldPassword: '', newPassword: '', confirm: '' })

onMounted(async () => {
  try {
    // 每次进入刷新用户信息（角色/状态以服务端为准，审核通过、角色调整后即时生效）
    userStore.setUserInfo(await getMe())
  } catch (e) { /* 未登录已由守卫处理 */ }
})

async function handleCommand(command) {
  if (command === 'logout') {
    try { await logoutApi() } catch (e) { /* 后端不可用时也允许本地退出 */ }
    userStore.logout()
    router.push('/login')
  } else if (command === 'password') {
    pwdForm.value = { oldPassword: '', newPassword: '', confirm: '' }
    pwdDialog.value = true
  }
}

async function submitPassword() {
  const f = pwdForm.value
  if (!f.oldPassword) { ElMessage.warning('请输入原密码'); return }
  if (!f.newPassword || f.newPassword.length < 6 || f.newPassword.length > 20) {
    ElMessage.warning('新密码长度需为 6-20 位'); return
  }
  if (f.newPassword !== f.confirm) { ElMessage.warning('两次输入的新密码不一致'); return }
  pwdLoading.value = true
  try {
    await changePassword(f.oldPassword, f.newPassword)
    ElMessage.success('密码已修改，请重新登录')
    pwdDialog.value = false
    userStore.logout()
    router.push('/login')
  } catch (e) { /* 错误已统一提示 */ } finally {
    pwdLoading.value = false
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
.pwd-tip { font-size: 12px; color: #909399; padding-left: 12px; }
</style>
