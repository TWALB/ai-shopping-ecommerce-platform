<template>
  <div class="as-page">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <span class="title">我的咨询与投诉</span>
          <div class="head-right">
            <el-select v-model="status" placeholder="全部状态" clearable style="width: 140px" @change="search">
              <el-option :value="0" label="待处理" />
              <el-option :value="1" label="已回复" />
            </el-select>
            <el-button @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="shopName" label="店铺" min-width="120" show-overflow-tooltip />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.type === 1 ? 'danger' : 'info'" size="small">{{ typeText(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
        <el-table-column label="关联订单" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.orderNo || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'warning'" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="160">
          <template #default="{ row }">{{ row.createdTime?.replace('T', ' ') }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="total > 0" class="pager" background layout="prev, pager, next, total"
                     :total="total" :page-size="pageSize" v-model:current-page="pageNum" @current-change="load" />
    </el-card>

    <el-dialog v-model="detailDialog" title="咨询/投诉详情" width="560px">
      <div v-if="current" class="detail">
        <div class="row"><span class="label">店铺</span><span>{{ current.shopName }}</span></div>
        <div class="row"><span class="label">类型</span><span>{{ typeText(current.type) }}</span></div>
        <div class="row"><span class="label">关联订单</span><span>{{ current.orderNo || '—' }}</span></div>
        <div class="row"><span class="label">提交时间</span><span>{{ current.createdTime?.replace('T', ' ') }}</span></div>
        <div class="row"><span class="label">我的内容</span><span class="content">{{ current.content }}</span></div>
        <div class="row"><span class="label">商家回复</span>
          <span v-if="current.replyContent" class="content reply">{{ current.replyContent }}
            <span class="reply-time">{{ current.replyTime?.replace('T', ' ') }}</span>
          </span>
          <span v-else class="empty">商家暂未回复</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getMyComplaintPage } from '../api/complaint'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const status = ref(null)
const pageNum = ref(1)
const pageSize = 10
const detailDialog = ref(false)
const current = ref(null)

const typeText = (t) => (t === 1 ? '投诉' : '咨询')
const statusText = (s) => (s === 1 ? '已回复' : s === 2 ? '已关闭' : '待处理')

async function load() {
  loading.value = true
  try {
    const data = await getMyComplaintPage({
      pageNum: pageNum.value,
      pageSize,
      status: status.value === null ? undefined : status.value
    })
    list.value = data.list || []
    total.value = data.total || 0
  } catch (e) { /* 错误已统一提示 */ } finally {
    loading.value = false
  }
}

function search() {
  pageNum.value = 1
  load()
}

function openDetail(row) {
  current.value = row
  detailDialog.value = true
}

onMounted(load)
</script>

<style scoped>
.as-page { max-width: 1180px; margin: 0 auto; }
.head { display: flex; align-items: center; justify-content: space-between; }
.title { font-weight: 700; color: #1f2937; }
.head-right { display: flex; gap: 10px; }
.pager { margin-top: 16px; justify-content: flex-end; display: flex; }
.detail .row { display: flex; gap: 12px; padding: 8px 0; border-bottom: 1px dashed #f1f5f9; font-size: 14px; color: #334155; }
.detail .label { width: 72px; flex: none; color: #64748b; }
.detail .content { flex: 1; white-space: pre-wrap; word-break: break-word; }
.detail .reply { color: #0f766e; }
.detail .reply-time { display: block; margin-top: 4px; font-size: 12px; color: #94a3b8; }
.detail .empty { color: #94a3b8; }
</style>
