<template>
  <div class="monitor">
    <div class="head">
      <div class="title">管理员监察中心</div>
      <div class="head-right">
        <el-switch v-model="autoRefresh" size="small" active-text="自动刷新" style="margin-right: 8px" />
        <el-button size="small" type="primary" :loading="loading" @click="loadAll">刷新</el-button>
      </div>
    </div>

    <!-- 三端 + 系统概览 -->
    <div class="kpi-row">
      <div class="kpi">
        <div class="kpi-title">学生端</div>
        <div class="kpi-line"><span>在校学生</span><b>{{ overview?.student?.studentCount ?? '-' }}</b></div>
        <div class="kpi-line"><span>学习目标</span><b>{{ overview?.student?.goalCount ?? '-' }}</b></div>
        <div class="kpi-line"><span>画像快照</span><b>{{ overview?.student?.profileCount ?? '-' }}</b></div>
        <div class="kpi-line"><span>未读通知</span><b class="warn">{{ overview?.student?.unreadNotify ?? '-' }}</b></div>
      </div>
      <div class="kpi">
        <div class="kpi-title">教师端</div>
        <div class="kpi-line"><span>教师账号</span><b>{{ overview?.teacher?.teacherCount ?? '-' }}</b></div>
        <div class="kpi-line"><span>待处理预警</span><b class="warn">{{ overview?.teacher?.activeWarning ?? '-' }}</b></div>
        <div class="kpi-line"><span>进行中工单</span><b>{{ overview?.teacher?.openTicket ?? '-' }}</b></div>
        <div class="kpi-line"><span>画像快照</span><b>{{ overview?.teacher?.snapshotCount ?? '-' }}</b></div>
      </div>
      <div class="kpi">
        <div class="kpi-title">家长端</div>
        <div class="kpi-line"><span>家长账号</span><b>{{ overview?.parent?.parentCount ?? '-' }}</b></div>
        <div class="kpi-line"><span>回执总数</span><b>{{ overview?.parent?.feedbackCount ?? '-' }}</b></div>
        <div class="kpi-line"><span>未读通知</span><b class="warn">{{ overview?.parent?.unreadNotify ?? '-' }}</b></div>
        <div class="kpi-line"><span style="opacity:0">·</span><b style="opacity:0">-</b></div>
      </div>
      <div class="kpi">
        <div class="kpi-title">系统端</div>
        <div class="kpi-line"><span>账号总数</span><b>{{ overview?.system?.userCount ?? '-' }}</b></div>
        <div class="kpi-line"><span>审计日志</span><b>{{ overview?.system?.auditCount ?? '-' }}</b></div>
        <div class="kpi-line"><span>AI 调用</span><b>{{ overview?.system?.aiCallCount ?? '-' }}</b></div>
        <div class="kpi-line"><span>成绩记录</span><b>{{ overview?.system?.scoreCount ?? '-' }}</b></div>
      </div>
    </div>

    <div class="main-row">
      <!-- 学生数据全景 -->
      <div class="panel student-panel">
        <div class="panel-title">
          学生数据全景
          <el-select v-model="selectedStudent" filterable clearable placeholder="搜索并选择学生"
                     style="width: 220px; margin-left: 12px" @change="onStudentChange">
            <el-option v-for="s in studentOptions" :key="s.id" :value="s.id"
                       :label="`${s.studentNo} · ${s.name}`" />
          </el-select>
          <el-input v-model="studentKeyword" placeholder="按学号/姓名搜索" size="small" clearable
                    style="width: 150px; margin-left: 8px" @keyup.enter="searchStudents" @clear="searchStudents" />
          <el-button size="small" @click="searchStudents">查</el-button>
        </div>

        <div v-if="panorama?.studentName" class="panorama">
          <div class="p-head">
            {{ panorama.studentName }}
            <span class="p-sub">{{ panorama.studentNo }} · {{ panorama.clazzName || '-' }} · {{ panorama.grade || '-' }} · {{ panorama.collegeName || '-' }}</span>
          </div>
          <div class="p-grid">
            <div class="p-cell"><div>成绩记录</div><b>{{ panorama.scoreCount ?? '-' }}</b></div>
            <div class="p-cell"><div>平均分</div><b>{{ panorama.avgScore ?? '-' }}</b></div>
            <div class="p-cell"><div>画像快照</div><b>{{ panorama.snapshotCount ?? '-' }}</b></div>
            <div class="p-cell"><div>待处理预警</div><b class="warn">{{ panorama.activeWarning ?? '-' }}</b></div>
            <div class="p-cell"><div>学习目标</div><b>{{ panorama.goalCount ?? '-' }}</b></div>
            <div class="p-cell"><div>未读通知</div><b>{{ panorama.notifyUnread ?? '-' }}</b></div>
            <div class="p-cell"><div>帮扶工单</div><b>{{ panorama.ticketCount ?? '-' }}</b></div>
            <div class="p-cell"><div>消息</div><b>{{ panorama.messageCount ?? '-' }}</b></div>
          </div>
          <div v-if="panorama.latestPredictions?.length" class="p-predict">
            <div class="p-predict-title">最新风险预测</div>
            <el-table :data="panorama.latestPredictions" size="small" border>
              <el-table-column prop="subject" label="预测结果" />
              <el-table-column prop="content" label="学期" width="140" />
              <el-table-column prop="time" label="时间" width="130" />
            </el-table>
          </div>
        </div>
        <el-empty v-else description="选择学生查看三端数据全景" :image-size="60" />
      </div>

      <!-- 各端最新动态 -->
      <div class="panel feed-panel">
        <div class="panel-title">各端最新动态</div>
        <el-tabs v-model="activeTab">
          <el-tab-pane label="预警" name="warnings">
            <el-table :data="latest?.warnings || []" size="small" border>
              <el-table-column prop="time" label="时间" width="110" />
              <el-table-column prop="subject" label="对象" />
              <el-table-column prop="content" label="类型/状态" width="150" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="帮扶工单" name="tickets">
            <el-table :data="latest?.tickets || []" size="small" border>
              <el-table-column prop="time" label="时间" width="110" />
              <el-table-column prop="subject" label="学生" />
              <el-table-column prop="content" label="状态" width="120" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="家长回执" name="feedbacks">
            <el-table :data="latest?.feedbacks || []" size="small" border>
              <el-table-column prop="time" label="时间" width="110" />
              <el-table-column prop="subject" label="回执对象" />
              <el-table-column prop="content" label="内容" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="站内消息" name="messages">
            <el-table :data="latest?.messages || []" size="small" border>
              <el-table-column prop="time" label="时间" width="110" />
              <el-table-column prop="subject" label="会话" />
              <el-table-column prop="content" label="内容" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="AI 调用" name="aiCalls">
            <el-table :data="latest?.aiCalls || []" size="small" border>
              <el-table-column prop="time" label="时间" width="110" />
              <el-table-column prop="subject" label="场景/模型" />
              <el-table-column prop="content" label="状态" width="140" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="审计日志" name="audits">
            <el-table :data="latest?.audits || []" size="small" border>
              <el-table-column prop="time" label="时间" width="110" />
              <el-table-column prop="subject" label="操作者·模块" />
              <el-table-column prop="content" label="参数" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { monitorOverview, monitorLatest, monitorStudent } from '@/api/monitor'
import { pageStudents } from '@/api/student'

const overview = ref(null)
const latest = ref(null)
const panorama = ref(null)
const loading = ref(false)
const autoRefresh = ref(true)
const activeTab = ref('warnings')

const studentKeyword = ref('')
const studentOptions = ref([])
const selectedStudent = ref(null)

let timer = null

async function loadOverview() {
  overview.value = await monitorOverview()
}
async function loadLatest() {
  latest.value = await monitorLatest()
}
async function loadAll() {
  loading.value = true
  try {
    await Promise.all([loadOverview(), loadLatest()])
  } finally {
    loading.value = false
  }
}

async function searchStudents() {
  const res = await pageStudents({ current: 1, size: 50, keyword: studentKeyword.value || undefined })
  studentOptions.value = (res?.records || []).map((s) => ({ id: s.id, studentNo: s.studentNo, name: s.name }))
  if (selectedStudent.value && !studentOptions.value.some((o) => o.id === selectedStudent.value)) {
    selectedStudent.value = null
    panorama.value = null
  }
}

async function onStudentChange(id) {
  if (!id) {
    panorama.value = null
    return
  }
  panorama.value = await monitorStudent(id)
}

onMounted(async () => {
  await loadAll()
  await searchStudents()
  timer = setInterval(() => {
    if (autoRefresh.value) loadAll()
  }, 10000)
})

onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.monitor {
  padding: 16px;
  box-sizing: border-box;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.title {
  font-size: 18px;
  font-weight: 700;
}

.head-right {
  display: flex;
  align-items: center;
}

.kpi-row {
  display: flex;
  gap: 12px;
  margin-bottom: 14px;
}

.kpi {
  flex: 1;
  background: #fff;
  border: 1px solid #e6e8eb;
  border-radius: 10px;
  padding: 12px 16px;
}

.kpi-title {
  font-size: 14px;
  font-weight: 700;
  color: #1f2329;
  margin-bottom: 8px;
}

.kpi-line {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #646a73;
  line-height: 1.9;
}

.kpi-line b {
  color: #1f2329;
}

.kpi-line b.warn {
  color: #e64340;
}

.main-row {
  display: flex;
  gap: 12px;
}

.panel {
  background: #fff;
  border: 1px solid #e6e8eb;
  border-radius: 10px;
  padding: 12px 16px;
  box-sizing: border-box;
}

.student-panel {
  width: 46%;
  min-width: 460px;
}

.feed-panel {
  flex: 1;
  min-width: 0;
}

.panel-title {
  font-size: 14px;
  font-weight: 700;
  color: #1f2329;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
}

.p-head {
  font-size: 16px;
  font-weight: 700;
  margin-bottom: 10px;
}

.p-sub {
  font-size: 12px;
  color: #646a73;
  font-weight: 400;
  margin-left: 8px;
}

.p-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}

.p-cell {
  background: #f7f8fa;
  border-radius: 8px;
  padding: 8px;
  text-align: center;
}

.p-cell div {
  font-size: 12px;
  color: #646a73;
}

.p-cell b {
  font-size: 18px;
  color: #1f2329;
}

.p-cell b.warn {
  color: #e64340;
}

.p-predict-title {
  font-size: 13px;
  color: #1f2329;
  font-weight: 600;
  margin-bottom: 6px;
}

:deep(.el-table) {
  font-size: 12px;
}
</style>
