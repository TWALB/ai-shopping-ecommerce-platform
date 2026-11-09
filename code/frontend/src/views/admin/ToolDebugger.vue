<template>
  <div class="ai-page">
    <div class="ai-head">
      <div>
        <h3>{{ domain === 'biz' ? '业务工具调试' : '商品工具调试' }}</h3>
        <p>
          直接执行工具本身，不经过模型——验证的是「这个工具拿到的数据对不对」，不是「模型会不会选它」。
          参数表单由工具登记时上的 JSON Schema 自动生成，不用为每个工具手写页面。
        </p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="load">刷新工具</el-button>
      </div>
    </div>

    <div class="debug-layout">
      <el-card shadow="never" class="tool-list">
        <div class="list-head">工具（{{ tools.length }}）</div>
        <div v-for="t in tools" :key="t.id" class="tool-item" :class="{ active: current && current.toolCode === t.toolCode }"
             @click="select(t)">
          <div class="tool-name">
            <span>{{ t.toolName }}</span>
            <el-tag v-if="t.enabled !== 1" size="small" type="info" effect="plain">停用</el-tag>
          </div>
          <div class="tool-code mono">{{ t.toolCode }}</div>
        </div>
        <div v-if="!tools.length && !loading" class="muted empty">该业务域暂无工具</div>
      </el-card>

      <div class="debug-main">
        <el-card v-if="!current" shadow="never">
          <div class="empty-tip">从左侧选择一个工具开始调试</div>
        </el-card>

        <template v-else>
          <el-card shadow="never">
            <div class="cur-head">
              <div>
                <div class="cur-title">
                  {{ current.toolName }}
                  <span class="mono cur-code">{{ current.toolCode }}</span>
                  <el-tag v-if="current.builtin === 1" size="small" type="info" effect="plain">内置</el-tag>
                  <el-tag v-if="current.enabled !== 1" size="small" type="warning" effect="plain">已停用</el-tag>
                </div>
                <div class="cur-desc">{{ current.description }}</div>
              </div>
              <div class="cur-actions">
                <el-switch :model-value="rawMode" size="small" active-text="原始 JSON" @change="toggleRaw" />
              </div>
            </div>

            <el-form v-if="!rawMode" label-width="96px" label-position="left" class="param-form">
              <el-form-item v-for="(spec, name) in schemaProps" :key="name">
                <template #label>
                  <span class="param-label">
                    {{ name }}
                    <span v-if="required.includes(name)" class="req">*</span>
                  </span>
                </template>
                <el-input-number v-if="spec.type === 'number' || spec.type === 'integer'"
                                 v-model="params[name]" :min="0" :controls="false" style="width: 200px" />
                <el-input v-else v-model="params[name]" placeholder="请输入" style="width: 320px" />
                <div class="param-desc">{{ spec.description || spec.type }}</div>
              </el-form-item>
              <div v-if="!Object.keys(schemaProps).length" class="muted">该工具未声明入参</div>
            </el-form>

            <el-input v-else v-model="rawText" type="textarea" :autosize="{ minRows: 6, maxRows: 14 }"
                      class="mono" />

            <div class="param-actions">
              <el-button type="primary" :loading="running" @click="run">发送请求</el-button>
              <el-button @click="fillSample">用示例填充</el-button>
              <el-button @click="resetParams">清空参数</el-button>
              <span class="spacer" />
              <span class="muted">{{ current.resultHint }}</span>
            </div>
          </el-card>

          <el-card shadow="never" class="result-card" v-loading="running">
            <template #header>
              <div class="res-head">
                <b>调用结果</b>
                <template v-if="result">
                  <el-tag :type="result.success ? 'success' : 'danger'" size="small" effect="plain">
                    {{ result.success ? '成功' : '失败' }}
                  </el-tag>
                  <span class="muted">{{ result.costMs }} ms · trace {{ result.traceId }}</span>
                </template>
                <span v-else class="muted">尚未调用</span>
              </div>
            </template>

            <div v-if="!result" class="empty-tip">
              点「发送请求」后，这里会给出耗时、调用号和完整返回体。失败时同时显示后端抛出的人话异常。
            </div>

            <el-tabs v-else v-model="resTab">
              <el-tab-pane label="响应" name="resp">
                <el-alert v-if="!result.success" type="error" :closable="false" show-icon :title="result.error" class="mb" />
                <pre class="mono block">{{ pretty(result.response) }}</pre>
              </el-tab-pane>
              <el-tab-pane label="请求参数" name="req">
                <pre class="mono block">{{ pretty(result.request) }}</pre>
              </el-tab-pane>
              <el-tab-pane label="返回结构说明" name="hint">
                <p class="hint-text">{{ current.resultHint || '该工具未填写返回结构说明' }}</p>
              </el-tab-pane>
            </el-tabs>
          </el-card>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAiTools, debugAiTool } from '../../api/adminAi'

const route = useRoute()

const tools = ref([])
const current = ref(null)
const loading = ref(false)
const running = ref(false)

const rawMode = ref(false)
const rawText = ref('{}')
const params = reactive({})

const result = ref(null)
const resTab = ref('resp')

const domain = computed(() => route.meta.domain || 'product')

/** Schema 里的 properties 与 required，用来生成表单 */
const schemaProps = computed(() => parseJson(current.value?.paramsSchema)?.properties || {})
const required = computed(() => parseJson(current.value?.paramsSchema)?.required || [])

function parseJson(text) {
  if (!text) return {}
  try {
    return JSON.parse(text)
  } catch (e) {
    return {}
  }
}

async function load() {
  loading.value = true
  try {
    tools.value = (await getAiTools({ domain: domain.value })) || []
    const want = route.query.tool
    const hit = (want && tools.value.find((t) => t.toolCode === want)) || tools.value[0]
    if (hit) select(hit)
  } finally {
    loading.value = false
  }
}

function select(tool) {
  current.value = tool
  result.value = null
  resTab.value = 'resp'
  rawMode.value = false
  fillParams(parseJson(tool.sampleParams))
  rawText.value = tool.sampleParams || '{}'
}

function fillParams(obj) {
  Object.keys(params).forEach((k) => delete params[k])
  Object.entries(obj || {}).forEach(([k, v]) => {
    params[k] = v
  })
}

function fillSample() {
  const sample = parseJson(current.value?.sampleParams)
  fillParams(sample)
  rawText.value = current.value?.sampleParams || '{}'
  ElMessage.success('已填入示例参数')
}

function resetParams() {
  fillParams({})
  rawText.value = '{}'
  result.value = null
}

function toggleRaw(v) {
  rawMode.value = v
  if (v) {
    rawText.value = JSON.stringify(params, null, 2)
  } else {
    fillParams(parseJson(rawText.value))
  }
}

async function run() {
  let payload
  if (rawMode.value) {
    try {
      payload = JSON.parse(rawText.value || '{}')
    } catch (e) {
      ElMessage.error('参数不是合法 JSON：' + e.message)
      return
    }
  } else {
    payload = {}
    Object.entries(params).forEach(([k, v]) => {
      if (v !== '' && v !== null && v !== undefined) payload[k] = v
    })
  }

  running.value = true
  try {
    result.value = await debugAiTool(current.value.toolCode, payload)
  } finally {
    running.value = false
  }
}

function pretty(obj) {
  if (obj === undefined || obj === null) return '（空）'
  try {
    return JSON.stringify(obj, null, 2)
  } catch (e) {
    return String(obj)
  }
}

// 两个调试台复用同一组件，切路由时实例不会重建，需要手动重载
watch(() => route.meta.domain, () => load())
watch(() => route.query.tool, () => {
  const hit = tools.value.find((t) => t.toolCode === route.query.tool)
  if (hit) select(hit)
})

load()
</script>

<style scoped>
.debug-layout { display: grid; grid-template-columns: 236px minmax(0, 1fr); gap: 12px; align-items: start; }
.tool-list { border-radius: 10px; padding: 4px; }
.list-head { font-size: 12px; color: #94a3b8; padding: 8px 10px 6px; }
.tool-item {
  padding: 8px 10px; border-radius: 8px; cursor: pointer; margin-bottom: 2px;
  border: 1px solid transparent;
}
.tool-item:hover { background: #f8fafc; }
.tool-item.active { background: #eef4ff; border-color: #dbe7ff; }
.tool-name { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #0f172a; font-weight: 500; }
.tool-code { color: #94a3b8; font-size: 11px; margin-top: 2px; }
.debug-main { display: flex; flex-direction: column; gap: 12px; }
.cur-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.cur-title { display: flex; align-items: center; gap: 8px; font-size: 15px; font-weight: 600; color: #0f172a; }
.cur-code { font-size: 12px; color: #64748b; font-weight: 400; background: #f1f5f9; border-radius: 4px; padding: 1px 6px; }
.cur-desc { font-size: 12px; color: #64748b; line-height: 1.7; margin-top: 6px; max-width: 640px; }
.param-label { font-family: "SFMono-Regular", Consolas, monospace; font-size: 12px; color: #334155; }
.req { color: #dc2626; margin-left: 2px; }
.param-desc { font-size: 11px; color: #94a3b8; margin-top: 2px; width: 100%; }
.param-form :deep(.el-form-item) { margin-bottom: 12px; }
.param-actions { display: flex; align-items: center; gap: 8px; margin-top: 8px; }
.param-actions .spacer { flex: 1; }
.res-head { display: flex; align-items: center; gap: 10px; font-size: 14px; }
.empty-tip { font-size: 12px; color: #94a3b8; line-height: 1.8; padding: 8px 0; }
.block {
  background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px;
  padding: 12px; white-space: pre-wrap; line-height: 1.7; max-height: 460px; overflow: auto; margin: 0;
}
.hint-text { font-size: 13px; color: #475569; line-height: 1.8; margin: 0; }
.mb { margin-bottom: 10px; }
.empty { padding: 10px; }
</style>
