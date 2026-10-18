<template>
  <el-card shadow="never">
    <el-tabs v-model="activeTab">
      <!-- ============ 试卷录入 ============ -->
      <el-tab-pane label="试卷录入" name="scan">
        <el-card shadow="never" class="upload-card">
          <el-upload
            drag
            :auto-upload="true"
            :show-file-list="false"
            accept=".png,.jpg,.jpeg,.bmp,.webp,.pdf,.docx,.doc"
            :http-request="handleUpload"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽或点击上传试卷 <em>（图片 / PDF / Word）</em></div>
            <div class="el-upload__tip">支持 png / jpg / bmp / pdf / docx / doc，扫描件自动 OCR 识别</div>
          </el-upload>
          <div class="upload-meta">
            <el-input v-model="uploadForm.title" placeholder="试卷标题（不填则用文件名）" style="width: 260px" />
            <el-select v-model="uploadForm.courseId" placeholder="关联课程（可选）" clearable style="width: 180px">
              <el-option v-for="c in courses" :key="c.id" :label="c.courseName || c.name" :value="c.id" />
            </el-select>
          </div>
        </el-card>

        <el-table v-loading="loading" :data="scans" stripe style="margin-top: 12px">
          <el-table-column prop="title" label="试卷标题" min-width="180" show-overflow-tooltip />
          <el-table-column label="类型" width="70">
            <template #default="{ row }">{{ row.fileType }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="questionCount" label="题目数" width="70" />
          <el-table-column label="难度" width="80">
            <template #default="{ row }">{{ row.difficulty ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="提取方式" width="100">
            <template #default="{ row }">{{ methodText(row.method) }}</template>
          </el-table-column>
          <el-table-column prop="createTime" label="录入时间" width="160" />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'PARSED'" size="small" type="primary" @click="parseScan(row)">
                AI 解析生成题库
              </el-button>
              <el-button size="small" @click="openDetail(row)">查看</el-button>
              <el-button size="small" type="danger" @click="removeScan(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ============ 题库管理 ============ -->
      <el-tab-pane label="题库管理" name="bank">
        <div class="toolbar">
          <el-select v-model="bankQuery.type" placeholder="题型" clearable style="width: 120px">
            <el-option label="单选" value="SINGLE" />
            <el-option label="多选" value="MULTI" />
            <el-option label="填空" value="FILL" />
            <el-option label="判断" value="JUDGE" />
            <el-option label="简答" value="ESSAY" />
          </el-select>
          <el-input v-model="bankQuery.keyword" placeholder="题干 / 知识点关键词" clearable style="width: 220px" @keyup.enter="loadBank" />
          <el-button type="primary" @click="loadBank">查询</el-button>
          <div class="toolbar-right">
            <el-button type="success" :disabled="!selectedBankIds.length" @click="openToHomework">
              加入作业（已选 {{ selectedBankIds.length }}）
            </el-button>
          </div>
        </div>

        <el-table v-loading="bankLoading" :data="bankRows" stripe @selection-change="onBankSelect">
          <el-table-column type="selection" width="45" />
          <el-table-column label="题型" width="70">
            <template #default="{ row }">{{ typeText(row.questionType) }}</template>
          </el-table-column>
          <el-table-column prop="stem" label="题干" min-width="220" show-overflow-tooltip />
          <el-table-column prop="knowledgePoint" label="知识点" width="110" show-overflow-tooltip />
          <el-table-column prop="score" label="分值" width="70" />
          <el-table-column label="难度" width="70">
            <template #default="{ row }">{{ row.difficulty || '中' }}</template>
          </el-table-column>
          <el-table-column prop="answer" label="答案" width="110" show-overflow-tooltip />
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="openBankEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="removeBank(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- ============ 录入详情 ============ -->
    <el-dialog v-model="detailVisible" :title="detail?.title || '试卷详情'" width="880px" top="4vh">
      <el-alert v-if="detail?.aiSummary" type="success" :closable="false" style="margin-bottom: 10px">
        <b>AI 试卷分析：</b>{{ detail.aiSummary }}（难度 {{ detail.difficulty ?? '—' }}）
      </el-alert>
      <el-alert v-if="detail?.errorMsg" type="error" :closable="false" style="margin-bottom: 10px">{{ detail.errorMsg }}</el-alert>

      <div class="section-title">识别文本</div>
      <el-input
        v-model="detailOcrText"
        type="textarea"
        :rows="6"
        placeholder="识别出的试卷全文（可手动修正后重新解析）"
        style="margin-bottom: 10px"
      />

      <div class="section-title">解析题目（{{ detail?.questions?.length || 0 }} 题）</div>
      <el-table :data="detail?.questions || []" stripe size="small">
        <el-table-column prop="questionNo" label="题号" width="60" />
        <el-table-column label="题型" width="70">
          <template #default="{ row }">{{ typeText(row.questionType) }}</template>
        </el-table-column>
        <el-table-column prop="stem" label="题干" min-width="200" show-overflow-tooltip />
        <el-table-column prop="knowledgePoint" label="知识点" width="100" show-overflow-tooltip />
        <el-table-column prop="score" label="分值" width="60" />
        <el-table-column prop="answer" label="答案" width="100" show-overflow-tooltip />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button size="small" @click="openBankEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="detail?.status === 'PARSED' || detail?.status === 'REVIEWED'" type="primary" :loading="parseLoading" @click="parseScan(detail)">
          重新解析生成题库
        </el-button>
      </template>
    </el-dialog>

    <!-- ============ 题目编辑 ============ -->
    <el-dialog v-model="editVisible" title="编辑题目" width="680px">
      <el-form label-width="80px">
        <el-form-item label="题型">
          <el-select v-model="editForm.questionType" style="width: 200px">
            <el-option label="单选" value="SINGLE" />
            <el-option label="多选" value="MULTI" />
            <el-option label="填空" value="FILL" />
            <el-option label="判断" value="JUDGE" />
            <el-option label="简答" value="ESSAY" />
          </el-select>
        </el-form-item>
        <el-form-item label="题干"><el-input v-model="editForm.stem" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="选项">
          <el-input
            v-model="editOptionsText"
            type="textarea"
            :rows="3"
            placeholder='每行一个选项，如 "A. 2x"（选择题适用，其余题型留空）'
          />
        </el-form-item>
        <el-form-item label="答案"><el-input v-model="editForm.answer" /></el-form-item>
        <el-form-item label="解析"><el-input v-model="editForm.analysis" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="知识点"><el-input v-model="editForm.knowledgePoint" /></el-form-item>
        <el-form-item label="分值"><el-input-number v-model="editForm.score" :min="0" :precision="1" /></el-form-item>
        <el-form-item label="难度">
          <el-select v-model="editForm.difficulty" style="width: 120px">
            <el-option label="易" value="易" />
            <el-option label="中" value="中" />
            <el-option label="难" value="难" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="saveBankEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- ============ 加入作业 ============ -->
    <el-dialog v-model="hwVisible" title="题库题目一键布置作业" width="520px">
      <el-form label-width="90px">
        <el-form-item label="作业标题"><el-input v-model="hwForm.title" placeholder="如：第三章 课后巩固作业" /></el-form-item>
        <el-form-item label="班级">
          <el-select v-model="hwForm.clazzId" placeholder="选择班级" style="width: 100%">
            <el-option v-for="c in clazzes" :key="c.id" :label="c.clazzName || c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程">
          <el-select v-model="hwForm.courseId" placeholder="关联课程" clearable style="width: 100%">
            <el-option v-for="c in courses" :key="c.id" :label="c.courseName || c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker v-model="hwForm.deadline" type="datetime" placeholder="不填则不限制" style="width: 100%" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="hwVisible = false">取消</el-button>
        <el-button type="primary" :loading="hwLoading" @click="submitToHomework">布置作业</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { listBase } from '@/api/base'
import {
  uploadPaper, paperScanList, paperScanDetail, parsePaper, deletePaper,
  bankList, bankEdit, bankDelete, bankToHomework
} from '@/api/scan'

const activeTab = ref('scan')
const loading = ref(false)
const bankLoading = ref(false)
const scans = ref([])
const bankRows = ref([])
const selectedBankIds = ref([])
const courses = ref([])
const clazzes = ref([])

const uploadForm = reactive({ title: '', courseId: null })
const bankQuery = reactive({ type: null, keyword: '' })

// 详情
const detailVisible = ref(false)
const detail = ref(null)
const detailOcrText = ref('')
const parseLoading = ref(false)

// 编辑
const editVisible = ref(false)
const editForm = reactive({ id: null, questionType: 'SINGLE', stem: '', answer: '', analysis: '', score: 5, knowledgePoint: '', difficulty: '中' })
const editOptionsText = ref('')

// 加入作业
const hwVisible = ref(false)
const hwLoading = ref(false)
const hwForm = reactive({ title: '', clazzId: null, courseId: null, courseName: '', deadline: null })

const statusText = s => ({ PENDING: '待解析', PARSED: '已提取', REVIEWED: '已入库', FAILED: '失败' }[s] || s)
const statusTag = s => ({ PENDING: 'info', PARSED: 'warning', REVIEWED: 'success', FAILED: 'danger' }[s] || 'info')
const methodText = m => ({ PDF_TEXT: 'PDF文本', PDF_OCR: 'PDF扫描OCR', DOCX: 'Word文本', IMAGE_OCR: '图片OCR' }[m] || m || '—')
const typeText = t => ({ SINGLE: '单选', MULTI: '多选', FILL: '填空', JUDGE: '判断', ESSAY: '简答' }[t] || t)

function fmtOpts(optsJson) {
  try {
    const arr = JSON.parse(optsJson || '[]')
    return arr.join('\n')
  } catch { return '' }
}

async function handleUpload({ file }) {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('title', uploadForm.title)
  if (uploadForm.courseId) fd.append('courseId', uploadForm.courseId)
  try {
    const res = await uploadPaper(fd)
    if (res.code !== 200) throw new Error(res.message || '上传失败')
    ElMessage.success('上传成功，文本已提取')
    uploadForm.title = ''
    loadScans()
  } catch (e) {
    ElMessage.error(e.message || '上传失败')
  }
}

async function loadScans() {
  loading.value = true
  try {
    const res = await paperScanList({})
    if (res.code === 200) scans.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function parseScan(row) {
  parseLoading.value = true
  try {
    const res = await parsePaper(row.id)
    if (res.code !== 200) throw new Error(res.message)
    ElMessage.success(`AI 解析完成，生成 ${res.data.questionCount} 道题目`)
    if (detailVisible.value) openDetail(res.data)
    loadScans()
    loadBank()
  } catch (e) {
    ElMessage.error(e.message || '解析失败')
  } finally {
    parseLoading.value = false
  }
}

async function openDetail(row) {
  const res = await paperScanDetail(row.id)
  if (res.code === 200) {
    detail.value = res.data
    detailOcrText.value = res.data.ocrText || ''
    detailVisible.value = true
  }
}

async function removeScan(row) {
  await ElMessageBox.confirm(`确认删除录入「${row.title}」及其解析题目？`, '提示', { type: 'warning' })
  await deletePaper(row.id)
  ElMessage.success('已删除')
  loadScans()
}

async function loadBank() {
  bankLoading.value = true
  try {
    const res = await bankList({ ...bankQuery, type: bankQuery.type || null, keyword: bankQuery.keyword || null })
    if (res.code === 200) bankRows.value = res.data || []
  } finally {
    bankLoading.value = false
  }
}

function onBankSelect(rows) {
  selectedBankIds.value = rows.map(r => r.id)
}

function openBankEdit(row) {
  Object.assign(editForm, {
    id: row.id,
    questionType: row.questionType || 'SINGLE',
    stem: row.stem || '',
    answer: row.answer || '',
    analysis: row.analysis || '',
    score: row.score != null ? Number(row.score) : 5,
    knowledgePoint: row.knowledgePoint || '',
    difficulty: row.difficulty || '中'
  })
  editOptionsText.value = fmtOpts(row.optionsJson)
  editVisible.value = true
}

async function saveBankEdit() {
  const optionsJson = JSON.stringify(editOptionsText.value.split('\n').map(s => s.trim()).filter(Boolean))
  const res = await bankEdit(editForm.id, { ...editForm, optionsJson })
  if (res.code === 200) {
    ElMessage.success('已保存')
    editVisible.value = false
    if (activeTab.value === 'bank') loadBank()
    if (detailVisible.value) openDetail(detail.value)
  } else {
    ElMessage.error(res.message)
  }
}

async function removeBank(row) {
  await ElMessageBox.confirm('确认删除该题目？', '提示', { type: 'warning' })
  await bankDelete(row.id)
  ElMessage.success('已删除')
  loadBank()
  if (detailVisible.value) openDetail(detail.value)
}

function openToHomework() {
  hwForm.title = ''
  hwForm.clazzId = null
  hwForm.courseId = null
  hwForm.courseName = ''
  hwForm.deadline = null
  hwVisible.value = true
}

async function submitToHomework() {
  if (!hwForm.title || !hwForm.clazzId) {
    ElMessage.warning('请填写作业标题并选择班级')
    return
  }
  hwLoading.value = true
  try {
    const course = courses.value.find(c => c.id === hwForm.courseId)
    const res = await bankToHomework({
      bankIds: selectedBankIds.value,
      title: hwForm.title,
      clazzId: hwForm.clazzId,
      courseId: hwForm.courseId || null,
      courseName: course ? (course.courseName || course.name) : '',
      deadline: hwForm.deadline || null
    })
    if (res.code === 200) {
      ElMessage.success(`作业已布置（#${res.data}），可在「作业管理」查看`)
      hwVisible.value = false
    } else {
      ElMessage.error(res.message)
    }
  } finally {
    hwLoading.value = false
  }
}

onMounted(async () => {
  loadScans()
  loadBank()
  const [c, cl] = await Promise.all([listBase('course'), listBase('clazz')])
  courses.value = c.data || []
  clazzes.value = cl.data || []
})
</script>

<style scoped>
.upload-card {
  border: 1px dashed #d9d9d9;
}
.upload-meta {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}
.toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}
.toolbar-right {
  margin-left: auto;
}
.section-title {
  font-weight: 600;
  margin: 8px 0 6px;
  color: #303133;
}
</style>
