<template>
  <div class="admin-page">
    <el-card>
      <el-tabs v-model="activeTab">
        <!-- 平台数据总览 -->
        <el-tab-pane label="平台数据总览" name="stats">
          <el-row :gutter="16">
            <el-col :span="6" v-for="s in stats" :key="s.label">
              <el-statistic :title="s.label" :value="s.value" />
            </el-col>
          </el-row>
          <el-empty description="管理员统计接口（/api/admin/stats/**）开发完成后展示数据" style="margin-top: 30px" />
        </el-tab-pane>

        <!-- 评价审核 -->
        <el-tab-pane label="评价审核" name="comments">
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

          <el-table :data="comments" v-loading="loading" stripe>
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
                <el-button v-if="row.status === 0" type="success" size="small" @click="audit(row, 1)">通过展示</el-button>
                <el-button v-if="row.status !== 2" type="danger" size="small" @click="audit(row, 2)">违规删除</el-button>
                <el-tag v-if="row.status === 2" type="info" size="small">已删除</el-tag>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination v-if="total > 0" class="pager" background layout="prev, pager, next, total"
                         :total="total" :page-size="pageSize" v-model:current-page="pageNum"
                         @current-change="loadComments" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCommentPage, auditComment } from '../api/comment'

const activeTab = ref('stats')

// 数据总览（占位，待 /api/admin/stats 接入）
const stats = ref([
  { label: '用户数', value: 0 },
  { label: '商品数', value: 0 },
  { label: '订单数', value: 0 },
  { label: '交易总额(¥)', value: 0 }
])

// 评价审核
const comments = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const statusFilter = ref(null)
const keyword = ref('')
const loading = ref(false)

const statusMap = { 0: '待审核', 1: '已展示', 2: '违规删除' }
const statusText = (s) => statusMap[s] || '未知'
const statusTagType = (s) => (s === 0 ? 'warning' : s === 1 ? 'success' : 'danger')

async function loadComments() {
  loading.value = true
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize,
      status: statusFilter.value === null || statusFilter.value === '' ? undefined : statusFilter.value,
      productName: keyword.value || undefined
    }
    const data = await getCommentPage(params)
    comments.value = data.list || []
    total.value = data.total || 0
  } catch (e) {
    /* 错误已统一提示 */
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pageNum.value = 1
  loadComments()
}

async function audit(row, status) {
  const action = status === 1 ? '通过并展示该评价？' : '将该评价标记为违规并删除？'
  await ElMessageBox.confirm(action, '审核确认', { type: status === 1 ? 'success' : 'warning' })
  await auditComment(row.id, status)
  ElMessage.success(status === 1 ? '已通过展示' : '已删除')
  loadComments()
}

onMounted(() => {
  if (activeTab.value === 'comments') loadComments()
})
</script>

<style scoped>
.admin-page { max-width: 1100px; margin: 0 auto; }
.filter-row { display: flex; gap: 12px; margin-bottom: 14px; }
.rate-cell { display: inline-block; margin-right: 8px; color: #475569; font-size: 13px; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
