import request from './request'

// 大模型导购接口（接口文档：九）
// 注意：/chat/send 为 SSE 流式接口，需用原生 fetch 读取流（见 ChatView.vue），不走本文件 axios

export const createSession = (title) => request.post('/chat/session', null, { params: { title } })
export const getSessionPage = () => request.get('/chat/session/page')
export const deleteSession = (id) => request.delete(`/chat/session/${id}`)
export const getMessagePage = (sessionId) => request.get('/chat/message/page', { params: { sessionId } })
export const getHotQuestions = () => request.get('/chat/hot-questions')

/**
 * SSE 流式发送消息（原生 fetch，事件回调）
 * @param {Object} opts { sessionId, content, onIntent, onProducts, onAnswer, onDone }
 */
export async function sendChatStream(opts) {
  const { sessionId, content, onIntent, onProducts, onAnswer, onDone } = opts
  const resp = await fetch('/api/chat/send?' + new URLSearchParams({ sessionId, content }), {
    method: 'POST',
    headers: { Authorization: `Bearer ${localStorage.getItem('token')}` }
  })
  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    // 按空行切分 SSE 帧
    const frames = buffer.split('\n\n')
    buffer = frames.pop()
    for (const frame of frames) {
      let event = 'message'
      const dataLines = []
      for (const line of frame.split('\n')) {
        if (line.startsWith('event:')) event = line.slice(6).trim()
        if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
      }
      if (!dataLines.length) continue
      const data = dataLines.join('\n')
      try {
        if (event === 'intent') onIntent?.(JSON.parse(data))
        else if (event === 'products') onProducts?.(JSON.parse(data))
        else if (event === 'answer') onAnswer?.(JSON.parse(data))
        else if (event === 'done') onDone?.(JSON.parse(data))
      } catch (e) { /* 忽略非JSON帧 */ }
    }
  }
}
