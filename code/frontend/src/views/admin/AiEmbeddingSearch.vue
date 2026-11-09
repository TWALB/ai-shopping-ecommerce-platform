<template>
  <div class="ai-page">
    <div class="ai-head">
      <div>
        <h3>Embedding 检索</h3>
        <p>与线上导购完全相同的链路：意图识别 → 槽位抽取 → 向量/本地召回。可用来验证"知识库同步是否真的生效"，也是排查推荐不准的第一现场。</p>
      </div>
    </div>

    <el-card shadow="never">
      <div class="tool-row">
        <el-input v-model="query" placeholder="例如：5000以内拍照好的手机，和续航强的对比"
                  style="width: 420px" clearable @keyup.enter="doSearch" />
        <el-input-number v-model="topK" :min="1" :max="5" :step="1" controls-position="right" style="width: 110px" />
        <el-switch v-model="useVector" active-text="优先向量" inactive-text="强制本地" />
        <el-button type="primary" :loading="loading" @click="doSearch">检索</el-button>
        <span class="spacer" />
        <span class="muted">TopN 与线上一致，最多 5 条</span>
      </div>

      <el-alert v-if="!result" type="info" :closable="false" show-icon
                title="输入一句真实用户会说的话，看导购会召回哪些商品、走的是哪条通路" />

      <template v-else>
        <div class="tool-row" style="margin-top: 4px">
          <el-tag :type="result.mode === 'embedding' ? 'success' : 'warning'" effect="light">
            {{ result.modeLabel }}
          </el-tag>
          <el-tag type="info" effect="plain">意图：{{ result.intentLabel }}</el-tag>
          <el-tag type="info" effect="plain">耗时 {{ result.latencyMs }} ms</el-tag>
          <el-tag v-for="(v, k) in filledSlots" :key="k" type="info" effect="plain">{{ slotLabel(k) }}：{{ v }}</el-tag>
        </div>
        <el-alert :title="result.note" type="info" :closable="false" show-icon style="margin-bottom: 12px" />

        <el-table :data="result.items" stripe>
          <el-table-column label="#" width="56">
            <template #default="{ $index }">{{ $index + 1 }}</template>
          </el-table-column>
          <el-table-column prop="productName" label="商品" min-width="220" />
          <el-table-column label="价格" width="110">
            <template #default="{ row }">¥{{ row.price }}</template>
          </el-table-column>
          <el-table-column prop="sales" label="销量" width="90" />
          <el-table-column :label="result.mode === 'embedding' ? '余弦相似度' : '相关度'" width="220">
            <template #default="{ row }">
              <el-progress :percentage="pct(row.similarity)" :stroke-width="10"
                           :color="result.mode === 'embedding' ? '#2563eb' : '#f59e0b'" />
            </template>
          </el-table-column>
          <el-table-column label="商品 ID" width="100">
            <template #default="{ row }"><span class="mono">{{ row.productId }}</span></template>
          </el-table-column>
        </el-table>

        <div v-if="!result.items.length" class="empty-hint">没有召回任何商品 — 对导购来说这是一次「弃答」，模型会提示用户调整需求</div>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { aiKnowledgeSearch } from '../../api/adminAi'

const query = ref('5000以内拍照好的手机')
const topK = ref(5)
const useVector = ref(true)
const loading = ref(false)
const result = ref(null)

const filledSlots = computed(() => {
  const slots = result.value?.slots || {}
  const out = {}
  Object.entries(slots).forEach(([k, v]) => {
    if (v !== null && v !== '' && v !== 0) out[k] = v
  })
  return out
})

const SLOT_LABELS = { category: '品类', budget: '预算', keyword: '关注点', brand: '品牌' }
const slotLabel = (k) => SLOT_LABELS[k] || k

const pct = (v) => Math.max(0, Math.min(100, Math.round((v || 0) * 100)))

async function doSearch() {
  if (!query.value.trim()) return
  loading.value = true
  try {
    result.value = await aiKnowledgeSearch({
      query: query.value.trim(),
      topK: topK.value,
      vector: useVector.value
    })
  } finally {
    loading.value = false
  }
}
</script>
