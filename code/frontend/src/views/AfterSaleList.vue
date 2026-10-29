<template>
  <div class="as-page">
    <el-card>
      <template #header>
        <div class="head">
          <b>我的售后</b>
          <div class="head-right">
            <el-select v-model="status" placeholder="全部状态" clearable style="width: 150px" @change="search">
              <el-option v-for="(t, v) in statusMap" :key="v" :value="Number(v)" :label="t" />
            </el-select>
            <el-button @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="afterSaleNo" label="售后单号" width="190" />
        <el-table-column label="商品" min-width="200">
          <template #default="{ row }">
            <div class="prod">
              <el-image :src="row.productImage || placeholder" fit="cover" class="prod-img" />
              <div>
                <div class="prod-name">{{ row.productName }}</div>
                <div class="prod-sku">{{ row.skuName }} ×{{ row.quantity }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ typeText(row.type) }}</template>
        </el-table-column>
        <el-table-column label="退款金额" width="110">
          <template #default="{ row }">¥{{ row.amount }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="申请原因" min-width="180" show-overflow-tooltip />
        <el-table-column label="申请时间" width="170">
          <template #default="{ row }">{{ row.createdTime?.replace('T', ' ') }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openDetail(row.id)">详情</el-button>
            <el-button v-if="row.status === 0" size="small" link type="danger" @click="cancel(row)">撤销</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination v-if="total > 0" class="pager" background layout="prev, pager, next, total"
                     :total="total" :page-size="pageSize" v-model:current-page="pageNum" @current-change="load" />
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawer" title="售后详情" size="520px">
      <div v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="售后单号">{{ detail.afterSaleNo }}</el-descriptions-item>
          <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="店铺">{{ detail.shopName }}</el-descriptions-item>
          <el-descriptions-item label="商品">{{ detail.productName }}（{{ detail.skuName }} ×{{ detail.quantity }}）</el-descriptions-item>
          <el-descriptions-item label="售后类型">{{ typeText(detail.type) }}</el-descriptions-item>
          <el-descriptions-item label="退款金额">¥{{ detail.amount }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag :type="statusTag(detail.status)" size="small">{{ statusText(detail.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="申请原因">{{ detail.reason }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.rejectReason" label="商家/平台意见">{{ detail.rejectReason }}</el-descriptions-item>
        </el-descriptions>

        <div class="progress-title">处理进度</div>
        <el-timeline>
          <el-timeline-item v-for="(p, i) in detail.progress" :key="i" :timestamp="p.time"
                            :type="p.active ? 'primary' : 'info'" :hollow="!p.active">
            <b>{{ p.title }}</b>
            <div class="p-desc">{{ p.description }}</div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyAfterSalePage, getAfterSaleDetail, cancelAfterSale } from '../api/afterSale'

const placeholder = 'https://via.placeholder.com/60'
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const status = ref(null)
const loading = ref(false)
const drawer = ref(false)
const detail = ref(null)

const statusMap = { 0: '待商家处理', 1: '商家同意', 2: '商家拒绝', 3: '平台介入', 4: '已完成', 5: '已撤销' }
const typeMap = { 0: '仅退款', 1: '退货退款', 2: '换货' }
const statusText = (s) => statusMap[s] || '未知'
const typeText = (t) => typeMap[t] || '售后'
const statusTag = (s) => (s === 0 ? 'warning' : s === 1 ? 'success' : s === 2 ? 'danger'
  : s === 3 ? 'primary' : s === 4 ? 'success' : 'info')

async function load() {
  loading.value = true
  try {
    const data = await getMyAfterSalePage({
      pageNum: pageNum.value,
      pageSize,
      status: status.value === null ? undefined : status.value
    })
    list.value = (data && data.list) || []
    total.value = (data && data.total) || 0
  } catch (e) { /* 错误已统一提示 */ } finally {
    loading.value = false
  }
}

function search() {
  pageNum.value = 1
  load()
}

async function openDetail(id) {
  detail.value = await getAfterSaleDetail(id)
  drawer.value = true
}

async function cancel(row) {
  await ElMessageBox.confirm(`确定撤销售后单「${row.afterSaleNo}」吗？`, '提示', { type: 'warning' })
  await cancelAfterSale(row.id)
  ElMessage.success('已撤销')
  load()
}

onMounted(load)
</script>

<style scoped>
.as-page { max-width: 1180px; margin: 0 auto; }
.head { display: flex; align-items: center; justify-content: space-between; }
.head-right { display: flex; gap: 10px; }
.prod { display: flex; align-items: center; gap: 10px; }
.prod-img { width: 44px; height: 44px; border-radius: 6px; flex: none; }
.prod-name { font-size: 13px; color: #334155; }
.prod-sku { font-size: 12px; color: #94a3b8; margin-top: 2px; }
.pager { margin-top: 16px; justify-content: flex-end; display: flex; }
.progress-title { font-weight: 700; margin: 22px 0 12px; color: #334155; }
.p-desc { color: #64748b; font-size: 13px; margin-top: 2px; }
</style>
