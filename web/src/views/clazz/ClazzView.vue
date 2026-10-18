<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>班级学情对比</span>
          <el-select v-model="termId" placeholder="学期" clearable size="small" style="width: 180px"
                     @change="loadComparison">
            <el-option v-for="t in terms" :key="t.id" :label="t.termName" :value="t.id" />
          </el-select>
        </div>
      </template>

      <div ref="compareRef" class="chart-box"></div>

      <el-table v-loading="loading" :data="ranking" border stripe size="small" style="margin-top: 16px">
        <el-table-column type="index" label="排名" width="70" />
        <el-table-column prop="clazzName" label="班级" min-width="140" />
        <el-table-column prop="scoreCount" label="成绩数" width="90" />
        <el-table-column prop="avgScore" label="平均分" width="100" />
        <el-table-column label="及格率" width="140">
          <template #default="{ row }">
            <el-progress :percentage="toNumber(row.passRate)" :stroke-width="14" text-inside />
          </template>
        </el-table-column>
        <el-table-column label="优秀率" width="140">
          <template #default="{ row }">
            <el-progress :percentage="toNumber(row.excellentRate)" :stroke-width="14" text-inside
                         status="success" />
          </template>
        </el-table-column>
        <el-table-column prop="warningCount" label="预警人数" width="100" />
      </el-table>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>课堂表现排名</span>
          <div>
            <el-button size="small" :loading="generating" @click="doGenerateDemo">生成演示数据</el-button>
            <el-button type="primary" size="small" @click="loadPerformance">刷新</el-button>
          </div>
        </div>
      </template>

      <el-empty v-if="!performance.length" description="暂无课堂表现数据" :image-size="70" />
      <el-table v-else :data="performance" border stripe size="small">
        <el-table-column type="index" label="名次" width="70" />
        <el-table-column prop="studentNo" label="学号" width="130" />
        <el-table-column prop="studentName" label="姓名" width="100" />
        <el-table-column prop="recordCount" label="记录数" width="90" />
        <el-table-column label="平均参与度" width="150">
          <template #default="{ row }">
            <el-progress :percentage="toNumber(row.avgParticipation) * 10" :stroke-width="14" text-inside
                         :format="() => row.avgParticipation" />
          </template>
        </el-table-column>
        <el-table-column prop="avgHomework" label="平均作业分" width="110" />
        <el-table-column prop="totalQuestions" label="互动次数" width="100" />
        <el-table-column label="评级" width="100">
          <template #default="{ row }">
            <el-tag :type="row.level === '积极' ? 'success' : row.level === '一般' ? 'warning' : 'danger'"
                    size="small">
              {{ row.level }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewDetail(row)">明细</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="detailDialog" :title="detailTitle" width="620px">
      <el-table :data="detailRecords" border stripe size="small" max-height="360">
        <el-table-column prop="recordDate" label="日期" width="120" />
        <el-table-column prop="participationScore" label="参与度" width="90" />
        <el-table-column prop="homeworkScore" label="作业分" width="90" />
        <el-table-column prop="questionCount" label="互动次数" width="100" />
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { clazzComparison } from '@/api/dashboard'
import { performanceRanking, listPerformance, generateDemoPerformance } from '@/api/performance'
import { listBase } from '@/api/base'

const termId = ref(null)
const terms = ref([])
const ranking = ref([])
const performance = ref([])
const loading = ref(false)
const generating = ref(false)
const compareRef = ref()
let compareChart

const detailDialog = ref(false)
const detailTitle = ref('')
const detailRecords = ref([])

function toNumber(v) {
  const n = Number(v)
  return Number.isFinite(n) ? Math.min(100, Math.round(n)) : 0
}

async function loadComparison() {
  loading.value = true
  try {
    ranking.value = await clazzComparison(termId.value)
    renderChart()
  } finally {
    loading.value = false
  }
}

async function loadPerformance() {
  performance.value = await performanceRanking(termId.value, 20)
}

async function doGenerateDemo() {
  generating.value = true
  try {
    const res = await generateDemoPerformance(termId.value, 3)
    ElMessage.success(res.message || '已生成')
    await loadPerformance()
  } finally {
    generating.value = false
  }
}

async function viewDetail(row) {
  detailTitle.value = `${row.studentName || ''}（${row.studentNo || ''}）课堂表现明细`
  detailRecords.value = await listPerformance(row.studentId, termId.value)
  detailDialog.value = true
}

function renderChart() {
  nextTick(() => {
    if (!compareRef.value) return
    compareChart = compareChart || echarts.init(compareRef.value)
    compareChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['平均分', '及格率', '优秀率'], bottom: 0 },
      grid: { left: 50, right: 40, top: 30, bottom: 50 },
      xAxis: { type: 'category', data: ranking.value.map((r) => r.clazzName) },
      yAxis: [
        { type: 'value', name: '平均分', max: 100 },
        { type: 'value', name: '百分比', max: 100 }
      ],
      series: [
        { name: '平均分', type: 'bar', barWidth: '35%', data: ranking.value.map((r) => Number(r.avgScore) || 0) },
        {
          name: '及格率',
          type: 'line',
          smooth: true,
          yAxisIndex: 1,
          data: ranking.value.map((r) => toNumber(r.passRate))
        },
        {
          name: '优秀率',
          type: 'line',
          smooth: true,
          yAxisIndex: 1,
          data: ranking.value.map((r) => toNumber(r.excellentRate))
        }
      ]
    })
  })
}

function handleResize() {
  compareChart?.resize()
}

onMounted(async () => {
  terms.value = await listBase('term')
  if (terms.value.length) termId.value = terms.value[terms.value.length - 1].id
  await loadComparison()
  await loadPerformance()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  compareChart?.dispose()
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chart-box {
  height: 320px;
  width: 100%;
}
</style>
