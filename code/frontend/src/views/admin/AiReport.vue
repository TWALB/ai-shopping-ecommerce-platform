<template>
  <div class="ai-page">
    <div class="ai-head">
      <div>
        <h3>AI 运营增长报告</h3>
        <p>把导购当作一个运营活动来看：多少人用、问了多少、推荐了什么、最终有没有下单。转化率 = 用过导购且下过单的用户 / 用过导购的用户。</p>
      </div>
      <div class="head-actions">
        <el-radio-group v-model="days" size="small" @change="load">
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
          <el-radio-button :value="90">近 90 天</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <div class="stat-row" v-loading="loading">
      <div class="stat-box">
        <div class="label">导购会话</div>
        <div class="value">{{ data.sessionCount }}<small>个</small></div>
        <div class="hint">统计周期内新建会话</div>
      </div>
      <div class="stat-box">
        <div class="label">用户提问</div>
        <div class="value">{{ data.questionCount }}<small>次</small></div>
        <div class="hint">平均每会话 {{ data.avgTurns }} 轮</div>
      </div>
      <div class="stat-box">
        <div class="label">使用用户</div>
        <div class="value">{{ data.chatUsers }}<small>人</small></div>
        <div class="hint">期间发起过提问</div>
      </div>
      <div class="stat-box">
        <div class="label">下单转化率</div>
        <div class="value">{{ data.conversionRate }}<small>%</small></div>
        <div class="hint">{{ data.convertedUsers }} / {{ data.chatUsers }} 人下过单</div>
      </div>
    </div>

    <div class="stat-row">
      <div class="stat-box">
        <div class="label">导购运行次数</div>
        <div class="value">{{ data.runCount }}<small>次</small></div>
        <div class="hint">每次助手回答计一次</div>
      </div>
      <div class="stat-box">
        <div class="label">推荐商品</div>
        <div class="value">{{ data.recommendedProducts }}<small>款</small></div>
        <div class="hint">回答中出现过的不同商品</div>
      </div>
      <div class="stat-box">
        <div class="label">知识库覆盖</div>
        <div class="value">{{ data.vectorizedProducts }}<small>/ {{ data.knowledgeProducts }}</small></div>
        <div class="hint">已建立向量的商品 / 全部商品</div>
      </div>
      <div class="stat-box">
        <div class="label">平台下单用户</div>
        <div class="value">{{ data.orderUsers }}<small>人</small></div>
        <div class="hint">全平台累计</div>
      </div>
    </div>

    <el-card shadow="never">
      <template #header><b>每日提问与回答</b></template>
      <div ref="chartRef" class="trend-chart" />
    </el-card>

    <el-card shadow="never">
      <template #header><b>意图分布</b></template>
      <div v-if="data.intents?.length">
        <div v-for="i in data.intents" :key="i.intent" class="intent-row">
          <span class="intent-name">{{ i.label }}</span>
          <el-progress :percentage="intentShare(i.count)" :stroke-width="10" :show-text="false" color="#2563eb" style="flex: 1" />
          <span class="intent-count">{{ i.count }} 次</span>
        </div>
      </div>
      <div v-else class="empty-hint">统计周期内暂无回答记录</div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { getAiReport } from '../../api/adminAi'

const days = ref(7)
const loading = ref(false)
const chartRef = ref(null)
let chart = null

const data = ref({
  sessionCount: 0, questionCount: 0, runCount: 0, avgTurns: 0,
  chatUsers: 0, orderUsers: 0, convertedUsers: 0, conversionRate: 0,
  recommendedProducts: 0, knowledgeProducts: 0, vectorizedProducts: 0,
  trend: [], intents: []
})

const intentTotal = computed(() => (data.value.intents || []).reduce((s, i) => s + i.count, 0))
const intentShare = (count) => (intentTotal.value ? Math.round((count * 100) / intentTotal.value) : 0)

function renderChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const trend = data.value.trend || []
  chart.setOption({
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    tooltip: { trigger: 'axis' },
    legend: { data: ['提问', '回答'], right: 0, top: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 12 } },
    xAxis: {
      type: 'category',
      data: trend.map((t) => t.date.slice(5)),
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisLabel: { color: '#94a3b8', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#f1f5f9' } },
      axisLabel: { color: '#94a3b8', fontSize: 11 }
    },
    series: [
      { name: '提问', type: 'line', smooth: true, symbolSize: 6, data: trend.map((t) => t.questions),
        itemStyle: { color: '#2563eb' }, areaStyle: { color: 'rgba(37,99,235,0.08)' } },
      { name: '回答', type: 'line', smooth: true, symbolSize: 6, data: trend.map((t) => t.answers),
        lineStyle: { type: 'dashed' }, itemStyle: { color: '#16a34a' } }
    ]
  })
}

async function load() {
  loading.value = true
  try {
    data.value = await getAiReport({ days: days.value })
    await nextTick()
    renderChart()
  } finally {
    loading.value = false
  }
}

function onResize() {
  chart?.resize()
}

onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.trend-chart { height: 260px; width: 100%; }
.intent-row { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.intent-name { width: 90px; font-size: 12px; color: #475569; }
.intent-count { width: 70px; text-align: right; font-size: 12px; color: #64748b; }
</style>
