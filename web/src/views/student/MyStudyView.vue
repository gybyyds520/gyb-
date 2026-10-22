<template>
  <div class="page-container">
    <!-- 个人中心卡片（移动端专属，通用组件）：头像 + 身份 + 学情速览 -->
    <MobileProfileCard :stats="profileStats" :sub="overview.studentNo || '学号未绑定'" />

    <el-alert :closable="false" type="info" show-icon style="margin-bottom: 16px">
      <template #title>
        本页仅展示你本人的学业数据。同学之间的成绩与排名互不公开，如需了解班级整体情况请联系辅导员。
      </template>
    </el-alert>

    <el-row :gutter="16" v-if="overview">
      <el-col :xs="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <p class="stat-title">学分绩点 GPA</p>
          <p class="stat-value" :style="{ color: gpaColor(overview.indicator?.gpa) }">
            {{ overview.indicator?.gpa }}
          </p>
        </el-card>
      </el-col>
      <el-col :xs="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <p class="stat-title">已获学分</p>
          <p class="stat-value">{{ overview.indicator?.earnedCredit }}</p>
        </el-card>
      </el-col>
      <el-col :xs="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <p class="stat-title">欠学分</p>
          <p class="stat-value" :style="{ color: overview.indicator?.deficitCredit > 0 ? '#f56c6c' : '#67c23a' }">
            {{ overview.indicator?.deficitCredit }}
          </p>
        </el-card>
      </el-col>
      <el-col :xs="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <p class="stat-title">成绩趋势</p>
          <p class="stat-value" :style="{ color: slopeColor(overview.indicator?.trendSlope) }">
            {{ formatSlope(overview.indicator?.trendSlope) }}
          </p>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :xs="24" :md="12">
        <div ref="scoreRef" class="chart-box"></div>
      </el-col>
      <el-col :xs="24" :md="12">
        <div ref="trendRef" class="chart-box"></div>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header><span>我的学业预警</span></template>
      <el-empty v-if="!overview?.warnings?.length" description="目前没有学业预警，请保持当前学习节奏" :image-size="60" />
      <el-timeline v-else>
        <el-timeline-item
          v-for="w in overview.warnings"
          :key="w.id"
          :type="levelColor(w.warningLevel)"
          :timestamp="formatTime(w.createTime)"
          placement="top"
        >
          <div class="warn-item">
            <el-tag :type="levelTag(w.warningLevel)" size="small">{{ levelText(w.warningLevel) }}</el-tag>
            <span class="warn-advice">{{ w.advice }}</span>
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>我的成绩</span>
          <el-select v-model="termId" placeholder="全部学期" clearable size="small" style="width: 170px"
                     @change="loadScores">
            <el-option v-for="t in terms" :key="t.id" :label="t.termName" :value="t.id" />
          </el-select>
        </div>
      </template>
      <el-table v-loading="scoreLoading" :data="scores" border stripe size="small">
        <el-table-column prop="courseName" label="课程" min-width="150" />
        <el-table-column prop="usualScore" label="平时" width="80" />
        <el-table-column prop="midScore" label="期中" width="80" />
        <el-table-column prop="finalScore" label="期末" width="80" />
        <el-table-column label="总评" width="90">
          <template #default="{ row }">
            <el-text :type="row.isPass === 1 ? 'success' : 'danger'">{{ row.totalScore }}</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="gradePoint" label="绩点" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isPass === 1 ? 'success' : 'danger'" size="small">
              {{ row.isPass === 1 ? '通过' : '未通过' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 成绩趋势（VIP1 会员功能） -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>成绩趋势与多学期对比</span>
          <el-button size="small" type="primary" :loading="trendLoading" @click="loadTrend">
            查看（会员）
          </el-button>
        </div>
      </template>
      <el-table v-if="trend.length" :data="trend" border stripe size="small">
        <el-table-column label="学期" min-width="120">
          <template #default="{ row }">{{ termNameOf(row.termId) }}</template>
        </el-table-column>
        <el-table-column prop="courseCount" label="课程门数" width="90" />
        <el-table-column prop="avgScore" label="平均分" width="90" />
        <el-table-column prop="passRate" label="及格率(%)" width="100" />
        <el-table-column prop="maxScore" label="最高分" width="90" />
        <el-table-column prop="minScore" label="最低分" width="90" />
      </el-table>
      <el-empty v-else description="开通会员后可查看多学期成绩趋势对比" :image-size="60" />
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>我的巩固练习</span>
          <el-button size="small" :loading="practiceLoading" @click="loadPractices">刷新</el-button>
        </div>
      </template>
      <el-empty
        v-if="!practices.length"
        description="暂无巩固练习：教师针对你的错题生成「举一反三」练习后会出现在这里"
        :image-size="60"
      />
      <el-table v-else :data="practices" border stripe size="small">
        <el-table-column prop="courseName" label="课程" min-width="110" />
        <el-table-column prop="knowledgePoint" label="知识点" min-width="110" />
        <el-table-column label="题型" width="90">
          <template #default="{ row }">{{ practiceTypeText(row.questionType) }}</template>
        </el-table-column>
        <el-table-column prop="questionCount" label="题数" width="70" />
        <el-table-column label="来源" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.sourceType === 'AI' ? 'success' : 'warning'">
              {{ row.sourceType === 'AI' ? 'AI生成' : '规则生成' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生成时间" width="150">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openPractice(row.practiceId)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 我的作业（作业帮式作业闭环：教师布置→作答→自动批改） -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>我的作业</span>
          <el-button size="small" :loading="homeworkLoading" @click="loadHomework">刷新</el-button>
        </div>
      </template>
      <el-empty v-if="!homeworkList.length" description="暂无作业：教师布置作业后会显示在这里" :image-size="60" />
      <el-table v-else :data="homeworkList" border stripe size="small">
        <el-table-column prop="title" label="作业" min-width="150" show-overflow-tooltip />
        <el-table-column prop="courseName" label="课程" min-width="90" />
        <el-table-column prop="questionCount" label="题数" width="70" />
        <el-table-column label="截止时间" width="150">
          <template #default="{ row }">{{ formatTime(row.deadline) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.submitted" size="small" type="success">已交</el-tag>
            <el-tag v-else-if="row.overdue" size="small" type="danger">已逾期</el-tag>
            <el-tag v-else size="small" type="warning">待作答</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="得分" width="90">
          <template #default="{ row }">
            <span v-if="row.score !== null && row.score !== undefined">{{ row.score }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openHomework(row.id)">查看/作答</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 我的错题本（错题自动收录 + 按课程分组 + 掌握标记） -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>我的错题本</span>
          <div>
            <el-tag v-if="mistakeStats" size="small" effect="plain" style="margin-right: 6px">
              未掌握 {{ mistakeStats.unresolved }} 题
            </el-tag>
            <el-tag size="small" effect="plain" type="success" style="margin-right: 6px">
              已掌握 {{ mistakeStats.mastered }} 题
            </el-tag>
            <el-button size="small" :loading="mistakeLoading" @click="loadMistakes">刷新</el-button>
          </div>
        </div>
      </template>
      <el-empty v-if="!mistakeGroups.length" description="暂无错题：作业/考试中的错题会自动收录，也可手动添加" :image-size="60" />
      <div v-for="g in mistakeGroups" :key="g.courseName" style="margin-bottom: 8px">
        <p style="margin: 8px 0 6px; font-weight: 600">
          {{ g.courseName }}
          <el-tag size="small" effect="plain" style="margin-left: 6px">共 {{ g.total }} 题 / 未掌握 {{ g.unresolved }}</el-tag>
        </p>
        <el-table :data="g.items" border stripe size="small">
          <el-table-column label="知识点" width="110">
            <template #default="{ row }">{{ row.knowledgePoint || '未分类' }}</template>
          </el-table-column>
          <el-table-column prop="questionContent" label="题目" min-width="180" show-overflow-tooltip />
          <el-table-column label="我的答案" width="120">
            <template #default="{ row }">
              <span style="color: #f56c6c">{{ row.wrongAnswer }}</span>
            </template>
          </el-table-column>
          <el-table-column label="正确答案" width="120">
            <template #default="{ row }">
              <span style="color: #67c23a">{{ row.rightAnswer }}</span>
            </template>
          </el-table-column>
          <el-table-column label="错因" width="90">
            <template #default="{ row }">{{ row.mistakeReason }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.status === 'MASTERED'" size="small" type="success">已掌握</el-tag>
              <el-tag v-else size="small" type="danger">未掌握</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130">
            <template #default="{ row }">
              <el-button
                v-if="row.status !== 'MASTERED'"
                link type="primary" size="small"
                @click="markMastered(row.id)"
              >标记掌握</el-button>
              <el-button link type="danger" size="small" @click="delMistake(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <!-- AI 学科答疑（本地大模型，作业帮式 AI 辅导） -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>AI 学科答疑</span>
          <el-tag size="small" effect="plain">本地大模型辅导</el-tag>
        </div>
      </template>
      <div style="display: flex; gap: 8px; flex-wrap: wrap">
        <el-select v-model="tutorSubject" placeholder="学科（选填）" style="width: 130px" clearable>
          <el-option v-for="s in ['数学', '英语', '语文', '物理', '化学', '生物', '高数', '编程']" :key="s" :label="s" :value="s" />
        </el-select>
        <el-input
          v-model="tutorQuestion"
          placeholder="输入不会做的题目或知识点，例如：洛必达法则怎么用？"
          style="flex: 1; min-width: 260px"
          clearable
          @keyup.enter="askTutor"
        />
        <el-button type="primary" :loading="tutorLoading" @click="askTutor">提问</el-button>
      </div>
      <div v-if="tutorAnswer" style="margin-top: 12px">
        <el-alert type="success" :closable="false">
          <template #title>
            <div style="white-space: pre-wrap; line-height: 1.8">{{ tutorAnswer }}</div>
          </template>
        </el-alert>
      </div>
    </el-card>
  </div>

  <!-- 作业作答 -->
  <el-dialog v-model="hwDialog" :title="hwDetail?.title || '作业'" width="760px" destroy-on-close>
    <div v-loading="hwLoading">
      <template v-if="hwDetail">
        <p style="color: #909399; font-size: 13px; margin-top: 0">
          {{ hwDetail.courseName }} · 满分 {{ hwDetail.totalScore }}
          <span v-if="hwDetail.deadline"> · 截止 {{ formatTime(hwDetail.deadline) }}</span>
        </p>
        <div v-for="q in hwDetail.questions || []" :key="q.no" class="practice-item">
          <p class="practice-stem">
            <el-tag size="small" effect="plain">
              {{ { CHOICE: '单选', FILL: '填空', ESSAY: '简答' }[q.type] || q.type }}
            </el-tag>
            {{ q.no }}. {{ q.content }}
          </p>
          <el-input
            v-model="hwAnswers[q.no]"
            :type="q.type === 'ESSAY' ? 'textarea' : 'text'"
            :rows="q.type === 'ESSAY' ? 3 : 1"
            :placeholder="q.type === 'ESSAY' ? '输入你的作答（教师/AI 批改）' : '输入你的答案'"
          />
        </div>
      </template>
    </div>
    <template #footer>
      <el-button @click="hwDialog = false">关闭</el-button>
      <el-button
        v-if="hwDetail && !hwDetail.submission"
        type="primary" :loading="submitting" :disabled="!hwDetail"
        @click="submitHw"
      >提交作业（客观题自动批改）</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="practiceDialog" title="巩固练习" width="760px" destroy-on-close>
    <div v-loading="practiceLoading">
      <div v-for="q in currentPractice?.items || []" :key="q.index" class="practice-item">
        <p class="practice-stem">
          <el-tag size="small" effect="plain">{{ practiceDiffText(q.difficulty) }}</el-tag>
          {{ q.index }}. {{ q.stem }}
        </p>
        <p v-for="(o, i) in q.options || []" :key="i" class="practice-option">{{ o }}</p>
        <el-collapse>
          <el-collapse-item title="查看答案与解析">
            <p class="practice-answer">答案：{{ q.answer }}</p>
            <p class="practice-analysis">{{ q.analysis }}</p>
          </el-collapse-item>
        </el-collapse>
      </div>
      <el-empty v-if="currentPractice && !(currentPractice.items || []).length" description="暂无题目" />
    </div>
    <template #footer>
      <el-button @click="practiceDialog = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import MobileProfileCard from '@/components/MobileProfileCard.vue'
import { myOverview, myScores } from '@/api/portal'
import { myPractices, practiceDetail } from '@/api/exam'
import { myScoreTrend } from '@/api/vip'
import { listBase } from '@/api/base'
import { myHomework, studentHomeworkDetail, submitHomework } from '@/api/homework'
import { myMistakeList, myMistakeStats, markMistakeMastered, removeMistake } from '@/api/mistake'
import { aiTutor } from '@/api/ai'
import { useRouter } from 'vue-router'

const router = useRouter()

const overview = ref(null)

/** 个人中心卡片：学情速览（学生本人四项） */
const profileStats = computed(() => {
  if (!overview.value) return []
  return [
    { label: 'GPA', value: overview.value.indicator?.gpa ?? '-' },
    { label: '成绩记录', value: overview.value.scoreCount ?? 0 },
    { label: '进行中预警', value: overview.value.activeWarningCount ?? 0 },
    { label: '已获学分', value: overview.value.indicator?.earnedCredit ?? 0 }
  ]
})
const scores = ref([])
const terms = ref([])
const termId = ref(null)
const scoreLoading = ref(false)

const practices = ref([])
const practiceLoading = ref(false)
const trend = ref([])
const trendLoading = ref(false)
const practiceDialog = ref(false)
const currentPractice = ref(null)

const scoreRef = ref()
const trendRef = ref()
let scoreChart
let trendChart

/** 我的作业 */
const homeworkList = ref([])
const homeworkLoading = ref(false)
const hwDialog = ref(false)
const hwDetail = ref(null)
const hwLoading = ref(false)
const hwAnswers = ref({})
const submitting = ref(false)

/** 我的错题本 */
const mistakeGroups = ref([])
const mistakeStats = ref({ total: 0, unresolved: 0, mastered: 0 })
const mistakeLoading = ref(false)

/** AI 学科答疑 */
const tutorSubject = ref('')
const tutorQuestion = ref('')
const tutorAnswer = ref('')
const tutorLoading = ref(false)

function gpaColor(gpa) {
  const v = Number(gpa)
  if (!Number.isFinite(v)) return '#303133'
  if (v >= 3.5) return '#67c23a'
  if (v >= 2.0) return '#e6a23c'
  return '#f56c6c'
}

function slopeColor(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '#303133'
  if (n <= -8) return '#f56c6c'
  if (n < 0) return '#e6a23c'
  return '#67c23a'
}

function formatSlope(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '-'
  return (n > 0 ? '+' : '') + n + ' 分/学期'
}

function levelText(level) {
  return ['未分级', '蓝色', '黄色', '橙色', '红色'][level || 0]
}

function levelTag(level) {
  return ['info', 'info', 'warning', 'warning', 'danger'][level || 0]
}

function levelColor(level) {
  return ['primary', 'primary', 'warning', 'warning', 'danger'][level || 0]
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').substring(0, 16)
}

async function loadScores() {
  scoreLoading.value = true
  try {
    scores.value = await myScores(termId.value)
  } finally {
    scoreLoading.value = false
  }
}

function termNameOf(id) {
  const t = terms.value.find((x) => x.id === id)
  return t ? t.termName : '学期' + id
}

/** 成绩趋势：需要 VIP1；等级不足时引导前往会员中心 */
async function loadTrend() {
  trendLoading.value = true
  try {
    trend.value = await myScoreTrend()
  } catch (e) {
    ElMessage.warning(e.message || '该功能需要开通会员')
    router.push('/vip')
  } finally {
    trendLoading.value = false
  }
}

/** 教师为我生成的巩固练习 */
async function loadPractices() {
  practiceLoading.value = true
  try {
    practices.value = await myPractices()
  } finally {
    practiceLoading.value = false
  }
}

async function openPractice(id) {
  practiceLoading.value = true
  practiceDialog.value = true
  currentPractice.value = null
  try {
    currentPractice.value = await practiceDetail(id)
  } finally {
    practiceLoading.value = false
  }
}

function practiceTypeText(type) {
  return {
    SINGLE: '单选题', MULTI: '多选题', JUDGE: '判断题',
    FILL: '填空题', SHORT: '简答题', CALC: '计算题'
  }[type] || type || '-'
}

function practiceDiffText(level) {
  return { EASY: '基础', MEDIUM: '进阶', HARD: '提高' }[level] || '练习'
}

/** 我的作业 */
async function loadHomework() {
  homeworkLoading.value = true
  try {
    homeworkList.value = await myHomework()
  } finally {
    homeworkLoading.value = false
  }
}

async function openHomework(id) {
  hwLoading.value = true
  hwDialog.value = true
  hwDetail.value = null
  hwAnswers.value = {}
  try {
    const d = await studentHomeworkDetail(id)
    hwDetail.value = d
    // 已提交时回填答案，便于查看
    const sub = d.submission
    if (sub && sub.answersJson) {
      try {
        for (const a of JSON.parse(sub.answersJson)) hwAnswers.value[a.no] = a.answer
      } catch { /* 忽略 */ }
    }
  } finally {
    hwLoading.value = false
  }
}

async function submitHw() {
  const answers = Object.entries(hwAnswers.value).map(([no, answer]) => ({ no, answer: answer || '' }))
  submitting.value = true
  try {
    await submitHomework(hwDetail.value.id, answers)
    ElMessage.success('提交成功，客观题已自动批改，错题已收录错题本')
    hwDialog.value = false
    await loadHomework()
  } catch (e) {
    ElMessage.error(e.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

/** 我的错题本 */
async function loadMistakes() {
  mistakeLoading.value = true
  try {
    mistakeGroups.value = await myMistakeList()
    const st = await myMistakeStats()
    mistakeStats.value = st
  } catch (e) {
    ElMessage.error(e.message || '错题本加载失败')
  } finally {
    mistakeLoading.value = false
  }
}

async function markMastered(id) {
  try {
    await markMistakeMastered(id)
    ElMessage.success('已标记掌握')
    await loadMistakes()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function delMistake(id) {
  try {
    await removeMistake(id)
    ElMessage.success('已删除')
    await loadMistakes()
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  }
}

/** AI 学科答疑 */
async function askTutor() {
  const q = tutorQuestion.value.trim()
  if (!q) {
    ElMessage.warning('请先输入要解答的问题')
    return
  }
  tutorLoading.value = true
  tutorAnswer.value = ''
  try {
    const res = await aiTutor(q, tutorSubject.value)
    tutorAnswer.value = res.content
  } catch (e) {
    ElMessage.error(e.message || '本地大模型不可用，请确认 Ollama 已启动')
  } finally {
    tutorLoading.value = false
  }
}

function renderCharts() {
  nextTick(() => {
    const list = scores.value
    if (scoreRef.value) {
      scoreChart = scoreChart || echarts.init(scoreRef.value)
      scoreChart.setOption({
        title: { text: '我的各科成绩', textStyle: { fontSize: 14 } },
        tooltip: { trigger: 'axis' },
        grid: { left: 50, right: 20, top: 40, bottom: 60 },
        xAxis: {
          type: 'category',
          data: list.map((s) => s.courseName),
          axisLabel: { interval: 0, rotate: 30, fontSize: 10 }
        },
        yAxis: { type: 'value', max: 100, name: '分' },
        series: [{
          name: '总评',
          type: 'bar',
          barWidth: '55%',
          data: list.map((s) => ({
            value: Number(s.totalScore) || 0,
            itemStyle: { color: s.isPass === 1 ? '#409eff' : '#f56c6c' }
          })),
          markLine: {
            silent: true,
            data: [{ yAxis: 60, lineStyle: { color: '#f56c6c', type: 'dashed' }, label: { formatter: '及格线' } }]
          }
        }]
      })
    }

    const series = overview.value?.indicator?.avgScoreSeries || []
    if (trendRef.value) {
      trendChart = trendChart || echarts.init(trendRef.value)
      trendChart.setOption({
        title: { text: '学期均分变化', textStyle: { fontSize: 14 } },
        tooltip: { trigger: 'axis' },
        grid: { left: 50, right: 20, top: 40, bottom: 40 },
        xAxis: { type: 'category', data: series.map((_, i) => '第' + (i + 1) + '学期') },
        yAxis: { type: 'value', name: '均分' },
        series: [{
          name: '学期均分',
          type: 'line',
          smooth: true,
          areaStyle: { opacity: 0.2 },
          itemStyle: { color: '#409eff' },
          data: series.map((v) => Number(v) || 0)
        }]
      })
    }
  })
}

function handleResize() {
  scoreChart?.resize()
  trendChart?.resize()
}

onMounted(async () => {
  overview.value = await myOverview(null)
  terms.value = await listBase('term')
  await loadScores()
  await loadPractices()
  await loadHomework()
  await loadMistakes()
  renderCharts()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  scoreChart?.dispose()
  trendChart?.dispose()
})
</script>

<style scoped>
.stat-card {
  text-align: center;
}

.stat-title {
  margin: 0;
  font-size: 13px;
  color: #909399;
}

.stat-value {
  margin: 6px 0 0;
  font-size: 24px;
  font-weight: 700;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.warn-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.warn-advice {
  font-size: 13px;
}

.chart-box {
  height: 300px;
  width: 100%;
}

.practice-item {
  padding: 12px 0;
  border-bottom: 1px dashed #ebeef5;
}

.practice-stem {
  margin: 0 0 8px;
  font-size: 14px;
  line-height: 1.7;
}

.practice-option {
  margin: 0 0 4px;
  padding-left: 16px;
  font-size: 13px;
  color: #606266;
}

.practice-answer {
  margin: 0 0 4px;
  font-size: 13px;
  font-weight: 600;
  color: #409eff;
}

.practice-analysis {
  margin: 0;
  font-size: 13px;
  color: #606266;
  line-height: 1.7;
}
</style>
