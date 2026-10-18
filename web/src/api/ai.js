import request from './request'

/** 大模型服务健康检查 */
export function aiHealth() {
  return request.get('/ai/health')
}

/** 已安装模型列表 */
export function aiModels() {
  return request.get('/ai/models')
}

/** 生成学情报告 */
export function generateReport(params) {
  return request.post('/ai/report', null, { params })
}

/** 生成改进建议 */
export function generateAdvice(params) {
  return request.post('/ai/advice', null, { params })
}

/** 知识库列表 */
export function listKnowledge() {
  return request.get('/ai/knowledge')
}

/** 录入知识库文档 */
export function addKnowledge(docName, docType, content) {
  return request.post(`/ai/knowledge?docName=${encodeURIComponent(docName)}&docType=${docType}`, content, {
    headers: { 'Content-Type': 'text/plain' }
  })
}

/** 删除知识库文档 */
export function deleteKnowledge(id) {
  return request.delete(`/ai/knowledge/${id}`)
}

/** 提示词模板 */
export function listTemplates() {
  return request.get('/ai/templates')
}

/** 保存提示词模板 */
export function saveTemplate(data) {
  return request.post('/ai/templates', data)
}

/**
 * 流式问答：通过 SSE 逐段接收
 * @param {object} params 问题参数
 * @param {(chunk:string)=>void} onChunk 片段回调
 * @param {()=>void} onDone 结束回调
 */
export function streamChat(params, onChunk, onDone) {
  const qs = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') qs.append(k, v)
  })
  const es = new EventSource(`/api/ai/chat/stream?${qs.toString()}`)
  es.onmessage = (e) => {
    if (e.data === '[DONE]') {
      es.close()
      onDone && onDone()
      return
    }
    try {
      const payload = JSON.parse(e.data)
      if (payload.content) onChunk(payload.content)
    } catch {
      onChunk(e.data)
    }
  }
  es.onerror = () => {
    es.close()
    onDone && onDone()
  }
  return es
}

/**
 * 学情智能体会话（SSE 流式，场景路由）。
 * 事件协议：
 *  - 默认事件：{content} 增量文本
 *  - meta 事件：{model,status,studentName,totalTokens}
 *  - error 事件：错误文本
 *  - done 事件：结束
 */
export function agentChatStream({ scene = 'CHAT', message = '', studentId, termId, userId, onContent, onMeta, onDone, onError }) {
  const params = new URLSearchParams({ scene, message })
  if (studentId) params.set('studentId', studentId)
  if (termId) params.set('termId', termId)
  if (userId) params.set('userId', userId)
  const es = new EventSource(`/api/ai/agent/chat/stream?${params.toString()}`)
  es.onmessage = (e) => {
    try {
      const data = JSON.parse(e.data)
      if (data && data.content) onContent && onContent(data.content)
    } catch {
      onContent && onContent(e.data)
    }
  }
  es.addEventListener('meta', (e) => {
    try {
      onMeta && onMeta(JSON.parse(e.data))
    } catch { /* ignore */ }
  })
  es.addEventListener('error', (e) => {
    if (e.data) onError && onError(e.data)
    es.close()
  })
  es.addEventListener('done', () => {
    es.close()
    onDone && onDone()
  })
  return es
}
