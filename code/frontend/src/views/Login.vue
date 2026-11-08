<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2 class="title">数码电商平台</h2>
      <el-tabs v-model="activeTab" stretch>
        <el-tab-pane label="登录" name="login">
          <el-form ref="loginFormRef" :model="loginForm" :rules="rules" label-width="0">
            <el-form-item prop="username"><el-input v-model="loginForm.username" placeholder="用户名" size="large" /></el-form-item>
            <el-form-item prop="password"><el-input v-model="loginForm.password" type="password" placeholder="密码" size="large" show-password /></el-form-item>
            <el-button type="primary" size="large" class="submit" :loading="loading" @click="handleLogin">登 录</el-button>
            <div class="forgot-link">
              <el-link type="primary" :underline="false" @click="openForgot">忘记密码？</el-link>
            </div>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="注册" name="register">
          <el-form ref="regFormRef" :model="regForm" :rules="rules" label-width="0">
            <el-form-item prop="username"><el-input v-model="regForm.username" placeholder="用户名（3-20位）" size="large" /></el-form-item>
            <el-form-item prop="password"><el-input v-model="regForm.password" type="password" placeholder="密码（6-20位）" size="large" show-password /></el-form-item>
            <el-form-item><el-input v-model="regForm.nickname" placeholder="昵称（选填）" size="large" /></el-form-item>
            <el-form-item><el-input v-model="regForm.phone" placeholder="手机号（选填）" size="large" /></el-form-item>
            <el-button type="primary" size="large" class="submit" :loading="loading" @click="handleRegister">注 册</el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 找回密码：手机号 + 验证码 -->
    <el-dialog v-model="forgotDialog" title="找回密码" width="430px">
      <el-form label-width="90px">
        <el-form-item label="手机号" required>
          <el-input v-model="forgotForm.phone" maxlength="11" placeholder="账号绑定的手机号" />
        </el-form-item>
        <el-form-item label="验证码" required>
          <div class="code-row">
            <el-input v-model="forgotForm.code" maxlength="6" placeholder="6 位验证码" />
            <el-button :disabled="countdown > 0" @click="sendCode">
              {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="forgotForm.newPassword" type="password" show-password placeholder="6-20 位" />
        </el-form-item>
      </el-form>
      <div class="tip">开发环境未接入短信网关，验证码会打印在后端日志（搜索「模拟短信」）。</div>
      <template #footer>
        <el-button @click="forgotDialog = false">取消</el-button>
        <el-button type="primary" :loading="forgotLoading" @click="submitForgot">重置密码</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login as loginApi, register as registerApi, sendSmsCode, forgotPassword } from '../api/auth'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()
const activeTab = ref('login')
const loading = ref(false)
const loginForm = ref({ username: '', password: '' })
const regForm = ref({ username: '', password: '', nickname: '', phone: '' })
const forgotDialog = ref(false)
const forgotLoading = ref(false)
const countdown = ref(0)
const forgotForm = ref({ phone: '', code: '', newPassword: '' })
let timer = null
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

/** 各角色端首页 */
function homeOf(role) {
  if (role === 1) return '/merchant'
  if (role === 2) return '/admin'
  return '/home'
}

async function handleLogin() {
  loading.value = true
  try {
    const data = await loginApi(loginForm.value)
    userStore.setToken(data.token)
    userStore.setUserInfo(data.user)
    ElMessage.success('登录成功')
    router.push(homeOf(data.user.role))
  } catch (e) { /* 错误已统一提示 */ } finally {
    loading.value = false
  }
}

async function handleRegister() {
  loading.value = true
  try {
    const data = await registerApi(regForm.value)
    userStore.setToken(data.token)
    userStore.setUserInfo(data.user)
    ElMessage.success('注册成功')
    router.push(homeOf(data.user.role))
  } catch (e) { /* 错误已统一提示 */ } finally {
    loading.value = false
  }
}

// ===== 找回密码 =====
function openForgot() {
  forgotForm.value = { phone: '', code: '', newPassword: '' }
  countdown.value = 0
  forgotDialog.value = true
}

async function sendCode() {
  const phone = forgotForm.value.phone.trim()
  if (!/^1\d{10}$/.test(phone)) {
    ElMessage.warning('请输入正确的 11 位手机号')
    return
  }
  try {
    await sendSmsCode(phone, 'forgot')
    ElMessage.success('验证码已发送')
    countdown.value = 60
    timer = setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) {
        clearInterval(timer)
        timer = null
      }
    }, 1000)
  } catch (e) { /* 错误已统一提示 */ }
}

async function submitForgot() {
  const f = forgotForm.value
  if (!/^1\d{10}$/.test(f.phone.trim())) { ElMessage.warning('请输入正确的手机号'); return }
  if (!f.code || f.code.trim().length !== 6) { ElMessage.warning('请输入 6 位验证码'); return }
  if (!f.newPassword || f.newPassword.length < 6 || f.newPassword.length > 20) {
    ElMessage.warning('新密码长度需为 6-20 位'); return
  }
  forgotLoading.value = true
  try {
    await forgotPassword(f.phone.trim(), f.code.trim(), f.newPassword)
    ElMessage.success('密码已重置，请用新密码登录')
    forgotDialog.value = false
    loginForm.value.password = f.newPassword
    activeTab.value = 'login'
  } catch (e) { /* 错误已统一提示 */ } finally {
    forgotLoading.value = false
  }
}

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.login-page { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #1e3a8a, #3b82f6); }
.login-card { width: 400px; border-radius: 12px; }
.title { text-align: center; margin-bottom: 8px; color: #1e3a8a; }
.submit { width: 100%; }
.forgot-link { text-align: right; margin-top: 8px; }
.code-row { display: flex; gap: 8px; width: 100%; }
.code-row .el-button { flex: 0 0 auto; }
.tip { font-size: 12px; color: #909399; line-height: 1.6; }
</style>
