<template>
  <div class="admin-page">
    <el-card>
      <!-- 数据总览 -->
      <div v-show="activeTab === 'stats'">
          <el-row :gutter="16">
            <el-col :span="6" v-for="s in overviewCards" :key="s.label">
              <el-card class="stat-card">
                <el-statistic :title="s.label" :value="s.value" :precision="s.precision || 0" />
              </el-card>
            </el-col>
          </el-row>
          <el-card class="block">
            <template #header>
              <div class="trend-head">
                <b>交易趋势</b>
                <el-radio-group v-model="trendDays" size="small" @change="loadTrend">
                  <el-radio-button :value="7">近7日</el-radio-button>
                  <el-radio-button :value="30">近30日</el-radio-button>
                </el-radio-group>
              </div>
            </template>
            <div ref="trendChartRef" class="trend-chart" />
          </el-card>
        </div>

        <!-- 用户管理 -->
        <div v-show="activeTab === 'users'">
          <div class="tool-row">
            <el-input v-model="userKeyword" placeholder="账号/昵称/手机号" clearable style="width: 200px" @keyup.enter="searchUsers" @clear="searchUsers" />
            <el-select v-model="userRole" placeholder="角色" clearable style="width: 130px" @change="searchUsers">
              <el-option :value="0" label="普通用户" />
              <el-option :value="1" label="商家" />
              <el-option :value="2" label="管理员" />
            </el-select>
            <el-select v-model="userStatus" placeholder="状态" clearable style="width: 120px" @change="searchUsers">
              <el-option :value="1" label="正常" />
              <el-option :value="0" label="禁用" />
            </el-select>
            <el-button type="primary" @click="searchUsers">搜索</el-button>
          </div>
          <el-table :data="users" v-loading="userLoading" stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="username" label="账号" width="120" />
            <el-table-column prop="nickname" label="昵称" width="120" />
            <el-table-column prop="phone" label="手机" width="130" />
            <el-table-column label="角色" width="90">
              <template #default="{ row }">
                <el-tag :type="row.role === 2 ? 'danger' : row.role === 1 ? 'warning' : 'info'">
                  {{ roleText(row.role) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '正常' : '禁用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="shopName" label="店铺" min-width="120">
              <template #default="{ row }">{{ row.shopName || '—' }}</template>
            </el-table-column>
            <el-table-column label="注册时间" width="160">
              <template #default="{ row }">{{ row.createdTime?.replace('T', ' ') }}</template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 1" size="small" type="danger" @click="toggleUser(row, 0)">禁用</el-button>
                <el-button v-else size="small" type="success" @click="toggleUser(row, 1)">启用</el-button>
                <el-dropdown @command="(role) => changeRole(row, role)">
                  <el-button size="small">改角色<el-icon><ArrowDown /></el-icon></el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="0">设为普通用户</el-dropdown-item>
                      <el-dropdown-item command="1">设为商家</el-dropdown-item>
                      <el-dropdown-item command="2">设为管理员</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-if="userTotal > 0" class="pager" background layout="prev, pager, next, total"
                         :total="userTotal" :page-size="pageSize" v-model:current-page="userPageNum" @current-change="loadUsers" />
        </div>

        <!-- 入驻审核 -->
        <div v-show="activeTab === 'applies'">
          <div class="tool-row">
            <el-select v-model="applyStatus" placeholder="审核状态" clearable style="width: 150px" @change="searchApplies">
              <el-option :value="0" label="待审核" />
              <el-option :value="1" label="已通过" />
              <el-option :value="2" label="已拒绝" />
            </el-select>
          </div>
          <el-table :data="applies" v-loading="applyLoading" stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column label="申请人" min-width="120">
              <template #default="{ row }">{{ row.nickname || row.username }}（{{ row.username }}）</template>
            </el-table-column>
            <el-table-column prop="realName" label="真实姓名" width="100" />
            <el-table-column prop="contactPhone" label="电话" width="120" />
            <el-table-column prop="shopName" label="拟开店名" width="140" />
            <el-table-column prop="shopIntro" label="简介" min-width="140" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 0 ? 'warning' : row.status === 1 ? 'success' : 'danger'">
                  {{ applyStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="申请时间" width="160">
              <template #default="{ row }">{{ row.createdTime?.replace('T', ' ') }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <template v-if="row.status === 0">
                  <el-button size="small" type="success" @click="audit(row, true)">通过</el-button>
                  <el-button size="small" type="danger" @click="audit(row, false)">拒绝</el-button>
                </template>
                <span v-else class="empty-text">{{ row.rejectReason || '—' }}</span>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-if="applyTotal > 0" class="pager" background layout="prev, pager, next, total"
                         :total="applyTotal" :page-size="pageSize" v-model:current-page="applyPageNum" @current-change="loadApplies" />
        </div>

        <!-- 商品管理 -->
        <div v-show="activeTab === 'products'">
          <div class="tool-row">
            <el-radio-group v-model="productStatus" @change="searchProducts">
              <el-radio-button :value="''">全部</el-radio-button>
              <el-radio-button :value="0">草稿</el-radio-button>
              <el-radio-button :value="1">已上架</el-radio-button>
              <el-radio-button :value="2">已下架</el-radio-button>
            </el-radio-group>
            <el-button type="primary" plain :loading="syncing" class="right-btn" @click="handleSyncKnowledge">
              重建导购知识库
            </el-button>
          </div>
          <el-table :data="products" v-loading="productLoading" stripe>
            <el-table-column label="商品" min-width="220">
              <template #default="{ row }">
                <div class="prod-cell">
                  <el-image :src="row.mainImage || placeholder" fit="cover" class="prod-img" />
                  <span>{{ row.productName }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="shopName" label="店铺" width="140" />
            <el-table-column prop="price" label="售价" width="90">
              <template #default="{ row }">¥{{ row.price }}</template>
            </el-table-column>
            <el-table-column prop="stock" label="库存" width="80" />
            <el-table-column prop="sales" label="销量" width="80" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : row.status === 0 ? 'info' : 'warning'">
                  {{ productStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status !== 1" size="small" type="success" @click="toggleProduct(row, 1)">上架</el-button>
                <el-button v-if="row.status === 1" size="small" type="warning" @click="toggleProduct(row, 2)">下架</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-if="productTotal > 0" class="pager" background layout="prev, pager, next, total"
                         :total="productTotal" :page-size="pageSize" v-model:current-page="productPageNum" @current-change="loadProducts" />
        </div>

        <!-- 订单管理 -->
        <div v-show="activeTab === 'orders'">
          <div class="tool-row">
            <el-input v-model="orderNo" placeholder="订单号精确查询" clearable style="width: 220px" @keyup.enter="searchOrders" @clear="searchOrders" />
            <el-select v-model="orderStatus" placeholder="订单状态" clearable style="width: 130px" @change="searchOrders">
              <el-option v-for="(t, v) in orderStatusMap" :key="v" :value="Number(v)" :label="t" />
            </el-select>
            <el-button type="primary" @click="searchOrders">搜索</el-button>
          </div>
          <el-table :data="orders" v-loading="orderLoading" stripe>
            <el-table-column prop="orderNo" label="订单号" width="200" />
            <el-table-column prop="shopName" label="店铺" width="140" />
            <el-table-column prop="itemCount" label="件数" width="70" />
            <el-table-column prop="payAmount" label="实付" width="110">
              <template #default="{ row }">¥{{ row.payAmount }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="orderStatusTag(row.status)">{{ orderStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="下单时间" width="170">
              <template #default="{ row }">{{ row.createdTime?.replace('T', ' ') }}</template>
            </el-table-column>
          </el-table>
          <el-pagination v-if="orderTotal > 0" class="pager" background layout="prev, pager, next, total"
                         :total="orderTotal" :page-size="pageSize" v-model:current-page="orderPageNum" @current-change="loadOrders" />
        </div>

        <!-- 售后管理 -->
        <div v-show="activeTab === 'aftersales'">
          <div class="tool-row">
            <el-select v-model="afterSaleStatus" placeholder="售后状态" clearable style="width: 160px" @change="searchAfterSales">
              <el-option :value="0" label="待商家处理" />
              <el-option :value="1" label="商家同意" />
              <el-option :value="2" label="商家拒绝" />
              <el-option :value="3" label="平台介入" />
              <el-option :value="4" label="已完成" />
              <el-option :value="5" label="已撤销" />
            </el-select>
          </div>
          <el-table :data="aftersales" v-loading="afterSaleLoading" stripe>
            <el-table-column prop="afterSaleNo" label="售后单号" width="190" />
            <el-table-column prop="username" label="申请人" width="110" />
            <el-table-column prop="productName" label="商品" min-width="160" show-overflow-tooltip />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">{{ afterSaleTypeText(row.type) }}</template>
            </el-table-column>
            <el-table-column prop="amount" label="金额" width="100">
              <template #default="{ row }">¥{{ row.amount }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="afterSaleStatusTag(row.status)">{{ afterSaleStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="原因" min-width="140" show-overflow-tooltip />
            <el-table-column label="申请时间" width="160">
              <template #default="{ row }">{{ row.createdTime?.replace('T', ' ') }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 2" size="small" type="warning" @click="platformIntervene(row, 3)">介入处理</el-button>
                <el-button v-if="row.status === 3" size="small" type="success" @click="platformIntervene(row, 4)">完成退款</el-button>
                <span v-if="row.status !== 2 && row.status !== 3" class="empty-text">—</span>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-if="afterSaleTotal > 0" class="pager" background layout="prev, pager, next, total"
                         :total="afterSaleTotal" :page-size="pageSize" v-model:current-page="afterSalePageNum" @current-change="loadAfterSales" />
        </div>

        <!-- 公告管理 -->
        <div v-show="activeTab === 'notices'">
          <div class="tool-row">
            <el-button type="primary" @click="openNoticeDialog">+ 发布公告</el-button>
          </div>
          <el-table :data="notices" v-loading="noticeLoading" stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">{{ noticeTypeText(row.type) }}</template>
            </el-table-column>
            <el-table-column label="发布时间" width="170">
              <template #default="{ row }">{{ row.publishTime?.replace('T', ' ') }}</template>
            </el-table-column>
            <el-table-column prop="publisherName" label="发布人" width="110" />
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="danger" @click="removeNotice(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-if="noticeTotal > 0" class="pager" background layout="prev, pager, next, total"
                         :total="noticeTotal" :page-size="pageSize" v-model:current-page="noticePageNum" @current-change="loadNotices" />
        </div>

        <!-- 评价审核（评价模块） -->
        <div v-show="activeTab === 'comments'">
          <div class="filter-row">
            <el-select v-model="statusFilter" placeholder="审核状态" clearable style="width: 160px" @change="loadComments">
              <el-option label="待审核" :value="0" />
              <el-option label="已展示" :value="1" />
              <el-option label="违规删除" :value="2" />
            </el-select>
            <el-input v-model="keyword" placeholder="按商品名搜索" clearable style="width: 220px"
                      @keyup.enter="onSearch" @clear="onSearch" />
            <el-button type="primary" @click="onSearch">搜索</el-button>
          </div>

          <el-table :data="comments" v-loading="commentLoading" stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="productName" label="商品" min-width="150" show-overflow-tooltip />
            <el-table-column prop="nickname" label="评价人" width="110" />
            <el-table-column label="评分" width="130">
              <template #default="{ row }">
                <span class="rate-cell">商品 {{ row.productScore }} ★</span>
                <span class="rate-cell">物流 {{ row.logisticsScore }} ★</span>
              </template>
            </el-table-column>
            <el-table-column prop="content" label="评价内容" min-width="200" show-overflow-tooltip />
            <el-table-column prop="createdTime" label="提交时间" width="160">
              <template #default="{ row }">{{ row.createdTime?.replace('T', ' ') }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 0" type="success" size="small" @click="handleAuditComment(row, 1)">通过展示</el-button>
                <el-button v-if="row.status !== 2" type="danger" size="small" @click="handleAuditComment(row, 2)">违规删除</el-button>
                <el-tag v-if="row.status === 2" type="info" size="small">已删除</el-tag>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination v-if="commentTotal > 0" class="pager" background layout="prev, pager, next, total"
                         :total="commentTotal" :page-size="commentPageSize" v-model:current-page="commentPageNum"
                         @current-change="loadComments" />
        </div>

        <!-- 操作日志 -->
        <div v-show="activeTab === 'logs'">
          <div class="tool-row">
            <el-input v-model="logUsername" placeholder="操作人" clearable style="width: 150px" @keyup.enter="searchLogs" @clear="searchLogs" />
            <el-input v-model="logModule" placeholder="模块（如 用户/审核/商品）" clearable style="width: 180px" @keyup.enter="searchLogs" @clear="searchLogs" />
            <el-button type="primary" @click="searchLogs">搜索</el-button>
          </div>
          <el-table :data="logs" v-loading="logLoading" stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="username" label="操作人" width="120" />
            <el-table-column prop="module" label="模块" width="90" />
            <el-table-column prop="action" label="操作内容" min-width="260" show-overflow-tooltip />
            <el-table-column prop="ip" label="IP" width="130" />
            <el-table-column label="时间" width="170">
              <template #default="{ row }">{{ row.createdTime?.replace('T', ' ') }}</template>
            </el-table-column>
          </el-table>
          <el-pagination v-if="logTotal > 0" class="pager" background layout="prev, pager, next, total"
                         :total="logTotal" :page-size="pageSize" v-model:current-page="logPageNum" @current-change="loadLogs" />
        </div>
    </el-card>

    <!-- 拒绝入驻弹窗 -->
    <el-dialog v-model="rejectDialog" title="拒绝入驻申请" width="440px">
      <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请填写拒绝原因" maxlength="200" />
      <template #footer>
        <el-button @click="rejectDialog = false">取消</el-button>
        <el-button type="danger" :loading="auditing" @click="doReject">确认拒绝</el-button>
      </template>
    </el-dialog>

    <!-- 发布公告弹窗 -->
    <el-dialog v-model="noticeDialog" title="发布公告" width="560px">
      <el-form :model="noticeForm" label-width="80px">
        <el-form-item label="标题" required>
          <el-input v-model="noticeForm.title" maxlength="100" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="noticeForm.type" style="width: 100%">
            <el-option :value="0" label="活动通知" />
            <el-option :value="1" label="商品上新" />
            <el-option :value="2" label="平台规则" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="noticeForm.content" type="textarea" :rows="4" maxlength="2000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="noticeDialog = false">取消</el-button>
        <el-button type="primary" :loading="publishing" @click="publishNotice">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import {
  getStatsOverview, getStatsTrend, getAdminUserPage, setUserStatus, setUserRole,
  getAdminApplyPage, auditApply, getAdminProductPage, setAdminProductStatus,
  getAdminOrderPage, getAdminAfterSalePage, addNotice, getNoticePage, deleteNotice,
  getOperationLogPage
} from '../api/admin'
import { getCommentPage, auditComment as auditCommentApi } from '../api/comment'
import { adminHandleAfterSale } from '../api/afterSale'
import { syncKnowledge } from '../api/chat'

const route = useRoute()
const placeholder = 'https://via.placeholder.com/80'
const activeTab = ref(route.query.tab || 'stats')
const pageSize = 10

// ===== 数据总览 =====
const overview = ref({})
const trendDays = ref(7)
const trendChartRef = ref(null)
let chart = null
const overviewCards = computed(() => [
  { label: '用户数', value: overview.value.userCount || 0 },
  { label: '商家数', value: overview.value.merchantCount || 0 },
  { label: '商品数', value: overview.value.productCount || 0 },
  { label: '订单数', value: overview.value.orderCount || 0 },
  { label: '累计交易额(¥)', value: overview.value.totalAmount || 0, precision: 2 },
  { label: '今日订单', value: overview.value.todayOrderCount || 0 },
  { label: '今日交易额(¥)', value: overview.value.todayAmount || 0, precision: 2 }
])

async function loadOverview() {
  overview.value = await getStatsOverview()
}

async function loadTrend() {
  const list = await getStatsTrend(trendDays.value)
  await nextTick()
  if (!chart) {
    chart = echarts.init(trendChartRef.value)
  }
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['订单数', '交易额'] },
    grid: { left: 60, right: 60, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: list.map((p) => p.date) },
    yAxis: [
      { type: 'value', name: '订单数', minInterval: 1 },
      { type: 'value', name: '交易额(¥)' }
    ],
    series: [
      { name: '订单数', type: 'line', smooth: true, data: list.map((p) => p.orderCount) },
      { name: '交易额', type: 'line', smooth: true, yAxisIndex: 1, data: list.map((p) => p.amount) }
    ]
  })
}

// ===== 用户管理 =====
const users = ref([])
const userTotal = ref(0)
const userPageNum = ref(1)
const userLoading = ref(false)
const userKeyword = ref('')
const userRole = ref(null)
const userStatus = ref(null)
const roleText = (r) => (r === 0 ? '普通用户' : r === 1 ? '商家' : '管理员')

async function loadUsers() {
  userLoading.value = true
  try {
    const data = await getAdminUserPage({
      pageNum: userPageNum.value, pageSize,
      keyword: userKeyword.value || undefined,
      role: userRole.value === null ? undefined : userRole.value,
      status: userStatus.value === null ? undefined : userStatus.value
    })
    users.value = data.list || []
    userTotal.value = data.total || 0
  } catch (e) { /* 忽略 */ } finally {
    userLoading.value = false
  }
}
function searchUsers() {
  userPageNum.value = 1
  loadUsers()
}
async function toggleUser(row, status) {
  await ElMessageBox.confirm(`确定${status === 0 ? '禁用' : '启用'}用户「${row.nickname || row.username}」吗？`, '提示', { type: 'warning' })
  await setUserStatus(row.id, status)
  ElMessage.success(status === 0 ? '已禁用' : '已启用')
  loadUsers()
}
async function changeRole(row, role) {
  await ElMessageBox.confirm(`确定将「${row.nickname || row.username}」设为${roleText(Number(role))}吗？`, '提示', { type: 'warning' })
  await setUserRole(row.id, Number(role))
  ElMessage.success('角色已调整')
  loadUsers()
}

// ===== 入驻审核 =====
const applies = ref([])
const applyTotal = ref(0)
const applyPageNum = ref(1)
const applyLoading = ref(false)
const applyStatus = ref(null)
const rejectDialog = ref(false)
const rejectReason = ref('')
const rejectApplyId = ref(null)
const auditing = ref(false)
const applyStatusText = (s) => (s === 0 ? '待审核' : s === 1 ? '已通过' : '已拒绝')

async function loadApplies() {
  applyLoading.value = true
  try {
    const data = await getAdminApplyPage({
      pageNum: applyPageNum.value, pageSize,
      status: applyStatus.value === null ? undefined : applyStatus.value
    })
    applies.value = data.list || []
    applyTotal.value = data.total || 0
  } catch (e) { /* 忽略 */ } finally {
    applyLoading.value = false
  }
}
function searchApplies() {
  applyPageNum.value = 1
  loadApplies()
}
function audit(row, agree) {
  if (agree) {
    ElMessageBox.confirm(`通过后将为「${row.shopName}」自动开通店铺，确认通过？`, '审核确认', { type: 'success' })
      .then(async () => {
        await auditApply(row.id, true)
        ElMessage.success('已通过，店铺已开通')
        loadApplies()
      }).catch(() => {})
  } else {
    rejectReason.value = ''
    rejectApplyId.value = row.id
    rejectDialog.value = true
  }
}
async function doReject() {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写拒绝原因')
    return
  }
  auditing.value = true
  try {
    await auditApply(rejectApplyId.value, false, rejectReason.value)
    ElMessage.success('已拒绝')
    rejectDialog.value = false
    loadApplies()
  } catch (e) { /* 忽略 */ } finally {
    auditing.value = false
  }
}

// ===== 商品管理 =====
const products = ref([])
const productTotal = ref(0)
const productPageNum = ref(1)
const productLoading = ref(false)
const productStatus = ref('')
const productStatusText = (s) => (s === 0 ? '草稿' : s === 1 ? '已上架' : '已下架')

async function loadProducts() {
  productLoading.value = true
  try {
    const data = await getAdminProductPage({
      pageNum: productPageNum.value, pageSize,
      status: productStatus.value === '' ? undefined : productStatus.value
    })
    products.value = data.list || []
    productTotal.value = data.total || 0
  } catch (e) { /* 忽略 */ } finally {
    productLoading.value = false
  }
}
function searchProducts() {
  productPageNum.value = 1
  loadProducts()
}
async function toggleProduct(row, status) {
  await ElMessageBox.confirm(`确定${status === 1 ? '上架' : '下架'}「${row.productName}」吗？`, '提示', { type: 'warning' })
  await setAdminProductStatus(row.id, status)
  ElMessage.success(status === 1 ? '已上架' : '已下架')
  loadProducts()
}

// 重建导购知识库（商品向量化）
const syncing = ref(false)
async function handleSyncKnowledge() {
  await ElMessageBox.confirm('将遍历全部上架商品并调用 Embedding 重建导购向量库，确认执行？', '知识库同步', { type: 'warning' })
  syncing.value = true
  try {
    const count = await syncKnowledge(true)
    ElMessage.success(`向量化完成，共处理 ${count} 件商品`)
  } catch (e) { /* 错误已统一提示（未配置 API Key 时会提示） */ } finally {
    syncing.value = false
  }
}

// ===== 订单管理 =====
const orders = ref([])
const orderTotal = ref(0)
const orderPageNum = ref(1)
const orderLoading = ref(false)
const orderNo = ref('')
const orderStatus = ref(null)
const orderStatusMap = { 0: '待付款', 1: '待发货', 2: '待收货', 3: '已完成', 4: '已取消', 5: '售后中' }
const orderStatusText = (s) => orderStatusMap[s] || '未知'
const orderStatusTag = (s) => (s === 0 ? 'warning' : s === 1 ? 'danger' : s === 2 ? 'primary' : 'info')

async function loadOrders() {
  orderLoading.value = true
  try {
    const data = await getAdminOrderPage({
      pageNum: orderPageNum.value, pageSize,
      status: orderStatus.value === null ? undefined : orderStatus.value,
      orderNo: orderNo.value || undefined
    })
    orders.value = data.list || []
    orderTotal.value = data.total || 0
  } catch (e) { /* 忽略 */ } finally {
    orderLoading.value = false
  }
}
function searchOrders() {
  orderPageNum.value = 1
  loadOrders()
}

// ===== 售后管理 =====
const aftersales = ref([])
const afterSaleTotal = ref(0)
const afterSalePageNum = ref(1)
const afterSaleLoading = ref(false)
const afterSaleStatus = ref(null)
const afterSaleTypeText = (t) => (t === 0 ? '仅退款' : t === 1 ? '退货退款' : '换货')
const afterSaleStatusMap = { 0: '待商家处理', 1: '商家同意', 2: '商家拒绝', 3: '平台介入', 4: '已完成', 5: '已撤销' }
const afterSaleStatusText = (s) => afterSaleStatusMap[s] || '未知'
const afterSaleStatusTag = (s) => (s === 0 ? 'warning' : s === 3 ? 'danger' : s === 4 ? 'success' : 'info')

async function loadAfterSales() {
  afterSaleLoading.value = true
  try {
    const data = await getAdminAfterSalePage({
      pageNum: afterSalePageNum.value, pageSize,
      status: afterSaleStatus.value === null ? undefined : afterSaleStatus.value
    })
    aftersales.value = data.list || []
    afterSaleTotal.value = data.total || 0
  } catch (e) { /* 忽略 */ } finally {
    afterSaleLoading.value = false
  }
}
function searchAfterSales() {
  afterSalePageNum.value = 1
  loadAfterSales()
}

/** 平台介入（3）或平台判定完成退款（4） */
async function platformIntervene(row, targetStatus) {
  const tip = targetStatus === 3
    ? `确认受理售后单「${row.afterSaleNo}」的平台介入？`
    : `确认判定「${row.afterSaleNo}」退款完成，并退款 ¥${row.amount}？`
  await ElMessageBox.confirm(tip, '平台处理', { type: 'warning' })
  await adminHandleAfterSale(row.id, { status: targetStatus, remark: '平台处理' })
  ElMessage.success(targetStatus === 3 ? '已进入平台介入' : '已判定完成并退款')
  loadAfterSales()
}

// ===== 公告管理 =====
const notices = ref([])
const noticeTotal = ref(0)
const noticePageNum = ref(1)
const noticeLoading = ref(false)
const noticeDialog = ref(false)
const publishing = ref(false)
const noticeForm = ref({ title: '', content: '', type: 0 })
const noticeTypeText = (t) => (t === 0 ? '活动' : t === 1 ? '商品上新' : '平台规则')

async function loadNotices() {
  noticeLoading.value = true
  try {
    const data = await getNoticePage({ pageNum: noticePageNum.value, pageSize })
    notices.value = data.list || []
    noticeTotal.value = data.total || 0
  } catch (e) { /* 忽略 */ } finally {
    noticeLoading.value = false
  }
}
function openNoticeDialog() {
  noticeForm.value = { title: '', content: '', type: 0 }
  noticeDialog.value = true
}
async function publishNotice() {
  if (!noticeForm.value.title || !noticeForm.value.content) {
    ElMessage.warning('请填写标题与内容')
    return
  }
  publishing.value = true
  try {
    await addNotice(noticeForm.value)
    ElMessage.success('公告已发布')
    noticeDialog.value = false
    loadNotices()
  } catch (e) { /* 忽略 */ } finally {
    publishing.value = false
  }
}
async function removeNotice(row) {
  await ElMessageBox.confirm(`确定删除公告「${row.title}」吗？`, '提示', { type: 'warning' })
  await deleteNotice(row.id)
  ElMessage.success('已删除')
  loadNotices()
}

// ===== 评价审核（评价模块）=====
const comments = ref([])
const commentTotal = ref(0)
const commentPageNum = ref(1)
const commentPageSize = 10
const statusFilter = ref(null)
const keyword = ref('')
const commentLoading = ref(false)
const statusMap = { 0: '待审核', 1: '已展示', 2: '违规删除' }
const statusText = (s) => statusMap[s] || '未知'
const statusTagType = (s) => (s === 0 ? 'warning' : s === 1 ? 'success' : 'danger')

async function loadComments() {
  commentLoading.value = true
  try {
    const data = await getCommentPage({
      pageNum: commentPageNum.value,
      pageSize: commentPageSize,
      status: statusFilter.value === null || statusFilter.value === '' ? undefined : statusFilter.value,
      productName: keyword.value || undefined
    })
    comments.value = data.list || []
    commentTotal.value = data.total || 0
  } catch (e) { /* 忽略 */ } finally {
    commentLoading.value = false
  }
}
function onSearch() {
  commentPageNum.value = 1
  loadComments()
}
async function handleAuditComment(row, status) {
  await ElMessageBox.confirm(status === 1 ? '通过并展示该评价？' : '将该评价标记为违规并删除？', '审核确认', { type: 'warning' })
  await auditCommentApi(row.id, status)
  ElMessage.success(status === 1 ? '已通过展示' : '已删除')
  loadComments()
}

// ===== 操作日志 =====
const logs = ref([])
const logTotal = ref(0)
const logPageNum = ref(1)
const logLoading = ref(false)
const logModule = ref('')
const logUsername = ref('')

async function loadLogs() {
  logLoading.value = true
  try {
    const data = await getOperationLogPage({
      pageNum: logPageNum.value, pageSize,
      module: logModule.value || undefined,
      username: logUsername.value || undefined
    })
    logs.value = data.list || []
    logTotal.value = data.total || 0
  } catch (e) { /* 忽略 */ } finally {
    logLoading.value = false
  }
}
function searchLogs() {
  logPageNum.value = 1
  loadLogs()
}

// ===== Tab 切换（侧边栏菜单由路由 query.tab 驱动）=====
function onTabChange(name) {
  const loaders = {
    stats: () => { loadOverview(); loadTrend() },
    users: loadUsers,
    applies: loadApplies,
    products: loadProducts,
    orders: loadOrders,
    aftersales: loadAfterSales,
    notices: loadNotices,
    comments: loadComments,
    logs: loadLogs
  }
  loaders[name]?.()
}

watch(() => route.query.tab, (tab) => {
  const next = tab || 'stats'
  if (next !== activeTab.value) {
    activeTab.value = next
    onTabChange(next)
  }
})

onMounted(() => {
  onTabChange(activeTab.value)
  window.addEventListener('resize', () => chart?.resize())
})
</script>

<style scoped>
.admin-page { max-width: 1180px; margin: 0 auto; }
.stat-card { text-align: center; margin-bottom: 8px; }
.block { margin-top: 16px; }
.trend-head { display: flex; align-items: center; justify-content: space-between; }
.trend-chart { height: 320px; }
.tool-row { display: flex; gap: 12px; margin-bottom: 14px; align-items: center; }
.right-btn { margin-left: auto; }
.filter-row { display: flex; gap: 12px; margin-bottom: 14px; }
.prod-cell { display: flex; align-items: center; gap: 10px; }
.prod-img { width: 52px; height: 52px; border-radius: 6px; }
.rate-cell { display: inline-block; margin-right: 8px; color: #475569; font-size: 13px; }
.pager { margin-top: 16px; justify-content: flex-end; }
.empty-text { color: #94a3b8; font-size: 13px; }
</style>
