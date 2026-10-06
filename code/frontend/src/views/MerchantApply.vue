<template>
  <div class="apply-page">
    <el-card v-if="!status" class="apply-card">
      <template #header><b>商家入驻申请</b></template>
      <el-form :model="form" label-width="100px" style="max-width: 520px">
        <el-form-item label="真实姓名" required>
          <el-input v-model="form.realName" placeholder="请输入真实姓名" />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="form.idCard" placeholder="选填" />
        </el-form-item>
        <el-form-item label="联系电话" required>
          <el-input v-model="form.contactPhone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="店铺名称" required>
          <el-input v-model="form.shopName" placeholder="拟开设店铺名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="店铺简介">
          <el-input v-model="form.shopIntro" type="textarea" :rows="3" placeholder="店铺简介（选填）" maxlength="500" />
        </el-form-item>
        <el-form-item label="资质材料URL">
          <el-input v-model="form.qualificationUrl" placeholder="资质材料文件URL（选填）" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit">提交申请</el-button>
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" show-icon
                title="提交后由平台管理员审核，通过后自动开通店铺并开通商家权限" />
    </el-card>

    <el-card v-else class="apply-card">
      <template #header><b>入驻申请状态</b></template>
      <el-result :icon="resultIcon" :title="resultTitle" :sub-title="resultSub">
        <template #extra>
          <el-button v-if="status.status === 1" type="primary" @click="$router.push('/merchant')">进入商家中心</el-button>
          <el-button v-if="status.status === 2" @click="reapply">重新申请</el-button>
          <el-button v-if="status.status === 2" @click="$router.push('/home')">返回首页</el-button>
        </template>
      </el-result>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { submitMerchantApply, getMerchantApplyStatus } from '../api/merchant'

const form = ref({ realName: '', idCard: '', contactPhone: '', shopName: '', shopIntro: '', qualificationUrl: '' })
const submitting = ref(false)
const status = ref(null)

const resultIcon = computed(() => (status.value?.status === 1 ? 'success' : status.value?.status === 2 ? 'warning' : 'info'))
const resultTitle = computed(() => {
  if (!status.value) return ''
  if (status.value.status === 1) return '入驻申请已通过'
  if (status.value.status === 2) return '入驻申请被拒绝'
  return '入驻申请审核中'
})
const resultSub = computed(() => {
  if (!status.value) return ''
  if (status.value.status === 1) return '已为你开通店铺与商家权限，可以开始发布商品了'
  if (status.value.status === 2) return status.value.rejectReason || '请修改资料后重新申请'
  return `申请时间：${status.value.createdTime?.replace('T', ' ')}，请耐心等待平台审核`
})

async function submit() {
  if (!form.value.realName || !form.value.contactPhone || !form.value.shopName) {
    ElMessage.warning('请填写完整必填信息')
    return
  }
  submitting.value = true
  try {
    await submitMerchantApply(form.value)
    ElMessage.success('申请已提交，等待平台审核')
    await load()
  } catch (e) {
    /* 错误已统一提示 */
  } finally {
    submitting.value = false
  }
}

function reapply() {
  status.value = null
}

async function load() {
  try {
    const data = await getMerchantApplyStatus()
    if (data) status.value = data
  } catch (e) { /* 忽略 */ }
}

onMounted(load)
</script>

<style scoped>
.apply-page { max-width: 720px; margin: 0 auto; }
.apply-card { margin-top: 20px; }
</style>
