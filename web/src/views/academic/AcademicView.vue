<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>学业核心指标</span>
          <div>
            <el-select v-model="termId" placeholder="学期（空为累计）" clearable size="small"
                       style="width: 180px" @change="load">
              <el-option v-for="t in terms" :key="t.id" :label="t.termName" :value="t.id" />
            </el-select>
            <el-button size="small" :loading="exporting" @click="doExport" style="margin-left: 8px">
              导出 Excel
            </el-button>
          </div>
        </div>
      </template>

      <el-alert :closable="false" type="info" show-icon style="margin-bottom: 16px">
        <template #title>
          指标口径：GPA 为学分加权绩点（4.0 制）；欠学分为不及格课程学分之和；
          趋势斜率为学期均分的最小二乘回归斜率（负值表示下滑）；
          偏离度为与班级均值相差的标准差倍数，用于识别"每门课低空飞过但整体落后"的隐性风险。
        </template>
      </el-alert>

      <el-table v-loading="loading" :data="list" border stripe size="small">
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="studentName" label="姓名" width="90" />
        <el-table-column label="GPA" width="90">
          <template #default="{ row }">
            <el-tag :type="gpaTagType(row.gpa)" size="small">{{ row.gpa }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalCredit" label="修读学分" width="90" />
        <el-table-column prop="earnedCredit" label="已获学分" width="90" />
        <el-table-column label="欠学分" width="110">
          <template #default="{ row }">
            <el-text v-if="row.deficitCredit > 0" type="danger">{{ row.deficitCredit }}</el-text>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="不及格" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.failCount" type="danger" size="small">{{ row.failCount }} 门</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="趋势斜率" width="110">
          <template #default="{ row }">
            <el-text :type="slopeType(row.trendSlope)">
              {{ row.trendSlope > 0 ? '+' : '' }}{{ row.trendSlope }}
            </el-text>
          </template>
        </el-table-column>
        <el-table-column label="绩点偏离" width="100">
          <template #default="{ row }">
            <el-text :type="row.deviation <= -1 ? 'danger' : 'info'">{{ row.deviation }}</el-text>
          </template>
        </el-table-column>
        <el-table-column label="风险判定" width="100">
          <template #default="{ row }">
            <el-tag :type="riskTagType(row.riskFlag)" size="small">{{ riskText(row.riskFlag) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="判定依据" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewTimeline(row)">时间线</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="timelineDialog" :title="timelineTitle" width="640px">
      <el-empty v-if="!timeline.length" description="暂无风险事件" :image-size="60" />
      <el-timeline v-else>
        <el-timeline-item v-for="(item, idx) in timeline" :key="idx"
                          :type="timelineColor(item.severity)" :timestamp="formatTime(item.eventTime)"
                          placement="top">
          <div class="timeline-item">
            <el-tag size="small">{{ item.eventTypeText }}</el-tag>
            <span class="timeline-title">{{ item.title }}</span>
          </div>
          <p v-if="item.detail" class="timeline-detail">{{ item.detail }}</p>
        </el-timeline-item>
      </el-timeline>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { academicBatch, exportIndicators, downloadBlob } from '@/api/academic'
import { warningTimeline } from '@/api/warning'
import { listBase } from '@/api/base'

const termId = ref(null)
const terms = ref([])
const list = ref([])
const loading = ref(false)
const exporting = ref(false)

const timelineDialog = ref(false)
const timelineTitle = ref('')
const timeline = ref([])

function gpaTagType(gpa) {
  const v = Number(gpa)
  if (!Number.isFinite(v)) return 'info'
  if (v >= 3.5) return 'success'
  if (v >= 2.0) return 'warning'
  return 'danger'
}

function slopeType(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return 'info'
  if (n <= -8) return 'danger'
  if (n < 0) return 'warning'
  return 'success'
}

function riskTagType(flag) {
  return flag === 'RISK' ? 'danger' : flag === 'ATTENTION' ? 'warning' : 'info'
}

function riskText(flag) {
  return flag === 'RISK' ? '风险' : flag === 'ATTENTION' ? '关注' : '正常'
}

function timelineColor(severity) {
  return severity === 'SEVERE' ? 'danger' : severity === 'ATTENTION' ? 'warning' : 'primary'
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').substring(0, 16)
}

async function load() {
  loading.value = true
  try {
    list.value = await academicBatch(termId.value, 0)
  } finally {
    loading.value = false
  }
}

async function doExport() {
  exporting.value = true
  try {
    const data = await exportIndicators(termId.value)
    downloadBlob(data, '学业指标.xlsx')
    ElMessage.success('导出成功')
  } finally {
    exporting.value = false
  }
}

async function viewTimeline(row) {
  timelineTitle.value = `${row.studentName || ''}（${row.studentNo || ''}）风险事件时间线`
  timeline.value = await warningTimeline(row.studentId)
  timelineDialog.value = true
}

onMounted(async () => {
  terms.value = await listBase('term')
  await load()
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.timeline-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.timeline-title {
  font-weight: 600;
  font-size: 13px;
}

.timeline-detail {
  margin: 4px 0 0;
  font-size: 12px;
  color: #909399;
}
</style>
