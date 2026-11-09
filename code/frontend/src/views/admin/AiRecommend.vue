<template>
  <div class="ai-page">
    <div class="ai-head">
      <div>
        <h3>AI 推荐商品</h3>
        <p>按被导购推荐次数聚合。次数高但销量低，说明推荐了却没人买 —— 要么价格不合适，要么详情页没承接住。</p>
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
        <div class="label">被推荐商品</div>
        <div class="value">{{ rows.length }}<small>款</small></div>
        <div class="hint">统计周期内出现在导购回答中的商品</div>
      </div>
      <div class="stat-box">
        <div class="label">推荐总次数</div>
        <div class="value">{{ totalCount }}<small>次</small></div>
        <div class="hint">同一商品被多次推荐累计计数</div>
      </div>
      <div class="stat-box">
        <div class="label">Top1 占比</div>
        <div class="value">{{ topShare }}<small>%</small></div>
        <div class="hint">首位商品推荐次数 / 总次数</div>
      </div>
      <div class="stat-box">
        <div class="label">数据来源</div>
        <div class="value" style="font-size: 14px; padding-top: 6px">chat_message.product_ids</div>
        <div class="hint">助手回答中记录的商品 ID 集合</div>
      </div>
    </div>

    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column label="#" width="56">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>
        <el-table-column prop="productName" label="商品" min-width="220" show-overflow-tooltip />
        <el-table-column label="价格" width="110">
          <template #default="{ row }">¥{{ row.price }}</template>
        </el-table-column>
        <el-table-column prop="sales" label="累计销量" width="110" />
        <el-table-column label="被推荐" width="240">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 8px">
              <el-progress :percentage="share(row.recommendCount)" :stroke-width="10" :show-text="false"
                           color="#2563eb" style="flex: 1" />
              <span class="mono">{{ row.recommendCount }} 次</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="最近推荐" width="160">
          <template #default="{ row }"><span class="muted">{{ fmt(row.lastTime) }}</span></template>
        </el-table-column>
        <el-table-column label="转化提示" min-width="180">
          <template #default="{ row }">
            <span class="muted">{{ hint(row) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!rows.length && !loading" class="empty-hint">统计周期内还没有推荐记录</div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getAiRecommendTop } from '../../api/adminAi'

const rows = ref([])
const days = ref(30)
const loading = ref(false)

const totalCount = computed(() => rows.value.reduce((s, r) => s + (r.recommendCount || 0), 0))
const topShare = computed(() => {
  if (!totalCount.value || !rows.value.length) return 0
  return Math.round((rows.value[0].recommendCount * 100) / totalCount.value)
})

function share(count) {
  const max = rows.value[0]?.recommendCount || 1
  return Math.round((count * 100) / max)
}

function fmt(v) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '-'
}

function hint(row) {
  if (!row.recommendCount) return '-'
  if (row.sales === 0) return '有推荐无销量，检查价格与详情页'
  if (row.recommendCount >= 5 && row.sales < 10) return '推荐多、转化弱，可考虑优化卖点'
  return '推荐与销量基本匹配'
}

async function load() {
  loading.value = true
  try {
    rows.value = await getAiRecommendTop({ days: days.value, limit: 20 })
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
