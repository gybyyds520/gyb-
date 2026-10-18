<template>
  <div class="page-container">
    <div style="display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 16px">
      <el-select
        v-model="clazzId"
        placeholder="全部班级"
        clearable
        style="width: 200px"
        @change="loadList"
      >
        <el-option v-for="c in clazzes" :key="c.id" :label="c.clazzName" :value="c.id" />
      </el-select>
      <el-button :loading="loading" @click="loadList">刷新</el-button>
      <div style="flex: 1" />
      <el-button type="primary" @click="openCreate">布置新作业</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="list" border stripe size="small" v-loading="loading">
        <el-table-column prop="title" label="作业" min-width="160" show-overflow-tooltip />
        <el-table-column prop="courseName" label="课程" width="100" />
        <el-table-column prop="clazzName" label="班级" width="110" />
        <el-table-column prop="questionCount" label="题数" width="60" />
        <el-table-column label="截止时间" width="150">
          <template #default="{ row }">{{ formatTime(row.deadline) }}</template>
        </el-table-column>
        <el-table-column label="完成率" width="100">
          <template #default="{ row }">
            <span>{{ row.submitted }}/{{ row.studentTotal }}</span>
            <el-tag size="small" style="margin-left: 6px" :type="rateTag(row.submitRate)">{{ row.submitRate }}%</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="平均分" width="90">
          <template #default="{ row }">
            <span v-if="row.avgScore !== null && row.avgScore !== undefined">{{ row.avgScore }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? '进行中' : '已截止' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row.id)">批改</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !list.length" description="暂无作业，点击右上角「布置新作业」" />
    </el-card>

    <!-- 新建作业 -->
    <el-dialog v-model="createDialog" title="布置新作业" width="820px" destroy-on-close>
      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 12px">
        <el-input v-model="createForm.title" placeholder="作业标题，如：第三章 函数 课后练习" />
        <el-select v-model="createForm.clazzId" placeholder="布置班级" style="width: 100%">
          <el-option v-for="c in clazzes" :key="c.id" :label="c.clazzName" :value="c.id" />
        </el-select>
        <el-select v-model="createForm.courseId" placeholder="课程（选填）" clearable style="width: 100%">
          <el-option v-for="c in courses" :key="c.id" :label="c.courseName" :value="c.id" />
        </el-select>
        <el-date-picker
          v-model="createForm.deadline"
          type="datetime"
          placeholder="截止时间（选填）"
          value-format="YYYY-MM-DDTHH:mm:ss"
          style="width: 100%"
        />
      </div>
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px">
        <span style="font-weight: 600">题目列表</span>
        <el-button size="small" type="primary" plain @click="addQuestion">+ 添加题目</el-button>
      </div>
      <div v-for="(q, i) in createForm.questions" :key="i" class="q-row">
        <span class="q-no">{{ i + 1 }}</span>
        <el-select v-model="q.type" style="width: 96px">
          <el-option label="单选" value="CHOICE" />
          <el-option label="填空" value="FILL" />
          <el-option label="简答/作文" value="ESSAY" />
        </el-select>
        <el-input v-model="q.content" placeholder="题目内容" style="flex: 1; min-width: 160px" />
        <el-input v-model="q.answer" placeholder="答案（客观题用于自动批改）" style="width: 170px" />
        <el-input-number v-model="q.score" :min="1" :max="100" :step="5" placeholder="分值" style="width: 110px" />
        <el-input v-model="q.knowledgePoint" placeholder="知识点（错题归类用）" style="width: 150px" />
        <el-button link type="danger" size="small" @click="removeQuestion(i)">删除</el-button>
      </div>
      <el-empty v-if="!createForm.questions.length" description="请添加至少一道题目" :image-size="50" />
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">发布作业</el-button>
      </template>
    </el-dialog>

    <!-- 作业详情 + 批改 -->
    <el-dialog v-model="detailDialog" :title="detail?.title || '作业详情'" width="900px" destroy-on-close>
      <div v-loading="detailLoading">
        <template v-if="detail">
          <p style="color: #909399; font-size: 13px; margin-top: 0">
            {{ detail.courseName }} · {{ detail.clazzName }} · 满分 {{ detail.totalScore }}
            · 完成 {{ detail.submitted }}/{{ detail.studentTotal }}（{{ detail.submitRate }}%）
            <span v-if="detail.avgScore !== null && detail.avgScore !== undefined"> · 平均分 {{ detail.avgScore }}</span>
          </p>
          <el-table :data="detail.submissions || []" border stripe size="small">
            <el-table-column prop="studentNo" label="学号" width="110" />
            <el-table-column prop="studentName" label="姓名" width="90" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="得分" width="90">
              <template #default="{ row }">
                <span v-if="row.score !== null && row.score !== undefined">{{ row.score }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="提交时间" width="150">
              <template #default="{ row }">{{ formatTime(row.submitTime) }}</template>
            </el-table-column>
            <el-table-column label="评语" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.feedback || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <template v-if="row.submissionId">
                  <el-button
                    link type="primary" size="small"
                    :loading="aiGradingId === row.submissionId"
                    @click="aiGrade(row)"
                  >AI 批改</el-button>
                  <el-button link type="warning" size="small" @click="openManualGrade(row)">人工批改</el-button>
                </template>
                <span v-else style="color: #c0c4cc">未提交</span>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
      <template #footer>
        <el-button @click="detailDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 人工批改 -->
    <el-dialog v-model="gradeDialog" title="人工批改" width="520px" destroy-on-close>
      <p style="margin-top: 0">
        学生：{{ gradingRow?.studentName }}（{{ gradingRow?.studentNo }}）
        <el-tag size="small" style="margin-left: 6px">客观题自动得分 {{ gradingRow?.autoScore }}</el-tag>
      </p>
      <el-input-number v-model="gradeForm.score" :min="0" :max="Number(detail?.totalScore || 100)" :step="1" style="width: 160px" />
      <el-input v-model="gradeForm.feedback" type="textarea" :rows="3" placeholder="批改评语（可结合学生作答填写）" style="margin-top: 10px" />
      <template #footer>
        <el-button @click="gradeDialog = false">取消</el-button>
        <el-button type="primary" :loading="grading" @click="submitGrade">保存批改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listBase } from '@/api/base'
import { teacherHomeworkList, teacherHomeworkDetail, createHomework, gradeHomework, aiGradeHomework } from '@/api/homework'

const clazzes = ref([])
const courses = ref([])
const clazzId = ref(null)
const list = ref([])
const loading = ref(false)

const createDialog = ref(false)
const creating = ref(false)
const createForm = ref({ title: '', clazzId: null, courseId: null, deadline: null, questions: [] })

const detailDialog = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const aiGradingId = ref(null)

const gradeDialog = ref(false)
const grading = ref(false)
const gradingRow = ref(null)
const gradeForm = ref({ score: null, feedback: '' })

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').substring(0, 16)
}

function rateTag(v) {
  const n = Number(v)
  if (n >= 90) return 'success'
  if (n >= 60) return 'warning'
  return 'danger'
}

function statusText(s) {
  return { PENDING: '未提交', SUBMITTED: '已提交', AUTO_GRADED: '客观题已批', GRADED: '已批' }[s] || s || '-'
}

function statusTag(s) {
  return { PENDING: 'info', SUBMITTED: 'warning', AUTO_GRADED: 'warning', GRADED: 'success' }[s] || 'info'
}

async function loadList() {
  loading.value = true
  try {
    list.value = await teacherHomeworkList(clazzId.value)
  } catch (e) {
    ElMessage.error(e.message || '作业列表加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  createForm.value = { title: '', clazzId: null, courseId: null, deadline: null, questions: [] }
  addQuestion()
  createDialog.value = true
}

function addQuestion() {
  createForm.value.questions.push({ type: 'CHOICE', content: '', answer: '', score: 10, knowledgePoint: '' })
}

function removeQuestion(i) {
  createForm.value.questions.splice(i, 1)
}

async function submitCreate() {
  const f = createForm.value
  if (!f.title.trim()) return ElMessage.warning('请输入作业标题')
  if (!f.clazzId) return ElMessage.warning('请选择布置班级')
  if (!f.questions.length) return ElMessage.warning('请至少添加一道题目')
  const questions = f.questions.map((q, i) => ({
    no: String(i + 1),
    type: q.type,
    content: q.content,
    answer: q.answer,
    score: q.score || 0,
    knowledgePoint: q.knowledgePoint
  }))
  const course = courses.value.find((c) => c.id === f.courseId)
  creating.value = true
  try {
    await createHomework({
      title: f.title,
      clazzId: f.clazzId,
      courseId: f.courseId,
      courseName: course ? course.courseName : undefined,
      deadline: f.deadline || undefined,
      questions
    })
    ElMessage.success('作业已发布')
    createDialog.value = false
    await loadList()
  } catch (e) {
    ElMessage.error(e.message || '发布失败')
  } finally {
    creating.value = false
  }
}

async function openDetail(id) {
  detailLoading.value = true
  detailDialog.value = true
  detail.value = null
  try {
    detail.value = await teacherHomeworkDetail(id)
  } catch (e) {
    ElMessage.error(e.message || '详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

async function aiGrade(row) {
  aiGradingId.value = row.submissionId
  try {
    const res = await aiGradeHomework(detail.value.id, row.submissionId)
    ElMessage.success(res.feedback ? `AI 批改完成：${res.feedback}` : 'AI 批改完成')
    await openDetail(detail.value.id)
  } catch (e) {
    ElMessage.error(e.message || 'AI 批改失败（请确认 Ollama 已启动）')
  } finally {
    aiGradingId.value = null
  }
}

function openManualGrade(row) {
  gradingRow.value = row
  gradeForm.value = { score: row.score ?? row.autoScore ?? null, feedback: row.feedback || '' }
  gradeDialog.value = true
}

async function submitGrade() {
  const row = gradingRow.value
  grading.value = true
  try {
    await gradeHomework(detail.value.id, row.submissionId, gradeForm.value)
    ElMessage.success('批改已保存')
    gradeDialog.value = false
    await openDetail(detail.value.id)
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    grading.value = false
  }
}

onMounted(async () => {
  try {
    clazzes.value = await listBase('clazz')
  } catch { /* 班级加载失败不阻塞 */ }
  try {
    courses.value = await listBase('course')
  } catch { /* 课程加载失败不阻塞 */ }
  await loadList()
})
</script>

<style scoped>
.q-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.q-no {
  width: 22px;
  font-weight: 600;
  color: #606266;
  flex-shrink: 0;
}
</style>
