<template>
  <div class="ai-page">
    <div class="ai-head">
      <div>
        <h3>AI 模型配置</h3>
        <p>接口地址、模型名、密钥、温度都在这里改，保存后下一次调用即生效，不需要重启服务。密钥只写不读：页面显示的永远是掩码，留空则沿用 application.yml。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button :loading="testing" @click="test">测试连接</el-button>
        <el-button type="primary" :loading="saving" :disabled="!dirty" @click="save">保存并生效</el-button>
      </div>
    </div>

    <el-alert v-if="state.hints && state.hints.length" type="warning" :closable="false" show-icon>
      <template #title>
        <span v-for="(h, i) in state.hints" :key="i">{{ i > 0 ? '；' : '' }}{{ h }}</span>
      </template>
    </el-alert>

    <div class="stat-row">
      <div class="stat-box">
        <div class="label">对话模型</div>
        <div class="value sm">{{ state.model || '—' }}</div>
        <div class="hint">{{ state.baseUrl }}</div>
      </div>
      <div class="stat-box">
        <div class="label">接口可用性</div>
        <div class="value">
          <span :class="state.available ? 'ok' : 'bad'">{{ state.available ? '可用' : '未配置' }}</span>
        </div>
        <div class="hint">密钥来源：{{ state.apiKeyFromDb ? '数据库' : 'application.yml' }}</div>
      </div>
      <div class="stat-box">
        <div class="label">向量通路</div>
        <div class="value">
          <span :class="vector.ready ? 'ok' : 'bad'">{{ vector.ready ? '就绪' : '未就绪' }}</span>
        </div>
        <div class="hint">已建向量 {{ vector.vectorized }} / {{ vector.total }} 件，{{ vector.dim || 0 }} 维</div>
      </div>
      <div class="stat-box">
        <div class="label">当前温度</div>
        <div class="value">{{ state.temperature }}</div>
        <div class="hint">越小越稳定，导购建议 0.2 - 0.5</div>
      </div>
    </div>

    <el-card shadow="never" class="form-card">
      <el-form :model="form" label-width="128px" label-position="right">
        <el-form-item label="接口地址">
          <el-input v-model="form.baseUrl" placeholder="https://.../compatible-mode/v1" />
          <div class="tip">OpenAI 兼容接口根地址，不要带 /chat/completions</div>
        </el-form-item>

        <el-form-item label="API Key">
          <el-input v-model="form.apiKey" type="password" show-password
                    :placeholder="state.apiKeyMasked ? '留空则继续使用 ' + state.apiKeyMasked : '未配置密钥，填写后才能调用模型'" />
          <div class="tip">
            当前生效：<span class="mono">{{ state.apiKeyMasked || '（空）' }}</span>
            <span v-if="state.apiKeyFromDb" class="warn">· 来自数据库，接口只返回掩码</span>
            <span v-else>· 来自配置文件，写入数据库后将优先使用数据库中的值</span>
          </div>
        </el-form-item>

        <el-form-item label="对话模型">
          <el-input v-model="form.model" placeholder="qwen-plus" style="width: 260px" />
          <span class="tip inline">用于导购回答与意图识别</span>
        </el-form-item>

        <el-form-item label="向量模型">
          <el-input v-model="form.embeddingModel" placeholder="text-embedding-v4" style="width: 260px" />
          <span class="tip inline">更换后维度可能变化，必须重新全量重建向量库</span>
        </el-form-item>

        <el-form-item label="温度">
          <el-slider v-model="form.temperature" :min="0" :max="2" :step="0.1" style="width: 320px" />
          <span class="temp">{{ form.temperature }}</span>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" :disabled="!dirty" @click="save">保存并生效</el-button>
          <el-button @click="load">放弃修改</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="!hasResult && !testing" shadow="never">
      <div class="empty-tip">
        改完参数点「测试连接」：会真实发一次对话和一次向量化，分别报告耗时与维度。存量向量维度与探针维度不一致时会直接提示需要重建。
      </div>
    </el-card>

    <el-card v-if="hasResult || testing" shadow="never" v-loading="testing" element-loading-text="正在调用模型…">
      <template #header>
        <div class="res-head">
          <b>连通性自检</b>
          <el-tag v-if="hasResult" :type="result.ok ? 'success' : 'danger'" size="small" effect="plain">
            {{ result.ok ? '全部通过' : '未通过' }}
          </el-tag>
          <span v-if="hasResult" class="muted">baseUrl {{ result.baseUrl }} · {{ result.model }}</span>
        </div>
      </template>

      <div v-if="hasResult" class="res-grid">
        <div class="res-item">
          <div class="res-label">对话接口</div>
          <div class="res-val">
            <span :class="result.chatOk ? 'ok' : 'bad'">{{ result.chatOk ? '通' : '不通' }}</span>
            <span class="muted">{{ result.chatMs }} ms</span>
          </div>
          <div class="res-sub">返回：{{ result.chatSample || '（空）' }}</div>
        </div>
        <div class="res-item">
          <div class="res-label">向量接口</div>
          <div class="res-val">
            <span :class="result.embedOk ? 'ok' : 'bad'">{{ result.embedOk ? '通' : '不通' }}</span>
            <span class="muted">{{ result.embedMs }} ms</span>
          </div>
          <div class="res-sub">探针维度：{{ result.embedDim || 0 }}</div>
        </div>
        <div class="res-item">
          <div class="res-label">存量向量</div>
          <div class="res-val">{{ result.vector?.dim || 0 }} 维</div>
          <div class="res-sub">
            <span v-if="result.dimMismatch" class="bad">维度不一致，需重建向量库</span>
            <span v-else class="muted">与当前模型一致或尚无存量</span>
          </div>
        </div>
      </div>

      <el-alert v-if="hasResult && result.error" type="error" :closable="false" show-icon :title="result.error" class="mt" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAiModel, saveAiModel, testAiModel } from '../../api/adminAi'

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)

const state = ref({})
const result = ref({})
const hasResult = ref(false)

const form = reactive({
  baseUrl: '',
  apiKey: '',
  model: '',
  embeddingModel: '',
  temperature: 0.3
})

/** 只在用户真改过时才允许保存，避免空表单把库里的值覆盖成空 */
const origin = reactive({ ...form })

const vector = computed(() => state.value.vector || {})

const dirty = computed(() =>
  form.baseUrl !== origin.baseUrl ||
  form.model !== origin.model ||
  form.embeddingModel !== origin.embeddingModel ||
  form.temperature !== origin.temperature ||
  (form.apiKey || '').length > 0
)

function syncForm(data) {
  form.baseUrl = data.baseUrl || ''
  form.model = data.model || ''
  form.embeddingModel = data.embeddingModel || ''
  form.temperature = data.temperature ?? 0.3
  form.apiKey = ''
  Object.assign(origin, { ...form })
}

async function load() {
  loading.value = true
  try {
    const data = await getAiModel()
    state.value = data || {}
    syncForm(state.value)
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const data = await saveAiModel({
      'model.base_url': form.baseUrl,
      'model.name': form.model,
      'model.embedding': form.embeddingModel,
      'model.temperature': String(form.temperature),
      'model.api_key': form.apiKey
    })
    state.value = data || {}
    syncForm(state.value)
    const warns = data?.warnings || []
    if (warns.length) {
      ElMessage.warning(warns[0])
    } else {
      ElMessage.success('已保存，下一次调用即使用新参数')
    }
  } finally {
    saving.value = false
  }
}

async function test() {
  testing.value = true
  hasResult.value = true
  try {
    result.value = await testAiModel()
  } catch (e) {
    result.value = { ok: false, error: e.message || '自检失败' }
  } finally {
    testing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.stat-box .value.sm { font-size: 16px; word-break: break-all; }
.stat-box .hint {
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
  overflow: hidden; word-break: break-all;
}
.ok { color: #16a34a; }
.bad { color: #dc2626; }
.warn { color: #d97706; }
.form-card { border-radius: 10px; }
.tip { font-size: 12px; color: #94a3b8; line-height: 1.6; margin-top: 4px; }
.tip.inline { margin: 0 0 0 12px; }
.temp { margin-left: 14px; font-size: 13px; color: #0f172a; font-weight: 500; }
.empty-tip { font-size: 12px; color: #94a3b8; line-height: 1.8; }
.res-head { display: flex; align-items: center; gap: 10px; font-size: 14px; }
.res-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.res-item { border: 1px solid #eef2f7; border-radius: 8px; padding: 12px 14px; }
.res-label { font-size: 12px; color: #64748b; }
.res-val { display: flex; align-items: baseline; gap: 10px; font-size: 15px; font-weight: 500; margin-top: 6px; color: #0f172a; }
.res-sub { font-size: 12px; color: #94a3b8; margin-top: 6px; line-height: 1.6; word-break: break-all; }
.mt { margin-top: 12px; }
</style>
