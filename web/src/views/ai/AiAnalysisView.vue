<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>大模型学情分析</span>
          <el-tag :type="aiUp ? 'success' : 'warning'" size="small">
            {{ aiUp ? 'Ollama 已连接' : 'Ollama 未连接（规则化降级）' }}
          </el-tag>
        </div>
      </template>

      <el-form inline>
        <el-form-item label="学生">
          <el-select v-model="studentId" filterable remote :remote-method="searchStudent" placeholder="输入学号或姓名"
                     style="width: 240px">
            <el-option v-for="s in students" :key="s.id" :label="s.studentNo + ' ' + s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="学期">
          <el-select v-model="termId" placeholder="请选择" clearable style="width: 200px">
            <el-option v-for="t in terms" :key="t.id" :label="t.termName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="reporting" :disabled="!studentId" @click="loadReport">生成学情报告</el-button>
          <el-button type="success" :loading="advicing" :disabled="!studentId" @click="loadAdvice">生成改进建议</el-button>
        </el-form-item>
      </el-form>

      <el-tabs v-model="tab">
        <el-tab-pane label="分析报告" name="report">
          <div v-if="reportMeta" class="meta-bar">
            <el-tag size="small">模型：{{ reportMeta.model }}</el-tag>
            <el-tag size="small" :type="reportMeta.status === 'SUCCESS' ? 'success' : 'warning'">
              {{ reportMeta.status === 'SUCCESS' ? '大模型生成' : '规则化降级' }}
            </el-tag>
            <span v-if="reportMeta.totalTokens">Tokens：{{ reportMeta.totalTokens }}</span>
          </div>
          <div class="md-body">{{ report }}</div>
        </el-tab-pane>

        <el-tab-pane label="改进建议" name="advice">
          <div class="md-body">{{ advice }}</div>
        </el-tab-pane>

        <el-tab-pane label="学情问答（RAG）" name="chat">
          <div class="chat-box">
            <div v-for="(m, i) in messages" :key="i" class="chat-item" :class="m.role">
              <div class="bubble">{{ m.content }}</div>
            </div>
            <div v-if="streaming" class="chat-item ai">
              <div class="bubble streaming">{{ streamText }}<span class="cursor">|</span></div>
            </div>
          </div>
          <div class="chat-input">
            <el-input v-model="question" type="textarea" :rows="2" placeholder="例如：该学生哪些课程需要重点关注？"
                      @keyup.ctrl.enter="ask" />
            <el-button type="primary" :loading="streaming" @click="ask">发送（Ctrl+Enter）</el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="知识库" name="kb">
          <el-button type="primary" size="small" @click="kbDialog = true">录入文档</el-button>
          <el-table :data="docs" border stripe size="small" style="margin-top: 12px">
            <el-table-column prop="docName" label="文档名称" min-width="200" />
            <el-table-column prop="docType" label="类型" width="100" />
            <el-table-column prop="chunkCount" label="分片数" width="90" />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button link type="danger" size="small" @click="removeDoc(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="kbDialog" title="录入知识库文档" width="600px">
      <el-form :model="kbForm" label-width="90px">
        <el-form-item label="文档名称"><el-input v-model="kbForm.docName" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="kbForm.docType">
            <el-option label="规章制度" value="POLICY" />
            <el-option label="课程资料" value="COURSE" />
            <el-option label="常见问题" value="FAQ" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="kbForm.content" type="textarea" :rows="8" placeholder="粘贴文档正文，系统将自动分片并向量化" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="kbDialog = false">取消</el-button>
        <el-button type="primary" :loading="kbSaving" @click="submitDoc">入库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { aiHealth, generateReport, generateAdvice, listKnowledge, addKnowledge, deleteKnowledge, streamChat } from '@/api/ai'
import { pageStudents } from '@/api/student'
import { listBase } from '@/api/base'

const aiUp = ref(false)
const tab = ref('report')
const studentId = ref(null)
const termId = ref(null)
const students = ref([])
const terms = ref([])
const report = ref('')
const advice = ref('')
const reportMeta = ref(null)
const reporting = ref(false)
const advicing = ref(false)

const question = ref('')
const messages = ref([])
const streamText = ref('')
const streaming = ref(false)

const docs = ref([])
const kbDialog = ref(false)
const kbSaving = ref(false)
const kbForm = reactive({ docName: '', docType: 'POLICY', content: '' })

async function searchStudent(kw) {
  const data = await pageStudents({ current: 1, size: 20, keyword: kw })
  students.value = data.records
}

async function loadReport() {
  reporting.value = true
  try {
    const res = await generateReport({ studentId: studentId.value, termId: termId.value })
    report.value = res.content
    reportMeta.value = res
    tab.value = 'report'
  } finally {
    reporting.value = false
  }
}

async function loadAdvice() {
  advicing.value = true
  try {
    const res = await generateAdvice({ studentId: studentId.value, termId: termId.value })
    advice.value = res.content
    tab.value = 'advice'
  } finally {
    advicing.value = false
  }
}

function ask() {
  if (!question.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  messages.value.push({ role: 'user', content: question.value })
  streaming.value = true
  streamText.value = ''
  tab.value = 'chat'
  const q = question.value
  question.value = ''

  streamChat({ question: q, studentId: studentId.value, termId: termId.value },
    (chunk) => { streamText.value += chunk },
    () => {
      messages.value.push({ role: 'ai', content: streamText.value })
      streamText.value = ''
      streaming.value = false
    })
}

async function submitDoc() {
  if (!kbForm.docName.trim() || !kbForm.content.trim()) {
    ElMessage.warning('请填写文档名称与内容')
    return
  }
  kbSaving.value = true
  try {
    await addKnowledge(kbForm.docName, kbForm.docType, kbForm.content)
    ElMessage.success('文档已入库并完成向量化')
    kbDialog.value = false
    kbForm.content = ''
    docs.value = await listKnowledge()
  } finally {
    kbSaving.value = false
  }
}

async function removeDoc(row) {
  await deleteKnowledge(row.id)
  ElMessage.success('已删除')
  docs.value = await listKnowledge()
}

onMounted(async () => {
  terms.value = await listBase('term')
  if (terms.value.length) termId.value = terms.value[terms.value.length - 1].id
  aiUp.value = await aiHealth()
  await searchStudent('')
  docs.value = await listKnowledge()
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.meta-bar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
  font-size: 13px;
  color: #606266;
}

.md-body {
  white-space: pre-wrap;
  line-height: 1.8;
  padding: 12px;
  background: #fafafa;
  border-radius: 4px;
  min-height: 200px;
  max-height: 480px;
  overflow: auto;
}

.chat-box {
  height: 340px;
  overflow: auto;
  padding: 12px;
  background: #fafafa;
  border-radius: 4px;
}

.chat-item {
  display: flex;
  margin-bottom: 10px;
}

.chat-item.user {
  justify-content: flex-end;
}

.bubble {
  max-width: 75%;
  padding: 8px 12px;
  border-radius: 6px;
  white-space: pre-wrap;
  line-height: 1.7;
  font-size: 14px;
}

.chat-item.user .bubble {
  background: #409eff;
  color: #fff;
}

.chat-item.ai .bubble {
  background: #fff;
  border: 1px solid #e4e7ed;
}

.cursor {
  animation: blink 1s infinite;
}

@keyframes blink {
  50% { opacity: 0; }
}

.chat-input {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  align-items: flex-end;
}
</style>
