<template>
  <div class="page-container">
    <el-row :gutter="16">
      <el-col v-for="card in statCards" :key="card.title" :xs="12" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-info">
              <p class="stat-title">{{ card.title }}</p>
              <p class="stat-value">{{ card.value }}</p>
            </div>
            <el-icon class="stat-icon" :style="{ color: card.color }">
              <component :is="card.icon" />
            </el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :xs="24" :md="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>学业风险分布</span>
              <el-text type="info" size="small">
                平均分 {{ data.avgScore ?? '-' }} · 及格率 {{ data.passRate ?? '-' }}%
              </el-text>
            </div>
          </template>
          <div ref="chartRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <template #header>最新预警</template>
          <el-empty v-if="!warnings.length" description="暂无待处理预警" :image-size="60" />
          <ul v-else class="warning-list">
            <li v-for="w in warnings" :key="w.id" class="warning-item">
              <el-tag :type="levelTagType(w.warningLevel)" size="small">{{ levelText(w.warningLevel) }}</el-tag>
              <span class="warning-title">{{ w.title }}</span>
            </li>
          </ul>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header>成绩分数段分布</template>
          <div ref="scoreRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header>学期成绩趋势</template>
          <div ref="trendRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>班级成绩排名</template>
      <el-table v-loading="loading" :data="clazzRanking" border stripe size="small">
        <el-table-column type="index" label="排名" width="70" />
        <el-table-column prop="clazzName" label="班级" min-width="140" />
        <el-table-column prop="scoreCount" label="成绩数" width="90" />
        <el-table-column prop="avgScore" label="平均分" width="100" />
        <el-table-column label="及格率" width="110">
          <template #default="{ row }">
            <el-progress :percentage="toNumber(row.passRate)" :stroke-width="12" text-inside />
          </template>
        </el-table-column>
        <el-table-column label="优秀率" width="110">
          <template #default="{ row }">
            <el-progress :percentage="toNumber(row.excellentRate)" :stroke-width="12" text-inside
                         status="success" />
          </template>
        </el-table-column>
        <el-table-column prop="warningCount" label="预警人数" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.warningCount" type="danger" size="small">{{ row.warningCount }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { User, TrendCharts, Warning, MagicStick } from '@element-plus/icons-vue'
import { overview, latestWarnings } from '@/api/dashboard'

const LEVEL_TEXT = { 1: '蓝色', 2: '黄色', 3: '橙色', 4: '红色' }
const LEVEL_TYPE = { 1: 'info', 2: 'warning', 3: 'warning', 4: 'danger' }
const LEVEL_COLOR = ['#409eff', '#e6a23c', '#f78989', '#f56c6c']

const data = ref({})
const warnings = ref([])
const loading = ref(false)
const chartRef = ref()
const scoreRef = ref()
const trendRef = ref()
let chart
let scoreChart
let trendChart

const statCards = computed(() => [
  { title: '在校学生', value: data.value.studentCount ?? 0, icon: User, color: '#2f6fed' },
  { title: '预测覆盖', value: data.value.predictionCount ?? 0, icon: TrendCharts, color: '#67c23a' },
  { title: '当前预警', value: data.value.activeWarningCount ?? 0, icon: Warning, color: '#e6a23c' },
  { title: 'AI 报告', value: data.value.reportCount ?? 0, icon: MagicStick, color: '#909399' }
])

const clazzRanking = computed(() => data.value.clazzRanking || [])

function levelText(level) {
  return LEVEL_TEXT[level] || '蓝色'
}

function levelTagType(level) {
  return LEVEL_TYPE[level] || 'info'
}

function toNumber(v) {
  const n = Number(v)
  return Number.isFinite(n) ? Math.min(100, Math.round(n)) : 0
}

function renderCharts() {
  const warnDist = data.value.warningDistribution || []
  if (chartRef.value) {
    chart = chart || echarts.init(chartRef.value)
    chart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 20, top: 30, bottom: 30 },
      xAxis: { type: 'category', data: warnDist.map((i) => i.name) },
      yAxis: { type: 'value' },
      series: [{
        name: '人数',
        type: 'bar',
        barWidth: '45%',
        data: warnDist.map((i, idx) => ({ value: Number(i.value) || 0, itemStyle: { color: LEVEL_COLOR[idx] } }))
      }]
    })
  }

  const scoreDist = data.value.scoreDistribution || []
  if (scoreRef.value) {
    scoreChart = scoreChart || echarts.init(scoreRef.value)
    scoreChart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [{
        name: '成绩分布',
        type: 'pie',
        radius: ['40%', '65%'],
        data: scoreDist.map((i) => ({ name: i.name, value: Number(i.value) || 0 }))
      }]
    })
  }

  const trend = data.value.termTrend || []
  if (trendRef.value) {
    trendChart = trendChart || echarts.init(trendRef.value)
    trendChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['平均分', '及格率'], bottom: 0 },
      grid: { left: 45, right: 40, top: 20, bottom: 40 },
      xAxis: { type: 'category', data: trend.map((t) => t.termName || ('学期' + t.termId)) },
      yAxis: [
        { type: 'value', name: '平均分', max: 100 },
        { type: 'value', name: '及格率', max: 100 }
      ],
      series: [
        { name: '平均分', type: 'line', smooth: true, data: trend.map((t) => Number(t.avgScore) || 0) },
        {
          name: '及格率',
          type: 'line',
          smooth: true,
          yAxisIndex: 1,
          data: trend.map((t) => toNumber(t.passRate))
        }
      ]
    })
  }
}

function handleResize() {
  chart?.resize()
  scoreChart?.resize()
  trendChart?.resize()
}

async function load() {
  loading.value = true
  try {
    data.value = await overview(null)
    warnings.value = await latestWarnings()
    renderCharts()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  scoreChart?.dispose()
  trendChart?.dispose()
})
</script>

<style scoped>
.stat-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
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

.stat-icon {
  font-size: 32px;
}

.chart-box {
  height: 280px;
  width: 100%;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.warning-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 280px;
  overflow-y: auto;
}

.warning-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid #f5f7fa;
}

.warning-title {
  font-size: 13px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
