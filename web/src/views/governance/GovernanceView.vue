<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>预警下钻分析</span>
          <div>
            <el-select v-model="dimension" size="small" style="width: 130px" @change="loadDrill">
              <el-option label="按学院" value="COLLEGE" />
              <el-option label="按专业" value="MAJOR" />
              <el-option label="按年级" value="GRADE" />
              <el-option label="按性别" value="GENDER" />
              <el-option label="按班级" value="CLAZZ" />
            </el-select>
            <el-select v-model="termId" placeholder="学期" clearable size="small" style="width: 170px"
                       @change="loadDrill">
              <el-option v-for="t in terms" :key="t.id" :label="t.termName" :value="t.id" />
            </el-select>
          </div>
        </div>
      </template>

      <div ref="drillRef" class="chart-box"></div>

      <el-table v-loading="drillLoading" :data="drill" border stripe size="small" style="margin-top: 16px">
        <el-table-column prop="name" :label="dimensionLabel" min-width="140" />
        <el-table-column prop="studentCount" label="学生数" width="90" />
        <el-table-column prop="warningCount" label="预警数" width="90" />
        <el-table-column label="预警率" width="150">
          <template #default="{ row }">
            <el-progress :percentage="toNumber(row.warningRate)" :stroke-width="14" text-inside />
          </template>
        </el-table-column>
        <el-table-column label="等级分布" min-width="220">
          <template #default="{ row }">
            <el-tag size="small" type="info">蓝 {{ row.blueCount }}</el-tag>
            <el-tag size="small" type="warning" style="margin-left: 4px">黄 {{ row.yellowCount }}</el-tag>
            <el-tag size="small" type="warning" style="margin-left: 4px">橙 {{ row.orangeCount }}</el-tag>
            <el-tag size="small" type="danger" style="margin-left: 4px">红 {{ row.redCount }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处置率" width="150">
          <template #default="{ row }">
            <el-progress :percentage="toNumber(row.handleRate)" :stroke-width="14" text-inside
                         status="success" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>处置时效（SLA）</span>
          <div>
            <el-button size="small" :loading="scanning" @click="doScan">扫描超时并升级</el-button>
            <el-button size="small" @click="loadSla">刷新</el-button>
            <el-button size="small" :loading="exporting" @click="doExportTickets">导出工单</el-button>
            <el-button size="small" :loading="exportingW" @click="doExportWarnings">导出预警</el-button>
          </div>
        </div>
      </template>

      <el-row :gutter="16" v-if="sla">
        <el-col :xs="12" :md="6">
          <el-card shadow="hover" class="stat-card">
            <p class="stat-title">处置及时率</p>
            <p class="stat-value" :style="{ color: rateColor(sla.onTimeRate) }">{{ sla.onTimeRate }}%</p>
          </el-card>
        </el-col>
        <el-col :xs="12" :md="6">
          <el-card shadow="hover" class="stat-card">
            <p class="stat-title">结案率</p>
            <p class="stat-value">{{ sla.closeRate }}%</p>
          </el-card>
        </el-col>
        <el-col :xs="12" :md="6">
          <el-card shadow="hover" class="stat-card">
            <p class="stat-title">超时工单</p>
            <p class="stat-value" :style="{ color: sla.overdueCount ? '#f56c6c' : '#67c23a' }">
              {{ sla.overdueCount }}
            </p>
          </el-card>
        </el-col>
        <el-col :xs="12" :md="6">
          <el-card shadow="hover" class="stat-card">
            <p class="stat-title">平均处理时长</p>
            <p class="stat-value">{{ sla.avgHandleHours }}<span class="unit">小时</span></p>
          </el-card>
        </el-col>
      </el-row>

      <el-alert :closable="false" type="warning" show-icon style="margin: 16px 0">
        <template #title>
          时限约定：蓝色 7 天、黄色 3 天、橙色 1 天、红色 8 小时；
          超时 24 小时升级至院系管理员，超时 72 小时升级至校级学工负责人。
        </template>
      </el-alert>

      <el-row :gutter="16">
        <el-col :xs="24" :md="12">
          <div ref="overdueRef" class="chart-box"></div>
        </el-col>
        <el-col :xs="24" :md="12">
          <div ref="feedbackRef" class="chart-box"></div>
        </el-col>
      </el-row>

      <el-table :data="sla?.ownerStats || []" border stripe size="small" style="margin-top: 16px">
        <el-table-column prop="ownerName" label="责任人" min-width="120" />
        <el-table-column prop="totalCount" label="工单数" width="90" />
        <el-table-column prop="closedCount" label="已结案" width="90" />
        <el-table-column prop="overdueCount" label="超时" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.overdueCount" type="danger" size="small">{{ row.overdueCount }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="及时率" width="160">
          <template #default="{ row }">
            <el-progress :percentage="toNumber(row.onTimeRate)" :stroke-width="14" text-inside
                         :status="row.onTimeRate >= 80 ? 'success' : undefined" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="feedbackDialog" title="预警有效性反馈" width="460px">
      <el-form label-width="80px">
        <el-form-item label="反馈">
          <el-radio-group v-model="feedbackForm.feedback">
            <el-radio value="EFFECTIVE">确认有效</el-radio>
            <el-radio value="FALSE_POSITIVE">误报</el-radio>
            <el-radio value="INCOMPLETE">信息不全</el-radio>
            <el-radio value="ADJUST">建议调整</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="feedbackForm.note" type="textarea" :rows="3" placeholder="补充具体情况，用于规则调优" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="feedbackDialog = false">取消</el-button>
        <el-button type="primary" @click="submitFeedback">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { warningDrill } from '@/api/warning'
import { slaStatistics, scanOverdue, ticketFeedback } from '@/api/intervention'
import { exportTickets, exportWarnings, downloadBlob } from '@/api/academic'
import { listBase } from '@/api/base'

const termId = ref(null)
const terms = ref([])
const dimension = ref('COLLEGE')
const drill = ref([])
const drillLoading = ref(false)
const sla = ref(null)
const scanning = ref(false)
const exporting = ref(false)
const exportingW = ref(false)

const drillRef = ref()
const overdueRef = ref()
const feedbackRef = ref()
let drillChart
let overdueChart
let feedbackChart

const feedbackDialog = ref(false)
const feedbackForm = ref({ ticketId: null, feedback: 'EFFECTIVE', note: '' })

const dimensionLabel = computed(() => ({
  COLLEGE: '学院', MAJOR: '专业', GRADE: '年级', GENDER: '性别', CLAZZ: '班级'
}[dimension.value]))

function toNumber(v) {
  const n = Number(v)
  return Number.isFinite(n) ? Math.min(100, Math.round(n)) : 0
}

function rateColor(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '#303133'
  if (n >= 90) return '#67c23a'
  if (n >= 70) return '#e6a23c'
  return '#f56c6c'
}

async function loadDrill() {
  drillLoading.value = true
  try {
    drill.value = await warningDrill(termId.value, dimension.value)
    renderDrill()
  } finally {
    drillLoading.value = false
  }
}

async function loadSla() {
  sla.value = await slaStatistics()
  renderSla()
}

async function doScan() {
  scanning.value = true
  try {
    const res = await scanOverdue()
    ElMessage.success(res.message || '扫描完成')
    await loadSla()
  } finally {
    scanning.value = false
  }
}

async function doExportTickets() {
  exporting.value = true
  try {
    downloadBlob(await exportTickets(), '干预工单.xlsx')
    ElMessage.success('导出成功')
  } finally {
    exporting.value = false
  }
}

async function doExportWarnings() {
  exportingW.value = true
  try {
    downloadBlob(await exportWarnings(termId.value), '预警数据.xlsx')
    ElMessage.success('导出成功')
  } finally {
    exportingW.value = false
  }
}

async function submitFeedback() {
  await ticketFeedback(feedbackForm.value.ticketId, feedbackForm.value.feedback, feedbackForm.value.note)
  ElMessage.success('反馈已记录')
  feedbackDialog.value = false
  await loadSla()
}

function renderDrill() {
  nextTick(() => {
    if (!drillRef.value) return
    drillChart = drillChart || echarts.init(drillRef.value)
    drillChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['蓝色', '黄色', '橙色', '红色'], bottom: 0 },
      grid: { left: 50, right: 20, top: 30, bottom: 50 },
      xAxis: { type: 'category', data: drill.value.map((d) => d.name) },
      yAxis: { type: 'value', name: '预警数' },
      series: [
        {
          name: '蓝色', type: 'bar', stack: 'lv',
          itemStyle: { color: '#409eff' },
          data: drill.value.map((d) => d.blueCount)
        },
        {
          name: '黄色', type: 'bar', stack: 'lv',
          itemStyle: { color: '#e6a23c' },
          data: drill.value.map((d) => d.yellowCount)
        },
        {
          name: '橙色', type: 'bar', stack: 'lv',
          itemStyle: { color: '#f78989' },
          data: drill.value.map((d) => d.orangeCount)
        },
        {
          name: '红色', type: 'bar', stack: 'lv',
          itemStyle: { color: '#f56c6c' },
          data: drill.value.map((d) => d.redCount)
        }
      ]
    })
  })
}

function renderSla() {
  nextTick(() => {
    const dist = sla.value?.overdueDistribution || []
    if (overdueRef.value) {
      overdueChart = overdueChart || echarts.init(overdueRef.value)
      overdueChart.setOption({
        title: { text: '超时时长分布', textStyle: { fontSize: 14 } },
        tooltip: { trigger: 'item' },
        legend: { bottom: 0 },
        series: [{
          name: '工单数',
          type: 'pie',
          radius: ['40%', '62%'],
          data: dist.map((d) => ({ name: d.name, value: Number(d.value) || 0 }))
        }]
      })
    }

    const fb = sla.value?.feedbackDistribution || {}
    if (feedbackRef.value) {
      feedbackChart = feedbackChart || echarts.init(feedbackRef.value)
      const FB_TEXT = {
        EFFECTIVE: '确认有效', FALSE_POSITIVE: '误报', INCOMPLETE: '信息不全', ADJUST: '建议调整'
      }
      feedbackChart.setOption({
        title: { text: '预警有效性反馈', textStyle: { fontSize: 14 } },
        tooltip: { trigger: 'axis' },
        grid: { left: 80, right: 30, top: 40, bottom: 30 },
        xAxis: { type: 'value' },
        yAxis: {
          type: 'category',
          data: Object.keys(fb).map((k) => FB_TEXT[k] || k)
        },
        series: [{
          name: '反馈数',
          type: 'bar',
          barWidth: '50%',
          itemStyle: { color: '#409eff' },
          data: Object.values(fb)
        }]
      })
    }
  })
}

function handleResize() {
  drillChart?.resize()
  overdueChart?.resize()
  feedbackChart?.resize()
}

onMounted(async () => {
  terms.value = await listBase('term')
  await loadDrill()
  await loadSla()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  drillChart?.dispose()
  overdueChart?.dispose()
  feedbackChart?.dispose()
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

.unit {
  font-size: 13px;
  font-weight: 400;
  color: #909399;
  margin-left: 2px;
}

.chart-box {
  height: 300px;
  width: 100%;
}
</style>
