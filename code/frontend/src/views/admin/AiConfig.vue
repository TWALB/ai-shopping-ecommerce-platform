<template>
  <div class="ai-page">
    <div class="ai-head">
      <div>
        <h3>提示词与参数</h3>
        <p>提示词是运营资产：改完直接保存，下一个对话请求即生效，不需要改代码、不需要重启。配置缺失或读库失败时自动回落到内置默认值，导购功能不会中断。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon>
      <template #title>
        职责边界：<b>怎么说</b> 由这里决定｜<b>拿什么说</b> 由知识库决定（商品、参数、政策）｜<b>我的订单到哪了</b> 这类实时事实必须查接口，不要写进提示词
      </template>
    </el-alert>

    <el-card v-for="item in list" :key="item.key" shadow="never" class="cfg-card">
      <div class="cfg-head">
        <div class="cfg-title">
          <span class="name">{{ item.name }}</span>
          <el-tag size="small" type="info" effect="plain" class="mono">{{ item.key }}</el-tag>
          <el-tag v-if="item.version" size="small" effect="plain">v{{ item.version }}</el-tag>
          <span class="muted">{{ item.updateTime ? '更新于 ' + fmt(item.updateTime) : '尚未写入数据库' }}</span>
          <span v-if="isDirty(item)" class="dirty">未保存</span>
        </div>
        <div class="cfg-actions">
          <el-button size="small" text @click="showDefault(item)">查看默认值</el-button>
          <el-button size="small" @click="reset(item)">恢复默认</el-button>
          <el-button size="small" type="primary" :loading="saving[item.key]" :disabled="!isDirty(item)"
                     @click="save(item)">保存</el-button>
        </div>
      </div>

      <p class="desc">{{ item.description }}</p>

      <el-input v-if="item.key === 'chat.attr_limit'" v-model="draft[item.key]" style="width: 160px" />
      <el-input v-else v-model="draft[item.key]" type="textarea" :autosize="{ minRows: 4, maxRows: 16 }"
                class="mono" />

      <div class="var-row">
        <template v-if="item.variables && item.variables.length">
          <span class="muted">可用变量：</span>
          <el-tag v-for="v in item.variables" :key="v" size="small" class="mono var-tag"
                  @click="insertVar(item, v)">{{ varText(v) }}</el-tag>
          <el-button link type="primary" size="small" @click="preview(item)">预览渲染结果</el-button>
        </template>
        <span v-else class="muted">该配置项无需占位符，直接编辑内容即可</span>
      </div>
    </el-card>

    <el-dialog v-model="defaultVisible" title="内置默认值" width="720px">
      <p class="muted">{{ defaultTitle }}　恢复默认会以这段内容覆盖当前配置，版本号继续自增。</p>
      <pre class="mono block">{{ defaultText }}</pre>
    </el-dialog>

    <el-dialog v-model="previewVisible" title="渲染预览" width="720px">
      <p class="muted">用示例变量（productCount=3、intentLabel=找商品）替换后的效果，不调用模型、不产生费用。</p>
      <pre class="mono block">{{ previewText }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAiConfig, saveAiConfig, resetAiConfig, previewAiConfig } from '../../api/adminAi'

const list = ref([])
const draft = reactive({})
const origin = reactive({})
const saving = reactive({})
const loading = ref(false)

const defaultVisible = ref(false)
const defaultText = ref('')
const defaultTitle = ref('')

const previewVisible = ref(false)
const previewText = ref('')

async function load() {
  loading.value = true
  try {
    const data = await getAiConfig()
    list.value = data || []
    list.value.forEach((item) => {
      draft[item.key] = item.value
      origin[item.key] = item.value
    })
  } finally {
    loading.value = false
  }
}

function isDirty(item) {
  return (draft[item.key] || '') !== (origin[item.key] || '')
}

function applyItem(item) {
  const idx = list.value.findIndex((x) => x.key === item.key)
  if (idx >= 0) list.value[idx] = item
  draft[item.key] = item.value
  origin[item.key] = item.value
}

async function save(item) {
  saving[item.key] = true
  try {
    const updated = await saveAiConfig({ key: item.key, value: draft[item.key] })
    applyItem(updated)
    ElMessage.success(`已保存 ${item.name}，下一个对话请求即生效`)
  } finally {
    saving[item.key] = false
  }
}

async function reset(item) {
  await ElMessageBox.confirm(`确定把「${item.name}」恢复为内置默认值？当前内容会被覆盖。`, '恢复默认', {
    type: 'warning'
  })
  const updated = await resetAiConfig(item.key)
  applyItem(updated)
  ElMessage.success('已恢复默认值')
}

async function preview(item) {
  const data = await previewAiConfig({ key: item.key, productCount: 3, intentLabel: '找商品' })
  previewText.value = data.rendered || ''
  previewVisible.value = true
}

function showDefault(item) {
  defaultTitle.value = item.name
  defaultText.value = item.defaultValue || ''
  defaultVisible.value = true
}

function insertVar(item, v) {
  draft[item.key] = (draft[item.key] || '') + varText(v)
}

function varText(v) {
  return '{{' + v + '}}'
}

function fmt(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 19)
}

onMounted(load)
</script>

<style scoped>
.cfg-card { border-radius: 10px; }
.cfg-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.cfg-title { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.cfg-title .name { font-size: 15px; font-weight: 600; color: #0f172a; }
.cfg-actions { display: flex; gap: 6px; flex-shrink: 0; }
.desc { font-size: 12px; color: #64748b; margin: 8px 0 10px; line-height: 1.7; }
.var-row { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
.var-tag { cursor: pointer; }
.dirty { font-size: 11px; color: #d97706; background: #fffbeb; border: 1px solid #fde68a; border-radius: 4px; padding: 0 6px; }
.block {
  background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px;
  padding: 12px; white-space: pre-wrap; line-height: 1.8; max-height: 420px; overflow: auto;
}
</style>
