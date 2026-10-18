<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>教务系统对接</span>
          <el-tag v-if="health" type="success" effect="dark">适配器：{{ health.provider }}</el-tag>
        </div>
      </template>
      <el-alert type="info" :closable="false" show-icon>
        <template #title>
          本系统通过「适配器模式」与教务系统解耦：当前使用 Mock 实现（模拟教务平台推送演示数据）。
          接入真实教务系统（正方/强智/URP）时，新增适配器实现并切换配置 <code>xqfx.acad.provider</code>，接口层无需改动。
        </template>
      </el-alert>

      <el-row :gutter="12" class="sync-row">
        <el-col v-for="t in TYPES" :key="t.type" :xs="12" :md="6">
          <el-card shadow="hover" class="sync-card">
            <div class="sync-icon" :style="{ background: t.color }">{{ t.icon }}</div>
            <div class="sync-name">{{ t.label }}</div>
            <div class="sync-desc">{{ t.desc }}</div>
            <el-button type="primary" plain size="small" :loading="syncing === t.type" style="margin-top: 10px"
                       @click="doSync(t.type)">
              同步{{ t.label }}
            </el-button>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header><span>最近同步记录</span></template>
      <el-table :data="logs" border stripe size="small" v-loading="logLoading">
        <el-table-column prop="taskNo" label="任务编号" width="200" />
        <el-table-column prop="importType" label="同步类型" width="140" />
        <el-table-column prop="fileName" label="说明" min-width="180" show-overflow-tooltip />
        <el-table-column prop="totalCount" label="总量" width="80" />
        <el-table-column prop="successCount" label="成功" width="80" />
        <el-table-column prop="failCount" label="失败" width="80" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'SUCCESS' ? 'success' : 'danger'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="时间" width="170" />
      </el-table>
      <el-empty v-if="!logLoading && !logs.length" description="暂无同步记录，点击上方按钮开始首次同步" :image-size="60" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { acadHealth, acadSync, acadLogs } from '@/api/acad'

const TYPES = [
  { type: 'STUDENTS', label: '学生', icon: '👨‍🎓', desc: '学生主数据（学号/姓名/班级/专业）', color: '#409eff' },
  { type: 'COURSES', label: '课程', icon: '📘', desc: '课程与开课计划（学分/课时/任课教师）', color: '#67c23a' },
  { type: 'SCORES', label: '成绩', icon: '📊', desc: '课程成绩（各学期考试数据）', color: '#e6a23c' },
  { type: 'ATTENDANCE', label: '考勤', icon: '🕐', desc: '考勤记录（迟到/缺勤/请假）', color: '#f56c6c' }
]

const health = ref(null)
const logs = ref([])
const syncing = ref('')
const logLoading = ref(false)

async function doSync(type) {
  syncing.value = type
  try {
    const res = await acadSync(type)
    ElMessage.success(`${res?.message || '同步完成'}（成功 ${res?.successCount ?? 0} 条）`)
    await loadLogs()
  } finally {
    syncing.value = ''
  }
}

async function loadLogs() {
  logLoading.value = true
  try {
    logs.value = await acadLogs()
  } finally {
    logLoading.value = false
  }
}

onMounted(async () => {
  health.value = await acadHealth()
  await loadLogs()
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sync-row {
  margin-top: 16px;
}

.sync-card {
  border-radius: 8px;
  margin-bottom: 12px;
}

.sync-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  color: #fff;
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8px;
}

.sync-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.sync-desc {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  margin-top: 4px;
}
</style>
