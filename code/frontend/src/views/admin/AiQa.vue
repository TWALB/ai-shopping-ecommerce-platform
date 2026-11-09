<template>
  <div class="ai-page">
    <div class="ai-head">
      <div>
        <h3>AI 商品问答</h3>
        <p>把导购真实对话整理成问答对，用于沉淀高频问题、发现回答缺陷。关键词检索命中提问或回答内容。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <div class="tool-row">
        <el-input v-model="keyword" placeholder="搜索提问或回答内容" clearable style="width: 300px"
                  @keyup.enter="search" @clear="search" />
        <el-button type="primary" @click="search">搜索</el-button>
        <span class="spacer" />
        <span class="muted">共 {{ total }} 组问答（最多回溯最近 500 条回答）</span>
      </div>

      <div v-loading="loading">
        <div v-if="!rows.length && !loading" class="empty-hint">暂无问答数据</div>
        <div v-for="item in rows" :key="item.answerId" class="qa-item">
          <div class="q">{{ item.question }}</div>
          <div class="a">{{ expanded[item.answerId] ? item.answer : trim(item.answer) }}</div>
          <div class="meta">
            <span>会话 #{{ item.sessionId }}</span>
            <span>{{ item.username }}</span>
            <el-tag size="small" type="info">{{ intentLabel(item.intent) }}</el-tag>
            <span>推荐 {{ item.productCount }} 件</span>
            <span>{{ fmt(item.createdTime) }}</span>
            <el-button v-if="(item.answer || '').length > 120" link type="primary"
                       @click="expanded[item.answerId] = !expanded[item.answerId]">
              {{ expanded[item.answerId] ? '收起' : '展开全文' }}
            </el-button>
          </div>
        </div>
      </div>

      <el-pagination v-if="total > 0" class="pager" background layout="prev, pager, next, total"
                     :total="total" :page-size="pageSize" v-model:current-page="pageNum" @current-change="load" />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { getAiQaPage } from '../../api/adminAi'

const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const keyword = ref('')
const loading = ref(false)
const expanded = reactive({})

const INTENTS = { find_product: '找商品', compare: '参数对比', recommend: '选购建议', chat: '闲聊' }
const intentLabel = (v) => INTENTS[v] || v || '-'

function fmt(v) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '-'
}

function trim(text) {
  const s = text || ''
  return s.length > 120 ? s.slice(0, 120) + '…' : s
}

async function load() {
  loading.value = true
  try {
    const data = await getAiQaPage({
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

onMounted(load)
</script>
