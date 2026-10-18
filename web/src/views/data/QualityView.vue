<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>数据质量报告</span>
          <div>
            <el-button type="primary" :loading="checking" @click="doCheck">执行全量校验</el-button>
            <el-button :loading="loading" @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <el-row :gutter="16" v-if="summary">
        <el-col :xs="12" :md="6">
          <el-card shadow="hover" class="stat-card">
            <p class="stat-title">数据健康度</p>
            <p class="stat-value" :style="{ color: healthColor }">{{ summary.healthRate ?? '-' }}%</p>
          </el-card>
        </el-col>
        <el-col :xs="12" :md="6">
          <el-card shadow="hover" class="stat-card">
            <p class="stat-title">参与校验记录</p>
            <p class="stat-value">{{ summary.totalRecords ?? 0 }}</p>
          </el-card>
        </el-col>
        <el-col :xs="12" :md="6">
          <el-card shadow="hover" class="stat-card">
            <p class="stat-title">问题总数</p>
            <p class="stat-value" :style="{ color: (summary.problemCount ?? 0) > 0 ? '#e6a23c' : '#67c23a' }">
              {{ summary.problemCount ?? 0 }}
            </p>
          </el-card>
        </el-col>
        <el-col :xs="12" :md="6">
          <el-card shadow="hover" class="stat-card">
            <p class="stat-title">报告条数</p>
            <p class="stat-value">{{ summary.reportCount ?? 0 }}</p>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" style="margin-top: 16px" v-if="summary">
        <el-col :xs="24" :md="12">
          <div ref="typeRef" class="chart-box"></div>
        </el-col>
        <el-col :xs="24" :md="12">
          <div ref="datasetRef" class="chart-box"></div>
        </el-col>
      </el-row>

      <el-table v-loading="loading" :data="reports" border stripe size="small" style="margin-top: 16px">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="dataset" label="数据集" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.dataset === 'STUDENT' ? '学生' : '成绩' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="checkRule" label="校验规则" min-width="140" />
        <el-table-column label="问题类型" width="110">
          <template #default="{ row }">
            <el-tag :type="problemTagType(row.problemType)" size="small">{{ problemText(row.problemType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="problemCount" label="问题数" width="90" />
        <el-table-column prop="checkTime" label="校验时间" width="170" />
        <el-table-column label="样例" width="90">
          <template #default="{ row }">
            <el-popover placement="left" :width="360" trigger="click">
              <template #reference>
                <el-button link type="primary" size="small">查看</el-button>
              </template>
              <pre class="sample-json">{{ formatSample(row.sampleJson) }}</pre>
            </el-popover>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { runQualityCheck, listQualityReports, qualitySummary } from '@/api/quality'

const summary = ref(null)
const reports = ref([])
const loading = ref(false)
const checking = ref(false)
const typeRef = ref()
const datasetRef = ref()
let typeChart
let datasetChart

const PROBLEM_TEXT = { MISSING: '缺失', OUT_OF_RANGE: '越界', DUPLICATE: '重复', INCONSISTENT: '不一致' }
const PROBLEM_TAG = { MISSING: 'danger', OUT_OF_RANGE: 'warning', DUPLICATE: 'warning', INCONSISTENT: 'info' }

const healthColor = computed(() => {
  const v = Number(summary.value?.healthRate)
  if (!Number.isFinite(v)) return '#303133'
  if (v >= 95) return '#67c23a'
  if (v >= 85) return '#e6a23c'
  return '#f56c6c'
})

function problemText(type) {
  return PROBLEM_TEXT[type] || type
}

function problemTagType(type) {
  return PROBLEM_TAG[type] || 'info'
}

function formatSample(json) {
  if (!json) return '无'
  try {
    return JSON.stringify(JSON.parse(json), null, 2)
  } catch {
    return json
  }
}

async function load() {
  loading.value = true
  try {
    reports.value = await listQualityReports(null)
    summary.value = await qualitySummary()
    renderCharts()
  } finally {
    loading.value = false
  }
}

async function doCheck() {
  checking.value = true
  try {
    const res = await runQualityCheck(null)
    ElMessage.success(res.message || '校验完成')
    await load()
  } finally {
    checking.value = false
  }
}

function renderCharts() {
  nextTick(() => {
    const byType = summary.value?.byProblemType || {}
    if (typeRef.value) {
      typeChart = typeChart || echarts.init(typeRef.value)
      typeChart.setOption({
        title: { text: '问题类型分布', textStyle: { fontSize: 14 } },
        tooltip: { trigger: 'item' },
        legend: { bottom: 0 },
        series: [{
          name: '问题数',
          type: 'pie',
          radius: ['40%', '62%'],
          data: Object.entries(byType).map(([k, v]) => ({ name: problemText(k), value: v }))
        }]
      })
    }

    const byDataset = summary.value?.byDataset || {}
    if (datasetRef.value) {
      datasetChart = datasetChart || echarts.init(datasetRef.value)
      datasetChart.setOption({
        title: { text: '数据集问题分布', textStyle: { fontSize: 14 } },
        tooltip: { trigger: 'axis' },
        grid: { left: 60, right: 20, top: 40, bottom: 30 },
        xAxis: { type: 'value' },
        yAxis: {
          type: 'category',
          data: Object.entries(byDataset).map(([k]) => (k === 'STUDENT' ? '学生数据' : '成绩数据'))
        },
        series: [{
          name: '问题数',
          type: 'bar',
          barWidth: '45%',
          itemStyle: { color: '#e6a23c' },
          data: Object.entries(byDataset).map(([, v]) => v)
        }]
      })
    }
  })
}

function handleResize() {
  typeChart?.resize()
  datasetChart?.resize()
}

onMounted(() => {
  load()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  typeChart?.dispose()
  datasetChart?.dispose()
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

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

.chart-box {
  height: 280px;
  width: 100%;
}

.sample-json {
  margin: 0;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
