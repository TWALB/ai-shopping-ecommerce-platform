<template>
  <div class="ai-page">
    <div class="ai-head">
      <div>
        <h3>AI 智能导购任务</h3>
        <p>每个会话即一次导购任务：从用户第一句提问开始，到最近一次回答结束。点开可看完整对话与每次推荐的商品。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <div class="tool-row">
        <el-input v-model="keyword" placeholder="会话标题" clearable style="width: 240px"
                  @keyup.enter="search" @clear="search" />
        <el-button type="primary" @click="search">查询</el-button>
        <span class="spacer" />
        <span class="muted">共 {{ total }} 个会话</span>
      </div>

      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="id" label="会话" width="80" />
        <el-table-column label="用户" width="150">
          <template #default="{ row }">
            <div>{{ row.nickname || row.username }}</div>
            <div class="muted">@{{ row.username }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="sessionTitle" label="会话标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="questionCount" label="提问" width="80" />
        <el-table-column prop="messageCount" label="消息" width="80" />
        <el-table-column label="最近意图" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.lastIntent" size="small" type="info">{{ intentLabel(row.lastIntent) }}</el-tag>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="160">
          <template #default="{ row }"><span class="muted">{{ fmt(row.updatedTime || row.createdTime) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openSession(row)">查看对话</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination v-if="total > 0" class="pager" background layout="prev, pager, next, total"
                     :total="total" :page-size="pageSize" v-model:current-page="pageNum" @current-change="load" />
    </el-card>

    <el-drawer v-model="drawer" :title="current?.sessionTitle || '会话详情'" size="520px">
      <div v-loading="msgLoading" class="chat-drawer">
        <div v-if="!messages.length" class="empty-hint">该会话暂无消息</div>
        <div v-for="m in messages" :key="m.id" class="chat-row" :class="{ right: m.role === 0 }">
          <div>
            <div class="chat-meta" :style="{ textAlign: m.role === 0 ? 'right' : 'left' }">
              {{ m.role === 0 ? '用户' : '导购' }} · {{ fmt(m.createdTime) }}
              <template v-if="m.intent"> · {{ intentLabel(m.intent) }}</template>
              <template v-if="m.productIds"> · 推荐 {{ m.productIds.split(',').length }} 件</template>
            </div>
            <div class="chat-bubble" :class="m.role === 0 ? 'user' : 'ai'">{{ m.content }}</div>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getAiSessionPage, getAiSessionMessages } from '../../api/adminAi'

const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const keyword = ref('')
const loading = ref(false)
const drawer = ref(false)
const current = ref(null)
const messages = ref([])
const msgLoading = ref(false)

const INTENTS = { find_product: '找商品', compare: '参数对比', recommend: '选购建议', chat: '闲聊' }
const intentLabel = (v) => INTENTS[v] || v

function fmt(v) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '-'
}

async function load() {
  loading.value = true
  try {
    const data = await getAiSessionPage({
      keyword: keyword.value || undefined,
      pageNum: pageNum.value,
      pageSize
    })
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function search() {
  pageNum.value = 1
  load()
}

async function openSession(row) {
  current.value = row
  drawer.value = true
  msgLoading.value = true
  try {
    messages.value = await getAiSessionMessages(row.id)
  } finally {
    msgLoading.value = false
  }
}

onMounted(load)
</script>
