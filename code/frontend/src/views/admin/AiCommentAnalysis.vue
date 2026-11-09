<template>
  <div class="ai-page">
    <div class="ai-head">
      <div>
        <h3>AI 评价分析</h3>
        <p>统计口径为规则聚合（评分分布 + 差评分词），不依赖大模型，离线也能演示。差评关键词可直接反馈给商家改进商品。</p>
      </div>
      <div class="head-actions">
        <el-radio-group v-model="days" size="small" @change="load">
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
          <el-radio-button :value="90">近 90 天</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <div class="stat-row">
      <div class="stat-box">
        <div class="label">评价总数</div>
        <div class="value">{{ data.total }}<small>条</small></div>
        <div class="hint">不含已判定违规删除</div>
      </div>
      <div class="stat-box">
        <div class="label">平均评分</div>
        <div class="value">{{ data.avgScore }}<small>/ 5</small></div>
        <div class="hint">商品评分均值</div>
      </div>
      <div class="stat-box">
        <div class="label">好评率</div>
        <div class="value">{{ data.goodRate }}<small>%</small></div>
        <div class="hint">评分 ≥ 4 星占比</div>
      </div>
      <div class="stat-box">
        <div class="label">差评待跟进</div>
        <div class="value">{{ data.negativeCount }}<small>条</small></div>
        <div class="hint">评分 ≤ 2 星</div>
      </div>
    </div>

    <el-card shadow="never" v-loading="loading">
      <el-row :gutter="24">
        <el-col :span="10">
          <div class="section-title">评分分布</div>
          <div v-for="d in data.distribution" :key="d.score" class="dist-row">
            <span class="dist-label">{{ d.score }} 星</span>
            <el-progress :percentage="d.rate" :stroke-width="12" :show-text="false"
                         :color="d.score >= 4 ? '#16a34a' : d.score === 3 ? '#f59e0b' : '#dc2626'" style="flex: 1" />
            <span class="dist-count">{{ d.count }}</span>
          </div>
          <div v-if="!data.distribution?.length" class="empty-hint">暂无评价</div>

          <div class="section-title" style="margin-top: 22px">未回复评价</div>
          <div class="muted">统计周期内还有 {{ data.unrepliedCount || 0 }} 条评价未回复</div>
        </el-col>

        <el-col :span="14">
          <div class="section-title">差评关键词</div>
          <div v-if="data.keywords?.length" class="kw-wrap">
            <el-tag v-for="k in data.keywords" :key="k.word" type="danger" effect="light"
                    :size="k.count > 2 ? 'large' : 'default'" style="margin: 0 8px 8px 0">
              {{ k.word }} · {{ k.count }}
            </el-tag>
          </div>
          <div v-else class="muted">暂无差评，或差评内容过短无法提取关键词</div>

          <div class="section-title" style="margin-top: 22px">最新差评</div>
          <el-table :data="data.negatives || []" size="small" v-if="data.negatives?.length">
            <el-table-column prop="productName" label="商品" min-width="140" show-overflow-tooltip />
            <el-table-column prop="score" label="评分" width="70" />
            <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
            <el-table-column label="回复" width="70">
              <template #default="{ row }">
                <el-tag :type="row.replied ? 'success' : 'warning'" size="small">
                  {{ row.replied ? '已回' : '未回' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <div v-else class="muted">统计周期内没有差评</div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getAiCommentAnalysis } from '../../api/adminAi'

const days = ref(30)
const loading = ref(false)
const data = ref({ total: 0, avgScore: 0, goodRate: 0, negativeCount: 0, distribution: [], keywords: [], negatives: [] })

async function load() {
  loading.value = true
  try {
    data.value = await getAiCommentAnalysis({ days: days.value })
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.section-title { font-size: 13px; font-weight: 500; color: #0f172a; margin-bottom: 12px; }
.dist-row { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.dist-label { width: 40px; font-size: 12px; color: #64748b; }
.dist-count { width: 40px; text-align: right; font-size: 12px; color: #64748b; }
.kw-wrap { min-height: 60px; }
</style>
