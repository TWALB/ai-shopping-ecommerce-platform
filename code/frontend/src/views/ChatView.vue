<template>
  <el-card class="chat-card">
    <!-- 会话列表 + 聊天区 -->
    <div class="chat-layout">
      <div class="session-list">
        <el-button type="primary" style="width: 100%; margin-bottom: 10px" @click="newSession">＋ 新会话</el-button>
        <div v-for="s in sessions" :key="s.id" class="session-item"
             :class="{ active: s.id === currentSessionId }" @click="switchSession(s.id)">
          {{ s.sessionTitle || `会话 ${s.id}` }}
        </div>
        <el-empty v-if="!sessions.length" description="暂无会话" :image-size="60" />
      </div>

      <div class="chat-main">
        <div class="messages" ref="msgBox">
          <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role === 0 ? 'user' : 'assistant'">
            <div class="bubble">
              <div v-if="m.role === 1 && m.intent" class="intent-tag">意图：{{ intentText(m.intent) }}</div>
              <div>{{ m.content }}</div>
              <!-- 推荐商品卡片 -->
              <div v-if="m.role === 1 && m.productCards && m.productCards.length" class="cards">
                <div v-for="p in m.productCards" :key="p.id" class="mini-card" @click="$router.push('/products')">
                  <div class="mini-name">{{ p.productName }}</div>
                  <div class="mini-price">¥{{ p.price }}</div>
                </div>
              </div>
            </div>
          </div>
          <div v-if="loading" class="msg assistant"><div class="bubble">导购思考中…</div></div>
        </div>

        <div class="quick">
          <el-tag v-for="q in hotQuestions" :key="q" class="quick-tag" @click="send(q)">{{ q }}</el-tag>
        </div>

        <div class="input-area">
          <el-input v-model="input" placeholder="描述您的需求，如：3000以内拍照好的手机" size="large"
                    @keyup.enter="send()" :disabled="loading" />
          <el-button type="primary" size="large" :loading="loading" @click="send()">发送</el-button>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { createSession, getSessionPage, getMessagePage, getHotQuestions, sendChatStream } from '../api/chat'

const sessions = ref([])
const currentSessionId = ref(null)
const messages = ref([])
const input = ref('')
const loading = ref(false)
const hotQuestions = ref([])
const msgBox = ref(null)

function intentText(intent) {
  const map = { find_product: '找商品', compare: '参数对比', recommend: '选购建议', chat: '闲聊' }
  return map[intent] || intent
}

async function newSession() {
  const data = await createSession('')
  currentSessionId.value = data.id
  messages.value = []
  loadSessions()
  scrollBottom()
}

async function loadSessions() {
  try {
    sessions.value = await getSessionPage()
    if (!currentSessionId.value && sessions.value.length) {
      currentSessionId.value = sessions.value[0].id
      loadMessages()
    }
  } catch (e) { /* 后端未实现 */ }
}

async function switchSession(id) {
  currentSessionId.value = id
  await loadMessages()
}

async function loadMessages() {
  if (!currentSessionId.value) return
  try {
    const list = await getMessagePage(currentSessionId.value)
    messages.value = list.map((m) => ({
      role: m.role,
      content: m.content,
      intent: m.intent,
      productCards: m.productIds ? m.productIds.map((id) => ({ id, productName: `商品 #${id}`, price: '--' })) : []
    }))
    scrollBottom()
  } catch (e) { /* 后端未实现 */ }
}

async function send(text) {
  const content = (text || input.value).trim()
  if (!content || loading.value) return
  if (!currentSessionId.value) await newSession()
  input.value = ''
  messages.value.push({ role: 0, content })
  loading.value = true
  scrollBottom()
  try {
    // SSE 流式：增量拼接回答
    let answer = ''
    messages.value.push({ role: 1, content: '', intent: '', productCards: [] })
    const aiMsg = messages.value[messages.value.length - 1]
    await sendChatStream({
      sessionId: currentSessionId.value,
      content,
      onIntent: (d) => { aiMsg.intent = d.intent },
      onProducts: (list) => {
        aiMsg.productCards = (list || []).map((p) => ({ id: p.id, productName: p.productName, price: p.price }))
      },
      onAnswer: (d) => {
        // 若后端按增量 delta 返回则追加；若整段返回则覆盖
        aiMsg.content = d.delta ? aiMsg.content + d.content : d.content
        scrollBottom()
      },
      onDone: () => { aiMsg.content = aiMsg.content || '（导购暂未返回内容）' }
    })
  } catch (e) {
    ElMessage.error('导购服务暂不可用（后端 /chat/send 待开发）')
    messages.value.pop()
  } finally {
    loading.value = false
    scrollBottom()
  }
}

function scrollBottom() {
  nextTick(() => {
    if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
  })
}

onMounted(async () => {
  loadSessions()
  try { hotQuestions.value = await getHotQuestions() } catch (e) { /* 忽略 */ }
})
</script>

<style scoped>
.chat-card { height: calc(100vh - 120px); }
.chat-layout { display: flex; height: 100%; }
.session-list { width: 220px; border-right: 1px solid #e5e7eb; padding-right: 12px; overflow-y: auto; }
.session-item { padding: 10px 12px; border-radius: 8px; cursor: pointer; margin-bottom: 6px; color: #374151; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.session-item:hover { background: #f3f4f6; }
.session-item.active { background: #eff6ff; color: #1d4ed8; }
.chat-main { flex: 1; display: flex; flex-direction: column; padding-left: 16px; min-width: 0; }
.messages { flex: 1; overflow-y: auto; padding: 8px 4px; }
.msg { display: flex; margin-bottom: 12px; }
.msg.user { justify-content: flex-end; }
.msg.assistant { justify-content: flex-start; }
.bubble { max-width: 75%; padding: 10px 14px; border-radius: 10px; line-height: 1.6; font-size: 14px; white-space: pre-wrap; }
.msg.user .bubble { background: #2563eb; color: #fff; border-bottom-right-radius: 2px; }
.msg.assistant .bubble { background: #f1f5f9; border-bottom-left-radius: 2px; }
.intent-tag { font-size: 12px; color: #7c3aed; margin-bottom: 4px; }
.cards { display: flex; gap: 8px; margin-top: 8px; flex-wrap: wrap; }
.mini-card { border: 1px solid #e2e8f0; border-radius: 8px; padding: 8px 10px; cursor: pointer; background: #fff; }
.mini-name { font-size: 13px; max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mini-price { color: #dc2626; font-weight: 700; font-size: 13px; }
.quick { display: flex; gap: 8px; flex-wrap: wrap; padding: 6px 0; }
.quick-tag { cursor: pointer; }
.input-area { display: flex; gap: 10px; padding-top: 10px; border-top: 1px solid #e5e7eb; }
</style>
