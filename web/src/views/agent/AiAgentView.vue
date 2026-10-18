<template>
  <div class="agent-container">
    <el-card shadow="never" class="chat-card">
      <template #header>
        <div class="card-header">
          <div class="agent-title">
            <span class="agent-badge">AI</span>
            学情智能体
          </div>
          <div class="header-right">
            <el-select v-if="userStore.isStaff" v-model="studentId" filterable remote :remote-method="searchStudent"
                       placeholder="选择学生" style="width: 200px; margin-right: 8px">
              <el-option v-for="s in students" :key="s.id" :label="s.studentNo + ' ' + s.name" :value="s.id" />
            </el-select>
            <el-select v-model="scene" style="width: 130px; margin-right: 8px">
              <el-option label="智能问答" value="CHAT" />
              <el-option label="学情报告" value="REPORT" />
              <el-option label="改进建议" value="ADVICE" />
            </el-select>
            <el-button size="small" @click="newSession">新会话</el-button>
          </div>
        </div>
      </template>

      <div ref="msgBox" class="msg-box">
        <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
          <div class="msg-avatar" :class="m.role">{{ m.role === 'user' ? '我' : 'AI' }}</div>
          <div class="msg-content">
            <div v-if="m.meta" class="msg-meta">
              模型：{{ m.meta.model }} · 学生：{{ m.meta.studentName || '-' }} · tokens：{{ m.meta.totalTokens ?? '-' }}
            </div>
            <div class="msg-text" v-html="renderText(m.text)"></div>
            <div v-if="m.error" class="msg-error">{{ m.error }}</div>
          </div>
        </div>
        <el-empty v-if="!messages.length" description="向智能体提问，或点击下方快捷入口" :image-size="70" />
      </div>

      <div class="quick-row">
        <el-tag v-for="q in QUICK" :key="q.scene + q.label" class="quick-tag" :type="q.type" @click="sendQuick(q)">
          {{ q.label }}
        </el-tag>
      </div>

      <div class="input-row">
        <el-input v-model="draft" type="textarea" :rows="2" resize="none"
                  placeholder="输入问题，例如：张三成绩下滑的可能原因有哪些？" @keydown.enter.exact.prevent="send" />
        <el-button type="primary" :loading="streaming" class="send-btn" @click="send">
          {{ streaming ? '生成中' : '发送' }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { agentChatStream } from '@/api/ai'
import { pageStudents } from '@/api/student'
import { listBase } from '@/api/base'

const userStore = useUserStore()
const messages = ref([])
const draft = ref('')
const scene = ref('CHAT')
const studentId = ref(null)
const students = ref([])
const termId = ref(null)
const terms = ref([])
const streaming = ref(false)
const msgBox = ref(null)
let sessionSeq = 0

const QUICK = [
  { scene: 'REPORT', label: '生成学情报告', type: 'primary' },
  { scene: 'ADVICE', label: '个性化改进建议', type: 'success' },
  { scene: 'CHAT', label: '预警原因分析', type: 'warning' },
  { scene: 'CHAT', label: '偏科成因解读', type: 'danger' }
]

const storageKey = 'xqfx_agent_sessions'

function loadSessions() {
  try {
    const raw = localStorage.getItem(storageKey)
    if (raw) {
      const list = JSON.parse(raw)
      if (list.length && list[list.length - 1]) {
        messages.value = list[list.length - 1].messages
        sessionSeq = list.length
      }
    }
  } catch { /* ignore */ }
}

function persist() {
  try {
    const list = JSON.parse(localStorage.getItem(storageKey) || '[]')
    if (list.length && list[list.length - 1] && list[list.length - 1].seq === sessionSeq) {
      list[list.length - 1].messages = messages.value
    } else {
      list.push({ seq: sessionSeq, title: '会话 ' + sessionSeq, messages: messages.value })
    }
    localStorage.setItem(storageKey, JSON.stringify(list.slice(-20)))
  } catch { /* ignore */ }
}

function newSession() {
  sessionSeq += 1
  messages.value = []
  draft.value = ''
  persist()
}

function renderText(text) {
  if (!text) return ''
  const esc = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  return esc
    .replace(/```([\s\S]*?)```/g, '<pre style="background:#f5f7fa;border-radius:6px;padding:8px 12px;font-size:12px;overflow-x:auto;margin:6px 0;">$1</pre>')
    .replace(/\*\*(.*?)\*\*/g, '<b>$1</b>')
    .replace(/\n/g, '<br/>')
}

function pushUser(text) {
  messages.value.push({ role: 'user', text })
  persist()
}

async function sendQuick(q) {
  scene.value = q.scene
  if (q.scene === 'CHAT') {
    draft.value = q.label === '预警原因分析'
      ? '请分析该生当前学业预警的主要原因，并结合学情给出建议。'
      : '请分析该生偏科成因，说明强项与弱项的差距及改进方向。'
  } else {
    draft.value = ''
  }
  await send()
}

async function send() {
  if (streaming.value) return
  const text = draft.value.trim()
  if (scene.value !== 'CHAT' && userStore.isStaff && !studentId.value) {
    ElMessage.warning('请先选择学生')
    return
  }
  if (scene.value === 'CHAT' && !text) {
    ElMessage.warning('请输入问题')
    return
  }
  pushUser(text || (scene.value === 'REPORT' ? '生成学情报告' : '生成改进建议'))
  draft.value = ''
  const botMsg = reactive({ role: 'bot', text: '', meta: null, error: '' })
  messages.value.push(botMsg)
  streaming.value = true
  await nextTick()
  scrollBottom()

  agentChatStream({
    scene: scene.value,
    message: text,
    studentId: userStore.isStaff ? studentId.value : null,
    termId: termId.value,
    onContent: (chunk) => {
      botMsg.text += chunk
      scrollBottom()
    },
    onMeta: (meta) => {
      botMsg.meta = meta
      scrollBottom()
    },
    onError: (err) => {
      botMsg.error = err
    },
    onDone: () => {
      streaming.value = false
      persist()
      scrollBottom()
    }
  })
}

function scrollBottom() {
  nextTick(() => {
    if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
  })
}

async function searchStudent(kw) {
  const data = await pageStudents({ current: 1, size: 20, keyword: kw })
  students.value = data.records
}

onMounted(async () => {
  terms.value = await listBase('term')
  if (terms.value.length) termId.value = terms.value[terms.value.length - 1].id
  loadSessions()
  if (userStore.isStaff) await searchStudent('')
})
</script>

<style scoped>
.agent-container {
  display: flex;
  gap: 12px;
}

.chat-card {
  flex: 1;
  min-width: 0;
  border-radius: 10px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.agent-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
}

.agent-badge {
  background: linear-gradient(135deg, #409eff, #7c4dff);
  color: #fff;
  border-radius: 6px;
  padding: 2px 8px;
  font-size: 13px;
}

.header-right {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}

.msg-box {
  height: 420px;
  overflow-y: auto;
  padding: 12px 4px;
  background: #fafbfc;
  border-radius: 8px;
  border: 1px solid #ebeef5;
}

.msg-row {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.msg-row.user {
  flex-direction: row-reverse;
}

.msg-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #fff;
  flex-shrink: 0;
}

.msg-avatar.user {
  background: #409eff;
}

.msg-avatar.bot {
  background: linear-gradient(135deg, #7c4dff, #409eff);
}

.msg-content {
  max-width: 78%;
}

.msg-row.user .msg-content {
  text-align: right;
}

.msg-text {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.8;
  word-break: break-word;
}

.msg-row.user .msg-text {
  background: #ecf5ff;
  border-color: #d9ecff;
  text-align: left;
}

.msg-meta {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.msg-error {
  margin-top: 6px;
  font-size: 12px;
  color: #f56c6c;
}

.quick-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 10px 0;
}

.quick-tag {
  cursor: pointer;
  padding: 6px 12px;
  font-size: 13px;
  border-radius: 16px;
}

.input-row {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.send-btn {
  height: 56px;
}
</style>
