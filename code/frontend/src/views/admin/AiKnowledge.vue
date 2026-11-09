<template>
  <div class="ai-page">
    <div class="ai-head">
      <div>
        <h3>商品知识库</h3>
        <p>导购可检索的知识全部来自这里：商品基础信息 + 参数属性。向量化后写入 Redis（product:embedding:{id}，TTL 7 天），供 Embedding 召回使用。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="syncing" @click="refreshAll">刷新</el-button>
        <el-button type="primary" :loading="syncing" @click="syncAll">全量重建向量</el-button>
      </div>
    </div>

    <div class="stat-row">
      <div class="stat-box">
        <div class="label">知识库商品</div>
        <div class="value">{{ total }}<small>件</small></div>
        <div class="hint">上架与下架商品均参与建库</div>
      </div>
      <div class="stat-box">
        <div class="label">已建立向量</div>
        <div class="value">{{ vectorizedCount }}<small>件</small></div>
        <div class="hint">Redis 中存在向量键</div>
      </div>
      <div class="stat-box">
        <div class="label">待同步</div>
        <div class="value">{{ total - vectorizedCount }}<small>件</small></div>
        <div class="hint">新增或修改参数后需重建</div>
      </div>
      <div class="stat-box">
        <div class="label">切片总数</div>
        <div class="value">{{ chunkCount }}<small>条</small></div>
        <div class="hint">按字段切分，一条字段一个切片</div>
      </div>
    </div>

    <el-card shadow="never">
      <div class="tool-row">
        <el-input v-model="keyword" placeholder="商品名称" clearable style="width: 220px"
                  @keyup.enter="search" @clear="search" />
        <el-select v-model="vectorized" placeholder="向量状态" clearable style="width: 140px" @change="search">
          <el-option label="已同步" :value="true" />
          <el-option label="未同步" :value="false" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <span class="spacer" />
        <span class="muted">向量通路：{{ vectorReady ? '已就绪' : '未就绪（未配置 Key 或库为空）' }}</span>
      </div>

      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="64" />
        <el-table-column prop="productName" label="商品" min-width="180" show-overflow-tooltip />
        <el-table-column label="分类 / 品牌" min-width="140">
          <template #default="{ row }">
            <span class="muted">{{ row.categoryName || '-' }} / {{ row.brandName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="价格" width="100">
          <template #default="{ row }">¥{{ row.price }}</template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="切片" width="70" />
        <el-table-column prop="textLength" label="知识文本" width="90">
          <template #default="{ row }">{{ row.textLength }} 字</template>
        </el-table-column>
        <el-table-column label="向量状态" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.vectorized" type="success" size="small">已同步 · {{ row.vectorDim }} 维</el-tag>
            <el-tag v-else type="info" size="small">未同步</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="知识预览" min-width="220">
          <template #default="{ row }">
            <div class="preview-cell" :title="row.preview">{{ row.preview }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openChunks(row)">切片明细</el-button>
            <el-button link type="primary" :loading="row._syncing" @click="syncOne(row)">重建</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination v-if="total > 0" class="pager" background layout="prev, pager, next, total"
                     :total="total" :page-size="pageSize" v-model:current-page="pageNum" @current-change="load" />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getKnowledgeProducts, getChunkSummary, syncAiKnowledge } from '../../api/adminAi'

const router = useRouter()
const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const keyword = ref('')
const vectorized = ref(null)
const loading = ref(false)
const syncing = ref(false)
const vectorReady = ref(false)
const vectorizedCount = ref(0)
const chunkCount = ref(0)

async function load() {
  loading.value = true
  try {
    const data = await getKnowledgeProducts({
      keyword: keyword.value || undefined,
      vectorized: vectorized.value === null ? undefined : vectorized.value,
      pageNum: pageNum.value,
      pageSize
    })
    rows.value = data.list.map((r) => ({ ...r, _syncing: false }))
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function loadSummary() {
  const list = await getChunkSummary()
  chunkCount.value = list.reduce((s, i) => s + (i.chunkCount || 0), 0)
  vectorizedCount.value = list.filter((i) => i.vectorized).length
  vectorReady.value = vectorizedCount.value > 0
}

function search() {
  pageNum.value = 1
  load()
}

async function refreshAll() {
  await Promise.all([load(), loadSummary()])
}

async function syncAll() {
  await ElMessageBox.confirm('将遍历全部商品调用 Embedding 重建向量，未配置 api-key 时会失败。确认执行？', '全量重建', { type: 'warning' })
  syncing.value = true
  try {
    const r = await syncAiKnowledge({ full: true })
    ElMessage.success(`重建完成：本次同步 ${r.synced} 条，库中共 ${r.total} 条向量`)
    await refreshAll()
  } finally {
    syncing.value = false
  }
}

async function syncOne(row) {
  row._syncing = true
  try {
    const r = await syncAiKnowledge({ productId: row.id })
    ElMessage.success(r.synced ? '向量已更新' : '未生成向量，请确认 api-key 配置')
    await refreshAll()
  } finally {
    row._syncing = false
  }
}

function openChunks(row) {
  router.push({ path: '/admin/ai/knowledge/chunks', query: { productId: row.id } })
}

onMounted(refreshAll)
</script>
