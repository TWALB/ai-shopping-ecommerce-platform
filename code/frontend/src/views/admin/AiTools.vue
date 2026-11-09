<template>
  <div class="ai-page">
    <div class="ai-head">
      <div>
        <h3>Function Tool</h3>
        <p>注册表决定「模型能看到哪些能力、每个能力怎么描述、参数长什么样」。执行逻辑在代码里，管理端改的是描述与参数说明，不是调用地址——避免把内网接口暴露成可配置项。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button @click="showOpenai">模型视角</el-button>
        <el-button @click="restore">恢复内置</el-button>
        <el-button type="primary" @click="openCreate">新增工具</el-button>
      </div>
    </div>

    <el-alert type="warning" :closable="false" show-icon>
      <template #title>
        当前阶段：注册表与调试台已可用，<b>尚未接入对话主链路</b>。接入需要改流式解析（tool_calls 增量拼接）与多轮回环，属于下一步，且要重跑一遍检索评测。
      </template>
    </el-alert>

    <div class="stat-row">
      <div class="stat-box">
        <div class="label">工具总数</div>
        <div class="value">{{ rows.length }}<small>个</small></div>
        <div class="hint">商品域 {{ countBy('product') }} · 业务域 {{ countBy('biz') }}</div>
      </div>
      <div class="stat-box">
        <div class="label">已启用</div>
        <div class="value">{{ rows.filter(r => r.enabled === 1).length }}<small>个</small></div>
        <div class="hint">停用后不会出现在模型视野里</div>
      </div>
      <div class="stat-box">
        <div class="label">内置实现</div>
        <div class="value">{{ rows.filter(r => r.builtin === 1).length }}<small>个</small></div>
        <div class="hint">有 Java 实现，不可删除</div>
      </div>
      <div class="stat-box">
        <div class="label">自定义</div>
        <div class="value">{{ rows.filter(r => r.builtin !== 1).length }}<small>个</small></div>
        <div class="hint">仅元数据，调试会提示无实现</div>
      </div>
    </div>

    <el-card shadow="never">
      <div class="tool-row">
        <el-input v-model="keyword" placeholder="编码 / 名称 / 说明" clearable style="width: 220px"
                  @keyup.enter="load" @clear="load" />
        <el-select v-model="domain" placeholder="业务域" clearable style="width: 140px" @change="load">
          <el-option label="商品域" value="product" />
          <el-option label="业务域" value="biz" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <span class="spacer" />
        <span class="muted">工具调试请到「商品工具调试 / 业务工具调试」，可填参直接执行</span>
      </div>

      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column label="工具编码" width="180">
          <template #default="{ row }">
            <span class="mono strong">{{ row.toolCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="toolName" label="名称" width="130" />
        <el-table-column label="业务域" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.domain === 'product' ? 'primary' : 'success'" effect="plain">
              {{ row.domain === 'product' ? '商品域' : '业务域' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled === 1" size="small" @change="(v) => toggle(row, v)" />
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.builtin === 1" size="small" type="info" effect="plain">内置</el-tag>
            <el-tag v-else size="small" type="warning" effect="plain">自定义</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="给模型看的说明" min-width="260">
          <template #default="{ row }">
            <div class="desc-cell">{{ row.description }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="debug(row)">调试</el-button>
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" :disabled="row.builtin === 1"
                       @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="drawerVisible" :title="editing ? '编辑工具' : '新增工具'" size="560px">
      <el-form :model="form" label-width="96px" label-position="top">
        <el-form-item label="工具编码（模型看到的 function name）">
          <el-input v-model="form.toolCode" :disabled="editing" placeholder="searchProduct" class="mono" />
          <div class="tip">字母开头，仅字母/数字/下划线，3-40 位；保存后不可修改</div>
        </el-form-item>
        <el-form-item label="展示名称">
          <el-input v-model="form.toolName" placeholder="商品检索" />
        </el-form-item>
        <el-form-item label="业务域">
          <el-radio-group v-model="form.domain" :disabled="editing">
            <el-radio value="product">商品域</el-radio>
            <el-radio value="biz">业务域</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="给模型看的用途说明">
          <el-input v-model="form.description" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }"
                    placeholder="什么时候该调用这个工具，越具体模型选得越准" />
        </el-form-item>
        <el-form-item label="入参 JSON Schema">
          <el-input v-model="form.paramsSchema" type="textarea" :autosize="{ minRows: 6, maxRows: 14 }"
                    class="mono" />
        </el-form-item>
        <el-form-item label="调试示例参数">
          <el-input v-model="form.sampleParams" type="textarea" :autosize="{ minRows: 3, maxRows: 8 }"
                    class="mono" />
        </el-form-item>
        <el-form-item label="返回结构说明">
          <el-input v-model="form.resultHint" placeholder="count / items[{productId, price}]" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortNo" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="openaiVisible" title="模型视角的 tools 数组" width="760px">
      <p class="muted">这就是接入主链路时塞进请求体 tools 字段的内容：只包含已启用的工具。</p>
      <pre class="mono block">{{ openaiText }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAiTools, createAiTool, updateAiTool, toggleAiTool, deleteAiTool,
  restoreAiTools, getAiOpenaiTools
} from '../../api/adminAi'

const router = useRouter()
const rows = ref([])
const loading = ref(false)
const keyword = ref('')
const domain = ref('')

const drawerVisible = ref(false)
const editing = ref(false)
const saving = ref(false)
const editingId = ref(null)

const openaiVisible = ref(false)
const openaiText = ref('')

const blank = () => ({
  toolCode: '', toolName: '', domain: 'product', description: '',
  paramsSchema: '{\n  "type": "object",\n  "properties": {\n    "keyword": { "type": "string", "description": "关键词" }\n  }\n}',
  sampleParams: '{"keyword": ""}', resultHint: '', sortNo: 99, enabled: 1
})

const form = reactive(blank())

function countBy(d) {
  return rows.value.filter((r) => r.domain === d).length
}

async function load() {
  loading.value = true
  try {
    rows.value = (await getAiTools({ keyword: keyword.value || undefined, domain: domain.value || undefined })) || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, blank())
  editing.value = false
  editingId.value = null
  drawerVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    toolCode: row.toolCode, toolName: row.toolName, domain: row.domain,
    description: row.description, paramsSchema: row.paramsSchema,
    sampleParams: row.sampleParams || '', resultHint: row.resultHint || '',
    sortNo: row.sortNo ?? 99, enabled: row.enabled ?? 1
  })
  editing.value = true
  editingId.value = row.id
  drawerVisible.value = true
}

async function submit() {
  saving.value = true
  try {
    if (editing.value) {
      await updateAiTool(editingId.value, {
        toolName: form.toolName, description: form.description,
        paramsSchema: form.paramsSchema, sampleParams: form.sampleParams,
        resultHint: form.resultHint, sortNo: form.sortNo, enabled: form.enabled
      })
    } else {
      await createAiTool({ ...form })
    }
    drawerVisible.value = false
    ElMessage.success('已保存')
    await load()
  } finally {
    saving.value = false
  }
}

async function toggle(row, enabled) {
  const updated = await toggleAiTool(row.id, enabled)
  const idx = rows.value.findIndex((r) => r.id === row.id)
  if (idx >= 0) rows.value[idx] = updated
  ElMessage.success(enabled ? '已启用' : '已停用，模型将看不到该工具')
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除工具「${row.toolName}」？`, '删除工具', { type: 'warning' })
  await deleteAiTool(row.id)
  ElMessage.success('已删除')
  await load()
}

async function restore() {
  const data = await restoreAiTools()
  ElMessage.success(`已补齐 ${data.restored} 个内置工具`)
  await load()
}

function debug(row) {
  router.push({
    path: `/admin/ai/tool/debug/${row.domain === 'biz' ? 'biz' : 'product'}`,
    query: { tool: row.toolCode }
  })
}

async function showOpenai() {
  openaiText.value = JSON.stringify(await getAiOpenaiTools({}), null, 2)
  openaiVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.mono.strong { color: #0f172a; font-weight: 500; }
.desc-cell {
  font-size: 12px; color: #64748b; line-height: 1.6;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.tip { font-size: 12px; color: #94a3b8; line-height: 1.6; margin-top: 4px; }
.block {
  background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px;
  padding: 12px; white-space: pre-wrap; line-height: 1.7; max-height: 460px; overflow: auto;
}
</style>
