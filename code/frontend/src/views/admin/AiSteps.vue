<template>
  <div class="ai-page">
    <div class="ai-head">
      <div>
        <h3>Agent Step 执行步骤</h3>
        <p>把一次运行拆成四步回放：用户提问 → 意图识别 → 商品召回 → 回答生成。步骤由运行记录反推，用于答辩演示"导购到底做了什么"。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="steps-layout">
      <el-card shadow="never" class="run-list">
        <template #header><b>最近运行</b></template>
        <div v-loading="loading">
          <div v-for="r in rows" :key="r.id" class="run-item" :class="{ active: current?.id === r.id }" @click="selectRun(r)">
            <div class="run-q">{{ r.question || '（未找到提问）' }}</div>
            <div class="run-meta">
              <span class="mono">#{{ r.id }}</span>
              <span>{{ intentLabel(r.intent) }}</span>
              <span>{{ r.productCount }} 件</span>
              <span>{{ fmt(r.createdTime) }}</span>
            </div>
          </div>
          <div v-if="!rows.length" class="empty-hint">暂无运行记录，先在商城端发起一次导购对话</div>
        </div>
      </el-card>

      <el-card shadow="never" class="step-detail">
        <template #header>
          <div class="detail-head">
            <b>执行步骤</b>
            <span v-if="detail" class="muted">Run #{{ detail.runId }} · {{ fmt(detail.createdTime) }}</span>
          </div>
        </template>
        <div v-loading="stepLoading">
          <div v-if="!detail" class="empty-hint">选择左侧任意一条运行记录查看步骤</div>
          <el-timeline v-else>
            <el-timeline-item v-for="s in detail.steps" :key="s.index" :timestamp="`Step ${s.index}`"
                              placement="top" type="primary" size="large">
              <div class="step-name">{{ s.name }}</div>
              <div class="step-summary">{{ s.summary }}</div>
              <div class="muted" style="margin-top: 4px">{{ s.detail }}</div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getAiRunPage, getAiRunSteps } from '../../api/adminAi'

const rows = ref([])
const current = ref(null)
const detail = ref(null)
const loading = ref(false)
const stepLoading = ref(false)

const INTENTS = { find_product: '找商品', compare: '参数对比', recommend: '选购建议', chat: '闲聊' }
const intentLabel = (v) => INTENTS[v] || v || '-'

function fmt(v) {
  return v ? String(v).replace('T', ' ').slice(5, 16) : '-'
}

async function load() {
  loading.value = true
  try {
    const data = await getAiRunPage({ pageNum: 1, pageSize: 15 })
    rows.value = data.list
    if (rows.value.length && !current.value) {
      await selectRun(rows.value[0])
    }
  } finally {
    loading.value = false
  }
}

async function selectRun(run) {
  current.value = run
  stepLoading.value = true
  try {
    detail.value = await getAiRunSteps(run.id)
  } finally {
    stepLoading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.steps-layout { display: flex; gap: 12px; align-items: flex-start; }
.run-list { width: 400px; flex-shrink: 0; }
.step-detail { flex: 1; min-height: 420px; }
.run-item { padding: 10px 10px; border-radius: 8px; cursor: pointer; border-bottom: 1px solid #f1f5f9; }
.run-item:hover { background: #f8fafc; }
.run-item.active { background: #eef4ff; box-shadow: inset 2px 0 0 #2563eb; }
.run-q { font-size: 13px; color: #0f172a; line-height: 1.6; margin-bottom: 6px;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.run-meta { display: flex; gap: 10px; font-size: 11px; color: #94a3b8; }
.detail-head { display: flex; align-items: baseline; gap: 10px; }
.step-name { font-size: 14px; font-weight: 500; color: #0f172a; }
.step-summary { font-size: 13px; color: #334155; line-height: 1.7; margin-top: 4px; }
</style>
