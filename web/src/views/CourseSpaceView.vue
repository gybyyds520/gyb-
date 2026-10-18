<template>
  <el-card shadow="never">
    <el-empty v-if="!courses.length && !loading" description="暂无课程" />
    <el-row v-else :gutter="16">
      <!-- 左侧：我的课程 -->
      <el-col :xs="24" :md="5">
        <div class="course-list">
          <div class="list-title">我的课程（{{ courses.length }}）</div>
          <div
            v-for="c in courses"
            :key="c.offeringId"
            class="course-item"
            :class="{ active: activeOffering === c.offeringId }"
            @click="selectCourse(c.offeringId)"
          >
            <div class="course-name">{{ c.courseName }}</div>
            <div class="course-meta">{{ c.teacherName }} · {{ c.clazzName || '跨班选修' }}</div>
            <div class="course-meta" v-if="c.schedule">{{ c.schedule }} · {{ c.classroom || '—' }}</div>
          </div>
        </div>
      </el-col>

      <!-- 右侧：课程详情 -->
      <el-col :xs="24" :md="19">
        <div v-if="!activeOffering" class="empty-tip">请选择左侧课程进入学习空间</div>
        <div v-else>
          <el-tabs v-model="activeTab" @tab-change="onTabChange">
            <!-- ========== 章节 ========== -->
            <el-tab-pane label="章节" name="chapter">
              <div class="toolbar">
                <el-button v-if="isStaff" type="primary" size="small" @click="openChapterDialog">+ 新建章节</el-button>
                <span class="tip">按章节组织课程学习与测验</span>
              </div>
              <el-table v-loading="tabLoading" :data="chapters" stripe>
                <el-table-column label="序号" width="70">
                  <template #default="{ row }">第 {{ row.chapterNo }} 章</template>
                </el-table-column>
                <el-table-column prop="title" label="章节标题" min-width="180" />
                <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
                <el-table-column label="资料数" width="80">
                  <template #default="{ row }">{{ row.materialCount }}</template>
                </el-table-column>
                <el-table-column v-if="isStaff" label="操作" width="150" fixed="right">
                  <template #default="{ row }">
                    <el-button size="small" @click="openChapterEdit(row)">编辑</el-button>
                    <el-button size="small" type="danger" @click="removeChapter(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <!-- ========== 资料 ========== -->
            <el-tab-pane label="资料" name="material">
              <div class="toolbar">
                <el-button v-if="isStaff" type="primary" size="small" @click="fileInput.click()">+ 上传资料</el-button>
                <input ref="fileInput" type="file" class="hidden-input" @change="onUpload" />
                <el-select v-model="materialChapter" placeholder="全部章节" clearable size="small" style="width: 150px; margin-left: 8px" @change="loadMaterials">
                  <el-option v-for="ch in chapters" :key="ch.id" :label="ch.title" :value="ch.id" />
                </el-select>
              </div>
              <el-table v-loading="tabLoading" :data="materials" stripe>
                <el-table-column prop="fileName" label="文件名" min-width="220" show-overflow-tooltip />
                <el-table-column label="类型" width="80">
                  <template #default="{ row }">
                    <el-tag size="small" type="info">{{ row.fileType }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="大小" width="90">
                  <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
                </el-table-column>
                <el-table-column prop="createTime" label="上传时间" width="160" />
                <el-table-column label="操作" width="140" fixed="right">
                  <template #default="{ row }">
                    <el-button size="small" type="primary" @click="downloadMaterial(row)">下载</el-button>
                    <el-button v-if="isStaff" size="small" type="danger" @click="removeMaterial(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <!-- ========== 章节测验 ========== -->
            <el-tab-pane label="章节测验" name="quiz">
              <div class="toolbar">
                <el-button v-if="isStaff" type="primary" size="small" @click="openQuizDialog">+ 新建章节测验</el-button>
                <span v-if="isStudent" class="tip">作答自动判分，错题自动收录错题本</span>
              </div>
              <el-table v-loading="tabLoading" :data="quizList" stripe>
                <el-table-column prop="title" label="测验标题" min-width="180" />
                <el-table-column label="总分" width="80">
                  <template #default="{ row }">{{ row.totalScore }}</template>
                </el-table-column>
                <el-table-column label="截止" width="150">
                  <template #default="{ row }">{{ row.deadline || '—' }}</template>
                </el-table-column>
                <el-table-column label="完成度" width="110">
                  <template #default="{ row }">{{ row.submittedCount || 0 }}/{{ row.totalStudents || 0 }}</template>
                </el-table-column>
                <el-table-column label="平均分" width="90">
                  <template #default="{ row }">{{ row.avgScore ?? '—' }}</template>
                </el-table-column>
                <el-table-column label="操作" width="180" fixed="right">
                  <template #default="{ row }">
                    <el-button v-if="isStaff" size="small" @click="viewQuizDetail(row)">详情</el-button>
                    <template v-else>
                      <el-button size="small" type="primary" :disabled="row.submitted" @click="openAnswer(row)">{{ row.submitted ? '已作答' : '去作答' }}</el-button>
                      <el-button v-if="row.submitted" size="small" @click="openAnswer(row, true)">查看得分</el-button>
                    </template>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <!-- ========== 课堂签到 ========== -->
            <el-tab-pane label="课堂签到" name="checkin">
              <div v-if="isStaff" class="toolbar">
                <el-button type="primary" size="small" @click="startCheckin">+ 发起签到</el-button>
              </div>
              <el-alert
                v-if="activeCheckin"
                :title="'签到进行中 · 签到码：' + activeCheckin.code"
                type="success"
                :closable="false"
                style="margin: 8px 0"
              >
                <template #default>
                  <div class="checkin-code">{{ activeCheckin.code }}</div>
                  <div class="checkin-code-sub">{{ activeCheckin.title }} · 已到 {{ activeCheckin.signedCount }} 人</div>
                  <el-button v-if="isStaff" size="small" type="warning" @click="closeCheckin(activeCheckin)">结束签到</el-button>
                </template>
              </el-alert>
              <el-table v-loading="tabLoading" :data="checkinList" stripe>
                <el-table-column prop="title" label="签到标题" min-width="160" />
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '进行中' : '已结束' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="startTime" label="发起时间" width="160" />
                <el-table-column label="已到" width="80">
                  <template #default="{ row }">{{ row.signedCount }}</template>
                </el-table-column>
                <el-table-column v-if="isStaff" label="操作" width="180" fixed="right">
                  <template #default="{ row }">
                    <el-button size="small" @click="openStats(row)">统计/补签</el-button>
                  </template>
                </el-table-column>
                <el-table-column v-else label="我的状态" width="120">
                  <template #default="{ row }">
                    <el-tag v-if="row.signed" type="success">已签到</el-tag>
                    <el-button v-else-if="row.status === 'ACTIVE'" size="small" type="primary" @click="openSign(row)">去签到</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <!-- ========== 讨论区 ========== -->
            <el-tab-pane label="讨论区" name="discussion">
              <div class="disc-publish">
                <el-input v-model="newTopic" type="textarea" :rows="2" placeholder="发起一个话题讨论…" />
                <el-button type="primary" size="small" style="margin-top: 6px" @click="publishTopic">发布</el-button>
              </div>
              <div v-loading="tabLoading" class="disc-list">
                <div v-for="d in discussions" :key="d.id" class="disc-item">
                  <div class="disc-head">
                    <el-tag v-if="d.pinned" size="small" type="danger" style="margin-right: 6px">置顶</el-tag>
                    <el-tag v-if="d.authorType === 'TEACHER'" size="small" type="warning" style="margin-right: 6px">教师</el-tag>
                    <span class="disc-author">{{ d.authorName }}</span>
                    <span class="disc-time">{{ d.createTime }}</span>
                    <div class="disc-ops">
                      <el-button size="small" text type="primary" @click="likeTopic(d)">赞 {{ d.likeCount }}</el-button>
                      <el-button v-if="isStaff" size="small" text @click="togglePin(d)">{{ d.pinned ? '取消置顶' : '置顶' }}</el-button>
                    </div>
                  </div>
                  <div class="disc-content">{{ d.content }}</div>
                  <div v-for="r in d.replies" :key="r.id" class="disc-reply">
                    <el-tag v-if="r.authorType === 'TEACHER'" size="small" type="warning" style="margin-right: 4px">教师</el-tag>
                    <span class="disc-author">{{ r.authorName }}：</span>
                    <span>{{ r.content }}</span>
                  </div>
                  <div class="disc-reply-box">
                    <el-input v-model="replyMap[d.id]" placeholder="回复…" size="small" style="width: 300px" @keyup.enter="replyTopic(d)" />
                    <el-button size="small" @click="replyTopic(d)">回复</el-button>
                  </div>
                </div>
                <el-empty v-if="!discussions.length" description="还没有讨论话题" />
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>
      </el-col>
    </el-row>

    <!-- 章节对话框 -->
    <el-dialog v-model="chapterDialog.visible" :title="chapterDialog.id ? '编辑章节' : '新建章节'" width="480">
      <el-form label-width="70px">
        <el-form-item label="章节序号">
          <el-input-number v-model="chapterDialog.chapterNo" :min="1" />
        </el-form-item>
        <el-form-item label="标题"><el-input v-model="chapterDialog.title" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="chapterDialog.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="chapterDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveChapter">保存</el-button>
      </template>
    </el-dialog>

    <!-- 签到统计 -->
    <el-dialog v-model="statsDialog.visible" :title="'签到统计：' + statsDialog.title" width="560">
      <el-alert :title="'应到 ' + statsDialog.data.total + ' 人 · 已到 ' + statsDialog.data.signedCount + ' 人'" type="info" :closable="false" style="margin-bottom: 10px" />
      <el-tabs>
        <el-tab-pane :label="'已到 (' + (statsDialog.data.signedList || []).length + ')'">
          <el-table :data="statsDialog.data.signedList || []" size="small">
            <el-table-column prop="studentNo" label="学号" width="120" />
            <el-table-column prop="name" label="姓名" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="'未到 (' + (statsDialog.data.absentList || []).length + ')'">
          <el-table :data="statsDialog.data.absentList || []" size="small">
            <el-table-column prop="studentNo" label="学号" width="120" />
            <el-table-column prop="name" label="姓名" />
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button size="small" type="warning" @click="manualSign(row)">补签</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <!-- 测验新建/作答 -->
    <el-dialog v-model="quizDialog.visible" :title="quizDialog.title" width="720" top="6vh">
      <template v-if="quizDialog.mode === 'create'">
        <el-form label-width="80px">
          <el-form-item label="测验标题"><el-input v-model="quizForm.title" placeholder="如：第一章 绪论测验" /></el-form-item>
          <el-form-item label="截止时间"><el-date-picker v-model="quizForm.deadline" type="datetime" placeholder="不填则长期有效" /></el-form-item>
        </el-form>
        <div class="toolbar">
          <span class="tip">题目（客观题自动判分，简答题人工/AI 批改）</span>
          <el-button size="small" type="primary" plain @click="openBankPicker">从题库选题</el-button>
          <el-button size="small" @click="addBlankQuestion">+ 手动加题</el-button>
        </div>
        <div v-for="(q, i) in quizForm.questions" :key="i" class="question-editor">
          <el-row :gutter="6" align="middle">
            <el-col :span="2">题{{ i + 1 }}</el-col>
            <el-col :span="5">
              <el-select v-model="q.type" size="small">
                <el-option label="单选" value="CHOICE" />
                <el-option label="填空" value="FILL" />
                <el-option label="判断" value="JUDGE" />
                <el-option label="简答" value="ESSAY" />
              </el-select>
            </el-col>
            <el-col :span="6"><el-input v-model="q.content" placeholder="题干" size="small" /></el-col>
            <el-col :span="6"><el-input v-model="q.answer" placeholder="答案（判断填 对/错）" size="small" /></el-col>
            <el-col :span="3"><el-input-number v-model="q.score" :min="1" size="small" /></el-col>
            <el-col :span="2"><el-button size="small" type="danger" text @click="removeQuestion(i)">删</el-button></el-col>
          </el-row>
        </div>
      </template>
      <template v-else>
        <div class="quiz-title">{{ quizForm.title }}</div>
        <div v-for="(q, i) in quizForm.questions" :key="i" class="question-answer">
          <div class="q-content">【{{ typeText(q.type) }}】{{ i + 1 }}. {{ q.content }}（{{ q.score }} 分）</div>
          <div v-if="q.type === 'CHOICE'">
            <el-radio-group v-model="answerMap[q.no]">
              <el-radio value="A">A</el-radio><el-radio value="B">B</el-radio>
              <el-radio value="C">C</el-radio><el-radio value="D">D</el-radio>
            </el-radio-group>
          </div>
          <div v-else-if="q.type === 'FILL'">
            <el-input v-model="answerMap[q.no]" placeholder="填写答案" style="width: 240px" />
          </div>
          <div v-else-if="q.type === 'JUDGE'">
            <el-radio-group v-model="answerMap[q.no]">
              <el-radio value="对">对</el-radio><el-radio value="错">错</el-radio>
            </el-radio-group>
          </div>
          <div v-else>
            <el-input v-model="answerMap[q.no]" type="textarea" :rows="2" placeholder="作答内容" />
          </div>
          <div v-if="quizDialog.readonly && q.correct !== undefined" class="q-result" :class="q.correct ? 'ok' : 'no'">
            {{ q.correct ? '✓ 正确' : '✗ 错误 · 参考答案：' + q.answer }}
          </div>
        </div>
      </template>
      <template #footer>
        <el-button @click="quizDialog.visible = false">关闭</el-button>
        <el-button v-if="quizDialog.mode === 'create'" type="primary" @click="publishQuiz">发布测验</el-button>
        <el-button v-else-if="!quizDialog.readonly" type="primary" @click="submitQuiz">提交作答</el-button>
      </template>
    </el-dialog>

    <!-- 题库选题 -->
    <el-dialog v-model="bankDialog.visible" title="从题库选题（勾选加入测验）" width="760" top="6vh">
      <div class="toolbar">
        <el-input v-model="bankQuery.keyword" placeholder="关键词搜索" size="small" style="width: 180px" @keyup.enter="loadBank" />
        <el-select v-model="bankQuery.type" placeholder="题型" clearable size="small" style="width: 110px">
          <el-option label="单选" value="SINGLE" /><el-option label="多选" value="MULTI" />
          <el-option label="填空" value="FILL" /><el-option label="判断" value="JUDGE" /><el-option label="简答" value="ESSAY" />
        </el-select>
        <el-button size="small" @click="loadBank">搜索</el-button>
      </div>
      <el-table v-loading="bankLoading" :data="bankList" size="small" @selection-change="onBankSelect" style="margin-top: 8px">
        <el-table-column type="selection" width="40" />
        <el-table-column prop="questionType" label="题型" width="70" />
        <el-table-column prop="stem" label="题干" min-width="260" show-overflow-tooltip />
        <el-table-column prop="knowledgePoint" label="知识点" width="120" show-overflow-tooltip />
        <el-table-column prop="score" label="分值" width="70" />
      </el-table>
      <div class="toolbar" style="margin-top: 8px">
        <el-button type="primary" size="small" @click="importBank">导入选中题目（{{ bankSelected.length }} 题）</el-button>
      </div>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import { useUserStore } from '@/stores/user'
import {
  myCourses, chapters as fetchChapters, createChapter, updateChapter, deleteChapter,
  materials as fetchMaterials, uploadMaterial, deleteMaterial,
  discussions as fetchDiscussions, createDiscussion, replyDiscussion, likeDiscussion, pinDiscussion
} from '@/api/courseSpace'
import {
  createCheckin, signCheckin, closeCheckin as closeCheckinApi, manualSign as manualSignApi,
  listCheckins, checkinStats, studentCheckins
} from '@/api/checkin'
import { createHomework, submitHomework, studentHomeworkDetail, offeringQuizzes, studentQuizzes } from '@/api/homework'

const userStore = useUserStore()
const isStaff = computed(() => userStore.isStaff)
const isStudent = computed(() => userStore.isStudent)

const loading = ref(false)
const courses = ref([])
const activeOffering = ref(null)
const activeTab = ref('chapter')
const tabLoading = ref(false)

const chapters = ref([])
const materials = ref([])
const materialChapter = ref(null)
const quizList = ref([])
const checkinList = ref([])
const discussions = ref([])
const newTopic = ref('')
const replyMap = ref({})
const fileInput = ref(null)

onMounted(loadCourses)

async function loadCourses() {
  loading.value = true
  try {
    courses.value = (await myCourses()) || []
    if (courses.value.length && !activeOffering.value) {
      selectCourse(courses.value[0].offeringId)
    }
  } finally {
    loading.value = false
  }
}

function selectCourse(id) {
  activeOffering.value = id
  activeTab.value = 'chapter'
  loadChapters()
  loadMaterials()
  loadQuizzes()
  loadCheckins()
  loadDiscussions()
}

function onTabChange(name) {
  if (name === 'chapter') loadChapters()
  else if (name === 'material') loadMaterials()
  else if (name === 'quiz') loadQuizzes()
  else if (name === 'checkin') loadCheckins()
  else if (name === 'discussion') loadDiscussions()
}

// ---------- 章节 ----------
async function loadChapters() {
  if (!activeOffering.value) return
  tabLoading.value = true
  try { chapters.value = (await fetchChapters(activeOffering.value)) || [] } finally { tabLoading.value = false }
}

const chapterDialog = ref({ visible: false, id: null, chapterNo: 1, title: '', description: '' })
function openChapterDialog() {
  chapterDialog.value = { visible: true, id: null, chapterNo: chapters.value.length + 1, title: '', description: '' }
}
function openChapterEdit(row) {
  chapterDialog.value = { visible: true, id: row.id, chapterNo: row.chapterNo, title: row.title, description: row.description || '' }
}
async function saveChapter() {
  const d = chapterDialog.value
  if (!d.title) return ElMessage.warning('请输入章节标题')
  if (d.id) {
    await updateChapter(d.id, { title: d.title, description: d.description, chapterNo: d.chapterNo })
    ElMessage.success('章节已更新')
  } else {
    await createChapter(activeOffering.value, { title: d.title, description: d.description, chapterNo: d.chapterNo })
    ElMessage.success('章节已创建')
  }
  d.visible = false
  loadChapters()
}
async function removeChapter(row) {
  await ElMessageBox.confirm(`确定删除章节「${row.title}」？`, '提示', { type: 'warning' })
  await deleteChapter(row.id)
  ElMessage.success('已删除')
  loadChapters()
}

// ---------- 资料 ----------
async function loadMaterials() {
  if (!activeOffering.value) return
  tabLoading.value = true
  try {
    materials.value = (await fetchMaterials(activeOffering.value, materialChapter.value)) || []
  } finally { tabLoading.value = false }
}
async function onUpload(e) {
  const file = e.target.files[0]
  if (!file) return
  const fd = new FormData()
  fd.append('file', file)
  if (materialChapter.value) fd.append('chapterId', materialChapter.value)
  await uploadMaterial(activeOffering.value, fd)
  ElMessage.success('资料上传成功')
  e.target.value = ''
  loadMaterials()
}
async function removeMaterial(row) {
  await ElMessageBox.confirm(`确定删除资料「${row.fileName}」？`, '提示', { type: 'warning' })
  await deleteMaterial(row.id)
  ElMessage.success('已删除')
  loadMaterials()
}
async function downloadMaterial(row) {
  try {
    const blob = await request.get(`/course-space/materials/${row.id}/file`, { responseType: 'blob' })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.fileName
    a.click()
    window.URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('下载失败')
  }
}
function formatSize(size) {
  if (!size) return '—'
  if (size > 1048576) return (size / 1048576).toFixed(1) + ' MB'
  return (size / 1024).toFixed(0) + ' KB'
}

// ---------- 测验 ----------
async function loadQuizzes() {
  if (!activeOffering.value) return
  tabLoading.value = true
  try {
    if (isStaff.value) {
      quizList.value = (await offeringQuizzes(activeOffering.value)) || []
    } else {
      quizList.value = (await studentQuizzes()) || []
    }
  } finally { tabLoading.value = false }
}

const quizDialog = ref({ visible: false, mode: 'create', title: '', readonly: false })
const quizForm = ref({ title: '', deadline: null, questions: [] })
const answerMap = ref({})

function openQuizDialog() {
  quizForm.value = { title: '', deadline: null, questions: [] }
  quizDialog.value = { visible: true, mode: 'create', title: '新建章节测验', readonly: false }
}
function addBlankQuestion() {
  quizForm.value.questions.push({ no: 'Q' + (quizForm.value.questions.length + 1), type: 'CHOICE', content: '', answer: '', score: 10 })
}
function removeQuestion(i) {
  quizForm.value.questions.splice(i, 1)
}
async function publishQuiz() {
  if (!quizForm.value.title) return ElMessage.warning('请输入测验标题')
  if (!quizForm.value.questions.length) return ElMessage.warning('请至少添加一道题目')
  const course = courses.value.find((c) => c.offeringId === activeOffering.value)
  const questions = quizForm.value.questions.map((q, i) => ({ ...q, no: String(i + 1) }))
  await createHomework({
    title: quizForm.value.title,
    courseId: course?.courseId || null,
    courseName: course?.courseName || '',
    clazzId: null,
    courseOfferingId: activeOffering.value,
    questions,
    deadline: quizForm.value.deadline
  })
  ElMessage.success('测验已发布，学生可在课程空间作答')
  quizDialog.value.visible = false
  loadQuizzes()
}

async function openAnswer(row, readonly = false) {
  const hw = await studentHomeworkDetail(row.id)
  quizForm.value = { title: hw.title, questions: hw.questions.map((q) => ({ ...q, no: q.no })) }
  answerMap.value = {}
  if (readonly) {
    const answers = {}
    ;(hw.submission?.answers || []).forEach((a) => { answers[a.no] = a.answer })
    answerMap.value = answers
    const questions = hw.questions || []
    const rightMap = {}
    questions.forEach((q) => {
      const stu = (answers[q.no] || '').trim().toUpperCase()
      const ans = (q.answer || '').trim().toUpperCase()
      const ok = q.type === 'JUDGE'
        ? judgeEq(answers[q.no] || '', q.answer || '')
        : stu === ans
      rightMap[q.no] = ok
    })
    quizForm.value.questions = questions.map((q) => ({ ...q, correct: rightMap[q.no] }))
  }
  quizDialog.value = { visible: true, mode: 'answer', title: hw.title, homeworkId: row.id, readonly }
}

function judgeEq(a, b) {
  const na = ['对', '√', 'T', '正确', 'TRUE', 'YES', 'true'].includes(a.trim()) ? 'T' : 'F'
  const nb = ['对', '√', 'T', '正确', 'TRUE', 'YES', 'true'].includes(b.trim()) ? 'T' : 'F'
  return na === nb
}

async function submitQuiz() {
  const answers = Object.entries(answerMap.value).map(([no, answer]) => ({ no, answer: String(answer ?? '') }))
  await submitHomework(quizDialog.value.homeworkId, answers)
  ElMessage.success('作答已提交并自动判分')
  quizDialog.value.visible = false
  loadQuizzes()
}

async function viewQuizDetail(row) {
  await ElMessageBox.alert(`测验「${row.title}」：总分 ${row.totalScore}，完成 ${row.submittedCount || 0}/${row.totalStudents || 0} 人，平均分 ${row.avgScore ?? '—'}`, '测验详情')
}

// ---------- 签到 ----------
async function loadCheckins() {
  if (!activeOffering.value) return
  tabLoading.value = true
  try {
    if (isStaff.value) {
      checkinList.value = (await listCheckins(activeOffering.value)) || []
    } else {
      const mine = (await studentCheckins()) || []
      checkinList.value = mine.filter((c) => c.offeringId === activeOffering.value)
    }
  } finally { tabLoading.value = false }
}

const activeCheckin = computed(() => checkinList.value.find((c) => c.status === 'ACTIVE'))

async function startCheckin() {
  await ElMessageBox.prompt('请输入签到标题（默认：课堂签到）', '发起签到', {
    confirmButtonText: '发起',
    inputValue: '课堂签到'
  }).then(async ({ value }) => {
    const r = await createCheckin(activeOffering.value, value || '课堂签到')
    ElMessage.success(`签到已发起，签到码：${r.code}`)
    loadCheckins()
  }).catch(() => {})
}

async function closeCheckin(row) {
  await closeCheckinApi(row.id)
  ElMessage.success('签到已结束')
  loadCheckins()
}

const statsDialog = ref({ visible: false, title: '', data: { total: 0, signedCount: 0, signedList: [], absentList: [] } })
async function openStats(row) {
  const r = await checkinStats(row.id)
  statsDialog.value = { visible: true, title: row.title, checkinId: row.id, data: r }
}
async function manualSign(row) {
  await ElMessageBox.confirm(`为「${row.name}」补签？`, '补签', { type: 'warning' })
  await manualSignApi(statsDialog.value.checkinId, row.studentId)
  ElMessage.success('补签成功')
}
async function openSign(row) {
  const { value } = await ElMessageBox.prompt(`请输入「${row.title}」的签到码`, '课堂签到', {
    confirmButtonText: '签到', inputPlaceholder: '6 位签到码'
  })
  await signCheckin(row.id, value.trim())
  ElMessage.success('签到成功，已记录考勤')
  loadCheckins()
}

// ---------- 讨论区 ----------
async function loadDiscussions() {
  if (!activeOffering.value) return
  tabLoading.value = true
  try { discussions.value = (await fetchDiscussions(activeOffering.value)) || [] } finally { tabLoading.value = false }
}
async function publishTopic() {
  if (!newTopic.value.trim()) return ElMessage.warning('请输入话题内容')
  await createDiscussion(activeOffering.value, newTopic.value.trim())
  newTopic.value = ''
  ElMessage.success('话题已发布')
  loadDiscussions()
}
async function replyTopic(d) {
  const content = replyMap.value[d.id]
  if (!content?.trim()) return ElMessage.warning('请输入回复内容')
  await replyDiscussion(d.id, content.trim())
  replyMap.value[d.id] = ''
  ElMessage.success('回复成功')
  loadDiscussions()
}
async function likeTopic(d) {
  await likeDiscussion(d.id)
  loadDiscussions()
}
async function togglePin(d) {
  await pinDiscussion(d.id, d.pinned ? 0 : 1)
  ElMessage.success(d.pinned ? '已取消置顶' : '已置顶')
  loadDiscussions()
}

// ---------- 题库选题 ----------
const bankDialog = ref({ visible: false })
const bankLoading = ref(false)
const bankList = ref([])
const bankSelected = ref([])
const bankQuery = ref({ keyword: '', type: '' })

async function openBankPicker() {
  bankDialog.value.visible = true
  bankSelected.value = []
  loadBank()
}
async function loadBank() {
  bankLoading.value = true
  try {
    bankList.value = (await request.get('/bank/list', { params: bankQuery.value })) || []
  } finally { bankLoading.value = false }
}
function onBankSelect(rows) {
  bankSelected.value = rows
}
function importBank() {
  const mapType = { SINGLE: 'CHOICE', MULTI: 'CHOICE', FILL: 'FILL', JUDGE: 'JUDGE', ESSAY: 'ESSAY' }
  bankSelected.value.forEach((b) => {
    quizForm.value.questions.push({
      no: 'Q' + (quizForm.value.questions.length + 1),
      type: mapType[b.questionType] || 'CHOICE',
      content: b.stem,
      answer: b.answer || '',
      score: b.score || 10
    })
  })
  ElMessage.success(`已导入 ${bankSelected.value.length} 题`)
  bankDialog.value.visible = false
}

function typeText(t) {
  return { CHOICE: '单选', FILL: '填空', JUDGE: '判断', ESSAY: '简答' }[t] || t
}

watch(materialChapter, () => loadMaterials())
</script>

<style scoped>
.course-list { border-right: 1px solid #ebeef5; padding-right: 12px; }
.course-list :deep(.course-item) { display: block; }
.list-title { font-weight: 700; margin-bottom: 8px; color: #303133; }
.course-item { padding: 10px; border: 1px solid #ebeef5; border-radius: 6px; margin-bottom: 8px; cursor: pointer; transition: all .2s; }
.course-item:hover { border-color: #409eff; }
.course-item.active { border-color: #409eff; background: #ecf5ff; }
.course-name { font-weight: 600; }
.course-meta { font-size: 12px; color: #909399; }
.empty-tip { color: #909399; text-align: center; padding: 60px 0; }
.toolbar { display: flex; align-items: center; margin-bottom: 10px; gap: 6px; }
.tip { color: #909399; font-size: 12px; }
.hidden-input { display: none; }
.checkin-code { font-size: 40px; font-weight: 800; letter-spacing: 8px; color: #67c23a; margin: 4px 0; }
.checkin-code-sub { color: #909399; font-size: 13px; margin-bottom: 6px; }
.disc-publish { margin-bottom: 12px; }
.disc-item { border: 1px solid #ebeef5; border-radius: 6px; padding: 10px 12px; margin-bottom: 10px; }
.disc-head { display: flex; align-items: center; gap: 6px; }
.disc-author { font-weight: 600; }
.disc-time { color: #909399; font-size: 12px; }
.disc-ops { margin-left: auto; }
.disc-content { margin: 6px 0; line-height: 1.6; }
.disc-reply { background: #f5f7fa; border-radius: 4px; padding: 4px 8px; margin: 4px 0; font-size: 13px; }
.disc-reply-box { margin-top: 6px; display: flex; gap: 6px; align-items: center; }
.question-editor { margin-bottom: 8px; }
.question-answer { margin-bottom: 14px; }
.q-content { margin-bottom: 4px; }
.q-result { font-size: 13px; margin-top: 4px; }
.q-result.ok { color: #67c23a; }
.q-result.no { color: #f56c6c; }
.quiz-title { font-size: 16px; font-weight: 700; margin-bottom: 12px; }
</style>
