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
        <el-button v-if="detail.status === 3" type="warning" size="large" @click="openComment">评价商品</el-button>
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
        <span class="i-ops">
          <el-tag v-if="runningAfterSale(it.orderItemId)" type="warning" size="small">售后处理中</el-tag>
          <el-button v-else-if="detail.status === 2 || detail.status === 3" size="small" plain
                     @click="openAfterSale(it)">申请售后</el-button>
        </span>
      </div>
      <div class="amount-box">
        <div>商品金额：¥{{ detail.totalAmount }}</div>
        <div>运费：¥{{ detail.freightAmount }}</div>
        <div class="pay-amount">实付金额：<b>¥{{ detail.payAmount }}</b></div>
        <div v-if="detail.remark" class="remark">备注：{{ detail.remark }}</div>
      </div>
    </el-card>

    <el-card v-if="afterSales.length" class="block">
      <template #header><b>售后记录</b></template>
      <div v-for="a in afterSales" :key="a.id" class="as-row">
        <span class="as-no">{{ a.afterSaleNo }}</span>
        <span class="as-name">{{ a.productName }}</span>
        <span>{{ asTypeText(a.type) }}</span>
        <span>¥{{ a.amount }}</span>
        <el-tag :type="asStatusTag(a.status)" size="small">{{ asStatusText(a.status) }}</el-tag>
        <span class="as-time">{{ a.createdTime?.replace('T', ' ') }}</span>
        <el-button v-if="a.status === 0" link type="danger" @click="cancelAfterSaleItem(a)">撤销</el-button>
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

    <!-- 售后申请弹窗 -->
    <el-dialog v-model="afterSaleDialog" title="申请售后" width="520px">
      <el-form label-width="90px">
        <el-form-item label="商品">{{ asForm.productName }}</el-form-item>
        <el-form-item label="售后类型">
          <el-radio-group v-model="asForm.type">
            <el-radio :value="0">仅退款</el-radio>
            <el-radio :value="1">退货退款</el-radio>
            <el-radio :value="2">换货</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="asForm.type !== 2" label="退款金额">
          <el-input-number v-model="asForm.amount" :min="0.01" :max="asForm.maxAmount" :precision="2" />
          <span class="as-tip">该商品实付 ¥{{ asForm.maxAmount }}</span>
        </el-form-item>
        <el-form-item label="申请原因">
          <el-input v-model="asForm.reason" type="textarea" :rows="3" maxlength="300" show-word-limit
                    placeholder="请描述问题，便于商家快速处理" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="afterSaleDialog = false">取消</el-button>
        <el-button type="primary" :loading="applying" @click="submitAfterSale">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 评价弹窗 -->
    <el-dialog v-model="commentDialog" title="评价商品" width="640px">
      <div class="comment-item" v-for="it in detail.items" :key="it.orderItemId">
        <div class="ci-head">
          <b>{{ it.productName }}</b>
          <span class="ci-sku">{{ it.skuName }}</span>
        </div>
        <div class="ci-form">
          <span>商品评分</span>
          <el-rate v-model="commentForms[it.orderItemId].productScore" />
          <span class="ci-gap">物流评分</span>
          <el-rate v-model="commentForms[it.orderItemId].logisticsScore" />
          <el-checkbox v-model="commentForms[it.orderItemId].isAnonymous" class="ci-anon">匿名</el-checkbox>
        </div>
        <el-input v-model="commentForms[it.orderItemId].content" type="textarea" :rows="2"
                  placeholder="说说使用感受吧（选填）" maxlength="500" show-word-limit />
      </div>
      <template #footer>
        <el-button @click="commentDialog = false">取消</el-button>
        <el-button type="primary" :loading="commenting" @click="submitComments">提交评价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrderDetail, cancelOrder, confirmOrder, getOrderTrack, payOrder } from '../api/order'
import { submitComment } from '../api/comment'
import { applyAfterSale, getMyAfterSalePage, cancelAfterSale } from '../api/afterSale'

const route = useRoute()
const router = useRouter()
const detail = ref(null)
const tracks = ref([])
const payDialog = ref(false)
const paying = ref(false)
const commentDialog = ref(false)
const commenting = ref(false)
const commentForms = ref({})
const placeholder = 'https://via.placeholder.com/80'

// 售后
const afterSales = ref([])
const afterSaleDialog = ref(false)
const applying = ref(false)
const asForm = ref({ orderItemId: null, productName: '', type: 0, amount: 0, maxAmount: 0, reason: '' })
const asStatusMap = { 0: '待商家处理', 1: '商家同意', 2: '商家拒绝', 3: '平台介入', 4: '已完成', 5: '已撤销' }
const asTypeMap = { 0: '仅退款', 1: '退货退款', 2: '换货' }
const asStatusText = (s) => asStatusMap[s] || '未知'
const asTypeText = (t) => asTypeMap[t] || '售后'
const asStatusTag = (s) => (s === 0 ? 'warning' : s === 1 ? 'success' : s === 2 ? 'danger'
  : s === 3 ? 'primary' : s === 4 ? 'success' : 'info')

/** 该明细是否有进行中的售后 */
function runningAfterSale(orderItemId) {
  return afterSales.value.some((a) => a.orderItemId === orderItemId && [0, 1, 3].includes(a.status))
}

async function loadAfterSales() {
  if (!detail.value) return
  try {
    const data = await getMyAfterSalePage({ orderId: detail.value.id, pageNum: 1, pageSize: 20 })
    afterSales.value = (data && data.list) || []
  } catch (e) {
    afterSales.value = []
  }
}

function openAfterSale(item) {
  const amount = Number(item.totalAmount || 0)
  asForm.value = {
    orderItemId: item.orderItemId,
    productName: item.productName,
    type: 0,
    amount,
    maxAmount: amount,
    reason: ''
  }
  afterSaleDialog.value = true
}

async function submitAfterSale() {
  if (!asForm.value.reason.trim()) {
    ElMessage.warning('请填写申请原因')
    return
  }
  applying.value = true
  try {
    await applyAfterSale({
      orderId: detail.value.id,
      orderItemId: asForm.value.orderItemId,
      type: asForm.value.type,
      reason: asForm.value.reason.trim(),
      amount: asForm.value.type === 2 ? 0 : asForm.value.amount
    })
    ElMessage.success('售后申请已提交，等待商家处理')
    afterSaleDialog.value = false
    load()
  } catch (e) {
    /* 错误已统一提示 */
  } finally {
    applying.value = false
  }
}

async function cancelAfterSaleItem(row) {
  await ElMessageBox.confirm(`确定撤销售后单「${row.afterSaleNo}」吗？`, '提示', { type: 'warning' })
  await cancelAfterSale(row.id)
  ElMessage.success('已撤销')
  load()
}

const statusMap = { 0: '待付款', 1: '待发货', 2: '待收货', 3: '已完成', 4: '已取消', 5: '售后中' }
const statusText = (s) => statusMap[s] || '未知'

async function load() {
  detail.value = await getOrderDetail(route.params.id)
  if (detail.value.logistics) {
    tracks.value = await getOrderTrack(route.params.id)
  } else {
    tracks.value = []
  }
  await loadAfterSales()
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

function openComment() {
  const forms = {}
  detail.value.items.forEach((it) => {
    forms[it.orderItemId] = { productScore: 5, logisticsScore: 5, content: '', isAnonymous: false }
  })
  commentForms.value = forms
  commentDialog.value = true
}

async function submitComments() {
  commenting.value = true
  try {
    for (const it of detail.value.items) {
      const f = commentForms.value[it.orderItemId]
      if (!f || !f.productScore) continue
      await submitComment({
        orderId: detail.value.id,
        orderItemId: it.orderItemId,
        productScore: f.productScore,
        logisticsScore: f.logisticsScore,
        content: f.content,
        isAnonymous: f.isAnonymous
      })
    }
    ElMessage.success('评价成功！')
    commentDialog.value = false
  } catch (e) {
    /* 错误已统一提示 */
  } finally {
    commenting.value = false
  }
}

onMounted(() => {
  load()
  if (route.query.pay === '1') openPay()
  if (route.query.comment === '1') openComment()
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
.i-ops { width: 96px; text-align: right; }
.as-row { display: flex; align-items: center; gap: 14px; padding: 8px 0; border-bottom: 1px dashed #f1f5f9; font-size: 13px; color: #64748b; }
.as-no { font-family: monospace; color: #334155; }
.as-name { flex: 1; color: #334155; }
.as-time { color: #94a3b8; }
.as-tip { color: #94a3b8; font-size: 12px; margin-left: 10px; }
.amount-box { text-align: right; padding-top: 12px; color: #64748b; line-height: 1.8; }
.pay-amount { font-size: 15px; color: #334155; }
.pay-amount b { color: #dc2626; font-size: 20px; }
.remark { color: #94a3b8; }
.pay-box { text-align: center; padding: 20px 0; }
.pay-amount-big { color: #dc2626; font-size: 32px; font-weight: 800; }
.pay-tip { color: #94a3b8; margin-top: 8px; font-size: 13px; }
.comment-item { border: 1px solid #f1f5f9; border-radius: 8px; padding: 12px; margin-bottom: 12px; }
.ci-head { margin-bottom: 8px; }
.ci-sku { color: #94a3b8; font-size: 12px; margin-left: 8px; }
.ci-form { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; font-size: 13px; color: #64748b; }
.ci-anon { margin-left: auto; }
</style>
