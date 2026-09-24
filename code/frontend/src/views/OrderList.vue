<template>
  <div class="order-page">
    <el-card>
      <el-tabs v-model="activeStatus" @tab-change="load">
        <el-tab-pane label="全部" name="" />
        <el-tab-pane label="待付款" name="0" />
        <el-tab-pane label="待发货" name="1" />
        <el-tab-pane label="待收货" name="2" />
        <el-tab-pane label="已完成" name="3" />
        <el-tab-pane label="已取消" name="4" />
      </el-tabs>

      <el-empty v-if="!orders.length" description="暂无订单" />

      <div class="order-card" v-for="o in orders" :key="o.id">
        <div class="oc-head">
          <span class="oc-shop">🏪 {{ o.shopName }}</span>
          <span class="oc-no">订单号：{{ o.orderNo }}</span>
          <span class="oc-status">{{ statusText(o.status) }}</span>
        </div>
        <div class="oc-body" @click="$router.push(`/orders/${o.id}`)">
          <el-image :src="o.firstImage || placeholder" fit="cover" class="oc-img" />
          <span class="oc-count">共 {{ o.itemCount }} 件商品</span>
          <span class="oc-amount">实付 <b>¥{{ o.payAmount }}</b></span>
          <span class="oc-time">{{ o.createdTime?.replace('T', ' ') }}</span>
        </div>
        <div class="oc-ops">
          <el-button v-if="o.status === 0" type="primary" size="small" @click="$router.push(`/orders/${o.id}?pay=1`)">去支付</el-button>
          <el-button v-if="o.status === 0" size="small" @click="cancel(o)">取消订单</el-button>
          <el-button v-if="o.status === 2" type="success" size="small" @click="confirm(o)">确认收货</el-button>
          <el-button size="small" @click="$router.push(`/orders/${o.id}`)">查看详情</el-button>
        </div>
      </div>

      <el-pagination v-if="total > 0" class="pager" background layout="prev, pager, next"
                     :total="total" :page-size="pageSize" v-model:current-page="pageNum"
                     @current-change="load" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrderPage, cancelOrder, confirmOrder } from '../api/order'

const orders = ref([])
const activeStatus = ref('')
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const placeholder = 'https://via.placeholder.com/120'

const statusMap = { 0: '待付款', 1: '待发货', 2: '待收货', 3: '已完成', 4: '已取消', 5: '售后中' }
const statusText = (s) => statusMap[s] || '未知'

async function load() {
  const params = { pageNum: pageNum.value, pageSize, status: activeStatus.value || undefined }
  const data = await getOrderPage(params)
  orders.value = data.list || []
  total.value = data.total || 0
}

async function cancel(o) {
  await ElMessageBox.confirm('取消后库存将回补，确定取消该订单吗？', '提示', { type: 'warning' })
  await cancelOrder(o.id)
  ElMessage.success('订单已取消')
  load()
}

async function confirm(o) {
  await ElMessageBox.confirm('请确认已收到商品', '确认收货', { type: 'warning' })
  await confirmOrder(o.id)
  ElMessage.success('已确认收货')
  load()
}

onMounted(load)
</script>

<style scoped>
.order-page { max-width: 1000px; margin: 0 auto; }
.order-card { border: 1px solid #e2e8f0; border-radius: 10px; margin-bottom: 16px; overflow: hidden; }
.oc-head { display: flex; align-items: center; gap: 16px; background: #f8fafc; padding: 10px 16px; font-size: 13px; }
.oc-shop { font-weight: 600; }
.oc-no { color: #94a3b8; }
.oc-status { margin-left: auto; color: #dc2626; font-weight: 600; }
.oc-body { display: flex; align-items: center; gap: 16px; padding: 14px 16px; cursor: pointer; }
.oc-img { width: 90px; height: 90px; border-radius: 8px; }
.oc-count { color: #64748b; }
.oc-amount { margin-left: auto; color: #64748b; }
.oc-amount b { color: #dc2626; font-size: 16px; }
.oc-time { color: #94a3b8; font-size: 12px; width: 160px; text-align: right; }
.oc-ops { display: flex; justify-content: flex-end; gap: 8px; padding: 0 16px 14px; }
.pager { margin-top: 16px; justify-content: center; }
</style>
