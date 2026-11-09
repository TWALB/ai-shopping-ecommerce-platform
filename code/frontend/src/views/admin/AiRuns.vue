<template>
  <div class="ai-page">
    <div class="ai-head">
      <div>
        <h3>Agent Run 运行记录</h3>
        <p>一次运行 = 用户一次提问 + 导购一次回答。记录用于定位"哪类问题答得不好"：看意图、召回数量、回答长度与 token 消耗。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <div class="tool-row">
        <el-select v-model="intent" placeholder="全部意图" clearable style="width: 160px" @change="search">
          <el-option label="找商品" value="find_product" />
          <el-option label="参数对比" value="compare" />
          <el-option label="选购建议" value="recommend" />
          <el-option label="闲聊/域外" value="chat" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <span class="spacer" />
        <span class="muted">共 {{ total }} 次运行</span>
      </div>

      <el-table :data="rows" v-loading="loading" stripe @row-click="openSteps">
        <el-table-column prop="id" label="Run" width="80">
          <template #default="{ row }"><span class="mono">#{{ row.id }}</span></template>
        </el-table-column>
        <el-table-column prop="question" label="用户提问" min-width="220" show-overflow-tooltip />
        <el-table-column label="意图" width="110">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ intentLabel(row.intent) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="召回" width="90">
          <template #default="{ row }">{{ row.productCount }} 件</template>
        </el-table-column>
        <el-table-column label="回答" width="100">
          <template #default="{ row }">{{ row.answerLength }} 字</template>
        </el-table-column>
        <el-table-column prop="tokenCount" label="Token" width="90" />
        <el-table-column label="用户" width="120">
          <template #default="{ row }"><span class="muted">{{ row.username }}</span></template>
        </el-table-column>
        <el-table-column label="时间" width="160">
          <template #default="{ row }"><span class="muted">{{ fmt(row.createdTime) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openSteps(row)">执行步骤</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination v-if="total > 0" class="pager" background layout="prev, pager, next, total"
                     :total="total" :page-size="pageSize" v-model:current-page="pageNum" @current-change="load" />
    </el-card>

    <el-drawer v-model="drawer" :title="`Run #${detail?.runId || ''} 执行步骤`" size="480px">
      <div v-loading="stepLoading">
        <el-timeline v-if="detail">
          <el-timeline-item v-for="s in detail.steps" :key="s.index" :timestamp="`Step ${s.index}`"
                            placement="top" type="primary">
            <div style="font-weight: 500; color: #0f172a; margin-bottom: 4px">{{ s.name }}</div>
            <div style="font-size: 13px; color: #334155; line-height: 1.7">{{ s.summary }}</div>
            <div class="muted" style="margin-top: 4px">{{ s.detail }}</div>
          </el-timeline-item>
        </el-timeline>
        <div v-else class="empty-hint">加载中…</div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getAiRunPage, getAiRunSteps } from '../../api/adminAi'

const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const intent = ref(null)
const loading = ref(false)
const drawer = ref(false)
const detail = ref(null)
const stepLoading = ref(false)

const INTENTS = { find_product: '找商品', compare: '参数对比', recommend: '选购建议', chat: '闲聊' }
const intentLabel = (v) => INTENTS[v] || v || '-'

function fmt(v) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '-'
}

async function load() {
  loading.value = true
  try {
    const data = await getAiRunPage({
      intent: intent.value || undefined,
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

async function openSteps(row) {
  drawer.value = true
  stepLoading.value = true
  detail.value = null
  try {
    detail.value = await getAiRunSteps(row.id)
  } finally {
    stepLoading.value = false
  }
}

onMounted(load)
</script>
