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
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login as loginApi, register as registerApi } from '../api/auth'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()
const activeTab = ref('login')
const loading = ref(false)
const loginForm = ref({ username: '', password: '' })
const regForm = ref({ username: '', password: '', nickname: '', phone: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  loading.value = true
  try {
    const data = await loginApi(loginForm.value)
    userStore.setToken(data.token)
    userStore.setUserInfo(data.user)
    ElMessage.success('登录成功')
    router.push('/home')
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
    router.push('/home')
  } catch (e) { /* 错误已统一提示 */ } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #1e3a8a, #3b82f6); }
.login-card { width: 400px; border-radius: 12px; }
.title { text-align: center; margin-bottom: 8px; color: #1e3a8a; }
.submit { width: 100%; }
</style>
