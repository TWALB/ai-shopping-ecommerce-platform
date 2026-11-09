<template>
  <div class="ai-page">
    <div class="ai-head">
      <div>
        <h3>商品知识切片</h3>
        <p>切片决定模型"看得见什么"。当前策略为按字段切分：一个字段（名称/标题/关键词/标签/简介/价格/每个参数）作为一个切片，与向量化文本口径一致。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="reload">刷新</el-button>
      </div>
    </div>

    <div class="stat-row">
      <div class="stat-box">
        <div class="label">当前切片数</div>
        <div class="value">{{ chunks.length }}<small>条</small></div>
        <div class="hint">{{ productId ? '单个商品' : '全部商品（最多展示 200 条）' }}</div>
      </div>
      <div class="stat-box">
        <div class="label">平均长度</div>
        <div class="value">{{ avgLength }}<small>字</small></div>
        <div class="hint">过短信息量不足，过长稀释相似度</div>
      </div>
      <div class="stat-box">
        <div class="label">覆盖商品</div>
        <div class="value">{{ summary.length }}<small>件</small></div>
        <div class="hint">已建立知识切片的商品数</div>
      </div>
      <div class="stat-box">
        <div class="label">最长切片</div>
        <div class="value">{{ maxLength }}<small>字</small></div>
        <div class="hint">通常为商品简介或参数值</div>
      </div>
    </div>

    <el-card shadow="never">
      <div class="tool-row">
        <el-select v-model="productId" filterable clearable placeholder="选择商品（不选则看全部）"
                   style="width: 340px" @change="loadChunks">
          <el-option v-for="s in summary" :key="s.productId" :label="s.productName" :value="s.productId" />
        </el-select>
        <span class="spacer" />
        <span class="muted">共 {{ summary.length }} 件商品，合计 {{ totalChunks }} 条切片</span>
      </div>

      <div v-loading="loading">
        <div v-if="!chunks.length" class="empty-hint">暂无切片数据</div>
        <div v-for="(c, i) in chunks" :key="i" class="chunk-card">
          <div class="chunk-type">{{ c.type }} · {{ c.productName }}</div>
          <div class="chunk-body">{{ c.content }}</div>
          <div class="chunk-meta">商品 ID {{ c.productId }} · {{ c.length }} 字</div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getAiChunks, getChunkSummary } from '../../api/adminAi'

const route = useRoute()
const chunks = ref([])
const summary = ref([])
const productId = ref(null)
const loading = ref(false)

const totalChunks = computed(() => summary.value.reduce((s, i) => s + (i.chunkCount || 0), 0))
const avgLength = computed(() => {
  if (!chunks.value.length) return 0
  return Math.round(chunks.value.reduce((s, c) => s + c.length, 0) / chunks.value.length)
})
const maxLength = computed(() => chunks.value.reduce((m, c) => Math.max(m, c.length), 0))

async function loadChunks() {
  loading.value = true
  try {
    chunks.value = await getAiChunks({ productId: productId.value || undefined })
  } finally {
    loading.value = false
  }
}

async function reload() {
  summary.value = await getChunkSummary()
  await loadChunks()
}

onMounted(async () => {
  const q = route.query.productId
  if (q) productId.value = Number(q)
  await reload()
})
</script>
