<template>
  <div v-if="detail" class="detail-page">
    <el-card class="head-card">
      <div class="head-row">
        <b class="head-status">{{ statusText(detail.status) }}</b>
        <span class="head-no">订单号：{{ detail.orderNo }}</span>
        <span class="head-time">下单时间：{{ detail.createdTime?.replace('T', ' ') }}</span>
      </div>
      <div class="head-ops">
        <el-button v-if="detail.status === 0" type="primary" size="large" @click="openPay">立即支付 ¥{{ detail.payAmount }}</el-button>
        <el-button v-if="detail.status === 0" size="large" @click="cancel">取消订单</el-button>
        <el-button v-if="detail.status === 2" type="success" size="large" @click="confirm">确认收货</el-button>
      </div>
    </el-card>

    <el-card class="block">
      <template #header><b>收货信息</b></template>
      <div class="recv">{{ detail.receiver.name }}　{{ detail.receiver.phone }}</div>
      <div class="recv-addr">{{ detail.receiver.address }}</div>
    </el-card>

    <el-card class="block">
      <template #header><b>商品清单（{{ detail.shopName }}）</b></template>
      <div class="item" v-for="it in detail.items" :key="it.orderItemId">
        <el-image :src="it.productImage || placeholder" fit="cover" class="i-img" @click="$router.push(`/products/${it.productId}`)" />
        <div class="i-info">
          <div class="i-name">{{ it.productName }}</div>
          <div class="i-sku">{{ it.skuName }}</div>
        </div>
        <span class="i-price">¥{{ it.price }}</span>
        <span class="i-qty">×{{ it.quantity }}</span>
        <span class="i-sub">¥{{ it.totalAmount }}</span>
      </div>
      <div class="amount-box">
        <div>商品金额：¥{{ detail.totalAmount }}</div>
        <div>运费：¥{{ detail.freightAmount }}</div>
        <div class="pay-amount">实付金额：<b>¥{{ detail.payAmount }}</b></div>
        <div v-if="detail.remark" class="remark">备注：{{ detail.remark }}</div>
      </div>
    </el-card>

    <el-card v-if="detail.payment" class="block">
      <template #header><b>支付信息</b></template>
      <div>支付流水号：{{ detail.payment.paymentNo }}</div>
      <div>支付时间：{{ detail.payment.payTime?.replace('T', ' ') }}</div>
    </el-card>

    <el-card v-if="tracks.length" class="block">
      <template #header><b>物流跟踪（{{ detail.logistics?.companyName }} {{ detail.logistics?.logisticsNo }}）</b></template>
      <el-timeline>
        <el-timeline-item v-for="(t, i) in tracks" :key="i" :timestamp="t.trackTime?.replace('T', ' ')">
          {{ t.trackInfo }}
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <!-- 模拟支付弹窗 -->
    <el-dialog v-model="payDialog" title="模拟支付" width="420px">
      <div class="pay-box">
        <div class="pay-amount-big">¥{{ detail.payAmount }}</div>
        <div class="pay-tip">本毕设采用模拟支付，点击确认即支付成功</div>
      </div>
      <template #footer>
        <el-button @click="payDialog = false">取消</el-button>
        <el-button type="primary" :loading="paying" @click="doPay">确认支付</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrderDetail, cancelOrder, confirmOrder, getOrderTrack, payOrder } from '../api/order'

const route = useRoute()
const router = useRouter()
const detail = ref(null)
const tracks = ref([])
const payDialog = ref(false)
const paying = ref(false)
const placeholder = 'https://via.placeholder.com/80'

const statusMap = { 0: '待付款', 1: '待发货', 2: '待收货', 3: '已完成', 4: '已取消', 5: '售后中' }
const statusText = (s) => statusMap[s] || '未知'

async function load() {
  detail.value = await getOrderDetail(route.params.id)
  if (detail.value.logistics) {
    tracks.value = await getOrderTrack(route.params.id)
  } else {
    tracks.value = []
  }
}

function openPay() {
  payDialog.value = true
}

async function doPay() {
  paying.value = true
  try {
    await payOrder(detail.value.id)
    ElMessage.success('支付成功！')
    payDialog.value = false
    load()
  } catch (e) {
    /* 错误已统一提示 */
  } finally {
    paying.value = false
  }
}

async function cancel() {
  await ElMessageBox.confirm('确定取消该订单吗？库存将回补', '提示', { type: 'warning' })
  await cancelOrder(detail.value.id)
  ElMessage.success('订单已取消')
  load()
}

async function confirm() {
  await ElMessageBox.confirm('请确认已收到商品', '确认收货', { type: 'warning' })
  await confirmOrder(detail.value.id)
  ElMessage.success('已确认收货')
  load()
}

onMounted(() => {
  load()
  if (route.query.pay === '1') openPay()
})
</script>

<style scoped>
.detail-page { max-width: 900px; margin: 0 auto; }
.head-card { text-align: center; }
.head-row { display: flex; align-items: center; justify-content: center; gap: 20px; margin-bottom: 16px; }
.head-status { color: #dc2626; font-size: 20px; }
.head-no, .head-time { color: #94a3b8; font-size: 13px; }
.block { margin-top: 16px; }
.recv { font-weight: 600; }
.recv-addr { color: #64748b; margin-top: 4px; }
.item { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid #f8fafc; }
.i-img { width: 64px; height: 64px; border-radius: 8px; cursor: pointer; }
.i-info { flex: 1; }
.i-name { font-weight: 600; }
.i-sku { color: #94a3b8; font-size: 12px; margin-top: 4px; }
.i-price { color: #64748b; width: 80px; text-align: right; }
.i-qty { width: 50px; text-align: center; color: #64748b; }
.i-sub { width: 100px; text-align: right; font-weight: 600; }
.amount-box { text-align: right; padding-top: 12px; color: #64748b; line-height: 1.8; }
.pay-amount { font-size: 15px; color: #334155; }
.pay-amount b { color: #dc2626; font-size: 20px; }
.remark { color: #94a3b8; }
.pay-box { text-align: center; padding: 20px 0; }
.pay-amount-big { color: #dc2626; font-size: 32px; font-weight: 800; }
.pay-tip { color: #94a3b8; margin-top: 8px; font-size: 13px; }
</style>
