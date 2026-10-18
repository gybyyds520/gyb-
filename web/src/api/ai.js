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

/** ① 预警工作周报（AI 聚合解读） */
export function generateWeeklyReport() {
  return request.post('/ai/weekly-report')
}

/** ③ 自然语言查学情（NL2Query） */
export function learningQuery(question) {
  return request.post('/ai/query', null, { params: { question } })
}

/** ④ 成绩单 AI 评语 */
export function generateComment(params) {
  return request.post('/ai/comment', null, { params })
}

/** ⑤ 家长沟通通知文案（可选发送） */
export function generateParentNotice(warningId, send = false) {
  return request.post(`/ai/parent-notice/${warningId}`, null, { params: { send } })
}

/** ② AI 干预帮扶方案 */
export function generateInterventionPlan(warningId) {
  return request.post(`/intervention/ai-plan/${warningId}`)
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
 * SSE 流式读取器（fetch + ReadableStream）：
 * EventSource 无法携带 Authorization 头，而本项目 SSE 接口均在 JWT 鉴权之后，
 * 使用 EventSource 会得到 401 并无限重连、前端永远停在“生成中”。
 * 改为 fetch 流式读取即可携带 token，且能区分 message / meta / error / done 事件。
 * @param {string} url SSE 地址（含 query）
 * @param {object} handlers { onContent, onMeta, onDone, onError }
 * @returns {AbortController} 可用于中断
 */
function createSseReader(url, { onContent, onMeta, onDone, onError } = {}) {
  const ctrl = new AbortController()
  const token = localStorage.getItem('xqfx_token')
  ;(async () => {
    try {
      const resp = await fetch(url, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        signal: ctrl.signal
      })
      if (!resp.ok) {
        onError && onError(`请求失败（HTTP ${resp.status}）`)
        onDone && onDone()
        return
      }
      const reader = resp.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buf = ''
      let finished = false

      const handleEvent = (ev, data) => {
        const trimmed = data.trim()
        if (!trimmed) return
        if (ev === 'done') {
          finished = true
          onDone && onDone()
        } else if (ev === 'meta') {
          try { onMeta && onMeta(JSON.parse(trimmed)) } catch { /* 忽略 */ }
        } else if (ev === 'error') {
          finished = true
          onError && onError(trimmed)
          onDone && onDone()
        } else {
          // 默认 message 事件
          if (trimmed === '[DONE]') {
            finished = true
            onDone && onDone()
            return
          }
          try {
            const payload = JSON.parse(trimmed)
            if (payload && payload.content) onContent && onContent(payload.content)
            else if (payload && payload.error) onError && onError(payload.error)
          } catch {
            onContent && onContent(trimmed)
          }
        }
      }

      while (!ctrl.signal.aborted) {
        const { done, value } = await reader.read()
        if (done) break
        buf += decoder.decode(value, { stream: true })
        let idx
        while ((idx = buf.indexOf('\n\n')) >= 0) {
          const block = buf.slice(0, idx)
          buf = buf.slice(idx + 2)
          let ev = 'message'
          const dataLines = []
          for (const line of block.split('\n')) {
            if (line.startsWith('event:')) ev = line.slice(6).trim()
            else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
          }
          if (dataLines.length) handleEvent(ev, dataLines.join('\n'))
          if (finished) break
        }
        if (finished) break
      }
      if (!finished && !ctrl.signal.aborted) onDone && onDone()
    } catch (e) {
      if (e.name !== 'AbortError') {
        onError && onError(e?.message || String(e))
        onDone && onDone()
      }
    }
  })()
  return ctrl
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
  return createSseReader(`/api/ai/chat/stream?${qs.toString()}`, {
    onContent: onChunk,
    onDone
  })
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
  return createSseReader(`/api/ai/agent/chat/stream?${params.toString()}`, {
    onContent,
    onMeta,
    onDone,
    onError
  })
}
