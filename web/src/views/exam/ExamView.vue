<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-form inline>
        <el-form-item label="学期">
          <el-select v-model="termId" placeholder="请选择" clearable style="width: 180px" @change="loadPapers">
            <el-option v-for="t in terms" :key="t.id" :label="t.termName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="试卷">
          <el-select v-model="paperId" placeholder="请选择" style="width: 240px" @change="loadAnalysis">
            <el-option v-for="p in papers" :key="p.id" :label="p.paperName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="analyzing" :disabled="!paperId" @click="doAnalyze">重新分析</el-button>
          <el-button type="success" :loading="generating" @click="doGenerateDemo">生成演示试卷</el-button>
        </el-form-item>
      </el-form>

      <template v-if="analysis && analysis.paper">
        <el-descriptions :column="6" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="满分">{{ analysis.paper.totalScore ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="平均分">{{ analysis.paper.avgScore ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="最高分">{{ analysis.paper.maxScore ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="最低分">{{ analysis.paper.minScore ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="及格率">{{ analysis.paper.passRate ?? '-' }}%</el-descriptions-item>
          <el-descriptions-item label="优秀率">{{ analysis.paper.excellentRate ?? '-' }}%</el-descriptions-item>
          <el-descriptions-item label="难度系数">{{ analysis.paper.difficulty ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="区分度">{{ analysis.paper.discrimination ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="标准差">{{ analysis.paper.stdDev ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="参试人数">{{ analysis.studentCount ?? 0 }}</el-descriptions-item>
        </el-descriptions>

        <el-alert :closable="false" type="info" show-icon style="margin-bottom: 16px">
          <template #title>
            难度系数越接近 1 表示试卷越容易；区分度建议 ≥ 0.3（
            <el-text type="success">≥0.4 优秀</el-text> /
            <el-text type="warning">0.3~0.4 良好</el-text> /
            <el-text type="danger">&lt;0.2 需修改</el-text>）。
          </template>
        </el-alert>

        <el-row :gutter="16">
          <el-col :xs="24" :md="14">
            <el-card shadow="never">
              <template #header>题目得分率</template>
              <div ref="rateRef" class="chart-box"></div>
            </el-card>
          </el-col>
          <el-col :xs="24" :md="10">
            <el-card shadow="never">
              <template #header>总分分布</template>
              <div ref="distRef" class="chart-box"></div>
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="16" style="margin-top: 16px">
          <el-col :xs="24" :md="12">
            <el-card shadow="never">
              <template #header>知识点掌握度</template>
              <div ref="knowRef" class="chart-box"></div>
            </el-card>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-card shadow="never">
              <template #header>难度 - 区分度</template>
              <div ref="scatterRef" class="chart-box"></div>
            </el-card>
          </el-col>
        </el-row>

        <el-card shadow="never" style="margin-top: 16px">
          <template #header>题目明细</template>
          <el-table :data="analysis.questions || []" border stripe size="small">
            <el-table-column prop="questionNo" label="题号" width="80" />
            <el-table-column prop="questionType" label="题型" width="90" />
            <el-table-column prop="knowledgePoint" label="知识点" min-width="120" />
            <el-table-column prop="fullScore" label="满分" width="80" />
            <el-table-column prop="avgScore" label="平均分" width="90" />
            <el-table-column label="得分率" width="110">
              <template #default="{ row }">
                <el-progress :percentage="toNumber(row.scoreRate)" :stroke-width="12" text-inside
                             :status="rateStatus(row.scoreRate)" />
              </template>
            </el-table-column>
            <el-table-column prop="difficulty" label="难度" width="90" />
            <el-table-column label="区分度" width="110">
              <template #default="{ row }">
                <el-tag :type="discTagType(row.discrimination)" size="small">
                  {{ row.discrimination ?? '-' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="stdDev" label="标准差" width="90" />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openPractice(row)">举一反三</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </template>

      <el-empty v-else description="请选择试卷或生成演示试卷" />
    </el-card>

    <!-- 举一反三：本地大模型生成的同类巩固练习 -->
    <el-dialog v-model="practiceDialog" :title="practiceTitle" width="760px" destroy-on-close>
      <el-alert :closable="false" type="info" show-icon style="margin-bottom: 12px">
        <template #title>
          围绕同一知识点由本地大模型生成同类练习题，用于举一反三巩固薄弱点；
          题目情境与原题不同，难度由易到难递进。
        </template>
      </el-alert>

      <div v-loading="practiceLoading" element-loading-text="本地大模型生成中...">
        <template v-if="practice">
          <div class="practice-meta">
            <el-tag size="small" :type="practice.sourceType === 'AI' ? 'success' : 'warning'">
              {{ practice.sourceType === 'AI' ? '本地大模型生成' : '规则降级生成' }}
            </el-tag>
            <el-text v-if="practice.model" size="small" type="info">模型：{{ practice.model }}</el-text>
            <el-text v-if="practice.costMs" size="small" type="info">耗时：{{ practice.costMs }} ms</el-text>
            <el-text v-if="practice.errorMsg" size="small" type="warning">{{ practice.errorMsg }}</el-text>
          </div>

          <div v-for="q in practice.items || []" :key="q.index" class="practice-item">
            <p class="practice-stem">
              <el-tag size="small" effect="plain">{{ diffText(q.difficulty) }}</el-tag>
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

          <el-empty v-if="!practice.items || !practice.items.length" description="未生成练习题" />
        </template>
      </div>

      <template #footer>
        <el-button @click="practiceDialog = false">关闭</el-button>
        <el-button type="primary" :loading="practiceLoading" @click="regenerate">重新生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import {
  listPapers, getAnalysis, analyzePaper, generateDemoPaper, generatePractice
} from '@/api/exam'
import { listBase } from '@/api/base'

const termId = ref(null)
const paperId = ref(null)
const terms = ref([])
const papers = ref([])
const analysis = ref(null)
const analyzing = ref(false)
const generating = ref(false)

const practiceDialog = ref(false)
const practiceLoading = ref(false)
const practice = ref(null)
const currentQuestion = ref(null)
const practiceTitle = computed(() => {
  const q = currentQuestion.value
  if (!q) return '举一反三 · 同类巩固练习'
  return `举一反三 · ${q.questionNo} ${q.knowledgePoint || ''}`
})

const rateRef = ref()
const distRef = ref()
const knowRef = ref()
const scatterRef = ref()
let rateChart
let distChart
let knowChart
let scatterChart

function toNumber(v) {
  const n = Number(v)
  return Number.isFinite(n) ? Math.min(100, Math.round(n)) : 0
}

function rateStatus(v) {
  const n = toNumber(v)
  if (n >= 80) return 'success'
  if (n >= 60) return undefined
  return 'exception'
}

function discTagType(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return 'info'
  if (n >= 0.4) return 'success'
  if (n >= 0.3) return 'warning'
  if (n >= 0.2) return 'warning'
  return 'danger'
}

async function loadPapers() {
  papers.value = await listPapers(termId.value)
  if (papers.value.length) {
    paperId.value = papers.value[0].id
    await loadAnalysis()
  } else {
    paperId.value = null
    analysis.value = null
  }
}

async function loadAnalysis() {
  if (!paperId.value) return
  analysis.value = await getAnalysis(paperId.value)
  renderCharts()
}

async function doAnalyze() {
  analyzing.value = true
  try {
    analysis.value = await analyzePaper(paperId.value)
    ElMessage.success('分析完成')
    renderCharts()
  } finally {
    analyzing.value = false
  }
}

async function doGenerateDemo() {
  generating.value = true
  try {
    const res = await generateDemoPaper(termId.value)
    ElMessage.success(res.message || '已生成')
    await loadPapers()
  } finally {
    generating.value = false
  }
}

// ==================== 举一反三 ====================

const PRACTICE_COUNT = 3

/** 打开弹窗并为该题目生成同类练习 */
async function openPractice(row) {
  currentQuestion.value = row
  practice.value = null
  practiceDialog.value = true
  await doGeneratePractice()
}

async function regenerate() {
  await doGeneratePractice()
}

async function doGeneratePractice() {
  const q = currentQuestion.value
  if (!q) return
  practiceLoading.value = true
  try {
    practice.value = await generatePractice(
      q.questionId, paperId.value, null, PRACTICE_COUNT
    )
  } catch (e) {
    ElMessage.error(e.message || '生成失败')
  } finally {
    practiceLoading.value = false
  }
}

function diffText(level) {
  return { EASY: '基础', MEDIUM: '进阶', HARD: '提高' }[level] || '练习'
}

function renderCharts() {
  nextTick(() => {
    const questions = analysis.value?.questions || []
    if (rateRef.value) {
      rateChart = rateChart || echarts.init(rateRef.value)
      rateChart.setOption({
        tooltip: { trigger: 'axis' },
        grid: { left: 45, right: 20, top: 20, bottom: 40 },
        xAxis: { type: 'category', data: questions.map((q) => q.questionNo) },
        yAxis: { type: 'value', max: 100, name: '得分率%' },
        series: [{
          name: '得分率',
          type: 'bar',
          barWidth: '55%',
          data: questions.map((q) => ({
            value: toNumber(q.scoreRate),
            itemStyle: { color: toNumber(q.scoreRate) >= 60 ? '#67c23a' : '#f56c6c' }
          }))
        }]
      })
    }

    const dist = analysis.value?.distribution || []
    if (distRef.value) {
      distChart = distChart || echarts.init(distRef.value)
      distChart.setOption({
        tooltip: { trigger: 'item' },
        legend: { bottom: 0 },
        series: [{
          name: '人数',
          type: 'pie',
          radius: ['40%', '65%'],
          data: dist.map((d) => ({ name: d.name, value: Number(d.value) || 0 }))
        }]
      })
    }

    const knowledge = analysis.value?.knowledge || []
    if (knowRef.value) {
      knowChart = knowChart || echarts.init(knowRef.value)
      knowChart.setOption({
        tooltip: { trigger: 'axis' },
        grid: { left: 80, right: 40, top: 20, bottom: 30 },
        xAxis: { type: 'value', max: 100, name: '掌握度%' },
        yAxis: { type: 'category', data: knowledge.map((k) => k.knowledgePoint) },
        series: [{
          name: '掌握度',
          type: 'bar',
          barWidth: '50%',
          label: { show: true, position: 'right', formatter: '{c}%' },
          data: knowledge.map((k) => ({
            value: toNumber(k.scoreRate),
            itemStyle: {
              color: toNumber(k.scoreRate) >= 85 ? '#67c23a'
                : toNumber(k.scoreRate) >= 60 ? '#e6a23c' : '#f56c6c'
            }
          }))
        }]
      })
    }

    if (scatterRef.value) {
      scatterChart = scatterChart || echarts.init(scatterRef.value)
      scatterChart.setOption({
        tooltip: {
          formatter: (p) => `${p.data[2]}<br/>难度：${p.data[0]}<br/>区分度：${p.data[1]}`
        },
        grid: { left: 50, right: 30, top: 20, bottom: 40 },
        xAxis: { type: 'value', name: '难度', min: 0, max: 1 },
        yAxis: { type: 'value', name: '区分度' },
        series: [{
          type: 'scatter',
          symbolSize: 12,
          data: questions.map((q) => [Number(q.difficulty) || 0, Number(q.discrimination) || 0, q.questionNo]),
          markLine: {
            silent: true,
            lineStyle: { type: 'dashed', color: '#f56c6c' },
            data: [{ yAxis: 0.3, label: { formatter: '合格线 0.3' } }]
          }
        }]
      })
    }
  })
}

function handleResize() {
  rateChart?.resize()
  distChart?.resize()
  knowChart?.resize()
  scatterChart?.resize()
}

onMounted(async () => {
  terms.value = await listBase('term')
  if (terms.value.length) termId.value = terms.value[terms.value.length - 1].id
  await loadPapers()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  rateChart?.dispose()
  distChart?.dispose()
  knowChart?.dispose()
  scatterChart?.dispose()
})
</script>

<style scoped>
.chart-box {
  height: 300px;
  width: 100%;
}

.practice-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 12px;
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
